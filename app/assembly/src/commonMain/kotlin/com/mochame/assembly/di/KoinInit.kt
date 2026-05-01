package com.mochame.assembly.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * THE RIBOSOME BLUEPRINT
 * This is the universal starter for the organism's nervous system.
 * * @param appDeclaration: A "Briefing" the Ribosome receives at birth.
 * It's a 'Function with Receiver', meaning the code inside the brackets
 * actually runs INSIDE the Ribosome's internal factory.
 */
fun initKoin(platformTag: String, appDeclaration: KoinAppDeclaration = {}) = startKoin {

    appDeclaration()

    modules(
    )
}