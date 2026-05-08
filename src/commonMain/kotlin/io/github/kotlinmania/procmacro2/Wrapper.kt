// port-lint: source wrapper.rs
package io.github.kotlinmania.procmacro2

internal fun insideProcMacro(): Boolean {
    return Detection.insideProcMacro()
}

private fun mismatch(line: Int): Nothing {
    throw IllegalStateException("compiler/fallback mismatch L$line")
}

internal class WrapperTokenStream private constructor(
    private val fallback: FallbackTokenStream,
) : Iterable<TokenTree> {
    companion object {
        fun new(): WrapperTokenStream {
            return WrapperTokenStream(FallbackTokenStream.new())
        }

        fun fromStrChecked(src: String): Result<WrapperTokenStream> {
            return FallbackTokenStream.fromStrChecked(src).map(::WrapperTokenStream)
        }

        fun fromFallback(inner: FallbackTokenStream): WrapperTokenStream {
            return WrapperTokenStream(inner)
        }

        fun fromToken(token: TokenTree): WrapperTokenStream {
            return WrapperTokenStream(FallbackTokenStream.fromTokenTree(token))
        }

        fun fromTokens(tokens: Iterable<TokenTree>): WrapperTokenStream {
            val stream = FallbackTokenStream.new()
            stream.extendTokenTrees(tokens)
            return WrapperTokenStream(stream)
        }

        fun fromStreams(streams: Iterable<WrapperTokenStream>): WrapperTokenStream {
            return WrapperTokenStream(FallbackTokenStream.fromTokenStreams(streams.map { it.unwrapStable() }))
        }
    }

    fun isEmpty(): Boolean = fallback.isEmpty()

    private fun unwrapNightly(): Nothing = mismatch(0)

    fun unwrapStable(): FallbackTokenStream = fallback

    fun extendTokens(tokens: Iterable<TokenTree>) {
        fallback.extendTokenTrees(tokens)
    }

    fun extendStreams(streams: Iterable<WrapperTokenStream>) {
        fallback.extendTokenStreams(streams.map { it.unwrapStable() })
    }

    override fun iterator(): WrapperTokenTreeIter {
        return WrapperTokenTreeIter(fallback.intoIter())
    }

    override fun toString(): String = fallback.toString()

    override fun equals(other: Any?): Boolean {
        return other is WrapperTokenStream &&
            fallback.iter().asSequence().toList() == other.fallback.iter().asSequence().toList()
    }

    override fun hashCode(): Int = fallback.iter().asSequence().toList().hashCode()
}

internal class WrapperLexError private constructor(
    private val compilerPanic: Boolean,
    private val fallback: FallbackLexError?,
) : IllegalArgumentException("cannot parse string into token stream") {
    companion object {
        fun fallback(error: FallbackLexError): WrapperLexError {
            return WrapperLexError(compilerPanic = false, fallback = error)
        }

        fun compilerPanic(): WrapperLexError {
            return WrapperLexError(compilerPanic = true, fallback = null)
        }
    }

    fun span(): WrapperSpan {
        return if (compilerPanic) {
            WrapperSpan.callSite()
        } else {
            WrapperSpan.fromFallback(requireNotNull(fallback).span())
        }
    }

    override fun toString(): String {
        return fallback?.toString() ?: FallbackLexError(FallbackSpan.callSite()).toString()
    }
}

internal class WrapperTokenTreeIter(
    private val fallback: RcVecIntoIter<TokenTree>,
) : Iterator<TokenTree> {
    override fun hasNext(): Boolean = fallback.hasNext()

    override fun next(): TokenTree = fallback.next()

    fun sizeHint(): Pair<Int, Int?> = fallback.sizeHint()
}

internal data class WrapperSpan(
    private val fallback: FallbackSpan,
) {
    companion object {
        fun callSite(): WrapperSpan {
            return WrapperSpan(FallbackSpan.callSite())
        }

        fun mixedSite(): WrapperSpan {
            return WrapperSpan(FallbackSpan.mixedSite())
        }

        fun defSite(): WrapperSpan {
            return WrapperSpan(FallbackSpan.defSite())
        }

        fun fromFallback(inner: FallbackSpan): WrapperSpan {
            return WrapperSpan(inner)
        }
    }

    fun resolvedAt(other: WrapperSpan): WrapperSpan {
        return WrapperSpan(fallback.resolvedAt(other.fallback))
    }

    fun locatedAt(other: WrapperSpan): WrapperSpan {
        return WrapperSpan(fallback.locatedAt(other.fallback))
    }

    fun unwrap(): Nothing = mismatch(0)

    fun byteRange(): IntRange = fallback.byteRange()

    fun start(): LineColumn = fallback.start()

    fun end(): LineColumn = fallback.end()

    fun file(): String = fallback.file()

    fun localFile(): String? = fallback.localFile()

    fun join(other: WrapperSpan): WrapperSpan? {
        return fallback.join(other.fallback)?.let(::WrapperSpan)
    }

    fun sourceText(): String? = fallback.sourceText()

    fun unwrapStable(): FallbackSpan = fallback

    override fun toString(): String = fallback.toString()
}

internal fun debugSpanFieldIfNontrivial(span: WrapperSpan): String? {
    return debugSpanFieldIfNontrivial(span.unwrapStable())
}

internal class WrapperGroup private constructor(
    private var fallback: FallbackGroup,
) {
    companion object {
        fun new(delimiter: Delimiter, stream: WrapperTokenStream): WrapperGroup {
            return WrapperGroup(FallbackGroup(delimiter, stream.unwrapStable()))
        }

        fun fromFallback(group: FallbackGroup): WrapperGroup {
            return WrapperGroup(group)
        }
    }

    fun delimiter(): Delimiter = fallback.delimiter()

    fun stream(): WrapperTokenStream = WrapperTokenStream.fromFallback(fallback.stream())

    fun span(): WrapperSpan = WrapperSpan.fromFallback(fallback.span())

    fun spanOpen(): WrapperSpan = WrapperSpan.fromFallback(fallback.spanOpen())

    fun spanClose(): WrapperSpan = WrapperSpan.fromFallback(fallback.spanClose())

    fun setSpan(span: WrapperSpan) {
        fallback.setSpan(span.unwrapStable())
    }

    fun unwrapStable(): FallbackGroup = fallback

    private fun unwrapNightly(): Nothing = mismatch(0)

    override fun toString(): String = fallback.toString()
}

internal class WrapperIdent private constructor(
    private var fallback: FallbackIdent,
) {
    companion object {
        fun newChecked(string: String, span: WrapperSpan): WrapperIdent {
            return WrapperIdent(FallbackIdent.newChecked(string, span.unwrapStable()))
        }

        fun newRawChecked(string: String, span: WrapperSpan): WrapperIdent {
            return WrapperIdent(FallbackIdent.newRawChecked(string, span.unwrapStable()))
        }

        fun fromFallback(inner: FallbackIdent): WrapperIdent {
            return WrapperIdent(inner)
        }
    }

    fun span(): WrapperSpan = WrapperSpan.fromFallback(fallback.span())

    fun setSpan(span: WrapperSpan) {
        fallback.setSpan(span.unwrapStable())
    }

    fun unwrapStable(): FallbackIdent = fallback

    private fun unwrapNightly(): Nothing = mismatch(0)

    fun contentEquals(other: String): Boolean = fallback.contentEquals(other)

    override fun toString(): String = fallback.toString()

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is WrapperIdent -> fallback == other.fallback
            is String -> fallback.contentEquals(other)
            else -> false
        }
    }

    override fun hashCode(): Int = fallback.hashCode()
}

internal class WrapperLiteral private constructor(
    private var fallback: FallbackLiteral,
) {
    companion object {
        fun fromStrChecked(repr: String): Result<WrapperLiteral> {
            return FallbackLiteral.fromStrChecked(repr).map(::WrapperLiteral)
        }

        fun fromStrUnchecked(repr: String): WrapperLiteral {
            return WrapperLiteral(FallbackLiteral.fromStrUnchecked(repr))
        }

        fun u8Suffixed(n: UByte): WrapperLiteral = WrapperLiteral(FallbackLiteral.u8Suffixed(n))
        fun u16Suffixed(n: UShort): WrapperLiteral = WrapperLiteral(FallbackLiteral.u16Suffixed(n))
        fun u32Suffixed(n: UInt): WrapperLiteral = WrapperLiteral(FallbackLiteral.u32Suffixed(n))
        fun u64Suffixed(n: ULong): WrapperLiteral = WrapperLiteral(FallbackLiteral.u64Suffixed(n))
        fun u128Suffixed(n: ULong): WrapperLiteral = WrapperLiteral(FallbackLiteral.u128Suffixed(n))
        fun usizeSuffixed(n: ULong): WrapperLiteral = WrapperLiteral(FallbackLiteral.usizeSuffixed(n))
        fun i8Suffixed(n: Byte): WrapperLiteral = WrapperLiteral(FallbackLiteral.i8Suffixed(n))
        fun i16Suffixed(n: Short): WrapperLiteral = WrapperLiteral(FallbackLiteral.i16Suffixed(n))
        fun i32Suffixed(n: Int): WrapperLiteral = WrapperLiteral(FallbackLiteral.i32Suffixed(n))
        fun i64Suffixed(n: Long): WrapperLiteral = WrapperLiteral(FallbackLiteral.i64Suffixed(n))
        fun i128Suffixed(n: Long): WrapperLiteral = WrapperLiteral(FallbackLiteral.i128Suffixed(n))
        fun isizeSuffixed(n: Long): WrapperLiteral = WrapperLiteral(FallbackLiteral.isizeSuffixed(n))
        fun f32Suffixed(n: Float): WrapperLiteral = WrapperLiteral(FallbackLiteral.f32Suffixed(n))
        fun f64Suffixed(n: Double): WrapperLiteral = WrapperLiteral(FallbackLiteral.f64Suffixed(n))

        fun u8Unsuffixed(n: UByte): WrapperLiteral = WrapperLiteral(FallbackLiteral.u8Unsuffixed(n))
        fun u16Unsuffixed(n: UShort): WrapperLiteral = WrapperLiteral(FallbackLiteral.u16Unsuffixed(n))
        fun u32Unsuffixed(n: UInt): WrapperLiteral = WrapperLiteral(FallbackLiteral.u32Unsuffixed(n))
        fun u64Unsuffixed(n: ULong): WrapperLiteral = WrapperLiteral(FallbackLiteral.u64Unsuffixed(n))
        fun u128Unsuffixed(n: ULong): WrapperLiteral = WrapperLiteral(FallbackLiteral.u128Unsuffixed(n))
        fun usizeUnsuffixed(n: ULong): WrapperLiteral = WrapperLiteral(FallbackLiteral.usizeUnsuffixed(n))
        fun i8Unsuffixed(n: Byte): WrapperLiteral = WrapperLiteral(FallbackLiteral.i8Unsuffixed(n))
        fun i16Unsuffixed(n: Short): WrapperLiteral = WrapperLiteral(FallbackLiteral.i16Unsuffixed(n))
        fun i32Unsuffixed(n: Int): WrapperLiteral = WrapperLiteral(FallbackLiteral.i32Unsuffixed(n))
        fun i64Unsuffixed(n: Long): WrapperLiteral = WrapperLiteral(FallbackLiteral.i64Unsuffixed(n))
        fun i128Unsuffixed(n: Long): WrapperLiteral = WrapperLiteral(FallbackLiteral.i128Unsuffixed(n))
        fun isizeUnsuffixed(n: Long): WrapperLiteral = WrapperLiteral(FallbackLiteral.isizeUnsuffixed(n))
        fun f32Unsuffixed(n: Float): WrapperLiteral = WrapperLiteral(FallbackLiteral.f32Unsuffixed(n))
        fun f64Unsuffixed(n: Double): WrapperLiteral = WrapperLiteral(FallbackLiteral.f64Unsuffixed(n))

        fun string(string: String): WrapperLiteral = WrapperLiteral(FallbackLiteral.string(string))

        fun character(ch: Char): WrapperLiteral = WrapperLiteral(FallbackLiteral.character(ch))

        fun byteCharacter(byte: UByte): WrapperLiteral = WrapperLiteral(FallbackLiteral.byteCharacter(byte))

        fun byteString(bytes: ByteArray): WrapperLiteral = WrapperLiteral(FallbackLiteral.byteString(bytes))

        fun cString(bytes: ByteArray): WrapperLiteral = WrapperLiteral(FallbackLiteral.cString(bytes))

        fun fromFallback(inner: FallbackLiteral): WrapperLiteral {
            return WrapperLiteral(inner)
        }
    }

    fun span(): WrapperSpan = WrapperSpan.fromFallback(fallback.span())

    fun setSpan(span: WrapperSpan) {
        fallback.setSpan(span.unwrapStable())
    }

    fun subspan(range: IntRange): WrapperSpan? {
        return fallback.subspan(range)?.let(WrapperSpan::fromFallback)
    }

    fun unwrapStable(): FallbackLiteral = fallback

    private fun unwrapNightly(): Nothing = mismatch(0)

    override fun toString(): String = fallback.toString()

    override fun equals(other: Any?): Boolean {
        return other is WrapperLiteral && fallback == other.fallback
    }

    override fun hashCode(): Int = fallback.hashCode()
}

internal fun invalidateCurrentThreadWrapperSpans() {
    if (insideProcMacro()) {
        throw IllegalStateException("invalidateCurrentThreadSpans is not available in procedural macros")
    }
    invalidateCurrentThreadSpans()
}
