package com.mochame.sync.di

import org.koin.dsl.module

val syncDatabaseModule = module {

    // 2. Provide the DAOs derived from THIS specific DB
    single { get<SyncDatabase>().syncMetadataDao() }
    single { get<SyncDatabase>().mutationLedgerDao() }
}
