package com.mochame.app.database

import com.mochame.app.di.JVMTestModules


class JvmBioDaoTest : BaseBioDaoTest() {
    override val platformTestModules = listOf(
        JVMTestModules.databaseModule,
        JVMTestModules.loggerModule
    )
}