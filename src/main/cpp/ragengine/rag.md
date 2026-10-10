
# Fendai RAG engine

`RagEngine` is an offline retrieval layer for `LlamaEngine`. It loads local text files and
text-based PDF files, splits them into overlapping chunks, and builds a grounded prompt for
llama.cpp. It uses BM25 lexical retrieval by default. Apps can optionally load a separate local
GGUF embedding model to enable hybrid BM25 and semantic retrieval.

## Android usage

Create the retriever after the Fendai native library has been loaded (creating a
`LlamaEngine` first is the simplest approach), then load a file or directory:

```kotlin
val llama = LlamaEngine()
llama.load(modelPath)

RagEngine().use { rag ->
		val chunks = rag.load(
				filesDir.resolve("knowledge").absolutePath,
				context,
				RagEngine.ChunkingOptions(chunkWords = 256, chunkOverlap = 24),
		)
		check(chunks > 0) { "No readable documents were found" }

		val question = "How do I reset the application?"
		val prompt = rag.buildPrompt(
				query = question,
				topK = 4,
				maxContextCharacters = 12_000,
		)
		val answer = llama.getResponse(prompt)
}
```

The directory is searched recursively. Text files are read as UTF-8-compatible text. PDFs are
parsed for embedded text using PDFBox Android and require an Android `Context`; scanned/image-only
PDFs are not OCRed and fail with an explanatory error. The source path is kept in each result so
the model can identify where context came from. Pass an Android `Context` to `RagEngine.load()`
when the file or directory contains PDFs. `MainFendAI.loadKnowledge` also has a context overload:

```kotlin
MainFendAI.loadKnowledge(
        pdfFile.absolutePath,
        context,
        onSuccess = { chunks -> MainFendAI.setRagEnabled(true) },
        onError = { e -> Log.e("RAG", "index failed", e) },
)
```
For content that is already in memory, use:

```kotlin
rag.addDocument("faq", "The reset action is available from Settings > Storage.")
val matches = rag.search("where is reset", topK = 3)
```

`ChunkingOptions` lets the caller control the maximum words per chunk and the number of
overlapping words retained between adjacent chunks. Defaults are 256 words and 24 overlapping
words; overlap must be non-negative and smaller than the chunk size. The same options apply to
text files, extracted PDF text, and long documents passed to `addDocument()`. For a directory
containing only text files, pass options without a context:

```kotlin
MainFendAI.loadKnowledge(
        knowledgeDirectory.absolutePath,
        RagEngine.ChunkingOptions(chunkWords = 400, chunkOverlap = 40),
        onSuccess = { chunks -> Log.d("RAG", "indexed $chunks chunks") },
        onError = { e -> Log.e("RAG", "index failed", e) },
)
```

Stopword filtering uses a built-in English list by default. For another language, configure the
language-specific words that should be ignored for both indexed documents and queries:

```kotlin
MainFendAI.setRagStopWords(setOf("el", "la", "los", "las", "de", "y"))
```

Pass an empty set to disable stopword filtering, or call `resetRagStopWords()` to restore the
built-in English list. Changing the list rebuilds the term index and should be done off the main
thread. FendAI does not automatically detect document language. Tokenization preserves UTF-8 bytes
and splits on whitespace and punctuation, which supports many space-delimited languages. It does
not perform Unicode case folding or word segmentation for languages such as Chinese or Japanese.

`search()` returns `RagEngine.Result(source, score, text)`. `buildPrompt()` formats the
highest scoring chunks and instructs the model to say it does not know when the context does
not contain an answer. The default `DOCUMENT_ONLY` answer mode remains strict. The optional
`DOCUMENTS_AND_MODEL_BACKGROUND` mode requests separate document-answer and general-background
sections. The final prompt should be passed directly to `LlamaEngine` rather than added to chat
history as a separate assistant message.

Note that `LLMInference::startCompletion()` records whatever string it receives as the user turn.
Passing a `buildPrompt()` result therefore stores the grounded prompt in chat history, not the
bare question. Sending the context as a separate system or assistant message instead would record
the retrieved text as a prior turn.

## Using RAG through `MainFendAI`

Most callers should not touch `com.dg.fendai.core.RagEngine` directly. `MainFendAI` owns one
retriever and applies grounding automatically:

```kotlin
MainFendAI.loadKnowledge(
        context.filesDir.resolve("knowledge").absolutePath,
        onSuccess = { chunks -> MainFendAI.setRagEnabled(true) },
        onError = { e -> Log.e("RAG", "index failed", e) })

val answer = MainFendAI.sendMessage("How do I reset the application?")

// Opt in for this turn only: let the model use general knowledge if no chunks match.
val generalAnswer = MainFendAI.sendMessage(
	"What is the capital of France?",
	allowGeneralKnowledgeWhenNoMatch = true,
)

// Opt in to separate document evidence from model background.
val mixedAnswer = MainFendAI.sendMessage(
	"What does the handbook say about refunds, and what is the general industry practice?",
	RagEngine.AnswerMode.DOCUMENTS_AND_MODEL_BACKGROUND,
)
```

Grounding is off until `setRagEnabled(true)` is called, and the index survives toggling. While on,
`sendMessage` builds its prompt from the index and falls back to the plain question when nothing is
indexed or retrieval fails, so a knowledge-base problem never blocks a conversation. When the index
is enabled and has no matching chunks, the default is a strict context-only prompt. Passing
`allowGeneralKnowledgeWhenNoMatch = true` sends the original question unchanged for that turn;
the resulting answer may use the model's pretrained knowledge and is not grounded in local sources.
That legacy no-match option takes precedence over the mixed answer mode when no passages match.
Matches remain grounded even when the option is enabled. Use
`searchKnowledge` to inspect what would be retrieved, and `releaseKnowledge` to free the native
index.

To enable semantic retrieval, supply a local GGUF model designed for text embeddings. This is a
separate model from the chat model and remains loaded in memory alongside it:

```kotlin
MainFendAI.setRagEmbeddingModel(
		filesDir.resolve("models/embedding-model.gguf").absolutePath,
		onSuccess = { Log.d("RAG", "Hybrid retrieval enabled") },
		onError = { e -> Log.e("RAG", "Embedding model load failed", e) },
)
```

The engine computes embeddings for already indexed chunks when configured and for later additions.
Calling `clearRagEmbeddingModel()` releases it and leaves the lexical index usable. Embedding
inference currently uses llama.cpp's mean pooling on CPU and encodes at most 512 model tokens per
chunk. Model load or embedding errors are reported; they do not silently switch to lexical search.
Use a model compatible with mean-pooled sentence embeddings and the languages in your documents.

`MainFendAI` decides the grounding fallback and keeps the engine behind a lock; the tests for that
decision live in `RagGroundingTest`.

## Function commands

These are the complete commands exposed by `RagEngine`:

| Kotlin command | Native function | Purpose |
| --- | --- | --- |
| `rag.clear()` | `RagEngine::clear()` | Remove all chunks and term statistics. |
| `rag.load(path, chunkingOptions)` | `RagEngine::loadPath(path, chunkWords, chunkOverlap)` | Clear the index, load one file or recursively load a directory with configurable word chunking, and return the chunk count. |
| `rag.addDocument(source, text)` | `RagEngine::addDocument(source, text)` | Add an in-memory document. |
| `rag.loadEmbeddingModel(path)` | `RagEngine::loadEmbeddingModel(path)` | Enable hybrid BM25 and semantic retrieval with a local embedding GGUF. |
| `rag.clearEmbeddingModel()` | `RagEngine::clearEmbeddingModel()` | Release the embedding model and retain BM25 retrieval. |
| `rag.setStopWords(words)` | `RagEngine::setStopWords(words)` | Replace the default stopword list; an empty list disables filtering. |
| `rag.resetStopWords()` | `RagEngine::resetStopWords()` | Restore the built-in English stopword list. |
| `rag.search(query, topK)` | `RagEngine::search(query, topK)` | Return the best matching chunks and scores. |
| `rag.buildPrompt(query, topK, maxContextCharacters, answerMode)` | `RagEngine::buildPrompt(query, topK, maxContextCharacters, answerMode)` | Create a document-only or mixed-background prompt. |
| `rag.chunkCount` | `RagEngine::size()` | Read the number of indexed chunks. |
| `rag.close()` | `RagEngine` destructor | Release native memory. |

Example command sequence:

```kotlin
RagEngine().use { rag ->
	rag.clear()
	val count = rag.load("/sdcard/Documents/knowledge")
	println("Indexed chunks: $count")

	rag.addDocument("runtime", "The current build is 1.0.0-alpha01.")
	val results = rag.search("current build", topK = 2)
	val prompt = rag.buildPrompt("What is the current build?", topK = 2, maxContextCharacters = 4000)
}
```

The internal `tokenize()` and `addChunks()` functions are implementation helpers and are not
commands. The standalone `fendai::rag::setUp(path)` function is retained for C++ compatibility;
new Android code should use `RagEngine` so its lifetime and index are explicit.

## Retrieval behavior

- Chunks default to up to 256 normalized words with a 24-word overlap; callers can override both
  values through `ChunkingOptions`.
- ASCII terms are lowercased; UTF-8 bytes are preserved. English stopwords are ignored by default
	and can be replaced with a language-specific list or disabled with an empty set. Simple suffix
	normalization matches common English forms such as `assessment`, `assessments`, and `assessing`.
- Results use BM25 scoring, which weights distinctive query terms and normalizes for chunk length.
- Exact adjacent query phrases receive a ranking boost, and near-identical chunks are suppressed.
- When a local embedding model is configured, normalized cosine similarity is blended with BM25
  (55% semantic, 45% lexical). Semantic-only candidates need cosine similarity of at least 0.25;
  without an embedding model, retrieval remains BM25-only.
- `topK` limits the number of retrieved chunks; `maxContextCharacters` limits prompt size.
- Loading a new path clears the existing index. Call `addDocument()` after `load()` to add
	in-memory material to the same index.
- Citation labels in generated answers are checked against the numbered passages actually included
	in the prompt. This verifies that a citation points to a retrieved passage, not that the passage
	logically supports the claim; model-based claims still need human review.

## Limitations and next step

Hybrid retrieval can improve matching across paraphrases, but quality depends on the selected
embedding model and costs additional memory and CPU time on-device. Model prompts cannot guarantee
that document facts and pretrained knowledge are perfectly separated; the mixed mode labels the
sections and programmatically checks citation references, but does not prove factual support or
identify where the model learned general background.
