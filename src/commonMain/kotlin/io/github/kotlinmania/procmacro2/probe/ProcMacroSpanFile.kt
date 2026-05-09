// port-lint: source probe/proc_macro_span_file.rs
package io.github.kotlinmania.procmacro2.probe.procmacrospanfile

import io.github.kotlinmania.procmacro2.Span

/** The subset of `Span`'s API stabilized in Rust 1.88. */
fun file(thisSpan: Span): String {
    return thisSpan.file()
}

fun localFile(thisSpan: Span): String? {
    return thisSpan.localFile()
}
