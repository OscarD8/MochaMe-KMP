package com.mochame.metadata.test.di

import com.mochame.di.IdentityMutex
import com.mochame.di.IoContext
import com.mochame.metadata.GlobalMetadataStore
import com.mochame.metadata.test.FakeGlobalMetaStore
import com.mochame.metadata.test.FakeIdGenerator
import com.mochame.utils.IdGenerator
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module


val fakeMetadataModule = module {
    singleOf(::FakeGlobalMetaStore) { bind<GlobalMetadataStore>() }
    singleOf(::FakeIdGenerator) { bind<IdGenerator>() }

    single<SyncUserProvider> {
        IdentityManager(
            metadataStore = get(),
            managerContext = get(qualifier<IoContext>()),
            idGenerator = get(),
            mutex = get(qualifier<IdentityMutex>()),
            logger = get()
        )
    }
}
