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
        fun new(): TokenStream =
            TokenStream(WrapperTokenStream.Fallback(FallbackTokenStream.new()))

        internal fun newFallback(inner: FallbackTokenStream): TokenStream =
            TokenStream(WrapperTokenStream.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.TokenStream): TokenStream =
            TokenStream(WrapperTokenStream.Compiler(inner))

        /**
         * Attempts to break the string into tokens and parse those tokens into a token
         * stream.
         *
         * May fail for a number of reasons, for example, if the string contains
         * unbalanced delimiters or characters not existing in the language.
         */
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

        /** Collects a number of token trees into a single stream. */
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
    private val inner: WrapperTokenStream,
) : Iterator<TokenTree> {
    private val iter = inner.intoIterW()

    override fun hasNext(): Boolean = iter.hasNext()

    override fun next(): TokenTree {
        val wrapperTree = iter.next()
        return wrapperTree.asFallbackTree()
    }

    internal fun sizeHint(): Pair<Int, Int?> = iter.sizeHint()

    override fun toString(): String {
        val items = mutableListOf<String>()
        val snapshot = iter
        val start =
            if (snapshot is WrapperTokenStreamIntoIter.Fallback) {
                snapshot.iter.remaining()
            } else {
                null
            }
        if (start != null) {
            for (tt in start) items.add(tt.toString())
        } else {
            while (snapshot.hasNext()) {
                items.add(snapshot.next().asFallbackTree().toString())
            }
        }
        return "TokenStream [${items.joinToString(", ")}]"
    }
}
