package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.procmacro.bridge.BridgeClientState
import io.github.kotlinmania.rustcliteralescaper.EscapeError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompilerBridgeParityTest {
    @Test
    fun outsideMacroSessionUsesFallbackAndRejectsCompilerUnwrap() {
        unforce()
        assertFalse(BridgeClientState.isAvailable())

        val empty = TokenStream.new()
        assertIs<WrapperTokenStream.Fallback>(empty.inner)

        val span = Span.callSite()
        assertIs<WrapperSpan.Fallback>(span.inner)

        assertFailsWith<IllegalStateException> {
            span.unwrap()
        }
        assertFailsWith<IllegalStateException> {
            empty.intoProcMacro()
        }

        invalidateCurrentThreadSpans()

        val crabChar = Literal.characterCodePoint(0x1F980)
        assertEquals("'🦀'", crabChar.toString())

        val parsedStr = Literal.fromString("\"\\u{1F980}\"").getOrThrow()
        assertEquals("🦀", parsedStr.strValue().getOrThrow())

        val parsedCstr = Literal.fromString("c\"hi\\x80\\u{1F980}\"").getOrThrow()
        assertContentEquals(
            byteArrayOf(
                'h'.code.toByte(),
                'i'.code.toByte(),
                0x80.toByte(),
                0xF0.toByte(),
                0x9F.toByte(),
                0xA6.toByte(),
                0x80.toByte(),
                0,
            ),
            parsedCstr.cstrValue().getOrThrow(),
        )

        val badStr = Literal.fromStrUnchecked("\"\\z\"").strValue()
        assertTrue(badStr.isFailure())
        assertEquals(ConversionErrorKind.FailedToUnescape(EscapeError.InvalidEscape), badStr.errorKind)

        val wrongKind = Literal.i32Unsuffixed(5).strValue()
        assertTrue(wrongKind.isFailure())
        assertEquals(ConversionErrorKind.InvalidLiteralKind, wrongKind.errorKind)

        val invalidTokens = TokenStream.fromString("unclosed (")
        assertTrue(invalidTokens.isFailure())
        val lexErr = assertNotNull(invalidTokens.lexError)
        assertTrue(lexErr.toString().isNotEmpty())
        assertNotNull(lexErr.span())
    }

    @Test
    fun activeBridgeSessionRoutesAllTypesThroughCompilerBackend() {
        unforce()
        val rawInput =
            io.github.kotlinmania.procmacro.TokenStream
                .fromString("alpha + 1")
                .getOrThrow()

        val rawOutput =
            BridgeClientState.runBangMacro("compilerParity", rawInput) { incoming ->
                assertTrue(BridgeClientState.isAvailable())

                val wrappedIncoming = TokenStream.fromProcMacro(incoming)
                assertIs<WrapperTokenStream.Compiler>(wrappedIncoming.inner)
                assertEquals("alpha + 1", wrappedIncoming.toString())

                val empty = TokenStream.new()
                assertIs<WrapperTokenStream.Compiler>(empty.inner)
                assertTrue(empty.isEmpty())

                val callSpan = Span.callSite()
                val mixedSpan = Span.mixedSite()
                val defSpan = Span.defSite()
                assertIs<WrapperSpan.Compiler>(callSpan.inner)
                assertIs<WrapperSpan.Compiler>(mixedSpan.inner)
                assertIs<WrapperSpan.Compiler>(defSpan.inner)

                val roundTrippedSpan = Span.fromProcMacro(callSpan.unwrap())
                assertEquals(callSpan, roundTrippedSpan)
                assertEquals(callSpan.unwrap(), callSpan.unstable())
                assertEquals(0, callSpan.start().column)
                assertEquals(0, callSpan.end().column)
                assertEquals("<token stream>", callSpan.file())
                assertNull(callSpan.localFile())
                assertNotNull(callSpan.byteRange())

                val ident = Ident.new("beta", callSpan)
                val rawIdent = Ident.newRaw("type", mixedSpan)
                assertIs<WrapperIdent.Compiler>(ident.inner)
                assertIs<WrapperIdent.Compiler>(rawIdent.inner)
                assertTrue(ident.equals("beta"))
                assertTrue(rawIdent.equals("r#type"))
                ident.setSpan(defSpan)
                assertEquals(defSpan, ident.span())

                val groupInner = TokenStream.fromTokenTree(TokenTree.Ident(ident))
                val group = Group(Delimiter.Brace, groupInner)
                assertIs<WrapperGroup.Compiler>(group.inner)
                assertEquals("{ beta }", group.toString())
                assertNotNull(group.delimSpan().open())
                assertNotNull(group.delimSpan().close())
                assertNotNull(group.delimSpan().join())
                group.setSpan(mixedSpan)
                assertEquals(mixedSpan, group.span())

                val colons =
                    TokenStream.fromTokenTrees(
                        listOf(
                            TokenTree.Punct(Punct(':', Spacing.Joint, callSpan)),
                            TokenTree.Punct(Punct(':', Spacing.Alone, callSpan)),
                        ),
                    )
                assertEquals("::", colons.toString())

                val rustTokens = TokenStream.fromString("r#\"raw\"# b\"xy\" c\"cz\" 99u64").getOrThrow()
                assertIs<WrapperTokenStream.Compiler>(rustTokens.inner)
                assertFalse(rustTokens.isEmpty())

                val badStream = TokenStream.fromString("(")
                assertTrue(badStream.isFailure())
                assertNotNull(badStream.lexError)

                val deferred = TokenStream.new()
                deferred.extendTokenTrees(
                    listOf(
                        TokenTree.Ident(rawIdent),
                        TokenTree.Group(group),
                    ),
                )
                deferred.extendTokenStreams(listOf(colons))
                val iter = deferred.iterator()
                assertEquals(4 to 4, iter.sizeHint())
                assertTrue(iter.toString().startsWith("TokenStream ["))
                assertEquals(4, iter.asSequence().count())

                assertFailsWith<IllegalStateException> {
                    invalidateCurrentThreadSpans()
                }

                try {
                    force()
                    val forcedStream = TokenStream.new()
                    assertIs<WrapperTokenStream.Fallback>(forcedStream.inner)
                    assertTrue(forcedStream.intoProcMacro().isEmpty())
                    val forcedSpan = Span.callSite()
                    assertIs<WrapperSpan.Fallback>(forcedSpan.inner)
                    invalidateCurrentThreadSpans()

                    assertFailsWith<IllegalStateException> {
                        ident.setSpan(forcedSpan)
                    }
                } finally {
                    unforce()
                }

                assertIs<WrapperTokenStream.Compiler>(TokenStream.new().inner)
                deferred.intoProcMacro()
            }

        assertFalse(BridgeClientState.isAvailable())
        assertEquals("r#type { beta } ::", rawOutput.toString())
    }

    @Test
    fun allThirtyTwoLiteralFactoriesPreserveCompilerSpellings() {
        unforce()
        val emptyInput =
            io.github.kotlinmania.procmacro.TokenStream
                .new()

        val rendered =
            BridgeClientState.runBangMacro("literalParity", emptyInput) { _ ->
                val literals =
                    listOf(
                        Literal.u8Suffixed(1u),
                        Literal.u16Suffixed(2u),
                        Literal.u32Suffixed(3u),
                        Literal.u64Suffixed(4uL),
                        Literal.u128Suffixed(5uL),
                        Literal.usizeSuffixed(6uL),
                        Literal.i8Suffixed(-1),
                        Literal.i16Suffixed(-2),
                        Literal.i32Suffixed(-3),
                        Literal.i64Suffixed(-4L),
                        Literal.i128Suffixed(-5L),
                        Literal.isizeSuffixed(-6L),
                        Literal.f32Suffixed(1.5f),
                        Literal.f64Suffixed(2.5),
                        Literal.u8Unsuffixed(7u),
                        Literal.u16Unsuffixed(8u),
                        Literal.u32Unsuffixed(9u),
                        Literal.u64Unsuffixed(10uL),
                        Literal.u128Unsuffixed(11uL),
                        Literal.usizeUnsuffixed(12uL),
                        Literal.i8Unsuffixed(13),
                        Literal.i16Unsuffixed(14),
                        Literal.i32Unsuffixed(15),
                        Literal.i64Unsuffixed(16L),
                        Literal.i128Unsuffixed(17L),
                        Literal.isizeUnsuffixed(18L),
                        Literal.f32Unsuffixed(3.5f),
                        Literal.f64Unsuffixed(4.0),
                        Literal.string("hi"),
                        Literal.character('z'),
                        Literal.characterCodePoint(0x1F980),
                        Literal.byteCharacter('A'.code.toUByte()),
                        Literal.byteString(byteArrayOf('a'.code.toByte(), 'b'.code.toByte())),
                        Literal.cString(byteArrayOf('o'.code.toByte(), 'k'.code.toByte())),
                        Literal.fromString("0xff_u32").getOrThrow(),
                        Literal.fromStrUnchecked("r#\"verbatim\"#"),
                    )

                for (lit in literals) {
                    assertIs<WrapperLiteral.Compiler>(lit.inner)
                }

                TokenStream.fromTokenTrees(literals.map(TokenTree::Literal)).intoProcMacro()
            }

        val text = rendered.toString()
        assertTrue(text.contains("1u8"))
        assertTrue(text.contains("6usize"))
        assertTrue(text.contains("-3i32"))
        assertTrue(text.contains("15"))
        assertFalse(text.contains("\"15\""))
        assertTrue(text.contains("\"hi\""))
        assertTrue(text.contains("'z'"))
        assertTrue(text.contains("'🦀'"))
        assertTrue(text.contains("b'A'"))
        assertTrue(text.contains("b\"ab\""))
        assertTrue(text.contains("c\"ok\""))
        assertTrue(text.contains("0xff_u32"))
        assertTrue(text.contains("r#\"verbatim\"#"))
    }
}
