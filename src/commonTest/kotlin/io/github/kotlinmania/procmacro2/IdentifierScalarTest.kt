package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentifierScalarTest {
    @Test
    fun constructorsAndTokenizerAcceptTheSameSupplementaryIdentifiers() {
        // Deseret letter; mathematical digit is XID_Continue but not XID_Start.
        for (name in listOf("\uD801\uDC00", "x\uD835\uDFCE", "\uD801\uDC00\u0301", "_\uD801\uDC00")) {
            val span = Span.callSite()
            assertEquals(name, Ident.new(name, span).toString())
            assertEquals("r#$name", Ident.newRaw(name, span).toString())
            for (text in listOf(name, "r#$name")) {
                val tokens = TokenStream.fromString(text).getOrThrow().toList()
                assertEquals(1, tokens.size)
                assertEquals(text, assertIs<TokenTree.Ident>(tokens.single()).toString())
            }
        }
    }

    @Test
    fun supplementarySymbolsAndLeadingDigitsAreRejected() {
        for (name in listOf("\uD83D\uDE00", "x\uD83D\uDE00", "\uD835\uDFCE", "r#\uD83D\uDE00")) {
            assertFailsWith<IllegalArgumentException>(name) { Ident.new(name, Span.callSite()) }
            assertTrue(TokenStream.fromString(name).isFailure(), name)
        }
    }

    @Test
    fun unpairedSurrogatesAreNotRustScalars() {
        for (name in listOf("\uD800", "\uDC00", "x\uD800", "x\uDC00")) {
            assertFailsWith<IllegalArgumentException> { Ident.new(name, Span.callSite()) }
            assertTrue(TokenStream.fromString(name).isFailure())
        }
    }
}
