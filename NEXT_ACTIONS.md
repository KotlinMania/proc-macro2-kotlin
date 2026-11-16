# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 11/15 (73.3%)
- **Function parity:** 229/293 matched (target 388) — 78.2%
- **Class/type parity:** 19/37 matched (target 49) — 51.4%
- **Combined symbol parity:** 248/330 matched (target 437) — 75.2%
- **Average inline-code cosine:** 0.32 (function body across 11 matched files)
- **Average documentation cosine:** 0.22 (doc text across 11 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 10 files with <0.60 function similarity
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

- **Target:** `Fallback`
- **Similarity:** 0.19
- **Dependents:** 1
- **Priority Score:** 1422208.1
- **Functions:** 77/109 matched (target 145)
- **Missing functions:** `get_cursor`, `fmt`, `fmt`, `fmt`, `from`, `from`, `from`, `from_iter`, `from_iter`, `extend`, `extend`, `invalidate_current_thread_spans`, `call_site`, `join`, `source_text`, `first_byte`, `last_byte`, `fmt`, `fmt`, `fmt`, `eq`, `eq`, `fmt`, `fmt`, `fmt`, `_new`, `fmt`, `fmt`, `from_str_checked`, `from_str_unchecked`, `valid`, `valid`
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

- **Target:** `Wrapper`
- **Similarity:** 0.13
- **Dependents:** 0
- **Priority Score:** 359608.8
- **Functions:** 57/86 matched (target 131)
- **Missing functions:** `fmt`, `from`, `from`, `from`, `from`, `from_iter`, `from_iter`, `extend`, `extend`, `fmt`, `from`, `from`, `fmt`, `fmt`, `into_iter`, `from`, `from`, `fmt`, `debug_span_field_if_nontrivial`, `from`, `fmt`, `fmt`, `from`, `fmt`, `fmt`, `from`, `fmt`, `fmt`, `invalidate_current_thread_spans`
- **Types:** 4/10 matched (target 22)
- **Missing types:** `TokenStream`, `LexError`, `TokenTreeIter`, `Item`, `IntoIter`, `Span`

### 6. rcvec

- **Target:** `Rcvec`
- **Similarity:** 0.15
- **Dependents:** 0
- **Priority Score:** 22608.5
- **Functions:** 20/20 matched (target 24)
- **Missing functions:** _none_
- **Types:** 4/6 matched (target 5)
- **Missing types:** `Item`, `IntoIter`

### 7. extra

- **Target:** `Extra`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 20808.6
- **Functions:** 4/6 matched (target 5)
- **Missing functions:** `new`, `fmt`
- **Types:** 2/2 matched (target 4)
- **Missing types:** _none_

### 8. detection

- **Target:** `Detection`
- **Similarity:** 0.39
- **Dependents:** 0
- **Priority Score:** 20606.1
- **Functions:** 4/5 matched
- **Missing functions:** `initialize`
- **Types:** 0/1 matched (target 0)
- **Missing types:** `PanicHook`

### 9. parse

- **Target:** `Parse`
- **Similarity:** 0.21
- **Dependents:** 0
- **Priority Score:** 5307.9
- **Functions:** 50/50 matched (target 60)
- **Missing functions:** _none_
- **Types:** 3/3 matched
- **Missing types:** _none_

### 10. location

- **Target:** `Location`
- **Similarity:** 0.18
- **Dependents:** 0
- **Priority Score:** 308.2
- **Functions:** 2/2 matched (target 3)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 11. marker

- **Target:** `Marker`
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
| `num` | `Num` | 0 | `num.rs` | `Num.kt` |
| `probe` | `probe.Probe` | 0 | `probe.rs` | `probe/Probe.kt` |
| `rustc_literal_escaper` | `RustcLiteralEscaper` | 0 | `rustc_literal_escaper.rs` | `RustcLiteralEscaper.kt` |

