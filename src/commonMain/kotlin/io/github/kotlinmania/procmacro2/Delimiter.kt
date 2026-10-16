package io.github.kotlinmania.procmacro2

/** Describes how a sequence of token trees is delimited. */
enum class Delimiter {
    /** `( ... )` */
    Parenthesis,

    /** `{ ... }` */
    Brace,

    /** `[ ... ]` */
    Bracket,

    /**
     * An invisible delimiter, that may, for example, appear around tokens
     * coming from a macro variable.
     */
    None,
}
