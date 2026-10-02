//
// Created by Bima Maulana Effendy on 18/09/26.
//

#pragma once

#include <string>
#include <vector>
#include <memory>
#include <unordered_map>

namespace fendai::rag{

    struct SearchResult {
        std::string source;
        std::string text;
        float score;
    };

    /**
     * Retrieval engine for local knowledge grounding.
     *
     * The engine keeps a lightweight index of text chunks. During a user question,
     * it tokenizes the query, scores the chunks using a TF-IDF-inspired method,
     * and then packages the best matching context into a prompt for the LLM.
     */
    class RagEngine {
    public:
        /** Removes all indexed chunks and term statistics. */
        void clear();

        /**
         * Loads a single file or a whole directory into the index.
         *
         * This is the main setup step: it clears the previous index, reads the source
         * documents from disk, splits them into chunks, and returns how many chunks were
         * created so callers can confirm the knowledge base was loaded.
         */
        int loadPath(const std::string& path);

        /**
         * Adds one in-memory document to the index.
         *
         * This is useful for runtime-generated knowledge such as FAQs, user data,
         * or temporary instructions that should be searchable without storing files.
         */
        void addDocument(const std::string& source, const std::string& text);

        /**
         * Finds the most relevant chunks for a query.
         *
         * It tokenizes the query, computes a relevance score for each chunk, then sorts
         * results from strongest match to weakest and limits them to topK items.
         */
        std::vector<SearchResult> search(const std::string& query, int topK) const;

        /**
         * Builds a grounded prompt for the model.
         *
         * The prompt includes only the strongest retrieved chunks and tells the LLM to use
         * that context, or say it does not know if the context lacks an answer. When enabled,
         * general-knowledge fallback returns the original query if no chunks match.
         */
        std::string buildPrompt(const std::string& query, int topK, int maxContextCharacters,
                bool allowGeneralKnowledgeWhenNoMatch = false) const;

        /** Returns the number of indexed chunks. */
        size_t size() const;

    private:
        struct Chunk {
            std::string source;
            std::string text;
            std::unordered_map<std::string, int> terms;
        };

        std::vector<Chunk> _chunks;
        std::unordered_map<std::string, int> _documentFrequency;
    };

    /** Compatibility helper that loads a path into the process-local setup index. */
    void setUp(const std::string& loc);
}
