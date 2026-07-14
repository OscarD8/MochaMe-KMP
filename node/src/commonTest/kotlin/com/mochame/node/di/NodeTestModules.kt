package com.mochame.node.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.annotations.NodeManagerMutex
import com.mochame.node.data.NodeContextDao
import com.mochame.node.data.NodeContextMicroSchema
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

@KoinApplication(modules = [NodeContextTestModule::class])
object NodeContextTestApp

// -----------------------------------------------------------
// Modules
// -----------------------------------------------------------

@ComponentScan("com.mochame.node.di")
@Module(
    includes = [
        NodeProductionModule::class,
        TestSupportModule::class,
        NodeTestPersistenceModule::class,
    ]
)
class NodeContextTestModule

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

@OptIn(ExperimentalKermitApi::class)
@Factory
data class NodeContextTestEnv(
    val manager: NodeContextManager,
    val db: NodeContextMicroSchema,
    val dao: NodeContextDao,
    val writer: TestLogWriter,
    @NodeManagerMutex val managerMutex: Mutex
)


