* FENDAI local AI

- Basic Func
  

- New feature

# Chat History Management Functions - Simple Guide

## Overview
These functions help you manage chat history in your LlamaEngine Android app.

---

## Functions

### 1. `clearAllHistory()`

**What it does:**
Deletes everything - all messages and memory. Like starting a brand new conversation.

**When to use:**
- "New Chat" button
- User wants to start over completely

**Example:**
```kotlin
// User clicks "New Chat" button
btnNewChat.setOnClickListener {
    llamaEngine.clearAllHistory()  // Delete everything
    chatMessages.clear()            // Also clear your UI list
}
```

**What gets deleted:**
- ✅ All chat messages (user + assistant)
- ✅ Model's memory
- ✅ Everything

---

### 2. `clearMemory()`

**What it does:**
Clears the model's memory but keeps your chat messages for viewing.

**When to use:**
- Memory is full but you want to see old messages
- Need to free up space

**Example:**
```kotlin
// Context is getting full
if (contextUsed > 90) {
    llamaEngine.clearMemory()  // Clear memory only
    // Messages still visible in chat UI
}
```

**What gets deleted:**
- ✅ Model's memory
- ❌ Messages (you can still see them)

---

### 3. `undoLastMessages(count: Int = 1)`

**What it does:**
Removes the last N messages. Like an "undo" button.

**Parameters:**
- `count` - How many messages to remove (default = 1)

**When to use:**
- User wants to delete their last message
- Remove wrong responses

**Examples:**
```kotlin
// Remove last 1 message
btnUndo.setOnClickListener {
    llamaEngine.undoLastMessages(1)  // or just: undoLastMessages()
    chatMessages.removeAt(chatMessages.size - 1)
}

// Remove last 2 messages (user + assistant pair)
llamaEngine.undoLastMessages(2)
```

**What gets deleted:**
- ✅ Last N messages from the end

---

### 4. `getTotalMessages(): Int`

**What it does:**
Returns how many messages are in the chat.

**Returns:**
- Number of messages (0 if empty)

**When to use:**
- Check if chat is empty
- Display message count
- Enable/disable buttons

**Examples:**
```kotlin
// Check if empty
if (llamaEngine.getTotalMessages() == 0) {
    showEmptyState()
}

// Display count
textCount.text = "Messages: ${llamaEngine.getTotalMessages()}"

// Enable undo only if there are messages
btnUndo.isEnabled = llamaEngine.getTotalMessages() > 0
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

## Simple Complete Example

```kotlin
class ChatActivity : AppCompatActivity() {
    private lateinit var llamaEngine: LlamaEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load model
        llamaEngine = LlamaEngine()
        lifecycleScope.launch {
            llamaEngine.load("/sdcard/model.gguf")
        }
        
        // New Chat button
        btnNewChat.setOnClickListener {
            llamaEngine.clearAllHistory()
            chatMessages.clear()
            adapter.notifyDataSetChanged()
        }
        
        // Undo button
        btnUndo.setOnClickListener {
            if (llamaEngine.getTotalMessages() > 0) {
                llamaEngine.undoLastMessages(1)
                chatMessages.removeAt(chatMessages.size - 1)
                adapter.notifyDataSetChanged()
            }
        }
        
        // Show message count
        fun updateCount() {
            textCount.text = "Total: ${llamaEngine.getTotalMessages()}"
        }
    }
}
```

---

## Important Notes

⚠️ **Safety:**
- All functions check if model is loaded first
- Safe to call even if chat is empty
- Won't crash your app

✅ **Remember:**
- Call `clearAllHistory()` for "New Chat"
- Call `undoLastMessages()` for "Undo"
- Call `getTotalMessages()` to check count
- Always update your UI after clearing!

---

## That's It!

Just 4 simple functions to manage your chat:
1. **Clear all** → `clearAllHistory()`
2. **Clear memory only** → `clearMemory()`
3. **Undo** → `undoLastMessages(n)`
4. **Get count** → `getTotalMessages()`
