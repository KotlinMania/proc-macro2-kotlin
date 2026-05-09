// port-lint: source fallback.rs
package io.github.kotlinmania.procmacro2

/** Force use of the fallback implementation of the API for now. */
fun force() {
    Detection.forceFallback()
}

/** Resume using the compiler implementation of the API if it is available. */
fun unforce() {
    Detection.unforceFallback()
}

internal class FallbackTokenStream internal constructor(
    private var inner: RcVec<TokenTree>,
) {
    companion object {
        fun new(): FallbackTokenStream {
            return FallbackTokenStream(RcVecBuilder.new<TokenTree>().build())
        }

        fun fromStrChecked(src: String): Result<FallbackTokenStream> {
            var cursor = getCursor(src)
            val byteOrderMark = "\uFEFF"
            if (cursor.startsWith(byteOrderMark)) {
                cursor = cursor.advance(byteOrderMark.length)
            }
            return tokenStream(cursor)
        }

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

    fun clone(): FallbackTokenStream = FallbackTokenStream(inner.clone())

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

internal class FallbackLexError(
    private val span: FallbackSpan,
) {
    fun span(): FallbackSpan = span

    override fun toString(): String = "cannot parse string into token stream"
}

private fun pushTokenFromProcMacro(vec: RcVecMut<TokenTree>, token: TokenTree) {
    if (token is TokenTree.Literal && token.value.inner.repr.startsWith('-')) {
        val literal = token.value.inner.clone()
        literal.repr = literal.repr.removePrefix("-")
        val punct = Punct('-', Spacing.Alone)
        punct.setSpan(Span.newFallback(literal.span()))
        vec.push(TokenTree.Punct(punct))
        vec.push(TokenTree.Literal(Literal.newFallback(literal)))
    } else {
        vec.push(token)
    }
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
fun invalidateCurrentThreadSpans() {
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
            LineColumn(line = idx, column = adjusted - lines[idx - 1])
        }
    }

    fun spanWithin(span: FallbackSpan): Boolean = span.lo >= this.span.lo && span.hi <= this.span.hi

    fun byteRange(span: FallbackSpan): IntRange {
        return byte(span.lo)..byte(span.hi)
    }

    fun byte(ch: Int): Int {
        val charIndex = ch - span.lo
        charIndexToByteOffset[charIndex]?.let { return it }

        val previous = charIndexToByteOffset.entries
            .filter { it.key <= charIndex }
            .maxByOrNull { it.key }
        val previousCharIndex = previous?.key ?: 0
        val previousByteOffset = previous?.value ?: 0
        val byteOffset = sourceText.substring(previousByteOffset)
            .codePointByteOffsets()
            .drop(charIndex - previousCharIndex)
            .firstOrNull()
            ?.let { previousByteOffset + it }
            ?: sourceText.encodeToByteArray().size
        charIndexToByteOffset[charIndex] = byteOffset
        return byteOffset
    }

    fun sourceText(span: FallbackSpan): String {
        val byteRange = byteRange(span)
        return sourceText.substringByUtf8ByteRange(byteRange)
    }
}

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

private object SourceMap {
    private val files = mutableListOf(
        FileInfo(
            sourceText = "",
            span = FallbackSpan(0, 0),
            lines = listOf(0),
        ),
    )

    fun invalidate() {
        files.subList(1, files.size).clear()
    }

    private fun nextStartPos(): Int = files.last().span.hi + 1

    fun addFile(src: String): FallbackSpan {
        val (len, lines) = linesOffsets(src)
        val lo = nextStartPos()
        val span = FallbackSpan(lo, lo + len)
        files.add(FileInfo(src, span, lines))
        return span
    }

    private fun find(span: FallbackSpan): Int {
        val index = files.indexOfFirst { it.spanWithin(span) }
        check(index >= 0) { "invalid span with no related file info" }
        return index
    }

    fun filepath(span: FallbackSpan): String {
        val i = find(span)
        return if (i == 0) "<unspecified>" else "<parsed string $i>"
    }

    fun fileinfo(span: FallbackSpan): FileInfo = files[find(span)]
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

    fun byteRange(): IntRange {
        return if (isCallSite()) {
            0..0
        } else {
            SourceMap.fileinfo(this).byteRange(this)
        }
    }

    fun start(): LineColumn = SourceMap.fileinfo(this).offsetLineColumn(lo)

    fun end(): LineColumn = SourceMap.fileinfo(this).offsetLineColumn(hi)

    fun file(): String = SourceMap.filepath(this)

    fun localFile(): String? = null

    fun join(other: FallbackSpan): FallbackSpan? {
        val file = SourceMap.fileinfo(this)
        if (!file.spanWithin(other)) {
            return null
        }
        return FallbackSpan(kotlin.math.min(lo, other.lo), kotlin.math.max(hi, other.hi))
    }

    fun sourceText(): String? {
        return if (isCallSite()) {
            null
        } else {
            SourceMap.fileinfo(this).sourceText(this)
        }
    }

    fun firstByte(): FallbackSpan = FallbackSpan(lo, kotlin.math.min(lo + 1, hi))

    fun lastByte(): FallbackSpan = FallbackSpan(kotlin.math.max(hi - 1, lo), hi)

    private fun isCallSite(): Boolean = lo == 0 && hi == 0

    override fun toString(): String = "bytes($lo..$hi)"
}

internal fun debugSpanFieldIfNontrivial(span: FallbackSpan): String? {
    return if (span == FallbackSpan.callSite()) null else span.toString()
}

internal data class FallbackGroup(
    private val delimiter: Delimiter,
    private val stream: FallbackTokenStream,
    private var span: FallbackSpan = FallbackSpan.callSite(),
) {
    fun delimiter(): Delimiter = delimiter

    fun stream(): FallbackTokenStream = stream.clone()

    fun span(): FallbackSpan = span

    fun spanOpen(): FallbackSpan = span.firstByte()

    fun spanClose(): FallbackSpan = span.lastByte()

    fun setSpan(span: FallbackSpan) {
        this.span = span
    }

    override fun toString(): String {
        val open: String
        val close: String
        when (delimiter) {
            Delimiter.Parenthesis -> {
                open = "("
                close = ")"
            }
            Delimiter.Brace -> {
                open = "{ "
                close = "}"
            }
            Delimiter.Bracket -> {
                open = "["
                close = "]"
            }
            Delimiter.None -> {
                open = ""
                close = ""
            }
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

        fun newUnchecked(string: String, span: FallbackSpan): FallbackIdent {
            return FallbackIdent(string, span, raw = false)
        }

        fun newRawChecked(string: String, span: FallbackSpan): FallbackIdent {
            validateIdentRaw(string)
            return newRawUnchecked(string, span)
        }

        fun newRawUnchecked(string: String, span: FallbackSpan): FallbackIdent {
            return FallbackIdent(string, span, raw = true)
        }
    }

    fun span(): FallbackSpan = span

    fun setSpan(span: FallbackSpan) {
        this.span = span
    }

    fun contentEquals(other: String): Boolean {
        return if (raw) {
            other.startsWith("r#") && sym == other.substring(2)
        } else {
            sym == other
        }
    }

    override fun toString(): String = if (raw) "r#$sym" else sym

    override fun equals(other: Any?): Boolean {
        return other is FallbackIdent && sym == other.sym && raw == other.raw
    }

    override fun hashCode(): Int = 31 * sym.hashCode() + raw.hashCode()
}

internal fun isIdentStart(c: Char): Boolean {
    return c == '_' || c == '$' || c.isLetter()
}

internal fun isIdentContinue(c: Char): Boolean {
    return isIdentStart(c) || c.isDigit()
}

private fun validateIdent(string: String) {
    require(string.isNotEmpty()) { "Ident is not allowed to be empty; use nullable Ident" }
    require(!string.all { it in '0'..'9' }) { "Ident cannot be a number; use Literal instead" }
    require(identOk(string)) { "$string is not a valid Ident" }
}

private fun identOk(string: String): Boolean {
    val first = string.firstOrNull() ?: return false
    if (!isIdentStart(first)) {
        return false
    }
    return string.drop(1).all(::isIdentContinue)
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
                    return Result.failure(LexError(FallbackLexError(FallbackSpan.callSite())))
                }
            }
            return literal(cursor).fold(
                onSuccess = { (rest, literal) ->
                    if (rest.isEmpty()) {
                        if (negative) {
                            literal.repr = "-${literal.repr}"
                        }
                        literal.span = FallbackSpan(lo, rest.off)
                        Result.success(literal)
                    } else {
                        Result.failure(LexError(FallbackLexError(FallbackSpan.callSite())))
                    }
                },
                onFailure = { Result.failure(LexError(FallbackLexError(FallbackSpan.callSite()))) },
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
        fun f32Suffixed(n: Float): FallbackLiteral = suffixed(n, "f32")
        fun f64Suffixed(n: Double): FallbackLiteral = suffixed(n, "f64")

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
            var s = f.toString()
            if (!s.contains('.')) {
                s += ".0"
            }
            return FallbackLiteral(s)
        }

        fun f64Unsuffixed(f: Double): FallbackLiteral {
            var s = f.toString()
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
            val repr = StringBuilder()
            repr.append('\'')
            if (ch == '"') {
                repr.append(ch)
            } else {
                repr.append(ch.escapeDebug())
            }
            repr.append('\'')
            return FallbackLiteral(repr.toString())
        }

        fun byteCharacter(byte: UByte): FallbackLiteral {
            val b = byte.toInt()
            val repr = StringBuilder("b'")
            when (b) {
                0 -> repr.append("\\0")
                '\t'.code -> repr.append("\\t")
                '\n'.code -> repr.append("\\n")
                '\r'.code -> repr.append("\\r")
                '\''.code -> repr.append("\\'")
                '\\'.code -> repr.append("\\\\")
                in 0x20..0x7e -> repr.append(b.toChar())
                else -> repr.append("\\x").append(b.toString(16).uppercase().padStart(2, '0'))
            }
            repr.append('\'')
            return FallbackLiteral(repr.toString())
        }

        fun byteString(bytes: ByteArray): FallbackLiteral {
            val repr = StringBuilder("b\"")
            for ((index, raw) in bytes.withIndex()) {
                val b = raw.toInt() and 0xff
                when (b) {
                    0 -> {
                        val next = bytes.getOrNull(index + 1)?.toInt()?.and(0xff)
                        repr.append(if (next != null && next in '0'.code..'7'.code) "\\x00" else "\\0")
                    }
                    '\t'.code -> repr.append("\\t")
                    '\n'.code -> repr.append("\\n")
                    '\r'.code -> repr.append("\\r")
                    '"'.code -> repr.append("\\\"")
                    '\\'.code -> repr.append("\\\\")
                    in 0x20..0x7e -> repr.append(b.toChar())
                    else -> repr.append("\\x").append(b.toString(16).uppercase().padStart(2, '0'))
                }
            }
            repr.append('"')
            return FallbackLiteral(repr.toString())
        }

        fun cString(bytes: ByteArray): FallbackLiteral {
            val repr = StringBuilder("c\"")
            escapeUtf8(bytes.decodeToString(), repr)
            repr.append('"')
            return FallbackLiteral(repr.toString())
        }

        private fun suffixed(n: Any, suffix: String): FallbackLiteral = FallbackLiteral("$n$suffix")

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

    override fun equals(other: Any?): Boolean {
        return other is FallbackLiteral && repr == other.repr
    }

    override fun hashCode(): Int = repr.hashCode()
}

private fun escapeUtf8(string: String, repr: StringBuilder) {
    val chars = string.iterator()
    while (chars.hasNext()) {
        val ch = chars.nextChar()
        when {
            ch == '\u0000' -> repr.append("\\0")
            ch == '\'' -> repr.append(ch)
            else -> repr.append(ch.escapeDebug())
        }
    }
}

private fun Char.escapeDebug(): String {
    return when (this) {
        '\t' -> "\\t"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '"' -> "\\\""
        '\'' -> "\\'"
        '\\' -> "\\\\"
        else -> if (isISOControl()) "\\u{${code.toString(16)}}" else toString()
    }
}

private fun String.codePointByteOffsets(): List<Int> {
    val offsets = mutableListOf<Int>()
    var byteOffset = 0
    for (ch in this) {
        offsets.add(byteOffset)
        byteOffset += ch.toString().encodeToByteArray().size
    }
    offsets.add(byteOffset)
    return offsets
}

private fun String.substringByUtf8ByteRange(range: IntRange): String {
    val bytes = encodeToByteArray()
    val endExclusive = (range.last + 1).coerceAtMost(bytes.size)
    return bytes.copyOfRange(range.first.coerceAtLeast(0), endExclusive).decodeToString()
}
