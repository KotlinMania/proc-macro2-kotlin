// port-lint: source lib.rs

package io.github.kotlinmania.procmacro2

class TokenStreamParseResult internal constructor(
    val value: TokenStream?,
    val error: String?,
) {
    init {
        require((value == null) != (error == null)) {
            "TokenStreamParseResult must carry exactly one of value or error"
        }
    }

    fun isSuccess(): Boolean = value != null

    fun isFailure(): Boolean = error != null

    fun getOrThrow(): TokenStream =
        value ?: throw IllegalStateException(error)
}

class LiteralParseResult internal constructor(
    val value: Literal?,
    val error: String?,
) {
    init {
        require((value == null) != (error == null)) {
            "LiteralParseResult must carry exactly one of value or error"
        }
    }

    fun isSuccess(): Boolean = value != null

    fun isFailure(): Boolean = error != null

    fun getOrThrow(): Literal =
        value ?: throw IllegalStateException(error)
}

class StringParseResult internal constructor(
    val value: String?,
    val error: String?,
) {
    init {
        require((value == null) != (error == null)) {
            "StringParseResult must carry exactly one of value or error"
        }
    }

    fun isSuccess(): Boolean = value != null

    fun isFailure(): Boolean = error != null

    fun getOrThrow(): String =
        value ?: throw IllegalStateException(error)
}

class ByteArrayParseResult internal constructor(
    val value: ByteArray?,
    val error: String?,
) {
    init {
        require((value == null) != (error == null)) {
            "ByteArrayParseResult must carry exactly one of value or error"
        }
    }

    fun isSuccess(): Boolean = value != null

    fun isFailure(): Boolean = error != null

    fun getOrThrow(): ByteArray =
        value ?: throw IllegalStateException(error)
}

class TokenStream internal constructor(
    internal val inner: WrapperTokenStream,
) : Iterable<TokenTree> {
    companion object {
        fun new(): TokenStream =
            TokenStream(WrapperTokenStream.Fallback(FallbackTokenStream.new()))

        internal fun newFallback(inner: FallbackTokenStream): TokenStream =
            TokenStream(WrapperTokenStream.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.TokenStream): TokenStream =
            TokenStream(WrapperTokenStream.Compiler(inner))

        fun fromString(src: String): TokenStreamParseResult {
            val result = FallbackTokenStream.fromStrChecked(src)
            return if (result.isSuccess) {
                TokenStreamParseResult(TokenStream(WrapperTokenStream.Fallback(result.getOrThrow())), null)
            } else {
                TokenStreamParseResult(null, result.exceptionOrNull()?.message ?: "cannot parse string into token stream")
            }
        }

        fun fromTokenTree(token: TokenTree): TokenStream =
            TokenStream(WrapperTokenStream.Fallback(FallbackTokenStream.fromTokenTree(token)))

        fun fromTokenTrees(tokens: Iterable<TokenTree>): TokenStream {
            val stream = FallbackTokenStream.new()
            stream.extendTokenTrees(tokens)
            return TokenStream(WrapperTokenStream.Fallback(stream))
        }

        fun fromTokenStreams(streams: Iterable<TokenStream>): TokenStream {
            val stream = FallbackTokenStream.new()
            stream.extendTokenStreams(streams.map { it.inner.asFallbackStream() })
            return TokenStream(WrapperTokenStream.Fallback(stream))
        }
    }

    fun isEmpty(): Boolean = inner.isEmpty()

    fun extendTokenTrees(tokens: Iterable<TokenTree>) {
        inner.extendTokenTrees(tokens)
    }

    fun extendTokenStreams(streams: Iterable<TokenStream>) {
        inner.extendTokenStreams(streams.map { it.inner })
    }

    fun extendGroups(tokens: Iterable<Group>) {
        extendTokenTrees(tokens.map(TokenTree::Group))
    }

    fun extendIdents(tokens: Iterable<Ident>) {
        extendTokenTrees(tokens.map(TokenTree::Ident))
    }

    fun extendPuncts(tokens: Iterable<Punct>) {
        extendTokenTrees(tokens.map(TokenTree::Punct))
    }

    fun extendLiterals(tokens: Iterable<Literal>) {
        extendTokenTrees(tokens.map(TokenTree::Literal))
    }

    override fun iterator(): TokenStreamIntoIter = TokenStreamIntoIter(inner)

    fun clone(): TokenStream = TokenStream(inner.clone_())

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean =
        other is TokenStream &&
            inner.toTokenStreamEqualityItems() ==
            other.inner.toTokenStreamEqualityItems()

    override fun hashCode(): Int =
        inner.toTokenStreamEqualityItems().hashCode()
}

internal class LexError internal constructor(
    internal val inner: WrapperLexError,
) : IllegalArgumentException("cannot parse string into token stream") {
    fun span(): Span = Span(inner.span())

    override fun toString(): String = inner.toString_()
}

class Span internal constructor(
    internal val inner: WrapperSpan,
) {
    companion object {
        internal fun newFallback(inner: FallbackSpan): Span = Span(WrapperSpan.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Span): Span = Span(WrapperSpan.Compiler(inner))

        fun callSite(): Span =
            if (useCompiler()) {
                Span(
                    WrapperSpan.Compiler(
                        io.github.kotlinmania.procmacro.Span
                            .callSite(),
                    ),
                )
            } else {
                Span(WrapperSpan.Fallback(FallbackSpan.callSite()))
            }

        fun mixedSite(): Span =
            if (useCompiler()) {
                Span(
                    WrapperSpan.Compiler(
                        io.github.kotlinmania.procmacro.Span
                            .mixedSite(),
                    ),
                )
            } else {
                Span(WrapperSpan.Fallback(FallbackSpan.mixedSite()))
            }

        fun defSite(): Span =
            if (useCompiler()) {
                Span(
                    WrapperSpan.Compiler(
                        io.github.kotlinmania.procmacro.Span
                            .defSite(),
                    ),
                )
            } else {
                Span(WrapperSpan.Fallback(FallbackSpan.defSite()))
            }
    }

    fun resolvedAt(other: Span): Span = Span(inner.resolvedAt(other.inner))

    fun locatedAt(other: Span): Span = Span(inner.locatedAt(other.inner))

    fun byteRange(): IntRange = inner.byteRange()

    fun start(): LineColumn = inner.start()

    fun end(): LineColumn = inner.end()

    fun file(): String = inner.file()

    fun localFile(): String? = inner.localFile()

    fun join(other: Span): Span? = inner.join(other.inner)?.let(::Span)

    fun sourceText(): String? = inner.sourceText()

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean = other is Span && inner.eq(other.inner)

    override fun hashCode(): Int = inner.hashCode_()
}

sealed class TokenTree {
    abstract fun span(): Span

    abstract fun setSpan(span: Span): TokenTree

    data class Group(
        val value: io.github.kotlinmania.procmacro2.Group,
    ) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }

    data class Ident(
        val value: io.github.kotlinmania.procmacro2.Ident,
    ) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }

    data class Punct(
        val value: io.github.kotlinmania.procmacro2.Punct,
    ) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }

    data class Literal(
        val value: io.github.kotlinmania.procmacro2.Literal,
    ) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }
}

class Group internal constructor(
    internal val inner: WrapperGroup,
) {
    companion object {
        internal fun newFallback(inner: FallbackGroup): Group = Group(WrapperGroup.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Group): Group = Group(WrapperGroup.Compiler(inner))
    }

    constructor(delimiter: Delimiter, stream: TokenStream) : this(
        WrapperGroup.Fallback(FallbackGroup(delimiter, stream.inner.asFallbackStream())),
    )

    fun delimiter(): Delimiter = inner.delimiter()

    fun stream(): TokenStream = TokenStream(inner.stream())

    fun span(): Span = Span(inner.span())

    fun spanOpen(): Span = Span(inner.spanOpen())

    fun spanClose(): Span = Span(inner.spanClose())

    fun delimSpan(): DelimSpan = DelimSpan(this)

    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean = other is Group && inner.eq(other.inner)

    override fun hashCode(): Int = inner.hashCode_()
}

enum class Delimiter {
    Parenthesis,
    Brace,
    Bracket,
    None,
}

class Punct(
    private val ch: Char,
    private var spacing: Spacing,
    private var span: Span = Span.callSite(),
) {
    init {
        require(ch in PUNCT_CHARS) { "unsupported proc macro punctuation character '$ch'" }
    }

    fun asChar(): Char = ch

    fun spacing(): Spacing = spacing

    fun span(): Span = span

    fun setSpan(span: Span) {
        this.span = span
    }

    override fun toString(): String = ch.toString()

    override fun equals(other: Any?): Boolean = other is Punct && ch == other.ch && spacing == other.spacing

    override fun hashCode(): Int = 31 * ch.hashCode() + spacing.hashCode()
}

private const val PUNCT_CHARS = "!#%&'*+,-./:;<=>?@^|~$"

enum class Spacing {
    Alone,
    Joint,
}

class Ident internal constructor(
    internal val inner: WrapperIdent,
) : Comparable<Ident> {
    companion object {
        internal fun newFallback(inner: FallbackIdent): Ident = Ident(WrapperIdent.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Ident): Ident = Ident(WrapperIdent.Compiler(inner))

        fun new(string: String, span: Span): Ident =
            if (useCompiler()) {
                Ident(
                    WrapperIdent.Compiler(
                        io.github.kotlinmania.procmacro.Ident
                            .new(string, span.inner.toCompilerSpan()),
                    ),
                )
            } else {
                Ident(WrapperIdent.Fallback(FallbackIdent.newChecked(string, span.inner.toFallbackSpan())))
            }

        fun newRaw(string: String, span: Span): Ident =
            if (useCompiler()) {
                Ident(
                    WrapperIdent.Compiler(
                        io.github.kotlinmania.procmacro.Ident
                            .newRaw(string, span.inner.toCompilerSpan()),
                    ),
                )
            } else {
                Ident(WrapperIdent.Fallback(FallbackIdent.newRawChecked(string, span.inner.toFallbackSpan())))
            }
    }

    fun span(): Span = Span(inner.span())

    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    override fun compareTo(other: Ident): Int = toString().compareTo(other.toString())

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean =
        when (other) {
            is Ident -> inner.toString_() == other.inner.toString_()
            is String -> inner.contentEquals(other)
            else -> false
        }

    override fun hashCode(): Int = toString().hashCode()
}

class Literal internal constructor(
    internal val inner: WrapperLiteral,
) {
    companion object {
        internal fun newFallback(inner: FallbackLiteral): Literal = Literal(WrapperLiteral.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Literal): Literal = Literal(WrapperLiteral.Compiler(inner))

        fun u8Suffixed(n: UByte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u8Suffixed(n)))

        fun u16Suffixed(n: UShort): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u16Suffixed(n)))

        fun u32Suffixed(n: UInt): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u32Suffixed(n)))

        fun u64Suffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u64Suffixed(n)))

        fun u128Suffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u128Suffixed(n)))

        fun usizeSuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.usizeSuffixed(n)))

        fun i8Suffixed(n: Byte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i8Suffixed(n)))

        fun i16Suffixed(n: Short): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i16Suffixed(n)))

        fun i32Suffixed(n: Int): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i32Suffixed(n)))

        fun i64Suffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i64Suffixed(n)))

        fun i128Suffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i128Suffixed(n)))

        fun isizeSuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.isizeSuffixed(n)))

        fun f32Suffixed(n: Float): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f32Suffixed(n)))

        fun f64Suffixed(n: Double): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f64Suffixed(n)))

        fun u8Unsuffixed(n: UByte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u8Unsuffixed(n)))

        fun u16Unsuffixed(n: UShort): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u16Unsuffixed(n)))

        fun u32Unsuffixed(n: UInt): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u32Unsuffixed(n)))

        fun u64Unsuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u64Unsuffixed(n)))

        fun u128Unsuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u128Unsuffixed(n)))

        fun usizeUnsuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.usizeUnsuffixed(n)))

        fun i8Unsuffixed(n: Byte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i8Unsuffixed(n)))

        fun i16Unsuffixed(n: Short): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i16Unsuffixed(n)))

        fun i32Unsuffixed(n: Int): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i32Unsuffixed(n)))

        fun i64Unsuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i64Unsuffixed(n)))

        fun i128Unsuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i128Unsuffixed(n)))

        fun isizeUnsuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.isizeUnsuffixed(n)))

        fun f32Unsuffixed(n: Float): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f32Unsuffixed(n)))

        fun f64Unsuffixed(n: Double): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f64Unsuffixed(n)))

        fun string(string: String): Literal =
            if (useCompiler()) {
                Literal(
                    WrapperLiteral.Compiler(
                        io.github.kotlinmania.procmacro.Literal
                            .string(string),
                    ),
                )
            } else {
                Literal(WrapperLiteral.Fallback(FallbackLiteral.string(string)))
            }

        fun character(ch: Char): Literal =
            if (useCompiler()) {
                Literal(
                    WrapperLiteral.Compiler(
                        io.github.kotlinmania.procmacro.Literal
                            .character(ch),
                    ),
                )
            } else {
                Literal(WrapperLiteral.Fallback(FallbackLiteral.character(ch)))
            }

        fun byteCharacter(byte: UByte): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.byteCharacter(byte)))

        fun byteString(bytes: ByteArray): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.byteString(bytes)))

        fun cString(bytes: ByteArray): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.cString(bytes)))

        fun fromString(repr: String): LiteralParseResult {
            val result = FallbackLiteral.fromStrChecked(repr)
            return if (result.isSuccess) {
                LiteralParseResult(Literal(WrapperLiteral.Fallback(result.getOrThrow())), null)
            } else {
                LiteralParseResult(null, result.exceptionOrNull()?.message ?: "cannot parse string into literal")
            }
        }

        fun fromStrUnchecked(repr: String): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.fromStrUnchecked(repr)))
    }

    fun span(): Span = Span(inner.span())

    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    fun subspan(range: IntRange): Span? = inner.subspan(range)?.let(::Span)

    fun strValue(): StringParseResult {
        val value = inner.strValue_()
        return if (value != null) {
            StringParseResult(value, null)
        } else {
            StringParseResult(null, "invalid literal kind")
        }
    }

    fun cstrValue(): ByteArrayParseResult {
        val value = inner.cstrValue_()
        return if (value != null) {
            ByteArrayParseResult(value, null)
        } else {
            ByteArrayParseResult(null, "invalid literal kind")
        }
    }

    fun byteStrValue(): ByteArrayParseResult {
        val value = inner.byteStrValue_()
        return if (value != null) {
            ByteArrayParseResult(value, null)
        } else {
            ByteArrayParseResult(null, "invalid literal kind")
        }
    }

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean = other is Literal && inner.eq(other.inner)

    override fun hashCode(): Int = inner.toString_().hashCode()
}

internal sealed class ConversionErrorKind(
    message: String,
) : IllegalArgumentException(message) {
    data class FailedToUnescape(
        val error: EscapeError,
    ) : ConversionErrorKind(error.name)

    data object InvalidLiteralKind : ConversionErrorKind("invalid literal kind")
}

internal fun getRaw(repr: String): String? {
    val pounds = repr.takeWhile { it == '#' }.length
    return if (
        repr.length >= pounds + 2 + pounds &&
        repr.getOrNull(pounds) == '"' &&
        repr.dropLast(pounds).endsWith('"') &&
        repr.takeLast(pounds).all { it == '#' }
    ) {
        repr.substring(pounds + 1, repr.length - pounds - 1)
    } else {
        null
    }
}

class TokenStreamIntoIter internal constructor(
    private val inner: WrapperTokenStream,
) : Iterator<TokenTree> {
    private val iter = inner.intoIterW()

    override fun hasNext(): Boolean = iter.hasNext()

    override fun next(): TokenTree {
        val wrapperTree = iter.next()
        return wrapperTree.asFallbackTree()
    }

    internal fun sizeHint(): Pair<Int, Int?> {
        var count = 0
        val tmp = inner.intoIterW()
        while (tmp.hasNext()) {
            tmp.next()
            count++
        }
        return count to count
    }

    override fun toString(): String {
        val items = mutableListOf<String>()
        val tmp = inner.intoIterW()
        while (tmp.hasNext()) {
            items.add(tmp.next().asFallbackTree().toString())
        }
        return "TokenStream [${items.joinToString(", ")}]"
    }
}
