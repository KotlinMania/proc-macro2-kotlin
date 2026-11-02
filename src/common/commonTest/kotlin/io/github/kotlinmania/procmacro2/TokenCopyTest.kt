package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.procmacro.TokenStreamParseOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import io.github.kotlinmania.procmacro.Delimiter as ProcMacroDelimiter
import io.github.kotlinmania.procmacro.Group as ProcMacroGroup
import io.github.kotlinmania.procmacro.Literal as ProcMacroLiteral
import io.github.kotlinmania.procmacro.Span as ProcMacroSpan
import io.github.kotlinmania.procmacro.TokenStream as ProcMacroTokenStream
import io.github.kotlinmania.procmacro.TokenTree as ProcMacroTokenTree

class TokenCopyTest {
    @Test
    fun spanCopiesRetainBackendAndAreIndependentOfReplacement() {
        val fallbackStream = TokenStream.fromString("first second").getOrThrow()
        val fallback = fallbackStream.last().span()
        val compiler = Span.newCompiler(ProcMacroSpan.mixedSite())
        for (original in listOf(fallback, compiler)) {
            val before = original.inner
            val copy = original.clone()
            assertNotSame(original, copy)
            assertEquals(before, copy.inner)
            copy.replaceFrom(Span.callSite())
            assertEquals(before, original.inner)
            val next = original.clone()
            original.replaceFrom(Span.callSite())
            assertEquals(before, next.inner)
        }
    }

    private fun spanData(span: Span): String =
        when (val inner = span.inner) {
            is WrapperSpan.Fallback -> "fallback:${inner.span}:${span.sourceText()}"
            is WrapperSpan.Compiler ->
                "compiler:${inner.span.byteRange()}:${inner.span.sourceText()}"
        }

    private fun tokenData(tree: TokenTree): String =
        when (tree) {
            is TokenTree.Group ->
                "group:${tree.value.delimiter()}:${spanData(tree.span())}:${spanData(tree.value.spanOpen())}:${spanData(tree.value.spanClose())}" +
                    tree.value
                        .stream()
                        .joinToString(prefix = "[", postfix = "]", transform = ::tokenData)
            is TokenTree.Ident -> "ident:$tree:${spanData(tree.span())}"
            is TokenTree.Punct -> "punct:$tree:${tree.value.spacing()}:${spanData(tree.span())}"
            is TokenTree.Literal -> "literal:$tree:${spanData(tree.span())}"
        }

    private fun mutate(tree: TokenTree) {
        val replacement =
            when (tree.span().inner) {
                is WrapperSpan.Fallback -> Span.newFallback(FallbackSpan.callSite())
                is WrapperSpan.Compiler -> Span.newCompiler(ProcMacroSpan.callSite())
            }
        tree.setSpan(replacement)
        if (tree is TokenTree.Group) tree.value.stream().forEach(::mutate)
    }

    private fun verifyCopies(stream: TokenStream) {
        val before = stream.clone().map(::tokenData)
        val copies = stream.clone().toList()
        assertEquals(before, copies.map(::tokenData))
        copies.forEach(::mutate)
        assertEquals(before, stream.clone().map(::tokenData))
        for (tree in stream.clone()) {
            val original = tokenData(tree)
            val copied = tree.cloneToken()
            assertNotSame(tree, copied)
            assertEquals(original, tokenData(copied))
            mutate(copied)
            assertEquals(original, tokenData(tree))
        }
    }

    @Test
    fun fallbackCopiesRetainAllTokenKindsAndNestedData() {
        val sample = "r#type::call(0xff_u16, r##\"raw /* text */\"##, b\"ab\", 'x', 1.25e2f32)"
        verifyCopies(TokenStream.fromString(sample).getOrThrow())
        val nested = TokenStream.fromString("(r#type [\"nested\"])").getOrThrow()
        verifyCopies(TokenStream.fromTokenTree(TokenTree.Group(Group(Delimiter.None, nested))))
    }

    @Test
    fun compilerCopiesUseExistingFacadeAndRetainDelimiterSpans() {
        val outcome = ProcMacroTokenStream.fromString("type += 0xff; (\"é\" [nested])")
        val parsed = assertIs<TokenStreamParseOutcome.Ok>(outcome).value
        verifyCopies(TokenStream(WrapperTokenStream.Compiler(parsed)))
        val group = ProcMacroGroup.new(ProcMacroDelimiter.NONE, parsed)
        val groupStream = ProcMacroTokenStream.fromTokenTree(ProcMacroTokenTree.Group(group))
        verifyCopies(TokenStream(WrapperTokenStream.Compiler(groupStream)))
        val wrapped = Group.newCompiler(group)
        val before = wrapped.stream().map(::tokenData)
        wrapped.stream().forEach(::mutate)
        assertEquals(before, wrapped.stream().map(::tokenData))
    }

    @Test
    fun copyingNegativeLiteralDoesNotSplitOrReparseIt() {
        val original = Literal.i32Unsuffixed(-42)
        val copied = original.cloneToken()
        assertEquals("-42", copied.toString())
        assertEquals(tokenData(TokenTree.Literal(original)), tokenData(TokenTree.Literal(copied)))
        val compiler = Literal.newCompiler(ProcMacroLiteral.i32Unsuffixed(-42))
        assertEquals("-42", compiler.cloneToken().toString())
    }

    @Test
    fun fallbackIdentifierCopyRetainsRawNameAndIndependentSpan() {
        val sourceSpan = FallbackSpan(10, 16)
        val originals =
            listOf(
                FallbackIdent.newUnchecked("name", sourceSpan),
                FallbackIdent.newRawUnchecked("type", sourceSpan),
            )
        for (original in originals) {
            val copied = original.clone()
            assertNotSame(original, copied)
            assertEquals(original.toString(), copied.toString())
            assertEquals(sourceSpan, copied.span())
            copied.setSpan(FallbackSpan.callSite())
            assertEquals(sourceSpan, original.span())
        }
    }

    @Test
    fun existingFallbackLiteralCopyRetainsRepresentationAndIndependentSpan() {
        val sourceSpan = FallbackSpan(20, 30)
        for (representation in listOf("-42", "0xff_u16", "r##\"raw /* text */\"##", "1.25e2f32")) {
            val original = FallbackLiteral(representation, sourceSpan)
            val copied = original.clone()
            assertNotSame(original, copied)
            assertEquals(representation, copied.toString())
            assertEquals(sourceSpan, copied.span())
            copied.setSpan(FallbackSpan.callSite())
            assertEquals(sourceSpan, original.span())
        }
    }
}
