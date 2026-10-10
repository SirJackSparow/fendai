package com.dg.fendai.core

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Offline document retrieval for grounding a [LlamaEngine] prompt. */
class RagEngine : AutoCloseable {
    private var nativeHandle = create()

    enum class AnswerMode(internal val nativeValue: Int) {
        DOCUMENT_ONLY(0),
        DOCUMENTS_AND_MODEL_BACKGROUND(1),
    }

    data class ChunkingOptions(
        val chunkWords: Int = 256,
        val chunkOverlap: Int = 24,
    ) {
        init {
            require(chunkWords > 0) { "chunkWords must be positive" }
            require(chunkOverlap in 0 until chunkWords) {
                "chunkOverlap must be non-negative and smaller than chunkWords"
            }
        }
    }

    companion object {
        @Volatile
        private var pdfBoxInitialized = false

        @Synchronized
        private fun initializePdfBox(context: Context) {
            if (!pdfBoxInitialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                pdfBoxInitialized = true
            }
        }
    }

    /** Removes every indexed chunk. */
    fun clear() {
        checkOpen()
        clear(nativeHandle)
    }

    /** Loads text files recursively, or one file. Returns chunk count. */
    fun load(path: String, chunkingOptions: ChunkingOptions = ChunkingOptions()): Int =
        load(path, null, chunkingOptions)

    /**
     * Loads text files and text-based PDFs. An Android context is required only if [path]
     * contains a PDF.
     */
    fun load(
        path: String,
        context: Context?,
        chunkingOptions: ChunkingOptions = ChunkingOptions(),
    ): Int {
        checkOpen()
        val root = File(path)
        require(root.exists() && (root.isFile || root.isDirectory)) {
            "Knowledge path is not a file or directory: $path"
        }

        val pdfFiles = when {
            root.isFile && root.extension.equals("pdf", ignoreCase = true) -> listOf(root)
            root.isDirectory -> root.walkTopDown()
                .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                .toList()
            else -> emptyList()
        }
        if (pdfFiles.isNotEmpty()) {
            initializePdfBox(requireNotNull(context) {
                "Android Context is required to index PDF files"
            })
        }

        loadPath(nativeHandle, path, chunkingOptions.chunkWords, chunkingOptions.chunkOverlap)
        pdfFiles.forEach { pdf ->
            val text = PDDocument.load(pdf).use { document ->
                PDFTextStripper().getText(document)
            }
            require(text.isNotBlank()) {
                "PDF contains no extractable text (scanned PDFs require OCR): ${pdf.name}"
            }
            addDocument(pdf.absolutePath, text)
        }
        return chunkCount
    }

    /** Adds an in-memory document. */
    fun addDocument(source: String, text: String) {
        checkOpen()
        require(source.isNotBlank()) { "source must not be blank" }
        require(text.isNotBlank()) { "text must not be blank" }
        addDocument(nativeHandle, source, text)
    }

    /**
     * Enables hybrid BM25 and semantic retrieval using a local llama.cpp embedding GGUF model.
     * The embedding model is separate from the chat model and is loaded into memory by this engine.
     */
    fun loadEmbeddingModel(modelPath: String) {
        checkOpen()
        require(File(modelPath).isFile) { "Embedding model file does not exist: $modelPath" }
        loadEmbeddingModel(nativeHandle, modelPath)
    }

    /** Disables semantic retrieval while preserving the lexical BM25 index. */
    fun clearEmbeddingModel() {
        checkOpen()
        clearEmbeddingModel(nativeHandle)
    }

    /**
     * Uses a caller-provided stopword list for both indexing and queries. Pass an empty set to
     * disable stopword filtering. Existing indexed term statistics are rebuilt immediately.
     */
    fun setStopWords(stopWords: Set<String>) {
        checkOpen()
        require(stopWords.none { it.isBlank() }) { "stopWords must not contain blank entries" }
        setStopWords(nativeHandle, stopWords.toTypedArray())
    }

    /** Restores the built-in English stopword list. */
    fun resetStopWords() {
        checkOpen()
        resetStopWords(nativeHandle)
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
        buildPrompt(query, topK, maxContextCharacters, false, AnswerMode.DOCUMENT_ONLY)

    /** Builds a prompt using document-only grounding or a separate general-background section. */
    fun buildPrompt(
        query: String,
        topK: Int,
        maxContextCharacters: Int,
        answerMode: AnswerMode,
    ): String = buildPrompt(query, topK, maxContextCharacters, false, answerMode)

    /**
     * Returns the original query instead of a grounded prompt when fallback is enabled
     * and no chunks match.
     */
    fun buildPrompt(
        query: String,
        topK: Int,
        maxContextCharacters: Int,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
    ): String = buildPrompt(
        query,
        topK,
        maxContextCharacters,
        allowGeneralKnowledgeWhenNoMatch,
        AnswerMode.DOCUMENT_ONLY,
    )

    /** Builds a prompt with explicit control of no-match fallback and answer source policy. */
    fun buildPrompt(
        query: String,
        topK: Int,
        maxContextCharacters: Int,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
        answerMode: AnswerMode,
    ): String {
        checkOpen()
        require(query.isNotBlank()) { "query must not be blank" }
        require(maxContextCharacters > 0) { "maxContextCharacters must be positive" }
        return buildPrompt(
            nativeHandle,
            query,
            topK,
            maxContextCharacters,
            allowGeneralKnowledgeWhenNoMatch,
            answerMode.nativeValue,
        )
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
    private external fun loadPath(handle: Long, path: String, chunkWords: Int, chunkOverlap: Int): Int
    private external fun loadEmbeddingModel(handle: Long, modelPath: String)
    private external fun clearEmbeddingModel(handle: Long)
    private external fun setStopWords(handle: Long, stopWords: Array<String>)
    private external fun resetStopWords(handle: Long)
    private external fun addDocument(handle: Long, source: String, text: String)
    private external fun search(handle: Long, query: String, topK: Int): Array<String>
    private external fun buildPrompt(
        handle: Long,
        query: String,
        topK: Int,
        maxContextCharacters: Int,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
        answerMode: Int,
    ): String
    private external fun size(handle: Long): Int
}