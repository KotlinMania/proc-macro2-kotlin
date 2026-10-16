package io.github.kotlinmania.procmacro2

/**
 * Whether a [Punct] is followed immediately by another [Punct] or followed by
 * another token or whitespace.
 */
enum class Spacing {
    /**
     * A [Punct] is not immediately followed by another [Punct].
     * For example `+` is [Alone] in `+ =`, `+ident` or `+()`.
     */
    Alone,

    /**
     * A [Punct] is immediately followed by another [Punct] or can join with
     * an identifier to form a lifetime.
     * For example `+` is [Joint] in `+=` or `'` is [Joint] in `'#`.
     */
    Joint,
}
