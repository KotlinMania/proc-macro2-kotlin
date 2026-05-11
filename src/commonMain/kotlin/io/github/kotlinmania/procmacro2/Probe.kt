// port-lint: ignore
// Upstream `probe.rs` and its `probe/*.rs` submodules are Rust-only build-time
// compilation probes that detect whether the surrounding rustc supports the
// unstable `proc_macro::Span` API surface. The Kotlin port has a single fixed
// `Span` type, so the compile-detection mechanism has no analog; this file
// remains as a placeholder noting that intentional absence.
package io.github.kotlinmania.procmacro2
