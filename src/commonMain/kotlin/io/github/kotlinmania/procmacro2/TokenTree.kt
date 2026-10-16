package io.github.kotlinmania.procmacro2

/** A single token or a delimited sequence of token trees. */
sealed class TokenTree {
    /**
     * Returns the span of this tree, delegating to the [span] method of
     * the contained token or a delimited stream.
     */
    abstract fun span(): Span

    /**
     * Configures the span for only this token.
     *
     * Note that if this token is a [Group] then this method will not configure
     * the span of each of the internal tokens, this will simply delegate to
     * the [setSpan] method of each variant.
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

    /** A single punctuation character. */
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

    /** A literal character, string, number, etc. */
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
