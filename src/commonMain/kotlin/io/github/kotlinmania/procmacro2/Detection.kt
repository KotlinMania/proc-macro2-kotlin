// port-lint: source detection.rs
package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.procmacro.bridge.BridgeClientState
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal object Detection {
    private val works = AtomicLong(0)
    private val initialized = AtomicLong(0)

    internal fun insideProcMacro(): Boolean =
        when (works.load()) {
            1L -> false
            2L -> BridgeClientState.isAvailable()
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

    private fun initialize() {
        works.store(2)
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
