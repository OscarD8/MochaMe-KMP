package com.mochame.bio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DailyContextRoute(
    epochDay: Long,
    modifier: Modifier = Modifier
) {
    val viewModel: DailyContextViewModel = koinViewModel(
        key = "DailyContextViewModel_$epochDay"
    ) { parametersOf(epochDay) }

    val state by viewModel.state.collectAsStateWithLifecycle()

    DailyContextScreen(
        state = state,
        onIntent = viewModel::dispatch,
        modifier = modifier
    )
}

@Composable
fun DailyContextScreen(
    state: DailyContextUiState,
    onIntent: (DailyContextIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading context for day ${state.epochDay}...")
        }
        return
    }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        val isWideLayout = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Text(
                text = "Daily Context: Day ${state.epochDay}",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isWideLayout) {
                // Two-Column Grid for Desktop JVM / Tablets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SleepInputField(
                            value = state.sleepHoursInput,
                            onIntent = onIntent,
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ReadinessInputField(
                            value = state.readinessScoreInput,
                            onIntent = onIntent,
                            onDone = { focusManager.clearFocus() }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        NapSwitchField(state.isNapped, onIntent)
                    }
                }
            } else {
                // Single Column for Mobile
                SleepInputField(
                    value = state.sleepHoursInput,
                    onIntent = onIntent,
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                ReadinessInputField(
                    value = state.readinessScoreInput,
                    onIntent = onIntent,
                    onDone = { focusManager.clearFocus() }
                )
                Spacer(modifier = Modifier.height(16.dp))
                NapSwitchField(state.isNapped, onIntent)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onIntent(DailyContextIntent.Save)
                    },
                    enabled = !state.isSaving
                ) {
                    Text(if (state.isSaving) "Saving..." else "Save Changes")
                }

                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        onIntent(DailyContextIntent.Delete)
                    },
                    enabled = !state.isSaving
                ) {
                    Text("Delete Record")
                }
            }

            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SleepInputField(
    value: String,
    onIntent: (DailyContextIntent) -> Unit,
    onNext: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onIntent(DailyContextIntent.UpdateSleepInput(it)) },
        label = { Text("Sleep Hours (0.0 - 72.0)") },
        placeholder = { Text("e.g. 7.5") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReadinessInputField(
    value: String,
    onIntent: (DailyContextIntent) -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onIntent(DailyContextIntent.UpdateReadinessInput(it)) },
        label = { Text("Readiness Score (1 - 5)") },
        placeholder = { Text("e.g. 4") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NapSwitchField(
    isNapped: Boolean,
    onIntent: (DailyContextIntent) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Napped Today", style = MaterialTheme.typography.titleMedium)
            Text("Logged afternoon recovery nap", style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = isNapped,
            onCheckedChange = { onIntent(DailyContextIntent.ToggleNapped(it)) }
        )
    }
}