
# Fendai RAG engine

`RagEngine` is an offline retrieval layer for `LlamaEngine`. It loads local text files,
splits them into overlapping chunks, ranks chunks with a TF-IDF-style lexical score, and
builds a grounded prompt for llama.cpp. No network, database, or separate embedding model
is required.

## Android usage

Create the retriever after the Fendai native library has been loaded (creating a
`LlamaEngine` first is the simplest approach), then load a file or directory:

```kotlin
val llama = LlamaEngine()
llama.load(modelPath)

RagEngine().use { rag ->
		val chunks = rag.load(filesDir.resolve("knowledge").absolutePath)
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

The directory is searched recursively. Every regular file is read as UTF-8-compatible text;
the source path is kept in each result so the model can identify where context came from.
For content that is already in memory, use:

```kotlin
rag.addDocument("faq", "The reset action is available from Settings > Storage.")
val matches = rag.search("where is reset", topK = 3)
```

`search()` returns `RagEngine.Result(source, score, text)`. `buildPrompt()` formats the
highest scoring chunks and instructs the model to say it does not know when the context does
not contain an answer. The final prompt should be passed directly to `LlamaEngine` rather
than added to chat history as a separate assistant message.

## Function commands

These are the complete commands exposed by `RagEngine`:

| Kotlin command | Native function | Purpose |
| --- | --- | --- |
| `rag.clear()` | `RagEngine::clear()` | Remove all chunks and term statistics. |
| `rag.load(path)` | `RagEngine::loadPath(path)` | Clear the index, load one file or recursively load a directory, and return the chunk count. |
| `rag.addDocument(source, text)` | `RagEngine::addDocument(source, text)` | Add an in-memory document. |
| `rag.search(query, topK)` | `RagEngine::search(query, topK)` | Return the best matching chunks and scores. |
| `rag.buildPrompt(query, topK, maxContextCharacters)` | `RagEngine::buildPrompt(query, topK, maxContextCharacters)` | Create a grounded prompt for `LlamaEngine`. |
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

- Chunks contain up to 180 normalized words with a 30-word overlap.
- Terms are lowercased and punctuation is ignored.
- Results are scored with term frequency and inverse document frequency.
- `topK` limits the number of retrieved chunks; `maxContextCharacters` limits prompt size.
- Loading a new path clears the existing index. Call `addDocument()` after `load()` to add
	in-memory material to the same index.

## Limitations and next step

This version is lexical retrieval, not vector or embedding retrieval. It works well when a
question shares words with the source documents and keeps the native library self-contained.
For semantic search, replace the scoring body in `rag.cpp` with embeddings from a GGUF
embedding model and store normalized vectors in `RagEngine::Chunk`; the Kotlin API and
prompt assembly can remain unchanged.
