# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 13/15 (86.7%)
- **Function parity:** 235/335 matched (target 365) — 70.1%
- **Class/type parity:** 22/46 matched (target 63) — 47.8%
- **Combined symbol parity:** 257/381 matched (target 428) — 67.5%
- **Average inline-code cosine:** 0.31 (function body across 13 matched files)
- **Average documentation cosine:** 0.25 (doc text across 13 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 12 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 1 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. fallback

- **Target:** `procmacro2.Fallback`
- **Similarity:** 0.19
- **Dependents:** 1
- **Priority Score:** 1462208.1
- **Functions:** 73/109 matched (target 131)
- **Missing functions:** `push_negative_literal`, `drop`, `get_cursor`, `fmt`, `fmt`, `fmt`, `from`, `from`, `from`, `from_iter`, `from_iter`, `extend`, `extend`, `invalidate_current_thread_spans`, `call_site`, `call_site`, `join`, `source_text`, `first_byte`, `last_byte`, `fmt`, `fmt`, `fmt`, `eq`, `eq`, `fmt`, `fmt`, `fmt`, `_new`, `from_str_unchecked`, `fmt`, `fmt`, `from_str_checked`, `from_str_unchecked`, `valid`, `valid`
- **Types:** 4/13 matched (target 10)
- **Missing types:** `TokenStream`, `LexError`, `Item`, `IntoIter`, `Span`, `Group`, `Ident`, `Literal`, `FromStr2`

### 2. probe.proc_macro_span

- **Target:** `probe.ProcMacroSpan`
- **Similarity:** 0.40
- **Dependents:** 1
- **Priority Score:** 1000906.0
- **Functions:** 9/9 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 3. probe.proc_macro_span_location

- **Target:** `probe.ProcMacroSpanLocation`
- **Similarity:** 0.37
- **Dependents:** 1
- **Priority Score:** 1000406.3
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 4. probe.proc_macro_span_file

- **Target:** `probe.ProcMacroSpanFile`
- **Similarity:** 0.37
- **Dependents:** 1
- **Priority Score:** 1000206.3
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 5. wrapper

- **Target:** `procmacro2.Wrapper`
- **Similarity:** 0.04
- **Dependents:** 0
- **Priority Score:** 669609.6
- **Functions:** 27/86 matched (target 66)
- **Missing functions:** `mismatch`, `new`, `evaluate_now`, `into_token_stream`, `new`, `from_str_checked`, `is_empty`, `unwrap_nightly`, `unwrap_stable`, `fmt`, `from`, `from`, `from`, `into_compiler_token`, `from`, `from_iter`, `from_iter`, `extend`, `extend`, `fmt`, `from`, `from`, `fmt`, `fmt`, `into_iter`, `call_site`, `mixed_site`, `def_site`, `unwrap`, `unwrap_nightly`, `from`, `from`, `fmt`, `debug_span_field_if_nontrivial`, `new`, `unwrap_nightly`, `from`, `fmt`, `fmt`, `new_checked`, `new_raw_checked`, `unwrap_nightly`, `from`, `fmt`, `fmt`, `from_str_checked`, `from_str_unchecked`, `f32_unsuffixed`, `f64_unsuffixed`, `string`, `character`, `byte_character`, `byte_string`, `c_string`, `unwrap_nightly`, `from`, `fmt`, `fmt`, `invalidate_current_thread_spans`
- **Types:** 3/10 matched (target 15)
- **Missing types:** `TokenStream`, `DeferredTokenStream`, `LexError`, `TokenTreeIter`, `Item`, `IntoIter`, `Span`

### 6. rustc_literal_escaper

- **Target:** `procmacro2.RustcLiteralEscaper`
- **Similarity:** 0.34
- **Dependents:** 0
- **Priority Score:** 74806.6
- **Functions:** 38/40 matched (target 59)
- **Missing functions:** `char2raw_unit`, `char2raw_unit`
- **Types:** 3/8 matched (target 19)
- **Missing types:** `CheckRaw`, `RawUnit`, `Error`, `Unescape`, `Unit`

### 7. rcvec

- **Target:** `procmacro2.Rcvec`
- **Similarity:** 0.15
- **Dependents:** 0
- **Priority Score:** 22608.5
- **Functions:** 20/20 matched (target 24)
- **Missing functions:** _none_
- **Types:** 4/6 matched (target 5)
- **Missing types:** `Item`, `IntoIter`

### 8. extra

- **Target:** `procmacro2.Extra`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 20808.6
- **Functions:** 4/6 matched (target 5)
- **Missing functions:** `new`, `fmt`
- **Types:** 2/2 matched (target 4)
- **Missing types:** _none_

### 9. detection

- **Target:** `procmacro2.Detection`
- **Similarity:** 0.39
- **Dependents:** 0
- **Priority Score:** 20606.1
- **Functions:** 4/5 matched
- **Missing functions:** `initialize`
- **Types:** 0/1 matched
- **Missing types:** `PanicHook`

### 10. parse

- **Target:** `procmacro2.Parse`
- **Similarity:** 0.26
- **Dependents:** 0
- **Priority Score:** 5307.4
- **Functions:** 50/50 matched (target 52)
- **Missing functions:** _none_
- **Types:** 3/3 matched
- **Missing types:** _none_

### 11. location

- **Target:** `procmacro2.Location`
- **Similarity:** 0.18
- **Dependents:** 0
- **Priority Score:** 308.2
- **Functions:** 2/2 matched (target 3)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 12. num

- **Target:** `procmacro2.Num`
- **Similarity:** 0.25
- **Dependents:** 0
- **Priority Score:** 307.5
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 13. marker

- **Target:** `procmacro2.Marker`
- **Similarity:** 1.00
- **Dependents:** 0
- **Priority Score:** 100.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |
| `probe` | `probe.Probe` | 0 | `probe.rs` | `probe/Probe.kt` |

