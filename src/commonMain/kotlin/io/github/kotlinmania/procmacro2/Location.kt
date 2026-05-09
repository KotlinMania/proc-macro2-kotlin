// port-lint: source location.rs
package io.github.kotlinmania.procmacro2

/**
 * A line-column pair representing the start or end of a `Span`.
 *
 * This type is semver exempt and not exposed by default.
 */
data class LineColumn(
    /**
     * The 1-indexed line in the source file on which the span starts or ends
     * (inclusive).
     */
    val line: Int,
    /**
     * The 0-indexed column (in UTF-8 characters) in the source file on which
     * the span starts or ends (inclusive).
     */
    val column: Int,
) : Comparable<LineColumn> {
    fun cmp(other: LineColumn): Int {
        val lineCmp = line.compareTo(other.line)
        if (lineCmp != 0) {
            return lineCmp
        }
        return column.compareTo(other.column)
    }

    fun partialCmp(other: LineColumn): Int? = cmp(other)

    override fun compareTo(other: LineColumn): Int = cmp(other)
}
