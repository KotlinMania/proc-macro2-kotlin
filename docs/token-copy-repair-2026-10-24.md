# Lossless token-copy repair

Rust's Syn buffer returns cloned tokens. Previously Kotlin returned shared mutable
objects; changing a returned token's span changed the buffer. This is corrected
through public `cloneToken()` operations for TokenTree, Group, Ident, Literal and
Punct, independent TokenStream clones, and independent streams returned by Group.

Fallback copies retain exact identifier/raw flag, literal representation, spacing,
spans and nested token data. Stream copying uses the stored token representation;
it does not run insertion normalization or split/reparse negative literals. The
compiler branch calls the existing bridge deep-copy implementation through the
new `proc-macro-kotlin` facade, preserving separate delimiter spans and backend
provenance. No lexer or parser is added or changed.

The name `cloneToken` is a deliberate Kotlin compatibility adaptation: Syn already
exports an Ident.clone extension. Syn's copy/clone entry points remain available
and delegate to the lossless member. The copy semantics follow Rust. The current
fallback implementation eagerly copies nested token objects; no allocation or
performance equivalence with Rust's shared storage is claimed.

Validation: 66 JVM and 66 macOS proc-macro2 tests pass, including fallback/compiler
tokens, raw identifiers, suffixes/raw literals, joint punctuation, negative literals
and nested/None-delimited groups. The integrated Syn suite passes 414 JVM / 412
macOS tests. Its three previous failure reproductions now pass. A standalone
Kotlin/Native oracle matches Rust's complete 19-line output, including exact
token/spelling/span fingerprints and 12 mutation-isolation checks. Compiler-backed
metadata has separate unit coverage; the oracle does not execute inside rustc.

Reproduce without publishing or changing pins:

```
./gradlew --offline --no-daemon --include-build ../proc-macro-kotlin -Pproject.repositories.mavenLocal=true -Dmaven.repo.local=/Users/sydney/.m2/repository jvmTest macosArm64Test
```

Evidence: `rust-kotlin/tmp/token-copy-repair-tkw9djkt/copy-validation.json`, saved
test XML, oracle sources/output and tested KLIBs. The earlier foundation receipt
and failing Syn results remain historical evidence. The two pre-existing dirty
configuration files remain byte-identical and are excluded from the repair commit.
Other platforms are unverified. No publication, remote CI or configuration changes.
