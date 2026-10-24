# Existing tokenizer byte-access optimization

The Windows direct-translation inventory exposed repeated whole-tail allocation:
`tokenStream` encoded all remaining source and allocated a byte list just to read
its first byte. Several complete modules took minutes to process. That observation
does not attribute all elapsed time to this operation.

`Cursor` now caches whether its immutable remaining string is ASCII. ASCII byte
access uses a read-only indexed view, and the tokenizer/whitespace loop use a
first-byte operation. Advancing a cursor propagates the cached property; a false
value is conservative even if its suffix becomes ASCII. The cache is outside the
data-class constructor, so equality, hashCode, copy, destructuring, offsets and
public APIs retain their previous meaning. Non-ASCII input retains the existing
UTF-8 encoding path. No token recognition, delimiter, literal, comment or identifier
rule is replaced; this is an optimization of the existing engine, not a new lexer.
Existing UTF-16/UTF-8 limitations are not claimed repaired by this change.

Validation: 69 JVM and 69 macOS ARM64 tests pass, including three new tests for byte
values, malformed UTF-16, cursor value semantics, and cached/fresh token/span
agreement. The first test attempt incorrectly fingerprinted wrapper identities;
the corrected test compares actual span values. All previous 66 tests passed in
that first attempt too. Other platforms are not validated by these local checks.

Receipts and a saved library for translator bootstrap are in
`../rust-kotlin/tmp/windows-lexer-byte-access-01/`. The two pre-existing dirty
configuration files remain unchanged. No publication or dependency pin change is
part of this increment. Translation throughput and old/new output comparison are
tracked separately in the translator's Windows report.
