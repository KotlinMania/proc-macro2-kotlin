// port-lint: source marker.rs
package io.github.kotlinmania.procmacro2

// Zero sized marker with the correct set of autotrait impls we want all proc
// macro types to have.
internal class ProcMacroAutoTraits

internal val MARKER: ProcMacroAutoTraits = ProcMacroAutoTraits()
