// port-lint: source probe.rs
package io.github.kotlinmania.procmacro2

// Upstream probe.rs conditionally declares three submodules
// (ProcMacroSpan, ProcMacroSpanFile, ProcMacroSpanLocation)
// that exercise unstable compiler `Span` API surface
// (byteRange, start, end, line, column, file, localFile, join, subspan).
// These only exist inside the Rust compiler's procedural macro context.
//
// The Kotlin port has no embedding compiler, so none of these probes
// have a target to exercise. The submodules are structurally inapplicable.