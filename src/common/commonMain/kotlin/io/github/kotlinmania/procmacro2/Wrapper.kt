// port-lint: source wrapper.rs
package io.github.kotlinmania.procmacro2

import io.github.kotlinmania.procmacro.LiteralParseOutcome
import io.github.kotlinmania.procmacro.TokenStreamParseOutcome
import io.github.kotlinmania.procmacro.bridge.BridgeClientState
import io.github.kotlinmania.procmacro.bridge.cloneTokenTree
import io.github.kotlinmania.procmacro2.probe.ProcMacroSpan
import io.github.kotlinmania.procmacro2.probe.ProcMacroSpanFile
import io.github.kotlinmania.procmacro2.probe.ProcMacroSpanLocation
import io.github.kotlinmania.procmacro.Delimiter as ProcmacroDelimiter
import io.github.kotlinmania.procmacro.Group as ProcmacroGroup
import io.github.kotlinmania.procmacro.Ident as ProcmacroIdent
import io.github.kotlinmania.procmacro.LexError as ProcmacroLexError
import io.github.kotlinmania.procmacro.Literal as ProcmacroLiteral
import io.github.kotlinmania.procmacro.Punct as ProcmacroPunct
import io.github.kotlinmania.procmacro.Spacing as ProcmacroSpacing
import io.github.kotlinmania.procmacro.Span as ProcmacroSpan
import io.github.kotlinmania.procmacro.TokenStream as ProcmacroTokenStream
import io.github.kotlinmania.procmacro.TokenTree as ProcmacroTokenTree
import io.github.kotlinmania.procmacro.tokenstream.IntoIter as ProcmacroIntoIter

internal class DeferredTokenStream(
    var stream: ProcmacroTokenStream,
    val extra: MutableList<ProcmacroTokenTree> = mutableListOf(),
) {
    companion object {
        fun new(stream: ProcmacroTokenStream): DeferredTokenStream =
            DeferredTokenStream(stream = stream, extra = mutableListOf())
    }

    fun isEmpty(): Boolean = stream.isEmpty() && extra.isEmpty()

    fun evaluateNow() {
        if (extra.isNotEmpty()) {
            val pending = extra.toList()
            extra.clear()
            stream.extendTokenTrees(pending)
        }
    }

    fun intoTokenStream(): ProcmacroTokenStream {
        evaluateNow()
        return stream
    }

    fun cloneDeferred(): DeferredTokenStream {
        val clonedStream = stream.clone()
        val clonedExtra = extra.map(::cloneTokenTree).toMutableList()
        return DeferredTokenStream(stream = clonedStream, extra = clonedExtra)
    }
}

internal sealed class WrapperTokenStream {
    class Compiler(
        val deferred: DeferredTokenStream,
    ) : WrapperTokenStream() {
        constructor(stream: ProcmacroTokenStream) : this(DeferredTokenStream.new(stream))
    }

    class Fallback(
        val stream: FallbackTokenStream,
    ) : WrapperTokenStream()

    companion object {
        fun new(): WrapperTokenStream =
            if (insideProcMacro()) {
                Compiler(DeferredTokenStream.new(ProcmacroTokenStream.new()))
            } else {
                Fallback(FallbackTokenStream.new())
            }

        fun fromStrChecked(src: String): Result<WrapperTokenStream> =
            if (insideProcMacro()) {
                val fallbackCheck = FallbackTokenStream.fromStrChecked(src)
                if (fallbackCheck.isFailure) {
                    Result.failure(LexErrorException(LexError(WrapperLexError.CompilerPanic)))
                } else {
                    when (val outcome = ProcmacroTokenStream.fromString(src)) {
                        is TokenStreamParseOutcome.Ok ->
                            Result.success(Compiler(DeferredTokenStream.new(outcome.value)))
                        is TokenStreamParseOutcome.Err -> {
                            val converted = compilerStreamFromFallback(fallbackCheck.getOrThrow())
                            Result.success(Compiler(DeferredTokenStream.new(converted)))
                        }
                    }
                }
            } else {
                FallbackTokenStream.fromStrChecked(src).map(::Fallback)
            }

        fun fromProcMacro(inner: ProcmacroTokenStream): WrapperTokenStream =
            Compiler(DeferredTokenStream.new(inner))

        fun fromFallback(inner: FallbackTokenStream): WrapperTokenStream =
            Fallback(inner)

        fun fromTokenTree(token: TokenTree): WrapperTokenStream =
            if (insideProcMacro()) {
                val tree = intoCompilerToken(token)
                Compiler(DeferredTokenStream.new(ProcmacroTokenStream.fromTokenTree(tree)))
            } else {
                Fallback(FallbackTokenStream.fromTokenTree(token))
            }

        fun fromTokenTrees(tokens: Iterable<TokenTree>): WrapperTokenStream =
            if (insideProcMacro()) {
                val compilerTrees = tokens.map(::intoCompilerToken)
                Compiler(DeferredTokenStream.new(ProcmacroTokenStream.fromTokenTrees(compilerTrees)))
            } else {
                val stream = FallbackTokenStream.new()
                stream.extendTokenTrees(tokens)
                Fallback(stream)
            }

        fun fromTokenStreams(streams: Iterable<WrapperTokenStream>): WrapperTokenStream {
            val iterator = streams.iterator()
            if (!iterator.hasNext()) {
                return new()
            }
            return when (val first = iterator.next()) {
                is Compiler -> {
                    val acc = first.deferred.cloneDeferred()
                    acc.evaluateNow()
                    val remaining = mutableListOf<ProcmacroTokenStream>()
                    while (iterator.hasNext()) {
                        remaining.add(iterator.next().unwrapNightly())
                    }
                    acc.stream.extendTokenStreams(remaining)
                    Compiler(acc)
                }
                is Fallback -> {
                    val acc = first.stream.clone()
                    val remaining = mutableListOf<FallbackTokenStream>()
                    while (iterator.hasNext()) {
                        remaining.add(iterator.next().unwrapStable())
                    }
                    acc.extendTokenStreams(remaining)
                    Fallback(acc)
                }
            }
        }
    }
}

internal sealed class WrapperSpan {
    class Compiler(
        val span: ProcmacroSpan,
    ) : WrapperSpan()

    class Fallback(
        val span: FallbackSpan,
    ) : WrapperSpan()

    companion object {
        fun callSite(): WrapperSpan =
            if (insideProcMacro()) {
                Compiler(ProcmacroSpan.callSite())
            } else {
                Fallback(FallbackSpan.callSite())
            }

        fun mixedSite(): WrapperSpan =
            if (insideProcMacro()) {
                Compiler(ProcmacroSpan.mixedSite())
            } else {
                Fallback(FallbackSpan.mixedSite())
            }

        fun defSite(): WrapperSpan =
            if (insideProcMacro()) {
                Compiler(ProcmacroSpan.defSite())
            } else {
                Fallback(FallbackSpan.defSite())
            }
    }
}

internal sealed class WrapperGroup {
    class Compiler(
        val group: ProcmacroGroup,
    ) : WrapperGroup()

    class Fallback(
        val group: FallbackGroup,
    ) : WrapperGroup()

    companion object {
        fun new(
            delimiter: Delimiter,
            stream: WrapperTokenStream,
        ): WrapperGroup =
            when (stream) {
                is WrapperTokenStream.Compiler -> {
                    val compilerDelimiter = delimiter.toProcmacroDelimiter()
                    val compilerStream = stream.deferred.intoTokenStream()
                    Compiler(ProcmacroGroup.new(compilerDelimiter, compilerStream))
                }
                is WrapperTokenStream.Fallback ->
                    Fallback(FallbackGroup(delimiter, stream.stream))
            }
    }
}

internal sealed class WrapperIdent {
    class Compiler(
        val ident: ProcmacroIdent,
    ) : WrapperIdent()

    class Fallback(
        val ident: FallbackIdent,
    ) : WrapperIdent()

    companion object {
        fun newChecked(
            string: String,
            span: WrapperSpan,
        ): WrapperIdent =
            when (span) {
                is WrapperSpan.Compiler -> Compiler(ProcmacroIdent.new(string, span.span))
                is WrapperSpan.Fallback -> Fallback(FallbackIdent.newChecked(string, span.span))
            }

        fun newRawChecked(
            string: String,
            span: WrapperSpan,
        ): WrapperIdent =
            when (span) {
                is WrapperSpan.Compiler -> Compiler(ProcmacroIdent.newRaw(string, span.span))
                is WrapperSpan.Fallback -> Fallback(FallbackIdent.newRawChecked(string, span.span))
            }
    }
}

internal sealed class WrapperLiteral {
    class Compiler(
        val literal: ProcmacroLiteral,
    ) : WrapperLiteral()

    class Fallback(
        val literal: FallbackLiteral,
    ) : WrapperLiteral()

    companion object {
        fun fromStrChecked(repr: String): Result<WrapperLiteral> =
            if (insideProcMacro()) {
                val fallbackCheck = FallbackLiteral.fromStrChecked(repr)
                if (fallbackCheck.isFailure) {
                    Result.failure(LexErrorException(LexError(WrapperLexError.CompilerPanic)))
                } else {
                    when (val outcome = ProcmacroLiteral.fromString(repr)) {
                        is LiteralParseOutcome.Ok -> Result.success(Compiler(outcome.value))
                        is LiteralParseOutcome.Err -> {
                            val bridged = ProcmacroLiteral.fromStrUnchecked(fallbackCheck.getOrThrow().toString())
                            Result.success(Compiler(bridged))
                        }
                    }
                }
            } else {
                FallbackLiteral.fromStrChecked(repr).map(::Fallback)
            }

        fun fromStrUnchecked(repr: String): WrapperLiteral =
            if (insideProcMacro()) {
                Compiler(ProcmacroLiteral.fromStrUnchecked(repr))
            } else {
                Fallback(FallbackLiteral.fromStrUnchecked(repr))
            }

        fun u8Suffixed(n: UByte): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u8Suffixed(n)) else Fallback(FallbackLiteral.u8Suffixed(n))

        fun u16Suffixed(n: UShort): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u16Suffixed(n)) else Fallback(FallbackLiteral.u16Suffixed(n))

        fun u32Suffixed(n: UInt): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u32Suffixed(n)) else Fallback(FallbackLiteral.u32Suffixed(n))

        fun u64Suffixed(n: ULong): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u64Suffixed(n)) else Fallback(FallbackLiteral.u64Suffixed(n))

        fun u128Suffixed(n: ULong): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u128Suffixed(n)) else Fallback(FallbackLiteral.u128Suffixed(n))

        fun usizeSuffixed(n: ULong): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.usizeSuffixed(n)) else Fallback(FallbackLiteral.usizeSuffixed(n))

        fun i8Suffixed(n: Byte): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i8Suffixed(n)) else Fallback(FallbackLiteral.i8Suffixed(n))

        fun i16Suffixed(n: Short): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i16Suffixed(n)) else Fallback(FallbackLiteral.i16Suffixed(n))

        fun i32Suffixed(n: Int): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i32Suffixed(n)) else Fallback(FallbackLiteral.i32Suffixed(n))

        fun i64Suffixed(n: Long): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i64Suffixed(n)) else Fallback(FallbackLiteral.i64Suffixed(n))

        fun i128Suffixed(n: Long): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i128Suffixed(n)) else Fallback(FallbackLiteral.i128Suffixed(n))

        fun isizeSuffixed(n: Long): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.isizeSuffixed(n)) else Fallback(FallbackLiteral.isizeSuffixed(n))

        fun f32Suffixed(n: Float): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.f32Suffixed(n)) else Fallback(FallbackLiteral.f32Suffixed(n))

        fun f64Suffixed(n: Double): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.f64Suffixed(n)) else Fallback(FallbackLiteral.f64Suffixed(n))

        fun u8Unsuffixed(n: UByte): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u8Unsuffixed(n)) else Fallback(FallbackLiteral.u8Unsuffixed(n))

        fun u16Unsuffixed(n: UShort): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u16Unsuffixed(n)) else Fallback(FallbackLiteral.u16Unsuffixed(n))

        fun u32Unsuffixed(n: UInt): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u32Unsuffixed(n)) else Fallback(FallbackLiteral.u32Unsuffixed(n))

        fun u64Unsuffixed(n: ULong): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u64Unsuffixed(n)) else Fallback(FallbackLiteral.u64Unsuffixed(n))

        fun u128Unsuffixed(n: ULong): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.u128Unsuffixed(n)) else Fallback(FallbackLiteral.u128Unsuffixed(n))

        fun usizeUnsuffixed(n: ULong): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.usizeUnsuffixed(n)) else Fallback(FallbackLiteral.usizeUnsuffixed(n))

        fun i8Unsuffixed(n: Byte): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i8Unsuffixed(n)) else Fallback(FallbackLiteral.i8Unsuffixed(n))

        fun i16Unsuffixed(n: Short): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i16Unsuffixed(n)) else Fallback(FallbackLiteral.i16Unsuffixed(n))

        fun i32Unsuffixed(n: Int): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i32Unsuffixed(n)) else Fallback(FallbackLiteral.i32Unsuffixed(n))

        fun i64Unsuffixed(n: Long): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i64Unsuffixed(n)) else Fallback(FallbackLiteral.i64Unsuffixed(n))

        fun i128Unsuffixed(n: Long): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.i128Unsuffixed(n)) else Fallback(FallbackLiteral.i128Unsuffixed(n))

        fun isizeUnsuffixed(n: Long): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.isizeUnsuffixed(n)) else Fallback(FallbackLiteral.isizeUnsuffixed(n))

        fun f32Unsuffixed(n: Float): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.f32Unsuffixed(n)) else Fallback(FallbackLiteral.f32Unsuffixed(n))

        fun f64Unsuffixed(n: Double): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.f64Unsuffixed(n)) else Fallback(FallbackLiteral.f64Unsuffixed(n))

        fun string(string: String): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.string(string)) else Fallback(FallbackLiteral.string(string))

        fun character(ch: Char): WrapperLiteral =
            if (insideProcMacro()) Compiler(ProcmacroLiteral.character(ch)) else Fallback(FallbackLiteral.character(ch))

        fun characterCodePoint(codePoint: Int): WrapperLiteral =
            if (insideProcMacro()) {
                Compiler(ProcmacroLiteral.characterCodePoint(codePoint))
            } else {
                Fallback(FallbackLiteral.characterCodePoint(codePoint))
            }

        fun byteCharacter(byte: UByte): WrapperLiteral =
            if (insideProcMacro()) {
                Compiler(ProcmacroLiteral.byteCharacter(byte.toByte()))
            } else {
                Fallback(FallbackLiteral.byteCharacter(byte))
            }

        fun byteString(bytes: ByteArray): WrapperLiteral =
            if (insideProcMacro()) {
                Compiler(ProcmacroLiteral.byteString(bytes))
            } else {
                Fallback(FallbackLiteral.byteString(bytes))
            }

        fun cString(bytes: ByteArray): WrapperLiteral =
            if (insideProcMacro()) {
                Compiler(ProcmacroLiteral.cString(bytes))
            } else {
                Fallback(FallbackLiteral.cString(bytes))
            }
    }
}

internal sealed class WrapperLexError {
    class Compiler(
        val error: ProcmacroLexError,
    ) : WrapperLexError()

    class Fallback(
        val error: FallbackLexError,
    ) : WrapperLexError()

    object CompilerPanic : WrapperLexError()
}

internal fun useCompiler(): Boolean = insideProcMacro()

internal fun mismatch(context: String): Nothing =
    error("compiler/fallback mismatch ($context)")

internal fun WrapperTokenStream.unwrapNightly(): ProcmacroTokenStream =
    when (this) {
        is WrapperTokenStream.Compiler -> deferred.intoTokenStream()
        is WrapperTokenStream.Fallback -> mismatch("TokenStream::unwrapNightly")
    }

internal fun WrapperTokenStream.unwrapStable(): FallbackTokenStream =
    when (this) {
        is WrapperTokenStream.Compiler -> mismatch("TokenStream::unwrapStable")
        is WrapperTokenStream.Fallback -> stream
    }

internal fun fromTokenStream(inner: WrapperTokenStream): ProcmacroTokenStream =
    when (inner) {
        is WrapperTokenStream.Compiler -> inner.deferred.intoTokenStream()
        is WrapperTokenStream.Fallback -> compilerStreamFromFallback(inner.stream)
    }

internal fun intoCompilerToken(token: TokenTree): ProcmacroTokenTree =
    when (token) {
        is TokenTree.Group -> ProcmacroTokenTree.Group(token.value.inner.unwrapNightly())
        is TokenTree.Punct -> {
            val spacing = token.value.spacing().toProcmacroSpacing()
            val punct = ProcmacroPunct.new(token.value.asChar(), spacing)
            val compilerSpan =
                token.value
                    .span()
                    .inner
                    .unwrapNightly()
            punct.setSpan(compilerSpan)
            ProcmacroTokenTree.Punct(punct)
        }
        is TokenTree.Ident -> ProcmacroTokenTree.Ident(token.value.inner.unwrapNightly())
        is TokenTree.Literal -> ProcmacroTokenTree.Literal(token.value.inner.unwrapNightly())
    }

internal fun WrapperTokenStream.isEmpty(): Boolean =
    when (this) {
        is WrapperTokenStream.Compiler -> deferred.isEmpty()
        is WrapperTokenStream.Fallback -> stream.isEmpty()
    }

internal fun WrapperTokenStream.toString_(): String =
    when (this) {
        is WrapperTokenStream.Compiler -> deferred.intoTokenStream().toString()
        is WrapperTokenStream.Fallback -> stream.toString()
    }

internal fun WrapperTokenStream.clone_(): WrapperTokenStream =
    when (this) {
        is WrapperTokenStream.Compiler -> WrapperTokenStream.Compiler(deferred.cloneDeferred())
        is WrapperTokenStream.Fallback -> WrapperTokenStream.Fallback(stream.clone())
    }

internal fun WrapperTokenStream.extendTokenTrees(trees: Iterable<TokenTree>) {
    when (this) {
        is WrapperTokenStream.Compiler -> {
            for (token in trees) {
                deferred.extra.add(intoCompilerToken(token))
            }
        }
        is WrapperTokenStream.Fallback -> stream.extendTokenTrees(trees)
    }
}

internal fun WrapperTokenStream.extendTokenStreams(streams: Iterable<WrapperTokenStream>) {
    when (this) {
        is WrapperTokenStream.Compiler -> {
            deferred.evaluateNow()
            deferred.stream.extendTokenStreams(streams.map { it.unwrapNightly() })
        }
        is WrapperTokenStream.Fallback ->
            stream.extendTokenStreams(streams.map { it.unwrapStable() })
    }
}

internal fun WrapperTokenStream.iter(): Sequence<WrapperTokenTree> =
    when (this) {
        is WrapperTokenStream.Compiler ->
            deferred
                .intoTokenStream()
                .asSequence()
                .map { it.toWrapperTree() }
        is WrapperTokenStream.Fallback ->
            stream
                .iter()
                .asSequence()
                .map { it.toWrapperTree() }
    }

internal fun WrapperTokenStream.intoIterW(): WrapperTokenStreamIntoIter =
    when (this) {
        is WrapperTokenStream.Compiler ->
            WrapperTokenStreamIntoIter.Compiler(deferred.intoTokenStream().iterator())
        is WrapperTokenStream.Fallback ->
            WrapperTokenStreamIntoIter.Fallback(stream.intoIter())
    }

internal sealed class WrapperTokenStreamIntoIter {
    class Compiler(
        val iter: ProcmacroIntoIter,
    ) : WrapperTokenStreamIntoIter() {
        override fun hasNext(): Boolean = iter.hasNext()

        override fun next(): WrapperTokenTree = iter.next().toWrapperTree()

        override fun sizeHint(): Pair<Int, Int?> {
            val remaining = iter.remainingCount()
            return Pair(remaining, remaining)
        }

        override fun remainingPublicTrees(): List<TokenTree> {
            val copy = iter.clone()
            val out = mutableListOf<TokenTree>()
            while (copy.hasNext()) {
                out.add(copy.next().toWrapperTree().toPublicTree())
            }
            return out
        }
    }

    class Fallback(
        val iter: RcVecIntoIter<TokenTree>,
    ) : WrapperTokenStreamIntoIter() {
        override fun hasNext(): Boolean = iter.hasNext()

        override fun next(): WrapperTokenTree = iter.next().toWrapperTree()

        override fun sizeHint(): Pair<Int, Int?> = iter.sizeHint()

        override fun remainingPublicTrees(): List<TokenTree> = iter.remaining()
    }

    abstract fun hasNext(): Boolean

    abstract fun next(): WrapperTokenTree

    abstract fun sizeHint(): Pair<Int, Int?>

    abstract fun remainingPublicTrees(): List<TokenTree>
}

private sealed class TokenEqualityKey {
    data class GroupKey(
        val delimiter: Delimiter,
        val items: List<TokenEqualityKey>,
    ) : TokenEqualityKey()

    data class IdentKey(
        val text: String,
    ) : TokenEqualityKey()

    data class PunctKey(
        val ch: Char,
        val spacing: Spacing,
    ) : TokenEqualityKey()

    data class LiteralKey(
        val text: String,
    ) : TokenEqualityKey()
}

private fun WrapperTokenTree.toEqualityKey(): TokenEqualityKey =
    when (this) {
        is WrapperTokenTree.Group ->
            TokenEqualityKey.GroupKey(
                delimiter = value.delimiter(),
                items = value.stream().toEqualityKeys(),
            )
        is WrapperTokenTree.Ident -> TokenEqualityKey.IdentKey(value.toString_())
        is WrapperTokenTree.Punct -> TokenEqualityKey.PunctKey(value.ch, value.spacing)
        is WrapperTokenTree.Literal -> TokenEqualityKey.LiteralKey(value.toString_())
    }

private fun WrapperTokenStream.toEqualityKeys(): List<TokenEqualityKey> =
    iter().map { it.toEqualityKey() }.toList()

internal fun WrapperTokenStream.toTokenStreamEqualityItems(): List<*> = toEqualityKeys()

internal fun WrapperSpan.unwrap(): ProcmacroSpan =
    when (this) {
        is WrapperSpan.Compiler -> span
        is WrapperSpan.Fallback -> error("proc_macro::Span is only available in procedural macros")
    }

internal fun WrapperSpan.unwrapNightly(): ProcmacroSpan =
    when (this) {
        is WrapperSpan.Compiler -> span
        is WrapperSpan.Fallback -> mismatch("Span::unwrapNightly")
    }

internal fun WrapperSpan.resolvedAt(other: WrapperSpan): WrapperSpan =
    when {
        this is WrapperSpan.Compiler && other is WrapperSpan.Compiler ->
            WrapperSpan.Compiler(span.resolvedAt(other.span))
        this is WrapperSpan.Fallback && other is WrapperSpan.Fallback ->
            WrapperSpan.Fallback(span.resolvedAt(other.span))
        else -> mismatch("Span::resolvedAt")
    }

internal fun WrapperSpan.locatedAt(other: WrapperSpan): WrapperSpan =
    when {
        this is WrapperSpan.Compiler && other is WrapperSpan.Compiler ->
            WrapperSpan.Compiler(span.locatedAt(other.span))
        this is WrapperSpan.Fallback && other is WrapperSpan.Fallback ->
            WrapperSpan.Fallback(span.locatedAt(other.span))
        else -> mismatch("Span::locatedAt")
    }

internal fun WrapperSpan.byteRange(): IntRange =
    when (this) {
        is WrapperSpan.Compiler -> ProcMacroSpan.byteRange(span)
        is WrapperSpan.Fallback -> span.byteRange()
    }

internal fun WrapperSpan.start(): LineColumn =
    when (this) {
        is WrapperSpan.Compiler -> {
            val startSpan = ProcMacroSpanLocation.start(span)
            LineColumn(
                line = ProcMacroSpanLocation.line(startSpan),
                column = (ProcMacroSpanLocation.column(startSpan) - 1).coerceAtLeast(0),
            )
        }
        is WrapperSpan.Fallback -> span.start()
    }

internal fun WrapperSpan.end(): LineColumn =
    when (this) {
        is WrapperSpan.Compiler -> {
            val endSpan = ProcMacroSpanLocation.end(span)
            LineColumn(
                line = ProcMacroSpanLocation.line(endSpan),
                column = (ProcMacroSpanLocation.column(endSpan) - 1).coerceAtLeast(0),
            )
        }
        is WrapperSpan.Fallback -> span.end()
    }

internal fun WrapperSpan.file(): String =
    when (this) {
        is WrapperSpan.Compiler -> ProcMacroSpanFile.file(span)
        is WrapperSpan.Fallback -> span.file()
    }

internal fun WrapperSpan.localFile(): String? =
    when (this) {
        is WrapperSpan.Compiler -> ProcMacroSpanFile.localFile(span)
        is WrapperSpan.Fallback -> span.localFile()
    }

internal fun WrapperSpan.join(other: WrapperSpan): WrapperSpan? =
    when {
        this is WrapperSpan.Compiler && other is WrapperSpan.Compiler ->
            ProcMacroSpan.join(span, other.span)?.let(WrapperSpan::Compiler)
        this is WrapperSpan.Fallback && other is WrapperSpan.Fallback ->
            span.join(other.span)?.let(WrapperSpan::Fallback)
        else -> null
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
    when {
        this is WrapperSpan.Compiler && other is WrapperSpan.Compiler -> span.eq(other.span)
        this is WrapperSpan.Fallback && other is WrapperSpan.Fallback -> span == other.span
        else -> false
    }

internal fun WrapperSpan.hashCode_(): Int =
    when (this) {
        is WrapperSpan.Compiler -> span.toString().hashCode()
        is WrapperSpan.Fallback -> span.hashCode()
    }

internal fun WrapperGroup.unwrapNightly(): ProcmacroGroup =
    when (this) {
        is WrapperGroup.Compiler -> group
        is WrapperGroup.Fallback -> mismatch("Group::unwrapNightly")
    }

internal fun WrapperGroup.delimiter(): Delimiter =
    when (this) {
        is WrapperGroup.Compiler -> group.delimiter().toProcmacro2Delimiter()
        is WrapperGroup.Fallback -> group.delimiter()
    }

internal fun WrapperGroup.stream(): WrapperTokenStream =
    when (this) {
        is WrapperGroup.Compiler ->
            WrapperTokenStream.Compiler(DeferredTokenStream.new(group.stream()))
        is WrapperGroup.Fallback -> WrapperTokenStream.Fallback(group.stream())
    }

internal fun WrapperGroup.cloneToken(): WrapperGroup =
    when (this) {
        is WrapperGroup.Fallback -> WrapperGroup.Fallback(group.clone())
        is WrapperGroup.Compiler ->
            WrapperGroup.Compiler(
                (cloneTokenTree(ProcmacroTokenTree.Group(group)) as ProcmacroTokenTree.Group).value,
            )
    }

internal fun WrapperIdent.unwrapNightly(): ProcmacroIdent =
    when (this) {
        is WrapperIdent.Compiler -> ident
        is WrapperIdent.Fallback -> mismatch("Ident::unwrapNightly")
    }

internal fun WrapperIdent.cloneToken(): WrapperIdent =
    when (this) {
        is WrapperIdent.Fallback -> WrapperIdent.Fallback(ident.clone())
        is WrapperIdent.Compiler ->
            WrapperIdent.Compiler(
                (cloneTokenTree(ProcmacroTokenTree.Ident(ident)) as ProcmacroTokenTree.Ident).value,
            )
    }

internal fun WrapperLiteral.unwrapNightly(): ProcmacroLiteral =
    when (this) {
        is WrapperLiteral.Compiler -> literal
        is WrapperLiteral.Fallback -> mismatch("Literal::unwrapNightly")
    }

internal fun WrapperLiteral.cloneToken(): WrapperLiteral =
    when (this) {
        is WrapperLiteral.Fallback -> WrapperLiteral.Fallback(literal.clone())
        is WrapperLiteral.Compiler ->
            WrapperLiteral.Compiler(
                (cloneTokenTree(ProcmacroTokenTree.Literal(literal)) as ProcmacroTokenTree.Literal).value,
            )
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
    when {
        this is WrapperGroup.Compiler && span is WrapperSpan.Compiler -> group.setSpan(span.span)
        this is WrapperGroup.Fallback && span is WrapperSpan.Fallback -> group.setSpan(span.span)
        else -> mismatch("Group::setSpan")
    }
}

internal fun WrapperGroup.toString_(): String =
    when (this) {
        is WrapperGroup.Compiler -> group.toString()
        is WrapperGroup.Fallback -> group.toString()
    }

internal fun WrapperGroup.eq(other: WrapperGroup): Boolean =
    delimiter() == other.delimiter() &&
        stream().toEqualityKeys() == other.stream().toEqualityKeys()

internal fun WrapperGroup.hashCode_(): Int =
    31 * delimiter().hashCode() + stream().toEqualityKeys().hashCode()

internal fun WrapperIdent.span(): WrapperSpan =
    when (this) {
        is WrapperIdent.Compiler -> WrapperSpan.Compiler(ident.span())
        is WrapperIdent.Fallback -> WrapperSpan.Fallback(ident.span())
    }

internal fun WrapperIdent.setSpan(span: WrapperSpan) {
    when {
        this is WrapperIdent.Compiler && span is WrapperSpan.Compiler -> ident.setSpan(span.span)
        this is WrapperIdent.Fallback && span is WrapperSpan.Fallback -> ident.setSpan(span.span)
        else -> mismatch("Ident::setSpan")
    }
}

internal fun WrapperIdent.eq(other: WrapperIdent): Boolean =
    when {
        this is WrapperIdent.Compiler && other is WrapperIdent.Compiler ->
            ident.toString() == other.ident.toString()
        this is WrapperIdent.Fallback && other is WrapperIdent.Fallback ->
            ident == other.ident
        else -> mismatch("Ident::eq")
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
    when {
        this is WrapperLiteral.Compiler && span is WrapperSpan.Compiler -> literal.setSpan(span.span)
        this is WrapperLiteral.Fallback && span is WrapperSpan.Fallback -> literal.setSpan(span.span)
        else -> mismatch("Literal::setSpan")
    }
}

internal fun WrapperLiteral.subspan(range: IntRange): WrapperSpan? =
    when (this) {
        is WrapperLiteral.Compiler -> ProcMacroSpan.subspan(literal, range)?.let(WrapperSpan::Compiler)
        is WrapperLiteral.Fallback -> literal.subspan(range)?.let(WrapperSpan::Fallback)
    }

internal fun WrapperLiteral.toString_(): String =
    when (this) {
        is WrapperLiteral.Compiler -> literal.toString()
        is WrapperLiteral.Fallback -> literal.toString()
    }

internal fun WrapperLiteral.eq(other: WrapperLiteral): Boolean =
    toString_() == other.toString_()

internal fun WrapperLexError.toString_(): String =
    when (this) {
        is WrapperLexError.Compiler -> error.toString()
        is WrapperLexError.Fallback -> error.toString()
        WrapperLexError.CompilerPanic -> FallbackLexError.callSite().toString()
    }

internal fun WrapperLexError.span(): WrapperSpan =
    when (this) {
        is WrapperLexError.Compiler -> WrapperSpan.Compiler(ProcmacroSpan.callSite())
        is WrapperLexError.Fallback -> WrapperSpan.Fallback(error.span())
        WrapperLexError.CompilerPanic -> WrapperSpan.callSite()
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

internal fun WrapperTokenTree.toPublicTree(): TokenTree =
    when (this) {
        is WrapperTokenTree.Group -> TokenTree.Group(Group(value))
        is WrapperTokenTree.Ident -> TokenTree.Ident(Ident(value))
        is WrapperTokenTree.Punct -> TokenTree.Punct(Punct(value.ch, value.spacing, Span(value.span)))
        is WrapperTokenTree.Literal -> TokenTree.Literal(Literal(value))
    }

internal fun ProcmacroTokenTree.toWrapperTree(): WrapperTokenTree =
    when (this) {
        is ProcmacroTokenTree.Group ->
            WrapperTokenTree.Group(WrapperGroup.Compiler(value))
        is ProcmacroTokenTree.Ident ->
            WrapperTokenTree.Ident(WrapperIdent.Compiler(value))
        is ProcmacroTokenTree.Punct ->
            WrapperTokenTree.Punct(
                WrapperPunct(
                    ch = value.asChar(),
                    spacing = value.spacing().toProcmacro2Spacing(),
                    span = WrapperSpan.Compiler(value.span()),
                ),
            )
        is ProcmacroTokenTree.Literal ->
            WrapperTokenTree.Literal(WrapperLiteral.Compiler(value))
    }

internal fun TokenTree.toWrapperTree(): WrapperTokenTree =
    when (this) {
        is TokenTree.Group -> WrapperTokenTree.Group(value.inner)
        is TokenTree.Ident -> WrapperTokenTree.Ident(value.inner)
        is TokenTree.Punct ->
            WrapperTokenTree.Punct(WrapperPunct(value.asChar(), value.spacing(), value.span().inner))
        is TokenTree.Literal -> WrapperTokenTree.Literal(value.inner)
    }

private fun compilerTreeFromFallback(tree: TokenTree): ProcmacroTokenTree =
    when (tree) {
        is TokenTree.Group -> {
            val innerFallback =
                when (val innerStream = tree.value.inner.stream()) {
                    is WrapperTokenStream.Compiler -> innerStream.deferred.intoTokenStream()
                    is WrapperTokenStream.Fallback -> compilerStreamFromFallback(innerStream.stream)
                }
            ProcmacroTokenTree.Group(
                ProcmacroGroup.new(
                    tree.value.delimiter().toProcmacroDelimiter(),
                    innerFallback,
                ),
            )
        }
        is TokenTree.Ident -> {
            val spelling = tree.value.toString()
            val span = ProcmacroSpan.callSite()
            val ident =
                if (spelling.startsWith("r#")) {
                    ProcmacroIdent.newRaw(spelling.removePrefix("r#"), span)
                } else {
                    ProcmacroIdent.new(spelling, span)
                }
            ProcmacroTokenTree.Ident(ident)
        }
        is TokenTree.Punct -> {
            val punct =
                ProcmacroPunct.new(
                    tree.value.asChar(),
                    tree.value.spacing().toProcmacroSpacing(),
                )
            ProcmacroTokenTree.Punct(punct)
        }
        is TokenTree.Literal ->
            ProcmacroTokenTree.Literal(ProcmacroLiteral.fromStrUnchecked(tree.value.toString()))
    }

internal fun compilerStreamFromFallback(stream: FallbackTokenStream): ProcmacroTokenStream {
    if (!BridgeClientState.isAvailable()) {
        error("procedural macro API is used outside of a procedural macro")
    }
    val out = ProcmacroTokenStream.new()
    val converted =
        stream
            .iter()
            .asSequence()
            .map(::compilerTreeFromFallback)
            .toList()
    out.extendTokenTrees(converted)
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

internal fun wrapperInvalidateCurrentThreadSpans() {
    if (insideProcMacro()) {
        error("proc_macro2::extra::invalidate_current_thread_spans is not available in procedural macros")
    } else {
        invalidateCurrentThreadSpansInternal()
    }
}
