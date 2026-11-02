// port-lint: source rustc_literal_escaper.rs

// Vendored from rustc-literal-escaper v0.0.5.
// https://github.com/rust-lang/literal-escaper/tree/v0.0.5

/**
 * Utilities for validating (raw) string, char, and byte literals and turning
 * escape sequences into the values they represent.
 */
package io.github.kotlinmania.procmacro2

/**
 * Errors and warnings that can occur during string, char, and byte unescaping.
 *
 * Mostly relating to malformed escape sequences, but also a few other problems.
 */
public enum class EscapeError {
    /** Expected 1 char, but 0 were found. */
    ZeroChars,

    /** Expected 1 char, but more than 1 were found. */
    MoreThanOneChar,

    /** Escaped backslash character without continuation. */
    LoneSlash,

    /** Invalid escape character, for example `\z`. */
    InvalidEscape,

    /** Raw carriage return encountered. */
    BareCarriageReturn,

    /** Raw carriage return encountered in raw string. */
    BareCarriageReturnInRawString,

    /** Unescaped character that was expected to be escaped, for example raw tab. */
    EscapeOnlyChar,

    /** Numeric character escape is too short, for example `\x1`. */
    TooShortHexEscape,

    /** Invalid character in numeric escape, for example `\xz`. */
    InvalidCharInHexEscape,

    /** Character code in numeric escape is non-ASCII, for example `\xFF`. */
    OutOfRangeHexEscape,

    /** Unicode escape was not followed by `{`. */
    NoBraceInUnicodeEscape,

    /** Non-hexadecimal value in a Unicode escape. */
    InvalidCharInUnicodeEscape,

    /** Empty Unicode escape. */
    EmptyUnicodeEscape,

    /** No closing brace in a Unicode escape, for example `\u{12`. */
    UnclosedUnicodeEscape,

    /** Unicode escape starts with an underscore. */
    LeadingUnderscoreUnicodeEscape,

    /** More than 6 characters in a Unicode escape, for example `\u{10FFFF_FF}`. */
    OverlongUnicodeEscape,

    /** Invalid in-bound Unicode character code, for example a surrogate. */
    LoneSurrogateUnicodeEscape,

    /** Out of bounds Unicode character code, for example `\u{FFFFFF}`. */
    OutOfRangeUnicodeEscape,

    /** Unicode escape code in byte literal. */
    UnicodeEscapeInByte,

    /** Non-ASCII character in byte literal, byte string literal, or raw byte string literal. */
    NonAsciiCharInByte,

    /** NUL in a C string literal. */
    NulInCStr,

    /**
     * After a line ending with backslash, the next line contains whitespace
     * characters that are not skipped.
     */
    UnskippedWhitespaceWarning,

    /** After a line ending with backslash, multiple lines are skipped. */
    MultipleSkippedLinesWarning,
    ;

    /** Returns true for actual errors, as opposed to warnings. */
    public fun isFatal(): Boolean = this != UnskippedWhitespaceWarning && this != MultipleSkippedLinesWarning
}

/** Half-open byte range into the source string. */
internal data class ByteRange(
    val start: Int,
    val end: Int,
)

/**
 * Result of unescaping or checking a single unit of a literal. Equivalent to
 * `Result<T, EscapeError>` in upstream; sealed here so callers can pattern-
 * match in Kotlin.
 */
internal sealed class EscapeResult<out T> {
    internal data class Ok<T>(
        val value: T,
    ) : EscapeResult<T>()

    internal data class Err(
        val error: EscapeError,
    ) : EscapeResult<Nothing>()
}

/**
 * The non-zero byte type. Used to carry the invariant that a byte literal's
 * unescaped value is not zero.
 */
internal class NonZeroU8 private constructor(
    private val value: Int,
) {
    companion object {
        fun new(byte: Int): NonZeroU8? =
            if ((byte and 0xFF) == 0) {
                null
            } else {
                NonZeroU8(byte and 0xFF)
            }
    }

    fun get(): Int = value

    override fun equals(other: Any?): Boolean = other is NonZeroU8 && value == other.value

    override fun hashCode(): Int = value

    override fun toString(): String = "NonZeroU8($value)"
}

/**
 * Check a raw string literal for validity.
 *
 * Takes the contents of a raw string literal, without quotes, and produces a
 * sequence of 21-bit Unicode scalar values or errors, which are returned by
 * invoking `callback`. Does no escaping, but produces errors for bare carriage
 * return.
 */
internal fun checkRawStr(
    src: String,
    callback: (ByteRange, EscapeResult<Int>) -> Unit,
) {
    checkRaw(src, ::char2rawUnit, callback)
}

/**
 * Check a raw byte string literal for validity.
 *
 * Takes the contents of a raw byte string literal, without quotes, and produces
 * a sequence of bytes or errors, which are returned by invoking `callback`.
 * Does no escaping, but produces errors for bare carriage return.
 */
internal fun checkRawByteStr(
    src: String,
    callback: (ByteRange, EscapeResult<Int>) -> Unit,
) {
    checkRaw(src, ::byte2rawUnit, callback)
}

/**
 * Check a raw C string literal for validity.
 *
 * Takes the contents of a raw C string literal, without quotes, and produces a
 * sequence of characters or errors, which are returned by invoking `callback`.
 * Does no escaping, but produces errors for bare carriage return.
 */
internal fun checkRawCStr(
    src: String,
    callback: (ByteRange, EscapeResult<NonZeroChar>) -> Unit,
) {
    checkRaw(src, ::cstr2rawUnit, callback)
}

private fun <T> checkRaw(
    src: String,
    char2RawUnit: (Int) -> EscapeResult<T>,
    callback: (ByteRange, EscapeResult<T>) -> Unit,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val c = chars.next() ?: break
        val res =
            if (c == '\r'.code) {
                err(EscapeError.BareCarriageReturnInRawString)
            } else {
                char2RawUnit(c)
            }
        val end = chars.byteIndex
        callback(ByteRange(start, end), res)
    }
}

private fun char2rawUnit(c: Int): EscapeResult<Int> = ok(c)

private fun byte2rawUnit(c: Int): EscapeResult<Int> = char2byte(c)

private fun cstr2rawUnit(c: Int): EscapeResult<NonZeroChar> =
    NonZeroChar.new(c)?.let(::ok) ?: err(EscapeError.NulInCStr)

private fun char2byte(c: Int): EscapeResult<Int> =
    if (c in 0..0x7F) {
        ok(c)
    } else {
        err(EscapeError.NonAsciiCharInByte)
    }

/**
 * Unescape a char literal.
 *
 * Takes the contents of a char literal, without quotes, and returns an
 * unescaped 21-bit Unicode scalar value or an error.
 */
internal fun unescapeChar(src: String): EscapeResult<Int> = unescapeSingle(ScalarCursor(src), CharUnescape)

/**
 * Unescape a byte literal.
 *
 * Takes the contents of a byte literal, without quotes, and returns an
 * unescaped byte or an error.
 */
internal fun unescapeByte(src: String): EscapeResult<Int> = unescapeSingle(ScalarCursor(src), ByteUnescape)

/**
 * Unescape a string literal.
 *
 * Takes the contents of a string literal, without quotes, and produces a
 * sequence of escaped 21-bit Unicode scalar values or errors, which are
 * returned by invoking `callback`.
 */
internal fun unescapeStr(
    src: String,
    callback: (ByteRange, EscapeResult<Int>) -> Unit,
) {
    unescape(src, CharUnescape, callback)
}

/**
 * Unescape a byte string literal.
 *
 * Takes the contents of a byte string literal, without quotes, and produces a
 * sequence of escaped bytes or errors, which are returned by invoking
 * `callback`.
 */
internal fun unescapeByteStr(
    src: String,
    callback: (ByteRange, EscapeResult<Int>) -> Unit,
) {
    unescape(src, ByteUnescape, callback)
}

/**
 * Unescape a C string literal.
 *
 * Takes the contents of a C string literal, without quotes, and produces a
 * sequence of escaped mixed units or errors, which are returned by invoking
 * `callback`.
 */
internal fun unescapeCStr(
    src: String,
    callback: (ByteRange, EscapeResult<MixedUnit>) -> Unit,
) {
    unescape(src, CStrUnescape, callback)
}

/**
 * Enum representing either a Unicode scalar or a high byte.
 *
 * Used for mixed UTF-8 string literals, meaning those that allow both Unicode
 * chars and high bytes.
 */
internal sealed class MixedUnit {
    data class Char(
        val value: NonZeroChar,
    ) : MixedUnit()

    data class HighByte(
        val value: NonZeroU8,
    ) : MixedUnit()

    companion object {
        fun from(c: NonZeroChar): MixedUnit = Char(c)

        fun from(byte: NonZeroU8): MixedUnit =
            if (byte.get() <= 0x7F) {
                Char(checkNotNull(NonZeroChar.new(byte.get())))
            } else {
                HighByte(byte)
            }

        fun tryFromScalar(codePoint: Int): EscapeResult<MixedUnit> =
            NonZeroChar.new(codePoint)?.let { ok(Char(it)) } ?: err(EscapeError.NulInCStr)

        fun tryFromByte(byte: Int): EscapeResult<MixedUnit> {
            val nonzero = NonZeroU8.new(byte) ?: return err(EscapeError.NulInCStr)
            return ok(from(nonzero))
        }
    }
}

private interface UnescapeStrategy<T> {
    val zeroResult: EscapeResult<T>

    fun nonzeroByte2unit(b: NonZeroU8): T

    fun char2unit(c: Int): EscapeResult<T>

    fun hex2unit(b: Int): EscapeResult<T>

    fun unicode2unit(r: EscapeResult<Int>): EscapeResult<T>
}

private fun <T> unescapeSingle(
    chars: ScalarCursor,
    strategy: UnescapeStrategy<T>,
): EscapeResult<T> {
    val res =
        when (val c = chars.next()) {
            null -> return err(EscapeError.ZeroChars)
            '\\'.code -> unescape1(chars, strategy)
            '\n'.code, '\t'.code, '\''.code -> err(EscapeError.EscapeOnlyChar)
            '\r'.code -> err(EscapeError.BareCarriageReturn)
            else -> strategy.char2unit(c)
        }
    val value =
        when (res) {
            is EscapeResult.Ok -> res.value
            is EscapeResult.Err -> return res
        }
    if (chars.next() != null) {
        return err(EscapeError.MoreThanOneChar)
    }
    return ok(value)
}

private fun <T> unescape1(
    chars: ScalarCursor,
    strategy: UnescapeStrategy<T>,
): EscapeResult<T> {
    val c = chars.next() ?: return err(EscapeError.LoneSlash)
    if (c == '0'.code) {
        return strategy.zeroResult
    }
    val simple = simpleEscape(c)
    if (simple is SimpleEscape.Known) {
        return ok(strategy.nonzeroByte2unit(simple.byte))
    }
    return when (c) {
        'x'.code -> {
            when (val byte = hexEscape(chars)) {
                is EscapeResult.Ok -> strategy.hex2unit(byte.value)
                is EscapeResult.Err -> byte
            }
        }
        'u'.code -> {
            val unicode =
                when (val value = unicodeEscape(chars)) {
                    is EscapeResult.Ok -> scalarValueToCodePoint(value.value)
                    is EscapeResult.Err -> return value
                }
            strategy.unicode2unit(unicode)
        }
        else -> err(EscapeError.InvalidEscape)
    }
}

private fun <T> unescape(
    src: String,
    strategy: UnescapeStrategy<T>,
    callback: (ByteRange, EscapeResult<T>) -> Unit,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val c = chars.next() ?: break
        val res =
            when (c) {
                '\\'.code -> {
                    if (chars.peek() == '\n'.code) {
                        chars.next()
                        skipAsciiWhitespace(chars, start) { range, err ->
                            callback(range, err(err))
                        }
                        continue
                    } else {
                        unescape1(chars, strategy)
                    }
                }
                '"'.code -> err(EscapeError.EscapeOnlyChar)
                '\r'.code -> err(EscapeError.BareCarriageReturn)
                else -> strategy.char2unit(c)
            }
        val end = chars.byteIndex
        callback(ByteRange(start, end), res)
    }
}

private fun simpleEscape(c: Int): SimpleEscape {
    val byte =
        when (c) {
            '"'.code -> '"'.code
            'n'.code -> '\n'.code
            'r'.code -> '\r'.code
            't'.code -> '\t'.code
            '\\'.code -> '\\'.code
            '\''.code -> '\''.code
            else -> return SimpleEscape.Unknown(c)
        }
    return SimpleEscape.Known(checkNotNull(NonZeroU8.new(byte)))
}

private sealed class SimpleEscape {
    data class Known(
        val byte: NonZeroU8,
    ) : SimpleEscape()

    data class Unknown(
        val codePoint: Int,
    ) : SimpleEscape()
}

private fun hexDigitValue(codePoint: Int): Int? =
    when (codePoint) {
        in '0'.code..'9'.code -> codePoint - '0'.code
        in 'a'.code..'f'.code -> codePoint - 'a'.code + 10
        in 'A'.code..'F'.code -> codePoint - 'A'.code + 10
        else -> null
    }

private fun hexEscape(chars: ScalarCursor): EscapeResult<Int> {
    val hi = chars.next() ?: return err(EscapeError.TooShortHexEscape)
    val hiDigit = hexDigitValue(hi) ?: return err(EscapeError.InvalidCharInHexEscape)

    val lo = chars.next() ?: return err(EscapeError.TooShortHexEscape)
    val loDigit = hexDigitValue(lo) ?: return err(EscapeError.InvalidCharInHexEscape)

    return ok(hiDigit * 16 + loDigit)
}

private fun unicodeEscape(chars: ScalarCursor): EscapeResult<Int> {
    if (chars.next() != '{'.code) {
        return err(EscapeError.NoBraceInUnicodeEscape)
    }

    var value =
        when (val c = chars.next()) {
            null -> return err(EscapeError.UnclosedUnicodeEscape)
            '_'.code -> return err(EscapeError.LeadingUnderscoreUnicodeEscape)
            '}'.code -> return err(EscapeError.EmptyUnicodeEscape)
            else -> hexDigitValue(c) ?: return err(EscapeError.InvalidCharInUnicodeEscape)
        }

    var digits = 1
    while (true) {
        when (val c = chars.next()) {
            null -> return err(EscapeError.UnclosedUnicodeEscape)
            '_'.code -> continue
            '}'.code -> {
                return if (digits > 6) {
                    err(EscapeError.OverlongUnicodeEscape)
                } else {
                    ok(value)
                }
            }
            else -> {
                val digit = hexDigitValue(c) ?: return err(EscapeError.InvalidCharInUnicodeEscape)
                digits += 1
                if (digits > 6) {
                    continue
                }
                value = value * 16 + digit
            }
        }
    }
}

private fun skipAsciiWhitespace(
    chars: ScalarCursor,
    start: Int,
    callback: (ByteRange, EscapeError) -> Unit,
) {
    val afterBackslashNewline = chars.byteIndex
    var sawNewline = false
    while (true) {
        when (chars.peek()) {
            ' '.code -> chars.next()
            '\t'.code -> chars.next()
            '\n'.code -> {
                chars.next()
                sawNewline = true
            }
            '\r'.code -> chars.next()
            else -> break
        }
    }
    val firstNonSpace = chars.byteIndex - afterBackslashNewline
    val end = start + 2 + firstNonSpace
    if (sawNewline) {
        callback(ByteRange(start, end), EscapeError.MultipleSkippedLinesWarning)
    }
    val c = chars.peek()
    if (c != null && isUnicodeWhiteSpace(c)) {
        callback(
            ByteRange(start, end + utf8ScalarLen(c)),
            EscapeError.UnskippedWhitespaceWarning,
        )
    }
}

private fun isUnicodeWhiteSpace(codePoint: Int): Boolean =
    codePoint in 0x0009..0x000D ||
        codePoint == 0x0020 ||
        codePoint == 0x0085 ||
        codePoint == 0x00A0 ||
        codePoint == 0x1680 ||
        codePoint in 0x2000..0x200A ||
        codePoint == 0x2028 ||
        codePoint == 0x2029 ||
        codePoint == 0x202F ||
        codePoint == 0x205F ||
        codePoint == 0x3000

private object CharUnescape : UnescapeStrategy<Int> {
    override val zeroResult: EscapeResult<Int> = ok(0)

    override fun nonzeroByte2unit(b: NonZeroU8): Int = b.get()

    override fun char2unit(c: Int): EscapeResult<Int> = ok(c)

    override fun hex2unit(b: Int): EscapeResult<Int> =
        if (b <= 0x7F) {
            ok(b)
        } else {
            err(EscapeError.OutOfRangeHexEscape)
        }

    override fun unicode2unit(r: EscapeResult<Int>): EscapeResult<Int> = r
}

private object ByteUnescape : UnescapeStrategy<Int> {
    override val zeroResult: EscapeResult<Int> = ok(0)

    override fun nonzeroByte2unit(b: NonZeroU8): Int = b.get()

    override fun char2unit(c: Int): EscapeResult<Int> = char2byte(c)

    override fun hex2unit(b: Int): EscapeResult<Int> = ok(b)

    override fun unicode2unit(r: EscapeResult<Int>): EscapeResult<Int> =
        when (r) {
            is EscapeResult.Ok,
            is EscapeResult.Err,
            -> err(EscapeError.UnicodeEscapeInByte)
        }
}

private object CStrUnescape : UnescapeStrategy<MixedUnit> {
    override val zeroResult: EscapeResult<MixedUnit> = err(EscapeError.NulInCStr)

    override fun nonzeroByte2unit(b: NonZeroU8): MixedUnit = MixedUnit.from(b)

    override fun char2unit(c: Int): EscapeResult<MixedUnit> = MixedUnit.tryFromScalar(c)

    override fun hex2unit(b: Int): EscapeResult<MixedUnit> = MixedUnit.tryFromByte(b)

    override fun unicode2unit(r: EscapeResult<Int>): EscapeResult<MixedUnit> =
        when (r) {
            is EscapeResult.Ok -> char2unit(r.value)
            is EscapeResult.Err -> r
        }
}

/** Enum of the different kinds of literal. */
internal enum class Mode {
    Char,
    Byte,
    Str,
    RawStr,
    ByteStr,
    RawByteStr,
    CStr,
    RawCStr,
    ;

    fun inDoubleQuotes(): Boolean =
        when (this) {
            Str,
            RawStr,
            ByteStr,
            RawByteStr,
            CStr,
            RawCStr,
            -> true
            Char,
            Byte,
            -> false
        }

    fun prefixNoraw(): String =
        when (this) {
            Char,
            Str,
            RawStr,
            -> ""
            Byte,
            ByteStr,
            RawByteStr,
            -> "b"
            CStr,
            RawCStr,
            -> "c"
        }
}

internal fun checkForErrors(
    src: String,
    mode: Mode,
    errorCallback: (ByteRange, EscapeError) -> Unit,
) {
    when (mode) {
        Mode.Char -> {
            val chars = ScalarCursor(src)
            when (val result = unescapeSingle(chars, CharUnescape)) {
                is EscapeResult.Err -> errorCallback(ByteRange(0, chars.byteIndex), result.error)
                is EscapeResult.Ok -> Unit
            }
        }
        Mode.Byte -> {
            val chars = ScalarCursor(src)
            when (val result = unescapeSingle(chars, ByteUnescape)) {
                is EscapeResult.Err -> errorCallback(ByteRange(0, chars.byteIndex), result.error)
                is EscapeResult.Ok -> Unit
            }
        }
        Mode.Str ->
            unescapeStr(src) { range, res ->
                if (res is EscapeResult.Err) {
                    errorCallback(range, res.error)
                }
            }
        Mode.ByteStr ->
            unescapeByteStr(src) { range, res ->
                if (res is EscapeResult.Err) {
                    errorCallback(range, res.error)
                }
            }
        Mode.CStr ->
            unescapeCStr(src) { range, res ->
                if (res is EscapeResult.Err) {
                    errorCallback(range, res.error)
                }
            }
        Mode.RawStr ->
            checkRawStr(src) { range, res ->
                if (res is EscapeResult.Err) {
                    errorCallback(range, res.error)
                }
            }
        Mode.RawByteStr ->
            checkRawByteStr(src) { range, res ->
                if (res is EscapeResult.Err) {
                    errorCallback(range, res.error)
                }
            }
        Mode.RawCStr ->
            checkRawCStr(src) { range, res ->
                if (res is EscapeResult.Err) {
                    errorCallback(range, res.error)
                }
            }
    }
}

private fun scalarValueToCodePoint(value: Int): EscapeResult<Int> =
    when {
        value < 0 || value > 0x10FFFF -> err(EscapeError.OutOfRangeUnicodeEscape)
        value in 0xD800..0xDFFF -> err(EscapeError.LoneSurrogateUnicodeEscape)
        else -> ok(value)
    }

private fun <T> ok(value: T): EscapeResult<T> = EscapeResult.Ok(value)

private fun err(error: EscapeError): EscapeResult.Err = EscapeResult.Err(error)

private class ScalarCursor(
    private val src: String,
) {
    private var index: Int = 0
    var byteIndex: Int = 0
        private set

    fun next(): Int? {
        if (index >= src.length) {
            return null
        }
        val first = src[index]
        if (first.isHighSurrogate() && index + 1 < src.length) {
            val second = src[index + 1]
            if (second.isLowSurrogate()) {
                val scalar =
                    ((first.code - 0xD800) shl 10) +
                        (second.code - 0xDC00) +
                        0x10000
                index += 2
                byteIndex += 4
                return scalar
            }
        }
        index += 1
        byteIndex += utf8ScalarLen(first.code)
        return first.code
    }

    fun peek(): Int? {
        if (index >= src.length) {
            return null
        }
        val first = src[index]
        if (first.isHighSurrogate() && index + 1 < src.length) {
            val second = src[index + 1]
            if (second.isLowSurrogate()) {
                return ((first.code - 0xD800) shl 10) +
                    (second.code - 0xDC00) +
                    0x10000
            }
        }
        return first.code
    }
}

internal fun utf8ScalarLen(codePoint: Int): Int =
    when {
        codePoint <= 0x7F -> 1
        codePoint <= 0x7FF -> 2
        codePoint <= 0xFFFF -> 3
        else -> 4
    }

internal fun StringBuilder.appendUnicodeScalar(codePoint: Int): StringBuilder {
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        val high = ((offset ushr 10) + 0xD800).toChar()
        val low = ((offset and 0x3FF) + 0xDC00).toChar()
        append(high)
        append(low)
    }
    return this
}

internal fun encodeScalarUtf8(codePoint: Int): ByteArray =
    when {
        codePoint <= 0x7F -> byteArrayOf(codePoint.toByte())
        codePoint <= 0x7FF ->
            byteArrayOf(
                (0xC0 or (codePoint ushr 6)).toByte(),
                (0x80 or (codePoint and 0x3F)).toByte(),
            )
        codePoint <= 0xFFFF ->
            byteArrayOf(
                (0xE0 or (codePoint ushr 12)).toByte(),
                (0x80 or ((codePoint ushr 6) and 0x3F)).toByte(),
                (0x80 or (codePoint and 0x3F)).toByte(),
            )
        else ->
            byteArrayOf(
                (0xF0 or (codePoint ushr 18)).toByte(),
                (0x80 or ((codePoint ushr 12) and 0x3F)).toByte(),
                (0x80 or ((codePoint ushr 6) and 0x3F)).toByte(),
                (0x80 or (codePoint and 0x3F)).toByte(),
            )
    }
