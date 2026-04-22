package com.mochame.platform.providers

interface AppPathsProvider {
    val blobPending: String
    val blobCommitted: String
    val databasePath: String
}