package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals

class SpanScalarColumnTest {
    @Test
    fun sourceColumnsCountUnicodeScalarsRatherThanUtf16Units() {
        val tokens = TokenStream.fromString("𐐀 é\n𐐀x +").getOrThrow().toList()
        val expected = listOf(1 to 0, 1 to 2, 2 to 0, 2 to 3)
        val ends = listOf(1 to 1, 1 to 3, 2 to 2, 2 to 4)
        assertEquals(expected, tokens.map { it.span().start().let { p -> p.line to p.column } })
        assertEquals(ends, tokens.map { it.span().end().let { p -> p.line to p.column } })
        assertEquals(listOf(0..1, 3..3, 5..7, 9..9), tokens.map { it.span().sourceUtf16Range() })
        assertEquals(null, Span.callSite().sourceUtf16Range())
    }

    @Test
    fun commentsStringsAndGroupsRetainScalarLocations() {
        val group = (TokenStream.fromString("/*𐐀*/ (\"𐐀\" x)").getOrThrow().single() as TokenTree.Group).value
        assertEquals(LineColumn(1, 6), group.spanOpen().start())
        assertEquals(LineColumn(1, 13), group.spanClose().end())
        val items = group.stream().toList()
        assertEquals(LineColumn(1, 10), items[0].span().end())
        assertEquals(LineColumn(1, 11), items[1].span().start())
    }
}
