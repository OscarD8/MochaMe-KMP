package com.mochame.app.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * THE RIBOSOME BLUEPRINT
 * This is the universal starter for the organism's nervous system.
 * * @param appDeclaration: A "Briefing" the Ribosome receives at birth.
 * It's a 'Function with Receiver', meaning the code inside the brackets
 * actually runs INSIDE the Ribosome's internal factory.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    // 1. Execute the planet-specific briefing (empty on Linux, Context on Android)
    appDeclaration()
    // 2. Load the shared genetic traits (DAOs, UI Logic) and platform limbs (Database)
    modules(platformModule, appModule)
}
