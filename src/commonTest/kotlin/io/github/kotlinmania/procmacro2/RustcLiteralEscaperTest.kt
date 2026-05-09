// port-lint: ignore
// Focused coverage for the vendored literal escaper port.
package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RustcLiteralEscaperTest {
    @Test
    fun unescapesCharAndByteLiterals() {
        assertEquals('\n', unescapeChar("\\n").value())
        assertEquals(0x7F, unescapeByte("\\x7f").value())
        assertEquals(EscapeError.MoreThanOneChar, unescapeChar("ab").error())
    }

    @Test
    fun preservesUnicodeByteEscapePrecedence() {
        assertEquals(EscapeError.UnicodeEscapeInByte, unescapeByte("\\u{41}").error())
        assertEquals(EscapeError.UnclosedUnicodeEscape, unescapeByte("\\u{").error())
    }

    @Test
    fun unescapesStringContinuation() {
        val units = mutableListOf<Pair<ByteRange, EscapeResult<Char>>>()
        unescapeStr("a\\\n  b") { range, result ->
            units.add(range to result)
        }

        val expected: List<Pair<ByteRange, EscapeResult<Char>>> =
            listOf(
                ByteRange(0, 1) to EscapeResult.Ok('a'),
                ByteRange(5, 6) to EscapeResult.Ok('b'),
            )
        assertEquals(
            expected,
            units,
        )
    }

    @Test
    fun reportsRawAndCStringErrors() {
        val raw = mutableListOf<Pair<ByteRange, EscapeResult<NonZeroChar>>>()
        checkRawCStr("a\u0000") { range, result ->
            raw.add(range to result)
        }

        assertEquals(ByteRange(0, 1), raw[0].first)
        assertEquals('a', raw[0].second.value().get())
        assertEquals(ByteRange(1, 2), raw[1].first)
        assertEquals(EscapeError.NulInCStr, raw[1].second.error())
    }

    @Test
    fun separatesCStringCharsAndHighBytes() {
        val units = mutableListOf<EscapeResult<MixedUnit>>()
        unescapeCStr("A\\x80") { _, result ->
            units.add(result)
        }

        val char = assertIs<MixedUnit.Char>(units[0].value())
        assertEquals('A', char.value.get())
        val highByte = assertIs<MixedUnit.HighByte>(units[1].value())
        assertEquals(0x80, highByte.value.get())
        assertEquals(EscapeError.NulInCStr, unescapeCStrUnit("\\0").error())
    }

    private fun unescapeCStrUnit(src: String): EscapeResult<MixedUnit> {
        var unit: EscapeResult<MixedUnit> = EscapeResult.Err(EscapeError.ZeroChars)
        unescapeCStr(src) { _, result ->
            unit = result
        }
        return unit
    }

    private fun <T> EscapeResult<T>.value(): T {
        return when (this) {
            is EscapeResult.Ok -> value
            is EscapeResult.Err -> throw AssertionError("expected Ok, got $error")
        }
    }

    private fun EscapeResult<*>.error(): EscapeError {
        return when (this) {
            is EscapeResult.Ok -> throw AssertionError("expected Err, got $value")
            is EscapeResult.Err -> error
        }
    }
}
