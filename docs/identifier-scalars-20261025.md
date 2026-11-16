# Preserve Rust identifier classification over complete Unicode scalars

The existing tokenizer accepted every supplementary code point, while
`Fallback.identOk` iterated Kotlin `Char` values and rejected all surrogate pairs.
Before the repair, two of three focused tests failed: a valid Deseret identifier
could not be constructed, and emoji was accepted by token parsing.

`Fallback.kt` now uses the existing unicode-ident table predicates over integer
scalars. Constructor validation follows the original Rust `src/fallback.rs`
`ident_ok` loop using the already-present `codePointAndWidthAt` decoder. The
tokenizer already calls these classification helpers; no new lexer or parser was
introduced. Raw-identifier restrictions and public APIs are unchanged. Malformed
UTF-16 is rejected as invalid input because it cannot represent Rust `str`.

Validation: all 73 JVM and 73 macOS ARM64 tests pass, including valid/nonvalid
supplementary identifiers, raw identifiers, continuation-only digits and malformed
UTF-16. `proc-macro2-kotlin:0.1.7` published to local Maven using the repaired
`unicode-ident-kotlin:0.1.1`. Existing targets/pins and the user's dirty build and
version-catalog changes were preserved. Logs are in
`tmp/identifier-scalars-20261025/{before,after,publish}.log`.

This is a source-backed dependency correction, not whole-file generated adoption.
These tests exercise the fallback backend; compiler-host hygiene, Swift export,
and execution on other platforms remain unverified.
