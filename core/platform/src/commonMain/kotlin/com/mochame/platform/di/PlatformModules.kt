package com.mochame.platform.di

import com.mochame.contract.di.CommittedDir
import com.mochame.contract.di.PendingDir
import com.mochame.platform.providers.AppPathsProvider
import kotlinx.io.files.Path
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

// TODO annotate

val productionPlatformModule = module {
    single(qualifier<PendingDir>()) { Path(get<AppPathsProvider>().blobPending) }
    single(qualifier<CommittedDir>()) { Path(get<AppPathsProvider>().blobCommitted) }
}

