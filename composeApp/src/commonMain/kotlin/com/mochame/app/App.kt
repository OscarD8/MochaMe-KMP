package com.mochame.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview // The updated import
import com.mochame.app.ui.ProofOfLifeScreen
import com.mochame.app.ui.ProofOfLifeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview // This now uses the non-deprecated version
fun App() {
    // koinViewModel() handles the 'ViewModelStoreOwner' for Linux and Android
    val viewModel = koinViewModel<ProofOfLifeViewModel>()

    ProofOfLifeScreen(viewModel)
}