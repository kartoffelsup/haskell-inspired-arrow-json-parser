package io.github.kartoffelsup.json

import arrow.core.Tuple2
import arrow.core.toT

data class StringView private constructor(
    private val buffer: String,
    private val startIndex: Int,
    private val endIndex: Int,
    private val length: Int = (endIndex - startIndex) + 1
) {
    fun span(p: (Char) -> Boolean): Tuple2<StringView, StringView> {
        if (isEmpty()) {
            return Tuple2(this, this)
        }
        var lastIndexOfSequence = startIndex - 1
        for (index in startIndex..endIndex) {
            val char = buffer[index]
            if (p(char)) {
                lastIndexOfSequence = index
                continue
            } else {
                break
            }
        }

        val left = StringView(buffer, startIndex, lastIndexOfSequence)
        val right = StringView(buffer, lastIndexOfSequence + 1, endIndex)
        return left toT right
    }

    fun isNotEmpty(): Boolean = !isEmpty()

    fun isEmpty(): Boolean = length == 0

    fun length(): Int = length

    fun drop(num: Int): StringView = if (length() > num) {
        StringView(buffer, startIndex + num, endIndex)
    } else {
        StringView(buffer, 0, -1)
    }

    operator fun get(index: Int): Char = buffer[startIndex + index]

    val value: String by lazy { if (isEmpty()) "" else buffer.substring(startIndex..endIndex) }

    companion object {
        fun from(s: String) = StringView(s, 0, s.length - 1)
    }
}
