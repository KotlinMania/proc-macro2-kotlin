// port-lint: source wrapper.rs
package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.procmacro.bridge.BridgeClientState
import io.github.kotlinmania.procmacro.Delimiter as ProcmacroDelimiter
import io.github.kotlinmania.procmacro.Group as ProcmacroGroup
import io.github.kotlinmania.procmacro.Ident as ProcmacroIdent
import io.github.kotlinmania.procmacro.LexError as ProcmacroLexError
import io.github.kotlinmania.procmacro.Literal as ProcmacroLiteral
import io.github.kotlinmania.procmacro.Spacing as ProcmacroSpacing
import io.github.kotlinmania.procmacro.Span as ProcmacroSpan
import io.github.kotlinmania.procmacro.TokenStream as ProcmacroTokenStream
import io.github.kotlinmania.procmacro.TokenTree as ProcmacroTokenTree

internal sealed class WrapperTokenStream {
    class Compiler(
        val stream: ProcmacroTokenStream,
    ) : WrapperTokenStream()

    class Fallback(
        val stream: FallbackTokenStream,
    ) : WrapperTokenStream()
}

internal sealed class WrapperSpan {
    class Compiler(
        val span: ProcmacroSpan,
    ) : WrapperSpan()

    class Fallback(
        val span: FallbackSpan,
    ) : WrapperSpan()
}

internal sealed class WrapperGroup {
    class Compiler(
        val group: ProcmacroGroup,
    ) : WrapperGroup()

    class Fallback(
        val group: FallbackGroup,
    ) : WrapperGroup()
}

internal sealed class WrapperIdent {
    class Compiler(
        val ident: ProcmacroIdent,
    ) : WrapperIdent()

    class Fallback(
        val ident: FallbackIdent,
    ) : WrapperIdent()
}

internal sealed class WrapperLiteral {
    class Compiler(
        val literal: ProcmacroLiteral,
    ) : WrapperLiteral()

    class Fallback(
        val literal: FallbackLiteral,
    ) : WrapperLiteral()
}

internal sealed class WrapperLexError {
    class Compiler(
        val error: ProcmacroLexError,
    ) : WrapperLexError()

    class Fallback(
        val error: FallbackLexError,
    ) : WrapperLexError()
}

internal fun useCompiler(): Boolean = BridgeClientState.isAvailable()

internal fun WrapperTokenStream.isEmpty(): Boolean =
    when (this) {
        is WrapperTokenStream.Compiler -> stream.isEmpty()
        is WrapperTokenStream.Fallback -> stream.isEmpty()
    }

internal fun WrapperTokenStream.toString_(): String =
    when (this) {
        is WrapperTokenStream.Compiler -> stream.toString()
        is WrapperTokenStream.Fallback -> stream.toString()
    }

internal fun WrapperTokenStream.clone_(): WrapperTokenStream =
    when (this) {
        is WrapperTokenStream.Compiler -> {
            val trees = stream.toList()
            val out = ProcmacroTokenStream.new()
            out.extendTokenTrees(trees)
            WrapperTokenStream.Compiler(out)
        }
        is WrapperTokenStream.Fallback -> WrapperTokenStream.Fallback(stream.clone())
    }

internal fun WrapperTokenStream.extendTokenTrees(trees: Iterable<TokenTree>) {
    when (this) {
        is WrapperTokenStream.Compiler -> {
            val converted = trees.map { it.toCompilerTree() }
            stream.extendTokenTrees(converted)
        }
        is WrapperTokenStream.Fallback -> stream.extendTokenTrees(trees)
    }
}

internal fun WrapperTokenStream.extendTokenStreams(streams: Iterable<WrapperTokenStream>) {
    when (this) {
        is WrapperTokenStream.Compiler -> {
            val converted =
                streams.map {
                    when (it) {
                        is WrapperTokenStream.Compiler -> it.stream
                        is WrapperTokenStream.Fallback -> {
                            val trees =
                                it.stream
                                    .iter()
                                    .asSequence()
                                    .toList()
                            val out = ProcmacroTokenStream.new()
                            out.extendTokenTrees(trees.map { tree -> tree.toCompilerTree() })
                            out
                        }
                    }
                }
            for (s in converted) stream.extendTokenStreams(listOf(s))
        }
        is WrapperTokenStream.Fallback -> stream.extendTokenStreams(streams.map { it.asFallbackStream() })
    }
}

internal fun WrapperTokenStream.iter(): Sequence<WrapperTokenTree> =
    when (this) {
        is WrapperTokenStream.Compiler ->
            stream.asSequence().map { it.toWrapperTree() }
        is WrapperTokenStream.Fallback ->
            stream.iter().asSequence().map { it.toWrapperTree() }
    }

internal fun WrapperTokenStream.intoIterW(): WrapperTokenStreamIntoIter =
    when (this) {
        is WrapperTokenStream.Compiler ->
            WrapperTokenStreamIntoIter.Compiler(stream.iterator())
        is WrapperTokenStream.Fallback ->
            WrapperTokenStreamIntoIter.Fallback(stream.intoIter())
    }

internal sealed class WrapperTokenStreamIntoIter {
    class Compiler(
        val iter: Iterator<ProcmacroTokenTree>,
    ) : WrapperTokenStreamIntoIter() {
        override fun hasNext(): Boolean = iter.hasNext()

        override fun next(): WrapperTokenTree = iter.next().toWrapperTree()

        override fun sizeHint(): Pair<Int, Int?> = Pair(0, null)
    }

    class Fallback(
        val iter: RcVecIntoIter<TokenTree>,
    ) : WrapperTokenStreamIntoIter() {
        override fun hasNext(): Boolean = iter.hasNext()

        override fun next(): WrapperTokenTree = iter.next().toWrapperTree()

        override fun sizeHint(): Pair<Int, Int?> = iter.sizeHint()
    }

    abstract fun hasNext(): Boolean

    abstract fun next(): WrapperTokenTree

    abstract fun sizeHint(): Pair<Int, Int?>
}

internal fun WrapperTokenStream.asFallbackStream(): FallbackTokenStream =
    when (this) {
        is WrapperTokenStream.Fallback -> stream
        is WrapperTokenStream.Compiler -> {
            val stream = FallbackTokenStream.new()
            stream.extendTokenTrees(
                this
                    .iter()
                    .asSequence()
                    .map { it.asFallbackTree() }
                    .toList(),
            )
            stream
        }
    }

internal fun WrapperTokenStream.toTokenStreamEqualityItems(): List<*> =
    when (this) {
        is WrapperTokenStream.Compiler -> stream.toList()
        is WrapperTokenStream.Fallback -> stream.iter().asSequence().toList()
    }

internal fun WrapperSpan.span_(): Span = Span(this)

internal fun WrapperSpan.resolvedAt(other: WrapperSpan): WrapperSpan =
    when (this) {
        is WrapperSpan.Compiler -> WrapperSpan.Compiler(span.resolvedAt(other.toCompilerSpan()))
        is WrapperSpan.Fallback -> WrapperSpan.Fallback(span.resolvedAt(other.toFallbackSpan()))
    }

internal fun WrapperSpan.locatedAt(other: WrapperSpan): WrapperSpan =
    when (this) {
        is WrapperSpan.Compiler -> WrapperSpan.Compiler(span.locatedAt(other.toCompilerSpan()))
        is WrapperSpan.Fallback -> WrapperSpan.Fallback(span.locatedAt(other.toFallbackSpan()))
    }

internal fun WrapperSpan.byteRange(): IntRange =
    when (this) {
        is WrapperSpan.Compiler -> span.byteRange()
        is WrapperSpan.Fallback -> span.byteRange()
    }

internal fun WrapperSpan.start(): LineColumn =
    when (this) {
        is WrapperSpan.Compiler -> LineColumn(span.line(), span.column())
        is WrapperSpan.Fallback -> span.start()
    }

internal fun WrapperSpan.end(): LineColumn =
    when (this) {
        is WrapperSpan.Compiler -> {
            LineColumn(span.end().line(), span.end().column())
        }
        is WrapperSpan.Fallback -> span.end()
    }

internal fun WrapperSpan.file(): String =
    when (this) {
        is WrapperSpan.Compiler -> span.file()
        is WrapperSpan.Fallback -> span.file()
    }

internal fun WrapperSpan.localFile(): String? =
    when (this) {
        is WrapperSpan.Compiler -> span.localFile()
        is WrapperSpan.Fallback -> span.localFile()
    }

internal fun WrapperSpan.join(other: WrapperSpan): WrapperSpan? =
    when (this) {
        is WrapperSpan.Compiler -> span.join(other.toCompilerSpan())?.let { WrapperSpan.Compiler(it) }
        is WrapperSpan.Fallback -> span.join(other.toFallbackSpan())?.let { WrapperSpan.Fallback(it) }
    }

internal fun WrapperSpan.sourceText(): String? =
    when (this) {
        is WrapperSpan.Compiler -> span.sourceText()
        is WrapperSpan.Fallback -> span.sourceText()
    }

internal fun WrapperSpan.toString_(): String =
    when (this) {
        is WrapperSpan.Compiler -> span.toString()
        is WrapperSpan.Fallback -> span.toString()
    }

internal fun WrapperSpan.eq(other: WrapperSpan): Boolean =
    when (this) {
        is WrapperSpan.Compiler -> span.eq(other.toCompilerSpan())
        is WrapperSpan.Fallback -> span == other.toFallbackSpan()
    }

internal fun WrapperSpan.hashCode_(): Int =
    when (this) {
        is WrapperSpan.Compiler -> span.toString().hashCode()
        is WrapperSpan.Fallback -> span.hashCode()
    }

internal fun WrapperSpan.toCompilerSpan(): ProcmacroSpan =
    when (this) {
        is WrapperSpan.Compiler -> span
        is WrapperSpan.Fallback -> {
            val sp = span
            ProcmacroSpan.callSite()
        }
    }

internal fun WrapperGroup.delimiter(): Delimiter =
    when (this) {
        is WrapperGroup.Compiler -> group.delimiter().toProcmacro2Delimiter()
        is WrapperGroup.Fallback -> group.delimiter()
    }

internal fun WrapperGroup.stream(): WrapperTokenStream =
    when (this) {
        is WrapperGroup.Compiler -> WrapperTokenStream.Compiler(group.stream())
        is WrapperGroup.Fallback -> WrapperTokenStream.Fallback(group.stream())
    }

internal fun WrapperGroup.span(): WrapperSpan =
    when (this) {
        is WrapperGroup.Compiler -> WrapperSpan.Compiler(group.span())
        is WrapperGroup.Fallback -> WrapperSpan.Fallback(group.span())
    }

internal fun WrapperGroup.spanOpen(): WrapperSpan =
    when (this) {
        is WrapperGroup.Compiler -> WrapperSpan.Compiler(group.spanOpen())
        is WrapperGroup.Fallback -> WrapperSpan.Fallback(group.spanOpen())
    }

internal fun WrapperGroup.spanClose(): WrapperSpan =
    when (this) {
        is WrapperGroup.Compiler -> WrapperSpan.Compiler(group.spanClose())
        is WrapperGroup.Fallback -> WrapperSpan.Fallback(group.spanClose())
    }

internal fun WrapperGroup.setSpan(span: WrapperSpan) {
    when (this) {
        is WrapperGroup.Compiler -> group.setSpan(span.toCompilerSpan())
        is WrapperGroup.Fallback -> group.setSpan(span.toFallbackSpan())
    }
}

internal fun WrapperGroup.toString_(): String =
    when (this) {
        is WrapperGroup.Compiler -> group.toString()
        is WrapperGroup.Fallback -> group.toString()
    }

internal fun WrapperGroup.eq(other: WrapperGroup): Boolean =
    when (this) {
        is WrapperGroup.Compiler -> group.toString() == other.toGroup().toString()
        is WrapperGroup.Fallback -> group.toString() == other.toGroup().toString()
    }

internal fun WrapperGroup.toGroup(): ProcmacroGroup =
    when (this) {
        is WrapperGroup.Compiler -> group
        is WrapperGroup.Fallback -> ProcmacroGroup.new(group.delimiter().toProcmacroDelimiter(), compilerStreamFromFallback(group.stream()))
    }

internal fun WrapperIdent.span(): WrapperSpan =
    when (this) {
        is WrapperIdent.Compiler -> WrapperSpan.Compiler(ident.span())
        is WrapperIdent.Fallback -> WrapperSpan.Fallback(ident.span())
    }

internal fun WrapperIdent.setSpan(span: WrapperSpan) {
    when (this) {
        is WrapperIdent.Compiler -> ident.setSpan(span.toCompilerSpan())
        is WrapperIdent.Fallback -> ident.setSpan(span.toFallbackSpan())
    }
}

internal fun WrapperIdent.toString_(): String =
    when (this) {
        is WrapperIdent.Compiler -> ident.toString()
        is WrapperIdent.Fallback -> ident.toString()
    }

internal fun WrapperIdent.contentEquals(s: String): Boolean =
    when (this) {
        is WrapperIdent.Compiler -> ident.toString() == s
        is WrapperIdent.Fallback -> ident.contentEquals(s)
    }

internal fun WrapperLiteral.span(): WrapperSpan =
    when (this) {
        is WrapperLiteral.Compiler -> WrapperSpan.Compiler(literal.span())
        is WrapperLiteral.Fallback -> WrapperSpan.Fallback(literal.span())
    }

internal fun WrapperLiteral.setSpan(span: WrapperSpan) {
    when (this) {
        is WrapperLiteral.Compiler -> literal.setSpan(span.toCompilerSpan())
        is WrapperLiteral.Fallback -> literal.setSpan(span.toFallbackSpan())
    }
}

internal fun WrapperLiteral.subspan(range: IntRange): WrapperSpan? =
    when (this) {
        is WrapperLiteral.Compiler -> literal.subspan(range)?.let { WrapperSpan.Compiler(it) }
        is WrapperLiteral.Fallback -> literal.subspan(range)?.let { WrapperSpan.Fallback(it) }
    }

internal fun WrapperLiteral.toString_(): String =
    when (this) {
        is WrapperLiteral.Compiler -> literal.toString()
        is WrapperLiteral.Fallback -> literal.toString()
    }

internal fun WrapperLiteral.eq(other: WrapperLiteral): Boolean =
    when (this) {
        is WrapperLiteral.Compiler -> literal.toString() == other.toString_()
        is WrapperLiteral.Fallback -> literal.toString() == other.toString_()
    }

internal fun WrapperLexError.toString_(): String =
    when (this) {
        is WrapperLexError.Compiler -> error.toString()
        is WrapperLexError.Fallback -> error.toString()
    }

internal fun WrapperLexError.span(): WrapperSpan =
    when (this) {
        is WrapperLexError.Compiler -> WrapperSpan.Compiler(ProcmacroSpan.callSite())
        is WrapperLexError.Fallback -> WrapperSpan.Fallback(error.span())
    }

internal sealed class WrapperTokenTree {
    class Group(
        val value: WrapperGroup,
    ) : WrapperTokenTree()

    class Ident(
        val value: WrapperIdent,
    ) : WrapperTokenTree()

    class Punct(
        val value: WrapperPunct,
    ) : WrapperTokenTree()

    class Literal(
        val value: WrapperLiteral,
    ) : WrapperTokenTree()
}

internal data class WrapperPunct(
    val ch: Char,
    val spacing: Spacing,
    val span: WrapperSpan,
)

internal fun WrapperTokenTree.span(): WrapperSpan =
    when (this) {
        is WrapperTokenTree.Group -> value.span()
        is WrapperTokenTree.Ident -> value.span()
        is WrapperTokenTree.Punct -> value.span
        is WrapperTokenTree.Literal -> value.span()
    }

internal fun WrapperTokenTree.setSpan(span: WrapperSpan): WrapperTokenTree =
    when (this) {
        is WrapperTokenTree.Group -> {
            value.setSpan(span)
            this
        }
        is WrapperTokenTree.Ident -> {
            value.setSpan(span)
            this
        }
        is WrapperTokenTree.Punct -> WrapperTokenTree.Punct(value.copy(span = span))
        is WrapperTokenTree.Literal -> {
            value.setSpan(span)
            this
        }
    }

internal fun WrapperTokenTree.asFallbackTree(): TokenTree =
    when (this) {
        is WrapperTokenTree.Group -> TokenTree.Group(Group(value))
        is WrapperTokenTree.Ident -> TokenTree.Ident(Ident(value))
        is WrapperTokenTree.Punct -> TokenTree.Punct(Punct(value.ch, value.spacing, Span(value.span)))
        is WrapperTokenTree.Literal -> TokenTree.Literal(Literal(value))
    }

internal fun WrapperSpan.toFallbackSpan(): FallbackSpan =
    when (this) {
        is WrapperSpan.Fallback -> span
        is WrapperSpan.Compiler -> FallbackSpan.callSite()
    }

internal fun WrapperLiteral.strValue_(): String? =
    when (this) {
        is WrapperLiteral.Compiler -> {
            val repr = literal.toString()
            if (repr.startsWith('"') && repr.endsWith('"') && repr.length >= 2) {
                val quoted = repr.substring(1, repr.length - 1)
                val value = StringBuilder(quoted.length)
                var error: EscapeError? = null
                unescapeStr(quoted) { _, res ->
                    when (res) {
                        is EscapeResult.Ok -> value.append(res.value)
                        is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                    }
                }
                if (error != null) null else value.toString()
            } else if (repr.startsWith('r')) {
                getRaw(repr.substring(1))
            } else {
                null
            }
        }
        is WrapperLiteral.Fallback -> {
            val repr = literal.toString()
            if (repr.startsWith('"') && repr.endsWith('"') && repr.length >= 2) {
                val quoted = repr.substring(1, repr.length - 1)
                val value = StringBuilder(quoted.length)
                var error: EscapeError? = null
                unescapeStr(quoted) { _, res ->
                    when (res) {
                        is EscapeResult.Ok -> value.append(res.value)
                        is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                    }
                }
                if (error != null) null else value.toString()
            } else if (repr.startsWith('r')) {
                getRaw(repr.substring(1))
            } else {
                null
            }
        }
    }

internal fun WrapperLiteral.cstrValue_(): ByteArray? =
    when (this) {
        is WrapperLiteral.Compiler -> {
            val repr = literal.toString()
            if (repr.startsWith("c\"") && repr.endsWith('"') && repr.length >= 3) {
                val quoted = repr.substring(2, repr.length - 1)
                val value = mutableListOf<Byte>()
                var error: EscapeError? = null
                unescapeCStr(quoted) { _, res ->
                    when (res) {
                        is EscapeResult.Ok ->
                            when (res.value) {
                                is MixedUnit.Char -> {
                                    val ch = res.value.value.get()
                                    val utf8 = ch.toString().encodeToByteArray()
                                    for (b in utf8) value.add(b)
                                }
                                is MixedUnit.HighByte ->
                                    value.add(
                                        res.value.value
                                            .get()
                                            .toByte(),
                                    )
                            }
                        is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                    }
                }
                value.add(0)
                if (error != null) null else value.toByteArray()
            } else if (repr.startsWith("cr")) {
                val raw = getRaw(repr.substring(2))
                if (raw != null) raw.encodeToByteArray() + byteArrayOf(0) else null
            } else {
                null
            }
        }
        is WrapperLiteral.Fallback -> {
            val repr = literal.toString()
            if (repr.startsWith("c\"") && repr.endsWith('"') && repr.length >= 3) {
                val quoted = repr.substring(2, repr.length - 1)
                val value = mutableListOf<Byte>()
                var error: EscapeError? = null
                unescapeCStr(quoted) { _, res ->
                    when (res) {
                        is EscapeResult.Ok ->
                            when (res.value) {
                                is MixedUnit.Char -> {
                                    val ch = res.value.value.get()
                                    val utf8 = ch.toString().encodeToByteArray()
                                    for (b in utf8) value.add(b)
                                }
                                is MixedUnit.HighByte ->
                                    value.add(
                                        res.value.value
                                            .get()
                                            .toByte(),
                                    )
                            }
                        is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                    }
                }
                value.add(0)
                if (error != null) null else value.toByteArray()
            } else if (repr.startsWith("cr")) {
                val raw = getRaw(repr.substring(2))
                if (raw != null) raw.encodeToByteArray() + byteArrayOf(0) else null
            } else {
                null
            }
        }
    }

internal fun WrapperLiteral.byteStrValue_(): ByteArray? =
    when (this) {
        is WrapperLiteral.Compiler -> {
            val repr = literal.toString()
            if (repr.startsWith("b\"") && repr.endsWith('"') && repr.length >= 3) {
                val quoted = repr.substring(2, repr.length - 1)
                val value = mutableListOf<Byte>()
                var error: EscapeError? = null
                unescapeByteStr(quoted) { _, res ->
                    when (res) {
                        is EscapeResult.Ok -> value.add(res.value.toByte())
                        is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                    }
                }
                if (error != null) null else value.toByteArray()
            } else if (repr.startsWith("br")) {
                val raw = getRaw(repr.substring(2))
                if (raw != null) raw.encodeToByteArray() else null
            } else {
                null
            }
        }
        is WrapperLiteral.Fallback -> {
            val repr = literal.toString()
            if (repr.startsWith("b\"") && repr.endsWith('"') && repr.length >= 3) {
                val quoted = repr.substring(2, repr.length - 1)
                val value = mutableListOf<Byte>()
                var error: EscapeError? = null
                unescapeByteStr(quoted) { _, res ->
                    when (res) {
                        is EscapeResult.Ok -> value.add(res.value.toByte())
                        is EscapeResult.Err -> if (res.error.isFatal()) error = res.error
                    }
                }
                if (error != null) null else value.toByteArray()
            } else if (repr.startsWith("br")) {
                val raw = getRaw(repr.substring(2))
                if (raw != null) raw.encodeToByteArray() else null
            } else {
                null
            }
        }
    }

internal fun TokenTree.toCompilerTree(): ProcmacroTokenTree =
    when (this) {
        is TokenTree.Group ->
            ProcmacroTokenTree.Group(
                ProcmacroGroup.new(
                    value.delimiter().toProcmacroDelimiter(),
                    compilerStreamFromFallback(value.inner.stream().asFallbackStream()),
                ),
            )
        is TokenTree.Ident -> {
            val span = ProcmacroSpan.callSite()
            val ident =
                if (value.inner.toString_().startsWith("r#")) {
                    ProcmacroIdent.newRaw(value.inner.toString_().removePrefix("r#"), span)
                } else {
                    ProcmacroIdent.new(value.inner.toString_(), span)
                }
            ProcmacroTokenTree.Ident(ident)
        }
        is TokenTree.Punct ->
            ProcmacroTokenTree.Punct(
                io.github.kotlinmania.procmacro.Punct.new(
                    value.asChar(),
                    value.spacing().toProcmacroSpacing(),
                ),
            )
        is TokenTree.Literal ->
            ProcmacroTokenTree.Literal(ProcmacroLiteral.string(value.toString()))
    }

internal fun ProcmacroTokenTree.toWrapperTree(): WrapperTokenTree =
    when (this) {
        is ProcmacroTokenTree.Group ->
            WrapperTokenTree.Group(WrapperGroup.Compiler(value))
        is ProcmacroTokenTree.Ident ->
            WrapperTokenTree.Ident(WrapperIdent.Compiler(value))
        is ProcmacroTokenTree.Punct ->
            WrapperTokenTree.Punct(WrapperPunct(value.asChar(), value.spacing().toProcmacro2Spacing(), WrapperSpan.Compiler(value.span())))
        is ProcmacroTokenTree.Literal ->
            WrapperTokenTree.Literal(WrapperLiteral.Compiler(value))
    }

internal fun TokenTree.toWrapperTree(): WrapperTokenTree =
    when (this) {
        is TokenTree.Group -> {
            val wg = value.inner
            WrapperTokenTree.Group(wg)
        }
        is TokenTree.Ident -> {
            val wi = value.inner
            WrapperTokenTree.Ident(wi)
        }
        is TokenTree.Punct -> {
            val ws = value.span().inner
            WrapperTokenTree.Punct(WrapperPunct(value.asChar(), value.spacing(), ws))
        }
        is TokenTree.Literal -> {
            val wl = value.inner
            WrapperTokenTree.Literal(wl)
        }
    }

internal fun compilerStreamFromFallback(stream: FallbackTokenStream): ProcmacroTokenStream {
    val out = ProcmacroTokenStream.new()
    out.extendTokenTrees(
        stream
            .iter()
            .asSequence()
            .map { it.toCompilerTree() }
            .toList(),
    )
    return out
}

internal fun ProcmacroDelimiter.toProcmacro2Delimiter(): Delimiter =
    when (this) {
        ProcmacroDelimiter.PARENTHESIS -> Delimiter.Parenthesis
        ProcmacroDelimiter.BRACE -> Delimiter.Brace
        ProcmacroDelimiter.BRACKET -> Delimiter.Bracket
        ProcmacroDelimiter.NONE -> Delimiter.None
    }

internal fun Delimiter.toProcmacroDelimiter(): ProcmacroDelimiter =
    when (this) {
        Delimiter.Parenthesis -> ProcmacroDelimiter.PARENTHESIS
        Delimiter.Brace -> ProcmacroDelimiter.BRACE
        Delimiter.Bracket -> ProcmacroDelimiter.BRACKET
        Delimiter.None -> ProcmacroDelimiter.NONE
    }

internal fun ProcmacroSpacing.toProcmacro2Spacing(): Spacing =
    when (this) {
        ProcmacroSpacing.JOINT -> Spacing.Joint
        ProcmacroSpacing.ALONE -> Spacing.Alone
    }

internal fun Spacing.toProcmacroSpacing(): ProcmacroSpacing =
    when (this) {
        Spacing.Joint -> ProcmacroSpacing.JOINT
        Spacing.Alone -> ProcmacroSpacing.ALONE
    }

internal fun WrapperGroup.hashCode_(): Int =
    when (this) {
        is WrapperGroup.Compiler -> group.toString().hashCode()
        is WrapperGroup.Fallback -> group.hashCode()
    }
