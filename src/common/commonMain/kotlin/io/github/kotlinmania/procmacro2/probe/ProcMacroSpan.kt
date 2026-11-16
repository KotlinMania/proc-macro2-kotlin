// port-lint: source probe/proc_macro_span.rs
package io.github.kotlinmania.procmacro2.probe

import io.github.kotlinmania.procmacro.Literal
import io.github.kotlinmania.procmacro.Span

object ProcMacroSpan {
    fun byteRange(thisSpan: Span): IntRange = thisSpan.byteRange()

    fun start(thisSpan: Span): Span = thisSpan.start()

    fun end(thisSpan: Span): Span = thisSpan.end()

    fun line(thisSpan: Span): Int = thisSpan.line()

    fun column(thisSpan: Span): Int = thisSpan.column()

    fun file(thisSpan: Span): String = thisSpan.file()

    fun localFile(thisSpan: Span): String? = thisSpan.localFile()

    fun join(thisSpan: Span, other: Span): Span? = thisSpan.join(other)

    fun subspan(thisLiteral: Literal, range: IntRange): Span? = thisLiteral.subspan(range)
}
