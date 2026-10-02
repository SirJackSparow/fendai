#include "rag.h"

#include <algorithm>
#include <cmath>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <sstream>

namespace {
	constexpr size_t kChunkWords = 180;
	constexpr size_t kChunkOverlap = 30;

	/**
	 * Normalizes input into lowercase word tokens.
	 *
	 * This is the preprocessing step for indexing and query matching. It strips
	 * punctuation and turns text into a clean list of words so searches are case-
	 * insensitive and stable across different formatting styles.
	 */
	std::vector<std::string> tokenize(const std::string& text) {
		std::vector<std::string> result;
		std::string token;
		for (unsigned char character : text) {
			if (std::isalnum(character)) {
				token.push_back(static_cast<char>(std::tolower(character)));
			} else if (!token.empty()) {
				result.push_back(token);
				token.clear();
			}
		}
		if (!token.empty()) result.push_back(token);
		return result;
	}

	/**
	 * Breaks one large document into overlapping chunks.
	 *
	 * The engine does not index one giant text blob. Instead it creates smaller
	 * windows of words (around 180 words with 30-word overlap) so the retriever can
	 * find the most relevant snippet and keep the context precise.
	 */
	void addChunks(fendai::rag::RagEngine& engine, const std::string& source,
			const std::string& text) {
		const auto words = tokenize(text);
		if (words.empty()) return;
		for (size_t start = 0; start < words.size();) {
			const size_t end = std::min(start + kChunkWords, words.size());
			std::ostringstream chunk;
			for (size_t index = start; index < end; ++index) {
				if (index != start) chunk << ' ';
				chunk << words[index];
			}
			engine.addDocument(source, chunk.str());
			if (end == words.size()) break;
			start = end > kChunkOverlap ? end - kChunkOverlap : end;
		}
	}
}

namespace fendai::rag {
	/**
	 * Removes the current knowledge base from memory.
	 *
	 * This is used before reloading a path or resetting the app's local retrieval
	 * knowledge. It drops both the chunks and the stored document-frequency counts.
	 */
	void RagEngine::clear() {
		_chunks.clear();
		_documentFrequency.clear();
	}

	/**
	 * Adds one chunk of text to the index.
	 *
	 * The chunk stores its source file and the word-frequency map for retrieval. The
	 * global document-frequency map is updated too, which helps the search function
	 * give more weight to distinctive words.
	 */
	void RagEngine::addDocument(const std::string& source, const std::string& text) {
		const auto words = tokenize(text);
		if (words.empty()) return;
		Chunk chunk{source, text, {}};
		for (const auto& word : words) ++chunk.terms[word];
		for (const auto& term : chunk.terms) ++_documentFrequency[term.first];
		_chunks.push_back(std::move(chunk));
	}

	/**
	 * Loads a file or directory into the retrieval index.
	 *
	 * The engine first clears old data, then recursively reads all regular files and
	 * converts them into chunked documents. This is the setup step that prepares the
	 * local knowledge base for future queries.
	 */
	int RagEngine::loadPath(const std::string& path) {
		clear();
		const std::filesystem::path root(path);
		if (!std::filesystem::exists(root)) return 0;
		auto loadFile = [this](const std::filesystem::path& file) {
			std::ifstream input(file, std::ios::in | std::ios::binary);
			if (!input) return;
			std::ostringstream contents;
			contents << input.rdbuf();
			addChunks(*this, file.string(), contents.str());
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
	 * It turns the query into tokens and scores each stored chunk with TF-IDF. A word
	 * that appears many times in a chunk gets a higher local score, but common words that
	 * appear in too many chunks are discounted. Then the best results are sorted and
	 * limited by topK.
	 */
	std::vector<SearchResult> RagEngine::search(const std::string& query, int topK) const {
		const auto queryTerms = tokenize(query);
		if (queryTerms.empty() || topK <= 0) return {};
		// Cache query weights once; repeated terms remain repeated to preserve scoring.
		std::vector<float> inverseFrequencies;
		inverseFrequencies.reserve(queryTerms.size());
		for (const auto& term : queryTerms) {
			const auto documentFrequency = _documentFrequency.find(term);
			if (documentFrequency == _documentFrequency.end()) {
				inverseFrequencies.push_back(0.0f);
				continue;
			}
			inverseFrequencies.push_back(std::log(
					(static_cast<float>(_chunks.size()) + 1.0f) /
					(static_cast<float>(documentFrequency->second) + 1.0f)) + 1.0f);
		}

		std::vector<SearchResult> results;
		for (const auto& chunk : _chunks) {
			float score = 0.0f;
			for (size_t index = 0; index < queryTerms.size(); ++index) {
				const auto& term = queryTerms[index];
				if (inverseFrequencies[index] == 0.0f) continue;
				const auto frequency = chunk.terms.find(term);
				if (frequency == chunk.terms.end()) continue;
				score += (1.0f + std::log(static_cast<float>(frequency->second))) *
						inverseFrequencies[index];
			}
			if (score > 0.0f) results.push_back({chunk.source, chunk.text, score});
		}
		const auto byScore = [](const auto& left, const auto& right) {
			return left.score > right.score;
		};
		const auto resultLimit = static_cast<size_t>(topK);
		if (results.size() > resultLimit) {
			// Order only the requested prefix when lower-ranked results will be discarded.
			std::partial_sort(results.begin(), results.begin() + resultLimit, results.end(), byScore);
			results.resize(resultLimit);
		} else {
			std::sort(results.begin(), results.end(), byScore);
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
			int maxContextCharacters, bool allowGeneralKnowledgeWhenNoMatch) const {
		const auto results = search(query, topK);
		if (results.empty() && allowGeneralKnowledgeWhenNoMatch) return query;

		std::ostringstream prompt;
		prompt << "Answer the question using only the context below. If the context does not "
				  "contain the answer, say that you do not know. Do not invent sources.\n\n";
		int usedCharacters = 0;
		for (const auto& result : results) {
			const std::string block = "[Source: " + result.source + "]\n" + result.text + "\n\n";
			if (usedCharacters + static_cast<int>(block.size()) > maxContextCharacters) break;
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