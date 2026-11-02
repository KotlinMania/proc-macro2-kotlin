package io.github.kotlinmania.procmacro2

class TokenStreamParseResult internal constructor(
    val value: TokenStream?,
    val error: String?,
    val lexError: LexError? = null,
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

/**
 * An abstract stream of tokens, or more concretely a sequence of token trees.
 *
 * This type provides interfaces for iterating over token trees and for
 * collecting token trees into one stream.
 */
class TokenStream internal constructor(
    internal var inner: WrapperTokenStream,
) : Iterable<TokenTree> {
    companion object {
        /** Returns an empty [TokenStream] containing no token trees. */
        fun new(): TokenStream = TokenStream(WrapperTokenStream.new())

        internal fun newFallback(inner: FallbackTokenStream): TokenStream =
            TokenStream(WrapperTokenStream.fromFallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.TokenStream): TokenStream =
            TokenStream(WrapperTokenStream.fromProcMacro(inner))

        /** Wraps a compiler [io.github.kotlinmania.procmacro.TokenStream] as a [TokenStream]. */
        fun fromProcMacro(inner: io.github.kotlinmania.procmacro.TokenStream): TokenStream =
            TokenStream(WrapperTokenStream.fromProcMacro(inner))

        /**
         * Attempts to break the string into tokens and parse those tokens into a token
         * stream.
         *
         * May fail for a number of reasons, for example, if the string contains
         * unbalanced delimiters or characters not existing in the language.
         */
        fun fromString(src: String): TokenStreamParseResult {
            val result = WrapperTokenStream.fromStrChecked(src)
            return if (result.isSuccess) {
                TokenStreamParseResult(TokenStream(result.getOrThrow()), null, null)
            } else {
                val ex = result.exceptionOrNull()
                TokenStreamParseResult(
                    value = null,
                    error = ex?.message ?: "cannot parse string into token stream",
                    lexError = ex.toLexErrorOrNull(),
                )
            }
        }

        fun fromTokenTree(token: TokenTree): TokenStream =
            TokenStream(WrapperTokenStream.fromTokenTree(token))

        /** Collects a number of token trees into a single stream. */
        fun fromTokenTrees(tokens: Iterable<TokenTree>): TokenStream =
            TokenStream(WrapperTokenStream.fromTokenTrees(tokens))

        fun fromTokenStreams(streams: Iterable<TokenStream>): TokenStream =
            TokenStream(WrapperTokenStream.fromTokenStreams(streams.map { it.inner }))
    }

    /** Checks if this [TokenStream] is empty. */
    fun isEmpty(): Boolean = inner.isEmpty()

    /** Converts this [TokenStream] into a compiler [io.github.kotlinmania.procmacro.TokenStream]. */
    fun intoProcMacro(): io.github.kotlinmania.procmacro.TokenStream = fromTokenStream(inner)

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

    fun replaceFrom(other: TokenStream) {
        inner = other.inner
    }

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean =
        other is TokenStream &&
            inner.toTokenStreamEqualityItems() ==
            other.inner.toTokenStreamEqualityItems()

    override fun hashCode(): Int =
        inner.toTokenStreamEqualityItems().hashCode()
}

/**
 * An iterator over [TokenStream]'s [TokenTree]s.
 *
 * The iteration is "shallow", e.g. the iterator doesn't recurse into
 * delimited groups, and returns whole groups as token trees.
 */
class TokenStreamIntoIter internal constructor(
    inner: WrapperTokenStream,
) : Iterator<TokenTree> {
    private val iter = inner.intoIterW()

    override fun hasNext(): Boolean = iter.hasNext()

    override fun next(): TokenTree {
        if (!iter.hasNext()) {
            throw NoSuchElementException()
        }
        return iter.next().toPublicTree()
    }

    internal fun sizeHint(): Pair<Int, Int?> = iter.sizeHint()

    override fun toString(): String {
        val items = iter.remainingPublicTrees().map { it.toString() }
        return "TokenStream [${items.joinToString(", ")}]"
    }
}
