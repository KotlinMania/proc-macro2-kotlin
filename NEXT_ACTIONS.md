# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 10/15 (66.7%)
- **Function parity:** 211/337 matched (target 392) — 62.6%
- **Class/type parity:** 28/60 matched (target 54) — 46.7%
- **Combined symbol parity:** 239/397 matched (target 446) — 60.2%
- **Average inline-code cosine:** 0.65 (function body across 10 matched files)
- **Average documentation cosine:** 0.41 (doc text across 10 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 4 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. fallback

- **Target:** `procmacro2.Fallback`
- **Similarity:** 0.51
- **Dependents:** 1
- **Priority Score:** 1198504.9
- **Functions:** 63/72 matched (target 123)
- **Missing functions:** `push_negative_literal`, `drop`, `fmt`, `from`, `from_iter`, `extend`, `fileinfo_mut`, `eq`, `valid`
- **Types:** 3/13 matched (target 9)
- **Missing types:** `TokenStream`, `LexError`, `TokenTreeIter`, `Item`, `IntoIter`, `Span`, `Group`, `Ident`, `Literal`, `FromStr2`

### 2. lib

- **Target:** `procmacro2.Lib`
- **Similarity:** 0.43
- **Dependents:** 0
- **Priority Score:** 166905.7
- **Functions:** 42/55 matched (target 124)
- **Missing functions:** `default`, `from_str`, `from`, `extend`, `from_iter`, `fmt`, `unwrap`, `unstable`, `eq`, `partial_cmp`, `cmp`, `hash`, `into_iter`
- **Types:** 11/14 matched
- **Missing types:** `Err`, `IntoIter`, `Item`

### 3. rustc_literal_escaper

- **Target:** `procmacro2.RustcLiteralEscaper`
- **Similarity:** 0.66
- **Dependents:** 0
- **Priority Score:** 53603.4
- **Functions:** 28/28 matched (target 51)
- **Missing functions:** _none_
- **Types:** 3/8 matched (target 18)
- **Missing types:** `CheckRaw`, `RawUnit`, `Error`, `Unescape`, `Unit`

### 4. extra

- **Target:** `procmacro2.Extra`
- **Similarity:** 0.38
- **Dependents:** 0
- **Priority Score:** 40806.2
- **Functions:** 3/6 matched (target 5)
- **Missing functions:** `invalidate_current_thread_spans`, `new`, `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `DelimSpanEnum`

### 5. rcvec

- **Target:** `procmacro2.Rcvec`
- **Similarity:** 0.72
- **Dependents:** 0
- **Priority Score:** 22302.8
- **Functions:** 17/17 matched (target 24)
- **Missing functions:** _none_
- **Types:** 4/6 matched (target 5)
- **Missing types:** `Item`, `IntoIter`

### 6. detection

- **Target:** `procmacro2.Detection`
- **Similarity:** 0.70
- **Dependents:** 0
- **Priority Score:** 10503.0
- **Functions:** 4/4 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/1 matched
- **Missing types:** `PanicHook`

### 7. parse

- **Target:** `procmacro2.Parse`
- **Similarity:** 0.72
- **Dependents:** 0
- **Priority Score:** 5302.8
- **Functions:** 50/50 matched (target 52)
- **Missing functions:** _none_
- **Types:** 3/3 matched
- **Missing types:** _none_

### 8. num

- **Target:** `procmacro2.Num`
- **Similarity:** 0.60
- **Dependents:** 0
- **Priority Score:** 304.0
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **TODOs:** 1

### 9. location

- **Target:** `procmacro2.Location`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 302.7
- **Functions:** 2/2 matched (target 3)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 10. marker

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

## Next Commands

```bash
# Initialize task queue for systematic porting
cd tools/ast_distance
./ast_distance --init-tasks ../../tmp/proc-macro2/src rust ../../src/commonMain/kotlin/io/github/kotlinmania/procmacro2 kotlin tasks.json ../../AGENTS.md

# Get next high-priority task
./ast_distance --assign tasks.json <agent-id>
```
