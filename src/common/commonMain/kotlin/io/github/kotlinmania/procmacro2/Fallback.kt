// port-lint: source fallback.rs

/**
 * Standalone (non-compiler-backed) implementation of the proc-macro surface.
 *
 * In upstream Rust this module is the fallback arm of the Compiler/Fallback
 * dispatch in `Wrapper.kt`. When no active procedural macro bridge session is
 * entered on the current thread (or when [force] is active), public types
 * delegate here.
 *
 * The internal types declared here ([FallbackTokenStream], [FallbackSpan],
 * [FallbackGroup], [FallbackIdent], [FallbackLiteral], [FallbackLexError]) are
 * named after their public counterparts with a `Fallback` prefix to
 * disambiguate; upstream places the same names inside a `fallback` module.
 */
package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.unicodeident.isXidContinue
import io.github.kotlinmania.unicodeident.isXidContinueCodePoint
import io.github.kotlinmania.unicodeident.isXidStart
import io.github.kotlinmania.unicodeident.isXidStartCodePoint
import kotlin.native.concurrent.ThreadLocal

/**
 * Force use of proc-macro2's fallback implementation of the API for now, even
 * if the compiler's implementation is available.
 */
fun force() {
    forceFallback()
}

/**
 * Resume using the compiler's implementation of the proc macro API if it is
 * available.
 */
fun unforce() {
    unforceFallback()
}

internal class FallbackTokenStream internal constructor(
    internal var inner: RcVec<TokenTree>,
) {
    companion object {
        fun new(): FallbackTokenStream = FallbackTokenStream(RcVecBuilder.new<TokenTree>().build())

        fun fromStrChecked(src: String): Result<FallbackTokenStream> {
            var cursor = getCursor(src)
            val byteOrderMark = "\uFEFF"
            if (cursor.startsWith(byteOrderMark)) {
                cursor = cursor.advance(byteOrderMark.length)
            }
            return tokenStream(cursor)
        }

        fun fromStrUnchecked(src: String): FallbackTokenStream = fromStrChecked(src).getOrThrow()

        fun fromTokenTree(tree: TokenTree): FallbackTokenStream {
            val stream = RcVecBuilder.new<TokenTree>()
            pushTokenFromProcMacro(stream.asMut(), tree)
            return FallbackTokenStream(stream.build())
        }

        fun fromTokenStreams(streams: Iterable<FallbackTokenStream>): FallbackTokenStream {
            val builder = RcVecBuilder.new<TokenTree>()
            for (stream in streams) {
                builder.extend(stream.takeInner().intoIter().remaining())
            }
            return FallbackTokenStream(builder.build())
        }
    }

    fun isEmpty(): Boolean = inner.len() == 0

    fun iter(): Iterator<TokenTree> = inner.iter()

    private fun takeInner(): RcVecBuilder<TokenTree> = inner.makeOwned()

    fun drop() {
        val firstMut = inner.getMut() ?: return
        val stack = ArrayDeque<Iterator<TokenTree>>()
        var current: Iterator<TokenTree> = firstMut.take().intoIter()
        while (true) {
            while (current.hasNext()) {
                val token = current.next()
                val group = (token as? TokenTree.Group)?.value?.inner ?: continue
                val fallbackGroup = (group as? WrapperGroup.Fallback)?.group ?: continue
                val childMut = fallbackGroup.innerStream().inner.getMut() ?: continue
                stack.addLast(current)
                current = childMut.take().intoIter()
            }
            current = stack.removeLastOrNull() ?: return
        }
    }

    fun extendTokenTrees(tokens: Iterable<TokenTree>) {
        val vec = inner.makeMut()
        for (token in tokens) {
            pushTokenFromProcMacro(vec.asMut(), token)
        }
    }

    fun extendTokenStreams(streams: Iterable<FallbackTokenStream>) {
        val vec = inner.makeMut()
        for (stream in streams) {
            vec.extend(stream.takeInner().intoIter().remaining())
        }
    }

    fun intoIter(): RcVecIntoIter<TokenTree> = takeInner().intoIter()

    fun clone(): FallbackTokenStream {
        val copied = RcVecBuilder.new<TokenTree>()
        for (tree in inner.iter()) copied.push(tree.cloneToken())
        return FallbackTokenStream(copied.build())
    }

    override fun toString(): String {
        val out = StringBuilder()
        var joint = false
        for ((index, token) in inner.iter().withIndex()) {
            if (index != 0 && !joint) {
                out.append(' ')
            }
            joint = token is TokenTree.Punct && token.value.spacing() == Spacing.Joint
            out.append(token)
        }
        return out.toString()
    }
}

internal typealias TokenTreeIter = RcVecIntoIter<TokenTree>

internal class FallbackLexError(
    private val span: FallbackSpan,
) {
    companion object {
        fun callSite(): FallbackLexError = FallbackLexError(FallbackSpan.callSite())
    }

    fun span(): FallbackSpan = span

    override fun toString(): String = "cannot parse string into token stream"
}

/**
 * Pushes a token onto the builder vec, splitting negative numeric literals
 * into a leading minus [Punct] and a positive [Literal] so the printed token
 * stream survives lossless round-tripping. See
 * https://github.com/dtolnay/proc-macro2/issues/235.
 */
private fun pushTokenFromProcMacro(vec: RcVecMut<TokenTree>, token: TokenTree) {
    fun pushNegativeLiteral(target: RcVecMut<TokenTree>, literal: FallbackLiteral) {
        literal.repr = literal.repr.removePrefix("-")
        val punct = Punct('-', Spacing.Alone)
        punct.setSpan(Span.newFallback(literal.span()))
        target.push(TokenTree.Punct(punct))
        target.push(TokenTree.Literal(Literal.newFallback(literal)))
    }

    if (token is TokenTree.Literal) {
        val wl = token.value.inner
        if (wl is WrapperLiteral.Fallback && wl.literal.repr.startsWith('-')) {
            pushNegativeLiteral(vec, wl.literal.clone())
            return
        }
    }
    vec.push(token)
}

internal class TokenStreamBuilder private constructor(
    private val inner: RcVecBuilder<TokenTree>,
) {
    companion object {
        fun new(): TokenStreamBuilder = TokenStreamBuilder(RcVecBuilder.new())

        fun withCapacity(cap: Int): TokenStreamBuilder = TokenStreamBuilder(RcVecBuilder.withCapacity(cap))
    }

    fun pushTokenFromParser(tt: TokenTree) {
        inner.push(tt)
    }

    fun build(): FallbackTokenStream = FallbackTokenStream(inner.build())
}

private fun getCursor(src: String): Cursor {
    val span = SourceMap.addFile(src)
    return Cursor(rest = src, off = span.lo)
}

/** Invalidates any spans that exist on the current thread. */
internal fun invalidateCurrentThreadSpansInternal() {
    SourceMap.invalidate()
}

private data class FileInfo(
    val sourceText: String,
    val span: FallbackSpan,
    val lines: List<Int>,
    val charIndexToByteOffset: MutableMap<Int, Int> = mutableMapOf(),
) {
    fun offsetLineColumn(offset: Int): LineColumn {
        require(spanWithin(FallbackSpan(offset, offset)))
        val adjusted = offset - span.lo
        val found = lines.binarySearch(adjusted)
        return if (found >= 0) {
            LineColumn(line = found + 1, column = 0)
        } else {
            val idx = -found - 1
            // Internal offsets index Kotlin UTF-16 storage. Rust's public column
            // counts Unicode scalars; reuse the existing scalar decoder.
            var index = lines[idx - 1]
            var column = 0
            while (index < adjusted) {
                index += sourceText.codePointAndWidthAt(index).second
                column += 1
            }
            LineColumn(line = idx, column = column)
        }
    }

    fun sourceUtf16Range(span: FallbackSpan): IntRange {
        require(spanWithin(span))
        return (span.lo - this.span.lo)..(span.hi - this.span.lo - 1)
    }

    fun spanWithin(span: FallbackSpan): Boolean = span.lo >= this.span.lo && span.hi <= this.span.hi

    // A span covers the half-open byte interval [byte(lo), byte(hi)); represent it
    // as an inclusive Kotlin IntRange whose last element is the final covered byte.
    fun byteRange(span: FallbackSpan): IntRange = byte(span.lo)..(byte(span.hi) - 1)

    fun byte(ch: Int): Int {
        val charIndex = ch - span.lo
        charIndexToByteOffset[charIndex]?.let { return it }

        var previousCharIndex = 0
        var previousByteOffset = 0
        var nextCharIndex = -1
        var nextByteOffset = 0
        for ((cachedChar, cachedByte) in charIndexToByteOffset) {
            if (cachedChar < charIndex && cachedChar >= previousCharIndex) {
                previousCharIndex = cachedChar
                previousByteOffset = cachedByte
            } else if (cachedChar > charIndex && (nextCharIndex < 0 || cachedChar < nextCharIndex)) {
                nextCharIndex = cachedChar
                nextByteOffset = cachedByte
            }
        }

        val byteOffset =
            if (nextCharIndex >= 0 && nextCharIndex - charIndex < charIndex - previousCharIndex) {
                nextByteOffset - utf8ByteCountBetween(sourceText, charIndex, nextCharIndex)
            } else {
                previousByteOffset + utf8ByteCountBetween(sourceText, previousCharIndex, charIndex)
            }
        charIndexToByteOffset[charIndex] = byteOffset
        return byteOffset
    }

    fun sourceText(span: FallbackSpan): String {
        require(spanWithin(span))
        return sourceText.substring(span.lo - this.span.lo, span.hi - this.span.lo)
    }
}

/**
 * Computes the offsets of each line in the given source string and the total
 * number of characters. Used by [SourceMap.addFile] to build the per-file
 * line-table.
 */
private fun linesOffsets(s: String): Pair<Int, List<Int>> {
    val lines = mutableListOf(0)
    var total = 0
    for (ch in s) {
        total += 1
        if (ch == '\n') {
            lines.add(total)
        }
    }
    return total to lines
}

@ThreadLocal
private object SourceMap {
    // Start with a single dummy file which all `callSite()` and `defSite()`
    // spans reference. Real files are appended on parse.
    private val files =
        mutableListOf(
            FileInfo(
                sourceText = "",
                span = FallbackSpan(0, 0),
                lines = listOf(0),
            ),
        )

    fun invalidate() {
        files.subList(1, files.size).clear()
    }

    // Add 1 so there's always space between files; we'll always have at least
    // 1 file, as the list is initialized with a dummy file above.
    private fun nextStartPos(): Int = files.last().span.hi + 1

    fun addFile(src: String): FallbackSpan {
        val (len, lines) = linesOffsets(src)
        val lo = nextStartPos()
        val span = FallbackSpan(lo, lo + len)
        files.add(FileInfo(src, span, lines))
        return span
    }

    private fun find(span: FallbackSpan): Int {
        val index =
            files.binarySearch { file ->
                when {
                    file.span.hi < span.lo -> -1
                    file.span.lo > span.hi -> 1
                    else -> {
                        check(file.spanWithin(span)) { "invalid span with no related file info" }
                        0
                    }
                }
            }
        check(index >= 0) { "invalid span with no related file info" }
        return index
    }

    fun filepath(span: FallbackSpan): String {
        val i = find(span)
        return if (i == 0) "<unspecified>" else "<parsed string $i>"
    }

    fun fileinfo(span: FallbackSpan): FileInfo = files[find(span)]

    fun fileinfoMut(span: FallbackSpan): FileInfo = files[find(span)]
}

internal data class FallbackSpan(
    val lo: Int,
    val hi: Int,
) {
    companion object {
        fun callSite(): FallbackSpan = FallbackSpan(0, 0)

        fun mixedSite(): FallbackSpan = callSite()

        fun defSite(): FallbackSpan = callSite()
    }

    fun resolvedAt(other: FallbackSpan): FallbackSpan = this

    fun locatedAt(other: FallbackSpan): FallbackSpan = other

    fun byteRange(): IntRange =
        if (isCallSite()) {
            0..0
        } else {
            SourceMap.fileinfoMut(this).byteRange(this)
        }

    fun start(): LineColumn = SourceMap.fileinfo(this).offsetLineColumn(lo)

    fun end(): LineColumn = SourceMap.fileinfo(this).offsetLineColumn(hi)

    fun sourceUtf16Range(): IntRange? =
        if (isCallSite()) null else SourceMap.fileinfo(this).sourceUtf16Range(this)

    fun file(): String = SourceMap.filepath(this)

    fun localFile(): String? = null

    fun join(other: FallbackSpan): FallbackSpan? {
        val file = SourceMap.fileinfo(this)
        if (!file.spanWithin(other)) {
            return null
        }
        return FallbackSpan(kotlin.math.min(lo, other.lo), kotlin.math.max(hi, other.hi))
    }

    fun sourceText(): String? =
        if (isCallSite()) {
            null
        } else {
            SourceMap.fileinfoMut(this).sourceText(this)
        }

    fun firstByte(): FallbackSpan = FallbackSpan(lo, kotlin.math.min(lo + 1, hi))

    fun lastByte(): FallbackSpan = FallbackSpan(kotlin.math.max(hi - 1, lo), hi)

    private fun isCallSite(): Boolean = lo == 0 && hi == 0

    override fun toString(): String = "bytes($lo..$hi)"
}

internal fun debugSpanFieldIfNontrivial(span: FallbackSpan): String? = if (span == FallbackSpan.callSite()) null else span.toString()

internal data class FallbackGroup(
    private val delimiter: Delimiter,
    private val stream: FallbackTokenStream,
    private var span: FallbackSpan = FallbackSpan.callSite(),
) {
    fun delimiter(): Delimiter = delimiter

    fun stream(): FallbackTokenStream = stream.clone()

    internal fun innerStream(): FallbackTokenStream = stream

    fun clone(): FallbackGroup = FallbackGroup(delimiter, stream.clone(), span)

    fun span(): FallbackSpan = span

    fun spanOpen(): FallbackSpan = span.firstByte()

    fun spanClose(): FallbackSpan = span.lastByte()

    fun setSpan(span: FallbackSpan) {
        this.span = span
    }

    // Match the formatting produced by the compiler's in-tree procedural
    // macro library:
    //   Empty parens:     ()
    //   Nonempty parens:  (...)
    //   Empty brackets:   []
    //   Nonempty brackets: [...]
    //   Empty braces:     { }
    //   Nonempty braces:  { ... }
    override fun toString(): String {
        val (open, close) =
            when (delimiter) {
                Delimiter.Parenthesis -> "(" to ")"
                Delimiter.Brace -> "{ " to "}"
                Delimiter.Bracket -> "[" to "]"
                Delimiter.None -> "" to ""
            }
        val body = stream.toString()
        val space = if (delimiter == Delimiter.Brace && !stream.isEmpty()) " " else ""
        return "$open$body$space$close"
    }
}

internal class FallbackIdent private constructor(
    private val sym: String,
    private var span: FallbackSpan,
    private val raw: Boolean,
) {
    companion object {
        fun newChecked(string: String, span: FallbackSpan): FallbackIdent {
            validateIdent(string)
            return newUnchecked(string, span)
        }

        fun newUnchecked(string: String, span: FallbackSpan): FallbackIdent = FallbackIdent(string, span, raw = false)

        fun newRawChecked(string: String, span: FallbackSpan): FallbackIdent {
            validateIdentRaw(string)
            return newRawUnchecked(string, span)
        }

        fun newRawUnchecked(string: String, span: FallbackSpan): FallbackIdent = FallbackIdent(string, span, raw = true)
    }

    fun span(): FallbackSpan = span

    fun setSpan(span: FallbackSpan) {
        this.span = span
    }

    fun clone(): FallbackIdent = FallbackIdent(sym, span, raw)

    fun contentEquals(other: String): Boolean =
        if (raw) {
            other.startsWith("r#") && sym == other.substring(2)
        } else {
            sym == other
        }

    override fun toString(): String = if (raw) "r#$sym" else sym

    override fun equals(other: Any?): Boolean = other is FallbackIdent && sym == other.sym && raw == other.raw

    override fun hashCode(): Int = 31 * sym.hashCode() + raw.hashCode()
}

internal fun isIdentStart(c: Char): Boolean = c == '_' || isXidStart(c)

internal fun isIdentContinue(c: Char): Boolean = isXidContinue(c)

// Rust char is a Unicode scalar, not a UTF-16 code unit. Both the tokenizer and
// constructor use the existing unicode-ident tables for the complete scalar.
internal fun isIdentStartCodePoint(codePoint: Int): Boolean =
    codePoint == '_'.code || isXidStartCodePoint(codePoint)

internal fun isIdentContinueCodePoint(codePoint: Int): Boolean =
    isXidContinueCodePoint(codePoint)

// Decodes the Unicode scalar at [index] (combining a surrogate pair into a single
// code point); returns the scalar paired with the number of UTF-16 code units it
// occupies (1 for BMP, 2 for supplementary).
internal fun String.codePointAndWidthAt(index: Int): Pair<Int, Int> {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            val cp = 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            return cp to 2
        }
    }
    return high.code to 1
}

private fun validateIdent(string: String) {
    require(string.isNotEmpty()) { "Ident is not allowed to be empty; use nullable Ident" }
    require(!string.all { it in '0'..'9' }) { "Ident cannot be a number; use Literal instead" }
    require(identOk(string)) { "$string is not a valid Ident" }
}

private fun identOk(string: String): Boolean {
    if (string.isEmpty()) return false
    val (first, firstWidth) = string.codePointAndWidthAt(0)
    if (!isIdentStartCodePoint(first)) return false
    var index = firstWidth
    while (index < string.length) {
        val (cp, width) = string.codePointAndWidthAt(index)
        if (!isIdentContinueCodePoint(cp)) return false
        index += width
    }
    return true
}

private fun validateIdentRaw(string: String) {
    validateIdent(string)
    require(string !in setOf("_", "super", "self", "Self", "crate")) {
        "`r#$string` cannot be a raw identifier"
    }
}

internal class FallbackLiteral internal constructor(
    internal var repr: String,
    private var span: FallbackSpan = FallbackSpan.callSite(),
) {
    companion object {
        fun new(repr: String): FallbackLiteral = FallbackLiteral(repr)

        fun fromStrChecked(repr: String): Result<FallbackLiteral> {
            var cursor = getCursor(repr)
            val lo = cursor.off
            val negative = cursor.startsWithChar('-')
            if (negative) {
                cursor = cursor.advance(1)
                if (!cursor.startsWithFn { it.isDigit() }) {
                    return Result.failure(
                        LexErrorException(LexError(WrapperLexError.Fallback(FallbackLexError(FallbackSpan.callSite())))),
                    )
                }
            }
            return literal(cursor).fold(
                onSuccess = { parsed ->
                    val (rest, literal) = parsed
                    if (rest.isEmpty()) {
                        if (negative) {
                            literal.repr = "-${literal.repr}"
                        }
                        literal.span = FallbackSpan(lo, rest.off)
                        Result.success(literal)
                    } else {
                        Result.failure(
                            LexErrorException(LexError(WrapperLexError.Fallback(FallbackLexError(FallbackSpan.callSite())))),
                        )
                    }
                },
                onFailure = {
                    Result.failure(
                        LexErrorException(LexError(WrapperLexError.Fallback(FallbackLexError(FallbackSpan.callSite())))),
                    )
                },
            )
        }

        fun fromStrUnchecked(repr: String): FallbackLiteral = FallbackLiteral(repr)

        fun u8Suffixed(n: UByte): FallbackLiteral = suffixed(n, "u8")

        fun u16Suffixed(n: UShort): FallbackLiteral = suffixed(n, "u16")

        fun u32Suffixed(n: UInt): FallbackLiteral = suffixed(n, "u32")

        fun u64Suffixed(n: ULong): FallbackLiteral = suffixed(n, "u64")

        fun u128Suffixed(n: ULong): FallbackLiteral = suffixed(n, "u128")

        fun usizeSuffixed(n: ULong): FallbackLiteral = suffixed(n, "usize")

        fun i8Suffixed(n: Byte): FallbackLiteral = suffixed(n, "i8")

        fun i16Suffixed(n: Short): FallbackLiteral = suffixed(n, "i16")

        fun i32Suffixed(n: Int): FallbackLiteral = suffixed(n, "i32")

        fun i64Suffixed(n: Long): FallbackLiteral = suffixed(n, "i64")

        fun i128Suffixed(n: Long): FallbackLiteral = suffixed(n, "i128")

        fun isizeSuffixed(n: Long): FallbackLiteral = suffixed(n, "isize")

        fun f32Suffixed(n: Float): FallbackLiteral = floatSuffixed(n.toString(), "f32")

        fun f64Suffixed(n: Double): FallbackLiteral = floatSuffixed(n.toString(), "f64")

        fun u8Unsuffixed(n: UByte): FallbackLiteral = unsuffixed(n)

        fun u16Unsuffixed(n: UShort): FallbackLiteral = unsuffixed(n)

        fun u32Unsuffixed(n: UInt): FallbackLiteral = unsuffixed(n)

        fun u64Unsuffixed(n: ULong): FallbackLiteral = unsuffixed(n)

        fun u128Unsuffixed(n: ULong): FallbackLiteral = unsuffixed(n)

        fun usizeUnsuffixed(n: ULong): FallbackLiteral = unsuffixed(n)

        fun i8Unsuffixed(n: Byte): FallbackLiteral = unsuffixed(n)

        fun i16Unsuffixed(n: Short): FallbackLiteral = unsuffixed(n)

        fun i32Unsuffixed(n: Int): FallbackLiteral = unsuffixed(n)

        fun i64Unsuffixed(n: Long): FallbackLiteral = unsuffixed(n)

        fun i128Unsuffixed(n: Long): FallbackLiteral = unsuffixed(n)

        fun isizeUnsuffixed(n: Long): FallbackLiteral = unsuffixed(n)

        fun f32Unsuffixed(f: Float): FallbackLiteral {
            var s = plainFloatString(f.toString())
            if (!s.contains('.')) {
                s += ".0"
            }
            return FallbackLiteral(s)
        }

        fun f64Unsuffixed(f: Double): FallbackLiteral {
            var s = plainFloatString(f.toString())
            if (!s.contains('.')) {
                s += ".0"
            }
            return FallbackLiteral(s)
        }

        fun string(string: String): FallbackLiteral {
            val repr = StringBuilder(string.length + 2)
            repr.append('"')
            escapeUtf8(string, repr)
            repr.append('"')
            return FallbackLiteral(repr.toString())
        }

        fun character(ch: Char): FallbackLiteral {
            require(!ch.isSurrogate()) { "Surrogate code unit is not a valid Unicode scalar: ${ch.code}" }
            return characterCodePoint(ch.code)
        }

        fun characterCodePoint(codePoint: Int): FallbackLiteral {
            require(codePoint in 0..0x10FFFF && codePoint !in 0xD800..0xDFFF) {
                "Invalid Unicode scalar value: $codePoint"
            }
            val repr = StringBuilder()
            repr.append('\'')
            escapeDebugScalar(
                codePoint = codePoint,
                escapeSingleQuote = true,
                escapeDoubleQuote = false,
                repr = repr,
            )
            repr.append('\'')
            return FallbackLiteral(repr.toString())
        }

        fun byteCharacter(byte: UByte): FallbackLiteral {
            val b = byte.toInt()
            val repr = StringBuilder("b'")
            when {
                b == 0x00 -> repr.append("\\0")
                b == 0x09 -> repr.append("\\t")
                b == 0x0a -> repr.append("\\n")
                b == 0x0d -> repr.append("\\r")
                b == 0x27 -> repr.append("\\'")
                b == 0x5c -> repr.append("\\\\")
                b in 0x20..0x7e -> repr.append(b.toChar())
                else -> repr.append("\\x").append(b.toString(16).uppercase().padStart(2, '0'))
            }
            repr.append('\'')
            return FallbackLiteral(repr.toString())
        }

        fun byteString(bytes: ByteArray): FallbackLiteral {
            val repr = StringBuilder("b\"")
            for ((index, raw) in bytes.withIndex()) {
                val b = raw.toInt() and 0xff
                when {
                    b == 0x00 -> {
                        val next = bytes.getOrNull(index + 1)?.toInt()?.and(0xff)
                        repr.append(if (next != null && next in 0x30..0x37) "\\x00" else "\\0")
                    }
                    b == 0x09 -> repr.append("\\t")
                    b == 0x0a -> repr.append("\\n")
                    b == 0x0d -> repr.append("\\r")
                    b == 0x22 -> repr.append("\\\"")
                    b == 0x5c -> repr.append("\\\\")
                    b in 0x20..0x7e -> repr.append(b.toChar())
                    else -> repr.append("\\x").append(b.toString(16).uppercase().padStart(2, '0'))
                }
            }
            repr.append('"')
            return FallbackLiteral(repr.toString())
        }

        fun cString(bytes: ByteArray): FallbackLiteral {
            val repr = StringBuilder("c\"")
            var offset = 0
            while (offset < bytes.size) {
                // Decode the next well-formed UTF-8 scalar and escape it like any other
                // text character (so `\t`, `\n`, `"` and printable scalars round-trip
                // correctly); bytes that are not part of a valid sequence are emitted as
                // an explicit `\xNN` byte escape, matching upstream cString.
                val seqLen = utf8SequenceLength(bytes, offset)
                if (seqLen > 0) {
                    escapeUtf8(bytes.copyOfRange(offset, offset + seqLen).decodeToString(), repr)
                    offset += seqLen
                } else {
                    repr.append("\\x").append(hexByte(bytes[offset].toInt() and 0xff))
                    offset++
                }
            }
            repr.append('"')
            return FallbackLiteral(repr.toString())
        }

        private fun suffixed(n: Any, suffix: String): FallbackLiteral = FallbackLiteral("$n$suffix")

        // Suffixed float literals render the value without a trailing `.0` for
        // integer-valued floats (`10f32`, not `10.0f32`), matching upstream which
        // formats the value with Display before appending the suffix.
        private fun floatSuffixed(display: String, suffix: String): FallbackLiteral {
            val body = if (display.endsWith(".0")) display.dropLast(2) else display
            return FallbackLiteral("$body$suffix")
        }

        private fun unsuffixed(n: Any): FallbackLiteral = FallbackLiteral(n.toString())
    }

    fun span(): FallbackSpan = span

    fun setSpan(span: FallbackSpan) {
        this.span = span
    }

    fun subspan(range: IntRange): FallbackSpan? {
        val lo = span.lo + range.first
        val hi = span.lo + range.last + 1
        return if (lo <= hi && hi <= span.hi) FallbackSpan(lo, hi) else null
    }

    fun clone(): FallbackLiteral = FallbackLiteral(repr, span)

    override fun toString(): String = repr

    override fun equals(other: Any?): Boolean = other is FallbackLiteral && repr == other.repr

    override fun hashCode(): Int = repr.hashCode()
}

private fun escapeUtf8(string: String, repr: StringBuilder) {
    var i = 0
    while (i < string.length) {
        val (codePoint, width) = string.codePointAndWidthAt(i)
        if (codePoint == 0) {
            // Peek (without consuming) at the next character: a following octal
            // digit forces the explicit `\x00` form so it cannot be misread as a
            // longer octal escape.
            val nextIsOctal = i + width < string.length && string[i + width] in '0'..'7'
            repr.append(if (nextIsOctal) "\\x00" else "\\0")
        } else {
            escapeDebugScalar(
                codePoint = codePoint,
                escapeSingleQuote = false,
                escapeDoubleQuote = true,
                repr = repr,
            )
        }
        i += width
    }
}

private fun escapeDebugScalar(
    codePoint: Int,
    escapeSingleQuote: Boolean,
    escapeDoubleQuote: Boolean,
    repr: StringBuilder,
) {
    when (codePoint) {
        0x00 -> {
            repr.append("\\0")
            return
        }
        0x09 -> {
            repr.append("\\t")
            return
        }
        0x0a -> {
            repr.append("\\n")
            return
        }
        0x0d -> {
            repr.append("\\r")
            return
        }
        0x5c -> {
            repr.append("\\\\")
            return
        }
        0x27 -> {
            repr.append(if (escapeSingleQuote) "\\'" else "'")
            return
        }
        0x22 -> {
            repr.append(if (escapeDoubleQuote) "\\\"" else "\"")
            return
        }
    }
    if (isPrintableUnicode(codePoint) && !isGraphemeExtendScalar(codePoint)) {
        repr.appendUnicodeScalar(codePoint)
    } else {
        repr.append("\\u{")
        repr.append(codePoint.toString(16))
        repr.append('}')
    }
}

private val PRINTABLE_BMP_CATEGORIES =
    setOf(
        CharCategory.UPPERCASE_LETTER,
        CharCategory.LOWERCASE_LETTER,
        CharCategory.TITLECASE_LETTER,
        CharCategory.MODIFIER_LETTER,
        CharCategory.OTHER_LETTER,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.DECIMAL_DIGIT_NUMBER,
        CharCategory.LETTER_NUMBER,
        CharCategory.OTHER_NUMBER,
        CharCategory.CONNECTOR_PUNCTUATION,
        CharCategory.DASH_PUNCTUATION,
        CharCategory.START_PUNCTUATION,
        CharCategory.END_PUNCTUATION,
        CharCategory.INITIAL_QUOTE_PUNCTUATION,
        CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.OTHER_PUNCTUATION,
        CharCategory.MATH_SYMBOL,
        CharCategory.CURRENCY_SYMBOL,
        CharCategory.MODIFIER_SYMBOL,
        CharCategory.OTHER_SYMBOL,
    )

private fun isPrintableUnicode(codePoint: Int): Boolean {
    if (codePoint in 0x20..0x7e) return true
    if (codePoint > 0xffff) {
        return isPrintableSupplementary(codePoint)
    }
    return codePoint.toChar().category in PRINTABLE_BMP_CATEGORIES
}

private fun isGraphemeExtendScalar(codePoint: Int): Boolean {
    if (codePoint > 0xffff) {
        return isSupplementaryGraphemeExtend(codePoint)
    }
    val category = codePoint.toChar().category
    return category == CharCategory.NON_SPACING_MARK ||
        category == CharCategory.ENCLOSING_MARK ||
        category == CharCategory.COMBINING_SPACING_MARK
}

private fun isPrintableSupplementary(codePoint: Int): Boolean {
    if (codePoint !in 0x10000..0x3ffff) return false
    if ((codePoint and 0xfffe) == 0xfffe) return false
    return !(
        codePoint == 0x110bd ||
            codePoint == 0x110cd ||
            codePoint in 0x13430..0x1343f ||
            codePoint in 0x1bca0..0x1bca3 ||
            codePoint in 0x1d173..0x1d17a
    )
}

private fun isSupplementaryGraphemeExtend(codePoint: Int): Boolean =
    codePoint in 0x1d165..0x1d169 ||
        codePoint in 0x1d16d..0x1d172 ||
        codePoint in 0x1d17b..0x1d182 ||
        codePoint in 0x1d185..0x1d18b ||
        codePoint in 0x1d1aa..0x1d1ad ||
        codePoint in 0xe0100..0xe01ef

internal fun StringBuilder.appendUnicodeScalar(codePoint: Int): StringBuilder {
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xD800 + (offset ushr 10)).toChar())
        append((0xDC00 + (offset and 0x3FF)).toChar())
    }
    return this
}

// Renders a Kotlin Float/Double `toString()` result in plain (non-scientific)
// decimal form, matching Rust's `{}` float Display which never uses exponent
// notation. Inputs without an exponent are returned unchanged.
private fun plainFloatString(s: String): String {
    val eIndex = s.indexOfFirst { it == 'e' || it == 'E' }
    if (eIndex < 0) {
        return s
    }
    val exponent = s.substring(eIndex + 1).toInt()
    val mantissa = s.substring(0, eIndex)
    val negative = mantissa.startsWith('-')
    val magnitude = mantissa.trimStart('-')
    val dot = magnitude.indexOf('.')
    val intPart = if (dot >= 0) magnitude.substring(0, dot) else magnitude
    val fracPart = if (dot >= 0) magnitude.substring(dot + 1) else ""
    val digits = intPart + fracPart
    val pointPos = intPart.length + exponent
    val out = StringBuilder()
    if (negative) {
        out.append('-')
    }
    when {
        pointPos <= 0 -> {
            out.append("0.")
            repeat(-pointPos) { out.append('0') }
            out.append(digits)
        }
        pointPos >= digits.length -> {
            out.append(digits)
            repeat(pointPos - digits.length) { out.append('0') }
        }
        else -> {
            out.append(digits, 0, pointPos)
            out.append('.')
            out.append(digits, pointPos, digits.length)
        }
    }
    return out.toString()
}

// Returns the length (1..4) of the well-formed UTF-8 scalar beginning at [index],
// or 0 if the bytes there do not form a valid sequence (so the caller can emit a
// raw `\xNN` byte escape instead). Rejects overlong encodings, surrogates, and
// out-of-range scalars the same way a strict UTF-8 decoder would.
private fun utf8SequenceLength(bytes: ByteArray, index: Int): Int {
    val b0 = bytes[index].toInt() and 0xff
    if (b0 < 0x80) return 1
    val len =
        when {
            b0 in 0xC2..0xDF -> 2
            b0 >= 0xE0 && b0 <= 0xEF -> 3
            b0 in 0xF0..0xF4 -> 4
            else -> 0
        }
    if (len == 0 || index + len > bytes.size) return 0
    val b1 = bytes[index + 1].toInt() and 0xff
    val validSecond =
        when (b0) {
            0xE0 -> b1 in 0xA0..0xBF
            0xED -> b1 in 0x80..0x9F // exclude surrogates U+D800..U+DFFF
            0xF0 -> b1 in 0x90..0xBF
            0xF4 -> b1 in 0x80..0x8F // cap at U+10FFFF
            else -> b1 in 0x80..0xBF
        }
    if (!validSecond) return 0
    for (k in 2 until len) {
        if ((bytes[index + k].toInt() and 0xC0) != 0x80) return 0
    }
    return len
}

private fun hexByte(value: Int): String {
    val hex = "0123456789ABCDEF"
    return "${hex[value shr 4 and 0xF]}${hex[value and 0xF]}"
}

private fun utf8ByteCountBetween(
    s: String,
    startUtf16: Int,
    endUtf16: Int,
): Int {
    var bytes = 0
    var i = startUtf16
    while (i < endUtf16) {
        val (cp, width) = s.codePointAndWidthAt(i)
        if (i + width <= endUtf16) {
            bytes +=
                when {
                    cp < 0x80 -> 1
                    cp < 0x800 -> 2
                    cp < 0x10000 -> 3
                    else -> 4
                }
            i += width
        } else {
            bytes += 2
            i += 1
        }
    }
    return bytes
}
