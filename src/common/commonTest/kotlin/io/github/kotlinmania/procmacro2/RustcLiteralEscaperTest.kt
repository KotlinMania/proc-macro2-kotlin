package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.rustcliteralescaper.ByteRange
import io.github.kotlinmania.rustcliteralescaper.CharEscapeOutcome
import io.github.kotlinmania.rustcliteralescaper.CharEscapeStep
import io.github.kotlinmania.rustcliteralescaper.EscapeError
import io.github.kotlinmania.rustcliteralescaper.MixedUnitEscapeOutcome
import io.github.kotlinmania.rustcliteralescaper.NonZeroCharEscapeStep
import io.github.kotlinmania.rustcliteralescaper.collectCheckRawCStr
import io.github.kotlinmania.rustcliteralescaper.collectCheckRawStr
import io.github.kotlinmania.rustcliteralescaper.collectUnescapeStr
import io.github.kotlinmania.rustcliteralescaper.unescapeByte
import io.github.kotlinmania.rustcliteralescaper.unescapeCStr
import io.github.kotlinmania.rustcliteralescaper.unescapeChar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RustcLiteralEscaperTest {
    @Test
    fun unescapesCharAndByteLiterals() {
        assertEquals('\n'.code, unescapeChar("\\n").unwrapCodePoint())
        assertEquals(0x1F980, unescapeChar("\\u{1F980}").unwrapCodePoint())
        assertEquals(0x7Fu.toUByte(), unescapeByte("\\x7f").unwrap())
        assertEquals(EscapeError.MoreThanOneChar, unescapeChar("ab").unwrapErr())
        assertEquals(EscapeError.LoneSurrogateUnicodeEscape, unescapeChar("\\u{D800}").unwrapErr())
        assertEquals(EscapeError.OutOfRangeUnicodeEscape, unescapeChar("\\u{110000}").unwrapErr())
    }

    @Test
    fun preservesUnicodeByteEscapePrecedence() {
        assertEquals(EscapeError.UnicodeEscapeInByte, unescapeByte("\\u{41}").unwrapErr())
        assertEquals(EscapeError.UnclosedUnicodeEscape, unescapeByte("\\u{").unwrapErr())
    }

    @Test
    fun unescapesStringContinuationAndSupplementaryScalars() {
        val units = collectUnescapeStr("a\\\n  b\\u{1F980}")
        val expected =
            listOf(
                CharEscapeStep(ByteRange(0, 1), CharEscapeOutcome.okCodePoint('a'.code)),
                CharEscapeStep(ByteRange(5, 6), CharEscapeOutcome.okCodePoint('b'.code)),
                CharEscapeStep(ByteRange(6, 15), CharEscapeOutcome.okCodePoint(0x1F980)),
            )
        assertEquals(expected, units)

        val rawUnits = collectCheckRawStr("🦀x")
        val expectedRaw =
            listOf(
                CharEscapeStep(ByteRange(0, 4), CharEscapeOutcome.okCodePoint(0x1F980)),
                CharEscapeStep(ByteRange(4, 5), CharEscapeOutcome.okCodePoint('x'.code)),
            )
        assertEquals(expectedRaw, rawUnits)
    }

    @Test
    fun reportsRawAndCStringErrors() {
        val raw: List<NonZeroCharEscapeStep> = collectCheckRawCStr("a\u0000🦀")

        assertEquals(ByteRange(0, 1), raw[0].range)
        assertEquals('a'.code, raw[0].outcome.unwrap().get())
        assertEquals(ByteRange(1, 2), raw[1].range)
        assertEquals(EscapeError.NulInCStr, raw[1].outcome.unwrapErr())
        assertEquals(ByteRange(2, 6), raw[2].range)
        assertEquals(0x1F980, raw[2].outcome.unwrap().get())
    }

    @Test
    fun separatesCStringCharsAndHighBytes() {
        val units = mutableListOf<MixedUnitEscapeOutcome>()
        unescapeCStr("A\\x80\\u{1F980}") { _, result ->
            units.add(result)
        }

        val charUnit = units[0].unwrap()
        assertTrue(charUnit.isChar)
        assertEquals('A'.code, assertNotNull(charUnit.charOrNull()).get())
        val highByte = units[1].unwrap()
        assertTrue(highByte.isHighByte)
        assertEquals(0x80u.toUByte(), assertNotNull(highByte.highByteOrNull()).get())
        val crab = units[2].unwrap()
        assertTrue(crab.isChar)
        assertEquals(0x1F980, assertNotNull(crab.charOrNull()).get())
        assertEquals(EscapeError.NulInCStr, unescapeCStrUnit("\\0").unwrapErr())
    }

    private fun unescapeCStrUnit(src: String): MixedUnitEscapeOutcome {
        var unit: MixedUnitEscapeOutcome = MixedUnitEscapeOutcome.err(EscapeError.ZeroChars)
        unescapeCStr(src) { _, result ->
            unit = result
        }
        return unit
    }
}
