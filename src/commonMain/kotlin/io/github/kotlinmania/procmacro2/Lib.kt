// port-lint: source lib.rs

/**
 * Kotlin Multiplatform port of the upstream Rust
 * [proc-macro2](https://crates.io/crates/proc-macro2) crate. The original
 * library is a wrapper around the compiler's in-tree procedural macro API,
 * exposed in Rust as the `proc-macro` crate. This library serves two purposes:
 *
 * - **Bring proc-macro-like functionality to other contexts like build scripts
 *   and regular application code.** Types from the in-tree procedural macro
 *   crate are entirely specific to procedural macros and cannot exist in code
 *   outside of a procedural macro. Meanwhile types in this library may exist
 *   anywhere, including non-macro code. By developing foundational libraries
 *   like syn and quote against this crate rather than the compiler's in-tree
 *   crate, the procedural macro ecosystem becomes easily applicable to many
 *   other use cases and the libraries can avoid reimplementing non-macro
 *   equivalents.
 *
 * - **Make procedural macros unit testable.** As a consequence of being specific
 *   to procedural macros, nothing that uses the compiler's in-tree procedural
 *   macro crate can be executed from a unit test. In order for helper libraries
 *   or components of a macro to be testable in isolation, they must be
 *   implemented using this library.
 *
 * ## Usage
 *
 * The skeleton of a typical procedural macro typically looks like this:
 *
 * ```
 * // @ProcMacroDerive("MyDerive")
 * fun myDerive(input: TokenStream): TokenStream {
 *     val output: TokenStream = run {
 *         // transform input
 *         input
 *     }
 *     return output
 * }
 * ```
 *
 * If parsing with a Kotlin port of syn you would use a `parseMacroInput`
 * equivalent instead to propagate parse errors correctly back to the compiler
 * when parsing fails.
 *
 * ## Unstable features
 *
 * The default feature set of upstream proc-macro2 tracks the most recent
 * stable compiler API; functionality in the compiler's in-tree procedural
 * macro crate that is not yet stable is not exposed by default. In the
 * upstream library, opting into the additional APIs available in the most
 * recent nightly compiler requires passing a dedicated semver-exempt config
 * flag to the Rust compiler and to any crate that depends on this one.
 * Kotlin Multiplatform has no equivalent feature-flag mechanism, so the port
 * simply exposes the always-stable surface plus whatever the fallback
 * implementation can compute on its own.
 *
 * ## Thread-safety
 *
 * Most types in this package are not safe to share across threads. The upstream
 * Rust implementation relies on thread-local source-map data; the Kotlin port
 * uses process-wide state for that map, so any cross-thread sharing of `Span`
 * values from concurrently-parsed sources risks reading stale entries.
 */
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

/**
 * An abstract stream of tokens, or more concretely a sequence of token trees.
 *
 * This type provides interfaces for iterating over token trees and for
 * collecting token trees into one stream.
 *
 * Token stream is both the input and output of a procedural macro definition.
 */
class TokenStream internal constructor(
    internal val inner: FallbackTokenStream,
) : Iterable<TokenTree> {
    companion object {
        /** Returns an empty [TokenStream] containing no token trees. */
        fun new(): TokenStream = TokenStream(FallbackTokenStream.new())

        internal fun newFallback(inner: FallbackTokenStream): TokenStream = TokenStream(inner)

        /**
         * Attempts to break the string into tokens and parse those tokens into a
         * token stream.
         *
         * May fail for a number of reasons, for example, if the string contains
         * unbalanced delimiters or characters not existing in the language.
         *
         * NOTE: Some errors may cause panics instead of returning a failed
         * [Result]. We reserve the right to change these errors into [LexError]s
         * later.
         */
        fun fromString(src: String): TokenStreamParseResult {
            val result = FallbackTokenStream.fromStrChecked(src)
            return if (result.isSuccess) {
                TokenStreamParseResult(TokenStream(result.getOrThrow()), null)
            } else {
                TokenStreamParseResult(null, result.exceptionOrNull()?.message ?: "cannot parse string into token stream")
            }
        }

        /** Builds a single-token stream from one [TokenTree]. */
        fun fromTokenTree(token: TokenTree): TokenStream = TokenStream(FallbackTokenStream.fromTokenTree(token))

        /** Collects a number of token trees into a single stream. */
        fun fromTokenTrees(tokens: Iterable<TokenTree>): TokenStream {
            val stream = FallbackTokenStream.new()
            stream.extendTokenTrees(tokens)
            return TokenStream(stream)
        }

        /** Concatenates a number of streams into a single stream. */
        fun fromTokenStreams(streams: Iterable<TokenStream>): TokenStream {
            val stream = FallbackTokenStream.fromTokenStreams(streams.map { it.inner })
            return TokenStream(stream)
        }
    }

    /** Checks if this [TokenStream] is empty. */
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

    /**
     * Prints the token stream as a string that is supposed to be losslessly
     * convertible back into the same token stream (modulo spans), except for
     * possibly groups with [Delimiter.None] delimiters and negative numeric
     * literals.
     */
    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean =
        other is TokenStream &&
            inner.iter().asSequence().toList() ==
            other.inner
                .iter()
                .asSequence()
                .toList()

    override fun hashCode(): Int =
        inner
            .iter()
            .asSequence()
            .toList()
            .hashCode()
}

/** Error returned from [TokenStream.fromString]. */
internal class LexError internal constructor(
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
         * written directly at the macro call location (call-site hygiene) and
         * other code at the macro call site will be able to refer to them as
         * well.
         */
        fun callSite(): Span = Span(FallbackSpan.callSite())

        /**
         * The span located at the invocation of the procedural macro, but with
         * local variables, labels, and crate-relative paths resolved at the
         * definition site of the macro. This is the same hygiene behavior as
         * Rust's declarative macros.
         */
        fun mixedSite(): Span = Span(FallbackSpan.mixedSite())

        /**
         * A span that resolves at the macro definition site.
         *
         * This method is semver exempt and not exposed by default in upstream
         * Rust; the Kotlin port has no feature gates and always exposes it.
         */
        fun defSite(): Span = Span(FallbackSpan.defSite())
    }

    /**
     * Creates a new span with the same line/column information as this span but
     * that resolves symbols as though it were at `other`.
     */
    fun resolvedAt(other: Span): Span = Span(inner.resolvedAt(other.inner))

    /**
     * Creates a new span with the same name resolution behavior as this span
     * but with the line/column information of `other`.
     */
    fun locatedAt(other: Span): Span = Span(inner.locatedAt(other.inner))

    /**
     * Returns the span's byte position range in the source file.
     *
     * The byte range is always accurate for code parsed by this Kotlin port,
     * since there is no embedding compiler to defer to.
     */
    fun byteRange(): IntRange = inner.byteRange()

    /** Get the starting line/column in the source file for this span. */
    fun start(): LineColumn = inner.start()

    /** Get the ending line/column in the source file for this span. */
    fun end(): LineColumn = inner.end()

    /**
     * The path to the source file in which this span occurs, for display
     * purposes.
     *
     * This might not correspond to a valid file system path. It might be
     * remapped, or might be an artificial path such as `"<macro expansion>"`.
     */
    fun file(): String = inner.file()

    /**
     * The path to the source file in which this span occurs on disk.
     *
     * This is the actual path on disk. It is unaffected by path remapping.
     *
     * This path should not be embedded in the output of the macro; prefer
     * [file] instead.
     */
    fun localFile(): String? = inner.localFile()

    /**
     * Create a new span encompassing this span and `other`.
     *
     * Returns `null` if this span and `other` are from different files.
     */
    fun join(other: Span): Span? = inner.join(other.inner)?.let(::Span)

    /**
     * Returns the source text behind a span. This preserves the original source
     * code, including spaces and comments. It only returns a result if the span
     * corresponds to real source code.
     *
     * Note: The observable result of a macro should only rely on the tokens and
     * not on this source text. The result of this function is a best effort to
     * be used for diagnostics only.
     */
    fun sourceText(): String? = inner.sourceText()

    /** Prints a span in a form convenient for debugging. */
    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean = other is Span && inner == other.inner

    override fun hashCode(): Int = inner.hashCode()
}

/** A single token or a delimited sequence of token trees (e.g. `[1, (), ..]`). */
sealed class TokenTree {
    /**
     * Returns the span of this tree, delegating to the `span` method of the
     * contained token or a delimited stream.
     */
    abstract fun span(): Span

    /**
     * Configures the span for *only this token*.
     *
     * Note that if this token is a [Group] then this method will not configure
     * the span of each of the internal tokens, it will simply delegate to the
     * `setSpan` method of each variant.
     */
    abstract fun setSpan(span: Span): TokenTree

    /** A token stream surrounded by bracket delimiters. */
    data class Group(
        val value: io.github.kotlinmania.procmacro2.Group,
    ) : TokenTree() {
        override fun span(): Span = value.span()

        override fun setSpan(span: Span): TokenTree {
            value.setSpan(span)
            return this
        }

        /**
         * Prints the token tree as a string that is supposed to be losslessly
         * convertible back into the same token tree (modulo spans), except for
         * possibly groups with [Delimiter.None] delimiters and negative
         * numeric literals.
         */
        override fun toString(): String = value.toString()
    }

    /** An identifier. */
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

    /** A single punctuation character (`+`, `,`, `$`, etc.). */
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

    /** A literal character (`'a'`), string (`"hello"`), number (`2.3`), etc. */
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

/**
 * A delimited token stream.
 *
 * A [Group] internally contains a [TokenStream] which is surrounded by
 * [Delimiter]s.
 */
class Group internal constructor(
    internal val inner: FallbackGroup,
) {
    companion object {
        internal fun newFallback(inner: FallbackGroup): Group = Group(inner)
    }

    /**
     * Creates a new [Group] with the given delimiter and token stream.
     *
     * This constructor will set the span for this group to [Span.callSite]. To
     * change the span you can use the [setSpan] method below.
     */
    constructor(delimiter: Delimiter, stream: TokenStream) : this(FallbackGroup(delimiter, stream.inner))

    /**
     * Returns the punctuation used as the delimiter for this group: a set of
     * parentheses, square brackets, or curly braces.
     */
    fun delimiter(): Delimiter = inner.delimiter()

    /**
     * Returns the [TokenStream] of tokens that are delimited in this [Group].
     *
     * Note that the returned token stream does not include the delimiter
     * returned above.
     */
    fun stream(): TokenStream = TokenStream(inner.stream())

    /**
     * Returns the span for the delimiters of this token stream, spanning the
     * entire [Group].
     */
    fun span(): Span = Span.newFallback(inner.span())

    /** Returns the span pointing to the opening delimiter of this group. */
    fun spanOpen(): Span = Span.newFallback(inner.spanOpen())

    /** Returns the span pointing to the closing delimiter of this group. */
    fun spanClose(): Span = Span.newFallback(inner.spanClose())

    /**
     * Returns an object that holds this group's [spanOpen] and [spanClose]
     * together (in a more compact representation than holding those 2 spans
     * individually).
     */
    fun delimSpan(): DelimSpan = DelimSpan(this)

    /**
     * Configures the span for this group's delimiters, but not its internal
     * tokens.
     *
     * This method will **not** set the span of all the internal tokens spanned
     * by this group, but rather it will only set the span of the delimiter
     * tokens at the level of the [Group].
     */
    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean = other is Group && inner == other.inner

    override fun hashCode(): Int = inner.hashCode()
}

/** Describes how a sequence of token trees is delimited. */
enum class Delimiter {
    /** `( ... )` */
    Parenthesis,

    /** `{ ... }` */
    Brace,

    /** `[ ... ]` */
    Bracket,

    /**
     * `∅ ... ∅`
     *
     * An invisible delimiter, that may, for example, appear around tokens
     * coming from a "macro variable" `$var`. It is important to preserve
     * operator priorities in cases like `$var * 3` where `$var` is `1 + 2`.
     * Invisible delimiters may not survive roundtrip of a token stream through
     * a string.
     *
     * Note: rustc currently can ignore the grouping of tokens delimited by
     * `None` in the output of a procedural macro. Only `None`-delimited groups
     * created by a declarative macro in the input of a procedural macro are
     * preserved, and only in very specific circumstances. Any `None`-delimited
     * groups (re)created by a procedural macro will therefore not preserve
     * operator priorities as indicated above. The other [Delimiter] variants
     * should be used instead in this context. This is an upstream Rust bug; for
     * details, see [rust-lang/rust#67062](https://github.com/rust-lang/rust/issues/67062).
     */
    None,
}

/**
 * A [Punct] is a single punctuation character like `+`, `-` or `#`.
 *
 * Multicharacter operators like `+=` are represented as two instances of
 * [Punct] with different forms of [Spacing] returned.
 */
class Punct(
    private val ch: Char,
    private var spacing: Spacing,
    private var span: Span = Span.callSite(),
) {
    init {
        require(ch in PUNCT_CHARS) { "unsupported proc macro punctuation character '$ch'" }
    }

    /** Returns the value of this punctuation character as a [Char]. */
    fun asChar(): Char = ch

    /**
     * Returns the spacing of this punctuation character, indicating whether
     * it's immediately followed by another [Punct] in the token stream, so
     * they can potentially be combined into a multicharacter operator
     * ([Spacing.Joint]), or it's followed by some other token or whitespace
     * ([Spacing.Alone]) so the operator has certainly ended.
     */
    fun spacing(): Spacing = spacing

    /** Returns the span for this punctuation character. */
    fun span(): Span = span

    /** Configure the span for this punctuation character. */
    fun setSpan(span: Span) {
        this.span = span
    }

    /**
     * Prints the punctuation character as a string that should be losslessly
     * convertible back into the same character.
     */
    override fun toString(): String = ch.toString()

    override fun equals(other: Any?): Boolean = other is Punct && ch == other.ch && spacing == other.spacing

    override fun hashCode(): Int = 31 * ch.hashCode() + spacing.hashCode()
}

private const val PUNCT_CHARS = "!#%&'*+,-./:;<=>?@^|~$"

/**
 * Whether a [Punct] is followed immediately by another [Punct] or followed by
 * another token or whitespace.
 */
enum class Spacing {
    /** E.g. `+` is [Alone] in `+ =`, `+ident` or `+()`. */
    Alone,

    /**
     * E.g. `+` is [Joint] in `+=` or `'` is [Joint] in `'#`.
     *
     * Additionally, a single-quote [Punct] can join with an adjacent
     * identifier to form a single labelled token of the form `'name`.
     */
    Joint,
}

/**
 * A word of code, which may be a keyword or legal variable name.
 *
 * An identifier consists of at least one Unicode code point matching the
 * Unicode Standard Annex 31 identifier rules: a starter character followed
 * by zero or more continue characters.
 *
 * - The empty string is not an identifier. Use a nullable [Ident] reference.
 * - A leading-apostrophe labelled token is not an identifier.
 *
 * An identifier constructed with [Ident.new] is permitted to be a Rust
 * keyword, though parsing one rejects Rust keywords.
 *
 * ## Examples
 *
 * A new ident can be created from a string using the [Ident.new] factory. A
 * span must be provided explicitly which governs the name resolution behavior
 * of the resulting identifier.
 *
 * ```
 * val callIdent = Ident.new("calligraphy", Span.callSite())
 * println(callIdent)
 * ```
 *
 * A string representation of the ident is available through [toString]:
 *
 * ```
 * val ident = Ident.new("calligraphy", Span.callSite())
 * val identString = ident.toString()
 * if (identString.length > 60) {
 *     println("Very long identifier: $identString")
 * }
 * ```
 */
class Ident internal constructor(
    internal val inner: FallbackIdent,
) : Comparable<Ident> {
    companion object {
        internal fun newFallback(inner: FallbackIdent): Ident = Ident(inner)

        /**
         * Creates a new [Ident] with the given `string` as well as the
         * specified `span`.
         *
         * The `string` argument must be a valid identifier permitted by the
         * language, otherwise the function will throw [IllegalArgumentException].
         *
         * As of this time [Span.callSite] explicitly opts-in to "call-site"
         * hygiene meaning that identifiers created with this span will be
         * resolved as if they were written directly at the location of the
         * macro call, and other code at the macro call site will be able to
         * refer to them as well.
         *
         * Later spans like [Span.defSite] will allow to opt-in to
         * "definition-site" hygiene meaning that identifiers created with this
         * span will be resolved at the location of the macro definition and
         * other code at the macro call site will not be able to refer to them.
         *
         * Due to the current importance of hygiene this constructor, unlike
         * other tokens, requires a [Span] to be specified at construction.
         */
        fun new(string: String, span: Span): Ident = Ident(FallbackIdent.newChecked(string, span.inner))

        /**
         * Same as [Ident.new], but creates a raw identifier (`r#ident`). The
         * `string` argument must be a valid identifier permitted by the
         * language (including keywords). Keywords which are usable in path
         * segments (e.g. `self`, `super`) are not supported, and will cause an
         * exception.
         */
        fun newRaw(string: String, span: Span): Ident = Ident(FallbackIdent.newRawChecked(string, span.inner))
    }

    /** Returns the span of this [Ident]. */
    fun span(): Span = Span.newFallback(inner.span())

    /**
     * Configures the span of this [Ident], possibly changing its hygiene
     * context.
     */
    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    override fun compareTo(other: Ident): Int = toString().compareTo(other.toString())

    /**
     * Prints the identifier as a string that should be losslessly convertible
     * back into the same identifier.
     */
    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean =
        when (other) {
            is Ident -> inner == other.inner
            is String -> inner.contentEquals(other)
            else -> false
        }

    override fun hashCode(): Int = toString().hashCode()
}

/**
 * A literal string (`"hello"`), byte string (`b"hello"`), character (`'a'`),
 * byte character (`b'a'`), an integer or floating point number with or
 * without a suffix (`1`, `1u8`, `2.3`, `2.3f32`).
 *
 * Boolean literals like `true` and `false` do not belong here, they are
 * [Ident]s.
 */
class Literal internal constructor(
    internal val inner: FallbackLiteral,
) {
    companion object {
        internal fun newFallback(inner: FallbackLiteral): Literal = Literal(inner)

        // Suffixed integer literals.
        //
        // Each of these factories creates an integer like `1u32` where the
        // integer value specified is the first part of the token and the
        // integral suffix is appended. Literals created from negative numbers
        // may not survive roundtrips through [TokenStream] or strings and may
        // be broken into two tokens (`-` and positive literal).
        //
        // Literals created through these methods have the [Span.callSite] span
        // by default, which can be configured with the [Literal.setSpan]
        // method below.
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

        /**
         * Creates a new suffixed floating-point literal.
         *
         * This factory creates a literal like `1.0f32` where the value
         * specified is the preceding part of the token and `f32` is the
         * suffix. Literals created from negative numbers may not survive
         * round-trips through [TokenStream] or strings.
         *
         * This function requires that the specified float is finite; if it is
         * infinite or NaN this function will throw.
         */
        fun f32Suffixed(n: Float): Literal = Literal(FallbackLiteral.f32Suffixed(n))

        /**
         * Creates a new suffixed floating-point literal with the `f64` suffix.
         *
         * Requires the specified double to be finite.
         */
        fun f64Suffixed(n: Double): Literal = Literal(FallbackLiteral.f64Suffixed(n))

        // Unsuffixed integer literals.
        //
        // Each of these factories creates an integer like `1` where the
        // integer value specified is the first part of the token. No suffix
        // is specified on this token, meaning that invocations like
        // [i8Unsuffixed] are equivalent to [u32Unsuffixed]. Literals created
        // from negative numbers may not survive roundtrips through
        // [TokenStream] or strings.
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

        /**
         * Creates a new unsuffixed floating-point literal.
         *
         * This factory is similar to [i8Unsuffixed] where the float's value is
         * emitted directly into the token but no suffix is used, so it may be
         * inferred to be an `f64` later in the compiler.
         *
         * Requires the specified float to be finite.
         */
        fun f32Unsuffixed(n: Float): Literal = Literal(FallbackLiteral.f32Unsuffixed(n))

        /**
         * Creates a new unsuffixed double literal.
         *
         * Requires the specified double to be finite.
         */
        fun f64Unsuffixed(n: Double): Literal = Literal(FallbackLiteral.f64Unsuffixed(n))

        /** String literal. */
        fun string(string: String): Literal = Literal(FallbackLiteral.string(string))

        /** Character literal. */
        fun character(ch: Char): Literal = Literal(FallbackLiteral.character(ch))

        /** Byte character literal. */
        fun byteCharacter(byte: UByte): Literal = Literal(FallbackLiteral.byteCharacter(byte))

        /** Byte string literal. */
        fun byteString(bytes: ByteArray): Literal = Literal(FallbackLiteral.byteString(bytes))

        /** C string literal. */
        fun cString(bytes: ByteArray): Literal = Literal(FallbackLiteral.cString(bytes))

        /**
         * Attempts to break the string into a single literal token.
         *
         * May fail for the same reasons [TokenStream.fromString] may fail.
         */
        fun fromString(repr: String): LiteralParseResult {
            val result = FallbackLiteral.fromStrChecked(repr)
            return if (result.isSuccess) {
                LiteralParseResult(Literal(result.getOrThrow()), null)
            } else {
                LiteralParseResult(null, result.exceptionOrNull()?.message ?: "cannot parse string into literal")
            }
        }

        /**
         * Intended for token-stream-builder callers that already validated the
         * input. Avoids reparsing/validating the literal's string
         * representation.
         */
        fun fromStrUnchecked(repr: String): Literal = Literal(FallbackLiteral.fromStrUnchecked(repr))
    }

    /** Returns the span encompassing this literal. */
    fun span(): Span = Span.newFallback(inner.span())

    /** Configures the span associated for this literal. */
    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    /**
     * Returns a [Span] that is a subset of [span] containing only the source
     * bytes in `range`. Returns `null` if the would-be trimmed span is outside
     * the bounds of this literal.
     */
    fun subspan(range: IntRange): Span? = inner.subspan(range)?.let(Span::newFallback)

    /** Returns the unescaped string value if this is a string literal. */
    fun strValue(): StringParseResult {
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
            return error?.let { StringParseResult(null, it.name) }
                ?: StringParseResult(value.toString(), null)
        }
        if (repr.startsWith('r')) {
            val raw = getRaw(repr.substring(1))
            if (raw != null) {
                return StringParseResult(raw, null)
            }
        }
        return StringParseResult(null, "invalid literal kind")
    }

    /**
     * Returns the unescaped string value (including nul terminator) if this is
     * a c-string literal.
     */
    fun cstrValue(): ByteArrayParseResult {
        val repr = toString()
        if (repr.startsWith("c\"") && repr.endsWith('"') && repr.length >= 3) {
            val quoted = repr.substring(2, repr.length - 1)
            val value = mutableListOf<Byte>()
            var error: EscapeError? = null
            unescapeCStr(quoted) { _, res ->
                when (res) {
                    is EscapeResult.Ok ->
                        when (res.value) {
                            is MixedUnit.Char -> {
                                val ch = res.value.value.get()
                                val utf8 = ch.toString().encodeToByteArray()
                                for (b in utf8) value.add(b)
                            }
                            is MixedUnit.HighByte ->
                                value.add(
                                    res.value.value
                                        .get()
                                        .toByte(),
                                )
                        }
                    is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                }
            }
            value.add(0)
            return error?.let { ByteArrayParseResult(null, it.name) }
                ?: ByteArrayParseResult(value.toByteArray(), null)
        }
        if (repr.startsWith("cr")) {
            val raw = getRaw(repr.substring(2))
            if (raw != null) {
                return ByteArrayParseResult(raw.encodeToByteArray() + byteArrayOf(0), null)
            }
        }
        return ByteArrayParseResult(null, "invalid literal kind")
    }

    /** Returns the unescaped string value if this is a byte string literal. */
    fun byteStrValue(): ByteArrayParseResult {
        val repr = toString()
        if (repr.startsWith("b\"") && repr.endsWith('"') && repr.length >= 3) {
            val quoted = repr.substring(2, repr.length - 1)
            val value = mutableListOf<Byte>()
            var error: EscapeError? = null
            unescapeByteStr(quoted) { _, res ->
                when (res) {
                    is EscapeResult.Ok -> value.add(res.value.toByte())
                    is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                }
            }
            return error?.let { ByteArrayParseResult(null, it.name) }
                ?: ByteArrayParseResult(value.toByteArray(), null)
        }
        if (repr.startsWith("br")) {
            val raw = getRaw(repr.substring(2))
            if (raw != null) {
                return ByteArrayParseResult(raw.encodeToByteArray(), null)
            }
        }
        return ByteArrayParseResult(null, "invalid literal kind")
    }

    override fun toString(): String = inner.toString()

    override fun equals(other: Any?): Boolean = other is Literal && inner == other.inner

    override fun hashCode(): Int = inner.hashCode()
}

/** Error when retrieving a string literal's unescaped value. */
internal sealed class ConversionErrorKind(
    message: String,
) : IllegalArgumentException(message) {
    /**
     * The literal is of the right string kind, but its contents are malformed
     * in a way that cannot be unescaped to a value.
     */
    data class FailedToUnescape(
        val error: EscapeError,
    ) : ConversionErrorKind(error.name)

    /**
     * The literal is not of the string kind whose value was requested, for
     * example byte string vs UTF-8 string.
     */
    data object InvalidLiteralKind : ConversionErrorKind("invalid literal kind")
}

// Parses the inner string of a `r#"..."#` raw-string repr (or its variants
// with more pound signs). Returns `null` if the input does not have the
// expected matching-pound shape.
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

/**
 * An iterator over a [TokenStream]'s [TokenTree]s.
 *
 * The iteration is "shallow", e.g. the iterator doesn't recurse into delimited
 * groups, and returns whole groups as token trees.
 */
class TokenStreamIntoIter internal constructor(
    private val inner: RcVecIntoIter<TokenTree>,
) : Iterator<TokenTree> {
    override fun hasNext(): Boolean = inner.hasNext()

    override fun next(): TokenTree = inner.next()

    internal fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()

    override fun toString(): String = "TokenStream ${inner.remaining().joinToString(prefix = "[", postfix = "]")}"
}
