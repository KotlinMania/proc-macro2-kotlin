// port-lint: source num.rs
package io.github.kotlinmania.procmacro2

internal class NonZeroChar private constructor(private val value: Char) {
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

    override fun equals(other: Any?): Boolean {
        return other is NonZeroChar && value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "NonZeroChar($value)"
}
