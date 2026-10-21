# Swift exporter version

The Swift exporter dependency reads `libs.versions.kotlin`, the same existing
catalog entry used by the Kotlin Multiplatform plugin. The catalog remains
2.4.20. The separate `versions.kotlin` Gradle property previously selected
exporter 2.4.10 while the plugin and Native compiler used 2.4.20.

This correction changes the generated Swift tool version from 2.4.10 to
2.4.20. It changes no Kotlin public declaration, dependency catalog pin,
platform target, Swift test assertion, or warning policy. Generated Swift
source/API equivalence is not asserted: the retained diagnostic contains
additional sealed-type adapters and requires a stock 2.4.20 comparison.
The historical 268-versus-282 line-extractor comparison remains failed;
it compared different exporter versions and is not an accepted API baseline.

The provenance receipt is workspace artifact
`automation-artifacts/2026-10-20-proc-macro2-publication-repair/strict-swift-4287/baseline-provenance-v2.json`
(SHA256 `b186ff9ccb27e7b2437ad89e282b02aff4eaab3a789aea01c8ea46fac569cffb`).
It records the retained task-history exporter paths, current cached JAR
hashes, source head, generation logs, and the identical Native KLIB file
hashes for the old and diagnostic outputs. Cached JAR hashes were checked
after generation; no historical runtime classloader hash capture exists.

Validation of this source change is static only. The existing strict Swift
failure and zero executed Swift behavior tests remain recorded. No full
Swift build or Actions run was launched for this version alignment.
