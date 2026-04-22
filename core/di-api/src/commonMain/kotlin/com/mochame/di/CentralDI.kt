package com.mochame.di

import org.koin.core.annotation.Qualifier

// -----------------------------------------------------------
// COROUTINE CONTEXT / SCOPE
// -----------------------------------------------------------
@Qualifier
annotation class IoContext
@Qualifier
annotation class MainContext
@Qualifier
annotation class DefaultContext
@Qualifier
annotation class AppScope

// -----------------------------------------------------------
// FILE SYSTEM
// -----------------------------------------------------------
@Qualifier
annotation class PendingDir
@Qualifier
annotation class CommittedDir


// -----------------------------------------------------------
// MUTEX
// -----------------------------------------------------------
@Qualifier
annotation class IdentityMutex
@Qualifier
annotation class JanitorMutex
@Qualifier
annotation class BlobMutex
