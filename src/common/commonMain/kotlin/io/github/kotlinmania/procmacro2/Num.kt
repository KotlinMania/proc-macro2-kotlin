// port-lint: source num.rs
package io.github.kotlinmania.procmacro2

internal class NonZeroChar private constructor(
    private val value: Int,
) {
    companion object {
        fun new(codePoint: Int): NonZeroChar? =
            if (codePoint <= 0 || codePoint > 0x10FFFF || codePoint in 0xD800..0xDFFF) {
                null
            } else {
                NonZeroChar(codePoint)
            }

        fun new(ch: Char): NonZeroChar? =
            if (ch == '\u0000' || ch.isSurrogate()) {
                null
            } else {
                NonZeroChar(ch.code)
            }
    }

    fun get(): Int = value

    override fun equals(other: Any?): Boolean = other is NonZeroChar && value == other.value

    override fun hashCode(): Int = value

    override fun toString(): String = "NonZeroChar($value)"
}
