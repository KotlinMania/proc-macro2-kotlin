// port-lint: source tests/comments.rs
package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private fun litOfOuterDocComment(tokens: TokenStream): Literal = litOfDocComment(tokens, false)

private fun litOfInnerDocComment(tokens: TokenStream): Literal = litOfDocComment(tokens, true)

private fun litOfDocComment(tokens: TokenStream, inner: Boolean): Literal {
    var iter = tokens.iterator()
    when (val token = iter.next()) {
        is TokenTree.Punct -> {
            assertEquals('#', token.value.asChar())
            assertEquals(Spacing.Alone, token.value.spacing())
        }
        else -> fail("wrong token $tokens")
    }
    if (inner) {
        when (val token = iter.next()) {
            is TokenTree.Punct -> {
                assertEquals('!', token.value.asChar())
                assertEquals(Spacing.Alone, token.value.spacing())
            }
            else -> fail("wrong token $tokens")
        }
    }
    iter =
        when (val token = iter.next()) {
            is TokenTree.Group -> {
                assertEquals(Delimiter.Bracket, token.value.delimiter())
                assertFalse(iter.hasNext(), "unexpected token $tokens")
                token.value.stream().iterator()
            }
            else -> fail("wrong token $tokens")
        }
    when (val token = iter.next()) {
        is TokenTree.Ident -> assertEquals("doc", token.value.toString())
        else -> fail("wrong token $tokens")
    }
    when (val token = iter.next()) {
        is TokenTree.Punct -> {
            assertEquals('=', token.value.asChar())
            assertEquals(Spacing.Alone, token.value.spacing())
        }
        else -> fail("wrong token $tokens")
    }
    return when (val token = iter.next()) {
        is TokenTree.Literal -> {
            assertFalse(iter.hasNext(), "unexpected token $tokens")
            token.value
        }
        else -> fail("wrong token $tokens")
    }
}

class CommentsTest {
    @Test
    fun closedImmediately() {
        val stream = TokenStream.fromString("/**/").getOrThrow()
        val tokens = stream.toList()
        assertTrue(tokens.isEmpty(), "not empty -- $tokens")
    }

    @Test
    fun incomplete() {
        assertTrue(TokenStream.fromString("/*/").isFailure())
    }

    @Test
    fun lit() {
        var stream = TokenStream.fromString("/// doc").getOrThrow()
        var lit = litOfOuterDocComment(stream)
        assertEquals("\" doc\"", lit.toString())

        stream = TokenStream.fromString("//! doc").getOrThrow()
        lit = litOfInnerDocComment(stream)
        assertEquals("\" doc\"", lit.toString())

        stream = TokenStream.fromString("/** doc */").getOrThrow()
        lit = litOfOuterDocComment(stream)
        assertEquals("\" doc \"", lit.toString())

        stream = TokenStream.fromString("/*! doc */").getOrThrow()
        lit = litOfInnerDocComment(stream)
        assertEquals("\" doc \"", lit.toString())
    }

    @Test
    fun carriageReturn() {
        var stream = TokenStream.fromString("///\r\n").getOrThrow()
        var lit = litOfOuterDocComment(stream)
        assertEquals("\"\"", lit.toString())

        stream = TokenStream.fromString("/**\r\n*/").getOrThrow()
        lit = litOfOuterDocComment(stream)
        assertEquals("\"\\r\\n\"", lit.toString())

        assertTrue(TokenStream.fromString("///\r").isFailure())
        assertTrue(TokenStream.fromString("///\r \n").isFailure())
        assertTrue(TokenStream.fromString("/**\r \n*/").isFailure())
    }
}
