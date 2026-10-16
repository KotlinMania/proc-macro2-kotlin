package io.github.kotlinmania.procmacro2

/** A region of source code, along with macro expansion information. */
class Span internal constructor(
    internal var inner: WrapperSpan,
) {
    companion object {
        internal fun newFallback(inner: FallbackSpan): Span = Span(WrapperSpan.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Span): Span = Span(WrapperSpan.Compiler(inner))

        /**
         * The span of the invocation of the current procedural macro.
         *
         * Identifiers created with this span will be resolved as if they were
         * written directly at the macro call location (call-site hygiene) and
         * other code at the macro call site will be able to refer to them as well.
         */
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

        /**
         * The span located at the invocation of the procedural macro, but with
         * local variables, labels, and crate references resolved at the definition site
         * of the macro.
         */
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

        /** A span that resolves at the macro definition site. */
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

    /**
     * Creates a new span with the same line/column information as this span but
     * that resolves symbols as though it were at [other].
     */
    fun resolvedAt(other: Span): Span = Span(inner.resolvedAt(other.inner))

    /**
     * Creates a new span with the same name resolution behavior as this span but
     * with the line/column information of [other].
     */
    fun locatedAt(other: Span): Span = Span(inner.locatedAt(other.inner))

    /** Returns the span's byte position range in the source file. */
    fun byteRange(): IntRange = inner.byteRange()

    /** Get the starting line/column in the source file for this span. */
    fun start(): LineColumn = inner.start()

    /** Get the ending line/column in the source file for this span. */
    fun end(): LineColumn = inner.end()

    /**
     * The path to the source file in which this span occurs, for display
     * purposes.
     */
    fun file(): String = inner.file()

    /**
     * The path to the source file in which this span occurs on disk.
     */
    fun localFile(): String? = inner.localFile()

    /**
     * Create a new span encompassing this span and [other].
     *
     * Returns null if this span and [other] are from different files.
     */
    fun join(other: Span): Span? = inner.join(other.inner)?.let(::Span)

    /**
     * Returns the source text behind a span. This preserves the original
     * source code, including spaces and comments.
     */
    fun sourceText(): String? = inner.sourceText()

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean = other is Span && inner.eq(other.inner)

    override fun hashCode(): Int = inner.hashCode_()

    fun replaceFrom(other: Span) {
        inner = other.inner
    }
}
