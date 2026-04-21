package com.mochame.core.providers

interface AppPathsProvider {
    val blobPending: String
    val blobCommitted: String
    val databasePath: String
}