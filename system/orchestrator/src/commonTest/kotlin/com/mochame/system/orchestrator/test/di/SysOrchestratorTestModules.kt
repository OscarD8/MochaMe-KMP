package com.mochame.system.orchestrator.test.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.contract.di.IdentityMutex
import com.mochame.contract.fixtures.FakeIdGenerator
import com.mochame.contract.fixtures.FakeNodeContextStore
import com.mochame.contract.fixtures.di.FixtureIdentityProviderModule
import com.mochame.support.TestSupportModule
import com.mochame.system.orchestrator.DefaultNodeContextManager
import com.mochame.system.orchestrator.SystemOrchestratorModule
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

// -----------------------------------------------------------
// Applications
// -----------------------------------------------------------

@KoinApplication(modules = [NodeManagerUnitTestModule::class])
object NodeManagerUnitTestApp

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
class NodeManagerUnitTestModule

// -----------------------------------------------------------
// Environments
// -----------------------------------------------------------

@OptIn(ExperimentalKermitApi::class)
@Factory
data class NodeManagerTestEnv(
    val manager: DefaultNodeContextManager,
    val fakeStore: FakeNodeContextStore,
    val fakeIdGen: FakeIdGenerator,
    val writer: TestLogWriter,
    @IdentityMutex val identityMutex: Mutex,
)


