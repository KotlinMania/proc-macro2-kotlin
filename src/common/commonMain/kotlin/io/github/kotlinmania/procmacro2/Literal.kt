package io.github.kotlinmania.procmacro2

class LiteralParseResult internal constructor(
    val value: Literal?,
    val error: String?,
    val lexError: LexError? = null,
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
    val errorKind: ConversionErrorKind? = null,
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
    val errorKind: ConversionErrorKind? = null,
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
 * A literal string (`"hello"`), byte string (`b"hello"`), C string (`c"hello"`),
 * character (`'a'`), byte character (`b'a'`), or an integer or floating point
 * number with or without a suffix (`1`, `1u8`, `2.3`, `2.3f32`).
 *
 * Boolean literals like `true` and `false` do not belong here, they are
 * [Ident]s.
 */
class Literal internal constructor(
    internal var inner: WrapperLiteral,
) {
    companion object {
        internal fun newFallback(inner: FallbackLiteral): Literal = Literal(WrapperLiteral.Fallback(inner))

        internal fun newCompiler(inner: io.github.kotlinmania.procmacro.Literal): Literal =
            Literal(WrapperLiteral.Compiler(inner))

        fun u8Suffixed(n: UByte): Literal = Literal(WrapperLiteral.u8Suffixed(n))

        fun u16Suffixed(n: UShort): Literal = Literal(WrapperLiteral.u16Suffixed(n))

        fun u32Suffixed(n: UInt): Literal = Literal(WrapperLiteral.u32Suffixed(n))

        fun u64Suffixed(n: ULong): Literal = Literal(WrapperLiteral.u64Suffixed(n))

        fun u128Suffixed(n: ULong): Literal = Literal(WrapperLiteral.u128Suffixed(n))

        fun usizeSuffixed(n: ULong): Literal = Literal(WrapperLiteral.usizeSuffixed(n))

        fun i8Suffixed(n: Byte): Literal = Literal(WrapperLiteral.i8Suffixed(n))

        fun i16Suffixed(n: Short): Literal = Literal(WrapperLiteral.i16Suffixed(n))

        fun i32Suffixed(n: Int): Literal = Literal(WrapperLiteral.i32Suffixed(n))

        fun i64Suffixed(n: Long): Literal = Literal(WrapperLiteral.i64Suffixed(n))

        fun i128Suffixed(n: Long): Literal = Literal(WrapperLiteral.i128Suffixed(n))

        fun isizeSuffixed(n: Long): Literal = Literal(WrapperLiteral.isizeSuffixed(n))

        fun f32Suffixed(n: Float): Literal = Literal(WrapperLiteral.f32Suffixed(n))

        fun f64Suffixed(n: Double): Literal = Literal(WrapperLiteral.f64Suffixed(n))

        fun u8Unsuffixed(n: UByte): Literal = Literal(WrapperLiteral.u8Unsuffixed(n))

        fun u16Unsuffixed(n: UShort): Literal = Literal(WrapperLiteral.u16Unsuffixed(n))

        fun u32Unsuffixed(n: UInt): Literal = Literal(WrapperLiteral.u32Unsuffixed(n))

        fun u64Unsuffixed(n: ULong): Literal = Literal(WrapperLiteral.u64Unsuffixed(n))

        fun u128Unsuffixed(n: ULong): Literal = Literal(WrapperLiteral.u128Unsuffixed(n))

        fun usizeUnsuffixed(n: ULong): Literal = Literal(WrapperLiteral.usizeUnsuffixed(n))

        fun i8Unsuffixed(n: Byte): Literal = Literal(WrapperLiteral.i8Unsuffixed(n))

        fun i16Unsuffixed(n: Short): Literal = Literal(WrapperLiteral.i16Unsuffixed(n))

        fun i32Unsuffixed(n: Int): Literal = Literal(WrapperLiteral.i32Unsuffixed(n))

        fun i64Unsuffixed(n: Long): Literal = Literal(WrapperLiteral.i64Unsuffixed(n))

        fun i128Unsuffixed(n: Long): Literal = Literal(WrapperLiteral.i128Unsuffixed(n))

        fun isizeUnsuffixed(n: Long): Literal = Literal(WrapperLiteral.isizeUnsuffixed(n))

        fun f32Unsuffixed(n: Float): Literal = Literal(WrapperLiteral.f32Unsuffixed(n))

        fun f64Unsuffixed(n: Double): Literal = Literal(WrapperLiteral.f64Unsuffixed(n))

        /** String literal. */
        fun string(string: String): Literal = Literal(WrapperLiteral.string(string))

        /** Character literal. */
        fun character(ch: Char): Literal = Literal(WrapperLiteral.character(ch))

        /** Character literal for any 21-bit Unicode scalar value. */
        fun characterCodePoint(codePoint: Int): Literal =
            Literal(WrapperLiteral.characterCodePoint(codePoint))

        /** Byte character literal. */
        fun byteCharacter(byte: UByte): Literal = Literal(WrapperLiteral.byteCharacter(byte))

        /** Byte string literal. */
        fun byteString(bytes: ByteArray): Literal = Literal(WrapperLiteral.byteString(bytes))

        /** C string literal. */
        fun cString(bytes: ByteArray): Literal = Literal(WrapperLiteral.cString(bytes))

        fun fromString(repr: String): LiteralParseResult {
            val result = WrapperLiteral.fromStrChecked(repr)
            return if (result.isSuccess) {
                LiteralParseResult(Literal(result.getOrThrow()), null, null)
            } else {
                val ex = result.exceptionOrNull()
                LiteralParseResult(
                    value = null,
                    error = ex?.message ?: "cannot parse string into literal",
                    lexError = ex.toLexErrorOrNull(),
                )
            }
        }

        fun fromStrUnchecked(repr: String): Literal = Literal(WrapperLiteral.fromStrUnchecked(repr))
    }

    /** Returns the span encompassing this literal. */
    fun span(): Span = Span(inner.span())

    /** Copies this literal, retaining its representation and span. */
    fun cloneToken(): Literal = Literal(inner.cloneToken())

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
        val repr = toString()
        if (repr.startsWith('"') && repr.endsWith('"') && repr.length >= 2) {
            val quoted = repr.substring(1, repr.length - 1)
            val value = StringBuilder(quoted.length)
            var failure: ConversionErrorKind? = null
            unescapeStr(quoted) { _, res ->
                when (res) {
                    is EscapeResult.Ok -> value.appendUnicodeScalar(res.value)
                    is EscapeResult.Err -> {
                        if (res.error.isFatal()) {
                            failure = ConversionErrorKind.FailedToUnescape(res.error)
                        }
                    }
                }
            }
            val finalFailure = failure
            return if (finalFailure != null) {
                StringParseResult(null, finalFailure.message(), finalFailure)
            } else {
                StringParseResult(value.toString(), null, null)
            }
        }
        if (repr.startsWith('r')) {
            val raw = getRaw(repr.substring(1))
            if (raw != null) {
                return StringParseResult(raw, null, null)
            }
        }
        val kind = ConversionErrorKind.InvalidLiteralKind
        return StringParseResult(null, kind.message(), kind)
    }

    /**
     * Returns the unescaped string value (including nul terminator) if this is
     * a C string literal.
     */
    fun cstrValue(): ByteArrayParseResult {
        val repr = toString()
        if (repr.startsWith("c\"") && repr.endsWith('"') && repr.length >= 3) {
            val quoted = repr.substring(2, repr.length - 1)
            val value = mutableListOf<Byte>()
            var failure: ConversionErrorKind? = null
            unescapeCStr(quoted) { _, res ->
                when (res) {
                    is EscapeResult.Ok ->
                        when (val unit = res.value) {
                            is MixedUnit.Char -> {
                                val utf8 = encodeScalarUtf8(unit.value.get())
                                for (b in utf8) {
                                    value.add(b)
                                }
                            }
                            is MixedUnit.HighByte -> value.add(unit.value.get().toByte())
                        }
                    is EscapeResult.Err -> {
                        if (res.error.isFatal()) {
                            failure = ConversionErrorKind.FailedToUnescape(res.error)
                        }
                    }
                }
            }
            val finalFailure = failure
            return if (finalFailure != null) {
                ByteArrayParseResult(null, finalFailure.message(), finalFailure)
            } else {
                value.add(0)
                ByteArrayParseResult(value.toByteArray(), null, null)
            }
        }
        if (repr.startsWith("cr")) {
            val raw = getRaw(repr.substring(2))
            if (raw != null) {
                return ByteArrayParseResult(raw.encodeToByteArray() + byteArrayOf(0), null, null)
            }
        }
        val kind = ConversionErrorKind.InvalidLiteralKind
        return ByteArrayParseResult(null, kind.message(), kind)
    }

    /** Returns the unescaped string value if this is a byte string literal. */
    fun byteStrValue(): ByteArrayParseResult {
        val repr = toString()
        if (repr.startsWith("b\"") && repr.endsWith('"') && repr.length >= 3) {
            val quoted = repr.substring(2, repr.length - 1)
            val value = mutableListOf<Byte>()
            var failure: ConversionErrorKind? = null
            unescapeByteStr(quoted) { _, res ->
                when (res) {
                    is EscapeResult.Ok -> value.add(res.value.toByte())
                    is EscapeResult.Err -> {
                        if (res.error.isFatal()) {
                            failure = ConversionErrorKind.FailedToUnescape(res.error)
                        }
                    }
                }
            }
            val finalFailure = failure
            return if (finalFailure != null) {
                ByteArrayParseResult(null, finalFailure.message(), finalFailure)
            } else {
                ByteArrayParseResult(value.toByteArray(), null, null)
            }
        }
        if (repr.startsWith("br")) {
            val raw = getRaw(repr.substring(2))
            if (raw != null) {
                return ByteArrayParseResult(raw.encodeToByteArray(), null, null)
            }
        }
        val kind = ConversionErrorKind.InvalidLiteralKind
        return ByteArrayParseResult(null, kind.message(), kind)
    }

    override fun toString(): String = inner.toString_()

    override fun equals(other: Any?): Boolean = other is Literal && inner.eq(other.inner)

    override fun hashCode(): Int = inner.toString_().hashCode()

    fun replaceFrom(other: Literal) {
        inner = other.inner
    }
}

/** Error when retrieving a string literal's unescaped value. */
public sealed class ConversionErrorKind {
    internal abstract fun message(): String

    /**
     * The literal is of the right string kind, but its contents are malformed
     * in a way that cannot be unescaped to a value.
     */
    public class FailedToUnescape(
        public val error: EscapeError,
    ) : ConversionErrorKind() {
        override fun message(): String = "failed to unescape literal: $error"

        override fun equals(other: Any?): Boolean = other is FailedToUnescape && other.error == error

        override fun hashCode(): Int = error.hashCode()

        override fun toString(): String = "FailedToUnescape($error)"
    }

    /**
     * The literal is not of the string kind whose value was requested, for
     * example byte string vs UTF-8 string.
     */
    public object InvalidLiteralKind : ConversionErrorKind() {
        override fun message(): String = "invalid literal kind"

        override fun toString(): String = "InvalidLiteralKind"
    }
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
