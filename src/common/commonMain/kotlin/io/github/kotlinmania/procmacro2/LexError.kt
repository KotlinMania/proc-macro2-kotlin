package io.github.kotlinmania.procmacro2

/** Error returned from [TokenStream.fromString] and [Literal.fromString]. */
public class LexError internal constructor(
    internal val inner: WrapperLexError,
) {
    /** Returns the [Span] associated with this lexing failure. */
    public fun span(): Span = Span(inner.span())

    override fun toString(): String = inner.toString_()
}

internal class LexErrorException(
    val lexError: LexError,
) : IllegalArgumentException(lexError.toString())

internal fun Throwable?.toLexErrorOrNull(): LexError? = (this as? LexErrorException)?.lexError
