package com.mochame.app.database

import androidx.room.RoomDatabaseConstructor

actual object MochaDatabaseConstructor : RoomDatabaseConstructor<MochaDatabase> {
    actual override fun initialize(): MochaDatabase = error("iOS implementation not available on this machine")
}