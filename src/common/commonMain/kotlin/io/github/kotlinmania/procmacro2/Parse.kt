// port-lint: source parse.rs
package io.github.kotlinmania.procmacro2

internal class Cursor internal constructor(
    internal val backing: CursorBacking,
    internal val pos: Int,
    val off: Int,
) {
    constructor(
        rest: String,
        off: Int,
    ) : this(CursorBacking(rest), 0, off)

    val rest: String
        get() = if (pos == 0) backing.source else backing.source.substring(pos)

    internal fun isAscii(): Boolean = backing.isAsciiFrom(pos)

    fun advance(bytes: Int): Cursor {
        val step = bytes.coerceAtMost(len()).coerceAtLeast(0)
        return if (step == 0) {
            this
        } else {
            Cursor(
                backing = backing,
                pos = pos + step,
                off = off + step,
            )
        }
    }

    fun startsWith(s: String): Boolean = startsWithAt(0, s)

    fun startsWithChar(ch: Char): Boolean = !isEmpty() && charAt(0) == ch

    fun startsWithFn(f: (Char) -> Boolean): Boolean = !isEmpty() && f(charAt(0))

    fun isEmpty(): Boolean = pos >= backing.source.length

    fun len(): Int = backing.source.length - pos

    fun asBytes(): ByteArray = rest.encodeToByteArray()

    fun bytes(): List<Int> = backing.bytesFrom(pos)

    fun firstByte(): Int? = backing.firstByteFrom(pos)

    fun chars(): Iterable<Char> = backing.charsFrom(pos)

    fun charIndices(): Sequence<Pair<Int, Char>> = backing.charIndicesFrom(pos)

    fun parse(tag: String): Result<Cursor> =
        if (startsWith(tag)) {
            Result.success(advance(tag.length))
        } else {
            reject()
        }

    operator fun component1(): String = rest

    operator fun component2(): Int = off

    fun copy(
        rest: String = this.rest,
        off: Int = this.off,
    ): Cursor = Cursor(rest, off)

    override fun equals(other: Any?): Boolean = this === other || backing.equalsCursor(pos, off, other)

    override fun hashCode(): Int = backing.hashCursor(pos, off)

    override fun toString(): String = "Cursor(rest=$rest, off=$off)"
}

private object Reject : Exception()

private typealias PResult<O> = Result<Pair<Cursor, O>>

private fun <T> reject(): Result<T> = Result.failure(Reject)

private fun skipWhitespace(input: Cursor): Cursor {
    var s = input
    while (!s.isEmpty()) {
        val byte = s.firstByte()!!
        if (byte == '/'.code) {
            if (
                s.startsWith("//") &&
                (!s.startsWith("///") || s.startsWith("////")) &&
                !s.startsWith("//!")
            ) {
                val (cursor, _) = takeUntilNewlineOrEof(s)
                s = cursor
                continue
            } else if (s.startsWith("/**/")) {
                s = s.advance(4)
                continue
            } else if (
                s.startsWith("/*") &&
                (!s.startsWith("/**") || s.startsWith("/***")) &&
                !s.startsWith("/*!")
            ) {
                val block = blockComment(s)
                if (block.isSuccess) {
                    s = block.getOrThrow().first
                    continue
                }
                return s
            }
        }
        when (byte) {
            ' '.code, in 0x09..0x0d -> {
                s = s.advance(1)
                continue
            }
            in 0..0x7f -> Unit
            else -> {
                val (cp, width) = s.codePointAndWidthAt(0)
                if (isWhitespace(cp)) {
                    s = s.advance(width)
                    continue
                }
            }
        }
        return s
    }
    return s
}

private fun blockComment(input: Cursor): PResult<String> {
    if (!input.startsWith("/*")) {
        return reject()
    }
    var depth = 0
    // Iterate by character, not by UTF-8 byte: the cursor's advance()/offset bookkeeping
    // is character-based, so a byte index would over-run the remaining slice once the comment
    // contains any multibyte character. The `/` and `*` markers are always ASCII.
    var i = 0
    val upper = input.len() - 1
    while (i < upper) {
        if (input.charAt(i) == '/' && input.charAt(i + 1) == '*') {
            depth += 1
            i += 1
        } else if (input.charAt(i) == '*' && input.charAt(i + 1) == '/') {
            depth -= 1
            if (depth == 0) {
                return Result.success(input.advance(i + 2) to input.slice(0, i + 2))
            }
            i += 1
        }
        i += 1
    }
    return reject()
}

private fun isWhitespace(cp: Int): Boolean =
    cp in 0x0009..0x000D ||
        cp == 0x0020 ||
        cp == 0x0085 ||
        cp == 0x00A0 ||
        cp == 0x1680 ||
        cp in 0x2000..0x200A ||
        cp == 0x200E ||
        cp == 0x200F ||
        cp == 0x2028 ||
        cp == 0x2029 ||
        cp == 0x202F ||
        cp == 0x205F ||
        cp == 0x3000

private fun wordBreak(input: Cursor): Result<Cursor> {
    if (input.isEmpty()) {
        return Result.success(input)
    }
    val (cp, _) = input.codePointAndWidthAt(0)
    return if (isIdentContinueCodePoint(cp)) reject() else Result.success(input)
}

private const val ERROR = "(/*ERROR*/)"

internal fun tokenStream(inputCursor: Cursor): Result<FallbackTokenStream> {
    var input = inputCursor
    var tokens = TokenStreamBuilder.new()
    val stack = mutableListOf<Pair<Int, Pair<Delimiter, TokenStreamBuilder>>>()

    while (true) {
        input = skipWhitespace(input)

        val doc = docComment(input, tokens)
        if (doc.isSuccess) {
            input = doc.getOrThrow().first
            continue
        }

        val lo = input.off
        val first = input.firstByte()
        if (first == null) {
            return if (stack.isEmpty()) {
                Result.success(tokens.build())
            } else {
                val errSpan = FallbackSpan(stack.last().first, stack.last().first)
                Result.failure(LexErrorException(LexError(WrapperLexError.Fallback(FallbackLexError(errSpan)))))
            }
        }

        val openDelimiter =
            when {
                first == '('.code && !input.startsWith(ERROR) -> Delimiter.Parenthesis
                first == '['.code -> Delimiter.Bracket
                first == '{'.code -> Delimiter.Brace
                else -> null
            }
        val closeDelimiter =
            when (first) {
                ')'.code -> Delimiter.Parenthesis
                ']'.code -> Delimiter.Bracket
                '}'.code -> Delimiter.Brace
                else -> null
            }

        if (openDelimiter != null) {
            input = input.advance(1)
            stack.add(lo to (openDelimiter to tokens))
            tokens = TokenStreamBuilder.new()
        } else if (closeDelimiter != null) {
            val frame =
                stack.removeLastOrNull()
                    ?: return Result.failure(LexErrorException(LexError(lexError(input))))
            val (openLo, outerFrame) = frame
            val (openDelimiterFrame, outer) = outerFrame
            if (openDelimiterFrame != closeDelimiter) {
                return Result.failure(LexErrorException(LexError(lexError(input))))
            }
            input = input.advance(1)
            val group = FallbackGroup(openDelimiterFrame, tokens.build())
            group.setSpan(FallbackSpan(openLo, input.off))
            tokens = outer
            tokens.pushTokenFromParser(TokenTree.Group(Group.newFallback(group)))
        } else {
            val leaf = leafToken(input)
            if (leaf.isFailure) {
                return Result.failure(LexErrorException(LexError(lexError(input))))
            }
            val (rest, tt) = leaf.getOrThrow()
            tt.setSpan(Span.newFallback(FallbackSpan(lo, rest.off)))
            tokens.pushTokenFromParser(tt)
            input = rest
        }
    }
}

private fun lexError(cursor: Cursor): WrapperLexError = WrapperLexError.Fallback(FallbackLexError(FallbackSpan(cursor.off, cursor.off)))

private fun leafToken(input: Cursor): PResult<TokenTree> {
    val lit = literal(input)
    if (lit.isSuccess) {
        val (rest, l) = lit.getOrThrow()
        return Result.success(rest to TokenTree.Literal(Literal.newFallback(l)))
    }
    val punct = punct(input)
    if (punct.isSuccess) {
        val (rest, p) = punct.getOrThrow()
        return Result.success(rest to TokenTree.Punct(p))
    }
    val ident = ident(input)
    if (ident.isSuccess) {
        val (rest, i) = ident.getOrThrow()
        return Result.success(rest to TokenTree.Ident(i))
    }
    if (input.startsWith(ERROR)) {
        val rest = input.advance(ERROR.length)
        val repr = Literal.newFallback(FallbackLiteral.new(ERROR))
        return Result.success(rest to TokenTree.Literal(repr))
    }
    return reject()
}

private fun ident(input: Cursor): PResult<Ident> {
    val literalPrefixes = listOf("r\"", "r#\"", "r##", "b\"", "b'", "br\"", "br#", "c\"", "cr\"", "cr#")
    return if (literalPrefixes.any { input.startsWith(it) }) {
        reject()
    } else {
        identAny(input)
    }
}

private fun identAny(input: Cursor): PResult<Ident> {
    val raw = input.startsWith("r#")
    val rest = input.advance(if (raw) 2 else 0)
    val ident = identNotRaw(rest)
    if (ident.isFailure) {
        return reject()
    }
    val (afterIdent, sym) = ident.getOrThrow()
    if (!raw) {
        return Result.success(afterIdent to Ident.newFallback(FallbackIdent.newUnchecked(sym, FallbackSpan.callSite())))
    }
    if (sym in setOf("_", "super", "self", "Self", "crate")) {
        return reject()
    }
    return Result.success(afterIdent to Ident.newFallback(FallbackIdent.newRawUnchecked(sym, FallbackSpan.callSite())))
}

private fun identNotRaw(input: Cursor): PResult<String> {
    val length = input.len()
    if (length == 0) {
        return reject()
    }
    // Walk by Unicode scalar (not UTF-16 code unit) so supplementary-plane
    // identifier characters such as Egyptian Hieroglyphs are not split across
    // their surrogate halves and rejected.
    val (firstCp, firstWidth) = input.codePointAndWidthAt(0)
    if (!isIdentStartCodePoint(firstCp)) {
        return reject()
    }
    var end = firstWidth
    while (end < length) {
        val (cp, width) = input.codePointAndWidthAt(end)
        if (!isIdentContinueCodePoint(cp)) {
            break
        }
        end += width
    }
    return Result.success(input.advance(end) to input.slice(0, end))
}

internal fun literal(input: Cursor): PResult<FallbackLiteral> {
    val rest = literalNocapture(input)
    if (rest.isFailure) {
        return reject()
    }
    val cursor = rest.getOrThrow()
    val end = input.len() - cursor.len()
    return Result.success(cursor to FallbackLiteral.new(input.slice(0, end)))
}

private fun literalNocapture(input: Cursor): Result<Cursor> {
    val parsers = listOf(::string, ::byteString, ::cString, ::byte, ::character, ::float, ::int)
    for (parser in parsers) {
        val parsed = parser(input)
        if (parsed.isSuccess) {
            return parsed
        }
    }
    return reject()
}

private fun literalSuffix(input: Cursor): Cursor {
    val ident = identNotRaw(input)
    return ident.getOrNull()?.first ?: input
}

private fun string(input: Cursor): Result<Cursor> {
    val cooked = input.parse("\"")
    if (cooked.isSuccess) {
        return cookedString(cooked.getOrThrow())
    }
    val raw = input.parse("r")
    if (raw.isSuccess) {
        return rawString(raw.getOrThrow())
    }
    return reject()
}

private fun cookedString(inputCursor: Cursor): Result<Cursor> {
    var input = inputCursor
    var i = 0
    while (i < input.len()) {
        val ch = input.charAt(i)
        when (ch) {
            '"' -> return Result.success(literalSuffix(input.advance(i + 1)))
            '\r' -> {
                if (input.charOrNull(i + 1) != '\n') {
                    return reject()
                }
                i += 1
            }
            '\\' -> {
                val next = input.charOrNull(i + 1) ?: return reject()
                when (next) {
                    'x' -> {
                        if (backslashXChar(input, i + 2).isFailure) return reject()
                        i += 3
                    }
                    'n', 'r', 't', '\\', '\'', '"', '0' -> i += 1
                    'u' -> {
                        val end = backslashU(input, i + 2).getOrElse { return reject() }.second
                        i = end - 1
                    }
                    '\n', '\r' -> {
                        input = input.advance(i + 2)
                        val trailed = trailingBackslash(input, next)
                        if (trailed.isFailure) return reject()
                        input = trailed.getOrThrow()
                        i = -1
                    }
                    else -> return reject()
                }
            }
        }
        i += 1
    }
    return reject()
}

private fun rawString(input: Cursor): Result<Cursor> {
    val parsed = delimiterOfRawString(input)
    if (parsed.isFailure) return reject()
    val (body, delimiter) = parsed.getOrThrow()
    var i = 0
    val length = body.len()
    while (i < length) {
        val byte = body.charAt(i)
        when {
            byte == '"' && body.startsWithAt(i + 1, delimiter) -> {
                return Result.success(literalSuffix(body.advance(i + 1 + delimiter.length)))
            }
            byte == '\r' && body.charOrNull(i + 1) != '\n' -> return reject()
        }
        i += 1
    }
    return reject()
}

private fun byteString(input: Cursor): Result<Cursor> {
    val cooked = input.parse("b\"")
    if (cooked.isSuccess) {
        return cookedByteString(cooked.getOrThrow())
    }
    val raw = input.parse("br")
    if (raw.isSuccess) {
        return rawByteString(raw.getOrThrow())
    }
    return reject()
}

private fun cookedByteString(inputCursor: Cursor): Result<Cursor> {
    var input = inputCursor
    var offset = 0
    while (offset < input.len()) {
        when (val ch = input.charAt(offset)) {
            '"' -> return Result.success(literalSuffix(input.advance(offset + 1)))
            '\r' -> {
                if (input.charOrNull(offset + 1) != '\n') return reject()
                offset += 1
            }
            '\\' -> {
                val next = input.charOrNull(offset + 1) ?: return reject()
                when (next) {
                    'x' -> {
                        if (backslashXByte(input, offset + 2).isFailure) return reject()
                        offset += 3
                    }
                    'n', 'r', 't', '\\', '0', '\'', '"' -> offset += 1
                    '\n', '\r' -> {
                        input = input.advance(offset + 2)
                        val trailed = trailingBackslash(input, next)
                        if (trailed.isFailure) return reject()
                        input = trailed.getOrThrow()
                        offset = -1
                    }
                    else -> return reject()
                }
            }
            else -> {
                if (ch.code !in 0..0x7f) return reject()
            }
        }
        offset += 1
    }
    return reject()
}

private fun delimiterOfRawString(input: Cursor): PResult<String> {
    val length = input.len()
    for (i in 0 until length) {
        when (input.charAt(i)) {
            '"' -> {
                if (i > 255) {
                    return reject()
                }
                return Result.success(input.advance(i + 1) to input.slice(0, i))
            }
            '#' -> Unit
            else -> return reject()
        }
    }
    return reject()
}

private fun rawByteString(input: Cursor): Result<Cursor> {
    val parsed = delimiterOfRawString(input)
    if (parsed.isFailure) return reject()
    val (body, delimiter) = parsed.getOrThrow()
    var i = 0
    val length = body.len()
    while (i < length) {
        val ch = body.charAt(i)
        when {
            ch == '"' && body.startsWithAt(i + 1, delimiter) -> {
                return Result.success(literalSuffix(body.advance(i + 1 + delimiter.length)))
            }
            ch == '\r' && body.charOrNull(i + 1) != '\n' -> return reject()
            ch.code !in 0..0x7f -> return reject()
        }
        i += 1
    }
    return reject()
}

private fun cString(input: Cursor): Result<Cursor> {
    val cooked = input.parse("c\"")
    if (cooked.isSuccess) {
        return cookedCString(cooked.getOrThrow())
    }
    val raw = input.parse("cr")
    if (raw.isSuccess) {
        return rawCString(raw.getOrThrow())
    }
    return reject()
}

private fun rawCString(input: Cursor): Result<Cursor> {
    val parsed = delimiterOfRawString(input)
    if (parsed.isFailure) return reject()
    val (body, delimiter) = parsed.getOrThrow()
    var i = 0
    val length = body.len()
    while (i < length) {
        val byte = body.charAt(i)
        when {
            byte == '"' && body.startsWithAt(i + 1, delimiter) -> {
                return Result.success(literalSuffix(body.advance(i + 1 + delimiter.length)))
            }
            byte == '\r' && body.charOrNull(i + 1) != '\n' -> return reject()
            byte == '\u0000' -> return reject()
        }
        i += 1
    }
    return reject()
}

private fun cookedCString(inputCursor: Cursor): Result<Cursor> {
    var input = inputCursor
    var i = 0
    while (i < input.len()) {
        val ch = input.charAt(i)
        when (ch) {
            '"' -> return Result.success(literalSuffix(input.advance(i + 1)))
            '\r' -> {
                if (input.charOrNull(i + 1) != '\n') return reject()
                i += 1
            }
            '\\' -> {
                val next = input.charOrNull(i + 1) ?: return reject()
                when (next) {
                    'x' -> {
                        if (backslashXNonzero(input, i + 2).isFailure) return reject()
                        i += 3
                    }
                    'n', 'r', 't', '\\', '\'', '"' -> i += 1
                    'u' -> {
                        val (codePoint, end) = backslashU(input, i + 2).getOrElse { return reject() }
                        if (codePoint == 0) return reject()
                        i = end - 1
                    }
                    '\n', '\r' -> {
                        input = input.advance(i + 2)
                        val trailed = trailingBackslash(input, next)
                        if (trailed.isFailure) return reject()
                        input = trailed.getOrThrow()
                        i = -1
                    }
                    else -> return reject()
                }
            }
            '\u0000' -> return reject()
        }
        i += 1
    }
    return reject()
}

private fun byte(input: Cursor): Result<Cursor> {
    val parsed = input.parse("b'")
    if (parsed.isFailure) return reject()
    val body = parsed.getOrThrow()
    if (body.isEmpty()) return reject()
    val first = body.charAt(0)
    var offset =
        if (first == '\\') {
            when (body.charOrNull(1)) {
                'x' -> {
                    if (backslashXByte(body, 2).isFailure) return reject()
                    4
                }
                'n', 'r', 't', '\\', '0', '\'', '"' -> 2
                else -> return reject()
            }
        } else {
            if (first.code !in 0..0x7f) return reject()
            1
        }
    if (body.charOrNull(offset) != '\'') {
        return reject()
    }
    offset += 1
    return Result.success(literalSuffix(body.advance(offset)))
}

private fun character(input: Cursor): Result<Cursor> {
    val parsed = input.parse("'")
    if (parsed.isFailure) return reject()
    val body = parsed.getOrThrow()
    if (body.isEmpty()) return reject()
    var idx =
        if (body.charAt(0) == '\\') {
            when (body.charOrNull(1)) {
                'x' -> {
                    if (backslashXChar(body, 2).isFailure) return reject()
                    4
                }
                'u' -> backslashU(body, 2).getOrElse { return reject() }.second
                'n', 'r', 't', '\\', '0', '\'', '"' -> 2
                else -> return reject()
            }
        } else {
            val (cp, width) = body.codePointAndWidthAt(0)
            if (cp in 0xD800..0xDFFF) return reject()
            width
        }
    if (body.charOrNull(idx) != '\'') {
        return reject()
    }
    idx += 1
    return Result.success(literalSuffix(body.advance(idx)))
}

private fun backslashXChar(chars: Cursor, start: Int): Result<Unit> {
    val first = chars.charOrNull(start) ?: return reject()
    val second = chars.charOrNull(start + 1) ?: return reject()
    return if (first in '0'..'7' && second.isHexDigit()) Result.success(Unit) else reject()
}

private fun backslashXByte(chars: Cursor, start: Int): Result<Unit> {
    val first = chars.charOrNull(start) ?: return reject()
    val second = chars.charOrNull(start + 1) ?: return reject()
    return if (first.isHexDigit() && second.isHexDigit()) Result.success(Unit) else reject()
}

private fun backslashXNonzero(chars: Cursor, start: Int): Result<Unit> {
    val first = chars.charOrNull(start) ?: return reject()
    val second = chars.charOrNull(start + 1) ?: return reject()
    return if (first.isHexDigit() && second.isHexDigit() && !(first == '0' && second == '0')) {
        Result.success(Unit)
    } else {
        reject()
    }
}

// Returns the decoded Unicode scalar value (as an Int code point, since Kotlin's
// Char cannot hold supplementary-plane scalars such as U+10FFFF) together with the
// index just past the closing brace. Surrogates and out-of-range scalars are
// rejected, matching the set of escapes the Rust lexer accepts.
private fun backslashU(chars: Cursor, start: Int): Result<Pair<Int, Int>> {
    if (chars.charOrNull(start) != '{') {
        return reject()
    }
    var value = 0
    var len = 0
    var index = start + 1
    val length = chars.len()
    while (index < length) {
        val ch = chars.charAt(index)
        val digit =
            when {
                ch in '0'..'9' -> ch.code - '0'.code
                ch in 'a'..'f' -> 10 + ch.code - 'a'.code
                ch in 'A'..'F' -> 10 + ch.code - 'A'.code
                ch == '_' && len > 0 -> {
                    index += 1
                    continue
                }
                ch == '}' && len > 0 -> {
                    return if (value > 0x10FFFF || value in 0xD800..0xDFFF) {
                        reject()
                    } else {
                        Result.success(value to (index + 1))
                    }
                }
                else -> return reject()
            }
        if (len == 6) {
            return reject()
        }
        value = value * 0x10 + digit
        len += 1
        index += 1
    }
    return reject()
}

private fun trailingBackslash(input: Cursor, lastInput: Char): Result<Cursor> {
    var last = lastInput
    var index = 0
    while (true) {
        if (last == '\r' && input.charOrNull(index) != '\n') {
            return reject()
        }
        val ch = input.charOrNull(index) ?: return reject()
        if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
            last = ch
            index += 1
        } else {
            return Result.success(input.advance(index))
        }
    }
}

private fun float(input: Cursor): Result<Cursor> {
    var rest = floatDigits(input).getOrElse { return reject() }
    if (!rest.isEmpty() && isIdentStartCodePoint(rest.codePointAndWidthAt(0).first)) {
        rest = identNotRaw(rest).getOrElse { return reject() }.first
    }
    return wordBreak(rest)
}

private fun floatDigits(input: Cursor): Result<Cursor> {
    val length = input.len()
    if (input.charOrNull(0)?.isDigit() != true) {
        return reject()
    }
    var len = 1
    var hasDot = false
    var hasExp = false
    while (len < length) {
        when (input.charAt(len)) {
            in '0'..'9', '_' -> len += 1
            '.' -> {
                if (hasDot) break
                if (
                    len + 1 < length &&
                    (input.charAt(len + 1) == '.' || isIdentStartCodePoint(input.codePointAndWidthAt(len + 1).first))
                ) {
                    return reject()
                }
                hasDot = true
                len += 1
            }
            'e', 'E' -> {
                hasExp = true
                len += 1
                break
            }
            else -> break
        }
    }
    if (!hasDot && !hasExp) {
        return reject()
    }
    if (hasExp) {
        val tokenBeforeExp = if (hasDot) Result.success(input.advance(len - 1)) else reject()
        var hasSign = false
        var hasExpValue = false
        while (len < length) {
            when (input.charAt(len)) {
                '+', '-' -> {
                    if (hasExpValue) break
                    if (hasSign) return tokenBeforeExp
                    hasSign = true
                    len += 1
                }
                in '0'..'9' -> {
                    hasExpValue = true
                    len += 1
                }
                '_' -> len += 1
                else -> break
            }
        }
        if (!hasExpValue) {
            return tokenBeforeExp
        }
    }
    return Result.success(input.advance(len))
}

private fun int(input: Cursor): Result<Cursor> {
    var rest = digits(input).getOrElse { return reject() }
    if (!rest.isEmpty() && isIdentStartCodePoint(rest.codePointAndWidthAt(0).first)) {
        rest = identNotRaw(rest).getOrElse { return reject() }.first
    }
    return wordBreak(rest)
}

private fun digits(inputCursor: Cursor): Result<Cursor> {
    var input = inputCursor
    val base =
        if (input.startsWith("0x")) {
            input = input.advance(2)
            16
        } else if (input.startsWith("0o")) {
            input = input.advance(2)
            8
        } else if (input.startsWith("0b")) {
            input = input.advance(2)
            2
        } else {
            10
        }
    var len = 0
    var empty = true
    val length = input.len()
    for (idx in 0 until length) {
        val byte = input.charAt(idx).code
        when (byte) {
            in '0'.code..'9'.code -> {
                val digit = byte - '0'.code
                if (digit >= base) return reject()
            }
            in 'a'.code..'f'.code -> {
                val digit = 10 + byte - 'a'.code
                if (digit >= base) break
            }
            in 'A'.code..'F'.code -> {
                val digit = 10 + byte - 'A'.code
                if (digit >= base) break
            }
            '_'.code -> {
                if (empty && base == 10) return reject()
                len += 1
                continue
            }
            else -> break
        }
        len += 1
        empty = false
    }
    return if (empty) reject() else Result.success(input.advance(len))
}

private fun punct(input: Cursor): PResult<Punct> {
    val parsed = punctChar(input)
    if (parsed.isFailure) return reject()
    val (rest, ch) = parsed.getOrThrow()
    if (ch == '\'') {
        val afterLifetime = identAny(rest)
        if (afterLifetime.isFailure) return reject()
        val after = afterLifetime.getOrThrow().first
        return if (after.startsWithChar('\'') || (after.startsWithChar('#') && !rest.startsWith("r#"))) {
            reject()
        } else {
            Result.success(rest to Punct('\'', Spacing.Joint))
        }
    }
    val kind = if (punctChar(rest).isSuccess) Spacing.Joint else Spacing.Alone
    return Result.success(rest to Punct(ch, kind))
}

private fun punctChar(input: Cursor): PResult<Char> {
    if (input.startsWith("//") || input.startsWith("/*")) {
        return reject()
    }
    val first = input.charOrNull(0) ?: return reject()
    return if (first in "~!@#$%^&*-=+|;:,<.>/?'") {
        Result.success(input.advance(1) to first)
    } else {
        reject()
    }
}

private fun docComment(input: Cursor, tokens: TokenStreamBuilder): PResult<Unit> {
    val lo = input.off
    val contents = docCommentContents(input)
    if (contents.isFailure) return reject()
    val (rest, pair) = contents.getOrThrow()
    val (comment, inner) = pair
    val fallbackSpan = FallbackSpan(lo, rest.off)
    val span = Span.newFallback(fallbackSpan)

    var cr = comment.indexOf('\r')
    while (cr >= 0) {
        if (comment.getOrNull(cr + 1) != '\n') {
            return reject()
        }
        cr = comment.indexOf('\r', startIndex = cr + 1)
    }

    val pound = Punct('#', Spacing.Alone)
    pound.setSpan(span)
    tokens.pushTokenFromParser(TokenTree.Punct(pound))

    if (inner) {
        val bang = Punct('!', Spacing.Alone)
        bang.setSpan(span)
        tokens.pushTokenFromParser(TokenTree.Punct(bang))
    }

    val docIdent = Ident.newFallback(FallbackIdent.newUnchecked("doc", fallbackSpan))
    val equal = Punct('=', Spacing.Alone)
    equal.setSpan(span)
    val literal = Literal.newFallback(FallbackLiteral.string(comment))
    literal.setSpan(span)
    val bracketed = TokenStreamBuilder.withCapacity(3)
    bracketed.pushTokenFromParser(TokenTree.Ident(docIdent))
    bracketed.pushTokenFromParser(TokenTree.Punct(equal))
    bracketed.pushTokenFromParser(TokenTree.Literal(literal))
    val group = FallbackGroup(Delimiter.Bracket, bracketed.build())
    val publicGroup = Group.newFallback(group)
    publicGroup.setSpan(span)
    tokens.pushTokenFromParser(TokenTree.Group(publicGroup))
    return Result.success(rest to Unit)
}

private fun docCommentContents(input: Cursor): PResult<Pair<String, Boolean>> {
    return when {
        input.startsWith("//!") -> {
            val advanced = input.advance(3)
            val (rest, s) = takeUntilNewlineOrEof(advanced)
            Result.success(rest to (s to true))
        }
        input.startsWith("/*!") -> {
            val block = blockComment(input).getOrElse { return reject() }
            Result.success(block.first to (block.second.substring(3, block.second.length - 2) to true))
        }
        input.startsWith("///") -> {
            val advanced = input.advance(3)
            if (advanced.startsWithChar('/')) {
                reject()
            } else {
                val (rest, s) = takeUntilNewlineOrEof(advanced)
                Result.success(rest to (s to false))
            }
        }
        input.startsWith("/**") && input.charOrNull(3) != '*' -> {
            val block = blockComment(input).getOrElse { return reject() }
            Result.success(block.first to (block.second.substring(3, block.second.length - 2) to false))
        }
        else -> reject()
    }
}

private fun takeUntilNewlineOrEof(input: Cursor): Pair<Cursor, String> {
    val length = input.len()
    for (i in 0 until length) {
        val ch = input.charAt(i)
        if (ch == '\n') {
            return input.advance(i) to input.slice(0, i)
        } else if (ch == '\r' && input.charOrNull(i + 1) == '\n') {
            return input.advance(i + 1) to input.slice(0, i)
        }
    }
    return input.advance(length) to input.slice(0, length)
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
