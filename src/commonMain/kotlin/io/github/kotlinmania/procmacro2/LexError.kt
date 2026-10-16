package io.github.kotlinmania.procmacro2

/** Error returned from [TokenStream.fromString]. */
internal class LexError internal constructor(
    internal val inner: WrapperLexError,
) : IllegalArgumentException("cannot parse string into token stream") {
    fun span(): Span = Span(inner.span())

    override fun toString(): String = inner.toString_()
}
