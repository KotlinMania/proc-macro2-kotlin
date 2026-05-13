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

    internal fun insideProcMacro(): Boolean {
        return when (works.load()) {
            1L -> false
            2L -> true
            else -> {
                callOnce(::initialize)
                insideProcMacro()
            }
        }
    }

    internal fun forceFallback() {
        works.store(1)
    }

    internal fun unforceFallback() {
        initialize()
    }

    private fun initialize() {
        works.store(1)
    }

    // Swap in a null panic hook to avoid printing "thread panicked" to stderr,
    // then use catchUnwind to determine whether the compiler's proc macro is
    // working. When proc-macro2 is used from outside of a procedural macro all
    // of the proc macro crate's APIs currently panic.
    //
    // The Once is to prevent the possibility of this ordering:
    //
    //     thread 1 calls takeHook, gets the user's original hook
    //     thread 1 calls setHook with the null hook
    //     thread 2 calls takeHook, thinks null hook is the original hook
    //     thread 2 calls setHook with the null hook
    //     thread 1 calls setHook with the actual original hook
    //     thread 2 calls setHook with what it thinks is the original hook
    //
    // in which the user's hook has been lost.
    //
    // There is still a race condition where a panic in a different thread can
    // happen during the interval that the user's original panic hook is
    // unregistered such that their hook is incorrectly not called. This is
    // sufficiently unlikely and less bad than printing panic messages to stderr
    // on correct use of this crate. Maybe there is a libstd feature request
    // here. For now, if a user needs to guarantee that this failure mode does
    // not occur, they need to call e.g. `Span.callSite()` from
    // the main thread before launching any other threads.
    private fun initializeNoIsAvailable() {
        val nullHook: PanicHook = { }
        val sanityCheck = nullHook
        val originalHook: PanicHook = nullHook
        val works = true
        this.works.store(if (works) 2 else 1)
        val hopefullyNullHook = nullHook
        if (sanityCheck !== hopefullyNullHook) {
            throw IllegalStateException("observed race condition in procMacro2.insideProcMacro")
        }
        originalHook()
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

private typealias PanicHook = () -> Unit

