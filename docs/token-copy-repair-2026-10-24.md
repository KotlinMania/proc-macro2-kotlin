# Token-copy repair — active

Rust proc-macro2 derives Clone for token trees, groups, identifiers and literals.
Syn's Rust buffer returns clones. Kotlin's buffer currently returns shared mutable
objects. A returned token's setSpan therefore changes the buffer. The initial
four-token Rust/Kotlin probe and new Syn regressions reproduce this difference.

This checkpoint adds the missing internal FallbackIdent.clone using its exact
symbol, raw-identifier flag and span. It reuses the existing FallbackLiteral.clone
and tests both operations, including negative/suffixed/raw literal representations.
All 63 JVM and 63 macOS tests pass. This primitive does **not** yet repair public
TokenTree, Group or TokenStream copies or Syn's cursor.

The compiler-backed branch has a suitable existing implementation in
`proc-macro-kotlin/src/commonMain/kotlin/io/github/kotlinmania/procmacro/bridge/Client.kt`:
private TokenTree.deepClone (line 269), called by internal BridgeMethods.tsClone
(line 123). It retains the literal kind/symbol/suffix, raw identifier, punctuation
spacing, separate delimiter spans and nested token stream. Calling that existing
implementation needs a small public facade in that dependency; exposing it is a
separate, explicitly requested scope extension. No facade or workaround has been
applied. Copying by text or discarding compiler span provenance is not an option.

Evidence: `rust-kotlin/tmp/token-copy-repair-tkw9djkt/` contains initial source/hash
and dirty-state receipts, saved JVM/macOS XML, `foundation-validation.json`, the
three failing Syn regressions, original Rust oracle output, and the un-applied
`proc-macro-facade-proposal.patch`. The existing dirty build.gradle.kts and
gradle/libs.versions.toml remain byte-identical and are excluded from this commit.
No settings, publication, remote CI or other sibling sources were changed.
