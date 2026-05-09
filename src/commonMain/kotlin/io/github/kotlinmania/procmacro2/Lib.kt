// port-lint: source lib.rs
package io.github.kotlinmania.procmacro2

/**
 * An abstract stream of tokens, or more concretely a sequence of token trees.
 *
 * This type provides interfaces for iterating over token trees and for
 * collecting token trees into one stream.
 */
class TokenStream internal constructor(
    internal val inner: FallbackTokenStream,
) : Iterable<TokenTree> {
    companion object {
        /** Returns an empty `TokenStream` containing no token trees. */
        fun new(): TokenStream = TokenStream(FallbackTokenStream.new())

        internal fun newFallback(inner: FallbackTokenStream): TokenStream = TokenStream(inner)

        /** Attempts to break the string into tokens and parse those tokens into a token stream. */
        fun fromString(src: String): Result<TokenStream> {
            return FallbackTokenStream.fromStrChecked(src).map(::TokenStream)
        }

        fun fromTokenTree(token: TokenTree): TokenStream = TokenStream(FallbackTokenStream.fromTokenTree(token))

        fun fromTokenTrees(tokens: Iterable<TokenTree>): TokenStream {
            val stream = FallbackTokenStream.new()
            stream.extendTokenTrees(tokens)
            return TokenStream(stream)
        }

        fun fromTokenStreams(streams: Iterable<TokenStream>): TokenStream {
            val stream = FallbackTokenStream.fromTokenStreams(streams.map { it.inner })
            return TokenStream(stream)
        }
    }

    /** Checks if this `TokenStream` is empty. */
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

    override fun iterator(): TokenStreamIntoIter = TokenStreamIntoIter(inner.intoIter())

    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean {
        return other is TokenStream &&
            inner.iter().asSequence().toList() == other.inner.iter().asSequence().toList()
    }

    override fun hashCode(): Int = inner.iter().asSequence().toList().hashCode()
}

/** Error returned from `TokenStream.fromString`. */
class LexError internal constructor(
    internal val inner: FallbackLexError,
) : IllegalArgumentException("cannot parse string into token stream") {
    fun span(): Span = Span.newFallback(inner.span())

    override fun toString(): String = inner.toString()
}

/** A region of source code, along with macro expansion information. */
class Span internal constructor(
    internal val inner: FallbackSpan,
) {
    companion object {
        internal fun newFallback(inner: FallbackSpan): Span = Span(inner)

        /**
         * The span of the invocation of the current procedural macro.
         *
         * Identifiers created with this span will be resolved as if they were
         * written directly at the macro call location.
         */
        fun callSite(): Span = Span(FallbackSpan.callSite())

        /**
         * The span located at the invocation of the procedural macro, but with
         * local variables, labels, and crate-relative paths resolved at the
         * definition site of the macro.
         */
        fun mixedSite(): Span = Span(FallbackSpan.mixedSite())

        /** A span that resolves at the macro definition site. */
        fun defSite(): Span = Span(FallbackSpan.defSite())
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

    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean {
        return other is Span && inner == other.inner
    }

    override fun hashCode(): Int = inner.hashCode()
}

/** A single token or a delimited sequence of token trees. */
sealed class TokenTree {
    abstract fun span(): Span

    abstract fun setSpan(span: Span): TokenTree

    data class Group(val value: io.github.kotlinmania.procmacro2.Group) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }

    data class Ident(val value: io.github.kotlinmania.procmacro2.Ident) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }

    data class Punct(val value: io.github.kotlinmania.procmacro2.Punct) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }

    data class Literal(val value: io.github.kotlinmania.procmacro2.Literal) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        override fun toString(): String = value.toString()
    }
}

/** A delimited token stream. */
class Group internal constructor(
    internal val inner: FallbackGroup,
) {
    companion object {
        internal fun newFallback(inner: FallbackGroup): Group = Group(inner)
    }

    constructor(delimiter: Delimiter, stream: TokenStream) : this(FallbackGroup(delimiter, stream.inner))

    fun delimiter(): Delimiter = inner.delimiter()

    fun stream(): TokenStream = TokenStream(inner.stream())

    fun span(): Span = Span.newFallback(inner.span())

    fun spanOpen(): Span = Span.newFallback(inner.spanOpen())

    fun spanClose(): Span = Span.newFallback(inner.spanClose())

    fun delimSpan(): DelimSpan = DelimSpan(this)

    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean {
        return other is Group && inner == other.inner
    }

    override fun hashCode(): Int = inner.hashCode()
}

/** Describes how a sequence of token trees is delimited. */
enum class Delimiter {
    /** Parentheses. */
    Parenthesis,

    /** Curly braces. */
    Brace,

    /** Square brackets. */
    Bracket,

    /** An invisible delimiter. */
    None,
}

/** A single punctuation character like `+`, `-` or `#`. */
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

    override fun equals(other: Any?): Boolean {
        return other is Punct && ch == other.ch && spacing == other.spacing
    }

    override fun hashCode(): Int = 31 * ch.hashCode() + spacing.hashCode()
}

private const val PUNCT_CHARS = "!#%&'*+,-./:;<=>?@^|~$"

/** Whether a `Punct` is followed immediately by another `Punct`. */
enum class Spacing {
    /** The punctuation is followed by another token or whitespace. */
    Alone,

    /** The punctuation is immediately followed by another punctuation. */
    Joint,
}

/** A word of code, which may be a keyword or legal variable name. */
class Ident internal constructor(
    internal val inner: FallbackIdent,
) : Comparable<Ident> {
    companion object {
        internal fun newFallback(inner: FallbackIdent): Ident = Ident(inner)

        fun new(string: String, span: Span): Ident = Ident(FallbackIdent.newChecked(string, span.inner))

        fun newRaw(string: String, span: Span): Ident = Ident(FallbackIdent.newRawChecked(string, span.inner))
    }

    fun span(): Span = Span.newFallback(inner.span())

    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    override fun compareTo(other: Ident): Int = toString().compareTo(other.toString())

    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is Ident -> inner == other.inner
            is String -> inner.contentEquals(other)
            else -> false
        }
    }

    override fun hashCode(): Int = toString().hashCode()
}

/** A literal string, character, byte string, integer, or floating-point number. */
class Literal internal constructor(
    internal val inner: FallbackLiteral,
) {
    companion object {
        internal fun newFallback(inner: FallbackLiteral): Literal = Literal(inner)

        fun u8Suffixed(n: UByte): Literal = Literal(FallbackLiteral.u8Suffixed(n))
        fun u16Suffixed(n: UShort): Literal = Literal(FallbackLiteral.u16Suffixed(n))
        fun u32Suffixed(n: UInt): Literal = Literal(FallbackLiteral.u32Suffixed(n))
        fun u64Suffixed(n: ULong): Literal = Literal(FallbackLiteral.u64Suffixed(n))
        fun u128Suffixed(n: ULong): Literal = Literal(FallbackLiteral.u128Suffixed(n))
        fun usizeSuffixed(n: ULong): Literal = Literal(FallbackLiteral.usizeSuffixed(n))
        fun i8Suffixed(n: Byte): Literal = Literal(FallbackLiteral.i8Suffixed(n))
        fun i16Suffixed(n: Short): Literal = Literal(FallbackLiteral.i16Suffixed(n))
        fun i32Suffixed(n: Int): Literal = Literal(FallbackLiteral.i32Suffixed(n))
        fun i64Suffixed(n: Long): Literal = Literal(FallbackLiteral.i64Suffixed(n))
        fun i128Suffixed(n: Long): Literal = Literal(FallbackLiteral.i128Suffixed(n))
        fun isizeSuffixed(n: Long): Literal = Literal(FallbackLiteral.isizeSuffixed(n))
        fun f32Suffixed(n: Float): Literal = Literal(FallbackLiteral.f32Suffixed(n))
        fun f64Suffixed(n: Double): Literal = Literal(FallbackLiteral.f64Suffixed(n))

        fun u8Unsuffixed(n: UByte): Literal = Literal(FallbackLiteral.u8Unsuffixed(n))
        fun u16Unsuffixed(n: UShort): Literal = Literal(FallbackLiteral.u16Unsuffixed(n))
        fun u32Unsuffixed(n: UInt): Literal = Literal(FallbackLiteral.u32Unsuffixed(n))
        fun u64Unsuffixed(n: ULong): Literal = Literal(FallbackLiteral.u64Unsuffixed(n))
        fun u128Unsuffixed(n: ULong): Literal = Literal(FallbackLiteral.u128Unsuffixed(n))
        fun usizeUnsuffixed(n: ULong): Literal = Literal(FallbackLiteral.usizeUnsuffixed(n))
        fun i8Unsuffixed(n: Byte): Literal = Literal(FallbackLiteral.i8Unsuffixed(n))
        fun i16Unsuffixed(n: Short): Literal = Literal(FallbackLiteral.i16Unsuffixed(n))
        fun i32Unsuffixed(n: Int): Literal = Literal(FallbackLiteral.i32Unsuffixed(n))
        fun i64Unsuffixed(n: Long): Literal = Literal(FallbackLiteral.i64Unsuffixed(n))
        fun i128Unsuffixed(n: Long): Literal = Literal(FallbackLiteral.i128Unsuffixed(n))
        fun isizeUnsuffixed(n: Long): Literal = Literal(FallbackLiteral.isizeUnsuffixed(n))
        fun f32Unsuffixed(n: Float): Literal = Literal(FallbackLiteral.f32Unsuffixed(n))
        fun f64Unsuffixed(n: Double): Literal = Literal(FallbackLiteral.f64Unsuffixed(n))

        fun string(string: String): Literal = Literal(FallbackLiteral.string(string))

        fun character(ch: Char): Literal = Literal(FallbackLiteral.character(ch))

        fun byteCharacter(byte: UByte): Literal = Literal(FallbackLiteral.byteCharacter(byte))

        fun byteString(bytes: ByteArray): Literal = Literal(FallbackLiteral.byteString(bytes))

        fun cString(bytes: ByteArray): Literal = Literal(FallbackLiteral.cString(bytes))

        fun fromString(repr: String): Result<Literal> {
            return FallbackLiteral.fromStrChecked(repr).map(::Literal)
        }

        fun fromStrUnchecked(repr: String): Literal = Literal(FallbackLiteral.fromStrUnchecked(repr))
    }

    fun span(): Span = Span.newFallback(inner.span())

    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    fun subspan(range: IntRange): Span? = inner.subspan(range)?.let(Span::newFallback)

    fun strValue(): Result<String> {
        val repr = toString()
        if (repr.startsWith('"') && repr.endsWith('"') && repr.length >= 2) {
            val quoted = repr.substring(1, repr.length - 1)
            val value = StringBuilder(quoted.length)
            var error: EscapeError? = null
            unescapeStr(quoted) { _, res ->
                when (res) {
                    is EscapeResult.Ok -> value.append(res.value)
                    is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                }
            }
            return error?.let { Result.failure(ConversionErrorKind.FailedToUnescape(it)) }
                ?: Result.success(value.toString())
        }
        if (repr.startsWith('r')) {
            val raw = getRaw(repr.substring(1))
            if (raw != null) {
                return Result.success(raw)
            }
        }
        return Result.failure(ConversionErrorKind.InvalidLiteralKind)
    }

    fun cstrValue(): Result<ByteArray> {
        val repr = toString()
        if (repr.startsWith("cr")) {
            val raw = getRaw(repr.substring(2))
            if (raw != null) {
                return Result.success(raw.encodeToByteArray() + byteArrayOf(0))
            }
        }
        return Result.failure(ConversionErrorKind.InvalidLiteralKind)
    }

    fun byteStrValue(): Result<ByteArray> {
        val repr = toString()
        if (repr.startsWith("br")) {
            val raw = getRaw(repr.substring(2))
            if (raw != null) {
                return Result.success(raw.encodeToByteArray())
            }
        }
        return Result.failure(ConversionErrorKind.InvalidLiteralKind)
    }

    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean {
        return other is Literal && inner == other.inner
    }

    override fun hashCode(): Int = inner.hashCode()
}

/** Error when retrieving a string literal's unescaped value. */
sealed class ConversionErrorKind(message: String) : IllegalArgumentException(message) {
    data class FailedToUnescape(val error: EscapeError) : ConversionErrorKind(error.name)

    data object InvalidLiteralKind : ConversionErrorKind("invalid literal kind")
}

private fun getRaw(repr: String): String? {
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

/** An iterator over `TokenStream`'s `TokenTree`s. */
class TokenStreamIntoIter internal constructor(
    private val inner: RcVecIntoIter<TokenTree>,
) : Iterator<TokenTree> {
    override fun hasNext(): Boolean = inner.hasNext()

    override fun next(): TokenTree = inner.next()

    fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()

    override fun toString(): String {
        return "TokenStream ${inner.remaining().joinToString(prefix = "[", postfix = "]")}"
    }
}
