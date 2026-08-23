package com.mochame.sync.common

import com.mochame.sync.api.metadata.MutationOp


@Suppress("NOTHING_TO_INLINE")
inline fun Long.hasTag(tag: Int): Boolean = (this and (1L shl tag)) != 0L

@Suppress("NOTHING_TO_INLINE")
inline fun Long.withTag(tag: Int): Long = this or (1L shl tag)

/**
 * Converts a list of changed tag indices into a 64-bit mask.
 */
fun List<Int>.toBitmask(): Long {
    var mask = 0L
    for (i in indices) {
        val tag = this[i]
        if (tag in 0..63) {
            mask = mask or (1L shl tag)
        }
    }
    return mask
}

// --- DIAGNOSTICS ---

/**
 * Traverses active bits.
 */
fun Long.toTagList(): List<Int> = buildList {
    var temp = this@toTagList
    while (temp != 0L) {
        add(temp.countTrailingZeroBits())
        temp = temp and (temp - 1L)
    }
}

/**
 * Generates human-readable summaries for local intent queue tracking without reflection.
 */
fun Long.toDiagnosticTagSummary(op: MutationOp): String {
    val opStr = if (op == MutationOp.DELETE) "DELETE" else "UPSERT"
    if (this == 0L) return "OP:$opStr []"
    return "OP:$opStr ${toTagList().joinToString(prefix = "[", postfix = "]", separator = ",")}"
}