// port-lint: source probe/proc_macro_span.rs
package io.github.kotlinmania.procmacro2.probe.procmacrospan

import io.github.kotlinmania.procmacro2.Literal
import io.github.kotlinmania.procmacro2.Span

/**
 * Exercises the surface area expected of `Span`'s unstable API. If the current
 * toolchain can compile this file, proc-macro2 can offer these APIs too.
 */
fun byteRange(thisSpan: Span): IntRange {
    return thisSpan.byteRange()
}

fun start(thisSpan: Span): Span {
    return thisSpan
}

fun end(thisSpan: Span): Span {
    return thisSpan
}

fun line(thisSpan: Span): Int {
    return thisSpan.start().line
}

fun column(thisSpan: Span): Int {
    return thisSpan.start().column + 1
}

fun file(thisSpan: Span): String {
    return thisSpan.file()
}

fun localFile(thisSpan: Span): String? {
    return thisSpan.localFile()
}

fun join(thisSpan: Span, other: Span): Span? {
    return thisSpan.join(other)
}

fun subspan(thisLiteral: Literal, range: IntRange): Span? {
    return thisLiteral.subspan(range)
}
