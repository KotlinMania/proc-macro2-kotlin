package io.github.kotlinmania.procmacro2

/**
 * A delimited token stream.
 *
 * A [Group] internally contains a [TokenStream] which is surrounded by
 * [Delimiter]s.
 */
class Group internal constructor(
    internal var inner: WrapperGroup,
) {
    companion object {
        internal fun newFallback(inner: FallbackGroup): Group = Group(WrapperGroup.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Group): Group = Group(WrapperGroup.Compiler(inner))
    }

    /**
     * Creates a new [Group] with the given delimiter and token stream.
     *
     * This constructor will set the span for this group to
     * [Span.callSite]. To change the span you can use the [setSpan]
     * method below.
     */
    constructor(delimiter: Delimiter, stream: TokenStream) : this(
        WrapperGroup.Fallback(FallbackGroup(delimiter, stream.inner.asFallbackStream())),
    )

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
    fun span(): Span = Span(inner.span())

    /** Returns the span pointing to the opening delimiter of this group. */
    fun spanOpen(): Span = Span(inner.spanOpen())

    /** Returns the span pointing to the closing delimiter of this group. */
    fun spanClose(): Span = Span(inner.spanClose())

    /**
     * Returns an object that holds this group's [spanOpen] and
     * [spanClose] together (in a more compact representation than holding
     * those 2 spans individually).
     */
    fun delimSpan(): DelimSpan = DelimSpan(this)

    /**
     * Configures the span for this [Group]'s delimiters, but not its internal
     * tokens.
     */
    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean = other is Group && inner.eq(other.inner)

    override fun hashCode(): Int = inner.hashCode_()

    fun replaceFrom(other: Group) {
        inner = other.inner
    }
}
