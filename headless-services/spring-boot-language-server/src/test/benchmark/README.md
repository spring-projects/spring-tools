# Benchmarks of the Kotlin parsers

Compares the Kotlin parsers of the language server (`psi`, `tree-sitter`, `syntax-api`, see `KotlinSyntaxReaders`) while they read
Kotlin sources into `KotlinSyntax`: time, allocation and memory, for small and large projects and for large files. The
results of a run are in section 9.5a of `docs/kotlin-support-plan.md`.

This directory is not compiled by Maven (it is not under `src/test/java`).

```bash
cd headless-services/spring-boot-language-server
src/test/benchmark/run-benchmarks.sh          # without the corpus of 5000 files
src/test/benchmark/run-benchmarks.sh full     # with it, takes a few minutes
```

What it does:

1. Builds the classes of the module and the harness, and generates the corpora into `target/benchmark/corpora`
   (`generate_corpora.py`): `small` is the Kotlin files of the test projects, the others are generated Spring style
   code with short function bodies (`medium`, `large`, `hugefile`) and with bodies of about 25 lines (`bodies`,
   `bodieshuge`).
2. **Checks** that all parsers read every file of the corpora the same way as PSI: `syntax-api` exactly, `tree-sitter`
   apart from the range of a declaration next to comments (where PSI includes the comments). And that tree-sitter and
   syntax-api read the same with and without skipping the bodies of functions.
3. **Measures**, in a fresh JVM for every parser and corpus (`BENCHMARK_HEAP`, default `2g`): the set up, the first
   pass (cold), the warm passes, the allocation per file, the heap the parser holds, the process memory (from
   `/proc/self/status`, so Linux only), and the time with several threads. The variants are `psi` (bodies of functions
   are parsed lazily, which means never), `psi-bodies` (`BenchPsiReader full`: the reader runs after everything was
   parsed), `tree-sitter` (skips the bodies of functions) and `tree-sitter-bodies` (parses them), and `syntax-api` (the bodies of
   functions are parsed lazily, which means never) and `syntax-api-bodies` (parses them).

Compare parsers with the same variant of the bodies: `psi-bodies` with `tree-sitter-bodies` and `syntax-api-bodies`, and
`psi` with `tree-sitter` and `syntax-api`. Process memory (RSS) depends on how the JVM sizes its heap, the allocation per file does not.
