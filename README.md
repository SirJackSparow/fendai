# Fendai SDK (`:fendai`)

Fendai is an Android SDK/Library for running on-device LLM inference, chat, and Retrieval-Augmented Generation (RAG) powered by [llama.cpp](https://github.com/ggml-org/llama.cpp).

---

## Installation & Integration

### Adding as a Dependency
In your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":fendai"))
}
```

Or via Maven / JitPack (when published):
```kotlin
dependencies {
    implementation("com.dg.fendai:fendai:1.1.1rag")
}
```

---

## Getting Started

### 1. Loading a Model
Initialize `LlamaEngine` and load your `.gguf` model file:

```kotlin
val llamaEngine = LlamaEngine()

lifecycleScope.launch(Dispatchers.IO) {
    val modelPath = File(context.filesDir, "model.gguf").absolutePath
    llamaEngine.loadModel(
        modelPath = modelPath,
        minP = 0.05f,
        temperature = 0.7f,
        storeChats = true,
        contextSize = 2048
    )
}
```

### 2. Generating Completions & Chat
```kotlin
// Add user message
llamaEngine.addChatMessage("Hello!", "user")

// Start generation
llamaEngine.startCompletion("Hello!")

// Stream or retrieve response chunks
val responseBuilder = StringBuilder()
while (true) {
    val chunk = llamaEngine.completionLoop()
    if (chunk.isEmpty()) break
    responseBuilder.append(chunk)
}
val finalResponse = responseBuilder.toString()
```

---

# Chat History & Memory Management Guide

## Overview
These functions help you manage chat history and model memory in your Fendai-powered Android app.

---

## Core Functions

### 1. `clearAllHistory()`
Deletes everything - all messages and memory. Like starting a brand new conversation.
```kotlin
btnNewChat.setOnClickListener {
    llamaEngine.clearAllHistory()  
    chatMessages.clear()           
}
```

### 2. `clearMemory()`
Clears the model's context memory while keeping your chat messages visible for viewing.
```kotlin
if (contextUsed > 90) {
    llamaEngine.clearMemory()  
}
```

### 3. `undoLastMessages(count: Int = 1)`
Removes the last N messages (like an "undo" button).
```kotlin
btnUndo.setOnClickListener {
    llamaEngine.undoLastMessages(1)  
    chatMessages.removeAt(chatMessages.size - 1)
}
```

### 4. `getTotalMessages(): Int`
Returns how many messages are in the chat history.
```kotlin
if (llamaEngine.getTotalMessages() == 0) {
    showEmptyState()
}
```

---

## Quick Comparison

| Function | Messages Deleted? | Memory Deleted? | Use Case |
|----------|-------------------|-----------------|----------|
| `clearAllHistory()` | ✅ Yes | ✅ Yes | New chat - delete everything |
| `clearMemory()` | ❌ No | ✅ Yes | Keep messages, free memory |
| `undoLastMessages(n)` | ✅ Last N | ✅ Yes | Undo mistakes |
| `getTotalMessages()` | - | - | Just check count |

---

## Complete Example

```kotlin
class ChatActivity : AppCompatActivity() {
    private lateinit var llamaEngine: LlamaEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        llamaEngine = LlamaEngine()
        lifecycleScope.launch(Dispatchers.IO) {
            llamaEngine.loadModel(
                modelPath = "/sdcard/model.gguf",
                minP = 0.05f,
                temperature = 0.7f,
                storeChats = true,
                contextSize = 2048
            )
        }
        
        btnNewChat.setOnClickListener {
            llamaEngine.clearAllHistory()
            chatMessages.clear()
        }
        
        btnUndo.setOnClickListener {
            if (llamaEngine.getTotalMessages() > 0) {
                llamaEngine.undoLastMessages(1)
                chatMessages.removeAt(chatMessages.size - 1)
            }
        }
    }
}
```
