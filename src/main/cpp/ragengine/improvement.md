# RAG Search Improvements

This note records the search-path changes made in `rag.cpp`. General setup and API usage are documented in [`rag.md`](rag.md).

## Improvements

- **Cached inverse document frequency (IDF):** Search now calculates the query-term IDF values before scoring chunks instead of repeating the logarithm calculation for every matching chunk. This reduces redundant math as the index grows.
- **Partial sorting for limited results:** When more chunks match than `topK`, `std::partial_sort` orders only the requested top results before the rest are discarded. When all matches fit within `topK`, the complete result list is sorted as before.
- **Preserved query behavior:** Repeated query terms still contribute repeatedly to a score. Returned matches remain ordered by descending score, and invalid `topK` values or queries with no tokens still return an empty list.
- **Documented non-obvious choices:** Comments in the search implementation explain the cached weights and partial-sort selection.
- **Optional no-match fallback:** `MainFendAI.sendMessage(message, allowGeneralKnowledgeWhenNoMatch = true)` sends the original question when retrieval returns no chunks. The option is off by default; an enabled fallback may use the model's pretrained knowledge rather than local sources.

## Validation

The native source compiled with C++17 and strict warnings (`-Wall -Wextra -Werror`). A small native harness verified that:

- The highest-scoring chunk is returned for `topK = 1`.
- Repeated query terms preserve their scoring contribution.
- Queries with no matching terms return no results.
- A non-positive `topK` returns no results.

The editor diagnostics check reported no errors for `rag.cpp`.

## Scope

These changes are limited to the RAG implementation and its Kotlin/JNI entry points in the `fendai` module. No `llama.cpp` source was changed. No whole-project benchmark or audit was performed; performance should be measured with representative knowledge-base sizes and queries before making broader optimization claims.
