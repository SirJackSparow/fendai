//
// Created by Bima Maulana Effendy on 18/09/26.
//

#pragma once

#include <string>
#include <vector>
#include <memory>
#include <unordered_map>
#include <unordered_set>

namespace fendai::rag{

    enum class AnswerMode : int {
        DOCUMENT_ONLY = 0,
        DOCUMENTS_AND_MODEL_BACKGROUND = 1,
    };

    struct SearchResult {
        std::string source;
        std::string text;
        float score;
    };

    /**
     * Retrieval engine for local knowledge grounding.
     *
     * The engine keeps a lightweight index of text chunks. During a user question,
     * it normalizes the query, scores chunks with BM25 and phrase boosts,
     * optionally blends BM25 and embedding cosine scores, and then packages the best matching
     * context into a prompt for the LLM.
     */
    class RagEngine {
    public:
        RagEngine();
        ~RagEngine();

        /** Removes all indexed chunks and term statistics. */
        void clear();

        /**
         * Loads a single file or a whole directory into the index.
         *
         * This is the main setup step: it clears the previous index, reads the source
         * documents from disk, splits them into chunks, and returns how many chunks were
         * created so callers can confirm the knowledge base was loaded.
         */
        int loadPath(const std::string& path, size_t chunkWords = 256,
                size_t chunkOverlap = 24);

        /**
         * Adds one in-memory document to the index.
         *
         * This is useful for runtime-generated knowledge such as FAQs, user data,
         * or temporary instructions that should be searchable without storing files.
         */
        void addDocument(const std::string& source, const std::string& text);

        /** Enables semantic/hybrid retrieval using a local llama.cpp embedding GGUF model. */
        void loadEmbeddingModel(const std::string& modelPath);

        /** Disables semantic retrieval and keeps the BM25 lexical index. */
        void clearEmbeddingModel();

        /** Replaces the default English stopwords; an empty list disables stopword filtering. */
        void setStopWords(const std::vector<std::string>& stopWords);

        /** Restores the built-in English stopword list. */
        void resetStopWords();

        /**
         * Finds the most relevant chunks for a query.
         *
         * It normalizes query terms, computes a BM25 relevance score with phrase boosts,
         * suppresses near-duplicate results, and limits output to topK items.
         */
        std::vector<SearchResult> search(const std::string& query, int topK) const;

        /**
         * Builds a grounded prompt for the model.
         *
         * The prompt includes only the strongest retrieved chunks and tells the LLM to use
         * that context, or say it does not know if the context lacks an answer. Callers may
         * separately opt into a labeled general-background section.
         */
        std::string buildPrompt(const std::string& query, int topK, int maxContextCharacters,
                bool allowGeneralKnowledgeWhenNoMatch = false,
                AnswerMode answerMode = AnswerMode::DOCUMENT_ONLY) const;

        /** Returns the number of indexed chunks. */
        size_t size() const;

    private:
        struct Chunk {
            std::string source;
            std::string text;
            std::unordered_map<std::string, int> terms;
            std::vector<float> embedding;
        };

        class EmbeddingModel;
        std::vector<Chunk> _chunks;
        std::unordered_map<std::string, int> _documentFrequency;
        size_t _chunkWords = 256;
        size_t _chunkOverlap = 24;
        std::unique_ptr<EmbeddingModel> _embeddingModel;
        bool _useDefaultStopWords = true;
        std::unordered_set<std::string> _customStopWords;

        void rebuildTermIndex();
    };

    /** Compatibility helper that loads a path into the process-local setup index. */
    void setUp(const std::string& loc);
}
