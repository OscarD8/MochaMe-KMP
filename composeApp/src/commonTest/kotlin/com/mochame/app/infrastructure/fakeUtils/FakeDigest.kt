package com.mochame.app.infrastructure.fakeUtils

import com.mochame.app.infrastructure.utils.Digest

val fakeDigest = object : Digest {
    var content = ""
    override fun update(source: ByteArray) { content += source.decodeToString() }
    override fun digest(): ByteArray = content.encodeToByteArray()
}