# port-lint Proposed Changes

**Generated:** 2026-08-28
**Source:** tmp/proc-macro2/src
**Target:** src/commonMain/kotlin/io/github/kotlinmania/procmacro2

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `src/commonTest/kotlin/io/github/kotlinmania/procmacro2/MarkerTest.kt` | `// port-lint: tests tests/marker.rs` | `// port-lint: tests marker.rs` | `marker.rs` | `port-lint provenance header matched only by basename: 'tests:tests/marker.rs' vs expected 'marker.rs'` |
