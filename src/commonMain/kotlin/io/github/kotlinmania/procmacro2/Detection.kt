// port-lint: source detection.rs
package io.github.kotlinmania.procmacro2

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Tracks whether proc-macro2 should defer to the embedding compiler's procedural
 * macro API or to its standalone fallback implementation. The Kotlin port has no
 * embedding compiler to defer to, so the result of [insideProcMacro] is always
 * false; the machinery is retained so [forceFallback] and [unforceFallback]
 * remain available to callers that want to assert a particular mode.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object Detection {
    private val works = AtomicLong(0)
    private val initialized = AtomicLong(0)

    internal fun insideProcMacro(): Boolean =
        when (works.load()) {
            1L -> false
            2L -> true
            else -> {
                callOnce(::initialize)
                insideProcMacro()
            }
        }

    internal fun forceFallback() {
        works.store(1)
    }

    internal fun unforceFallback() {
        initialize()
    }

    // The cfg(no_is_available) branch in detection.rs:56-75 swaps a null panic
    // hook to probe proc_macro::Span::call_site; the Kotlin port has no
    // embedding compiler to probe, so this branch is structurally inapplicable.
    private fun initialize() {
        works.store(1)
    }

    private fun callOnce(init: () -> Unit) {
        if (initialized.load() == 2L) {
            return
        }

        if (initialized.compareAndSet(expectedValue = 0L, newValue = 1L)) {
            init()
            initialized.store(2L)
            return
        }

        while (initialized.load() != 2L) {
        }
    }
}
