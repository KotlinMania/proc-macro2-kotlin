// port-lint: source parse.rs
package io.github.kotlinmania.procmacro2

internal data class Cursor(
    val rest: String,
    val off: Int,
) {
    fun advance(bytes: Int): Cursor {
        val front = rest.take(bytes)
        return Cursor(
            rest = rest.drop(bytes),
            off = off + front.length,
        )
    }

    fun startsWith(s: String): Boolean = rest.startsWith(s)

    fun startsWithChar(ch: Char): Boolean = rest.firstOrNull() == ch

    fun startsWithFn(f: (Char) -> Boolean): Boolean = rest.firstOrNull()?.let(f) == true

    fun isEmpty(): Boolean = rest.isEmpty()

    fun len(): Int = rest.length

    fun asBytes(): ByteArray = rest.encodeToByteArray()

    fun bytes(): List<Int> = asBytes().map { it.toInt() and 0xff }

    fun chars(): Iterable<Char> = rest.asIterable()

    fun charIndices(): List<Pair<Int, Char>> = rest.mapIndexed { index, ch -> index to ch }

    fun parse(tag: String): Result<Cursor> =
        if (startsWith(tag)) {
            Result.success(advance(tag.length))
        } else {
            reject()
        }
}

private object Reject : Exception()

private typealias PResult<O> = Result<Pair<Cursor, O>>

private fun <T> reject(): Result<T> = Result.failure(Reject)

private fun skipWhitespace(input: Cursor): Cursor {
    var s = input
    while (!s.isEmpty()) {
        val byte = s.asBytes()[0].toInt() and 0xff
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
                val ch = s.rest.first()
                if (isWhitespace(ch)) {
                    s = s.advance(ch.toString().encodeToByteArray().size)
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
    // is character-based, so a byte index would over-run input.rest once the comment
    // contains any multibyte character. The `/` and `*` markers are always ASCII.
    val chars = input.rest
    var i = 0
    val upper = chars.length - 1
    while (i < upper) {
        if (chars[i] == '/' && chars[i + 1] == '*') {
            depth += 1
            i += 1
        } else if (chars[i] == '*' && chars[i + 1] == '/') {
            depth -= 1
            if (depth == 0) {
                return Result.success(input.advance(i + 2) to chars.substring(0, i + 2))
            }
            i += 1
        }
        i += 1
    }
    return reject()
}

private fun isWhitespace(ch: Char): Boolean = ch.isWhitespace() || ch == '\u200e' || ch == '\u200f'

private fun wordBreak(input: Cursor): Result<Cursor> =
    when (val ch = input.rest.firstOrNull()) {
        null -> Result.success(input)
        else -> if (isIdentContinue(ch)) reject() else Result.success(input)
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
        val first = input.bytes().firstOrNull()
        if (first == null) {
            return if (stack.isEmpty()) {
                Result.success(tokens.build())
            } else {
                Result.failure(LexError(FallbackLexError(FallbackSpan(stack.last().first, stack.last().first))))
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
            val frame = stack.removeLastOrNull() ?: return Result.failure(LexError(lexError(input)))
            val (openLo, outerFrame) = frame
            val (openDelimiterFrame, outer) = outerFrame
            if (openDelimiterFrame != closeDelimiter) {
                return Result.failure(LexError(lexError(input)))
            }
            input = input.advance(1)
            val group = FallbackGroup(openDelimiterFrame, tokens.build())
            group.setSpan(FallbackSpan(openLo, input.off))
            tokens = outer
            tokens.pushTokenFromParser(TokenTree.Group(Group.newFallback(group)))
        } else {
            val leaf = leafToken(input)
            if (leaf.isFailure) {
                return Result.failure(LexError(lexError(input)))
            }
            val (rest, tt) = leaf.getOrThrow()
            tt.setSpan(Span.newFallback(FallbackSpan(lo, rest.off)))
            tokens.pushTokenFromParser(tt)
            input = rest
        }
    }
}

private fun lexError(cursor: Cursor): FallbackLexError = FallbackLexError(FallbackSpan(cursor.off, cursor.off))

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
    val chars = input.charIndices()
    val first = chars.firstOrNull()
    if (first == null || !isIdentStart(first.second)) {
        return reject()
    }
    var end = input.len()
    for ((i, ch) in chars.drop(1)) {
        if (!isIdentContinue(ch)) {
            end = i
            break
        }
    }
    return Result.success(input.advance(end) to input.rest.substring(0, end))
}

internal fun literal(input: Cursor): PResult<FallbackLiteral> {
    val rest = literalNocapture(input)
    if (rest.isFailure) {
        return reject()
    }
    val cursor = rest.getOrThrow()
    val end = input.len() - cursor.len()
    return Result.success(cursor to FallbackLiteral.new(input.rest.substring(0, end)))
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
    while (i < input.rest.length) {
        val ch = input.rest[i]
        when (ch) {
            '"' -> return Result.success(literalSuffix(input.advance(i + 1)))
            '\r' -> {
                if (input.rest.getOrNull(i + 1) != '\n') {
                    return reject()
                }
                i += 1
            }
            '\\' -> {
                val next = input.rest.getOrNull(i + 1) ?: return reject()
                when (next) {
                    'x' -> {
                        if (backslashXChar(input.rest, i + 2).isFailure) return reject()
                        i += 3
                    }
                    'n', 'r', 't', '\\', '\'', '"', '0' -> i += 1
                    'u' -> {
                        val end = backslashU(input.rest, i + 2).getOrElse { return reject() }.second
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
    while (i < body.rest.length) {
        val byte = body.rest[i]
        when {
            byte == '"' && body.rest.drop(i + 1).startsWith(delimiter) -> {
                return Result.success(literalSuffix(body.advance(i + 1 + delimiter.length)))
            }
            byte == '\r' && body.rest.getOrNull(i + 1) != '\n' -> return reject()
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
    // `bytes` must track `input`: a string-continuation escape advances `input`, so
    // the byte view has to be recomputed afterwards (otherwise the loop keeps reading
    // the pre-continuation bytes and mis-parses the remainder).
    var bytes = input.bytes()
    while (offset < bytes.size) {
        when (val b = bytes[offset]) {
            '"'.code -> return Result.success(literalSuffix(input.advance(offset + 1)))
            '\r'.code -> if (bytes.getOrNull(offset + 1) != '\n'.code) return reject() else offset += 1
            '\\'.code -> {
                val next = bytes.getOrNull(offset + 1) ?: return reject()
                when (next) {
                    'x'.code -> {
                        if (backslashXByte(bytes, offset + 2).isFailure) return reject()
                        offset += 3
                    }
                    'n'.code, 'r'.code, 't'.code, '\\'.code, '0'.code, '\''.code, '"'.code -> offset += 1
                    '\n'.code, '\r'.code -> {
                        input = input.advance(offset + 2)
                        val trailed = trailingBackslash(input, next.toChar())
                        if (trailed.isFailure) return reject()
                        input = trailed.getOrThrow()
                        bytes = input.bytes()
                        offset = -1
                    }
                    else -> return reject()
                }
            }
            else -> if (b !in 0..0x7f) return reject()
        }
        offset += 1
    }
    return reject()
}

private fun delimiterOfRawString(input: Cursor): PResult<String> {
    for ((i, byte) in input.bytes().withIndex()) {
        when (byte) {
            '"'.code -> {
                if (i > 255) {
                    return reject()
                }
                return Result.success(input.advance(i + 1) to input.rest.substring(0, i))
            }
            '#'.code -> Unit
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
    val bytes = body.bytes()
    while (i < bytes.size) {
        val byte = bytes[i]
        when {
            byte == '"'.code && body.rest.drop(i + 1).startsWith(delimiter) -> {
                return Result.success(literalSuffix(body.advance(i + 1 + delimiter.length)))
            }
            byte == '\r'.code && bytes.getOrNull(i + 1) != '\n'.code -> return reject()
            byte !in 0..0x7f -> return reject()
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
    while (i < body.rest.length) {
        val byte = body.rest[i]
        when {
            byte == '"' && body.rest.drop(i + 1).startsWith(delimiter) -> {
                return Result.success(literalSuffix(body.advance(i + 1 + delimiter.length)))
            }
            byte == '\r' && body.rest.getOrNull(i + 1) != '\n' -> return reject()
            byte == '\u0000' -> return reject()
        }
        i += 1
    }
    return reject()
}

private fun cookedCString(inputCursor: Cursor): Result<Cursor> {
    var input = inputCursor
    var i = 0
    while (i < input.rest.length) {
        val ch = input.rest[i]
        when (ch) {
            '"' -> return Result.success(literalSuffix(input.advance(i + 1)))
            '\r' -> if (input.rest.getOrNull(i + 1) != '\n') return reject() else i += 1
            '\\' -> {
                val next = input.rest.getOrNull(i + 1) ?: return reject()
                when (next) {
                    'x' -> {
                        if (backslashXNonzero(input.rest, i + 2).isFailure) return reject()
                        i += 3
                    }
                    'n', 'r', 't', '\\', '\'', '"' -> i += 1
                    'u' -> {
                        val (codePoint, end) = backslashU(input.rest, i + 2).getOrElse { return reject() }
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
    val bytes = body.bytes()
    if (bytes.isEmpty()) return reject()
    var offset =
        if (bytes[0] == '\\'.code) {
            when (bytes.getOrNull(1)) {
                'x'.code -> {
                    if (backslashXByte(bytes, 2).isFailure) return reject()
                    4
                }
                'n'.code, 'r'.code, 't'.code, '\\'.code, '0'.code, '\''.code, '"'.code -> 2
                else -> return reject()
            }
        } else {
            1
        }
    if (body.rest.getOrNull(offset) != '\'') {
        return reject()
    }
    offset += 1
    return Result.success(literalSuffix(body.advance(offset)))
}

private fun character(input: Cursor): Result<Cursor> {
    val parsed = input.parse("'")
    if (parsed.isFailure) return reject()
    val body = parsed.getOrThrow()
    val chars = body.rest
    if (chars.isEmpty()) return reject()
    var idx =
        if (chars[0] == '\\') {
            when (chars.getOrNull(1)) {
                'x' -> {
                    if (backslashXChar(chars, 2).isFailure) return reject()
                    4
                }
                'u' -> backslashU(chars, 2).getOrElse { return reject() }.second
                'n', 'r', 't', '\\', '0', '\'', '"' -> 2
                else -> return reject()
            }
        } else {
            1
        }
    if (body.rest.getOrNull(idx) != '\'') {
        return reject()
    }
    idx += 1
    return Result.success(literalSuffix(body.advance(idx)))
}

private fun backslashXChar(chars: String, start: Int): Result<Unit> {
    val first = chars.getOrNull(start) ?: return reject()
    val second = chars.getOrNull(start + 1) ?: return reject()
    return if (first in '0'..'7' && second.isHexDigit()) Result.success(Unit) else reject()
}

private fun backslashXByte(bytes: List<Int>, start: Int): Result<Unit> {
    val first = bytes.getOrNull(start)?.toChar() ?: return reject()
    val second = bytes.getOrNull(start + 1)?.toChar() ?: return reject()
    return if (first.isHexDigit() && second.isHexDigit()) Result.success(Unit) else reject()
}

private fun backslashXNonzero(chars: String, start: Int): Result<Unit> {
    val first = chars.getOrNull(start) ?: return reject()
    val second = chars.getOrNull(start + 1) ?: return reject()
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
private fun backslashU(chars: String, start: Int): Result<Pair<Int, Int>> {
    if (chars.getOrNull(start) != '{') {
        return reject()
    }
    var value = 0
    var len = 0
    var index = start + 1
    while (index < chars.length) {
        val ch = chars[index]
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
        if (last == '\r' && input.rest.getOrNull(index) != '\n') {
            return reject()
        }
        val ch = input.rest.getOrNull(index) ?: return reject()
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
    val ch = rest.rest.firstOrNull()
    if (ch != null && isIdentStart(ch)) {
        rest = identNotRaw(rest).getOrElse { return reject() }.first
    }
    return wordBreak(rest)
}

private fun floatDigits(input: Cursor): Result<Cursor> {
    val chars = input.rest
    if (chars.firstOrNull()?.isDigit() != true) {
        return reject()
    }
    var len = 1
    var hasDot = false
    var hasExp = false
    while (len < chars.length) {
        when (val ch = chars[len]) {
            in '0'..'9', '_' -> len += 1
            '.' -> {
                if (hasDot) break
                if (chars.getOrNull(len + 1)?.let { it == '.' || isIdentStart(it) } == true) {
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
        while (len < chars.length) {
            when (chars[len]) {
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
    val ch = rest.rest.firstOrNull()
    if (ch != null && isIdentStart(ch)) {
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
    for (byte in input.bytes()) {
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
    val first = input.rest.firstOrNull() ?: return reject()
    return if (first in "~!@#$%^&*-=+|;:,<.>/?'") {
        Result.success(input.advance(first.toString().encodeToByteArray().size) to first)
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

    var scanForBareCr = comment
    while (true) {
        val cr = scanForBareCr.indexOf('\r')
        if (cr < 0) break
        val after = scanForBareCr.substring(cr + 1)
        if (!after.startsWith('\n')) {
            return reject()
        }
        scanForBareCr = after
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
        input.startsWith("/**") && !input.rest.drop(3).startsWith('*') -> {
            val block = blockComment(input).getOrElse { return reject() }
            Result.success(block.first to (block.second.substring(3, block.second.length - 2) to false))
        }
        else -> reject()
    }
}

private fun takeUntilNewlineOrEof(input: Cursor): Pair<Cursor, String> {
    for ((i, ch) in input.charIndices()) {
        if (ch == '\n') {
            return input.advance(i) to input.rest.substring(0, i)
        } else if (ch == '\r' && input.rest.drop(i + 1).startsWith('\n')) {
            return input.advance(i + 1) to input.rest.substring(0, i)
        }
    }
    return input.advance(input.len()) to input.rest
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
