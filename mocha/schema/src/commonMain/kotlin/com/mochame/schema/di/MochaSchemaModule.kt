package com.mochame.schema.di

import androidx.sqlite.SQLiteDriver
import com.mochame.bio.data.BioDao
import com.mochame.annotations.IoContext
import com.mochame.node.data.NodeContextDao
import com.mochame.platform.di.CommonPlatformModule
import com.mochame.platform.providers.DatabaseLocation
import com.mochame.platform.di.PlatformContext
import com.mochame.platform.providers.RoomImmediateTransProvider
import com.mochame.platform.providers.platformBuilder
import com.mochame.resonance.data.ResonanceDao
import com.mochame.schema.MochaMeDatabase
import com.mochame.schema.MochaMeDatabaseConstructor
import com.mochame.sync.data.SyncIntentDao
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.telemetry.data.TelemetryDao
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Module(includes = [CommonPlatformModule::class])
class MochaSchemaModule {

    @Single
    fun provideDatabase(
        @Provided context: PlatformContext,
        @IoContext ioContext: CoroutineContext,
        driver: SQLiteDriver,
        location: DatabaseLocation
    ): MochaMeDatabase {
        return platformBuilder<MochaMeDatabase>(
            context = context,
            queryContext = ioContext,
            isTest = false,
            location = location,
            driver = driver,
            factory = { MochaMeDatabaseConstructor.initialize() }
        ).build()
    }

    @Single
    fun provideNodeContextDao(database: MochaMeDatabase): NodeContextDao =
        database.nodeContextDao()

    @Single
    fun provideSyncIntentDao(database: MochaMeDatabase): SyncIntentDao =
        database.syncIntentDao()

    @Single
    fun provideBioDao(database: MochaMeDatabase): BioDao =
        database.bioDao()

    @Single
    fun provideTelemetryDao(database: MochaMeDatabase): TelemetryDao =
        database.telemetryDao()

    @Single
    fun provideResonanceDao(database: MochaMeDatabase): ResonanceDao =
        database.resonanceDao()

    @Single
    fun provideTransactionProvider(database: MochaMeDatabase): TransactionProvider =
        RoomImmediateTransProvider(database)

}

