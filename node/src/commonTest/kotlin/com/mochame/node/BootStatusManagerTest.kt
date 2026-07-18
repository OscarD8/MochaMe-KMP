package com.mochame.node

import com.mochame.node.di.BootManagerUnitTestApp
import com.mochame.node.managers.DefaultBootStatusManager
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.api.exceptions.MochaException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals


// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend DefaultBootStatusManager.(TestScope) -> Unit) =
    runUnitEnvironment<DefaultBootStatusManager>(
        koinSetup = { includes(koinConfiguration<BootManagerUnitTestApp>()) },
        block = block
    )


@ExperimentalCoroutinesApi
class BootStatusManagerTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // STATE TRANSITIONS
    // -----------------------------------------------------------

    @Test
    fun should_defaultToIdleStatus_when_nodeFirstBoots() = runEnv {
        assertEquals(BootState.Idle, bootState.value)
    }

    @Test
    fun should_updateStateCorrectly_when_passedState() = runEnv {
        updateBootState(BootState.Initializing)
        assertEquals(BootState.Initializing, bootState.value)

        updateBootState(BootState.Ready)
        assertEquals(BootState.Ready, bootState.value)
    }

    @Test
    fun should_acceptFailureDetails_when_updateBootStatePassedAnException() = runEnv {
        val errorException = MochaException.Transient.BootTimeout("Connection timed out")
        val failureState = BootState.TransientFailure("Network error", errorException)

        updateBootState(failureState)

        assertEquals(failureState, bootState.value)
        assertEquals(errorException, failureState.exception)
    }

    @Test
    fun should_conflateBootState_when_rapidSequentialUpdatesOccur() =
        runEnv { testScope ->
            // Given - queued asynchronous collector
            val emittedStates = mutableListOf<BootState>()
            val collectionJob = testScope.launch {
                bootState.collect { emittedStates.add(it) }
            }
            testScope.runCurrent()

            // When - rapid, distinct updates
            updateBootState(BootState.Initializing)
            updateBootState(BootState.Ready)
            testScope.runCurrent()

            // Then - the collector collected conflation of initialized and ready status
            val expectedStates = listOf(
                BootState.Idle,
                BootState.Ready
            )
            assertEquals(expectedStates, emittedStates)

            collectionJob.cancel()
        }

    @Test
    fun should_notifyActiveCollectorsInstantly_when_bootStateIsUpdated() =
        runEnv { testScope ->
            // Given - eager asynchronous collector
            val emittedStates = mutableListOf<BootState>()
            testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
                bootState.collect { emittedStates.add(it) }
            }

            // When
            updateBootState(BootState.Initializing)
            updateBootState(BootState.Ready)

            // Then
            val expectedStates = listOf(
                BootState.Idle,
                BootState.Initializing,
                BootState.Ready
            )
            assertEquals(expectedStates, emittedStates)
        }

    // -----------------------------------------------------------
    // PROVIDER ACCESS
    // -----------------------------------------------------------

    @Test
    fun should_provideBootState_when_accessedViaProviderInterface() = runEnv {
        // Given
        val provider = this as BootStatusProvider
        // When
        updateBootState(BootState.Ready)
        // Then
        assertEquals(BootState.Ready, provider.bootState.value)
    }

}