// port-lint: source tests/test_fmt.rs
package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals

class TestFmtTest {
    @Test
    fun testFmtGroup() {
        val ident = Ident.new("x", Span.callSite())
        val inner = TokenStream.fromTokenTrees(listOf(TokenTree.Ident(ident)))
        val parensEmpty = Group(Delimiter.Parenthesis, TokenStream.new())
        val parensNonempty = Group(Delimiter.Parenthesis, inner)
        val bracketsEmpty = Group(Delimiter.Bracket, TokenStream.new())
        val bracketsNonempty = Group(Delimiter.Bracket, inner)
        val bracesEmpty = Group(Delimiter.Brace, TokenStream.new())
        val bracesNonempty = Group(Delimiter.Brace, inner)
        val noneEmpty = Group(Delimiter.None, TokenStream.new())
        val noneNonempty = Group(Delimiter.None, inner)

        // Matches libproc_macro.
        assertEquals("()", parensEmpty.toString())
        assertEquals("(x)", parensNonempty.toString())
        assertEquals("[]", bracketsEmpty.toString())
        assertEquals("[x]", bracketsNonempty.toString())
        assertEquals("{ }", bracesEmpty.toString())
        assertEquals("{ x }", bracesNonempty.toString())
        assertEquals("", noneEmpty.toString())
        assertEquals("x", noneNonempty.toString())
    }
}
