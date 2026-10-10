package com.dg.fendai.core

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Offline document retrieval for grounding a [LlamaEngine] prompt. */
class RagEngine : AutoCloseable {
    private var nativeHandle = create()

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
    fun load(path: String): Int = load(path, null)

    /**
     * Loads text files and text-based PDFs. An Android context is required only if [path]
     * contains a PDF.
     */
    fun load(path: String, context: Context?): Int {
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

        loadPath(nativeHandle, path)
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