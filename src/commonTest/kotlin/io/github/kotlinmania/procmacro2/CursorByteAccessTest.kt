package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CursorByteAccessTest {
    @Test
    fun byteViewsMatchExistingEncodingIncludingUnicodeAndMalformedUtf16() {
        val sources = listOf("", (0..127).map(Int::toChar).joinToString(""), "aé中😀z", "\uD800x\uDC00")
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
        val sources = listOf(
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
                fun fingerprints(stream: FallbackTokenStream): List<String> =
                    stream.iter().asSequence().map { token -> "${token::class.simpleName}:${(token.span().inner as WrapperSpan.Fallback).span}:$token" }.toList()
                assertEquals(fingerprints(first.getOrThrow()), fingerprints(second.getOrThrow()))
            }
        }
    }
}
