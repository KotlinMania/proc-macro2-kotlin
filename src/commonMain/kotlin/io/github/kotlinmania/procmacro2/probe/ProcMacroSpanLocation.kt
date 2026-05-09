// port-lint: source probe/proc_macro_span_location.rs
package io.github.kotlinmania.procmacro2.probe.procmacrospanlocation

import io.github.kotlinmania.procmacro2.Span

/** The subset of `Span`'s API stabilized in Rust 1.88. */
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
