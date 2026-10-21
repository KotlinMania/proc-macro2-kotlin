package io.github.kotlinmania.procmacro2

/** Prints the token stream in a form convenient for debugging. */
fun TokenStream.debug(alternate: Boolean = false): String = DebugFormatter(alternate).stream(this, 0)

/** Prints the token tree in a form convenient for debugging. */
fun TokenTree.debug(alternate: Boolean = false): String = DebugFormatter(alternate).tree(this, 0)

/** Prints the group in a form convenient for debugging. */
fun Group.debug(alternate: Boolean = false): String = DebugFormatter(alternate).group(this, 0)

/** Prints the punctuation and its spacing in a form convenient for debugging. */
fun Punct.debug(alternate: Boolean = false): String = DebugFormatter(alternate).punct(this, 0)

/** Prints the identifier in a form convenient for debugging. */
fun Ident.debug(alternate: Boolean = false): String = DebugFormatter(alternate).ident(this, 0)

/** Prints the literal in a form convenient for debugging. */
fun Literal.debug(alternate: Boolean = false): String = DebugFormatter(alternate).literal(this, 0)

private class DebugFormatter(
    private val alternate: Boolean,
) {
    fun stream(value: TokenStream, depth: Int): String {
        val entries = value.clone().map { tree(it, depth + 1) }
        if (entries.isEmpty()) return "TokenStream []"
        return if (alternate) {
            "TokenStream [\n" + entries.joinToString("") { indent(depth + 1) + it + ",\n" } + indent(depth) + "]"
        } else {
            "TokenStream [" + entries.joinToString(", ") + "]"
        }
    }

    fun tree(value: TokenTree, depth: Int): String =
        when (value) {
            is TokenTree.Group -> group(value.value, depth)
            is TokenTree.Ident -> ident(value.value, depth)
            is TokenTree.Punct -> punct(value.value, depth)
            is TokenTree.Literal -> literal(value.value, depth)
        }

    fun group(value: Group, depth: Int): String =
        struct("Group", listOf("delimiter" to value.delimiter().name, "stream" to stream(value.stream(), depth + 1)) +
            spanField(value.span()), depth)

    fun punct(value: Punct, depth: Int): String {
        val char = value.asChar().toString().replace("\\", "\\\\").replace("'", "\\'")
        return struct("Punct", listOf("char" to "'$char'", "spacing" to value.spacing().name) + spanField(value.span()), depth)
    }

    fun ident(value: Ident, depth: Int): String =
        struct("Ident", listOf("sym" to value.toString()) + spanField(value.span()), depth)

    fun literal(value: Literal, depth: Int): String =
        struct("Literal", listOf("lit" to value.toString()) + spanField(value.span()), depth)

    private fun spanField(span: Span): List<Pair<String, String>> =
        when (val inner = span.inner) {
            is WrapperSpan.Compiler -> listOf("span" to inner.span.toString())
            is WrapperSpan.Fallback -> debugSpanFieldIfNontrivial(inner.span)?.let { listOf("span" to it) } ?: emptyList()
        }

    private fun struct(name: String, fields: List<Pair<String, String>>, depth: Int): String =
        if (alternate) {
            "$name {\n" + fields.joinToString("") { (key, value) -> indent(depth + 1) + "$key: $value,\n" } + indent(depth) + "}"
        } else {
            "$name { " + fields.joinToString(", ") { (key, value) -> "$key: $value" } + " }"
        }

    private fun indent(depth: Int): String = "    ".repeat(depth)
}
