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
enum class EscapeError {
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
    fun isFatal(): Boolean {
        return this != UnskippedWhitespaceWarning && this != MultipleSkippedLinesWarning
    }
}

/** Half-open byte range into the source string, mirroring `Range<usize>`. */
data class ByteRange(
    val start: Int,
    val end: Int,
)

/**
 * Result of unescaping or checking a single unit of a literal. Equivalent to
 * `Result<T, EscapeError>` in upstream; sealed here so callers can pattern-
 * match in Kotlin.
 */
sealed class EscapeResult<out T> {
    data class Ok<T>(val value: T) : EscapeResult<T>()

    data class Err(val error: EscapeError) : EscapeResult<Nothing>()
}

/**
 * The non-zero byte type, mirroring Rust's `core::num::NonZeroU8`. Used to
 * carry the invariant that a byte literal's unescaped value is not `0`.
 */
class NonZeroU8 private constructor(private val value: Int) {
    companion object {
        fun new(byte: Int): NonZeroU8? {
            return if ((byte and 0xFF) == 0) {
                null
            } else {
                NonZeroU8(byte and 0xFF)
            }
        }
    }

    fun get(): Int {
        return value
    }

    override fun equals(other: Any?): Boolean {
        return other is NonZeroU8 && value == other.value
    }

    override fun hashCode(): Int {
        return value
    }

    override fun toString(): String {
        return "NonZeroU8($value)"
    }
}

/**
 * Check a raw string literal for validity.
 *
 * Takes the contents of a raw string literal, without quotes, and produces a
 * sequence of characters or errors, which are returned by invoking `callback`.
 * Does no escaping, but produces errors for bare carriage return.
 */
fun checkRawStr(
    src: String,
    callback: (ByteRange, EscapeResult<Char>) -> Unit,
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
fun checkRawByteStr(
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
fun checkRawCStr(
    src: String,
    callback: (ByteRange, EscapeResult<NonZeroChar>) -> Unit,
) {
    checkRaw(src, ::cstr2rawUnit, callback)
}

/**
 * Generic implementation of raw-literal checking, parameterised by the per-type
 * conversion `char2RawUnit`. Mirrors the upstream `trait CheckRaw`'s default
 * `check_raw` method whose impls for `str`, `[u8]`, and `CStr` differ only in
 * the associated `RawUnit` type and the `char2raw_unit` conversion. Takes the
 * contents of a raw literal (without quotes) and produces a sequence of
 * results which are returned via `callback`. Does no escaping, but produces
 * errors for bare carriage return.
 */
private fun <T> checkRaw(
    src: String,
    char2RawUnit: (Char) -> EscapeResult<T>,
    callback: (ByteRange, EscapeResult<T>) -> Unit,
) {
    val chars = CharCursor(src)
    while (true) {
        val start = chars.byteIndex
        val c = chars.next() ?: break
        val res =
            if (c == '\r') {
                err(EscapeError.BareCarriageReturnInRawString)
            } else {
                char2RawUnit(c)
            }
        val end = chars.byteIndex
        callback(ByteRange(start, end), res)
    }
}

/**
 * `RawUnit` conversion for raw `str` literals: every char passes through.
 * Equivalent to upstream `impl CheckRaw for str`.
 */
private fun char2rawUnit(c: Char): EscapeResult<Char> {
    return ok(c)
}

/**
 * `RawUnit` conversion for raw byte string literals: chars are constrained
 * to ASCII and emitted as bytes. Equivalent to upstream `impl CheckRaw for [u8]`.
 */
private fun byte2rawUnit(c: Char): EscapeResult<Int> {
    return char2byte(c)
}

/**
 * `RawUnit` conversion for raw C string literals: chars must be non-NUL.
 * Equivalent to upstream `impl CheckRaw for CStr`.
 */
private fun cstr2rawUnit(c: Char): EscapeResult<NonZeroChar> {
    return NonZeroChar.new(c)?.let(::ok) ?: err(EscapeError.NulInCStr)
}

/** Turn an ASCII char into a byte. */
private fun char2byte(c: Char): EscapeResult<Int> {
    return if (c.code <= 0x7F) {
        ok(c.code)
    } else {
        err(EscapeError.NonAsciiCharInByte)
    }
}

/**
 * Unescape a char literal.
 *
 * Takes the contents of a char literal, without quotes, and returns an
 * unescaped char or an error.
 */
fun unescapeChar(src: String): EscapeResult<Char> {
    return unescapeSingle(CharCursor(src), CharUnescape)
}

/**
 * Unescape a byte literal.
 *
 * Takes the contents of a byte literal, without quotes, and returns an
 * unescaped byte or an error.
 */
fun unescapeByte(src: String): EscapeResult<Int> {
    return unescapeSingle(CharCursor(src), ByteUnescape)
}

/**
 * Unescape a string literal.
 *
 * Takes the contents of a string literal, without quotes, and produces a
 * sequence of escaped characters or errors, which are returned by invoking
 * `callback`.
 */
fun unescapeStr(
    src: String,
    callback: (ByteRange, EscapeResult<Char>) -> Unit,
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
fun unescapeByteStr(
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
fun unescapeCStr(
    src: String,
    callback: (ByteRange, EscapeResult<MixedUnit>) -> Unit,
) {
    unescape(src, CStrUnescape, callback)
}

/**
 * Enum representing either a char or a byte.
 *
 * Used for mixed UTF-8 string literals, meaning those that allow both Unicode
 * chars and high bytes.
 */
sealed class MixedUnit {
    /**
     * Used for ASCII chars, written directly or via `\x00` through `\x7f`
     * escapes, and Unicode chars, written directly or via Unicode escapes.
     *
     * For example, if `\u00A5` appears in a string it is represented here as
     * `MixedUnit.Char`, and it will be appended to the relevant byte string as
     * the two-byte UTF-8 sequence `0xc2 0xa5`.
     */
    data class Char(val value: NonZeroChar) : MixedUnit()

    /**
     * Used for high bytes, `\x80` through `\xff`.
     *
     * For example, if `\xa5` appears in a string it is represented here as
     * `MixedUnit.HighByte`, and it will be appended to the relevant byte string
     * as the single byte `0xa5`.
     */
    data class HighByte(val value: NonZeroU8) : MixedUnit()

    companion object {
        fun from(c: NonZeroChar): MixedUnit {
            return Char(c)
        }

        fun from(byte: NonZeroU8): MixedUnit {
            return if (byte.get() <= 0x7F) {
                Char(NonZeroChar.new(byte.get().toChar())!!)
            } else {
                HighByte(byte)
            }
        }

        fun tryFrom(c: kotlin.Char): EscapeResult<MixedUnit> {
            return NonZeroChar.new(c)?.let { ok(Char(it)) } ?: err(EscapeError.NulInCStr)
        }

        fun tryFrom(byte: Int): EscapeResult<MixedUnit> {
            val nonzero = NonZeroU8.new(byte) ?: return err(EscapeError.NulInCStr)
            return ok(from(nonzero))
        }
    }
}

/**
 * Strategy interface for unescaping escape sequences in strings. Mirrors the
 * upstream `trait Unescape`: each implementing object pins the associated
 * `Unit` type (`Char` for string, `Int` for byte string, [MixedUnit] for C
 * string) and supplies the conversions used by [unescapeSingle], [unescape1],
 * and [unescape].
 */
private interface UnescapeStrategy<T> {
    /** Result of unescaping the zero char (`\0`). */
    val zeroResult: EscapeResult<T>

    /** Converts non-zero bytes to the unit type. */
    fun nonzeroByte2unit(b: NonZeroU8): T

    /** Converts chars to the unit type. */
    fun char2unit(c: Char): EscapeResult<T>

    /** Converts the byte of a hex escape to the unit type. */
    fun hex2unit(b: Int): EscapeResult<T>

    /** Converts the result of a Unicode escape to the unit type. */
    fun unicode2unit(r: EscapeResult<Char>): EscapeResult<T>
}

/** Unescape a single unit (single-quote syntax). */
private fun <T> unescapeSingle(
    chars: CharCursor,
    strategy: UnescapeStrategy<T>,
): EscapeResult<T> {
    val res =
        when (val c = chars.next()) {
            null -> return err(EscapeError.ZeroChars)
            '\\' -> unescape1(chars, strategy)
            '\n', '\t', '\'' -> err(EscapeError.EscapeOnlyChar)
            '\r' -> err(EscapeError.BareCarriageReturn)
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

/** Unescape the first unit of a string (double-quoted syntax). Previous char was a backslash. */
private fun <T> unescape1(
    chars: CharCursor,
    strategy: UnescapeStrategy<T>,
): EscapeResult<T> {
    val c = chars.next() ?: return err(EscapeError.LoneSlash)
    if (c == '0') {
        return strategy.zeroResult
    }
    val simple = simpleEscape(c)
    if (simple is SimpleEscape.Known) {
        return ok(strategy.nonzeroByte2unit(simple.byte))
    }
    return when (c) {
        'x' -> {
            when (val byte = hexEscape(chars)) {
                is EscapeResult.Ok -> strategy.hex2unit(byte.value)
                is EscapeResult.Err -> byte
            }
        }
        'u' -> {
            val unicode =
                when (val value = unicodeEscape(chars)) {
                    is EscapeResult.Ok -> scalarValueToChar(value.value)
                    is EscapeResult.Err -> return value
                }
            strategy.unicode2unit(unicode)
        }
        else -> err(EscapeError.InvalidEscape)
    }
}

/**
 * Unescape a string literal.
 *
 * Takes the contents of a raw string literal (without quotes) and produces a
 * sequence of results which are returned via `callback`.
 */
private fun <T> unescape(
    src: String,
    strategy: UnescapeStrategy<T>,
    callback: (ByteRange, EscapeResult<T>) -> Unit,
) {
    val chars = CharCursor(src)
    while (true) {
        val start = chars.byteIndex
        val c = chars.next() ?: break
        val res =
            when (c) {
                '\\' -> {
                    if (chars.peek() == '\n') {
                        chars.next()
                        skipAsciiWhitespace(chars, start) { range, err ->
                            callback(range, err(err))
                        }
                        continue
                    } else {
                        unescape1(chars, strategy)
                    }
                }
                '"' -> err(EscapeError.EscapeOnlyChar)
                '\r' -> err(EscapeError.BareCarriageReturn)
                else -> strategy.char2unit(c)
            }
        val end = chars.byteIndex
        callback(ByteRange(start, end), res)
    }
}

/** Interpret a non-NUL ASCII escape. */
private fun simpleEscape(c: Char): SimpleEscape {
    val byte =
        when (c) {
            '"' -> '"'.code
            'n' -> '\n'.code
            'r' -> '\r'.code
            't' -> '\t'.code
            '\\' -> '\\'.code
            '\'' -> '\''.code
            else -> return SimpleEscape.Unknown(c)
        }
    return SimpleEscape.Known(NonZeroU8.new(byte)!!)
}

/**
 * Result of [simpleEscape]: either a recognised non-NUL escape ([Known]) or
 * the unrecognised character to retry with another decoder ([Unknown]).
 * Models upstream's `Result<NonZeroU8, char>`.
 */
private sealed class SimpleEscape {
    data class Known(val byte: NonZeroU8) : SimpleEscape()

    data class Unknown(val char: Char) : SimpleEscape()
}

/** Interpret a hexadecimal escape. */
private fun hexEscape(chars: CharCursor): EscapeResult<Int> {
    val hi = chars.next() ?: return err(EscapeError.TooShortHexEscape)
    val hiDigit = hi.digitToIntOrNull(16) ?: return err(EscapeError.InvalidCharInHexEscape)

    val lo = chars.next() ?: return err(EscapeError.TooShortHexEscape)
    val loDigit = lo.digitToIntOrNull(16) ?: return err(EscapeError.InvalidCharInHexEscape)

    return ok(hiDigit * 16 + loDigit)
}

/** Interpret a Unicode escape. */
private fun unicodeEscape(chars: CharCursor): EscapeResult<Int> {
    if (chars.next() != '{') {
        return err(EscapeError.NoBraceInUnicodeEscape)
    }

    var value =
        when (val c = chars.next()) {
            null -> return err(EscapeError.UnclosedUnicodeEscape)
            '_' -> return err(EscapeError.LeadingUnderscoreUnicodeEscape)
            '}' -> return err(EscapeError.EmptyUnicodeEscape)
            else -> c.digitToIntOrNull(16) ?: return err(EscapeError.InvalidCharInUnicodeEscape)
        }

    var digits = 1
    while (true) {
        when (val c = chars.next()) {
            null -> return err(EscapeError.UnclosedUnicodeEscape)
            '_' -> continue
            '}' -> {
                return if (digits > 6) {
                    err(EscapeError.OverlongUnicodeEscape)
                } else {
                    ok(value)
                }
            }
            else -> {
                val digit = c.digitToIntOrNull(16) ?: return err(EscapeError.InvalidCharInUnicodeEscape)
                digits += 1
                if (digits > 6) {
                    continue
                }
                value = value * 16 + digit
            }
        }
    }
}

/**
 * Skip whitespace following a backslash-newline string continuation, calling
 * `callback` with [EscapeError.MultipleSkippedLinesWarning] when more than one
 * line is consumed and [EscapeError.UnskippedWhitespaceWarning] when non-ASCII
 * whitespace was the first non-skipped character. See the Rust Reference's
 * "String literals" section.
 */
private fun skipAsciiWhitespace(
    chars: CharCursor,
    start: Int,
    callback: (ByteRange, EscapeError) -> Unit,
) {
    val afterBackslashNewline = chars.byteIndex
    var sawNewline = false
    while (true) {
        when (chars.peek()) {
            ' ' -> chars.next()
            '\t' -> chars.next()
            '\n' -> {
                chars.next()
                sawNewline = true
            }
            '\r' -> chars.next()
            else -> break
        }
    }
    val firstNonSpace = chars.byteIndex - afterBackslashNewline
    val end = start + 2 + firstNonSpace
    if (sawNewline) {
        callback(ByteRange(start, end), EscapeError.MultipleSkippedLinesWarning)
    }
    val c = chars.peek()
    if (c != null && c.isWhitespace()) {
        callback(
            ByteRange(start, end + utf8Len(c)),
            EscapeError.UnskippedWhitespaceWarning,
        )
    }
}

/** Unescape strategy for `str` literals. Equivalent to upstream `impl Unescape for str`. */
private object CharUnescape : UnescapeStrategy<Char> {
    override val zeroResult: EscapeResult<Char> = ok('\u0000')

    override fun nonzeroByte2unit(b: NonZeroU8): Char {
        return b.get().toChar()
    }

    override fun char2unit(c: Char): EscapeResult<Char> {
        return ok(c)
    }

    override fun hex2unit(b: Int): EscapeResult<Char> {
        return if (b <= 0x7F) {
            ok(b.toChar())
        } else {
            err(EscapeError.OutOfRangeHexEscape)
        }
    }

    override fun unicode2unit(r: EscapeResult<Char>): EscapeResult<Char> {
        return r
    }
}

/** Unescape strategy for byte string literals. Equivalent to upstream `impl Unescape for [u8]`. */
private object ByteUnescape : UnescapeStrategy<Int> {
    override val zeroResult: EscapeResult<Int> = ok(0)

    override fun nonzeroByte2unit(b: NonZeroU8): Int {
        return b.get()
    }

    override fun char2unit(c: Char): EscapeResult<Int> {
        return char2byte(c)
    }

    override fun hex2unit(b: Int): EscapeResult<Int> {
        return ok(b)
    }

    override fun unicode2unit(r: EscapeResult<Char>): EscapeResult<Int> {
        return when (r) {
            is EscapeResult.Ok,
            is EscapeResult.Err,
            -> err(EscapeError.UnicodeEscapeInByte)
        }
    }
}

/** Unescape strategy for C string literals. Equivalent to upstream `impl Unescape for CStr`. */
private object CStrUnescape : UnescapeStrategy<MixedUnit> {
    override val zeroResult: EscapeResult<MixedUnit> = err(EscapeError.NulInCStr)

    override fun nonzeroByte2unit(b: NonZeroU8): MixedUnit {
        return MixedUnit.from(b)
    }

    override fun char2unit(c: Char): EscapeResult<MixedUnit> {
        return MixedUnit.tryFrom(c)
    }

    override fun hex2unit(b: Int): EscapeResult<MixedUnit> {
        return MixedUnit.tryFrom(b)
    }

    override fun unicode2unit(r: EscapeResult<Char>): EscapeResult<MixedUnit> {
        return when (r) {
            is EscapeResult.Ok -> char2unit(r.value)
            is EscapeResult.Err -> r
        }
    }
}

/** Enum of the different kinds of literal. */
enum class Mode {
    /** `'a'` */
    Char,

    /** `b'a'` */
    Byte,

    /** `"hello"` */
    Str,

    /** `r"hello"` */
    RawStr,

    /** `b"hello"` */
    ByteStr,

    /** `br"hello"` */
    RawByteStr,

    /** `c"hello"` */
    CStr,

    /** `cr"hello"` */
    RawCStr,
    ;

    fun inDoubleQuotes(): Boolean {
        return when (this) {
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
    }

    fun prefixNoraw(): String {
        return when (this) {
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
}

/**
 * Check a literal only for errors.
 *
 * Takes the contents of a literal, without quotes, and produces a sequence of
 * only errors, which are returned by invoking `errorCallback`.
 *
 * Does not produce any output other than errors.
 */
fun checkForErrors(
    src: String,
    mode: Mode,
    errorCallback: (ByteRange, EscapeError) -> Unit,
) {
    when (mode) {
        Mode.Char -> {
            val chars = CharCursor(src)
            when (val result = unescapeSingle(chars, CharUnescape)) {
                is EscapeResult.Err -> errorCallback(ByteRange(0, chars.byteIndex), result.error)
                is EscapeResult.Ok -> Unit
            }
        }
        Mode.Byte -> {
            val chars = CharCursor(src)
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

/**
 * Promote a Unicode scalar value to a [Char], reporting the appropriate error
 * for surrogate or out-of-range code points.
 */
private fun scalarValueToChar(value: Int): EscapeResult<Char> {
    return when {
        value > 0x10FFFF -> err(EscapeError.OutOfRangeUnicodeEscape)
        value in 0xD800..0xDFFF -> err(EscapeError.LoneSurrogateUnicodeEscape)
        value > Char.MAX_VALUE.code -> err(EscapeError.OutOfRangeUnicodeEscape)
        else -> ok(value.toChar())
    }
}

/** Helper that wraps a value in [EscapeResult.Ok], shortening callsite syntax. */
private fun <T> ok(value: T): EscapeResult<T> {
    return EscapeResult.Ok(value)
}

/** Helper that wraps an error in [EscapeResult.Err], shortening callsite syntax. */
private fun err(error: EscapeError): EscapeResult.Err {
    return EscapeResult.Err(error)
}

/**
 * Forward-only cursor over the chars of the source string, tracking both the
 * UTF-16 char index (used for reading the next char) and the UTF-8 byte index
 * (used for byte-range diagnostics that mirror upstream's `Range<usize>`).
 */
private class CharCursor(
    private val src: String,
) {
    private var index: Int = 0
    var byteIndex: Int = 0
        private set

    fun next(): Char? {
        if (index >= src.length) {
            return null
        }
        val c = src[index]
        index += 1
        byteIndex += utf8Len(c)
        return c
    }

    fun peek(): Char? {
        return if (index >= src.length) {
            null
        } else {
            src[index]
        }
    }
}

/** UTF-8 byte length of a single [Char], matching Rust's `char::len_utf8`. */
private fun utf8Len(c: Char): Int {
    return c.toString().encodeToByteArray().size
}
