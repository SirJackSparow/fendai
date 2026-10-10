package com.dg.fendai.implement.manager

internal object RagCitationValidator {
    data class Result(val answer: String, val warning: String?)

    fun validate(answer: String, allowedCitationIds: Set<Int>): Result {
        if (allowedCitationIds.isEmpty()) return Result(answer, null)
        val citedIds = Regex("\\[(\\d+)]")
            .findAll(answer)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()
        val invalidIds = citedIds - allowedCitationIds
        val warning = when {
            invalidIds.isNotEmpty() ->
                "Grounding check: citation(s) ${invalidIds.sorted().joinToString()} do not refer to passages supplied to the model."
            citedIds.intersect(allowedCitationIds).isEmpty() ->
                "Grounding check: no citation to the supplied document passages was found; verify document-based claims."
            else -> null
        }
        return Result(answer, warning)
    }
}
