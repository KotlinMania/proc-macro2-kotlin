// port-lint: source probe/proc_macro_span_location.rs
package io.github.kotlinmania.procmacro2.probe

import io.github.kotlinmania.procmacro.Span

object ProcMacroSpanLocation {
    fun start(thisSpan: Span): Span = thisSpan.start()

    fun end(thisSpan: Span): Span = thisSpan.end()

    fun line(thisSpan: Span): Int = thisSpan.line()

    fun column(thisSpan: Span): Int = thisSpan.column()
}
