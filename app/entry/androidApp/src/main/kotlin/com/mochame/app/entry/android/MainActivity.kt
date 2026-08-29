package com.mochame.app.entry.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mochame.app.ui.MochaComposeAppShell
import com.mochame.app.ui.di.initKoinCompose
import org.koin.android.ext.koin.androidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MochaComposeAppShell()
        }
    }
}

class MochaAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinCompose {
            androidContext(this@MochaAndroidApp)
        }
    }
}