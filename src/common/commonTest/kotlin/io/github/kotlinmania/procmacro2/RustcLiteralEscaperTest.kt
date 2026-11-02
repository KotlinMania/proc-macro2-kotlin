// port-lint: tests rustc_literal_escaper.rs
// Focused coverage for the vendored literal escaper port.
package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RustcLiteralEscaperTest {
    @Test
    fun unescapesCharAndByteLiterals() {
        assertEquals('\n'.code, unescapeChar("\\n").value())
        assertEquals(0x1F980, unescapeChar("\\u{1F980}").value())
        assertEquals(0x7F, unescapeByte("\\x7f").value())
        assertEquals(EscapeError.MoreThanOneChar, unescapeChar("ab").error())
        assertEquals(EscapeError.LoneSurrogateUnicodeEscape, unescapeChar("\\u{D800}").error())
        assertEquals(EscapeError.OutOfRangeUnicodeEscape, unescapeChar("\\u{110000}").error())
    }

    @Test
    fun preservesUnicodeByteEscapePrecedence() {
        assertEquals(EscapeError.UnicodeEscapeInByte, unescapeByte("\\u{41}").error())
        assertEquals(EscapeError.UnclosedUnicodeEscape, unescapeByte("\\u{").error())
    }

    @Test
    fun unescapesStringContinuationAndSupplementaryScalars() {
        val units = mutableListOf<Pair<ByteRange, EscapeResult<Int>>>()
        unescapeStr("a\\\n  b\\u{1F980}") { range, result ->
            units.add(range to result)
        }

        val expected: List<Pair<ByteRange, EscapeResult<Int>>> =
            listOf(
                ByteRange(0, 1) to EscapeResult.Ok('a'.code),
                ByteRange(5, 6) to EscapeResult.Ok('b'.code),
                ByteRange(6, 15) to EscapeResult.Ok(0x1F980),
            )
        assertEquals(
            expected,
            units,
        )

        val rawUnits = mutableListOf<Pair<ByteRange, EscapeResult<Int>>>()
        checkRawStr("🦀x") { range, result ->
            rawUnits.add(range to result)
        }
        val expectedRaw: List<Pair<ByteRange, EscapeResult<Int>>> =
            listOf(
                ByteRange(0, 4) to EscapeResult.Ok(0x1F980),
                ByteRange(4, 5) to EscapeResult.Ok('x'.code),
            )
        assertEquals(
            expectedRaw,
            rawUnits,
        )
    }

    @Test
    fun reportsRawAndCStringErrors() {
        val raw = mutableListOf<Pair<ByteRange, EscapeResult<NonZeroChar>>>()
        checkRawCStr("a\u0000🦀") { range, result ->
            raw.add(range to result)
        }

        assertEquals(ByteRange(0, 1), raw[0].first)
        assertEquals('a'.code, raw[0].second.value().get())
        assertEquals(ByteRange(1, 2), raw[1].first)
        assertEquals(EscapeError.NulInCStr, raw[1].second.error())
        assertEquals(ByteRange(2, 6), raw[2].first)
        assertEquals(0x1F980, raw[2].second.value().get())
    }

    @Test
    fun separatesCStringCharsAndHighBytes() {
        val units = mutableListOf<EscapeResult<MixedUnit>>()
        unescapeCStr("A\\x80\\u{1F980}") { _, result ->
            units.add(result)
        }

        val char = assertIs<MixedUnit.Char>(units[0].value())
        assertEquals('A'.code, char.value.get())
        val highByte = assertIs<MixedUnit.HighByte>(units[1].value())
        assertEquals(0x80, highByte.value.get())
        val crab = assertIs<MixedUnit.Char>(units[2].value())
        assertEquals(0x1F980, crab.value.get())
        assertEquals(EscapeError.NulInCStr, unescapeCStrUnit("\\0").error())
    }

    private fun unescapeCStrUnit(src: String): EscapeResult<MixedUnit> {
        var unit: EscapeResult<MixedUnit> = EscapeResult.Err(EscapeError.ZeroChars)
        unescapeCStr(src) { _, result ->
            unit = result
        }
        return unit
    }

    private fun <T> EscapeResult<T>.value(): T =
        when (this) {
            is EscapeResult.Ok -> value
            is EscapeResult.Err -> throw AssertionError("expected Ok, got $error")
        }

    private fun EscapeResult<*>.error(): EscapeError =
        when (this) {
            is EscapeResult.Ok -> throw AssertionError("expected Err, got $value")
            is EscapeResult.Err -> error
        }
}
