// port-lint: source probe/proc_macro_span_file.rs
package io.github.kotlinmania.procmacro2.probe

import io.github.kotlinmania.procmacro.Span

object ProcMacroSpanFile {
    fun file(thisSpan: Span): String = thisSpan.file()

    fun localFile(thisSpan: Span): String? = thisSpan.localFile()
}
