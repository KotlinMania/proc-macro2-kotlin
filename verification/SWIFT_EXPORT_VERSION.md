# Swift exporter version

## Historical stock version alignment

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

The version-alignment commit was checked statically. Its retained stock
Swift failure is historical evidence, separate from the published-tooling
adoption below.

## Published tooling selection

The production exporter now selects
`io.github.kotlinmania:swift-export-embeddable:2.4.20-km.1`, independently
retrieved from Maven Central after the tooling release's PR and main CI.
KGP and Kotlin/Native remain 2.4.20. `swift-tooling-lock.json` records the
published worker closure, helper and official SDK digests.

`./gradlew swiftExportSmokeTest --no-daemon --max-workers=2 --no-configuration-cache`
resolves the exporter and SDK helper through Gradle, verifies all worker
hashes, and runs the published helper against a verified official SDK in
`build/swift-tooling`. Only its separate `native-sdk` copy is selected for
the nested Native export. Native auto-caches use `build/native-auto-cache`,
and the complete prepared SDK inventory is checked after the tests.
Installed compilers and SDKs are never modified.

Generated dependencies retain their producer manifest and Swift 5 language
mode; the authored test package explicitly uses Swift 6. Both Swift and
Native compilation retain warnings as errors. The task checks the three
original behavior cases and actual frontend modes separately from package
configuration and generated test-runner commands. No generated Swift API
is rewritten. The mutable `KotlinError.wrapped` contract is preserved.
