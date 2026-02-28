package com.mochame.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mochame.app.di.initKoin
import com.mochame.app.ui.ProofOfLifeScreen
import com.mochame.app.ui.ProofOfLifeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize the global Koin instance.
        // Because we call this here, Koin is already available to Compose.
        initKoin {
            androidContext(this@MainActivity)
        }

        setContent {
            // 2. Direct access to the ViewModel.
            // koinViewModel() handles finding the Koin instance on its own now.
            val viewModel: ProofOfLifeViewModel = koinViewModel()

            ProofOfLifeScreen(viewModel)
        }
    }
}