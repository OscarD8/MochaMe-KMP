@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.node.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.annotations.NodeManagerMutex
import com.mochame.logger.test.TestLoggerModule
import com.mochame.node.data.NodeContextDao
import com.mochame.node.data.NodeContextMicroSchema
import com.mochame.node.policies.JitteredExecutionPolicy
import com.mochame.support.TestSupportModule
import com.mochame.sync.spi.node.NodeContextManager
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

// -----------------------------------------------------------
// Applications
// -----------------------------------------------------------

@KoinApplication(modules = [NodeContextIntTestModule::class])
object NodeContextIntTestApp

@KoinApplication(modules = [NodeProductionModule::class])
object BootManagerUnitTestApp

@KoinApplication(modules = [JitteredExecutionPolicyTestModule::class])
object JitteredExecutionTestApp


// -----------------------------------------------------------
// Modules
// -----------------------------------------------------------

@ComponentScan("com.mochame.node.di")
@Module(
    includes = [
        NodeProductionModule::class,
        NodeTestPersistenceModule::class,
        TestSupportModule::class,
    ]
)
class NodeContextIntTestModule

@Module
class NodeTestPersistenceModule {

    @Single
    fun provideDatabase(
    ): NodeContextMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideNodeContextDao(db: NodeContextMicroSchema): NodeContextDao =
        db.nodeContextDao()

}

@Module(includes = [TestLoggerModule::class, NodeProductionModule::class])
class JitteredExecutionPolicyTestModule

// -----------------------------------------------------------
// Environments
// -----------------------------------------------------------

@Factory
data class NodeContextIntTestEnv(
    val db: NodeContextMicroSchema,
    val dao: NodeContextDao,
    val manager: NodeContextManager,
    val writer: TestLogWriter,
    @NodeManagerMutex val managerMutex: Mutex
)

@Factory
data class JitteredExecutionTestEnv(
    val executor: JitteredExecutionPolicy,
    val writer: TestLogWriter
)
