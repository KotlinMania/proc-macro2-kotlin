// port-lint: tests tests/test_size.rs
package io.github.kotlinmania.procmacro2

// test_size.rs asserts mem::size_of on TokenStream, LexError, Span, Group,
// Punct, Ident, Literal, and TokenTree; Kotlin/KMP has no equivalent
// layout-size guarantee (object layout is JVM/JS/Native-specific and not
// under the program's control), so these assertions do not port.
internal object TestSizeTestPlaceholder
