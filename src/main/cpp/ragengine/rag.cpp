#include "rag.h"
#include "ggml-backend.h"
#include "llama.h"

#include <algorithm>
#include <cmath>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <limits>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <thread>
#include <unordered_set>
#include <utility>

namespace {
	bool endsWith(const std::string& value, const char* suffix) {
		const size_t suffixLength = std::char_traits<char>::length(suffix);
		return value.size() >= suffixLength &&
				value.compare(value.size() - suffixLength, suffixLength, suffix) == 0;
	}

	const std::unordered_set<std::string>& stopWords() {
		static const std::unordered_set<std::string> words = {
				"a", "an", "and", "are", "as", "at", "be", "been", "being", "but",
				"by", "can", "could", "did", "do", "does", "for", "from", "had",
				"has", "have", "he", "her", "here", "hers", "him", "his", "how",
				"i", "if", "in", "into", "is", "it", "its", "me", "my", "of",
				"on", "or", "our", "ours", "she", "so", "than", "that", "the",
				"their", "them", "then", "there", "these", "they", "this", "those",
				"to", "up", "us", "was", "we", "were", "what", "when", "where",
				"which", "who", "why", "will", "with", "would", "you", "your"
		};
		return words;
	}

	bool isWordCharacter(unsigned char character) {
		return character >= 0x80 || std::isalnum(character);
	}

	char lowercaseAscii(unsigned char character) {
		return character < 0x80
				? static_cast<char>(std::tolower(character))
				: static_cast<char>(character);
	}

	std::string normalizeToken(std::string token) {
		if (token.size() > 4 && endsWith(token, "ies")) {
			token.replace(token.size() - 3, 3, "y");
		} else if (token.size() > 5 && endsWith(token, "ing")) {
			token.resize(token.size() - 3);
			if (token.size() > 2 && token.back() == token[token.size() - 2] &&
					token.back() != 's' && token.back() != 'l' && token.back() != 'z') {
				token.pop_back();
			}
		} else if (token.size() > 4 && endsWith(token, "ed")) {
			token.resize(token.size() - 2);
			if (token.size() > 2 && token.back() == token[token.size() - 2] &&
					token.back() != 's' && token.back() != 'l' && token.back() != 'z') {
				token.pop_back();
			}
		} else if (token.size() > 5 && endsWith(token, "ments")) {
			token.resize(token.size() - 5);
		} else if (token.size() > 4 && endsWith(token, "ment")) {
			token.resize(token.size() - 4);
		} else if (token.size() > 4 && endsWith(token, "es")) {
			token.resize(token.size() - 2);
		} else if (token.size() > 3 && token.back() == 's' &&
				!endsWith(token, "ss") && !endsWith(token, "us")) {
			token.pop_back();
		}
		return token;
	}

	/**
	 * Normalizes input into lowercase word tokens and optionally removes common
	 * words that carry little retrieval value.
	 *
	 * A small suffix normalizer helps match common forms such as "assessment",
	 * "assessments", and "assessing" without an external NLP dependency.
	 */
	std::vector<std::string> tokenize(
			const std::string& text,
			bool removeStopWords = true,
			const std::unordered_set<std::string>* customStopWords = nullptr) {
		std::vector<std::string> result;
		std::string token;
		auto appendToken = [&] {
			if (token.empty()) return;
			token = normalizeToken(std::move(token));
			const auto& wordsToRemove = customStopWords == nullptr ? stopWords() : *customStopWords;
			if (!token.empty() && (!removeStopWords || wordsToRemove.find(token) == wordsToRemove.end())) {
				result.push_back(std::move(token));
			}
			token.clear();
		};
		for (unsigned char character : text) {
			if (isWordCharacter(character)) {
				token.push_back(lowercaseAscii(character));
			} else {
				appendToken();
			}
		}
		appendToken();
		return result;
	}

	/**
	 * Breaks one large document into overlapping chunks.
	 *
	 * The engine does not index one giant text blob. Instead it creates smaller
	 * windows of words using the configured chunk size and overlap so the retriever
	 * can find the most relevant snippet and keep the context precise.
	 */
	void addChunks(fendai::rag::RagEngine& engine, const std::string& source,
			std::istream& input, size_t chunkWords, size_t chunkOverlap) {
		std::vector<std::string> window;
		window.reserve(chunkWords + 1);
		std::string token;
		size_t chunksAdded = 0;
		auto addWindow = [&] {
			std::ostringstream chunk;
			for (size_t index = 0; index < window.size(); ++index) {
				if (index != 0) chunk << ' ';
				chunk << window[index];
			}
			engine.addDocument(source, chunk.str());
			++chunksAdded;
		};
		auto consumeCharacter = [&](unsigned char character) {
			if (isWordCharacter(character)) {
				token.push_back(lowercaseAscii(character));
			} else if (!token.empty()) {
				window.push_back(std::move(token));
				token.clear();
				if (window.size() > chunkWords) {
					std::string overflow = std::move(window.back());
					window.pop_back();
					addWindow();
					window.erase(window.begin(), window.begin() +
							static_cast<std::ptrdiff_t>(chunkWords - chunkOverlap));
					window.push_back(std::move(overflow));
				}
			}
		};
		char character;
		while (input.get(character)) {
			consumeCharacter(static_cast<unsigned char>(character));
		}
		if (!token.empty()) {
			window.push_back(std::move(token));
		}
		if (!window.empty() && (chunksAdded == 0 || window.size() > chunkOverlap)) {
			addWindow();
		}
	}
}

namespace fendai::rag {
	class RagEngine::EmbeddingModel {
	public:
		explicit EmbeddingModel(const std::string& modelPath) {
			static std::once_flag backendLoadFlag;
			std::call_once(backendLoadFlag, [] { ggml_backend_load_all(); });

			auto modelParams = llama_model_default_params();
			modelParams.n_gpu_layers = 0;
			_model = llama_model_load_from_file(modelPath.c_str(), modelParams);
			if (_model == nullptr) {
				throw std::runtime_error("Failed to load RAG embedding model: " + modelPath);
			}

			auto contextParams = llama_context_default_params();
			contextParams.n_ctx = kContextTokens;
			contextParams.n_batch = kContextTokens;
			contextParams.n_threads = std::clamp(
					static_cast<int>(std::thread::hardware_concurrency()), 1, 4);
			contextParams.n_threads_batch = contextParams.n_threads;
			contextParams.pooling_type = LLAMA_POOLING_TYPE_MEAN;
			contextParams.embeddings = true;
			contextParams.no_perf = true;
			_context = llama_init_from_model(_model, contextParams);
			if (_context == nullptr) {
				llama_model_free(_model);
				_model = nullptr;
				throw std::runtime_error("Failed to initialize RAG embedding model context");
			}
			_embeddingSize = llama_model_n_embd_out(_model);
			if (_embeddingSize <= 0) {
				llama_free(_context);
				llama_model_free(_model);
				_context = nullptr;
				_model = nullptr;
				throw std::runtime_error("RAG embedding model has no output embedding dimensions");
			}
		}

		~EmbeddingModel() {
			if (_context != nullptr) llama_free(_context);
			if (_model != nullptr) llama_model_free(_model);
		}

		std::vector<float> embed(const std::string& text) {
			if (text.size() > static_cast<size_t>(std::numeric_limits<int32_t>::max())) {
				throw std::runtime_error("Text is too large to tokenize for RAG embeddings");
			}
			const auto* vocab = llama_model_get_vocab(_model);
			const int32_t required = llama_tokenize(vocab, text.c_str(),
					static_cast<int32_t>(text.size()), nullptr, 0, true, false);
			if (required == std::numeric_limits<int32_t>::min()) {
				throw std::runtime_error("Text is too large to tokenize for RAG embeddings");
			}
			if (required > 0) {
				throw std::runtime_error("RAG embedding tokenizer returned an unexpected token count");
			}
			std::vector<llama_token> tokens(static_cast<size_t>(-required));
			const int32_t tokenCount = llama_tokenize(vocab, text.c_str(),
					static_cast<int32_t>(text.size()), tokens.data(),
					static_cast<int32_t>(tokens.size()), true, false);
			if (tokenCount < 0) {
				throw std::runtime_error("Failed to tokenize text for RAG embeddings");
			}
			tokens.resize(static_cast<size_t>(tokenCount));
			if (tokens.empty()) return {};
			if (tokens.size() > kContextTokens) tokens.resize(kContextTokens);

			llama_memory_clear(llama_get_memory(_context), true);
			llama_batch batch = llama_batch_init(static_cast<int32_t>(tokens.size()), 0, 1);
			if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr ||
					batch.seq_id == nullptr || batch.logits == nullptr) {
				llama_batch_free(batch);
				throw std::runtime_error("Failed to allocate batch for RAG embeddings");
			}
			batch.n_tokens = static_cast<int32_t>(tokens.size());
			for (int32_t index = 0; index < batch.n_tokens; ++index) {
				batch.token[index] = tokens[static_cast<size_t>(index)];
				batch.pos[index] = index;
				batch.n_seq_id[index] = 1;
				batch.seq_id[index][0] = 0;
				batch.logits[index] = true;
			}
			const int32_t decodeResult = llama_decode(_context, batch);
			llama_batch_free(batch);
			if (decodeResult < 0) {
				throw std::runtime_error("RAG embedding model failed to encode text");
			}
			const float* pooled = llama_get_embeddings_seq(_context, 0);
			if (pooled == nullptr) {
				throw std::runtime_error(
						"RAG embedding model did not return mean-pooled embeddings; use a compatible embedding GGUF");
			}
			std::vector<float> result(pooled, pooled + _embeddingSize);
			float norm = 0.0f;
			for (const float value : result) norm += value * value;
			norm = std::sqrt(norm);
			if (norm <= 0.0f || !std::isfinite(norm)) {
				throw std::runtime_error("RAG embedding model returned an invalid vector");
			}
			for (float& value : result) value /= norm;
			return result;
		}

	private:
		static constexpr uint32_t kContextTokens = 512;
		llama_model* _model = nullptr;
		llama_context* _context = nullptr;
		int32_t _embeddingSize = 0;
	};

	RagEngine::RagEngine() = default;
	RagEngine::~RagEngine() = default;

	/**
	 * Removes the current knowledge base from memory.
	 *
	 * This is used before reloading a path or resetting the app's local retrieval
	 * knowledge. It drops both the chunks and the stored document-frequency counts.
	 */
	void RagEngine::clear() {
		std::vector<Chunk>().swap(_chunks);
		std::unordered_map<std::string, int>().swap(_documentFrequency);
	}

	/**
	 * Adds one chunk of text to the index.
	 *
	 * The chunk stores its source file and the word-frequency map for retrieval. The
	 * global document-frequency map is updated too, which helps the search function
	 * give more weight to distinctive words.
	 */
	void RagEngine::addDocument(const std::string& source, const std::string& text) {
		size_t wordCount = 0;
		bool inWord = false;
		for (unsigned char character : text) {
			if (isWordCharacter(character)) {
				if (!inWord) ++wordCount;
				inWord = true;
			} else {
				inWord = false;
			}
		}
		if (wordCount > _chunkWords) {
			std::istringstream input(text);
			addChunks(*this, source, input, _chunkWords, _chunkOverlap);
			return;
		}

		const auto* customStopWords = _useDefaultStopWords ? nullptr : &_customStopWords;
		const auto words = tokenize(text, true, customStopWords);
		if (words.empty() && !_embeddingModel) return;
		Chunk chunk{source, text, {}, {}};
		for (const auto& word : words) ++chunk.terms[word];
		if (_embeddingModel) chunk.embedding = _embeddingModel->embed(text);
		for (const auto& term : chunk.terms) ++_documentFrequency[term.first];
		_chunks.push_back(std::move(chunk));
	}

	void RagEngine::loadEmbeddingModel(const std::string& modelPath) {
		auto model = std::make_unique<EmbeddingModel>(modelPath);
		std::vector<std::vector<float>> embeddings;
		embeddings.reserve(_chunks.size());
		for (const auto& chunk : _chunks) embeddings.push_back(model->embed(chunk.text));
		for (size_t index = 0; index < _chunks.size(); ++index) {
			_chunks[index].embedding = std::move(embeddings[index]);
		}
		_embeddingModel = std::move(model);
	}

	void RagEngine::clearEmbeddingModel() {
		_embeddingModel.reset();
		for (auto& chunk : _chunks) chunk.embedding.clear();
	}

	void RagEngine::setStopWords(const std::vector<std::string>& stopWords) {
		std::unordered_set<std::string> normalizedStopWords;
		for (const auto& word : stopWords) {
			for (const auto& token : tokenize(word, false)) {
				normalizedStopWords.insert(token);
			}
		}
		_customStopWords = std::move(normalizedStopWords);
		_useDefaultStopWords = false;
		rebuildTermIndex();
	}

	void RagEngine::resetStopWords() {
		_customStopWords.clear();
		_useDefaultStopWords = true;
		rebuildTermIndex();
	}

	void RagEngine::rebuildTermIndex() {
		_documentFrequency.clear();
		const auto* customStopWords = _useDefaultStopWords ? nullptr : &_customStopWords;
		for (auto& chunk : _chunks) {
			chunk.terms.clear();
			for (const auto& term : tokenize(chunk.text, true, customStopWords)) {
				++chunk.terms[term];
			}
			for (const auto& term : chunk.terms) ++_documentFrequency[term.first];
		}
	}

	/**
	 * Loads a file or directory into the retrieval index.
	 *
	 * The engine first clears old data, then recursively reads all regular files and
	 * converts them into chunked documents. This is the setup step that prepares the
	 * local knowledge base for future queries.
	 */
	int RagEngine::loadPath(const std::string& path, size_t chunkWords, size_t chunkOverlap) {
		if (chunkWords == 0 || chunkOverlap >= chunkWords) {
			throw std::invalid_argument("chunkWords must be positive and chunkOverlap must be smaller than chunkWords");
		}
		_chunkWords = chunkWords;
		_chunkOverlap = chunkOverlap;
		clear();
		const std::filesystem::path root(path);
		if (!std::filesystem::exists(root)) return 0;
		auto loadFile = [this](const std::filesystem::path& file) {
			std::string extension = file.extension().string();
			std::transform(extension.begin(), extension.end(), extension.begin(),
					[](unsigned char character) {
						return static_cast<char>(std::tolower(character));
					});
			if (extension == ".pdf") return;
			std::ifstream input(file, std::ios::in | std::ios::binary);
			if (!input) return;
			addChunks(*this, file.string(), input, _chunkWords, _chunkOverlap);
		};
		if (std::filesystem::is_regular_file(root)) {
			loadFile(root);
		} else if (std::filesystem::is_directory(root)) {
			for (const auto& entry : std::filesystem::recursive_directory_iterator(root)) {
				if (entry.is_regular_file()) loadFile(entry.path());
			}
		}
		return static_cast<int>(_chunks.size());
	}

	/**
	 * Searches for the best matching chunks for the current query.
	 *
	 * It scores normalized query terms with BM25, accounting for term rarity and chunk
	 * length. Common question words are ignored, while exact adjacent query phrases receive
	 * an additional boost. Near-identical chunks are suppressed from the result list.
	 */
	std::vector<SearchResult> RagEngine::search(const std::string& query, int topK) const {
		const auto* customStopWords = _useDefaultStopWords ? nullptr : &_customStopWords;
		const auto queryTerms = tokenize(query, true, customStopWords);
		if ((queryTerms.empty() && !_embeddingModel) || topK <= 0) return {};
		const std::vector<float> queryEmbedding = _embeddingModel
				? _embeddingModel->embed(query)
				: std::vector<float>();
		const std::unordered_set<std::string> uniqueQueryTerms(queryTerms.begin(), queryTerms.end());
		const auto phraseTerms = tokenize(query, false);
		size_t totalTerms = 0;
		for (const auto& chunk : _chunks) {
			for (const auto& term : chunk.terms) totalTerms += static_cast<size_t>(term.second);
		}
		const float averageChunkLength = _chunks.empty()
				? 0.0f
				: static_cast<float>(totalTerms) / static_cast<float>(_chunks.size());
		if (averageChunkLength <= 0.0f && !_embeddingModel) return {};

		struct Candidate {
			const Chunk* chunk;
			float lexicalScore;
			float semanticScore;
			float score;
		};
		const size_t resultLimit = static_cast<size_t>(topK);
		std::vector<Candidate> candidates;
		candidates.reserve(_chunks.size());
		float maxLexicalScore = 0.0f;
		for (const auto& chunk : _chunks) {
			size_t chunkLength = 0;
			for (const auto& term : chunk.terms) {
				chunkLength += static_cast<size_t>(term.second);
			}
			int matchedTerms = 0;
			float score = 0.0f;
			for (const auto& term : uniqueQueryTerms) {
				const auto frequency = chunk.terms.find(term);
				if (frequency == chunk.terms.end()) continue;
				++matchedTerms;
				const auto documentFrequency = _documentFrequency.find(term);
				if (documentFrequency == _documentFrequency.end()) continue;
				const float df = static_cast<float>(documentFrequency->second);
				const float idf = std::log(1.0f +
						(static_cast<float>(_chunks.size()) - df + 0.5f) / (df + 0.5f));
				const float tf = static_cast<float>(frequency->second);
				constexpr float k1 = 1.5f;
				constexpr float b = 0.75f;
				const float lengthNorm = k1 * (1.0f - b +
						b * static_cast<float>(chunkLength) / averageChunkLength);
				score += idf * (tf * (k1 + 1.0f)) / (tf + lengthNorm);
			}
			if (!uniqueQueryTerms.empty() && score > 0.0f) {
				const float coverage = static_cast<float>(matchedTerms) /
						static_cast<float>(uniqueQueryTerms.size());
				score *= 0.5f + 0.5f * coverage;
			}

			if (score > 0.0f && phraseTerms.size() > 1) {
				const auto chunkTokens = tokenize(chunk.text, false);
				for (size_t index = 1; index < phraseTerms.size(); ++index) {
					for (size_t tokenIndex = 1; tokenIndex < chunkTokens.size(); ++tokenIndex) {
						if (chunkTokens[tokenIndex - 1] == phraseTerms[index - 1] &&
								chunkTokens[tokenIndex] == phraseTerms[index]) {
							score += 1.0f;
							break;
						}
					}
				}
			}
			float semanticScore = 0.0f;
			if (_embeddingModel && chunk.embedding.size() == queryEmbedding.size()) {
				for (size_t index = 0; index < queryEmbedding.size(); ++index) {
					semanticScore += queryEmbedding[index] * chunk.embedding[index];
				}
			}
			constexpr float minimumSemanticSimilarity = 0.25f;
			if (score <= 0.0f &&
					(!_embeddingModel || semanticScore < minimumSemanticSimilarity)) continue;
			maxLexicalScore = std::max(maxLexicalScore, score);
			candidates.push_back({&chunk, score, semanticScore, 0.0f});
		}

		for (auto& candidate : candidates) {
			if (_embeddingModel) {
				const float lexical = maxLexicalScore > 0.0f
						? candidate.lexicalScore / maxLexicalScore
						: 0.0f;
				candidate.score = 0.45f * lexical +
						0.55f * std::max(0.0f, candidate.semanticScore);
			} else {
				candidate.score = candidate.lexicalScore;
			}
		}
		std::stable_sort(candidates.begin(), candidates.end(),
				[](const Candidate& left, const Candidate& right) {
					return left.score > right.score;
				});

		std::vector<SearchResult> results;
		std::vector<const Chunk*> selectedChunks;
		results.reserve(std::min(resultLimit, candidates.size()));
		selectedChunks.reserve(std::min(resultLimit, candidates.size()));
		for (const auto& candidate : candidates) {
			bool redundant = false;
			for (const Chunk* selected : selectedChunks) {
				size_t sharedTerms = 0;
				for (const auto& term : candidate.chunk->terms) {
					if (selected->terms.find(term.first) != selected->terms.end()) ++sharedTerms;
				}
				const size_t unionTerms = candidate.chunk->terms.size() +
						selected->terms.size() - sharedTerms;
				if (unionTerms > 0 &&
						static_cast<float>(sharedTerms) / static_cast<float>(unionTerms) >= 0.85f) {
					redundant = true;
					break;
				}
			}
			if (redundant) continue;
			results.push_back({candidate.chunk->source, candidate.chunk->text, candidate.score});
			selectedChunks.push_back(candidate.chunk);
			if (results.size() == resultLimit) break;
		}
		return results;
	}

	/**
	 * Formats the retrieved passages into an LLM prompt.
	 *
	 * This function takes the best matches, prepends the source text, and asks the
	 * model to answer using only that context. This is the grounding step that ties the
	 * model output back to the local files it indexed.
	 */
	std::string RagEngine::buildPrompt(const std::string& query, int topK,
			int maxContextCharacters, bool allowGeneralKnowledgeWhenNoMatch,
			AnswerMode answerMode) const {
		const auto results = search(query, topK);
		if (results.empty() && allowGeneralKnowledgeWhenNoMatch) return query;

		std::ostringstream prompt;
		if (answerMode == AnswerMode::DOCUMENTS_AND_MODEL_BACKGROUND) {
			prompt << "Answer directly and separate the answer into these sections:\n"
					  "Document answer: Use only the supplied passages for claims about the "
					  "documents and cite each such claim using its exact numeric citation, such "
					  "as [1]. If the passages do not answer the question, say that clearly.\n"
					  "General background: You may add relevant information from your general "
					  "model knowledge, but label it as background, do not imply it came from "
					  "the documents, and do not invent citations. You cannot reliably identify "
					  "the training source of general knowledge. If no passages were retrieved, "
					  "say so in the document section and answer only in the background section.\n\n";
		} else {
			prompt << "Answer the question directly using only the context below. Use relevant "
					  "details, avoid repeating the context, and cite factual claims using the "
					  "exact numeric citation for the supporting passage, such as [1]. If the "
					  "context does not contain the answer, clearly say so. Do not invent facts "
					  "or citations.\n\n";
		}
		int usedCharacters = 0;
		for (size_t index = 0; index < results.size(); ++index) {
			const auto& result = results[index];
			const std::string block = "[" + std::to_string(index + 1) + "] [Source: " +
					result.source + "]\n" + result.text + "\n\n";
			if (usedCharacters + static_cast<int>(block.size()) > maxContextCharacters) continue;
			prompt << block;
			usedCharacters += static_cast<int>(block.size());
		}
		prompt << "Question: " << query << "\nAnswer:";
		return prompt.str();
	}

	/**
	 * Returns how many text chunks are currently indexed.
	 */
	size_t RagEngine::size() const { return _chunks.size(); }

	/**
	 * Compatibility entry point for older code.
	 *
	 * It keeps a static engine internally so older C++ code can call a simple setup
	 * function without managing a RagEngine instance manually.
	 */
	void setUp(const std::string& loc) {
		static RagEngine engine;
		engine.loadPath(loc);
	}
}