package com.mochame.sync.fixtures.serialization

import com.mochame.sync.api.models.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.common.TriState
import com.mochame.utils.fixtures.HlcTestFactory

data class TestEntity(
    override val id: String = "default-entity-1",
    override val hlc: HLC = HlcTestFactory.create(),
    override val lastModified: Long = HlcTestFactory.create().ts,
    override val isDeleted: Boolean = false,
    val triStateValue: TriState = TriState.TRUE,
    val textValue: String = "default-text",
    val countValue: Int = 1,
) : LocalFirstEntity<TestEntity> {
    override fun withHlc(hlc: HLC): TestEntity = copy(hlc = hlc)
    override fun markDeleted(): TestEntity = copy(isDeleted = true)
}
