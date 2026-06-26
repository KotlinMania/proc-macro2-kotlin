// port-lint: source wrapper.rs
// Upstream wrapper.rs dispatches between two backing implementations of
// every public type: a Compiler variant that delegates to the in-tree
// procedural macro crate, and a Fallback variant. Kotlin Multiplatform has
// no embedding compiler to defer to, so the Compiler half is structurally
// impossible. Lib.kt stores Fallback types directly:
//   FallbackTokenStream, FallbackSpan, FallbackGroup, FallbackIdent,
//   FallbackLiteral, FallbackLexError.
// Wrapper dispatch is the identity function over the Fallback side; the
// fallback module exposes the full public surface without a dispatch layer.
package io.github.kotlinmania.procmacro2

internal object WrapperModuleDescriptor
