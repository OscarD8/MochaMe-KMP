package com.mochame.node.fixtures

import com.mochame.sync.spi.policy.ExecutionPolicy

/**
 * Essentially strips all policy and executes a block. Useful if a policy is required but
 * any testing must be isolated purely to the context of the SUT.
 */
class BlankExecutionPolicy : ExecutionPolicy {
    override suspend fun <R> execute(operationTag: String, block: suspend () -> R): R = block()
}