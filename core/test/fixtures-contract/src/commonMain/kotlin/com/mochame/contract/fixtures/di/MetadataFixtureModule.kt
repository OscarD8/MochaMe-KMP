package com.mochame.contract.fixtures.di

import com.mochame.contract.identity.GlobalMetadataStore
import com.mochame.contract.fixtures.FakeGlobalMetaStore
import com.mochame.contract.fixtures.FakeIdGenerator
import com.mochame.contract.identity.IdGenerator
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class MetadataFixtureModule {
    @Single
    fun provideMetaStore(): GlobalMetadataStore = FakeGlobalMetaStore()

    @Single
    fun provideIdGenerator(): IdGenerator = FakeIdGenerator()
}