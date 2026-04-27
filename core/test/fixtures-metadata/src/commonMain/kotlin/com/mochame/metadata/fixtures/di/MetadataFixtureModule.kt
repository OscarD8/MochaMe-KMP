package com.mochame.metadata.fixtures.di

import com.mochame.metadata.GlobalMetadataStore
import com.mochame.metadata.fixtures.FakeGlobalMetaStore
import com.mochame.metadata.fixtures.FakeIdGenerator
import com.mochame.utils.IdGenerator
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class MetadataFixtureModule {
    @Single
    fun provideMetaStore(): GlobalMetadataStore = FakeGlobalMetaStore()

    @Single
    fun provideIdGenerator(): IdGenerator = FakeIdGenerator()
}