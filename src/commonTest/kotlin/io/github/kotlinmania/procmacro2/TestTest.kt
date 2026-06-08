// port-lint: source tests/test.rs
// Port of the upstream proc-macro2 core test suite (tests/test.rs).
//
// Mapping notes vs. the Rust original:
//   * Rust `Span::call_site()` -> `Span.callSite()`, `Ident::new` -> `Ident.new`, etc.
//   * `#[should_panic]` constructors map to `assertFailsWith<IllegalArgumentException>`;
//     the Kotlin fallback validates ident/raw-ident strings the same way upstream does.
//   * Rust ranges are half-open; `Literal.subspan` takes an inclusive Kotlin `IntRange`,
//     so Rust `a..b` is written here as `a..b-1`.
//   * The port has no feature gates: `span_locations` and the semver-exempt value
//     accessors (`strValue`/`byteStrValue`/`cstrValue`, `start`/`end`/`file`) are always
//     present, so the `#[cfg(span_locations)]` / `#[cfg(procmacro2_semver_exempt)]`
//     branches are ported unconditionally.
//   * `Literal.c_string(&CStr)` takes the nul-terminated CStr upstream; the Kotlin
//     `Literal.cString(ByteArray)` takes the content bytes without the terminator.
//   * Rust's `{:?}`/`{:#?}` Debug formatting has no Kotlin analogue (the port exposes
//     Display via `toString`, not a structured Debug). `test_debug_ident` and
//     `test_debug_tokenstream` are therefore @Ignore'd below.
package io.github.kotlinmania.procmacro2

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun parse(src: String): TokenStream = TokenStream.fromString(src).getOrThrow()

private fun parseOk(src: String) =
    assertTrue(TokenStream.fromString(src).isSuccess(), "expected parse to succeed: $src")

private fun parseErr(src: String) =
    assertTrue(TokenStream.fromString(src).isFailure(), "expected parse to fail: $src")

private fun tokenCount(src: String): Int = parse(src).count()

class TestTest {
    @Test
    fun idents() {
        assertEquals("String", Ident.new("String", Span.callSite()).toString())
        assertEquals("fn", Ident.new("fn", Span.callSite()).toString())
        assertEquals("_", Ident.new("_", Span.callSite()).toString())
    }

    @Test
    fun rawIdents() {
        assertEquals("r#String", Ident.newRaw("String", Span.callSite()).toString())
        assertEquals("r#fn", Ident.newRaw("fn", Span.callSite()).toString())
    }

    @Test
    fun identRawUnderscore() {
        assertFailsWith<IllegalArgumentException> { Ident.newRaw("_", Span.callSite()) }
    }

    @Test
    fun identRawReserved() {
        assertFailsWith<IllegalArgumentException> { Ident.newRaw("super", Span.callSite()) }
    }

    @Test
    fun identEmpty() {
        assertFailsWith<IllegalArgumentException> { Ident.new("", Span.callSite()) }
    }

    @Test
    fun identNumber() {
        assertFailsWith<IllegalArgumentException> { Ident.new("255", Span.callSite()) }
    }

    @Test
    fun identInvalid() {
        assertFailsWith<IllegalArgumentException> { Ident.new("a#", Span.callSite()) }
    }

    @Test
    fun rawIdentEmpty() {
        assertFailsWith<IllegalArgumentException> { Ident.new("r#", Span.callSite()) }
    }

    @Test
    fun rawIdentNumber() {
        assertFailsWith<IllegalArgumentException> { Ident.new("r#255", Span.callSite()) }
    }

    @Test
    fun rawIdentInvalid() {
        assertFailsWith<IllegalArgumentException> { Ident.new("r#a#", Span.callSite()) }
    }

    @Test
    fun lifetimeEmpty() {
        assertFailsWith<IllegalArgumentException> { Ident.new("'", Span.callSite()) }
    }

    @Test
    fun lifetimeNumber() {
        assertFailsWith<IllegalArgumentException> { Ident.new("'255", Span.callSite()) }
    }

    @Test
    fun lifetimeInvalid() {
        assertFailsWith<IllegalArgumentException> { Ident.new("'a#", Span.callSite()) }
    }

    @Test
    fun literalString() {
        fun assert(literal: Literal, expected: String) = assertEquals(expected.trim(), literal.toString())

        assert(Literal.string(""), "  \"\"  ")
        assert(Literal.string("aA"), "  \"aA\"  ")
        assert(Literal.string("\t"), "  \"\\t\"  ")
        assert(Literal.string("❤"), "  \"❤\"  ")
        assert(Literal.string("'"), "  \"'\"  ")
        assert(Literal.string("\""), "  \"\\\"\"  ")
        assert(Literal.string("\u0000"), "  \"\\0\"  ")
        assert(Literal.string("\u0001"), "  \"\\u{1}\"  ")
        assert(
            Literal.string("a" + "\u0000" + "0b" + "\u0000" + "7c" + "\u0000" + "8d" + "\u0000" + "e" + "\u0000"),
            "  \"a\\x000b\\x007c\\08d\\0e\\0\"  ",
        )

        parseOk("\"\\\r\n    x\"")
        parseErr("\"\\\r\n  \rx\"")
    }

    @Test
    fun literalRawString() {
        parseOk("r\"\r\n\"")

        fun rawStringLiteralWithHashes(n: Int): String =
            buildString {
                append('r')
                repeat(n) { append('#') }
                append('"')
                append('"')
                repeat(n) { append('#') }
            }

        parseOk(rawStringLiteralWithHashes(255))
        // https://github.com/rust-lang/rust/pull/95251
        parseErr(rawStringLiteralWithHashes(256))
    }

    @Test
    fun literalStringValue() {
        for (string in listOf("", "...", "...\t...", "...\\...", "...\u0000...", "...\u0001...")) {
            assertEquals(string, Literal.string(string).strValue().getOrThrow())
            assertEquals(string, Literal.fromString("r\"$string\"").getOrThrow().strValue().getOrThrow())
            assertEquals(string, Literal.fromString("r##\"$string\"##").getOrThrow().strValue().getOrThrow())
        }
    }

    @Test
    fun literalByteCharacter() {
        fun assert(literal: Literal, expected: String) = assertEquals(expected.trim(), literal.toString())

        assert(Literal.byteCharacter('a'.code.toUByte()), "  b'a'  ")
        assert(Literal.byteCharacter(0u), "  b'\\0'  ")
        assert(Literal.byteCharacter('\t'.code.toUByte()), "  b'\\t'  ")
        assert(Literal.byteCharacter('\n'.code.toUByte()), "  b'\\n'  ")
        assert(Literal.byteCharacter('\r'.code.toUByte()), "  b'\\r'  ")
        assert(Literal.byteCharacter('\''.code.toUByte()), "  b'\\''  ")
        assert(Literal.byteCharacter('\\'.code.toUByte()), "  b'\\\\'  ")
        assert(Literal.byteCharacter(0x1fu), "  b'\\x1F'  ")
        assert(Literal.byteCharacter('"'.code.toUByte()), "  b'\"'  ")
    }

    @Test
    fun literalByteString() {
        fun assert(literal: Literal, expected: String) = assertEquals(expected.trim(), literal.toString())

        fun bytes(vararg b: Int): ByteArray = ByteArray(b.size) { b[it].toByte() }

        assert(Literal.byteString(bytes()), "  b\"\"  ")
        assert(Literal.byteString(bytes(0)), "  b\"\\0\"  ")
        assert(Literal.byteString(bytes('\t'.code)), "  b\"\\t\"  ")
        assert(Literal.byteString(bytes('\n'.code)), "  b\"\\n\"  ")
        assert(Literal.byteString(bytes('\r'.code)), "  b\"\\r\"  ")
        assert(Literal.byteString(bytes('"'.code)), "  b\"\\\"\"  ")
        assert(Literal.byteString(bytes('\\'.code)), "  b\"\\\\\"  ")
        assert(Literal.byteString(bytes(0x1f)), "  b\"\\x1F\"  ")
        assert(Literal.byteString(bytes('\''.code)), "  b\"'\"  ")
        assert(
            Literal.byteString(
                bytes('a'.code, 0, '0'.code, 'b'.code, 0, '7'.code, 'c'.code, 0, '8'.code, 'd'.code, 0, 'e'.code, 0),
            ),
            "  b\"a\\x000b\\x007c\\08d\\0e\\0\"  ",
        )

        parseOk("b\"\\\r\n    x\"")
        parseErr("b\"\\\r\n  \rx\"")
        parseErr("b\"\\\r\n  \u00A0x\"")
        parseErr("br\"\u00A0\"")
    }

    @Test
    fun literalByteStringValue() {
        val byteStrings =
            listOf(
                byteArrayOf(),
                "...".encodeToByteArray(),
                "...\t...".encodeToByteArray(),
                "...\\...".encodeToByteArray(),
                "...\u0000...".encodeToByteArray(),
                byteArrayOf('.'.code.toByte(), '.'.code.toByte(), '.'.code.toByte(), 0xF0.toByte(), '.'.code.toByte(), '.'.code.toByte(), '.'.code.toByte()),
            )
        for (bytestr in byteStrings) {
            assertTrue(bytestr.contentEquals(Literal.byteString(bytestr).byteStrValue().getOrThrow()))
        }
    }

    @Test
    fun literalCString() {
        fun assert(literal: Literal, expected: String) = assertEquals(expected.trim(), literal.toString())

        fun cstr(s: String): ByteArray = s.encodeToByteArray()

        assert(Literal.cString(byteArrayOf()), "  c\"\"  ")
        assert(Literal.cString(cstr("aA")), "  c\"aA\"  ")
        assert(Literal.cString(cstr("aA")), "  c\"aA\"  ")
        assert(Literal.cString(cstr("\t")), "  c\"\\t\"  ")
        assert(Literal.cString(cstr("❤")), "  c\"❤\"  ")
        assert(Literal.cString(cstr("'")), "  c\"'\"  ")
        assert(Literal.cString(cstr("\"")), "  c\"\\\"\"  ")
        assert(
            Literal.cString(byteArrayOf(0x7F, 0xFF.toByte(), 0xFE.toByte(), 0xCC.toByte(), 0xB3.toByte())),
            "  c\"\\u{7f}\\xFF\\xFE\\u{333}\"  ",
        )

        val strings =
            """
            c"hello\x80我叫\u{1F980}"
            cr"\"
            cr##"Hello "world"!"##
            c"\t\n\r\"\\"
            """.trimIndent()

        val tokens = parse(strings).iterator()
        val expected =
            listOf(
                """c"hello\x80我叫\u{1F980}"""",
                """cr"\"""",
                """cr##"Hello "world"!"##""",
                """c"\t\n\r\"\\"""",
            )
        for (exp in expected) {
            val tt = tokens.next()
            val literal = assertIs<TokenTree.Literal>(tt)
            assertEquals(exp, literal.value.toString())
        }
        assertFalse(tokens.hasNext(), "unexpected trailing token")

        for (invalid in listOf("""c"\0"""", """c"\x00"""", """c"\u{0}"""", "c\"\u0000\"")) {
            parseErr(invalid)
        }
    }

    @Test
    fun literalCStringValue() {
        val cstrings =
            listOf(
                "",
                "...",
                "...\t...",
                "...\\...",
                "...\u0001...",
            )
        for (cstr in cstrings) {
            val expected = cstr.encodeToByteArray() + byteArrayOf(0)
            assertTrue(expected.contentEquals(Literal.cString(cstr.encodeToByteArray()).cstrValue().getOrThrow()))
        }
    }

    @Test
    fun literalCharacter() {
        fun assert(literal: Literal, expected: String) = assertEquals(expected.trim(), literal.toString())

        assert(Literal.character('a'), "  'a'  ")
        assert(Literal.character('\t'), "  '\\t'  ")
        assert(Literal.character('❤'), "  '❤'  ")
        assert(Literal.character('\''), "  '\\''  ")
        assert(Literal.character('"'), "  '\"'  ")
        assert(Literal.character('\u0000'), "  '\\0'  ")
        assert(Literal.character('\u0001'), "  '\\u{1}'  ")
    }

    @Test
    fun literalInteger() {
        fun assert(literal: Literal, expected: String) = assertEquals(expected, literal.toString())

        assert(Literal.u8Suffixed(10u), "10u8")
        assert(Literal.u16Suffixed(10u), "10u16")
        assert(Literal.u32Suffixed(10u), "10u32")
        assert(Literal.u64Suffixed(10uL), "10u64")
        assert(Literal.u128Suffixed(10uL), "10u128")
        assert(Literal.usizeSuffixed(10uL), "10usize")

        assert(Literal.i8Suffixed(10), "10i8")
        assert(Literal.i16Suffixed(10), "10i16")
        assert(Literal.i32Suffixed(10), "10i32")
        assert(Literal.i64Suffixed(10L), "10i64")
        assert(Literal.i128Suffixed(10L), "10i128")
        assert(Literal.isizeSuffixed(10L), "10isize")

        assert(Literal.u8Unsuffixed(10u), "10")
        assert(Literal.u16Unsuffixed(10u), "10")
        assert(Literal.u32Unsuffixed(10u), "10")
        assert(Literal.u64Unsuffixed(10uL), "10")
        assert(Literal.u128Unsuffixed(10uL), "10")
        assert(Literal.usizeUnsuffixed(10uL), "10")

        assert(Literal.i8Unsuffixed(10), "10")
        assert(Literal.i16Unsuffixed(10), "10")
        assert(Literal.i32Unsuffixed(10), "10")
        assert(Literal.i64Unsuffixed(10L), "10")
        assert(Literal.i128Unsuffixed(10L), "10")
        assert(Literal.isizeUnsuffixed(10L), "10")

        assert(Literal.i32Suffixed(-10), "-10i32")
        assert(Literal.i32Unsuffixed(-10), "-10")
    }

    @Test
    fun literalFloat() {
        fun assert(literal: Literal, expected: String) = assertEquals(expected, literal.toString())

        assert(Literal.f32Suffixed(10.0f), "10f32")
        assert(Literal.f32Suffixed(-10.0f), "-10f32")
        assert(Literal.f64Suffixed(10.0), "10f64")
        assert(Literal.f64Suffixed(-10.0), "-10f64")

        assert(Literal.f32Unsuffixed(10.0f), "10.0")
        assert(Literal.f32Unsuffixed(-10.0f), "-10.0")
        assert(Literal.f64Unsuffixed(10.0), "10.0")
        assert(Literal.f64Unsuffixed(-10.0), "-10.0")

        assert(
            Literal.f64Unsuffixed(1e100),
            "10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000.0",
        )
    }

    @Test
    fun literalSuffix() {
        assertEquals(1, tokenCount("999u256"))
        assertEquals(3, tokenCount("999r#u256"))
        assertEquals(1, tokenCount("1."))
        assertEquals(3, tokenCount("1.f32"))
        assertEquals(1, tokenCount("1.0_0"))
        assertEquals(3, tokenCount("1._0"))
        assertEquals(3, tokenCount("1._m"))
        assertEquals(1, tokenCount("\"\"s"))
        assertEquals(1, tokenCount("r\"\"r"))
        assertEquals(1, tokenCount("r#\"\"#r"))
        assertEquals(1, tokenCount("b\"\"b"))
        assertEquals(1, tokenCount("br\"\"br"))
        assertEquals(1, tokenCount("br#\"\"#br"))
        assertEquals(1, tokenCount("c\"\"c"))
        assertEquals(1, tokenCount("cr\"\"cr"))
        assertEquals(1, tokenCount("cr#\"\"#cr"))
        assertEquals(1, tokenCount("'c'c"))
        assertEquals(1, tokenCount("b'b'b"))
        assertEquals(1, tokenCount("0E"))
        assertEquals(1, tokenCount("0o0A"))
        assertEquals(4, tokenCount("0E--0"))
        assertEquals(1, tokenCount("0.0ECMA"))
    }

    @Test
    fun literalIterNegative() {
        val negativeLiteral = Literal.i32Suffixed(-3)
        val tokens = TokenStream.fromTokenTree(TokenTree.Literal(negativeLiteral))
        val iter = tokens.iterator()

        val first = assertIs<TokenTree.Punct>(iter.next())
        assertEquals('-', first.value.asChar())
        assertEquals(Spacing.Alone, first.value.spacing())

        val second = assertIs<TokenTree.Literal>(iter.next())
        assertEquals("3i32", second.value.toString())

        assertFalse(iter.hasNext())
    }

    @Test
    fun literalParse() {
        assertTrue(Literal.fromString("1").isSuccess())
        assertTrue(Literal.fromString("-1").isSuccess())
        assertTrue(Literal.fromString("-1u12").isSuccess())
        assertTrue(Literal.fromString("1.0").isSuccess())
        assertTrue(Literal.fromString("-1.0").isSuccess())
        assertTrue(Literal.fromString("-1.0f12").isSuccess())
        assertTrue(Literal.fromString("'a'").isSuccess())
        assertTrue(Literal.fromString("\"\n\"").isSuccess())
        assertTrue(Literal.fromString("0 1").isFailure())
        assertTrue(Literal.fromString(" 0").isFailure())
        assertTrue(Literal.fromString("0 ").isFailure())
        assertTrue(Literal.fromString("/* comment */0").isFailure())
        assertTrue(Literal.fromString("0/* comment */").isFailure())
        assertTrue(Literal.fromString("0// comment").isFailure())
        assertTrue(Literal.fromString("- 1").isFailure())
        assertTrue(Literal.fromString("- 1.0").isFailure())
        assertTrue(Literal.fromString("-\"\"").isFailure())
    }

    @Test
    fun literalSpan() {
        val positive = Literal.fromString("0.1").getOrThrow()
        val negative = Literal.fromString("-0.1").getOrThrow()
        val subspan = positive.subspan(1..1) // Rust 1..2 (half-open)

        assertEquals(0, positive.span().start().column)
        assertEquals(3, positive.span().end().column)
        assertEquals(0, negative.span().start().column)
        assertEquals(4, negative.span().end().column)
        assertEquals(".", subspan!!.sourceText())

        assertNull(positive.subspan(1..3)) // Rust 1..4 (half-open)
    }

    @Test
    fun sourceText() {
        val input = "    𓀕 a z    "
        val tokens = parse(input).iterator()

        val first = tokens.next()
        assertEquals("𓀕", first.span().sourceText())

        val second = tokens.next()
        val third = tokens.next()
        assertEquals("z", third.span().sourceText())
        assertEquals("a", second.span().sourceText())
    }

    @Test
    fun lifetimes() {
        val tokens = parse("'a 'static 'struct 'r#gen 'r#prefix#lifetime").iterator()

        fun nextPunct(expected: Char, spacing: Spacing) {
            val p = assertIs<TokenTree.Punct>(tokens.next())
            assertEquals(expected, p.value.asChar())
            assertEquals(spacing, p.value.spacing())
        }

        fun nextIdent(expected: String) {
            val i = assertIs<TokenTree.Ident>(tokens.next())
            assertEquals(expected, i.value.toString())
        }

        nextPunct('\'', Spacing.Joint)
        nextIdent("a")
        nextPunct('\'', Spacing.Joint)
        nextIdent("static")
        nextPunct('\'', Spacing.Joint)
        nextIdent("struct")
        nextPunct('\'', Spacing.Joint)
        nextIdent("r#gen")
        nextPunct('\'', Spacing.Joint)
        nextIdent("r#prefix")
        nextPunct('#', Spacing.Alone)
        nextIdent("lifetime")

        parseErr("' a")
        parseErr("' r#gen")
        parseErr("' prefix#lifetime")
        parseErr("'prefix#lifetime")
        parseErr("'aa'bb")
        parseErr("'r#gen'a")
    }

    @Test
    fun roundtrip() {
        fun roundtrip(p: String) {
            val s = parse(p).toString()
            val s2 = parse(s).toString()
            assertEquals(s, s2)
        }
        roundtrip("a")
        roundtrip("<<")
        roundtrip("<<=")
        roundtrip(
            """
            1
            1.0
            1f32
            2f64
            1usize
            4isize
            4e10
            1_000
            1_0i32
            8u8
            9
            0
            0xffffffffffffffffffffffffffffffff
            1x
            1u80
            1f320
            """,
        )
        roundtrip("'a")
        roundtrip("'_")
        roundtrip("'static")
        roundtrip("'\\u{10__FFFF}'")
        roundtrip("\"\\u{10_F0FF__}foo\\u{1_0_0_0__}\"")
    }

    @Test
    fun fail() {
        parseErr("' static")
        parseErr("r#1")
        parseErr("r#_")
        parseErr("\"\\u{0000000}\"") // overlong unicode escape (rust allows at most 6 hex digits)
        parseErr("\"\\u{999999}\"") // outside of valid range of char
        parseErr("\"\\u{_0}\"") // leading underscore
        parseErr("\"\\u{}\"") // empty
        parseErr("b\"\r\"") // bare carriage return in byte string
        parseErr("r\"\r\"") // bare carriage return in raw string
        parseErr("\"\\\r  \"") // backslash carriage return
        parseErr("'aa'aa")
        parseErr("br##\"\"#")
        parseErr("cr##\"\"#")
        parseErr("\"\\\n\u0085\r\"")
    }

    @Test
    fun spanTest() {
        checkSpans(
            "/// This is a document comment\ntesting 123\n{\n  testing 234\n}",
            arrayOf(
                intArrayOf(1, 0, 1, 30), // #
                intArrayOf(1, 0, 1, 30), // [ ... ]
                intArrayOf(1, 0, 1, 30), // doc
                intArrayOf(1, 0, 1, 30), // =
                intArrayOf(1, 0, 1, 30), // "This is..."
                intArrayOf(2, 0, 2, 7), // testing
                intArrayOf(2, 8, 2, 11), // 123
                intArrayOf(3, 0, 5, 1), // { ... }
                intArrayOf(4, 2, 4, 9), // testing
                intArrayOf(4, 10, 4, 13), // 234
            ),
        )
    }

    @Test
    fun defaultSpan() {
        val start = Span.callSite().start()
        assertEquals(1, start.line)
        assertEquals(0, start.column)
        val end = Span.callSite().end()
        assertEquals(1, end.line)
        assertEquals(0, end.column)
        assertEquals("<unspecified>", Span.callSite().file())
        assertNull(Span.callSite().localFile())
    }

    @Test
    fun spanJoin() {
        val source1 = parse("aaa\nbbb").toList()
        val source2 = parse("ccc\nddd").toList()

        assertTrue(source1[0].span().file() != source2[0].span().file())
        assertEquals(source1[0].span().file(), source1[1].span().file())

        val joined1 = assertNotNull(source1[0].span().join(source1[1].span()))
        val joined2 = source1[0].span().join(source2[0].span())
        assertNull(joined2)

        val start = joined1.start()
        val end = joined1.end()
        assertEquals(1, start.line)
        assertEquals(0, start.column)
        assertEquals(2, end.line)
        assertEquals(3, end.column)

        assertEquals(source1[0].span().file(), joined1.file())
    }

    @Test
    fun noPanic() {
        val s = "b'" + "\u0086" + "  " + "\u0000\u0000\u0000" + "^" + "\""
        assertTrue(TokenStream.fromString(s).isFailure())
    }

    @Test
    fun punctBeforeComment() {
        val tts = parse("~// comment").iterator()
        val p = assertIs<TokenTree.Punct>(tts.next())
        assertEquals('~', p.value.asChar())
        assertEquals(Spacing.Alone, p.value.spacing())
    }

    @Test
    fun jointLastToken() {
        // This test verifies that we match the behavior of libproc_macro *not* in
        // the range nightly-2020-09-06 through nightly-2020-09-10, in which this
        // behavior was temporarily broken.
        // See https://github.com/rust-lang/rust/issues/76399
        val jointPunct = Punct(':', Spacing.Joint)
        val stream = TokenStream.fromTokenTree(TokenTree.Punct(jointPunct))
        val punct = assertIs<TokenTree.Punct>(stream.iterator().next())
        assertEquals(Spacing.Joint, punct.value.spacing())
    }

    @Test
    fun rawIdentifier() {
        val tts = parse("r#dyn").iterator()
        val raw = assertIs<TokenTree.Ident>(tts.next())
        assertEquals("r#dyn", raw.value.toString())
        assertFalse(tts.hasNext())
    }

    @Test
    fun testDisplayIdent() {
        // proc-macro2's Display ignores format flags; the Kotlin port has no
        // format-spec mechanism, so the padding variants collapse to plain toString.
        assertEquals("proc_macro", Ident.new("proc_macro", Span.callSite()).toString())
        assertEquals("r#proc_macro", Ident.newRaw("proc_macro", Span.callSite()).toString())
    }

    @Ignore // No Kotlin analogue for Rust's `{:?}` structured Debug; the port exposes Display only.
    @Test
    fun testDebugIdent() = Unit

    @Test
    fun testDisplayTokenstream() {
        assertEquals("[a + 1]", parse("[a + 1]").toString())
    }

    @Ignore // No Kotlin analogue for Rust's `{:#?}` pretty Debug; the port exposes Display only.
    @Test
    fun testDebugTokenstream() = Unit

    @Test
    fun defaultTokenstreamIsEmpty() {
        assertTrue(TokenStream.new().isEmpty())
    }

    @Test
    fun tokenstreamSizeHint() {
        val tokens = parse("a b (c d) e")
        assertEquals(4 to 4, tokens.iterator().sizeHint())
    }

    @Test
    fun tupleIndexing() {
        val tokens = parse("tuple.0.0").iterator()
        assertEquals("tuple", tokens.next().toString())
        assertEquals(".", tokens.next().toString())
        assertEquals("0.0", tokens.next().toString())
        assertFalse(tokens.hasNext())
    }

    @Test
    fun nonAsciiTokens() {
        checkSpans("// abc", arrayOf())
        checkSpans("// ábc", arrayOf())
        checkSpans("// abc x", arrayOf())
        checkSpans("// ábc x", arrayOf())
        checkSpans("/* abc */ x", arrayOf(intArrayOf(1, 10, 1, 11)))
        checkSpans("/* ábc */ x", arrayOf(intArrayOf(1, 10, 1, 11)))
        checkSpans("/* ab\nc */ x", arrayOf(intArrayOf(2, 5, 2, 6)))
        checkSpans("/* áb\nc */ x", arrayOf(intArrayOf(2, 5, 2, 6)))
        checkSpans("/*** abc */ x", arrayOf(intArrayOf(1, 12, 1, 13)))
        checkSpans("/*** ábc */ x", arrayOf(intArrayOf(1, 12, 1, 13)))
        checkSpans("\"abc\"", arrayOf(intArrayOf(1, 0, 1, 5)))
        checkSpans("\"ábc\"", arrayOf(intArrayOf(1, 0, 1, 5)))
        checkSpans("r#\"abc\"#", arrayOf(intArrayOf(1, 0, 1, 8)))
        checkSpans("r#\"ábc\"#", arrayOf(intArrayOf(1, 0, 1, 8)))
        checkSpans("r#\"a\nc\"#", arrayOf(intArrayOf(1, 0, 2, 3)))
        checkSpans("r#\"á\nc\"#", arrayOf(intArrayOf(1, 0, 2, 3)))
        checkSpans("'a'", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("'á'", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("//! abc", arrayOf(intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7)))
        checkSpans("//! ábc", arrayOf(intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7)))
        checkSpans("//! abc\n", arrayOf(intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7)))
        checkSpans("//! ábc\n", arrayOf(intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7), intArrayOf(1, 0, 1, 7)))
        checkSpans("/*! abc */", arrayOf(intArrayOf(1, 0, 1, 10), intArrayOf(1, 0, 1, 10), intArrayOf(1, 0, 1, 10)))
        checkSpans("/*! ábc */", arrayOf(intArrayOf(1, 0, 1, 10), intArrayOf(1, 0, 1, 10), intArrayOf(1, 0, 1, 10)))
        checkSpans("/*! a\nc */", arrayOf(intArrayOf(1, 0, 2, 4), intArrayOf(1, 0, 2, 4), intArrayOf(1, 0, 2, 4)))
        checkSpans("/*! á\nc */", arrayOf(intArrayOf(1, 0, 2, 4), intArrayOf(1, 0, 2, 4), intArrayOf(1, 0, 2, 4)))
        checkSpans("abc", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("ábc", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("ábć", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("abc// foo", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("ábc// foo", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("ábć// foo", arrayOf(intArrayOf(1, 0, 1, 3)))
        checkSpans("b\"a\\\n c\"", arrayOf(intArrayOf(1, 0, 2, 3)))
    }

    @Test
    fun whitespace() {
        // space, horizontal tab, vertical tab, form feed, carriage return, line
        // feed, non-breaking space, left-to-right mark, right-to-left mark
        val variousSpaces = " \t\u000B\u000C\r\n\u00A0\u200E\u200F"
        assertEquals(0, parse(variousSpaces).count())

        val loneCarriageReturns = " \r \r\r\n "
        parseOk(loneCarriageReturns)
    }

    @Test
    fun byteOrderMark() {
        val string = "\uFEFFfoo"
        val ident = assertIs<TokenTree.Ident>(parse(string).iterator().next())
        assertEquals("foo", ident.value.toString())

        parseErr("foo\uFEFF")
    }

    @Test
    fun testInvalidateCurrentThreadSpans() {
        // Upstream relies on per-thread span state so each test starts from offset 1.
        // The Kotlin port keeps this state process-wide (documented in Lib.kt), so
        // earlier tests in the same process leave the offset counter advanced;
        // invalidate first to establish the same baseline upstream's test assumes.
        invalidateCurrentThreadSpanData()
        assertEquals("bytes(1..2)", createSpan().toString())
        assertEquals("bytes(3..4)", createSpan().toString())

        invalidateCurrentThreadSpanData()

        // Test that span offsets have been reset after the invalidation call.
        assertEquals("bytes(1..2)", createSpan().toString())
    }

    @Test
    fun testUseSpanAfterInvalidation() {
        val span = createSpan()
        invalidateCurrentThreadSpanData()
        assertFailsWith<IllegalStateException> { span.sourceText() }
    }
}

private fun createSpan(): Span {
    val tts = parse("1")
    val literal = assertIs<TokenTree.Literal>(tts.iterator().next())
    return literal.value.span()
}

private fun checkSpans(p: String, lines: Array<IntArray>) {
    val ts = parse(p)
    val remaining = lines.toMutableList()
    checkSpansInternal(ts, remaining)
    assertTrue(remaining.isEmpty(), "leftover ranges: ${remaining.size}")
}

private fun checkSpansInternal(ts: TokenStream, lines: MutableList<IntArray>) {
    for (tt in ts) {
        if (lines.isEmpty()) return
        val expected = lines.removeAt(0)
        val (sline, scol, eline, ecol) = expected

        val start = tt.span().start()
        assertEquals(sline, start.line, "sline did not match for $tt")
        assertEquals(scol, start.column, "scol did not match for $tt")

        val end = tt.span().end()
        assertEquals(eline, end.line, "eline did not match for $tt")
        assertEquals(ecol, end.column, "ecol did not match for $tt")

        if (tt is TokenTree.Group) {
            checkSpansInternal(tt.value.stream(), lines)
        }
    }
}

private operator fun IntArray.component1(): Int = this[0]

private operator fun IntArray.component2(): Int = this[1]

private operator fun IntArray.component3(): Int = this[2]

private operator fun IntArray.component4(): Int = this[3]
