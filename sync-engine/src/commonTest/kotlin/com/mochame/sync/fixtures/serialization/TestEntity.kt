package com.mochame.sync.fixtures.serialization

import com.mochame.sync.api.models.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.common.TriState

data class TestEntity(
    override val id: String,
    override val hlc: HLC,
    override val lastModified: Long,
    override val isDeleted: Boolean = false,
    val triStateValue: TriState = TriState.UNSET,
    val textValue: String = "",
    val countValue: Int = 0,
) : LocalFirstEntity<TestEntity> {
    override fun withHlc(hlc: HLC): TestEntity = copy(hlc = hlc)
    override fun markDeleted(): TestEntity = copy(isDeleted = true)
}
