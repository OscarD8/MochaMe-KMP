package com.mochame.system.orchestrator.test.nodeManager


// Keeping this here as an approach to a scenario where you may
// want to unit and integration test. Realized this is not the case
// at the orchestration layer.

//abstract class IdentityManagerContractTest {
//
//    abstract fun runEnvironment(block: suspend IdentityTestEnvironment.(TestScope) -> Unit): TestResult
//
//    @Test
//    fun should_retrieve_existing_id_when_get_or_create_called() = runEnvironment {
//        // Arrange
//        val existingId = idGenerator.nextId()
//        store.saveNodeId(existingId)
//
//        // Act
//        val result = manager.getOrCreateNodeId()
//
//        // Assert
//        assertEquals(
//            existingId,
//            result,
//            "Manager should not have replaced an existing ID."
//        )
//    }
//}