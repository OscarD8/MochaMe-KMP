package com.mochame.node.di

import androidx.sqlite.SQLiteDriver
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.contract.di.NodeManagerMutex
import com.mochame.contract.providers.TransactionProvider
import com.mochame.node.NodeProductionModule
import com.mochame.node.data.NodeContextDao
import com.mochame.node.database.NodeContextMicroSchema
import com.mochame.platform.providers.PlatformContext
import com.mochame.support.TestSupportModule
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.utils.fixtures.FakeIdGenerator
import com.mochame.utils.fixtures.di.FixtureUtilsModule
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

@Module(
    includes = [
        TestSupportModule::class,
        NodeContextPersistenceModule::class,
        NodeProductionModule::class,
        FixtureUtilsModule::class
    ]
)
@ComponentScan("com.mochame.node.di")
class NodeContextTestModule

@Module
class NodeContextPersistenceModule {
    @Single
    fun provideDatabase(
        context: PlatformContext,
        driver: SQLiteDriver
    ): NodeContextMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideNodeContextDao(db: NodeContextMicroSchema): NodeContextDao =
        db.nodeContextDao()

    @Single
    fun provideTransactionProvider(): TransactionProvider =
        error("Blueprint Slot Only: Overridden at runtime in WrapperPersistence.kt")
}

@OptIn(ExperimentalKermitApi::class)
@Factory
data class NodeContextTestEnv(
    val manager: NodeContextManager,
    val dao: NodeContextDao,
    val idGen: FakeIdGenerator,
    val writer: TestLogWriter,
    @NodeManagerMutex val managerMutex: Mutex
)


