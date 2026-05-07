// port-lint: source num.rs
package io.github.kotlinmania.procmacro2

// TODO: use a stdlib non-zero Char once Kotlin has one (Rust 1.89+ has a dedicated type).
data class NonZeroChar private constructor(private val value: Char) {
    companion object {
        fun new(ch: Char): NonZeroChar? {
            return if (ch == '\u0000') {
                null
            } else {
                NonZeroChar(ch)
            }
        }
    }

    fun get(): Char = value
}
