# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 15/15 (100.0%)
- **Function parity:** 247/300 matched (target 512) — 82.3%
- **Class/type parity:** 32/60 matched (target 80) — 53.3%
- **Combined symbol parity:** 279/360 matched (target 592) — 77.5%
- **Average inline-code cosine:** 0.49 (function body across 14 matched files)
- **Average documentation cosine:** 0.23 (doc text across 14 matched files)
- **Cheat-zeroed Files:** 4
- **Critical Issues:** 7 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. fallback

- **Target:** `procmacro2.Fallback [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 1
- **Priority Score:** 1188510.0
- **Functions:** 63/72 matched (target 131)
- **Missing functions:** `push_negative_literal`, `drop`, `fmt`, `from`, `from_iter`, `extend`, `invalidate_current_thread_spans`, `eq`, `valid`
- **Types:** 4/13 matched (target 10)
- **Missing types:** `TokenStream`, `LexError`, `Item`, `IntoIter`, `Span`, `Group`, `Ident`, `Literal`, `FromStr2`

### 2. probe.proc_macro_span

- **Target:** `probe.ProcMacroSpan`
- **Similarity:** 0.71
- **Dependents:** 1
- **Priority Score:** 1000902.9
- **Functions:** 9/9 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 3. probe.proc_macro_span_location

- **Target:** `probe.ProcMacroSpanLocation`
- **Similarity:** 0.68
- **Dependents:** 1
- **Priority Score:** 1000403.2
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 4. probe.proc_macro_span_file

- **Target:** `probe.ProcMacroSpanFile`
- **Similarity:** 0.68
- **Dependents:** 1
- **Priority Score:** 1000203.2
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 5. wrapper

- **Target:** `procmacro2.Wrapper`
- **Similarity:** 0.21
- **Dependents:** 0
- **Priority Score:** 365907.9
- **Functions:** 20/49 matched (target 66)
- **Missing functions:** `mismatch`, `new`, `evaluate_now`, `into_token_stream`, `from_str_checked`, `unwrap_nightly`, `unwrap_stable`, `fmt`, `from`, `into_compiler_token`, `from_iter`, `extend`, `into_iter`, `call_site`, `mixed_site`, `def_site`, `unwrap`, `debug_span_field_if_nontrivial`, `new_checked`, `new_raw_checked`, `from_str_unchecked`, `f32_unsuffixed`, `f64_unsuffixed`, `string`, `character`, `byte_character`, `byte_string`, `c_string`, `invalidate_current_thread_spans`
- **Types:** 3/10 matched (target 15)
- **Missing types:** `TokenStream`, `DeferredTokenStream`, `LexError`, `TokenTreeIter`, `Item`, `IntoIter`, `Span`

### 6. lib

- **Target:** `procmacro2.Lib`
- **Similarity:** 0.41
- **Dependents:** 0
- **Priority Score:** 166905.9
- **Functions:** 42/55 matched (target 147)
- **Missing functions:** `default`, `from_str`, `from`, `extend`, `from_iter`, `fmt`, `unwrap`, `unstable`, `eq`, `partial_cmp`, `cmp`, `hash`, `into_iter`
- **Types:** 11/14 matched (target 18)
- **Missing types:** `Err`, `IntoIter`, `Item`

### 7. rustc_literal_escaper

- **Target:** `procmacro2.RustcLiteralEscaper`
- **Similarity:** 0.66
- **Dependents:** 0
- **Priority Score:** 53603.4
- **Functions:** 28/28 matched (target 59)
- **Missing functions:** _none_
- **Types:** 3/8 matched (target 19)
- **Missing types:** `CheckRaw`, `RawUnit`, `Error`, `Unescape`, `Unit`

### 8. extra

- **Target:** `procmacro2.Extra [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 30810.0
- **Functions:** 4/6 matched (target 5)
- **Missing functions:** `new`, `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `DelimSpanEnum`

### 9. rcvec

- **Target:** `procmacro2.Rcvec`
- **Similarity:** 0.72
- **Dependents:** 0
- **Priority Score:** 22302.8
- **Functions:** 17/17 matched (target 24)
- **Missing functions:** _none_
- **Types:** 4/6 matched (target 5)
- **Missing types:** `Item`, `IntoIter`

### 10. detection

- **Target:** `procmacro2.Detection`
- **Similarity:** 0.68
- **Dependents:** 0
- **Priority Score:** 10503.2
- **Functions:** 4/4 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/1 matched
- **Missing types:** `PanicHook`

### 11. parse

- **Target:** `procmacro2.Parse`
- **Similarity:** 0.71
- **Dependents:** 0
- **Priority Score:** 5302.9
- **Functions:** 50/50 matched (target 52)
- **Missing functions:** _none_
- **Types:** 3/3 matched
- **Missing types:** _none_

### 12. num

- **Target:** `procmacro2.Num`
- **Similarity:** 0.60
- **Dependents:** 0
- **Priority Score:** 304.0
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 13. location

- **Target:** `procmacro2.Location`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 302.7
- **Functions:** 2/2 matched (target 3)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 14. marker

- **Target:** `procmacro2.Marker [ZERO] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 110.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:tests/marker.rs` vs expected `marker.rs`
- **Proposed provenance header:** `// port-lint: tests marker.rs` (current: `// port-lint: tests tests/marker.rs`)
- **Lint issues:** 1

### 15. probe

- **Target:** `procmacro2.Probe [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

