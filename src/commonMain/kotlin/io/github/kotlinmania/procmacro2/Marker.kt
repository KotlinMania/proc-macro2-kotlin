// port-lint: source marker.rs
package io.github.kotlinmania.procmacro2

// Zero-sized marker with the correct set of auto-trait impls we want all proc-macro types to have.
//
// Rust uses:
// - `PhantomData<Rc<()>>` to influence auto-traits
// - `UnwindSafe` / `RefUnwindSafe` to communicate panic-unwind safety
//
// Kotlin does not expose a comparable auto-trait system; this class exists as a structural marker only.
internal class ProcMacroAutoTraits

internal val MARKER: ProcMacroAutoTraits = ProcMacroAutoTraits()
