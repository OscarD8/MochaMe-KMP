package com.mochame.bio.ui

data class DailyContextUiState(
    val epochDay: Long,
    val sleepHoursInput: String = "",
    val readinessScoreInput: String = "",
    val isNapped: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

internal data class TransientInput(
    val sleep: String? = null,        // null = untouched, "" = explicitly cleared, "X" = edited
    val readiness: String? = null,    // null = untouched, "" = explicitly cleared, "X" = edited
    val isNapped: Boolean? = null     // null = untouched, Boolean = edited
)

sealed interface DailyContextIntent {
    data class UpdateSleepInput(val input: String) : DailyContextIntent
    data class UpdateReadinessInput(val input: String) : DailyContextIntent
    data class ToggleNapped(val isNapped: Boolean) : DailyContextIntent
    data object Save : DailyContextIntent
    data object Delete : DailyContextIntent
    data object DismissError : DailyContextIntent
}