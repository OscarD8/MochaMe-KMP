package com.mochame.sync.api.node

interface IdGenerator {
    suspend fun nextId(): String
}