package com.mochame.contract.di

import org.koin.core.annotation.Qualifier

// -----------------------------------------------------------
// COROUTINE CONTEXT / SCOPE
// -----------------------------------------------------------
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoContext
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainContext
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultContext
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppScope

// -----------------------------------------------------------
// FILE SYSTEM
// -----------------------------------------------------------
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class PendingDir
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class CommittedDir

// -----------------------------------------------------------
// MUTEX
// -----------------------------------------------------------
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class NodeManagerMutex
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class JanitorMutex
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class BlobMutex
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class CoordinatorMutex


// -----------------------------------------------------------
// UTILS
// -----------------------------------------------------------
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class PlatformTag