# RAG Improvements

This note summarizes the retrieval and answer-generation improvements implemented in the FendAI
RAG path. General setup and API usage are documented in [`rag.md`](rag.md).

## Retrieval

- **BM25 lexical search remains the default.** Query terms are normalized and common stopwords are
  removed; BM25 weights distinctive terms and accounts for chunk length. Adjacent query phrases
  receive a boost, and near-duplicate chunks are suppressed.
- **Configurable stopword filtering:** English stopwords remain the default for compatibility.
  Developers can provide a language-specific list with `MainFendAI.setRagStopWords()`, pass an
  empty set to disable filtering, or restore the built-in English list with
  `MainFendAI.resetRagStopWords()`. Reconfiguring the list rebuilds indexed term statistics so
  document and query tokens use the same filtering. Tokenization preserves UTF-8 bytes for
  whitespace-delimited text, but does not provide Unicode case folding or CJK word segmentation.
- **Optional hybrid semantic search:** Developers can load a local llama.cpp-compatible embedding
  GGUF with `MainFendAI.setRagEmbeddingModel()`. FendAI computes normalized embeddings for indexed
  chunks and queries, then blends cosine similarity (55%) with normalized BM25 scores (45%).
  Semantic-only candidates must have a cosine similarity of at least 0.25. Without an embedding
  model, FendAI uses BM25 alone.
- **Local model lifecycle:** The embedding model is separate from the chat model, loaded on CPU,
  and retained by the RAG engine. It uses mean pooling and limits each encoded input to 512 tokens.
  Loading errors are reported rather than silently falling back to lexical-only search. Calling
  `clearRagEmbeddingModel()` releases the embedding model but preserves the BM25 index.

## Answer modes and citation checks

- **Document-only remains the default.** The prompt asks the model to answer from retrieved
  passages, state when they do not provide an answer, and cite numbered source passages.
- **Optional mixed-background mode:** Pass
  `RagEngine.AnswerMode.DOCUMENTS_AND_MODEL_BACKGROUND` to `MainFendAI.sendMessage()` to request
  separate document-answer and general-background sections. The document section must use
  retrieved passages; general model knowledge must be identified as background, not attributed to
  the documents.
- **Citation-reference validation:** After generation, FendAI checks citation numbers against the
  numbered passages included in the prompt and appends a warning if citations are absent or refer
  to a non-retrieved passage.
- **Limits:** Prompt instructions cannot guarantee that a model follows the requested sections or
  perfectly separates its training knowledge from document facts. Citation validation confirms
  only that a citation number refers to a supplied passage; it does not verify that the passage
  supports the claim. Review generated answers where factual accuracy is important.
- **No-match behavior:** Document-only mode reports when the context lacks an answer. The existing
  opt-in `allowGeneralKnowledgeWhenNoMatch` behavior still sends the original question unchanged
  if there are no matching chunks; it takes precedence over the mixed mode in that case.

## Validation

The implementation was validated with:

- `./gradlew :fendai:compileDebugKotlin :fendai:externalNativeBuildDebug :fendai:testDebugUnitTest :app:compileDebugKotlin`
- Native builds for the configured Android ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- Unit tests for citation-reference validation, alongside the FendAI unit-test suite.
- `git diff --check`.

The embedding path compiles for the configured ABIs, but was not exercised end-to-end with a
specific embedding GGUF on an Android device. Retrieval quality, model compatibility, latency, and
memory use should be measured with the embedding model and knowledge base selected by the app.

## Scope

Changes cover the native RAG engine, JNI bridge, Kotlin APIs, manager integration, tests, and RAG
documentation in the `fendai` module. The implementation uses llama.cpp's existing embedding APIs;
it does not add or modify llama.cpp sources. No whole-project performance benchmark or factual
support verifier was added.
