// port-lint: source tests/features.rs
package io.github.kotlinmania.procmacro2

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse

class FeaturesTest {
    @Test
    @Ignore
    fun makeSureNoProcMacro() {
        assertFalse(
            FEATURE_PROC_MACRO_ENABLED,
            "still compiled with proc_macro?",
        )
    }

    companion object {
        private const val FEATURE_PROC_MACRO_ENABLED: Boolean = false
    }
}
