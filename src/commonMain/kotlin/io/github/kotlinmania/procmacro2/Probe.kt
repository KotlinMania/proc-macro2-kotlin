// port-lint: ignore
// The upstream probe module and its submodules are Rust-only build-time
// compilation probes that detect whether the surrounding rustc supports the
// unstable Span API surface from the in-tree procedural macro crate. The
// Kotlin port has a single fixed Span type, so the compile-detection
// mechanism has no analog; this file remains as a placeholder noting that
// intentional absence.
package io.github.kotlinmania.procmacro2
