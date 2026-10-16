// port-lint: source tests/test_size.rs
package io.github.kotlinmania.procmacro2

// TestSize asserts memory size on TokenStream, LexError, Span, Group,
// Punct, Ident, Literal, and TokenTree; Kotlin Multiplatform has no equivalent
// layout size guarantee (object layout is runtime-specific and not
// under the program's control), so these assertions do not port.
internal object TestSizeTestPlaceholder
