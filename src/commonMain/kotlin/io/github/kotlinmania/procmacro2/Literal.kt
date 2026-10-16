package io.github.kotlinmania.procmacro2

class LiteralParseResult internal constructor(
    val value: Literal?,
    val error: String?,
) {
    init {
        require((value == null) != (error == null)) {
            "LiteralParseResult must carry exactly one of value or error"
        }
    }

    fun isSuccess(): Boolean = value != null

    fun isFailure(): Boolean = error != null

    fun getOrThrow(): Literal =
        value ?: throw IllegalStateException(error)
}

class StringParseResult internal constructor(
    val value: String?,
    val error: String?,
) {
    init {
        require((value == null) != (error == null)) {
            "StringParseResult must carry exactly one of value or error"
        }
    }

    fun isSuccess(): Boolean = value != null

    fun isFailure(): Boolean = error != null

    fun getOrThrow(): String =
        value ?: throw IllegalStateException(error)
}

class ByteArrayParseResult internal constructor(
    val value: ByteArray?,
    val error: String?,
) {
    init {
        require((value == null) != (error == null)) {
            "ByteArrayParseResult must carry exactly one of value or error"
        }
    }

    fun isSuccess(): Boolean = value != null

    fun isFailure(): Boolean = error != null

    fun getOrThrow(): ByteArray =
        value ?: throw IllegalStateException(error)
}

/**
 * A literal string (`"hello"`), byte string (`b"hello"`), character (`'a'`),
 * byte character (`b'a'`), an integer or floating point number with or without
 * a suffix (`1`, `1u8`, `2.3`, `2.3f32`).
 *
 * Boolean literals like `true` and `false` do not belong here, they are
 * [Ident]s.
 */
class Literal internal constructor(
    internal var inner: WrapperLiteral,
) {
    companion object {
        internal fun newFallback(inner: FallbackLiteral): Literal = Literal(WrapperLiteral.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Literal): Literal = Literal(WrapperLiteral.Compiler(inner))

        fun u8Suffixed(n: UByte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u8Suffixed(n)))

        fun u16Suffixed(n: UShort): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u16Suffixed(n)))

        fun u32Suffixed(n: UInt): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u32Suffixed(n)))

        fun u64Suffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u64Suffixed(n)))

        fun u128Suffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u128Suffixed(n)))

        fun usizeSuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.usizeSuffixed(n)))

        fun i8Suffixed(n: Byte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i8Suffixed(n)))

        fun i16Suffixed(n: Short): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i16Suffixed(n)))

        fun i32Suffixed(n: Int): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i32Suffixed(n)))

        fun i64Suffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i64Suffixed(n)))

        fun i128Suffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i128Suffixed(n)))

        fun isizeSuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.isizeSuffixed(n)))

        fun f32Suffixed(n: Float): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f32Suffixed(n)))

        fun f64Suffixed(n: Double): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f64Suffixed(n)))

        fun u8Unsuffixed(n: UByte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u8Unsuffixed(n)))

        fun u16Unsuffixed(n: UShort): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u16Unsuffixed(n)))

        fun u32Unsuffixed(n: UInt): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u32Unsuffixed(n)))

        fun u64Unsuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u64Unsuffixed(n)))

        fun u128Unsuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.u128Unsuffixed(n)))

        fun usizeUnsuffixed(n: ULong): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.usizeUnsuffixed(n)))

        fun i8Unsuffixed(n: Byte): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i8Unsuffixed(n)))

        fun i16Unsuffixed(n: Short): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i16Unsuffixed(n)))

        fun i32Unsuffixed(n: Int): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i32Unsuffixed(n)))

        fun i64Unsuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i64Unsuffixed(n)))

        fun i128Unsuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.i128Unsuffixed(n)))

        fun isizeUnsuffixed(n: Long): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.isizeUnsuffixed(n)))

        fun f32Unsuffixed(n: Float): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f32Unsuffixed(n)))

        fun f64Unsuffixed(n: Double): Literal = Literal(WrapperLiteral.Fallback(FallbackLiteral.f64Unsuffixed(n)))

        /** String literal. */
        fun string(string: String): Literal =
            if (useCompiler()) {
                Literal(
                    WrapperLiteral.Compiler(
                        io.github.kotlinmania.procmacro.Literal
                            .string(string),
                    ),
                )
            } else {
                Literal(WrapperLiteral.Fallback(FallbackLiteral.string(string)))
            }

        /** Character literal. */
        fun character(ch: Char): Literal =
            if (useCompiler()) {
                Literal(
                    WrapperLiteral.Compiler(
                        io.github.kotlinmania.procmacro.Literal
                            .character(ch),
                    ),
                )
            } else {
                Literal(WrapperLiteral.Fallback(FallbackLiteral.character(ch)))
            }

        /** Byte character literal. */
        fun byteCharacter(byte: UByte): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.byteCharacter(byte)))

        /** Byte string literal. */
        fun byteString(bytes: ByteArray): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.byteString(bytes)))

        /** C string literal. */
        fun cString(bytes: ByteArray): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.cString(bytes)))

        fun fromString(repr: String): LiteralParseResult {
            val result = FallbackLiteral.fromStrChecked(repr)
            return if (result.isSuccess) {
                LiteralParseResult(Literal(WrapperLiteral.Fallback(result.getOrThrow())), null)
            } else {
                LiteralParseResult(null, result.exceptionOrNull()?.message ?: "cannot parse string into literal")
            }
        }

        fun fromStrUnchecked(repr: String): Literal =
            Literal(WrapperLiteral.Fallback(FallbackLiteral.fromStrUnchecked(repr)))
    }

    /** Returns the span encompassing this literal. */
    fun span(): Span = Span(inner.span())

    /** Configures the span associated for this literal. */
    fun setSpan(span: Span) {
        inner.setSpan(span.inner)
    }

    /**
     * Returns a [Span] that is a subset of [span] containing only
     * the source bytes in range [range]. Returns null if the would-be
     * trimmed span is outside the bounds of this literal.
     */
    fun subspan(range: IntRange): Span? = inner.subspan(range)?.let(::Span)

    /** Returns the unescaped string value if this is a string literal. */
    fun strValue(): StringParseResult {
        val value = inner.strValue_()
        return if (value != null) {
            StringParseResult(value, null)
        } else {
            StringParseResult(null, "invalid literal kind")
        }
    }

    /**
     * Returns the unescaped string value (including nul terminator) if this is
     * a C string literal.
     */
    fun cstrValue(): ByteArrayParseResult {
        val value = inner.cstrValue_()
        return if (value != null) {
            ByteArrayParseResult(value, null)
        } else {
            ByteArrayParseResult(null, "invalid literal kind")
        }
    }

    /** Returns the unescaped string value if this is a byte string literal. */
    fun byteStrValue(): ByteArrayParseResult {
        val value = inner.byteStrValue_()
        return if (value != null) {
            ByteArrayParseResult(value, null)
        } else {
            ByteArrayParseResult(null, "invalid literal kind")
        }
    }

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean = other is Literal && inner.eq(other.inner)

    override fun hashCode(): Int = inner.toString_().hashCode()

    fun replaceFrom(other: Literal) {
        inner = other.inner
    }
}

/** Error when retrieving a string literal's unescaped value. */
internal sealed class ConversionErrorKind(
    message: String,
) : IllegalArgumentException(message) {
    /**
     * The literal is of the right string kind, but its contents are malformed
     * in a way that cannot be unescaped to a value.
     */
    data class FailedToUnescape(
        val error: EscapeError,
    ) : ConversionErrorKind(error.name)

    /**
     * The literal is not of the string kind whose value was requested, for
     * example byte string vs UTF-8 string.
     */
    data object InvalidLiteralKind : ConversionErrorKind("invalid literal kind")
}

internal fun getRaw(repr: String): String? {
    val pounds = repr.takeWhile { it == '#' }.length
    return if (
        repr.length >= pounds + 2 + pounds &&
        repr.getOrNull(pounds) == '"' &&
        repr.dropLast(pounds).endsWith('"') &&
        repr.takeLast(pounds).all { it == '#' }
    ) {
        repr.substring(pounds + 1, repr.length - pounds - 1)
    } else {
        null
    }
}
