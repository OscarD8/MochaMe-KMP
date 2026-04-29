package com.mochame.system.orchestrator.test.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.contract.di.AppScope
import com.mochame.contract.di.IdentityMutex
import com.mochame.contract.fixtures.di.FixtureIdentityProviderModule
import com.mochame.contract.identity.GlobalMetadataStore
import com.mochame.contract.identity.IdGenerator
import com.mochame.contract.identity.IdentityManager
import com.mochame.support.TestSupportModule
import com.mochame.system.orchestrator.SystemOrchestratorModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

// -----------------------------------------------------------
// Applications
// -----------------------------------------------------------

@KoinApplication(modules = [IdentityManagerUnitModule::class])
object IdentityManagerUnitTestApp

// -----------------------------------------------------------
// Modules
// -----------------------------------------------------------

@Module(
    includes = [
        FixtureIdentityProviderModule::class,
        TestSupportModule::class,
        SystemOrchestratorModule::class
    ]
)
@ComponentScan("com.mochame.system.orchestrator.test.di")
class IdentityManagerUnitModule

// -----------------------------------------------------------
// Environments
// -----------------------------------------------------------

@OptIn(ExperimentalKermitApi::class)
@Factory
data class IdentityTestEnvironment(
    val manager: IdentityManager,
    val store: GlobalMetadataStore,
    val idGenerator: IdGenerator,
    val writer: TestLogWriter,
    @AppScope val scope: CoroutineScope,
    @IdentityMutex val identityMutex: Mutex,
)


