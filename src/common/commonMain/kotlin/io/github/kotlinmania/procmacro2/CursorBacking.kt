package io.github.kotlinmania.procmacro2

internal class CursorBacking(
    val source: String,
) {
    private var lastNonAsciiPos: Int = -2
    private var utf8Bytes: ByteArray? = null
    private var charToByteOffset: IntArray? = null

    fun isAsciiFrom(pos: Int): Boolean {
        var last = lastNonAsciiPos
        if (last == -2) {
            last = -1
            for (index in source.length - 1 downTo 0) {
                if (source[index].code >= 0x80) {
                    last = index
                    break
                }
            }
            lastNonAsciiPos = last
        }
        return pos > last
    }

    fun firstByteFrom(pos: Int): Int? {
        if (pos >= source.length) return null
        val first = source[pos].code
        if (first < 0x80) return first
        val (cp, _) = source.codePointAndWidthAt(pos)
        return when {
            cp < 0x800 -> 0xC0 or (cp shr 6)
            cp in 0xD800..0xDFFF -> loneSurrogateUtf8[0].toInt() and 0xff
            cp < 0x10000 -> 0xE0 or (cp shr 12)
            else -> 0xF0 or (cp shr 18)
        }
    }

    fun bytesFrom(pos: Int): List<Int> {
        if (isAsciiFrom(pos)) {
            val sliceLength = source.length - pos
            return object : AbstractList<Int>() {
                override val size: Int get() = sliceLength

                override fun get(index: Int): Int {
                    if (index < 0 || index >= sliceLength) {
                        throw IndexOutOfBoundsException("index: $index, size: $sliceLength")
                    }
                    return source[pos + index].code
                }
            }
        }
        ensureUtf8Index()
        val encoded = utf8Bytes!!
        val offsets = charToByteOffset!!
        val rawStart = offsets[pos]
        if (rawStart >= 0) {
            val sliceLength = encoded.size - rawStart
            return object : AbstractList<Int>() {
                override val size: Int get() = sliceLength

                override fun get(index: Int): Int {
                    if (index < 0 || index >= sliceLength) {
                        throw IndexOutOfBoundsException("index: $index, size: $sliceLength")
                    }
                    return encoded[rawStart + index].toInt() and 0xff
                }
            }
        }
        val repl = loneSurrogateUtf8
        val tailStart = offsets[pos + 1]
        val sliceLength = repl.size + (encoded.size - tailStart)
        return object : AbstractList<Int>() {
            override val size: Int get() = sliceLength

            override fun get(index: Int): Int {
                if (index < 0 || index >= sliceLength) {
                    throw IndexOutOfBoundsException("index: $index, size: $sliceLength")
                }
                return if (index < repl.size) {
                    repl[index].toInt() and 0xff
                } else {
                    encoded[tailStart + index - repl.size].toInt() and 0xff
                }
            }
        }
    }

    fun charsFrom(pos: Int): Iterable<Char> =
        Iterable {
            object : Iterator<Char> {
                private var current = pos

                override fun hasNext(): Boolean = current < source.length

                override fun next(): Char {
                    if (current >= source.length) {
                        throw NoSuchElementException()
                    }
                    return source[current++]
                }
            }
        }

    fun charIndicesFrom(pos: Int): Sequence<Pair<Int, Char>> =
        Sequence {
            object : Iterator<Pair<Int, Char>> {
                private var current = pos

                override fun hasNext(): Boolean = current < source.length

                override fun next(): Pair<Int, Char> {
                    if (current >= source.length) {
                        throw NoSuchElementException()
                    }
                    val relative = current - pos
                    val ch = source[current++]
                    return relative to ch
                }
            }
        }

    fun equalsCursor(
        pos: Int,
        off: Int,
        other: Any?,
    ): Boolean {
        if (other !is Cursor) return false
        if (off != other.off) return false
        val length = source.length - pos
        if (length != other.len()) return false
        if (this === other.backing && pos == other.pos) return true
        return source.regionMatches(pos, other.backing.source, other.pos, length)
    }

    fun hashCursor(
        pos: Int,
        off: Int,
    ): Int {
        var textHash = 0
        for (index in pos until source.length) {
            textHash = 31 * textHash + source[index].code
        }
        return 31 * textHash + off.hashCode()
    }

    private fun ensureUtf8Index() {
        if (utf8Bytes != null) return
        val encoded = source.encodeToByteArray()
        val offsets = IntArray(source.length + 1)
        var byteOff = 0
        var index = 0
        while (index < source.length) {
            val ch = source[index]
            when {
                ch.code < 0x80 -> {
                    offsets[index] = byteOff
                    byteOff += 1
                    index += 1
                }
                ch.code < 0x800 -> {
                    offsets[index] = byteOff
                    byteOff += 2
                    index += 1
                }
                ch.isHighSurrogate() && index + 1 < source.length && source[index + 1].isLowSurrogate() -> {
                    offsets[index] = byteOff
                    offsets[index + 1] = -1 - byteOff
                    byteOff += 4
                    index += 2
                }
                ch.isSurrogate() -> {
                    offsets[index] = byteOff
                    byteOff += loneSurrogateUtf8.size
                    index += 1
                }
                else -> {
                    offsets[index] = byteOff
                    byteOff += 3
                    index += 1
                }
            }
        }
        check(byteOff == encoded.size) { "UTF-8 offset mismatch: $byteOff != ${encoded.size}" }
        offsets[source.length] = byteOff
        utf8Bytes = encoded
        charToByteOffset = offsets
    }
}

private val loneSurrogateUtf8: ByteArray = "\uD800".encodeToByteArray()

internal fun Cursor.charAt(index: Int): Char = backing.source[pos + index]

internal fun Cursor.charOrNull(index: Int): Char? {
    val target = pos + index
    return if (index >= 0 && target < backing.source.length) backing.source[target] else null
}

internal fun Cursor.slice(
    startIndex: Int,
    endIndex: Int,
): String = backing.source.substring(pos + startIndex, pos + endIndex)

internal fun Cursor.codePointAndWidthAt(index: Int): Pair<Int, Int> =
    backing.source.codePointAndWidthAt(pos + index)

internal fun Cursor.startsWithAt(
    startIndex: Int,
    prefix: String,
): Boolean =
    startIndex >= 0 &&
        pos + startIndex + prefix.length <= backing.source.length &&
        backing.source.regionMatches(pos + startIndex, prefix, 0, prefix.length)
