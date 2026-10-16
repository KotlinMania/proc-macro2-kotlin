package io.github.kotlinmania.procmacro2

/**
 * A word of source code, which may be a keyword or legal variable name.
 *
 * An identifier consists of at least one Unicode code point, the first of
 * which has the XID start property and the rest of which have the XID continue
 * property.
 */
class Ident internal constructor(
    internal var inner: WrapperIdent,
) : Comparable<Ident> {
    companion object {
        internal fun newFallback(inner: FallbackIdent): Ident = Ident(WrapperIdent.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Ident): Ident = Ident(WrapperIdent.Compiler(inner))

        /**
         * Creates a new [Ident] with the given [string] as well as the specified
         * [span].
         *
         * The [string] argument must be a valid identifier permitted by the
         * language, otherwise the function will throw [IllegalArgumentException].
         */
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

        /**
         * Same as [Ident.new], but creates a raw identifier (`r#ident`). The
         * [string] argument must be a valid identifier permitted by the language
         * (including keywords). Keywords which are usable in path segments
         * (such as `self`, `super`) are not supported, and will throw
         * [IllegalArgumentException].
         */
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

    /** Returns the span of this [Ident]. */
    fun span(): Span = Span(inner.span())

    /**
     * Configures the span of this [Ident], possibly changing its hygiene
     * context.
     */
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

    fun replaceFrom(other: Ident) {
        inner = other.inner
    }
}
