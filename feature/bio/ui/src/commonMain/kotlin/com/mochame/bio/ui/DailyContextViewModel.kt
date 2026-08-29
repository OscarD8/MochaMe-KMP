package com.mochame.bio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochame.bio.domain.DailyContextRepository
import com.mochame.bio.domain.SaveDailyContextUseCase
import com.mochame.utils.cli.PrimitiveParsers
import com.mochame.utils.cli.Update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel


@KoinViewModel
class DailyContextViewModel(
    @InjectedParam private val epochDay: Long,
    private val repository: DailyContextRepository,
    private val saveUseCase: SaveDailyContextUseCase
) : ViewModel() {

    private val userInputs = MutableStateFlow(TransientInput())
    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<DailyContextUiState> = combine(
        repository.observeContext(epochDay),
        userInputs,
        isSaving,
        errorMessage
    ) { entity, inputs, saving, error ->
        DailyContextUiState(
            epochDay = epochDay,
            sleepHoursInput = inputs.sleep ?: entity?.sleepHours?.toString().orEmpty(),
            readinessScoreInput = inputs.readiness ?: entity?.readinessScore?.toString().orEmpty(),
            isNapped = inputs.isNapped ?: entity?.isNapped ?: false,
            isLoading = false,
            isSaving = saving,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DailyContextUiState(epochDay = epochDay, isLoading = true)
    )

    fun dispatch(intent: DailyContextIntent) {
        when (intent) {
            is DailyContextIntent.UpdateSleepInput ->
                userInputs.update { it.copy(sleep = intent.input) }

            is DailyContextIntent.UpdateReadinessInput ->
                userInputs.update { it.copy(readiness = intent.input) }

            is DailyContextIntent.ToggleNapped ->
                userInputs.update { it.copy(isNapped = intent.isNapped) }

            is DailyContextIntent.Save -> performSave()
            is DailyContextIntent.Delete -> performDelete()
            is DailyContextIntent.DismissError -> errorMessage.value = null
        }
    }

    private fun performSave() {
        viewModelScope.launch {
            val snapshotInputs = userInputs.value
            val errors = mutableListOf<String>()

            val parsedSleep = snapshotInputs.sleep?.let {
                PrimitiveParsers.parseBoundedDouble(it, 0.0..72.0, "Sleep Hours")
                    .onFailure { err -> errors.add(err.message.orEmpty()) }
                    .getOrNull()
            }

            val parsedReadiness = snapshotInputs.readiness?.let {
                PrimitiveParsers.parseBoundedInt(it, 1..5, "Readiness Score")
                    .onFailure { err -> errors.add(err.message.orEmpty()) }
                    .getOrNull()
            }

            if (errors.isNotEmpty()) {
                errorMessage.value = errors.joinToString("\n")
                return@launch
            }

            isSaving.value = true
            errorMessage.value = null

            saveUseCase(
                epochDay = epochDay,
                sleepHours = Update.fromParsed(snapshotInputs.sleep, parsedSleep),
                readinessScore = Update.fromParsed(snapshotInputs.readiness, parsedReadiness),
                isNapped = Update.fromNullable(snapshotInputs.isNapped)
            ).fold(
                onSuccess = {
                    // Reset only untouched/saved fields without dropping concurrent edits
                    userInputs.update { current ->
                        current.copy(
                            sleep = if (current.sleep == snapshotInputs.sleep) null else current.sleep,
                            readiness = if (current.readiness == snapshotInputs.readiness) null else current.readiness,
                            isNapped = if (current.isNapped == snapshotInputs.isNapped) null else current.isNapped
                        )
                    }
                },
                onFailure = { error ->
                    errorMessage.value = error.message ?: "Persistence failure."
                }
            )
            isSaving.value = false
        }
    }

    private fun performDelete() {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            try {
                repository.softDeleteContext(epochDay)
                userInputs.value = TransientInput()
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Deletion failure."
            } finally {
                isSaving.value = false
            }
        }
    }
}