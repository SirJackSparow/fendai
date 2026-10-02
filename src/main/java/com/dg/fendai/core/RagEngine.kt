package com.dg.fendai.core

/** Offline document retrieval for grounding a [LlamaEngine] prompt. */
class RagEngine : AutoCloseable {
    private var nativeHandle = create()

    /** Removes every indexed chunk. */
    fun clear() {
        checkOpen()
        clear(nativeHandle)
    }

    /** Loads one file or recursively loads all files in a directory. Returns chunk count. */
    fun load(path: String): Int {
        checkOpen()
        return loadPath(nativeHandle, path)
    }

    /** Adds an in-memory document. */
    fun addDocument(source: String, text: String) {
        checkOpen()
        require(source.isNotBlank()) { "source must not be blank" }
        require(text.isNotBlank()) { "text must not be blank" }
        addDocument(nativeHandle, source, text)
    }

    /** Returns source, score, and text for the best matching chunks. */
    fun search(query: String, topK: Int = 4): List<Result> {
        checkOpen()
        require(query.isNotBlank()) { "query must not be blank" }
        return search(nativeHandle, query, topK).mapNotNull { row ->
            val fields = row.split('\t', limit = 3)
            if (fields.size == 3) Result(fields[0], fields[1].toFloatOrNull() ?: 0f, fields[2]) else null
        }
    }

    /** Builds a grounded prompt ready for [LlamaEngine.getResponse]. */
    fun buildPrompt(query: String, topK: Int = 4, maxContextCharacters: Int = 12000): String =
        buildPrompt(query, topK, maxContextCharacters, allowGeneralKnowledgeWhenNoMatch = false)

    /**
     * Returns the original query instead of a grounded prompt when fallback is enabled
     * and no chunks match.
     */
    fun buildPrompt(
        query: String,
        topK: Int,
        maxContextCharacters: Int,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
    ): String {
        checkOpen()
        require(query.isNotBlank()) { "query must not be blank" }
        require(maxContextCharacters > 0) { "maxContextCharacters must be positive" }
        return buildPrompt(nativeHandle, query, topK, maxContextCharacters, allowGeneralKnowledgeWhenNoMatch)
    }

    val chunkCount: Int
        get() = size(nativeHandle)

    override fun close() {
        if (nativeHandle != 0L) {
            destroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private fun checkOpen() = check(nativeHandle != 0L) { "RagEngine is closed" }

    data class Result(val source: String, val score: Float, val text: String)

    private external fun create(): Long
    private external fun destroy(handle: Long)
    private external fun clear(handle: Long)
    private external fun loadPath(handle: Long, path: String): Int
    private external fun addDocument(handle: Long, source: String, text: String)
    private external fun search(handle: Long, query: String, topK: Int): Array<String>
    private external fun buildPrompt(
        handle: Long,
        query: String,
        topK: Int,
        maxContextCharacters: Int,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
    ): String
    private external fun size(handle: Long): Int
}