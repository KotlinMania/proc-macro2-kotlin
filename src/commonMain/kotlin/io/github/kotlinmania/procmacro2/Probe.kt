// port-lint: source probe.rs
package io.github.kotlinmania.procmacro2

// Upstream probe.rs conditionally declares three submodules gated by
// cfg(proc_macro_span), cfg(proc_macro_span_file), cfg(proc_macro_span_location).
// These exercise unstable compiler Span API surface. The Kotlin port has no
// cfg mechanism, and proc-macro-kotlin provides the Span/Literal surface
// directly, so all three submodules are always available.
//
// Submodules:
//   probe.ProcMacroSpan        — byteRange, start, end, line, column, file, localFile, join, subspan
//   probe.ProcMacroSpanFile    — file, localFile
//   probe.ProcMacroSpanLocation — start, end, line, column
internal object ProbeModuleDescriptor
