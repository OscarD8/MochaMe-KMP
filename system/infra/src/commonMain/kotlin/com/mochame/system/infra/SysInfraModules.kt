package com.mochame.system.infra

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.mochame.system.infra.data", "com.mochame.system.infra.stores")
class SysInfraModule
