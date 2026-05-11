// port-lint: source extra.rs
package io.github.kotlinmania.procmacro2

/*
 * Items which do not have a correspondence to any API in the `proc_macro`
 * crate, but are necessary to include in proc-macro2.
 */

/**
 * Invalidate any [Span] that exists on the current thread.
 *
 * The implementation of [Span] uses thread-local data structures and this
 * function clears them. Calling any method on a [Span] on the current thread
 * created prior to the invalidation will return incorrect values or crash.
 *
 * This function is useful for programs that process more than 2^32 bytes of
 * Rust source code on the same thread. Just like rustc, proc-macro2 uses
 * 32-bit source locations, and these wrap around when the total source code
 * processed by the same thread exceeds 2^32 bytes (4 gigabytes). After a
 * wraparound, [Span] methods such as [Span.sourceText] can return wrong data.
 *
 * ## Example
 *
 * As of late 2023, there is 200 GB of Rust code published on crates.io.
 * Looking at just the newest version of every crate, it is 16 GB of code. So a
 * workload that involves parsing it all would overflow a 32-bit source
 * location unless spans are being invalidated.
 *
 * ```
 * coroutineScope {
 *     for (krate in everyVersionOfEveryCrate()) {
 *         launch(Dispatchers.IO) {
 *             invalidateCurrentThreadSpanData()
 *
 *             for (entry in krate.entries()) {
 *                 val path = entry.path()
 *                 if (path.extension != "rs") continue
 *                 val content = entry.readText()
 *                 TokenStream.fromString(content).onSuccess { /* ... */ }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * The Kotlin port exports this as [invalidateCurrentThreadSpanData] rather
 * than `invalidateCurrentThreadSpans` because the unprefixed name is already
 * taken at the same package level by the fallback-internal helper that this
 * function delegates to.
 *
 * ## Throws
 *
 * This function is not applicable to and will throw [IllegalStateException]
 * if called from a procedural macro context.
 */
fun invalidateCurrentThreadSpanData() {
    invalidateCurrentThreadSpans()
}

/**
 * An object that holds a [Group]'s [Group.spanOpen] and [Group.spanClose]
 * together in a more compact representation than holding those 2 spans
 * individually.
 *
 * The upstream Rust type carries a `DelimSpanEnum` with `Compiler` and
 * `Fallback` variants. The Kotlin port has no embedding compiler to defer to,
 * so only the fallback shape is retained; [open] and [close] are computed from
 * the group's joined span via [FallbackSpan.firstByte] and
 * [FallbackSpan.lastByte].
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
