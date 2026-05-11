// port-lint: ignore
// Upstream wrapper exists solely to dispatch between two backing
// implementations of every public type: a Compiler variant that delegates to
// the in-tree procedural macro crate available inside a procedural macro, and
// a Fallback variant that uses the standalone fallback implementation. Kotlin
// Multiplatform has no embedding compiler to defer to, so the Compiler half
// of every dispatch is structurally impossible; the wrapper collapses to the
// identity function over the Fallback side, and Lib.kt therefore stores
// FallbackTokenStream / FallbackSpan / FallbackGroup / FallbackIdent /
// FallbackLiteral / FallbackLexError directly. This file remains as a
// placeholder noting that intentional absence; the surface that the upstream
// wrapper would expose is reachable through the corresponding Fallback types.
package io.github.kotlinmania.procmacro2
