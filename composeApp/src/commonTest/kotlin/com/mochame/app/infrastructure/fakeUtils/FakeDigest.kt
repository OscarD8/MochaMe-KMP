package com.mochame.app.infrastructure.fakeUtils

import com.mochame.app.infrastructure.utils.Digest
import kotlinx.io.Source
import kotlinx.io.readByteArray

val fakeDigest = object : Digest {
    var content = ""
    override fun update(source: Source) { content += source.readByteArray() }
    override fun digest(): ByteArray = content.encodeToByteArray()
}