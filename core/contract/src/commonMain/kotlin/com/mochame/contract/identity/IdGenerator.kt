package com.mochame.contract.identity

interface IdGenerator {
    suspend fun nextId(): String
}