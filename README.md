# proc-macro2-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fproc--macro2--kotlin-blue.svg)](https://github.com/KotlinMania/proc-macro2-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/proc-macro2-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/proc-macro2-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/proc-macro2-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/proc-macro2-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`dtolnay/proc-macro2`](https://github.com/dtolnay/proc-macro2).

**Original Project:** This port is based on [`dtolnay/proc-macro2`](https://github.com/dtolnay/proc-macro2). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `dtolnay/proc-macro2`

> The text below is reproduced and lightly edited from [`https://github.com/dtolnay/proc-macro2`](https://github.com/dtolnay/proc-macro2). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

## proc-macro2

[<img alt="github" src="https://img.shields.io/badge/github-dtolnay/proc--macro2-8da0cb?style=for-the-badge&labelColor=555555&logo=github" height="20">](https://github.com/dtolnay/proc-macro2)
[<img alt="crates.io" src="https://img.shields.io/crates/v/proc-macro2.svg?style=for-the-badge&color=fc8d62&logo=rust" height="20">](https://crates.io/crates/proc-macro2)
[<img alt="docs.rs" src="https://img.shields.io/badge/docs.rs-proc--macro2-66c2a5?style=for-the-badge&labelColor=555555&logo=docs.rs" height="20">](https://docs.rs/proc-macro2)
[<img alt="build status" src="https://img.shields.io/github/actions/workflow/status/dtolnay/proc-macro2/ci.yml?branch=master&style=for-the-badge" height="20">](https://github.com/dtolnay/proc-macro2/actions?query=branch%3Amaster)

A wrapper around the procedural macro API of the compiler's `proc_macro` crate.
This library serves two purposes:

- **Bring proc-macro-like functionality to other contexts like build.rs and
  main.rs.** Types from `proc_macro` are entirely specific to procedural macros
  and cannot ever exist in code outside of a procedural macro. Meanwhile
  `proc_macro2` types may exist anywhere including non-macro code. By developing
  foundational libraries like [syn] and [quote] against `proc_macro2` rather
  than `proc_macro`, the procedural macro ecosystem becomes easily applicable to
  many other use cases and we avoid reimplementing non-macro equivalents of
  those libraries.

- **Make procedural macros unit testable.** As a consequence of being specific
  to procedural macros, nothing that uses `proc_macro` can be executed from a
  unit test. In order for helper libraries or components of a macro to be
  testable in isolation, they must be implemented using `proc_macro2`.

[syn]: https://github.com/dtolnay/syn
[quote]: https://github.com/dtolnay/quote

## Usage

```toml
[dependencies]
proc-macro2 = "1.0"
```

The skeleton of a typical procedural macro typically looks like this:

```rust
extern crate proc_macro;

#[proc_macro_derive(MyDerive)]
pub fn my_derive(input: proc_macro::TokenStream) -> proc_macro::TokenStream {
    let input = proc_macro2::TokenStream::from(input);

    let output: proc_macro2::TokenStream = {
        /* transform input */
    };

    proc_macro::TokenStream::from(output)
}
```

If parsing with [Syn], you'll use [`parse_macro_input!`] instead to propagate
parse errors correctly back to the compiler when parsing fails.

[`parse_macro_input!`]: https://docs.rs/syn/2.0/syn/macro.parse_macro_input.html

## Unstable features

The default feature set of proc-macro2 tracks the most recent stable compiler
API. Functionality in `proc_macro` that is not yet stable is not exposed by
proc-macro2 by default.

To opt into the additional APIs available in the most recent nightly compiler,
the `procmacro2_semver_exempt` config flag must be passed to rustc. We will
polyfill those nightly-only APIs back to Rust 1.71.0. As these are unstable APIs
that track the nightly compiler, minor versions of proc-macro2 may make breaking
changes to them at any time.

```
RUSTFLAGS='--cfg procmacro2_semver_exempt' cargo build
```

Note that this must not only be done for your crate, but for any crate that
depends on your crate. This infectious nature is intentional, as it serves as a
reminder that you are outside of the normal semver guarantees.

Semver exempt methods are marked as such in the proc-macro2 documentation.

<br>

#### License

<sup>
Licensed under either of <a href="LICENSE-APACHE">Apache License, Version
2.0</a> or <a href="LICENSE-MIT">MIT license</a> at your option.
</sup>

<br>

<sub>
Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in this crate by you, as defined in the Apache-2.0 license, shall
be dual licensed as above, without any additional terms or conditions.
</sub>

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:proc-macro2-kotlin:0.1.0-SNAPSHOT")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same MIT license as the upstream [`dtolnay/proc-macro2`](https://github.com/dtolnay/proc-macro2). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the proc-macro2 authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`dtolnay/proc-macro2`](https://github.com/dtolnay/proc-macro2) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
