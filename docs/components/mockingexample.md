
```kotlin 
everySuspend { mockDao.updateNodeId(any()) } sequentially {
    throws(SQLiteException("BUSY"))
    throws(SQLiteException("BUSY"))

    // Third call: actually call the real database
    calls { (newId: String) -> settingsDao.updateNodeId(newId) }
}

// Act:
storeWithMock.saveNodeId("verified")

// Assert
// Verify the mock was poked 3 times
verifySuspend(VerifyMode.order) {
    mockDao.updateNodeId("verified")
    mockDao.updateNodeId("verified")
    mockDao.updateNodeId("verified")
}

// Verify: real database now contains the data
val dbResult = settingsDao.getNodeIdentity()
assertNotNull(dbResult, "The real DB was never reached!")
assertEquals("verified", dbResult.nodeId, "The real DB was never updated!")

val recoveryLog = writer.logs.find { it.message.contains("3 attempts") }
assertNotNull(recoveryLog, "Log expected for three attempts made.")
```

