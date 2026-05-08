// port-lint: source extra.rs
package io.github.kotlinmania.procmacro2

/**
 * Invalidate any spans that exist on the current thread.
 *
 * The implementation of `Span` uses thread-local-style source map data in the
 * fallback model, and this function clears it.
 */
fun invalidateCurrentThreadSpanData() {
    invalidateCurrentThreadSpans()
}

/**
 * Holds a `Group`'s `spanOpen()` and `spanClose()` together in a more compact
 * representation than holding those two spans individually.
 */
class DelimSpan internal constructor(
    private val span: Span,
) {
    internal constructor(group: Group) : this(group.span())

    /** Returns a span covering the entire delimited group. */
    fun join(): Span = span

    /** Returns a span for the opening punctuation of the group only. */
    fun open(): Span = Span.newFallback(span.inner.firstByte())

    /** Returns a span for the closing punctuation of the group only. */
    fun close(): Span = Span.newFallback(span.inner.lastByte())

    override fun toString(): String = join().toString()
}
