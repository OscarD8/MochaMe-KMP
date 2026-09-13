package com.mochame.app.entry.android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mochame.sync.spi.network.SyncTransport
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class AndroidAppLifecycleObserver(
    private val transport: SyncTransport
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        transport.resume()
    }

    override fun onStop(owner: LifecycleOwner) {
        transport.pause()
    }
}