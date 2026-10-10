package com.dg.fendai

import com.dg.fendai.implement.manager.RagCitationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RagCitationValidatorTest {
    @Test
    fun `known source citations pass without a warning`() {
        val result = RagCitationValidator.validate("Supported claim [1].", setOf(1, 2))

        assertEquals("Supported claim [1].", result.answer)
        assertNull(result.warning)
    }

    @Test
    fun `citation absent from retrieved sources produces a warning`() {
        val result = RagCitationValidator.validate("Unsupported claim [3].", setOf(1, 2))

        assertEquals("Grounding check: citation(s) 3 do not refer to passages supplied to the model.", result.warning)
    }

    @Test
    fun `missing source citation produces a warning when context exists`() {
        val result = RagCitationValidator.validate("Uncited claim.", setOf(1))

        assertEquals(
            "Grounding check: no citation to the supplied document passages was found; verify document-based claims.",
            result.warning,
        )
    }

    @Test
    fun `no retrieved context does not require a citation`() {
        val result = RagCitationValidator.validate("Background response.", emptySet())

        assertEquals("Background response.", result.answer)
        assertNull(result.warning)
    }
}
