package com.mochame.app.infrastructure.utils

import kotlinx.io.Buffer

/**
 * A platform-agnostic way to get a thread-safe reusable buffer.
 * Gemini argued that this is helpful to prevent memory churn
 * from potentially many concurrent calls to reconstruct/encode
 * sync payloads. Buffers are not thread safe, so the intent
 * here is to establish a thread-safe single buffer object
 * that any given thread will reuse during its lifecycle.
 * The process changes from creating and garbage collecting
 * a different buffer per reconstruction/encode, to each thread
 * reusing its own buffer, clearing and re-writing to it.
  */
interface BufferProvider {
    fun get(): Buffer
}

/*
Summary of the Workflow

    Janitor starts loop for 20 items on Thread-IO-1.

    Item 1: scratchpad.get() (Lazy Init). Buffer is used, then clear() is called.

    Items 2-20: scratchpad.get() returns the existing buffer. No new memory is requested from the OS.

    Finish: Janitor stops. Thread-IO-1 goes back to sleep, but it still "holds" the buffer in its pocket.

    Efficiency: We have achieved N=1 allocation instead of N=20, and we have zero garbage for the collector to clean up.

    If a different component like the SyncCoordinator were to call the same reconstruction method,
    or if the Janitor came back to it within the same process lifecycle, it would then
    not require the creation of a new buffer, but use the one on that threads lifecycle.
 */