package io.github.kotlinmania.procmacro2

/**
 * A [Punct] is a single punctuation character like `+`, `-` or `#`.
 *
 * Multicharacter operators like `+=` are represented as two instances of
 * [Punct] with different forms of [Spacing] returned.
 */
class Punct(
    private val ch: Char,
    private var spacing: Spacing,
    private var span: Span = Span.callSite(),
) {
    init {
        require(ch in PUNCT_CHARS) { "unsupported proc macro punctuation character '$ch'" }
    }

    /** Returns the value of this punctuation character as [Char]. */
    fun asChar(): Char = ch

    /**
     * Returns the spacing of this punctuation character, indicating whether
     * it's immediately followed by another [Punct] in the token stream, so
     * they can potentially be combined into a multicharacter operator
     * ([Spacing.Joint]), or it's followed by some other token or whitespace
     * ([Spacing.Alone]) so the operator has certainly ended.
     */
    fun spacing(): Spacing = spacing

    /** Returns the span for this punctuation character. */
    fun span(): Span = span

    /** Configure the span for this punctuation character. */
    fun setSpan(span: Span) {
        this.span = span
    }

    override fun toString(): String = ch.toString()

    override fun equals(other: Any?): Boolean = other is Punct && ch == other.ch && spacing == other.spacing

    override fun hashCode(): Int = 31 * ch.hashCode() + spacing.hashCode()
}

private const val PUNCT_CHARS = "!#%&'*+,-./:;<=>?@^|~$"
