package com.mochame.contract.node

interface IdGenerator {
    suspend fun nextId(): String
}