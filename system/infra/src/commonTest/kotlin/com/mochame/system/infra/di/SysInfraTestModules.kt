package com.mochame.system.infra.di

import androidx.sqlite.SQLiteDriver
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.contract.node.NodeContextStore
import com.mochame.platform.providers.PlatformContext
import com.mochame.platform.providers.TransactionProvider
import com.mochame.support.TestSupportModule
import com.mochame.system.infra.SysInfraModule
import com.mochame.system.infra.database.NodeContextMicroSchemaConstructor
import com.mochame.system.infra.data.NodeContextDao
import com.mochame.system.infra.database.NodeContextMicroSchema
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

// -----------------------------------------------------------
// Applications
// -----------------------------------------------------------

@KoinApplication(modules = [NodeContextStoreTestModule::class])
object NodeContextStoreIntegrationTestApp

// -----------------------------------------------------------
// Modules
// -----------------------------------------------------------

@Module(
    includes = [
        TestSupportModule::class,
        SysInfraModule::class,
        NodeContextPersistenceModule::class,
    ]
)
@ComponentScan("com.mochame.system.infra.di")
class NodeContextStoreTestModule

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

// -----------------------------------------------------------
// Environments
// -----------------------------------------------------------

@OptIn(ExperimentalKermitApi::class)
@Factory
data class NodeContextStoreTestEnv(
    val store: NodeContextStore,
    val dao: NodeContextDao,
    val writer: TestLogWriter
)


