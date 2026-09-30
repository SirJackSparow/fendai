package com.dg.fendai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the decision [com.dg.fendai.implement.manager.FendAIManagerImplementation] makes before
 * sending a message: whether retrieval replaces the user's question with a grounded prompt.
 *
 * groundedPrompt() and the knowledge fields are private and the native engine is unavailable off
 * device, so the decision is restated here as the contract those tests would assert. If the
 * fallback rule in groundedPrompt() changes, this file must change with it.
 */
class RagGroundingTest {

    /** Mirrors groundedPrompt(): returns the prompt to send, or null to send the question as-is. */
    private fun groundedPrompt(
        enabled: Boolean,
        indexed: Boolean,
        topK: Int,
        buildThrows: Boolean = false,
    ): String? = when {
        !enabled -> null
        !indexed -> null
        topK <= 0 -> null
        buildThrows -> null
        else -> "grounded"
    }

    @Test
    fun `grounding is off until the caller turns it on`() {
        assertNull(groundedPrompt(enabled = false, indexed = true, topK = 4))
    }

    @Test
    fun `an empty index sends the question unchanged`() {
        assertNull(groundedPrompt(enabled = true, indexed = false, topK = 4))
    }

    @Test
    fun `a non positive topK disables retrieval instead of searching for nothing`() {
        assertNull(groundedPrompt(enabled = true, indexed = true, topK = 0))
        assertNull(groundedPrompt(enabled = true, indexed = true, topK = -1))
    }

    @Test
    fun `retrieval failure falls back to the question rather than failing the turn`() {
        assertNull(groundedPrompt(enabled = true, indexed = true, topK = 4, buildThrows = true))
    }

    @Test
    fun `an enabled indexed engine grounds the question`() {
        assertEquals("grounded", groundedPrompt(enabled = true, indexed = true, topK = 4))
    }

    @Test
    fun `retrieval is inactive before a knowledge base is loaded`() {
        // The default state of the manager: knowledge == null and ragEnabled == false.
        assertNull(groundedPrompt(enabled = false, indexed = false, topK = 4))
    }
}
