package com.mochame.app.domain.sync

import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.infrastructure.sync.HLC

interface MetadataStore {
    suspend fun recordMetadata(moduleName: MochaModule, hlc: HLC)
}
