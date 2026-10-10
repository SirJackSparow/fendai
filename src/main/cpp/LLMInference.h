//
// Created by Bima Maulana Effendy on 09/10/25.
//
#pragma once
#include "llama.h"
#include "common.h"
#include "mtmd.h"
#include "mtmd-helper.h"                              // ← ADD
#include <string>
#include <vector>

class LLMInference {
    // llama.cpp-specific types
    llama_context* _ctx = nullptr;
    llama_model*   _model = nullptr;
    llama_sampler* _chain = nullptr;
    llama_token    _currToken;
    llama_batch*   _batch = nullptr;
    mtmd_context*  _mtmdCtx = nullptr;         // ← ADD

    // container to store user/assistant messages in the chat
    std::vector<llama_chat_message> _messages;
    // stores the string generated after applying
    // the chat-template to all messages in `_messages`
    std::vector<char> _formattedMessages;
    // stores the tokens for the last query
    // appended to `_messages`
    std::vector<llama_token> _promptTokens;
    int                      _prevLen = 0;
    const char*              _chatTemplate;

    // stores the complete response for the given query
    std::string _response;
    std::string _cacheResponseTokens;
    // whether to cache previous messages in `_messages`
    bool _storeChats;

    // response generation metrics
    int64_t _responseGenerationTime = 0;
    long    _responseNumTokens      = 0;

    // length of context window consumed during the conversation
    int _nCtxUsed = 0;

    // How much of _promptTokens has already been fed to llama_decode(). A prompt wider than
    // n_batch is decoded in slices, so this tracks the slice cursor across calls.
    int _nPromptDecoded = 0;

    bool _isValidUtf8(const char* response);

    int _maxResponseTokens;
    int _configuredMaxResponseTokens;

    //neue 12 Feb 2026
    // Max tokens per response to prevent infinite generation
    const int MAX_RESPONSE_TOKENS = 512;

    // Context management helper
    void manageContext();
    //neue 12 Feb 2026

    bool _imageMode = false;

public:
    void loadModel(const char* modelPath, float minP, float temperature, bool storeChats, long contextSize,
            const char* chatTemplate, int nThreads, bool useMmap, bool useMlock, int topK, float topP, bool usePenalties, int maxResponseTokens,
            int nGpuLayers); // ← ADD: nGpuLayers

    void addChatMessage(const char* message, const char* role);

    float getResponseGenerationTime() const;

    int getContextSizeUsed() const;

    void startCompletion(const char* query);

    void startCompletionWithImage(const char* query,  // ← ADD
            const unsigned char* imageBytes,
            int imageSize);

    void loadMmproj(const char* mmprojPath);          // ← ADD
    void freeMmproj();                                // ← ADD
    bool isVisionEnabled() const { return _mtmdCtx != nullptr; } // ← ADD

    std::string completionLoop();

    void stopCompletion();

    //new feature 30 January 2026

    // Clear all chat history and memory
    void clearChatHistory();

    // Clear only the KV cache memory but keep messages
    void clearMemoryOnly();

    // Remove last N messages from chat
    void removeLastMessages(int count);

    // Get number of messages in chat
    int getMessageCount() const;

    //neue 12 Feb 2026
    void getLogAllHistory();

    std::atomic<bool> _stopGeneration{false};

    ~LLMInference();
};