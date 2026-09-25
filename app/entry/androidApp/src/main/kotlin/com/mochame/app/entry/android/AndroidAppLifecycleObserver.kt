package com.mochame.app.entry.android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mochame.annotations.AppMainScope
import com.mochame.sync.spi.network.SyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class AndroidAppLifecycleObserver(
    private val transport: SyncTransport,
    @AppMainScope private val coroutineScope: CoroutineScope
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            transport.pause()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            transport.resume()
        }
    }
}