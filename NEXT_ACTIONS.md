# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 6/15 (40.0%)
- **Function parity:** 25/331 matched (target 39) — 7.6%
- **Class/type parity:** 8/46 matched (target 10) — 17.4%
- **Combined symbol parity:** 33/377 matched (target 49) — 8.8%
- **Average inline-code cosine:** 0.75 (function body across 5 matched files)
- **Average documentation cosine:** 0.20 (doc text across 5 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 2 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. rcvec

- **Target:** `procmacro2.Rcvec`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 22302.7
- **Functions:** 17/17 matched (target 25)
- **Missing functions:** _none_
- **Types:** 4/6 matched (target 5)
- **Missing types:** `Item`, `IntoIter`

### 2. detection

- **Target:** `procmacro2.Detection`
- **Similarity:** 0.70
- **Dependents:** 0
- **Priority Score:** 503.0
- **Functions:** 4/4 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 3. num

- **Target:** `procmacro2.Num`
- **Similarity:** 0.60
- **Dependents:** 0
- **Priority Score:** 304.0
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **TODOs:** 1

### 4. location

- **Target:** `procmacro2.Location`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 302.7
- **Functions:** 2/2 matched (target 3)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 5. marker

- **Target:** `procmacro2.Marker`
- **Similarity:** 1.00
- **Dependents:** 0
- **Priority Score:** 100.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 6. probe

- **Target:** `procmacro2.Probe [STUB]`
- **Similarity:** 1.00
- **Dependents:** 0
- **Priority Score:** 0.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
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
## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |

