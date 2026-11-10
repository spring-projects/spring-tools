# Kotlin Source Support for the Spring Boot Language Server: Analysis and Plan

Status: research and first prototype (Nov 2026), see section 9 for what is implemented. Branch: `features/kotlin-support`.

GitHub issue: **GH-2085** (use it as the issue number in commit messages: `GH-2085: ...`).

## 1. Goal and assumptions

Users open a Kotlin Spring Boot project in VSCode. They have the JetBrains **"Java and Kotlin by IntelliJ IDEA"** extension (`jetbrains.intellij-server`) installed for Java/Kotlin editing, plus the Spring Boot extension. Spring Tools should behave the same as for Java projects: indexing, symbols, validations, quick fixes, completions, hovers, definitions, code lenses, inlay hints, semantic tokens, and the Claude plugin / MCP diagnostics.

Assumption: the JetBrains extension reports the project and its classpath to us.

**Scope:**
- **In scope:** the VSCode extension with the IntelliJ IDEA extension, and the Claude Code plugin (standalone LS, MCP diagnostics).
- **Out of scope for now:** Eclipse (no Kotlin tooling we would integrate with), and the separate JetBrains Kotlin LSP extension (`jetbrains.kotlin-server`), which can be revisited later.
- **Parity target:** "behaves the same" is the long-term goal, not the goal of the first release. Parity is defined per feature area (section 6). The first goal is stereotype indexing with the structure view, followed by request mapping indexing (6.1). All other indexers, diagnostics and editor features come later. Quick fixes are limited to a selected subset (section 5.5). Java checks that make no sense in Kotlin are not ported (section 5.7).

**Decision:** the integration (project model, classpath, `sts/java*`-style services) will be discussed with JetBrains, and we can ask for what we need. It is therefore **not treated as a risk** in this plan. Section 5.4 lists the findings as input for that discussion, including a concrete wishlist.

**Constraint:** the Kotlin implementation must be as separate and decoupled from the existing Java implementation as possible, with minimal changes to existing Java functionality (details in section 3.1).

Confidence tags used below: **[V]** means seen in code or in a source during this research. **[I]** means inference or not verified.

## 2. Findings about the current code base (Java coupling)

### 2.1 Headline findings

- Parsing and type resolution use **JDT core DOM embedded in the LS process**, in both the full LS and the standalone LS. JDT LS is only the source of project and classpath data and of a few "ask the client" requests (`sts/javaType`, etc.). [V]
- Coupling is very high. Of 676 files in `spring-boot-language-server/.../boot`, 214 import `org.eclipse.jdt.core.dom`, and 20 import OpenRewrite. [V]
- There is no `.kt` handling, no Kotlin language id and no Kotlin compiler dependency anywhere. [V]
- Kotlin classes already matter indirectly. Once compiled, they show up as binaries on the classpath. The Jandex and bytecode scanners (`Jar*Scanner`) therefore index Kotlin-compiled **jars** unchanged. [V]
- The VSCode client already lists the JetBrains **"Java and Kotlin by IntelliJ IDEA"** extension (`jetbrains.intellij-server`) as a Java-tooling provider in `JAVA_TOOLING_DESCRIPTORS` (`vscode-extensions/vscode-spring-boot/lib/Main.ts`). The README names it too. [V] `lib/java-tooling.ts` only waits for one of the listed extensions to activate. It obtains no classpath or other data from it. [V] The JetBrains Kotlin LSP (`jetbrains.kotlin-server`) is a different extension and out of scope (section 1).
- `vscode-extensions/commons-vscode/src/classpath.ts` always routes `sts/addClasspathListener` to `java.execute.workspaceCommand sts.java.addClasspathListener`, and only reads the `redhat.java` extension API. [V] Either the IntelliJ IDEA extension implements this JDT LS command, or Java projects get no classpath today when it is the only Java tooling installed. [I] See section 5.4.

### 2.2 Work areas

The paths below are relative to `headless-services/` unless noted. BLS means `spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot`.

| # | Work area | Java-coupled core | Reusable as-is | Kotlin effort |
|---|---|---|---|---|
| A | **Project and classpath model** | `CPE.isJavaContent`, `IClasspathUtil.getProjectJavaSourceFolders`, `MavenProjectClasspath` (default `src/main/java`), `GradleProjectClasspath`, JDT LS `ClasspathUtil`, `commons-vscode/src/classpath.ts` (hard-wired to `redhat.java`) | `IClasspath`, `IJavaProject`, `JavaProjectFinder` | Medium. Add source language or extensions to `CPE`, include `src/main/kotlin` and Kotlin outputs, add a classpath source for the JetBrains extension. |
| B | **File discovery, watching, document sync** | `.java` suffix checks in `SpringIndexerJava`, `SpringIndexerJavaCacheHelper`, `ModulithService`. `LanguageId.JAVA` in `BootLanguageServerBootApp` and `BootJavaLanguageServerComponents.LANGUAGES`. | `SpringIndexer` interface (35 lines, natural seam for `SpringIndexerKotlin`), `SpringSymbolIndex`, index cache, `SpringMetamodelIndex` | Small to medium. Add a `kotlin` `LanguageId`, watch patterns `**/*.kt`, a new indexer. |
| C | **Parsing and type resolution** | `CompilationUnitCache`, `CUResolver` (reflection into JDT internals), `SpringIndexerJavaContext`, `ASTUtils` (1284 lines, about 230 callers), `AnnotationHierarchies` | none | **Large and the key risk.** New per-file model and type resolver (section 4). |
| D | **Indexing (beans, request mappings, etc.)** | `SpringIndexerJavaAstScanner` (one `ASTVisitor`), `SpringComponentIndexer` (5 JDT-typed methods). Implementers: `ComponentIndexer`, `StereotypesIndexer`, `FeignClientIndexer`, `HttpExchangeIndexer`, plus helper indexers for beans, request mappings, data repositories, events, Spring AI and config properties. `WebFnRouteExtractor` is 1059 lines. | Index element data classes (about 14), the `Bean` model, `Jar*Scanner`, Jandex machinery | Large. Everything is "annotations plus type hierarchy". Needs a Kotlin extraction layer producing the same elements. |
| E | **Validations (reconcilers)** | `JdtAstReconciler` with about 45 implementations (Boot2/3/4, AOT, Spring AI, SpEL, Cron, queries) | `JavaReconciler`, `BootJavaReconcileEngine`, `ReconcilingContext`, `CachedDiagnostic`, MCP diagnostics | Large, but incremental. Triage for Kotlin relevance first (5.7), then port the highest-value reconcilers. The `explanations/*.md` playbooks are **not** language-neutral: 48 of 75 contain Java code blocks. [V] Kotlin diagnostics need Kotlin examples (5.7). |
| F | **Quick fixes and refactorings** | `JdtRefactoring` / `ASTRewrite` (about 27 classes, 4k lines). OpenRewrite recipes in `commons-rewrite` (2.8k lines). | `ReconcileProblemCodeActionProvider`, code-action plumbing | Large. No JDT equivalent exists for Kotlin. Needs a text-edit-based approach on PSI (section 4). `rewrite-kotlin` was excluded in commit `fae634b77`. |
| G | **Completions, hovers, definitions, references, code lenses, highlights** | `BootJavaCompletionEngine` (`NodeFinder`), `BootJavaHoverProvider`, `JavaDefinitionHandler`, `BootJavaReferencesHandler`, `BootJavaCodeLensEngine`. All provider SPIs take JDT `ASTNode` / `Annotation` / `ITypeBinding`. | Live-data model (about 40 classes, JMX/actuator) | Large. There is no language-neutral facade today. |
| H | **Semantic tokens, inlay hints** | `JdtSemanticTokensHandler` (registered with `LanguageId.JAVA`), `JdtInlayHintsHandler` | ANTLR reconcilers and grammars for embedded languages | Medium |
| I | **Embedded languages in string literals (SpEL, Cron, JPQL/HQL/SQL)** | `java/embedded/lang/*` (10 files, 745 lines) maps JDT `StringLiteral`/`TextBlock` to offset-mapped snippets | `AntlrReconciler`, `AntlrReconcilerWithSpel`, `commons/jpql` | Medium. Redo snippet extraction for Kotlin string templates and raw strings (`"""`). |
| J | **Client wiring (VSCode / Eclipse / Claude plugin)** | `documentSelector` (java only), `activationEvents` (`onLanguage:java`), `commons-vscode/src/java-data.ts` forwarding `sts/java*` to JDT LS, Eclipse `plugin.xml` content type, standalone `StandaloneFileWatcher` | MCP tools (`getProjectDiagnostics`), standalone launcher | Small to medium, but depends on what the JetBrains extension offers (section 5.4). |
| K | **Source navigation / type lookup callbacks** | `sts/javaType`, `sts/javaLocation`, `JdtLsIndex`, `BLS/java/links/*` | `LegacyJavaProject` + Jandex fallback | Medium. Jandex fallback exists, but Kotlin source locations need support. |

Also: the Claude plugin README says the watcher re-indexes "Java/Kotlin/Groovy" changes. In code, only `**/*.java` is indexed, so the README overclaims. [V] (`claude-plugins/spring-tools/README.md:266`)

### 2.3 Details that matter specifically for Kotlin

- The Gradle classpath code sets `isJavaContent` from whether a source folder holds only `.java` files. The logic looks odd or inverted and should be checked before relying on it. Pure Kotlin source folders would get `false`. [V]
- Maven: `src/main/kotlin` is not on the classpath unless the POM sets `sourceDirectory`. [V]
- Two parse paths resolve types differently [V]:
  - The **batch indexer** (`SpringIndexerJavaParserUtils.createParser`, `ASTParserCleanupEnabled`) passes the project's source folders to JDT as a sourcepath, so Java-to-Java references resolve from sources without compiling.
  - `CompilationUnitCache` (editor and quick-fix path) resolves against **binary roots only**.
  - JDT only reads `.java` sources, so for Java code to see types declared in Kotlin sources, compiled Kotlin output must exist.
  - **Known limitation, accepted under 3.1:** on the Java side, references to Kotlin-declared types (for example injection points of a Kotlin bean type, "injected into" hovers) resolve only after the Kotlin code has been compiled. We do not change the Java resolution to fix this.
- The OpenRewrite parsers filter sources by `.java` and `src/main/java`-style paths. [V]

## 3. Reasonable architecture direction

### 3.1 Design constraint: separate and decoupled from the Java implementation

**Requirement (from the project owner):** implement Kotlin support as separate and decoupled from the existing install as possible, and change as little as possible of the existing Java-related language server functionality.

Consequences for the design:

- **Additive, not refactoring.** No generalizing of the JDT-typed SPIs (`SpringComponentIndexer`, `JdtAstReconciler`, `HoverProvider`, `CompletionProvider`, etc.) and no changes to `ASTUtils`, `CompilationUnitCache` or the existing indexers. Kotlin gets its own parallel interfaces and implementations. Some duplication is accepted in exchange for zero regression risk for Java users. The one exception is the JDT bridge (section 4.3): if it proves viable, the existing Java indexers and declaration-level reconcilers are **reused** instead of duplicated. This needs one deliberate, scoped change to Java code: the code paths enabled for Kotlin switch their binding calls to a binding-access interface (touch point 7).
- **Separate module(s).** Kotlin code and the Kotlin compiler / Analysis API dependencies live in new Maven module(s) (for example `spring-boot-language-server-kotlin`), not in `spring-boot-language-server`. The Java modules do not depend on it. The Kotlin module depends on the existing ones, and the dependency never points the other way.
- **Plug in through extension points, adding minimal hooks where none exist.** The `SpringIndexer` interface is the natural seam, but there is no registration mechanism for it. `SpringSymbolIndex` builds its indexers as hard-coded arrays (`SpringSymbolIndex.java:244`, `:321`, `:325`). [V] So a small, behavior-neutral hook is needed there, for example appending any additional `SpringIndexer` beans found in the Spring context. Whether the reconciler, hover and code-action registries are injectable is not yet verified and must be checked in phase 0 (6.3). Every hook is listed below and reviewed on its own.
- **Optional at runtime.** If the Kotlin module is absent or disabled (feature flag or setting), the server behaves exactly as today. Java-only users pay no cost in jar size, memory or startup. This also keeps the large Kotlin compiler dependency out of the shipped Java server unless intended (and with the isolated classloader or child process from 4.1, out of its classpath entirely).
- **Shared data model only at the output side.** Kotlin indexers emit the existing index element classes, `Bean` model and `CachedDiagnostic`s. Those are plain data classes without AST references, so reusing them does not couple Kotlin to JDT. Downstream consumers (symbols, MCP, live data, caches) stay untouched.
- **Client side:** the VSCode `documentSelector` and activation event additions for `kotlin` are the only expected changes to the Java wiring in `Main.ts` and `package.json`. Keep the classpath and Java-data services for Kotlin in new files, not in `classpath.ts` and `java-data.ts`.
- **Explicit list of unavoidable touch points** (to be kept minimal and verified in phases 0 and 4, see 6.3):
  1. A hook in `SpringSymbolIndex` to register additional `SpringIndexer`s (today hard-coded arrays) and their `.kt` watch patterns.
  2. A new `KOTLIN` constant in `LanguageId` (none exists today [V]) and the `.kt` mapping in the language computer in `BootLanguageServerBootApp`.
  3. `CPE` / classpath model: source language info and Kotlin source and output folders. `isJavaContent` must keep its meaning for Java code.
  4. Kotlin source folders reported by Maven and Gradle project parsing.
  5. VSCode client selectors and activation.

  6. Possibly registration hooks for reconcilers, hovers and code actions, depending on the phase 0 check. Not needed for the first goal.
  7. **Binding-access interface (JDT bridge only, needs an explicit decision):** the Java code paths enabled for Kotlin (for the first goal `StereotypesIndexer`, `AnnotationHierarchies`, the `ASTUtils` methods they use, later the request mapping indexers) call bindings through an interface instead of `resolveBinding()` and JDT binding methods directly. The JDT implementation only forwards, so Java behavior stays the same, and the existing Java tests must pass unchanged. The scope grows with every indexer or reconciler enabled for Kotlin, and each extension is a decision of its own (4.3). **Decided against** (see 4.3 and 9.2): the `BindingResolver` bridge needs none of these changes, the Java indexing code stays untouched.

Anything beyond this list needs a deliberate decision, because it is a change to Java behavior.

**The changes made** (details in section 9): touch point 1 (as `SpringIndexerProvider`) and 2 (`LanguageId.KOTLIN`, the `.kt` mapping, and a `KotlinLanguageServerComponents` registered in `BootLanguageServerInitializer` for the outline), the `kotlin` entries in the VSCode `documentSelector` and activation events, the setting that switches the Kotlin support on, the component scan of the new package in `BootLanguageServerBootApp`, a new `kotlin-compiler-embeddable` dependency, and one additive accessor in `commons-java` (`IType.getDeclaredAnnotations()`). Touch point 7 was not needed (the JDT bridge in 9.2 replaces it): no Java indexing code is changed.


### 3.2 Parallel track

The Java side has no language-neutral facade. Rather than rewrite 200+ files, take a **parallel track** (see 3.1) and extract shared abstractions only where cheap and without touching Java code:

1. **Language-neutral "declaration model" for indexing** (type, method, field, parameter, annotations with resolved attributes, supertypes). Kotlin indexers produce the existing index element classes. Java indexers stay as they are. This avoids touching about 214 JDT-dependent files and reuses `SpringSymbolIndex`, the cache, the metamodel, MCP and the live-data code.
2. A `SpringIndexerKotlin implements SpringIndexer`, plus Kotlin variants of the AST-based reconcilers, hovers etc. only where the value is high.
3. Keep the **hover/completion/definition providers** for later. They need cursor-position node lookup and are the most AST-bound.

### 3.3 Packaging and enablement of the optional Kotlin module

Open decisions, to be settled after the spike once the real footprint is known:

- **VSCode extension (VSIX):** bundle the Kotlin module, or download it on demand on first use of a Kotlin project. Bundling adds the Kotlin compiler / Analysis API size to every VSIX (estimated tens of MB, see 5.3). On-demand download keeps the VSIX unchanged for Java users but needs a versioned artifact (for example on `cdn.spring.io/spring-tools`, like the standalone LS).
- **Claude Code plugin (standalone LS JAR):** same question for `update-local-jars.sh` and `release-standalone-ls.yml`.
- **Eclipse distribution:** not shipped (out of scope, section 1).
- **Loading:** the module is loaded only if it is present **and** enabled. Suggested enablement: a setting (for example `spring-boot.ls.kotlin.enabled`) defaulting to on when present, plus auto-detection, so that the Kotlin stack is only started if a project has Kotlin source folders or `.kt` files.
- **Isolation:** the compiler / Analysis API classes run in their own classloader (or child process, see 4.1). The Kotlin module's adapter code that talks to `SpringSymbolIndex` runs in the LS classloader.

### 3.4 Rejected: bytecode-based indexing of the project's own Kotlin output

**Decision: we will not follow this approach.** It was considered as an interim step, mainly for the headless Claude Code plugin: index the project's **compiled** Kotlin output (`target/classes`, `build/classes/kotlin/main`) with Jandex and the existing `Jar*Scanner` logic, without a Kotlin parser.

Reasons for rejecting it:
- **Too many limitations:**
  - It requires a prior build, so results are stale after every edit.
  - There are no precise source ranges. Line-number tables only cover method bodies, not fields, annotations or declarations.
  - None of the source-based reconcilers can run, so diagnostics would be limited to what bytecode shows.
- **Too different from the current approach:** the project's own Java code is indexed from sources (`SpringIndexerJava`, JDT AST). A bytecode path for own Kotlin code would be a second, structurally different indexing model with its own behavior, which would then have to be replaced by source-level support anyway.

**Kept for reference: Kotlin bytecode quirks.** These still matter for `Jar*Scanner` and Jandex when they index Kotlin-compiled **libraries** on the classpath (section 2.1) [I]:
- An annotation on a property without a use-site target goes to the constructor parameter, then the property, then the field. A property-level annotation lands on a synthetic `getX$annotations()` method, not on the field.
- Top-level functions compile into `FileNameKt` classes. Members of a companion object land on a nested `Companion` class.
- `suspend` functions have an extra `Continuation` parameter. Default arguments create `$default` synthetic methods. `const val` values are inlined at use sites.
- Parameter names need `-java-parameters` or the Kotlin metadata (`@kotlin.Metadata`). Nullability is only visible through `@Nullable` / `@NotNull` annotations.
- Classes are final unless the `kotlin-spring` (allopen) plugin opens them. Bytecode reflects the result.

## 4. Technology research: Kotlin parser and analysis

Requirements: open source, maintained by JetBrains, embeddable in a JVM LS (Java 21+), resolved types, walkable AST.

| Option | Resolved types | Stability | Verdict |
|---|---|---|---|
| **Kotlin Analysis API, standalone (K2)** (`org.jetbrains.kotlin:analysis-api-standalone-for-ide`; `buildStandaloneAnalysisAPISession`) | Yes (K2, `KaSession`) | Official docs say the only supported use is IntelliJ plugin development. Standalone mode is "in development and subject to incompatible changes". [V] Clean Maven Central library artifacts are planned for Kotlin 2.5.0 (per the detekt tracking issue #9676). [V] | **Best candidate.** Wrap it behind our own interface. |
| **PSI-only via `kotlin-compiler-embeddable`** (`KtFile`, `KtPsiFactory`) | No (syntax only) | Internal API, but widely used by detekt and ktlint. [I] | **Fallback / first milestone**, with our own light resolution (imports, same package, classpath lookup via Jandex). Good enough for annotation-driven indexing, weak for inferred types and DSL receivers. |
| K1 `BindingContext` | Yes | Being removed. K2 migration guide: no rollback to the old compiler from 2.4.0. K1 deprecation and deletion items are in the 2.4.20 changelog. [V] | Avoid. |
| FIR directly | Yes (most precise) | Internal, changes nearly every release. [I] | Avoid. More work than the Analysis API for no extra benefit. |
| OpenRewrite `rewrite-kotlin` | Yes (FIR types attached to an LST) | Apache 2.0. Builds on Kotlin compiler internals (`KotlinCoreEnvironment`, FIR sessions). The latest build in the local Maven repository (8.65.0, Oct 2025) still uses Kotlin 1.9.25. [V] Pulls in the OpenRewrite stack. The standalone repo was archived 25 Jan 2026 and work moved into `openrewrite/rewrite`. [V per earlier research] | **Not a dependency, but the best available reference implementation.** Details and lessons in 4.4. |
| JetBrains **Kotlin LSP** (`github.com/Kotlin/kotlin-lsp`) | Yes, internally | Alpha. Partly closed source, read-only mirror, based on IntelliJ. [V] | **Not usable as a library.** [I] See 5.4 for the project-model question. |
| tree-sitter (with the Kotlin grammar), ANTLR Kotlin grammar, `kotlinx-ast` | No | Stable parsers, but not JetBrains-maintained. tree-sitter is fast, error tolerant and supports incremental re-parsing of changed text. [I] | Only viable with fully self-written name and type resolution. **Built as the second prototype** (section 6.2) to compare against the JetBrains-based approach. |
| **JDT bridge**: build JDT DOM nodes from the Kotlin syntax tree; the Java logic gets its bindings from our own binding implementations (not a parser itself, it sits on top of PSI or tree-sitter, optionally the Analysis API for types) | Yes, through Kotlin binding stubs filled by Kotlin type resolution | Depends on package-private JDT API and on our own Kotlin resolution | **Analyzed in 4.3, implemented through a `BindingResolver` subclass** (9.2). |
| UAST | n/a | Part of the IntelliJ platform, not standalone | Not usable. [I] |
| KSP2 | Yes | Built for build-time code generation, not an in-process library | Not suitable. [I] |

### 4.1 Recommended technology path

1. **Milestone 0 (spike):** build a small standalone prototype with the Analysis API standalone session. Feed it a Spring Boot Kotlin project's source folder plus its classpath jars. Resolve `@Bean`, `@Component`, `@RestController` and `@GetMapping("/x")` annotations, and an expression-bodied `@Bean` function with an inferred return type. Measure:
   - Artifact size, startup time, and memory on a large project.
   - Re-analysis time after a single file change on disk.
   - **Unsaved buffers:** whether a changed in-memory document (LSP `didChange` content, not yet saved) can be re-analyzed in an existing session, and how fast. Editor features depend on this, and the standalone session's limited support for changing sources may block it (5.3).
   - Whether the LS fat jar already contains `kotlin-stdlib` (or other Kotlin artifacts) through a transitive dependency, and whether it clashes with the Analysis API's version.
2. **Milestone 1:** PSI parsing plus our own resolution, behind the same internal interface, if the spike shows the Analysis API is too heavy or unstable. The prototypes in section 6.2 (JetBrains parser and tree-sitter) replace the earlier single spike and are compared against the same test corpus.
3. Run the Kotlin stack in an **isolated classloader or child process**, pinned to one Kotlin version. [I] The Analysis API "for-ide" artifacts are not relocated and may clash with other IntelliJ classes. A fat Spring Boot jar also risks Kotlin stdlib conflicts. detekt repackages these artifacts for the same reason. [V/I]

### 4.2 Kotlin and Spring specifics the model must handle [I]

- `kotlin-spring` (allopen) and `kotlin-jpa` (no-arg) compiler plugins. Plugin-generated members are not visible to the Analysis API (detekt #7531). [V] So class openness must be assumed for stereotype classes rather than read from the declaration.
- Primary-constructor injection: constructor parameters are injection points, and nullability and default values affect optionality.
- `@Bean` functions with inferred return types need type resolution.
- DSLs: `beans { }`, `router { }`, `coRouter { }` (lambda with receiver). Detecting them needs receiver type resolution. They are the Kotlin analogue of `WebFnRouteExtractor`.
- `@ConfigurationProperties` data classes with constructor binding.
- Annotation arguments: `const val` from other files, string templates, named arguments, array literals, `vararg`. This needs cross-file constant evaluation.
- Use-site targets (`@field:`, `@get:`, `@param:`) decide which element gets the annotation.
- Top-level functions (compiled to `FileNameKt`), companion objects, `suspend` functions (an extra `Continuation` parameter in the JVM signature), and properties (field plus accessors).
- Mixed Java/Kotlin projects. Kotlin sees Java sources, Java sees Kotlin classes. The Java side works from compiled classes, so a Java file referencing a Kotlin type needs Kotlin output to exist.

### 4.3 The JDT bridge approach: build JDT AST nodes from the Kotlin AST

**Idea (from the project owner):** parse Kotlin with a Kotlin parser and transform the Kotlin AST into JDT AST nodes, because both languages are fairly similar. Then the existing logic (indexing, validations, ...) works on Kotlin sources without being duplicated.

The idea is attractive because 214 files import the JDT DOM (section 2). Whether it works depends almost entirely on **type bindings**, because most of that logic does not look at the syntax alone.

**Verified facts about the current code** [V]:
- 118 of the 214 JDT-dependent files use bindings (`resolveBinding` 128 call sites, `resolveTypeBinding` 50, `resolveAnnotationBinding` 27, `resolveMethodBinding` 22). `AnnotationHierarchies` (meta-annotations), `ASTUtils` and `StereotypesIndexer` are binding-driven. For example, `StereotypesIndexer` returns early when `resolveBinding()` is null.
- JDT internals (checked in the JDT core 3.42 sources in the local Maven repository; the language server uses 3.46):
  - `BindingResolver` is a package-private class with about 58 methods.
  - `AST.getBindingResolver`, `AST.setBindingResolver` and `CompilationUnit.setLineEndTable` are package-private.
  - `ASTNode.setSourceRange` is public.
  - `ITypeBinding` has about 54 methods and is marked `@noimplement`.
  - The 3.42 jar is not sealed and carries no signature files, so a class in package `org.eclipse.jdt.core.dom` from another jar can load. Not yet checked for the jars we ship.
- `AnnotationHierarchies` identifies types by binding key (`getKey()`), not by object identity.
- Only a few call sites compare bindings with each other: `isEqualTo` 5, `isAssignmentCompatible` 2, `isCastCompatible` 2.
- `SpringIndexerJavaAstScanner`, `SpringIndexerJavaContext`, `SpringIndexerJavaParserUtils` and `ASTParserCleanupEnabled` are public classes. `SpringIndexerJavaContext` is built from a `CompilationUnit`, a `TextDocument` and the document content. The constructor dependencies of the scanner have not been checked yet.

**Variants considered:**

| Variant | Verdict |
|---|---|
| Generate Java stub source from Kotlin and parse it with JDT | **Discarded.** |
| Build DOM nodes with `ast.newXxx()`, without bindings | **Rejected.** Such nodes have no bindings, so `resolveBinding()` returns null and the binding-driven logic (about 118 files, including the stereotype indexer) silently skips everything. |
| Build DOM nodes with `ast.newXxx()` plus a `BindingResolver` subclass that returns our own `ITypeBinding` etc. implementations | **Chosen, see 9.2.** Needs a split package in `org.eclipse.jdt.core.dom` (package-private `BindingResolver`), implementations of JDT's `@noimplement` binding interfaces, and its costs are listed under "The `BindingResolver` subclass" below. It needs no change to the Java code. |
| Build DOM nodes with `ast.newXxx()`; the Java logic accesses bindings through a binding-access interface of ours, with a JDT implementation and a Kotlin implementation | **Not chosen.** Every indexer that is enabled for Kotlin needs its binding calls switched to the interface (touch point 7 in 3.1), so each of them is a change to Java code, and the scope grows with every indexer and reconciler. Replaced by the `BindingResolver` variant (9.2). |

**How it would work:**
1. **Syntax:** the Kotlin parser (PSI or tree-sitter, behind the `KotlinSyntaxTree` interface of 6.2) delivers declarations, annotations, type references as written, imports and annotation argument expressions, each with source ranges.
2. **Kotlin name resolution (ours):** a light resolver turns names written in Kotlin into JVM FQNs. It uses a **project-wide Kotlin declaration index** built from the syntax of all Kotlin files, and the **existing Jandex classpath index**. It is much less than a type checker (no expression typing), but it must follow Kotlin's rules (see below).
3. **DOM builder:** creates a JDT `CompilationUnit` per Kotlin file with `ast.newXxx()`, in the **JVM shape** the Java logic expects (see below). Node ranges are set directly in Kotlin coordinates (`setSourceRange`), and the line table is set from the Kotlin document. So there is no position mapping afterwards: `NodeFinder`, locations, hashes from the Kotlin text and `getLineNumber` work on Kotlin offsets.
4. **Binding-access interface** (new, ours): the Java code paths enabled for Kotlin stop calling `node.resolveBinding()` and JDT binding methods directly and use the interface instead, for example `Bindings.of(typeDecl).type(typeDecl)`. The interface returns **our own small binding model** (type, method, field, annotation, member value, package), not JDT's `ITypeBinding`.
   - **How the right implementation is found:** like `AnnotationHierarchies.get(node)` today, which reads a property from the root node (`n.getRoot().getProperty(...)`) [V]. A CU parsed by JDT gets the JDT implementation (also the default when no property is set). A CU built from Kotlin gets the Kotlin implementation.
   - **JDT implementation:** wraps the real `ITypeBinding`, `IAnnotationBinding` etc. and forwards. No behavior change for Java.
   - **Kotlin implementation:** **binding stubs**, plain objects that carry the information from Kotlin type resolution, attached to the nodes we built through a side table (node to stub), the same "syntax element to type" split `rewrite-kotlin` uses (4.4). Types from the classpath (Spring annotations, JDK, libraries) are stubs too, filled from the Kotlin type resolution or from the existing Jandex index, so no real JDT bindings are needed for Kotlin files.
   - **Source of the Kotlin type information:** either the **Analysis API** (resolves Kotlin source and library types including annotations, so our own name resolution in step 2 shrinks to almost nothing) or, without it, our light resolver from step 2 plus Jandex. Which one is decided by comparing prototypes A and B (6.2).
   - **Mixing** happens only inside our model: equality is by binding key in both implementations.
   - **The contract is the binding surface:** the interface contains exactly the operations the enabled code paths need. In `StereotypesIndexer`, `AnnotationHierarchies` and `ASTUtils` these are about 15 distinct binding methods (`getQualifiedName`, `getBinaryName`, `getKey`, `getAnnotations`, `getAnnotationType`, `getValue`, `getSuperclass`, `getInterfaces`, `getDeclaringClass`, `getReturnType`, `getParameterTypes`, `getErasure`, `isAnnotation`, `isInterface`, `getName`). [V, approximate count from a regex scan]
   - **Existing model to compare against:** `commons-java` already has `IType`, `IMethod`, `IField`, `IAnnotation` and `IMemberValuePair`, backed by Jandex or JDT LS [V]. They model classpath elements, not AST bindings, so they may not fit directly, but they should be checked before designing a new model.
5. **Java logic, with the binding change only:** the existing `SpringIndexerJavaAstScanner` and `SpringComponentIndexer` beans run on these CUs through a `SpringIndexerJavaContext` that carries the Kotlin document. Apart from switching their binding calls to the interface (touch point 7 in 3.1), they stay as they are.

**Binding challenges** (the core of the feasibility check):
1. **Scope of the Java change.** Bindings are not only resolved, they are navigated (type, then supertype, then annotations, then annotation type, then member values). So every binding method used on the enabled code paths moves to the interface, not only the 228 `resolve*Binding` calls. `ASTUtils` alone has about 78 binding references, `AnnotationHierarchies` 49 and `StereotypesIndexer` 43 [V]. Only the methods on the first-goal paths are switched; the rest of the Java code keeps calling JDT directly and is not enabled for Kotlin files. Every indexer or reconciler enabled for Kotlin later widens the interface and the Java change, which is why each is a decision of its own.
2. **Regression risk for Java.** The JDT implementation only forwards, and the existing Java tests cover the switched code paths. Risks are mistakes in the mechanical switch (for example null handling) and the small cost of the indirection, which should be measured on a large Java project.
3. **Designing the model.** The model must cover what the enabled code needs and nothing more, stay independent of JDT types (otherwise the Kotlin side has to fake JDT objects again), and include what Kotlin can add: nullability, default values and `lateinit` can be optional fields instead of being impossible to express (challenge 8).
4. **Two representations of one type.** Once Kotlin is compiled, a Kotlin class exists as a Kotlin binding stub and as a real binary binding from the output folder (for example, reached through a Java type's supertype in a Java file). Both must render the **same key** and agree on supertypes and annotations. Keys must follow JDT's key syntax and the exact JVM shape (`int` vs `Integer`, `Unit` to `void`, the extra parameter of `suspend` functions, `@JvmName`, `@JvmOverloads`, mangled names of `internal` and value-class members), because bytecode-based index elements also use JDT-style keys (`JarBindingKeys`).
5. **Kotlin name resolution rules** (only if we resolve names ourselves instead of using the Analysis API). Our resolver must follow Kotlin, not Java:
   - The priority is explicit imports, then same package, then star imports, then default imports (`kotlin.*`, `kotlin.collections.*`, `kotlin.annotation.*`, `java.lang.*` and others).
   - Mapped built-ins have no class files (`Int`, `String`, `Any`, the `kotlin.collections` types) and need a mapping table. Nullability decides primitive vs boxed.
   - Standard-library type aliases (for example `kotlin.collections.ArrayList`, `kotlin.Exception`) exist only in Kotlin metadata [I], and project type aliases must be expanded.
   - Import aliases (`import a.B as C`).
   - Imports of top-level functions and properties map to a facade class (`PathsKt` or `@file:JvmName`). Imports of enum entries and object or companion members are member imports. Telling them from class imports needs the declaration index or Jandex.
6. **Annotation placement and values.**
   - Where an annotation on a property or constructor property lands (param, then property, then field, unless a use-site target says otherwise) depends on the annotation's `@Target`, which must be looked up (Jandex) while building the nodes. Java annotations can never target a Kotlin property, so `@Autowired lateinit var` lands on the field and `@Value` on a constructor property lands on the parameter.
   - Member values in our model must have the same meaning as in the JDT implementation: `String`, boxed primitives, type references for `Foo::class`, enum constant references, arrays for `[...]`, nested annotations.
   - `const val` references (also from other files) and string templates must be evaluated (by the Analysis API or by us), including constant expressions in the interface (the counterpart of `resolveConstantExpressionValue`).
   - Kotlin meta-annotations (`kotlin.annotation.Target`, `Retention`) on Kotlin annotation classes should be presented as the Java ones the compiler emits.
7. **Inferred types.** Kotlin requires explicit types for constructor and function parameters and for supertypes, which is what the stereotype and request mapping indexers mostly need. Only expression-bodied functions (`@Bean fun foo() = Foo()`) and initialized properties lack them. A small *type oracle* (heuristic first, the Analysis API later) covers those.
8. **Kotlin-only semantics** that JDT bindings cannot express: nullability (Spring treats a nullable injection point as optional), default parameter values, `lateinit`, extension receivers, variance (Kotlin `List<Foo>` as a parameter is `List<? extends Foo>` on the JVM). Not relevant for stereotypes, relevant later for bean injection. Our own model can carry them as optional information.
9. **Unresolved names.** The Kotlin implementation should return null rather than invent recovered bindings, and log the unresolved names, so that mistakes show up as missing data rather than wrong data.
10. **Cross-file and mixed projects.** Kotlin-to-Kotlin references across files resolve through Kotlin type resolution (Analysis API or our declaration index) without compiling, which is an advantage of own bindings. Java code referencing Kotlin types keeps resolving through compiled output only, because we do not change the Java parser setup (3.1).

11. **Line table.** `CompilationUnit.setLineEndTable` is package-private [V], so `getLineNumber`/`getColumnNumber` on a hand-built CU need reflection, or line and column lookups go through the interface too. `ReconcileUtils` uses them; the first goal does not.

**The `BindingResolver` subclass (chosen).** A `BindingResolver` subclass in package `org.eclipse.jdt.core.dom` (split package) returns our own implementations of JDT's binding interfaces, with no Java change at all. Its costs (how they are handled is described in 9.2):
- It must load in every distribution (VSIX fat jar, standalone/plugin JAR; OSGi in Eclipse would refuse a split package) and is tied to package-private JDT API that can change with any JDT update. The 3.42 jar is not sealed or signed [V]; the shipped 3.46 is not checked.
- It implements `@noimplement` interfaces (`ITypeBinding` has about 54 methods).
- Real JDT bindings that receive one of ours as an argument (`isEqualTo` 5 call sites, `isAssignmentCompatible` 2, `isCastCompatible` 2 [V]) presumably cast to JDT-internal classes and fail or return false [I].
- Classpath types need real JDT bindings by key (for example through `ASTParser.createASTs(..., bindingKeys, requestor)`) [I].

**JVM shape the DOM builder must produce** [I]:
- Classes: `class`, `interface`, `enum class`, `annotation class`, `data class`, `sealed`, `object` (static `INSTANCE` view) and `companion object` (nested `Companion` class plus a field).
- Modifiers: Kotlin classes and members are `final` unless `open` or opened by the `kotlin-spring` allopen plugin, which must be applied as a rule. Members are `public` by default.
- Properties: a field plus accessors. Constructor `val`/`var` parameters become fields and constructor parameters. Annotation use-site targets decide which node carries the annotation.
- Functions: top-level functions and properties live in a synthetic facade class (`FileNameKt` or `@file:JvmName`). Extension functions get the receiver as first parameter. Default arguments, `@JvmOverloads`, `suspend` (extra `Continuation` parameter), `Unit` to `void`.
- Synthetic nodes (accessors, `Companion`, facade class) get the range of the declaration they come from, or are flagged as synthetic.
- **Identifiers:** JDT's `SimpleName` only accepts valid Java identifiers [I]. Kotlin names in backticks and names that are Java keywords (`default`, `native`, `int`, ...) need a strategy (escape, or skip with a log entry).
- Method bodies are not built for the first goal.

**What carries over, per work area (section 2.2):**

| Area | Fit |
|---|---|
| D. Indexing: stereotypes, request mappings via annotations, `@Bean` methods with explicit return types, config properties, event listeners, data repositories and Feign/HTTP exchange interfaces | **Good.** All are driven by annotations and declarations, so the existing `SpringComponentIndexer` implementations could run on Kotlin, with their binding calls switched to the interface. This is the first goal (6.1). |
| E. Reconcilers | **Mixed.** Declaration- and annotation-based checks can run, but only the Kotlin-applicable ones should (triage 5.7), because some give wrong advice for Kotlin (finality, visibility defaults, constructor injection). Body-based checks do not run. Each reconciler also widens the binding interface and the Java change. |
| F. Quick fixes | **Does not carry over.** `JdtRefactorings` re-parses the document as Java and produces Java text. These must stay disabled for Kotlin documents, and Kotlin fixes would be separate (5.5). |
| G. Hovers, definitions, references, code lenses, annotation completions | **Good for annotation and declaration positions**, because ranges are Kotlin ranges. Anything that reads the text of a node from the document has to be checked. |
| H. Semantic tokens, inlay hints | Plausible for annotation and string positions. Needs checking against the Kotlin extension's own highlighting. |
| I. Embedded languages (SpEL, Cron, queries in string literals) | **Needs separate work.** The snippet offset mapping assumes Java string escapes and text blocks, so Kotlin templates and raw strings need their own mapping. |
| Method bodies (`WebFnRouteExtractor`, `beans {}`, `router {}`) | Not covered for now. Building bodies means expression typing in our resolver, which is the point of diminishing returns. |

**Decision rule for prototype J** (applied in 6.2): go if
- the binding interface for the first-goal code paths stays bounded and documented, and the JDT implementation passes the existing Java tests unchanged, with no measurable slowdown;
- the number of Java files that switch to the interface is acceptable to the project owner (touch point 7 in 3.1);
- the stereotype and request mapping indexers produce the same elements for the Kotlin twin as for the Java twin;
- the other changes in the Java code stay within the touch-point list of 3.1.

No-go if the interface spreads through large parts of the Java code even for the first goal, or the Kotlin side has to fake JDT objects after all. Then either the fallback (`BindingResolver` subclass) is checked, or the direct approaches (prototypes A and B) take over.

### 4.4 What OpenRewrite's `rewrite-kotlin` shows

OpenRewrite parses Kotlin and produces its own tree with types, which is the same kind of translation we are considering. I read the sources of `rewrite-kotlin` 1.19.4 and checked the 8.65.0 build in the local Maven repository (`/m2-host-repo/org/openrewrite/rewrite-kotlin`). Findings marked [V] are from that code.

**How it works [V]:**
1. **Environment.** `KotlinParser` creates a `KotlinCoreEnvironment` (compiler internals), wraps each source in a `LightVirtualFile` to get a PSI `KtFile`, adds the project classpath as JVM classpath roots, creates a **FIR session** (`FirSessionFactoryHelper.createSessionWithDependencies`), builds FIR for the files (`buildFirFromKtFiles`) and runs the compiler's resolution phases (`runResolution`). Type checking and IR generation are commented out. There is a code comment "Add extension registrars when needed here", so **compiler plugins (allopen, no-arg) are not applied**.
2. **Syntax tree from PSI.** `KotlinTreeParserVisitor` (4,473 lines) walks the PSI and creates the tree nodes. It keeps whitespace and comments (`Space` prefixes, padding) because the goal is lossless printing and recipes that rewrite code. We do not need that part.
3. **Types from FIR, attached through a side table.** `PsiElementAssociations` walks the FIR and records, for each PSI element (`element.source.psi`), the FIR elements. The visitor asks it for the type of each PSI node (`type(psiElement)`). `KotlinTypeMapping` (1,359 lines, plus `KotlinIrTypeMapping`, signature builders and caches) turns FIR classes, functions, properties and annotations into OpenRewrite `JavaType` objects (class with supertypes, members, annotations, flags from visibility and modality).
4. **The tree is a Java tree with Kotlin extensions.** `K extends J`: Kotlin classes, methods and variables reuse the Java LST nodes (`J.ClassDeclaration`, `J.MethodDeclaration`, `J.VariableDeclarations`, `J.Annotation`), and Kotlin-only constructs are separate `K.*` nodes (`K.Property`, `K.StringTemplate`, `K.When`, `K.FunctionType`, `K.Constructor`, `K.TypeAlias`, `K.Binary`, ...) or Java nodes with **markers** (`PrimaryConstructor`, `Extension`, `By`, `OmitBraces`, `Elvis`, `AnnotationUseSite`, ...). This is exactly the idea of "transform the Kotlin AST into a Java-like AST" and it works for them, but **at the level of syntax**, not as a JVM view.

**Mapping decisions that are relevant for the bridge [V]:**
- **Classes:** `visitClass` creates a `J.ClassDeclaration` with kind class, interface, enum or annotation, and **adds a `final` modifier when the class has no `open`**. That matches the Kotlin rule our DOM builder needs. The class type comes from FIR.
- **Primary constructor:** it becomes a `J.MethodDeclaration` named `<constructor>` (marked `PrimaryConstructor`, implicit) that is *added to the class body statements*. Constructor parameters that are properties are not turned into fields.
- **Properties:** `val`/`var` become `J.VariableDeclarations` with a `final` modifier or a `var` language-extension modifier. Only properties with accessors, a receiver or constraints become `K.Property`. They do **not** synthesize fields and getters/setters like the JVM does.
- **Annotations:** `visitAnnotationEntry` creates a `J.Annotation`. Use-site targets (`@field:`, `@get:`) are wrapped in a `K.AnnotationType` with a marker. Named arguments become `J.Assignment`, spread arguments `K.SpreadArgument`, string templates `K.StringTemplate`. Annotation types with SOURCE retention are dropped from the type's annotation list.
- **Types stay Kotlin-level:** they come from FIR class ids, so `kotlin.String` stays `kotlin.String` (only Java-origin classes show `java.lang.*`). There is no mapping to JVM types.
- **Maturity:** the visitor still has **60 `UnsupportedOperationException` ("TODO") sites**, and the 8.65.0 build from Oct 2025 is still on Kotlin **1.9.25**, years after Kotlin 2.0. The module is about 25,000 lines in 103 files (a lot of it formatting and style code that we would not need).

**Lessons for the bridge and the prototypes** (as inspiration only, we do not reuse the implementation):
1. **The translation idea is proven, but their target differs from ours.** They keep Kotlin structure for lossless printing and need Kotlin-aware recipes. We need the opposite: a **JVM-shaped Java view** (fields plus accessors, `FileNameKt`, `Companion`, JVM types) so that unchanged Java indexers work. That is why our DOM builder must apply the JVM mapping rules (4.3) that `rewrite-kotlin` deliberately does not apply.
2. **Syntax tree plus type oracle keyed by syntax element** is the right split. `PsiElementAssociations` is a manual version of what the K2 Analysis API gives for free (resolve a PSI element in a `KaSession`). For prototype J this is the side table of the Kotlin binding implementation (node to binding stub), plus an optional *type oracle* for the few types not written in the source (inferred return types of `@Bean fun foo() = Foo()`).
3. **The construct catalogue is useful.** The list of `visit*` methods and `K.*` / marker types is a ready-made checklist of Kotlin constructs for the DOM builder and for the test corpus, in particular: primary constructor, constructor properties, delegation (`by`), extension functions and properties, `when`, lambdas with receivers, trailing lambdas, destructuring, type aliases, `object` and companion, `typeConstraints` (`where`), and annotation use-site targets.
4. **Version coupling is real.** Even a dedicated team behind a mature tool ships on compiler internals pinned to 1.9.x. This supports the plan to isolate the Kotlin stack, to pin one version, and to prefer the Analysis API over FIR internals for anything beyond syntax. For the bridge, which needs only syntax from the Kotlin side, PSI parsing (or tree-sitter) is much less exposed to compiler changes than the FIR session.
5. **Compiler plugins matter.** `rewrite-kotlin` ignores allopen/no-arg, so a `@Component` class without `open` shows as final in their model. Our DOM builder must apply the `kotlin-spring` rule explicitly (4.3).
6. **Inspiration only, not a dependency and not part of our tests.** Its output is an OpenRewrite tree, not JDT nodes, it drags in compiler internals and a fixed Kotlin version, and the repository was archived in January 2026 (per earlier research, not re-verified). Whether `rewrite-kotlin` is currently on the language server's classpath (the 2023 exclusion in commit `fae634b77` is no longer in the `pom.xml` files, but a `rewrite-kotlin` artifact exists in the local Maven repository) needs a `dependency:tree` check, because it would affect jar size and the decoupling rules.

## 5. Open issues and risks

### 5.1 Analysis API instability and packaging
No stability promise. Clean Maven Central artifacts were expected with Kotlin 2.5.0 (around September 2026 according to the detekt issue, where a 7 Oct 2026 comment says detekt is unblocked). [V] They may already be released, which would reduce the packaging part of this risk. Verify the coordinates first (section 7). Mitigation: thin internal interface, pinned version.

### 5.2 Kotlin version coupling
The embedded compiler version must be able to read the project's language level. Newer language features in the project than in our pinned compiler can cause misparses. [I]

### 5.3 Footprint
`kotlin-compiler-embeddable` is about 55-70 MB. [I] (not measured) The Analysis API stack is heavier at runtime. Expect hundreds of MB and seconds of cold start on large projects. [I] The standalone session is built once and incremental update support is limited. [I] The workaround is to rebuild per change batch or to run a separate process.

This is not only a performance question. Editor features (hovers, completions, reconciling while typing) must analyze **unsaved buffer content**. If the standalone session cannot pick up in-memory changes cheaply, those features are blocked, not just slow. The spike must test this explicitly (4.1).

### 5.4 Integration with the JetBrains extension (to be agreed with JetBrains)
The target extension is **"Java and Kotlin by IntelliJ IDEA"** (`jetbrains.intellij-server`).

- It is LSP-based and reached release candidate in Oct 2026. [V]
- After a trial it needs an **IntelliJ IDEA Ultimate subscription**. [V] Users face this cost, and it may affect how many Kotlin users can benefit. Worth raising with JetBrains, for example whether Spring Tools users get a free tier.
- Spring Tools already lists it as Java tooling (`Main.ts`, `JAVA_TOOLING_DESCRIPTORS`). [V] But `java-tooling.ts` only waits for activation and obtains nothing from it. [V]
- **First question to answer:** how do Java projects get their classpath today when this extension is the only Java tooling installed? `classpath.ts` always calls `java.execute.workspaceCommand sts.java.addClasspathListener`, which is a JDT LS command (from our `jdt-ls-extension`), and only reads the `redhat.java` API. [V]
  - If the IntelliJ IDEA extension implements `java.execute.workspaceCommand` with our `sts.java.*` commands, a classpath channel already exists, and Kotlin source roots could be added to the same messages (`CPE` entries). That would be the least work on both sides.
  - If it does not, the existing Java support has no classpath with this extension either, and the integration question applies to Java and Kotlin alike.
  - The same check applies to the `sts/java*` data requests (`commons-vscode/src/java-data.ts`).
- No documented public API was found for other VSCode extensions to read its project model or classpath. [V] (absence in searches only)
- The separate JetBrains Kotlin LSP extension (`jetbrains.kotlin-server`, alpha since 28 May 2026, partly closed source) has an unofficial debugging command `jetbrains.exportWorkspaceToJson` (per a third-party wiki). [V that the wiki claims it; I otherwise] Out of scope (section 1), noted for reference.

Current state is input for the JetBrains discussion, not a blocker. Wishlist to raise:
- A public, versioned way to get project modules, source roots (Java and Kotlin), output folders and the resolved classpath, with change notifications (the equivalent of `sts/addClasspathListener`).
- The type, javadoc, location and type-search services currently provided by JDT LS (`sts/javaType`, `sts/javadoc`, `sts/javaLocation`, `sts/javaSearchTypes`), or agreement that we use the Jandex fallback.
- Which Kotlin compiler plugins (allopen/spring, no-arg/jpa) are active per module.
- Which Kotlin version and language level each module uses (matters for the version pinning in 5.2).

The LS keeps the Maven/Gradle-based classpath computation (`LegacyJavaProjectsService`) as a fallback, since it already works without an IDE classpath provider.

### 5.5 Quick fixes
There is no JDT-like refactoring framework for Kotlin. Options: text edits derived from PSI ranges (simple fixes such as add or remove annotations, changing visibility), or per-fix custom code. Do not plan to port all quick fixes. Start with the 5-10 most common.

### 5.6 License
Kotlin compiler and Analysis API: Apache 2.0. [I] The JetBrains extensions are not ours to bundle. The Kotlin LSP extension ships under a JetBrains free-plugin license, not the repo's Apache-2.0 [V], and the IntelliJ IDEA extension is commercial after the trial. [V]

### 5.7 Triage of Java diagnostics for Kotlin

The ~45 Java reconcilers do not map one-to-one to Kotlin. Some checks don't apply (Kotlin has no field injection in the Java sense; it uses `lateinit var`). Some need different detection (constructor injection via primary constructors, `open` vs `final`, visibility defaults: Kotlin members are public by default). Some only make sense with a different fix.

- Before porting, triage every problem code: applies as-is / applies with different detection / not applicable to Kotlin.
- Record the result in `claude-plugins/research-notes/TODO_quickfixes.md` with a **Kotlin column**, so it stays next to the quick-fix and playbook coverage.
- **Playbooks:** CLAUDE.md requires a playbook per diagnostic code. 48 of 75 playbooks contain Java code blocks. [V] Kotlin diagnostics need Kotlin examples, either added to the same playbook (preferred, one playbook per code) or as a Kotlin section. New Kotlin-only codes need new playbooks and must pass `check-explanations.mjs`.

### 5.8 Testing strategy

- **Parity baseline:** pairs of Kotlin and Java twin test projects with the same Spring structure. Tests assert that index elements, symbols and problem codes match (minus the triaged differences from 5.7).
- **Harness:** reuse `language-server-test-harness` for LSP-level tests of the Kotlin module. Kotlin module tests live in the new module, so Java tests are not affected.
- **CI:** Kotlin test projects need the Kotlin Maven plugin (or Gradle Kotlin plugin) at build time. Tests that need compiled output (for example mixed Java/Kotlin projects, see 2.3) must build these projects first. Check that this fits the existing multi-platform CI (`multiplatform-ls-build.yml`) without slowing the Java build noticeably.
- **Spike results** (memory, time) become benchmark tests, so regressions in the Kotlin stack are visible.

## 6. Implementation plan

### 6.1 First goal: stereotype indexing and the structure view for Spring Boot + Kotlin projects

**Goal:** parse Kotlin sources in the same way Java sources are parsed today, implement the **stereotype indexer** for Kotlin, and show the resulting structure view information for Spring Boot + Kotlin projects. The step beyond this is **request mapping indexing**. All other indexers stay out of scope for now: beans (`@Bean`, injection), config properties, events, data repositories, Feign, HTTP exchange, Spring AI, and the functional DSLs.

Also out of scope for the first goal: reconcilers and diagnostics, quick fixes, hovers, completions, definitions, code lenses, semantic tokens, inlay hints.

**What the Java stereotype indexer does** (`boot/java/stereotypes/StereotypesIndexer`, 380 lines, plus `Stereotype*Element` classes) and what Kotlin must reproduce [V]:
- A **package element** per package declaration, with the package-level annotation types (`StereotypePackageElement`).
- A **class element** per type declaration (class, interface, object, enum, record-like data class), with:
  - the type's FQN and source `Location`,
  - its annotation types, **including meta-annotations** (via `AnnotationHierarchies`, for example `@RestController` to `@Controller` to `@Component`) and annotations inherited from supertypes,
  - the FQNs of **all supertypes**, for `doesImplement`,
  - a **content hash** of the type's source text, filled in after all indexers have run (`context.hashTypeAfterScanning`).
- A **method element** per **annotated** method (`StereotypeMethodElement`): name, label, signature, location, annotation types, content hash. Only annotated methods get elements.
- **Stereotype definition elements** for jMolecules stereotype annotations (`StereotypeDefinitionElement`). Lower priority for Kotlin.
- The elements are the existing, AST-free index element classes, so Kotlin produces the same `CachedIndexElement`s and the structure view (`StructureViewProvider`, tree builder) consumes them unchanged. [V for the classes, I that nothing in the structure view depends on Java-only information]

**What the Kotlin side needs from the parser** (the interface that both prototypes in 6.2 must satisfy):
1. Package name and package annotations of a file.
2. Per class, interface, object, enum and companion object: FQN (including nesting), source range of the declaration and of its name, modifiers, annotations, direct supertypes.
3. Per annotation: the **resolved FQN** of the annotation type, plus the resolved FQNs of its arguments only as needed by the request mapping step (path, method, `consumes`/`produces`, constants).
4. Per function: name, parameter types (for the signature and label), annotations, source range.
5. Supertype closure and annotation meta-hierarchy lookups. Binary types (jars, class folders) are available via the existing Jandex machinery. Types declared in Kotlin sources of the same project need the Kotlin side to answer the same question for source types.
6. Kotlin specifics (from 4.2) that matter at this level: use-site targets, primary-constructor properties, top-level functions, companion objects, `object` declarations, nested and local classes.

**Note on the bridge (4.3):** with the JDT bridge, the parser only has to deliver items 1, 2, 4 and the annotation argument syntax of item 3 as *syntax* with ranges. Name resolution (item 3) is done by our Kotlin resolver. Name resolution may also come from the Analysis API. Supertype closure and meta-annotation lookups (item 5) come from the Kotlin binding stubs behind the binding-access interface, and the stereotype and request mapping indexers are the existing Java ones with their binding calls switched to the interface. The list above is the full requirement for the direct approaches (prototypes A and B).

**Request mapping step (next, still part of this plan):** `RequestMappingIndexer` equivalent for Kotlin: `@RequestMapping` and the `@GetMapping` family on classes and functions, parent path plus function path combination, HTTP methods, `consumes`/`produces`, `@RestController`/`@Controller` detection through meta-annotations, constant and string-template resolution in annotation values, `@Path`-style placeholders. This adds argument resolution (named arguments, `value =`/`path =`, arrays, `const val` constants) on top of the stereotype work. Out of scope for this step: `RouterFunction`/`router {}`/`coRouter {}` DSLs and `@HttpExchange` interfaces.

### 6.2 Prototypes

All prototypes live in the separate Kotlin module (3.1) and are test-driven at first (no language server wiring). They run against the **same test corpus**, so they can be compared directly.

**Test corpus:** Kotlin twins of existing Java stereotype and request mapping test projects, covering at least: stereotypes via meta-annotations, annotated functions, nested classes, `object` and companion objects, data classes, constructor properties, use-site targets, `const val` in annotation values, string templates, named arguments, array and `::class` values in annotations, expression-bodied `@Bean` functions without explicit return type, mixed Java/Kotlin imports, wildcard imports, import aliases, and files with syntax errors (editing state).

**Prototype syntax interface:** a thin `KotlinSyntaxTree` (package, imports, declarations with kinds and modifiers, annotations with argument expression syntax, type references as written, ranges for declaration, name, annotation and argument). It is the first deliverable and is shared by the bridge prototype and by the direct prototypes, so PSI and tree-sitter can both be plugged in.

**Prototype J (first): JDT bridge feasibility check** (4.3), time-boxed, with PSI as the first syntax provider. The first three steps are a spike on the Java side that can stop the approach early, before any Kotlin code is written.
1. **Binding surface audit:** list every binding operation used on the first-goal code paths (`StereotypesIndexer`, `AnnotationHierarchies`, the `ASTUtils` methods they use, the request mapping indexers, including callees). Count the Java files and call sites that would switch to the interface. Compare with the existing `IType`/`IAnnotation` model in `commons-java`.
2. **Interface and JDT implementation:** design the binding-access interface and its model, implement the forwarding JDT implementation, and switch the stereotype code path to it (on a branch). The existing Java tests must pass unchanged. Measure indexing time on a large Java project before and after.
3. **Checkpoint with the project owner:** size of the Java change (touch point 7 in 3.1). Stop or switch to the fallback here if it is too large.
4. `PsiKotlinSyntaxTree`: implement the syntax interface on `kotlin-compiler-embeddable` PSI (`KtFile`), in an isolated classloader (4.1).
5. **Kotlin type information:** first our light resolver with a declaration index and Jandex lookups (4.3, challenge 5) for the first-goal constructs. If prototype A is far enough, also try the Analysis API as the source.
6. **DOM builder and Kotlin binding stubs** for the first-goal constructs (classes, interfaces, objects and companions, enums, constructor properties, annotated functions and properties, annotations with arguments) in JVM shape with Kotlin ranges, with the node-to-stub side table attached to the CU. Inferred types: constructor call and literal heuristic only.
7. **Stereotype indexing through the existing Java code:** run `SpringIndexerJavaAstScanner` and the `SpringComponentIndexer` beans (`StereotypesIndexer`) with a `SpringIndexerJavaContext` built from the CU and the Kotlin document. Assert that the index elements of the Kotlin twin equal those of the Java twin (types, annotation types including meta-annotations, supertypes, annotated methods, locations, content hashes taken from the Kotlin text).
8. **Request mapping:** switch the request mapping code path to the interface, then the same comparison: class and function mappings, path combination, HTTP methods, constants, arrays, named arguments.
9. **Binary oracle tests:** compile the corpus with `kotlinc` and compare the Kotlin binding stubs with the JDT implementation's view of the compiled classes (keys, supertypes, annotations and their placement, method signatures). Compiled bytecode is the ground truth for the JVM shape (4.3, challenge 4).
10. Apply the decision rule of 4.3.

**Prototype A: JetBrains parser with a direct Kotlin model** (the fallback if J is a no-go, and the source of inferred types and body analysis if J is a go).
1. A1: PSI only: declarations, annotations (short names plus imports), supertypes. Reuses `PsiKotlinSyntaxTree` from J.
2. A2: add resolution with the Analysis API standalone session (`buildStandaloneAnalysisAPISession`): source module plus library modules from the classpath given by the project model. Resolve annotation FQNs, supertypes, function signatures, inferred return types, `const val` values.
3. Own Kotlin stereotype and request mapping indexers (direct, no bridge) producing the same index elements.
4. Measure: dependency and artifact size, startup, memory and time for a large project, time to update after a file edit on disk and after an **unsaved edit** (5.3), correctness against the corpus.

**Prototype B: tree-sitter.**
1. Pick a tree-sitter binding for the JVM and a Kotlin grammar. To verify: the maintained Java binding and its Java version requirement (a binding based on the FFM API needs a newer Java than the project's Java 21 source level [I]), native library packaging per platform (Linux, macOS, Windows, x64/arm64), how it fits the signing and notarization requirements of the VSIX and Eclipse distribution builds, and the grammar's maintenance state and coverage of current Kotlin syntax.
2. `TreeSitterKotlinSyntaxTree`: implement the same syntax interface. This makes tree-sitter a **second syntax provider for prototype J** (run the same J tests with it) and the basis of a direct variant.
3. Direct variant: write our own resolution on top (imports, same-package types, wildcard imports, nested types, Kotlin default imports, import aliases, lookups of binary types through the existing Jandex index, in-project declarations from the same parse), then the same direct indexers as in A3.
4. Measure the same things as in A, and additionally the **incremental re-parse** after an unsaved edit, which is the main reason to try tree-sitter.

**Comparison and decision (exit of the prototype phase):**

| Criterion | J: bridge on PSI | J: bridge on tree-sitter | A: JetBrains direct | B: tree-sitter direct |
|---|---|---|---|---|
| Correctness on the corpus (stereotypes, meta-annotations, supertypes) | | | | |
| Correctness for request mapping values (constants, templates, named args) | | | | |
| Reuse of existing Java indexers and reconcilers (how much runs unchanged) | | | none | none |
| Changes needed in the Java code (list of touch points) | | | | |
| Effort for features beyond the first goal (beans, config properties, hovers, completions, diagnostics) | | | | |
| Maintenance by JetBrains / API stability risk | | | | |
| Download / artifact size, native libs, platform packaging | | | | |
| Memory and time on a large project | | | | |
| Update after unsaved edit | | | | |
| Fits the decoupling rules of 3.1 (classloader isolation, optional module) | | | | |

Possible outcomes: the bridge on PSI or on tree-sitter as the main path (A and B reduce to a syntax front-end comparison plus an inferred-type service); a direct approach (J is a no-go); or a hybrid (for example the bridge for declarations, direct Kotlin code for bodies and DSLs). The result and the table go into this document.

### 6.3 Phases

Status (section 9): phase 1 (through a `BindingResolver` bridge instead of a binding-access interface, see 4.3 and 9.2) and phases 5 and 6 are implemented for stereotypes, request mappings and the other component indexers of the Java side. Phase 2 (tree-sitter) and the comparison of phase 4 are open.

| Phase | Scope | Exit criteria |
|---|---|---|
| 0. Preparation | Kotlin Spring Boot test projects with Java twins (5.8) and the corpus for 6.2. Check the open questions in section 7 (IntelliJ extension classpath, `SpringSymbolIndex` hook, injectable registries, reuse of the scanner and context classes outside `SpringIndexerJava`, Kotlin 2.5 artifacts). | Corpus and twins exist and build in CI. |
| 1. Prototype J (first) | Feasibility check of the JDT bridge with a binding-access interface: binding surface audit, interface plus JDT forwarding implementation on the stereotype path, checkpoint on the size of the Java change, then syntax interface, PSI syntax provider, Kotlin type information, DOM builder with binding stubs, stereotype and request mapping indexing, binary oracle tests (6.2). | Go / no-go for the bridge by the decision rule in 4.3, with the findings recorded here. |
| 2. Prototype B (as syntax provider) | tree-sitter syntax provider for the same interface and the J tests re-run. Packaging and Java version findings. | Comparison of the two syntax providers recorded here. |
| 3. Prototype A | If J is a no-go: A1 and A2 with direct indexers. If J is a go: only A2 as an inferred-type and body-analysis service, and a measurement of its cost. | Measurements and corpus results recorded here. |
| 4. Decision | Comparison table (6.2), decision or hybrid, packaging decision (3.3). | Decision documented in this file. |
| 5. Foundations | The touch points of 3.1: `LanguageId.KOTLIN`, `.kt` watching, the `SpringSymbolIndex` hook, `CPE` source language, Maven/Gradle Kotlin source dirs and outputs, VSCode selector and activation. The chosen parser behind the module's interface, isolated. | `.kt` files are discovered and parsed, errors reported in the log. No Java test changes. |
| 6. Stereotype indexer (first goal) | `SpringIndexerKotlin` running the stereotype indexing (through the bridge or direct, per the decision) producing package, class and method elements with meta-annotations, supertypes and content hashes (6.1). Symbols for Kotlin files in the index. | The structure view for a Kotlin Spring Boot project matches the Java twin. Content hashes and incremental updates work (edit a method body, add a class, delete a file). |
| 7. Request mapping indexer | Kotlin request mapping indexing (6.1). Workspace symbols for endpoints. | Request mappings of the Kotlin twin match the Java twin. |
| 8. Later (not planned in detail) | Remaining indexers (with the bridge many of them come almost for free, so these are re-estimated after phase 1), MCP `getProjectDiagnostics` and reconcilers per the triage (5.7), editor features, quick fixes (5.5), functional DSLs. | Re-planned after phase 7. |

## 7. Next steps

1. Prepare phase 0: Kotlin Spring Boot test projects with Java twins and the prototype corpus.
2. Start prototype J (phase 1) with the binding surface audit: it decides the most important architecture question (reuse vs. parallel implementation) and shows the size of the Java change before any Kotlin code is written.
3. Find out how the existing Java support gets its classpath with the IntelliJ IDEA extension (5.4). Does it implement `java.execute.workspaceCommand` with our `sts.java.*` commands? Then prepare the JetBrains discussion from the wishlist in 5.4.
4. Check with `mvn dependency:tree` whether `rewrite-kotlin` (and with it a Kotlin compiler) is already on the language server's classpath today (4.4).
5. Verify the open questions:
   - Can `SpringIndexerJavaAstScanner`, `SpringIndexerJavaContext` and the `SpringComponentIndexer` beans be used from outside `SpringIndexerJava` without changes (visibility, constructor dependencies)? What does `SpringIndexerJava` do beyond that (cache helper, dependency tracker, reconcile service) that the Kotlin variant must replicate?
   - How many Java files and call sites does the binding-access interface touch for the first goal, and does the existing `commons-java` `IType`/`IAnnotation` model fit as its model (4.3)?
   - Only for the fallback: can a `BindingResolver` subclass in package `org.eclipse.jdt.core.dom` load in all distributions, is the shipped JDT 3.46 jar unsealed and unsigned like 3.42, and can real JDT bindings for classpath types be obtained by key?
   - Are the Kotlin 2.5.0 Analysis API standalone artifacts on Maven Central, and under which coordinates? Can `buildStandaloneAnalysisAPISession` re-analyze unsaved buffers?
   - Which tree-sitter Java binding and Kotlin grammar are maintained, what Java version do they need, and can their native libraries be packaged and signed for all target platforms?
   - Are the reconciler, hover and code-action registries injectable (3.1)? Not needed for the first goal.
   - Does anything in the structure view tree builder depend on Java-only element information?

## 8. Sources

- Kotlin LSP: https://github.com/Kotlin/kotlin-lsp, https://youtrack.jetbrains.com/projects/KT/issues/KT-80322
- JetBrains VSCode announcement: https://blog.jetbrains.com/kotlin/2026/05/official-kotlin-support-for-visual-studio-code-is-now-available-in-alpha/
- Analysis API docs: https://kotlin.github.io/analysis-api/index_md.html
- Standalone session builder: https://github.com/JetBrains/kotlin/blob/master/analysis/analysis-api-standalone/src/org/jetbrains/kotlin/analysis/api/standalone/StandaloneAnalysisAPISessionBuilder.kt
- Standalone transitive deps issue: https://youtrack.jetbrains.com/issue/KT-61639
- detekt Analysis API migration: https://github.com/detekt/detekt/issues/9676
- K2 migration guide: https://kotlinlang.org/docs/k2-compiler-migration-guide.html
- OpenRewrite licensing: https://docs.openrewrite.org/licensing/openrewrite-licensing

## 9. Implementation status (GH-2085)

The first steps of the plan are implemented on the branch `features/kotlin-support`, as a sequence of commits that each build and pass the tests of the language server (the only failures are the two `testClassReferenceInValueLink` tests of the properties and YAML editors, which fail the same way without these changes, in this environment). The indexing of Java files is not changed: no Java indexer, reconciler or helper is touched.

### 9.1 Kotlin syntax

Package `org.springframework.ide.vscode.boot.kotlin.syntax`, kept apart from the Java code:

- `KotlinSyntax` is the model of the syntax of a Kotlin file: package, imports, classes (kind, name, supertypes, annotations with their arguments, functions, nested classes), constants, all with source ranges. It is what the rest of the Kotlin support works on, so the syntax provider can be exchanged (tree-sitter, 6.2).
- `KotlinPsiReader` fills it from the Kotlin compiler's PSI parser (`kotlin-compiler-embeddable` 2.2.21). The parser environment is set up on first use. Line separators are normalized for the parser and mapped back, so ranges are offsets into the original text.
- `pom.xml` of the language server: the `kotlin-compiler-embeddable` dependency (version 2.2.21 pinned in the pom, the Spring Boot BOM manages Kotlin 2.3). It adds the Kotlin compiler to the language server jar even for Java-only users (the executable jar grows from about 90 MB to 154 MB), which is the packaging question of 3.3 and still open. The parser classes are only loaded when the first Kotlin file is read.

**Tests:** `KotlinPsiReaderTest` (syntax, ranges, CRLF, syntax errors).

### 9.2 Kotlin types and the JDT bridge

The indexers of the Java side work on JDT ASTs and JDT bindings (`resolveBinding()`, `ITypeBinding`, ...). Instead of changing them (or generalizing them behind an interface, see 4.3), the Kotlin file is turned into a JDT AST whose nodes **resolve to JDT bindings that we provide**. The Java indexers then run on it unchanged.

**Kotlin type resolution** (`boot.kotlin.bindings`):
- `KotlinTypeResolver` resolves the names written in Kotlin to JVM types, with Kotlin's lookup order (enclosing classes, explicit imports with aliases, the package, star imports, the default imports). `KotlinBuiltins` maps the types Kotlin has built in (`Int` to `int`, `Int?` to `Integer`, `List` to `java.util.List`, `Array<T>`, Kotlin's aliases of Java classes). Types come from the Kotlin sources of the project (`KotlinDeclarationIndex`) or from the classpath (Jandex). Names that cannot be resolved are kept as written for types, annotations that cannot be resolved are left out. Cyclic supertypes are cut, as JDT never hands out cyclic hierarchies and the Java helpers that walk the hierarchy do not guard against them.
- The result is the model of the Kotlin side: `ResolvedType`, `ResolvedAnnotation`, `ResolvedMemberValuePair`, `ResolvedMethod`, `ResolvedVariable`, `ResolvedPackage`. They reproduce JDT's quirks where the indexers depend on them: the binary name of a primitive is its descriptor letter (`I`, `V`, `Z`), arrays have descriptor-style binary names, parameterized types have the binary name of the generic type, their names join type arguments with `,` and no space, and interfaces have no superclass.
- **Constants:** annotation arguments are mostly constants, which JDT evaluates for Java through its bindings. The syntax model reads `const val` (top-level, in classes, objects and companions), concatenations and string templates. `KotlinConstantResolver` evaluates them: constants of Kotlin sources in other files and classes, in the class they are used in and in its companion, imported ones, top-level ones, and `static final` constants of Java classes on the classpath, which are read from the class files with ASM (`ClasspathConstants`).

**The AST** (`boot.kotlin.ast`): `KotlinDomBuilder` builds a JDT AST in the shape the JVM sees the classes in: classes, interfaces, annotation types, objects and companion objects as classes (the companion as a nested class `Companion`), functions as methods, annotations with their arguments. Every node has the range of the Kotlin source it stands for, so locations and content hashes point into the Kotlin file. The value of a constant is put into the AST as a literal at the place of the expression, so the unchanged indexer code reads the value from the literal. Enum constants and constants that are not known stay names, like they do for Java sources without a binding. Class literals (`Foo::class`) are `TypeLiteral`s. Names of functions that are not valid Java identifiers (backticks, Java keywords) are changed to valid ones. `KotlinNodeBindings` records what each node stands for in the Kotlin source.

**The bridge** (`boot.kotlin.jdt` and `org.eclipse.jdt.core.dom`):
- `SpringToolsBindingResolver` in `org.eclipse.jdt.core.dom` subclasses the package-private `BindingResolver` and forwards 16 of its 60 methods to the public `JdtBindingSource` interface. The JDT 3.46 jar is **signed**, so loading the class from our jar fails with `SecurityException: signer information does not match` [V]. `JdtBindingResolverBridge` reads the class bytes as a resource and defines them with `MethodHandles.privateLookupIn(AST.class, ...).defineClass(...)`, which gives the class the protection domain of JDT. Verified on the plain test class path and in the Spring Boot exec jar [V]; the standalone language server is packaged the same way, but was not run separately. The overrides are compiled against JDT, so a changed signature in a JDT update breaks the build. Nothing may refer to `SpringToolsBindingResolver` directly.
- `KotlinTypeBinding`, `KotlinMethodBinding`, `KotlinAnnotationBinding`, `KotlinMemberValuePairBinding`, `KotlinVariableBinding` and `KotlinPackageBinding` implement the JDT interfaces as adapters over the Kotlin model. `KotlinJdtBindingSource` maps the nodes of the AST to them. About 1,800 lines including the resolver, most of it trivial defaults. JDT methods the Kotlin model cannot answer return JDT's "unknown" values and are recorded (`KotlinJdtBindings.gaps()`).
- The method return type node is only a placeholder in the AST (`BeansIndexer` resolves it), so it resolves to the return type of the function.

**Additive change in `commons-java`:** `IType.getDeclaredAnnotations()` (default: `getAnnotations()`), overridden by the Jandex type. `IType.getAnnotations()` of a Jandex type reports the annotations of the members of the type as well (for example `@AliasFor` on the methods of `@RestController`), which would be wrong in an annotation hierarchy. The Java code does not use it.

**The contract for the Kotlin side:** the bindings have to look like JDT's. The Java indexers assume complete, acyclic bindings: a binding that is null where JDT never returns null (for example the type of a method) throws inside the indexer, and the scanner drops **all** elements of that type, not just the one element; a cycle makes the hierarchy helpers loop. Plus the risks of package-private JDT API and `@noimplement` interfaces (4.3), mitigated by compiling against JDT and by the Kotlin and Java twin tests (9.3).

**Tests:** `KotlinTypeResolverTest` (name lookup, built-in types, meta-annotations through the classpath and through other Kotlin files, nested classes, cycles, the Jandex annotation pitfall), `KotlinConstantResolverTest`, `KotlinDomBuilderTest` (AST shape, ranges inside their parents, bindings, and method signatures equal to those of the equivalent Java code), `KotlinJdtBridgeTest` (the plain JDT calls on the Kotlin AST), and `KotlinJdtBridgeComponentIndexerTest` (the unchanged `ComponentIndexer` on a Kotlin file: `@Configuration` beans with their `@Bean` methods, event listeners, `@ConfigurationProperties` beans and the main application element).

### 9.3 Indexing Kotlin sources

`SpringIndexerKotlin` (package `boot.kotlin`) is a `SpringIndexer` that is separate from the Java one and shares no state with it. It parses the `.kt` files of the project (the `kotlin` folder next to the Java source folders, and `src/main/kotlin`), registers all of them in the declaration index (the files refer to each other) and runs **the same `SpringComponentIndexer` beans that index Java sources** (`ComponentIndexer`, `StereotypesIndexer`, `FeignClientIndexer`, `HttpExchangeIndexer`) through the unchanged `SpringIndexerJavaAstScanner` on the Kotlin AST (9.2). The results are the existing index elements, so the structure view, symbols and everything downstream treat them like the ones of Java projects. There is no Kotlin specific indexer. Updated and deleted files are handled, and `computeDocumentSymbols` works on editor content.

Changes outside the new package, as discussed in 3.1:
- `SpringIndexerProvider` (new, in `boot.java.utils`) and a small change in `SpringSymbolIndex`: beans of this type contribute additional indexers, which take part in file watching, updates and deletions like the built-in ones. This is touch point 1 and it does not change anything while no provider exists. `KotlinIndexerProvider` is the one provider.
- `BootLanguageServerBootApp`: the new package is added to the component scan.
- `BootJavaConfig.isKotlinSupportEnabled()`: the setting `boot-java.kotlin-support.on` (default: off). Switching it off removes the Kotlin elements again, switching it on indexes the Kotlin files; nothing else is affected. In VSCode it is a user setting in `package.json`; there is no Eclipse preference, since Eclipse is out of scope.

**Types of the classpath** come from a Jandex index of the classpath of the project, as the Java indexer reads the jars directly: the project's own index if it is a Jandex one, otherwise one the Kotlin indexer creates and disposes itself. In VSCode the projects are backed by JDT LS, whose index asks the client for every single type (one blocking request each, with what JDT LS reports about it), which is slow and does not carry annotation attribute values faithfully (arrays come back as `toString()`). Without annotations resolved there are no stereotypes and no beans, so no request mappings.

**Open editors:** the symbols of an open document are computed while `SpringSymbolIndex` holds its own lock, on a request thread. For a project that is not indexed yet, `computeDocumentSymbols` therefore returns nothing and sets nothing up: creating the state of a project asks the project for its index, which for a JDT LS project registers a listener with the project cache while the cache is notifying its listeners, and that deadlocks with the indexing of the project, which needs the same lock. The outline is empty until the project is indexed. In general: code on the request path of open documents must not set up anything that talks to the project cache or the client.

**Tests:**
- `SpringIndexerKotlinTest` indexes the Kotlin twin project `test-stereotypes-support-kotlin` and compares the elements with the ones of the Java original: annotation types including meta-annotations, supertypes and method elements are equal. It also covers locations in the Kotlin file, content hashes, the main application element, new, changed and deleted files, and switching the support off and on.
- `SpringIndexerKotlinRequestMappingTest` indexes a Kotlin twin of the request mapping test project (`test-request-mapping-symbols-kotlin`) next to the Java original and compares the mappings: path, request methods, content and accept types, version and method signature, for more than 25 mappings. It also covers constants (different class, same class with companion, class file constant, chains, concatenation, templates, top-level constants), mappings inherited from superclasses and interfaces, locations, new, changed and deleted controllers, and the workspace symbols.
- `SpringIndexerKotlinClasspathIndexTest` runs the indexer on a project whose own index must not be used, and covers the outline before and after indexing.
- `KotlinStructureTreeTest` calls the structure command and checks that the Kotlin types are grouped by stereotype, with their request mappings.

### 9.4 IDE integration

- **Language server:** `LanguageId.KOTLIN` and the `.kt` mapping in the language computer of `BootLanguageServerBootApp` (touch point 2 in 3.1). `KotlinLanguageServerComponents`, registered in `BootLanguageServerInitializer`, serves the outline (document symbols from the index). Kotlin is deliberately not one of the languages of the Java components, whose hovers and reconcilers read the text of a document as Java. The components take documents reported as `kotlin` or `plaintext` and answer only for `.kt` files, because a client reports `.kt` files as plain text when none of its extensions defines the Kotlin language.
- **VSCode client:** `kotlin` in the `documentSelector` and as an activation event, and the `boot-java.kotlin-support.on` setting. The selector is by language, so the outline needs an extension that defines the Kotlin language. A selector by file pattern (`{ scheme: 'file', pattern: '**/*.kt' }`) would also cover setups with only the Red Hat Java extension; the server side already accepts that.
- Workspace symbols and the structure view are global (not tied to open documents) and work from the index.

**Tests:** `SpringIndexerKotlinRequestMappingTest` asks for the outline of a Kotlin file through `textDocument/documentSymbol` (the same symbols as for the Java file, also when the client reports the file as plain text, and none for other plain text files). Not verified in a running VSCode.

### 9.5 Findings and limits so far

- **The stereotype and request mapping indexers produce the same elements for the Kotlin twin projects as for the Java originals**, with the Java indexers unchanged.
- The structure tree of a Kotlin project differs from the Java twin at the top: the Java project has package stereotypes from `package-info.java`, Kotlin has no package annotations.
- **Not covered yet** (all in the plan, not implemented): enum classes, top-level functions and properties, the primary constructor and properties (so beans of Kotlin classes have no injection points yet), function bodies, supertype and parameter nodes in the AST (so the event type of an `@EventListener` method is empty), annotation use-site targets (they are read but the placement is not applied), the `kotlin-spring` allopen rule, Kotlin compiler plugins, inferred types (functions without a declared return type resolve to `void`, which is wrong data rather than missing data, and is why the signature of an expression-bodied function differs from the one of the Java twin), mixed Java/Kotlin projects (Java files do not see Kotlin classes without compiled output), line and column numbers on the Kotlin AST (`CompilationUnit.getLineNumber` is not set), dependencies between files (a changed constant does not index the files that use it again), persistence of the index, reconcilers, and the functional router DSLs.
- **Open from the plan:** the tree-sitter prototype, the comparison of syntax providers, packaging (3.3), classpath and source folders from the JetBrains extension (5.4: the indexer finds Kotlin folders by convention), and the Eclipse side.

### 9.6 Next steps

1. Extend the shape of the AST: primary constructors and properties (injection points, `@Autowired lateinit var`), top-level functions, supertype and parameter nodes. The unchanged `ASTUtils.findInjectionPoints` and the event listener indexing then work on them, as long as the bindings of the new nodes are complete.
2. Inferred return types for `@Bean` functions (a type oracle, 4.3 challenge 7), then the dependency tracking between Kotlin files.
3. The tree-sitter syntax provider behind `KotlinSyntax`, and the comparison with the PSI based one (6.2).
4. Decide about packaging (3.3): the Kotlin compiler as an optional module or an on-demand download.
