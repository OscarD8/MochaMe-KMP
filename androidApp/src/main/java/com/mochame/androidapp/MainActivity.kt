package com.mochame.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mochame.app.App
import com.mochame.app.di.initKoin
import com.mochame.app.ui.ProofOfLifeScreen
import com.mochame.app.ui.ProofOfLifeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         * THE ANDROID ADAPTATION
         * We are now INSIDE the Ribosome. We call 'androidContext',
         * which is an internal Koin tool, to grab the 'Security Key' (this Activity).
         * Without this key, the organism cannot open the Room Database bank vault.
         */
        initKoin("Android") {
            androidContext(this@MainActivity)
        }

        setContent {
            App()
        }
    }
}