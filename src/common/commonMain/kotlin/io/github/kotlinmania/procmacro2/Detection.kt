// port-lint: source detection.rs
package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.procmacro.bridge.BridgeClientState
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
private val WORKS = AtomicLong(0)

@OptIn(ExperimentalAtomicApi::class)
private val INIT = AtomicLong(0)

@OptIn(ExperimentalAtomicApi::class)
internal fun insideProcMacro(): Boolean =
    when (WORKS.load()) {
        1L -> false
        2L -> BridgeClientState.isAvailable()
        else -> {
            callOnce(::initialize)
            insideProcMacro()
        }
    }

@OptIn(ExperimentalAtomicApi::class)
internal fun forceFallback() {
    WORKS.store(1L)
}

internal fun unforceFallback() {
    initialize()
}

@OptIn(ExperimentalAtomicApi::class)
private fun initialize() {
    WORKS.store(2L)
}

@OptIn(ExperimentalAtomicApi::class)
private fun callOnce(init: () -> Unit) {
    if (INIT.load() == 2L) {
        return
    }

    if (INIT.compareAndSet(expectedValue = 0L, newValue = 1L)) {
        init()
        INIT.store(2L)
        return
    }

    while (INIT.load() != 2L) {
        // Spin until the initializing thread publishes completion.
    }
}
