package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorByteAccessTest {
    @Test
    fun byteViewsMatchExistingEncodingIncludingUnicodeAndMalformedUtf16() {
        val malformed = charArrayOf(0xD800.toChar(), 'x', 0xDC00.toChar()).concatToString()
        val sources = listOf("", (0..127).map(Int::toChar).joinToString(""), "aé中😀z", malformed)
        for (source in sources) {
            val cursor = Cursor(source, 7)
            for (offset in 0..source.length) {
                val suffix = cursor.advance(offset)
                val expected = source.drop(offset).encodeToByteArray().map { it.toInt() and 0xff }
                assertEquals(expected, suffix.bytes())
                assertEquals(expected.firstOrNull(), suffix.firstByte())
                assertEquals(7 + offset, suffix.off)
                assertEquals(Cursor(source.drop(offset), 7 + offset), suffix)
            }
        }
    }

    @Test
    fun cachedViewsDoNotChangeValueSemanticsOrOutliveTheirSource() {
        val original = Cursor("abc", 2)
        val expected = original.copy()
        val view = original.bytes()
        assertEquals('a'.code, original.firstByte())
        assertEquals(expected, original)
        assertEquals(expected.hashCode(), original.hashCode())
        assertEquals(listOf(98, 99), original.advance(1).bytes())
        assertEquals(listOf(97, 98, 99), view)
        assertFailsWith<IndexOutOfBoundsException> { view[-1] }
        assertFailsWith<IndexOutOfBoundsException> { view[3] }
    }

    @Test
    fun warmedAndFreshCursorsProduceIdenticalTokenTreesAndSpans() {
        val sources =
            listOf(
                "/// docs\npub const X: u32 = 0xFF_u32; /* outer /* nested */ end */ fn f() { r#\"{}\"#; b\"abc\"; }",
                "// comment\nfn café() { \"é😀\"; r#\"中\"#; }",
                "fn f() { [ (",
            )
        for (source in sources) {
            val warmed = Cursor(source, 1)
            warmed.bytes()
            val first = tokenStream(warmed)
            val second = tokenStream(Cursor(source, 1))
            assertEquals(first.isSuccess, second.isSuccess)
            if (first.isSuccess) {
                assertEquals(first.getOrThrow().toString(), second.getOrThrow().toString())
                fun fingerprints(stream: FallbackTokenStream): List<String> {
                    val result = mutableListOf<String>()
                    for (token in stream.iter()) {
                        val span = (token.span().inner as WrapperSpan.Fallback).span
                        result.add("${token::class.simpleName}:$span:$token")
                    }
                    return result
                }
                assertEquals(fingerprints(first.getOrThrow()), fingerprints(second.getOrThrow()))
            }
        }
    }

    @Test
    fun advancingPastUnicodeDocCommentPunctuationRestoresAsciiClassification() {
        val commentLine = "/// Summary — “smart quotes” in doc comment"
        val body = "\npub const MASK: u64 = 0xFFFF_FFFF;\nfn helper(v: u32) -> u32 { v + 1 }\n"
        val source = commentLine + body
        val lastNonAsciiIndex = source.indexOfLast { it.code >= 0x80 }
        assertTrue(lastNonAsciiIndex >= 0)

        var cursor = Cursor(source, 0)
        assertFalse(cursor.isAscii())

        val onLastNonAscii = cursor.advance(lastNonAsciiIndex)
        assertFalse(onLastNonAscii.isAscii())

        val afterNonAscii = onLastNonAscii.advance(1)
        assertTrue(afterNonAscii.isAscii())
        assertEquals(source.substring(lastNonAsciiIndex + 1), afterNonAscii.rest)

        cursor = afterNonAscii
        while (!cursor.isEmpty()) {
            assertTrue(cursor.isAscii())
            cursor = cursor.advance(1)
        }
        assertTrue(cursor.isAscii())
    }

    @Test
    fun surrogateEdgesAndDestructuringPreserveCursorSemantics() {
        val trickySurrogates =
            listOf(
                "😀😀",
                charArrayOf(0xD800.toChar(), 0xD83D.toChar(), 0xDE00.toChar(), 0xDC00.toChar()).concatToString(),
                "prefix — “quote” 😀 suffix",
            )
        for (source in trickySurrogates) {
            val root = Cursor(source, 10)
            for (offset in 0..source.length) {
                val advanced = root.advance(offset)
                val fresh = Cursor(source.substring(offset), 10 + offset)
                val (destructuredRest, destructuredOff) = advanced
                assertEquals(fresh.rest, destructuredRest)
                assertEquals(fresh.off, destructuredOff)
                assertEquals(fresh, advanced)
                assertEquals(fresh.hashCode(), advanced.hashCode())
                assertEquals(fresh.toString(), advanced.toString())
                assertEquals(fresh, advanced.copy())
                assertEquals(fresh.chars().toList(), advanced.chars().toList())
                assertEquals(fresh.charIndices().toList(), advanced.charIndices().toList())
                val expectedBytes = source.substring(offset).encodeToByteArray().map { it.toInt() and 0xff }
                assertEquals(expectedBytes, advanced.bytes())
                assertEquals(expectedBytes.firstOrNull(), advanced.firstByte())
            }
        }
    }

    @Test
    fun largeModuleWithUnicodeTypographyTokenizesAndSlicesLinearly() {
        val builder = StringBuilder(90_000)
        builder.append("//! Numeric helpers — “fast path” overview\n")
        for (index in 0 until 2_400) {
            if (index == 1_200) {
                builder.append("/// Mid-file note — “checked arithmetic” invariants\n")
            }
            builder.append("pub const C_")
            builder.append(index)
            builder.append(": u64 = 0x")
            builder.append(index.toString(16))
            builder.append("_u64;\n")
        }
        builder.append("/// Trailing note — “end of module” marker\n")
        val largeSource = builder.toString()
        assertTrue(largeSource.length > 68_000)

        var probe = Cursor(largeSource, 0)
        var steps = 0
        while (!probe.isEmpty() && steps < 2_000) {
            val view = probe.bytes()
            assertEquals(probe.firstByte(), view.firstOrNull())
            assertFailsWith<IndexOutOfBoundsException> { view[-1] }
            assertFailsWith<IndexOutOfBoundsException> { view[view.size] }
            probe = probe.advance(31)
            steps += 1
        }

        val parsed = TokenStream.fromString(largeSource)
        assertTrue(parsed.isSuccess())
        val topLevelTokens = parsed.getOrThrow().toList()
        assertTrue(topLevelTokens.size > 8_000)
    }
}
