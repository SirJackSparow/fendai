//
// Created by Bima Maulana Effendy on 09/10/25.
//

#include "LLMInference.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <iostream>
#include <utility>

#define TAG "[Fendai-Cpp]"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void llamaAndroidLog(ggml_log_level level, const char * text, void *) {
    const android_LogPriority priority = level >= GGML_LOG_LEVEL_ERROR
            ? ANDROID_LOG_ERROR
            : level == GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
    __android_log_print(priority, TAG, "%s", text);
}

static void freeMessages(std::vector<llama_chat_message>& messages) {
    for (const auto& message : messages) {
        free(const_cast<char*>(message.role));
        free(const_cast<char*>(message.content));
    }
    messages.clear();
}


//this is must order otherwise will error
//// 1. PENALTIES (Optional - if you want to prevent repetition)
//llama_sampler_chain_add(sampler, llama_sampler_init_penalties(...));  // repetition, frequency, presence
//
//// 2. DRY (Optional - Dynamic Repetition Penalty)
//llama_sampler_chain_add(sampler, llama_sampler_init_dry(...));
//
//// 3. TOP-K (Optional - keeps only top K tokens)
//llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
//
//// 4. TAIL-FREE (Optional - TFS sampling)
//llama_sampler_chain_add(sampler, llama_sampler_init_tail_free(...));
//
//// 5. TYPICAL (Optional - Typical sampling)
//llama_sampler_chain_add(sampler, llama_sampler_init_typical(...));
//
//// 6. TOP-P / NUCLEUS (Optional - cumulative probability threshold)
//llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
//
//// 7. MIN-P (Optional - minimum probability threshold)
//llama_sampler_chain_add(sampler, llama_sampler_init_min_p(minP, 1));
//
//// 8. XTC (Optional - Exclude Top Choices)
//llama_sampler_chain_add(sampler, llama_sampler_init_xtc(...));
//
//// 9. TEMPERATURE (REQUIRED - adjusts randomness)
//llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
//
//// 10. GRAMMAR (Optional - for structured output like JSON)
//llama_sampler_chain_add(sampler, llama_sampler_init_grammar(...));
//
//// 11. MIROSTAT (Alternative to dist - adaptive sampling)
//// OR
//llama_sampler_chain_add(sampler, llama_sampler_init_mirostat(...));
//
//// 12. DIST (REQUIRED - ALWAYS LAST - final token selection)
//llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed));

void
LLMInference::loadModel(const char *model_path, float minP, float temperature, bool storeChats,
        long contextSize,
        const char *chatTemplate, int nThreads, bool useMmap, bool useMlock,
        int topK, float topP, bool usePenalties, int maxResponseTokens,
        int nGpuLayers) {
    _stopGeneration.store(false);
    llama_log_set(llamaAndroidLog, nullptr);

    // load dynamic backends
    ggml_backend_load_all();

    // Log available backends
    size_t n_backends = ggml_backend_reg_count();
    LOGi("Available backends: %zu", n_backends);
    for (size_t i = 0; i < n_backends; ++i) {
        ggml_backend_reg_t reg = ggml_backend_reg_get(i);
        LOGi("  Backend %zu: %s", i, ggml_backend_reg_name(reg));
    }

    // create an instance of llama_model
    llama_model_params model_params = llama_model_default_params();
    ggml_backend_dev_t gpuDevice = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU);
    if (gpuDevice == nullptr) {
        gpuDevice = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_IGPU);
    }
    ggml_backend_dev_t cpuDevice = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
    ggml_backend_dev_t cpuDevices[] = {cpuDevice, nullptr};

    if (useMmap && useMlock) {
        model_params.load_mode = LLAMA_LOAD_MODE_MMAP_MLOCK;
    } else if (useMmap) {
        model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    } else if (useMlock) {
        model_params.load_mode = LLAMA_LOAD_MODE_MLOCK;
    } else {
        model_params.load_mode = LLAMA_LOAD_MODE_NONE;
    }
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_batch = std::min<long>(contextSize, 256);
    ctx_params.n_threads = nThreads;
    ctx_params.no_perf = true; // disable performance metrics

    const bool requestGpu = nGpuLayers != 0 && gpuDevice != nullptr;
    bool loaded = false;
    if (requestGpu) {
        ggml_backend_dev_t gpuDevices[] = {gpuDevice, nullptr};
        llama_model_params gpuParams = model_params;
        gpuParams.devices = gpuDevices;
        gpuParams.n_gpu_layers = nGpuLayers;
        LOGi("Trying GPU backend %s with %s layers offloaded",
             ggml_backend_dev_name(gpuDevice),
             nGpuLayers < 0 ? "all" : std::to_string(nGpuLayers).c_str());

        _model = llama_model_load_from_file(model_path, gpuParams);
        if (_model != nullptr) {
            _ctx = llama_init_from_model(_model, ctx_params);
            loaded = _ctx != nullptr;
            if (!loaded) {
                llama_model_free(_model);
                _model = nullptr;
            }
        }

        if (!loaded) {
            LOGe("GPU model initialization failed; retrying with CPU");
        }
    } else if (nGpuLayers == 0) {
        LOGi("GPU offload disabled; using CPU");
    } else {
        LOGi("No GPU backend available; using CPU");
    }

    if (!loaded) {
        model_params.n_gpu_layers = 0;
        model_params.devices = cpuDevice != nullptr ? cpuDevices : nullptr;
        _model = llama_model_load_from_file(model_path, model_params);
        if (_model == nullptr) {
            LOGe("failed to load model from %s using CPU", model_path);
            throw std::runtime_error("Model load failed on GPU and CPU");
        }

        _ctx = llama_init_from_model(_model, ctx_params);
        if (_ctx == nullptr) {
            llama_model_free(_model);
            _model = nullptr;
            LOGe("llama context initialization failed using CPU");
            throw std::runtime_error("Model context initialization failed on CPU");
        }
    }

    // create an instance of llama_sampler
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true; // disable performance metrics
    _chain = llama_sampler_chain_init(sampler_params);

    struct llama_sampler *penalty_sampler = llama_sampler_init_penalties(
            /* n_vocab           */ llama_vocab_n_tokens(llama_model_get_vocab(_model)),
            /* penalty_last_n    */ 64,
            /* penalty_repeat    */ 1.2f,
            /* penalty_freq      */ 0.0f,
            /* penalty_present   */ 0.0f
    );

    if (usePenalties) {
        llama_sampler_chain_add(
                _chain,
                penalty_sampler
        );
    }

    if (topK > 0) {
        llama_sampler_chain_add(_chain, llama_sampler_init_top_k(topK));
    }
    if (minP >= 0.01f) {
        llama_sampler_chain_add(_chain, llama_sampler_init_min_p(minP, 1));
    }

    if (topP <= 0.99) {
        llama_sampler_chain_add(_chain, llama_sampler_init_top_p(topP, 1));
    }
    llama_sampler_chain_add(_chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(_chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));


    _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    _messages.clear();
    _maxResponseTokens = maxResponseTokens;
    _configuredMaxResponseTokens = maxResponseTokens;

    if (chatTemplate == nullptr) {
        _chatTemplate = llama_model_chat_template(_model, nullptr);
    } else {
        _chatTemplate = strdup(chatTemplate);
    }
    this->_storeChats = storeChats;
}

void
LLMInference::addChatMessage(const char *message, const char *role) {
    _messages.push_back({strdup(role), strdup(message)});
}

float
LLMInference::getResponseGenerationTime() const {
    return (float) _responseNumTokens / (_responseGenerationTime / 1e6);
}

int
LLMInference::getContextSizeUsed() const {
    return _nCtxUsed;
}

void
LLMInference::startCompletion(const char *query) {
    _imageMode = false;                        // ← ADD

    if (!_storeChats) {
        _prevLen = 0;
        _formattedMessages.clear();
        _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    }
    _responseGenerationTime = 0;
    _responseNumTokens = 0;

    //neue
    if (_storeChats) {
        manageContext();
    }

    addChatMessage(query, "user");
    // apply the chat-template
    int newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
            _formattedMessages.data(), _formattedMessages.size());
    if (newLen > (int) _formattedMessages.size()) {
        // resize the output buffer `_formattedMessages`
        // and re-apply the chat template
        _formattedMessages.resize(newLen);
        newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
                _formattedMessages.data(), _formattedMessages.size());
    }
    if (newLen < 0) {
        throw std::runtime_error(
                "llama_chat_apply_template() in LLMInference::startCompletion() failed");
    }
    std::string prompt(_formattedMessages.begin() + _prevLen, _formattedMessages.begin() + newLen);
    _promptTokens = common_tokenize(llama_model_get_vocab(_model), prompt, true, true);

    const uint32_t contextSize = llama_n_ctx(_ctx);
    uint32_t usedTokens = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;
    const int configuredResponseTokens = std::max(1, _configuredMaxResponseTokens);
    const size_t responseReserve = static_cast<size_t>(
            std::min(configuredResponseTokens, static_cast<int>(contextSize / 4)));
    if (_storeChats &&
            static_cast<size_t>(usedTokens) + _promptTokens.size() + responseReserve > contextSize) {
        std::vector<llama_chat_message> retainedMessages;
        const bool hasSystemMessage = !_messages.empty() &&
                std::string(_messages.front().role) == "system";
        if (hasSystemMessage) retainedMessages.push_back(_messages.front());
        retainedMessages.push_back(_messages.back());

        for (size_t index = 0; index < _messages.size(); ++index) {
            if ((hasSystemMessage && index == 0) || index == _messages.size() - 1) continue;
            free(const_cast<char*>(_messages[index].role));
            free(const_cast<char*>(_messages[index].content));
        }
        _messages = std::move(retainedMessages);

        llama_memory_clear(llama_get_memory(_ctx), true);
        _nCtxUsed = 0;
        _prevLen = 0;

        newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
                _formattedMessages.data(), _formattedMessages.size());
        if (newLen > static_cast<int>(_formattedMessages.size())) {
            _formattedMessages.resize(newLen);
            newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(),
                    true, _formattedMessages.data(), _formattedMessages.size());
        }
        if (newLen < 0) {
            throw std::runtime_error(
                    "llama_chat_apply_template() in LLMInference::startCompletion() failed");
        }
        prompt.assign(_formattedMessages.begin(), _formattedMessages.begin() + newLen);
        _promptTokens = common_tokenize(llama_model_get_vocab(_model), prompt, true, true);
        usedTokens = 0;
    }

    const size_t availableResponseTokens = contextSize > usedTokens + _promptTokens.size()
            ? contextSize - usedTokens - _promptTokens.size()
            : 0;
    if (availableResponseTokens == 0) {
        throw std::runtime_error(
                "Prompt exceeds the model context window; reduce the input or retrieved RAG context");
    }
    _maxResponseTokens = static_cast<int>(std::min(
            static_cast<size_t>(configuredResponseTokens), availableResponseTokens));

    // create a llama_batch containing a single sequence
    // see llama_batch_init for more details
    _batch = new llama_batch();
    _batch->token = _promptTokens.data();
    _batch->n_tokens = _promptTokens.size();

    // A prompt wider than n_batch cannot be decoded in one llama_decode() call, so decode it in
    // slices here. Token positions must keep advancing across slices, otherwise attention sees the
    // later tokens as replacing the earlier ones.
    _nPromptDecoded = 0;
}


// ← ADD: new function — image + text (or image only if query is "")
void
LLMInference::startCompletionWithImage(const char *query,
        const unsigned char *imageBytes,
        int imageSize) {
    if (!_mtmdCtx) {
        LOGe("vision not enabled — call loadMmproj() first");
        throw std::runtime_error("loadMmproj() was not called");
    }

    _imageMode = true;
    _responseGenerationTime = 0;
    _responseNumTokens = 0;

    if (!_storeChats) {
        _prevLen = 0;
        _formattedMessages.clear();
        _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    }

    if (_storeChats) {
        manageContext();
    }

    // build user message with image marker
    std::string imageMarker = mtmd_default_marker();
    std::string userMessage;
    if (query != nullptr && strlen(query) > 0) {
        userMessage = std::string(query) + "\n" + imageMarker;
    } else {
        userMessage = imageMarker;
    }

    addChatMessage(userMessage.c_str(), "user");

    // apply chat template
    int newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
            _formattedMessages.data(), _formattedMessages.size());
    if (newLen > (int) _formattedMessages.size()) {
        _formattedMessages.resize(newLen);
        newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
                _formattedMessages.data(), _formattedMessages.size());
    }
    if (newLen < 0) {
        throw std::runtime_error(
                "llama_chat_apply_template() in startCompletionWithImage() failed");
    }

    std::string prompt(_formattedMessages.begin() + _prevLen,
            _formattedMessages.begin() + newLen);

    // decode image bytes — ctx required as first arg
    mtmd_bitmap *bitmap = mtmd_helper_bitmap_init_from_buf(
            _mtmdCtx,
            imageBytes,
            (size_t) imageSize,
            false,
            mtmd_helper_init_opt_default()
    ).bitmap;
    if (!bitmap) {
        LOGe("failed to decode image bytes");
        throw std::runtime_error("mtmd_helper_bitmap_init_from_buf() failed");
    }

    // tokenize: pre-allocate output, use mtmd_input_text struct
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    mtmd_input_text inputText = { prompt.c_str(), true, true };
    const mtmd_bitmap *bitmapsArr[] = { bitmap };
    int32_t tokResult = mtmd_tokenize(_mtmdCtx, chunks, &inputText, bitmapsArr, 1);
    if (tokResult != 0) {
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bitmap);
        throw std::runtime_error("mtmd_tokenize() failed code=" + std::to_string(tokResult));
    }

    // evaluate all chunks into context
    llama_pos nPast = 0;
    if (llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) >= 0) {
        nPast = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;
    }

    llama_pos newNPast = 0;
    int32_t evalResult = mtmd_helper_eval_chunks(
            _mtmdCtx,
            _ctx,
            chunks,
            nPast,
            0,       // seq_id
            512,     // n_batch
            true,    // logits_last
            &newNPast
    );
    if (evalResult != 0) {
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bitmap);
        throw std::runtime_error("mtmd_helper_eval_chunks() failed");
    }

    // tokens already evaluated — completionLoop will skip decode on first call
    _promptTokens.clear();
    _nPromptDecoded = 0;
    _batch = new llama_batch();
    _batch->n_tokens = 0;

    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bitmap);

    LOGi("image ready — starting completionLoop()");
}


// ← ADD: load mmproj once after loadModel()
void
LLMInference::loadMmproj(const char *mmprojPath) {
    LOGi("loading mmproj from %s", mmprojPath);
    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu = true;
    params.n_threads = 4;
    _mtmdCtx = mtmd_init_from_file(mmprojPath, _model, params);
    if (!_mtmdCtx) {
        LOGe("failed to load mmproj from %s", mmprojPath);
        throw std::runtime_error("mtmd_init_from_file() failed");
    }
    LOGi("mmproj loaded — vision enabled");
}


// ← ADD: free mmproj
void
LLMInference::freeMmproj() {
    if (_mtmdCtx) {
        mtmd_free(_mtmdCtx);
        _mtmdCtx = nullptr;
    }
}


// taken from:
// https://github.com/ggerganov/llama.cpp/blob/master/examples/llama.android/llama/src/main/cpp/llama-android.cpp#L38
bool
LLMInference::_isValidUtf8(const char *response) {
    if (!response) {
        return true;
    }
    const unsigned char *bytes = (const unsigned char *) response;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

std::string
LLMInference::completionLoop() {


    if (_stopGeneration.load()) {
        if (!_response.empty()) {
            addChatMessage(_response.c_str(), "assistant");
            _response.clear();
        }
        return "[EOG]";
    }

    // Force stop after max tokens
    if (_responseNumTokens >= _maxResponseTokens) {
        if (!_response.empty()) {
            addChatMessage(_response.c_str(), "assistant");
            _response.clear();
        }
        return "[EOG]";
    }

    // check if the length of the inputs to the model
    // have exceeded the context size of the model
    uint32_t contextSize = llama_n_ctx(_ctx);
    _nCtxUsed = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;

    // ← ADD: skip decode on first image call (already evaluated by mtmd)
    if (!_imageMode) {
        // One decode per call, never wider than n_batch: llama_decode() aborts the whole process
        // on a batch that exceeds it, and a RAG prompt reliably does. While the prompt is still
        // being drained we feed one slice and return without sampling; once it is fully decoded
        // the batch holds the single predicted token, as before.
        // _batch->pos stays null so llama.cpp continues positions from the KV cache itself.
        const int nBatch = (int) llama_n_batch(_ctx);
        const int nPrompt = (int) _promptTokens.size();
        if (_nPromptDecoded < nPrompt) {
            const int slice = std::min(nBatch, nPrompt - _nPromptDecoded);
            if (_nCtxUsed + slice > contextSize) {
                throw std::runtime_error("context size reached");
            }
            _batch->token = _promptTokens.data() + _nPromptDecoded;
            _batch->n_tokens = slice;
            if (llama_decode(_ctx, *_batch) < 0) {
                throw std::runtime_error("llama_decode() failed");
            }
            _nPromptDecoded += slice;
            return "";
        }
        if (_batch->n_tokens > 0) {
            if (_nCtxUsed + _batch->n_tokens > contextSize) {
                throw std::runtime_error("context size reached");
            }
            if (llama_decode(_ctx, *_batch) < 0) {
                throw std::runtime_error("llama_decode() failed");
            }
        }
    }
    _imageMode = false;                        // ← ADD: reset after first image call

    auto start = ggml_time_us();

    if (_stopGeneration.load()) {
        if (!_response.empty()) {
            addChatMessage(_response.c_str(), "assistant");
            _response.clear();
        }
        return "[EOG]";
    }

    // sample a token and check if it is an EOG (end of generation token)
    // convert the integer token to its corresponding word-piece
    _currToken = llama_sampler_sample(_chain, _ctx, -1);
    if (llama_vocab_is_eog(llama_model_get_vocab(_model), _currToken)) {
        addChatMessage(strdup(_response.data()), "assistant");
        _response.clear();
        return "[EOG]";
    }
    std::string piece = common_token_to_piece(_ctx, _currToken, true);

    auto end = ggml_time_us();
    _responseGenerationTime += (end - start);
    _responseNumTokens += 1;
    _cacheResponseTokens += piece;

    // re-init the batch with the newly predicted token
    // key, value pairs of all previous tokens have been cached
    // in the KV cache
    _batch->token = &_currToken;
    _batch->n_tokens = 1;
    _batch->pos = nullptr;

    if (_isValidUtf8(_cacheResponseTokens.c_str())) {
        _response += _cacheResponseTokens;
        std::string valid_utf8_piece = _cacheResponseTokens;
        _cacheResponseTokens.clear();
        return valid_utf8_piece;
    }

    return "";
}


void
LLMInference::stopCompletion() {
    if (_storeChats && !_response.empty()) {
        addChatMessage(_response.c_str(), "assistant");
    }
    _response.clear();
    _imageMode = false;                        // ← ADD
    _prevLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), false,
            nullptr, 0);
    if (_prevLen < 0) {
        throw std::runtime_error(
                "llama_chat_apply_template() in LLMInference::stopCompletion() failed");
    }
}

LLMInference::~LLMInference() {
    freeMessages(_messages);
    if (_mtmdCtx) mtmd_free(_mtmdCtx);        // ← ADD
    llama_free(_ctx);
    llama_model_free(_model);
    delete _batch;
    llama_sampler_free(_chain);
}

//new feature 30 January 2026

void LLMInference::clearChatHistory() {
    if (_ctx != nullptr) {
        llama_memory_t mem = llama_get_memory(_ctx);
        llama_memory_clear(mem, true);

        _nCtxUsed = 0;
        _prevLen = 0;
        _imageMode = false;                    // ← ADD
        freeMessages(_messages);
        _response.clear();
        _cacheResponseTokens.clear();
        _formattedMessages.clear();
        _promptTokens.clear();
        _responseGenerationTime = 0;
        _responseNumTokens = 0;
    }
}

void LLMInference::clearMemoryOnly() {
    if (_ctx != nullptr) {
        llama_memory_t mem = llama_get_memory(_ctx);
        llama_memory_clear(mem, true);
        _nCtxUsed = 0;
        _prevLen = 0;
        _imageMode = false;                    // ← ADD
    }
}

void LLMInference::removeLastMessages(int count) {
    if (count > 0 && !_messages.empty()) {
        int toRemove = std::min(count, (int)_messages.size());
        for (auto message = _messages.end() - toRemove; message != _messages.end(); ++message) {
            free(const_cast<char*>(message->role));
            free(const_cast<char*>(message->content));
        }
        _messages.erase(_messages.end() - toRemove, _messages.end());
    }
}

int LLMInference::getMessageCount() const {
    return (int)_messages.size();
}

void LLMInference::manageContext() {
    uint32_t contextSize = llama_n_ctx(_ctx);
    uint32_t usedTokens = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;

    if (usedTokens > (contextSize * 0.75)) {
        if (_messages.size() > 3) {
            size_t removeStart = 0;
            if (_messages.size() > 0 &&
                    std::string(_messages[0].role) == "system") {
                removeStart = 1;
            }

            if (_messages.size() > removeStart + 2) {
                for (size_t index = removeStart; index < removeStart + 2; ++index) {
                    free(const_cast<char*>(_messages[index].role));
                    free(const_cast<char*>(_messages[index].content));
                }
                _messages.erase(
                        _messages.begin() + removeStart,
                        _messages.begin() + removeStart + 2
                );
            }

            llama_memory_seq_rm(llama_get_memory(_ctx), -1, 0, -1);

            _prevLen = 0;
        }
    }
}

void LLMInference::getLogAllHistory() {
    for (size_t i = 0; i < _messages.size(); i++) {
        __android_log_print(ANDROID_LOG_DEBUG, "LLMInference",
                "Message %zu - Role: %s, Content: %s",
                i, _messages[i].role, _messages[i].content);
    }
}