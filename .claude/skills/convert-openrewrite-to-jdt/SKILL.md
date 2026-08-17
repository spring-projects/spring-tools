---
name: convert-openrewrite-to-jdt
description: Converts a Spring Boot language server quick fix in headless-services from an OpenRewrite recipe to a JDT-based refactoring, following this repo's stated goal of moving away from OpenRewrite recipes for Java quick fixes. Use when asked to convert, migrate, or port an OpenRewrite recipe (in commons-rewrite) to a JDT refactoring, or when implementing a new Java quick fix that should use JDT instead of OpenRewrite.
---

# Convert an OpenRewrite recipe to a JDT refactoring

## Background

Every Java quick fix in `spring-boot-language-server` shares one diagnostic/CodeAction
pipeline (`boot/java/reconcilers/JdtAstReconciler` → `ReconcileProblemImpl` →
`SimpleLanguageServer.createProblemCollector` → LSP `Diagnostic`/`CodeAction`/
`executeCommand` → `QuickfixRegistry`). Only the *fix descriptor* and its *execution
engine* differ between the two mechanisms:

| | OpenRewrite (old) | JDT (target) |
|---|---|---|
| Descriptor | `FixDescriptor` (`commons-rewrite/.../java/FixDescriptor.java`) | `JdtFixDescriptor` (`boot/java/jdt/refactoring/JdtFixDescriptor.java`) |
| Quickfix type id | `RewriteRefactorings.REWRITE_RECIPE_QUICKFIX` | `JdtRefactorings.JDT_QUICKFIX` |
| Fix logic | `org.openrewrite.Recipe` subclass; re-parses source with OpenRewrite's own parser | `JdtRefactoring` subclass; runs against the *same* JDT `CompilationUnit` used for reconciling |
| Scope | `RecipeScope.NODE/FILE/PROJECT`, generic | No generic scope concept — a `JdtFixDescriptor` just lists the `docUris` to apply to; each refactoring implements its own "multiple targets" story (e.g. `int... offsets`) |

## Workflow

1. **Read the recipe and its test.** Recipe lives in
   `headless-services/commons/commons-rewrite/src/main/java/org/springframework/ide/vscode/commons/rewrite/java/`
   (or `org/openrewrite/java/spring/...`). Its test (sibling `src/test` tree) is the
   ground truth for the before/after behavior you must preserve.

2. **Find the reconciler wiring the recipe.** Grep the recipe's class name under
   `spring-boot-language-server/.../boot/java/reconcilers/`. Read how it detects the
   problem (JDT AST + resolved bindings + `AnnotationHierarchies`) and what
   `FixDescriptor`(s)/`RecipeScope`(s) it builds via `ReconcileUtils.setRewriteFixes`.

3. **Design the `JdtRefactoring`.** Look at existing examples in
   `boot/java/jdt/refactoring/` for the closest shape:
   - *Offset-anchored, single or batched fix* — `ChangeMethodVisibilityRefactoring`,
     `RemoveAnnotationRefactoring`, `AddAnnotationRefactoring` (constructor takes the
     `int` offset(s) the reconciler already found; `apply()` re-locates the node via
     `NodeFinder`).
   - *Offset-anchored, re-derives related nodes* — `ExtractRequestMappingParentPathRefactoring`
     (class + method offsets, matches annotations by simple name).
   - *Self-scanning, no offsets* — `MovePathToRequestMappingRefactoring` (used when the
     fix genuinely needs to re-scan a whole compilation unit, e.g. for a "fix all in
     file" quick fix without pre-collected offsets).

   Prefer an offset-based design when the reconciler already found the exact node(s) —
   it's cheaper and matches most of the codebase. Only self-scan when you truly need to
   reprocess a whole file.

4. **Match by simple name, not resolved bindings, inside the refactoring.**
   `JdtRefactoring.apply()` runs with real bindings in production (via
   `CompilationUnitCache`), but this class's own unit tests parse with an *empty*
   classpath (see `ExtractRequestMappingParentPathRefactoringTest`), so
   `resolveTypeBinding()`/`resolveBinding()` return `null` there. Match annotation/type
   names via `JdtRefactorUtils.extractSimpleName(...)` on the syntactic type name, the
   way every existing `JdtRefactoring` does — don't rely on resolved bindings inside
   these classes. (The *reconciler*, which always runs with a real classpath, is the
   right place to use bindings/`AnnotationHierarchies` for the actual diagnostic
   detection — leave that binding-based logic alone.)

5. **Constructor must be Gson-serializable and the class concrete/named** (not a
   lambda or anonymous class) — see `JdtRefactoring`'s javadoc. Only primitives,
   Strings, ints/offsets, `List<String>`, and similar simple records/value objects.

6. **Reuse `JdtRefactorUtils` first.** It already has `addImport`, `removeImports`,
   `toLspTextDocumentEdit`, `extractSimpleName`/`extractPackageName`, `newStringLiteral`,
   `markerAnnotationLike`, `findValueOrPathMemberValuePair`. Add a new helper there
   (not a private method in your new class) if a second refactoring will need it too.

7. **Register the new class in `IndexGsonTypeFactories.jdtRefactorings()`.** This is
   mandatory, not automatic: `RegisteredSubtypesTypeAdapterFactory` deliberately never
   falls back to `Class.forName` when deserializing (security hardening against a
   malicious/buggy LSP client instantiating arbitrary classes), so every concrete
   `JdtRefactoring` subtype needs an explicit `registerSubtype(...)` call or
   deserialization throws.

8. **Rewire the reconciler**: replace `FixDescriptor`/`RewriteRefactorings.REWRITE_RECIPE_QUICKFIX`
   with `JdtFixDescriptor`/`JdtRefactorings.JDT_QUICKFIX` (see
   `BeanMethodNotPublicReconciler.addQuickFixes` for the pattern). Flag these to the
   user rather than silently deciding:
   - **Project-wide scope** — the JDT engine has no built-in "whole project" mode; a
     `JdtFixDescriptor` only applies to the exact `docUris` listed. None of the
     already-converted reconcilers offer a project-wide JDT quick fix. Default to
     dropping it unless told to keep it (which means the reconciler must enumerate
     every project `.java` source file itself and list them all as `docUris`).
   - **Detection gaps** — some recipes handle annotation forms/cases the reconciler's
     diagnostic logic never actually detects (e.g. a keyword-form attribute the recipe
     supports but the reconciler only checks a shorthand form). Ask whether to close
     such gaps while converting, or preserve bug-for-bug parity with today's behavior.

9. **Old recipe cleanup** — only delete the OpenRewrite recipe + its test after
   grepping for other references and confirming with the user; some recipes may still
   be used elsewhere.

10. **Tests**:
    - Port the recipe's test cases into a new unit test for the `JdtRefactoring`,
      mirroring `ExtractRequestMappingParentPathRefactoringTest`: bare
      `ASTParser.newParser(AST.JLS25)` with an empty environment, `ASTRewrite.create(cu.getAST())`,
      `apply(rewrite, cu)`, `rewrite.rewriteAST(doc, formatterOptions)`, `edit.apply(doc)`,
      then compare the resulting source text.
    - Update the reconciler's own test (extends `BaseReconcilerTest`) — the quick fix
      count usually changes.

11. **Build/verify**:
    ```bash
    cd headless-services
    # only if you see "cannot find symbol" from stale local .m2 artifacts:
    ./mvnw -q -o install -pl spring-boot-language-server -am -DskipTests
    ./mvnw -q -o test -pl spring-boot-language-server \
      -Dtest=<NewRefactoringTest>,<ReconcilerTest> -Dsurefire.failIfNoSpecifiedTests=false
    ./mvnw -q -o test -pl commons/commons-rewrite   # if you removed a recipe/test from this module
    ```

12. **Plugin docs** — if `claude-plugins/spring-tools/explanations/<ProblemType code>.md`
    exists for this diagnostic, check its before/after examples still match; usually no
    change is needed since that file documents observable behavior, not implementation.

13. **Copyright header** — add/bump the current year in the EPL header of every file
    you touch, per this repo's root `CLAUDE.md`.

## Key files

| Purpose | Path |
|---|---|
| JDT refactoring interface | `spring-boot-language-server/.../boot/java/jdt/refactoring/JdtRefactoring.java` |
| JDT execution engine | `.../jdt/refactoring/JdtRefactorings.java` |
| JDT fix descriptor | `.../jdt/refactoring/JdtFixDescriptor.java` |
| Shared JDT AST helpers | `.../jdt/refactoring/JdtRefactorUtils.java` |
| Gson polymorphic registration | `.../boot/index/cache/IndexGsonTypeFactories.java` (`jdtRefactorings()`) |
| Reconciler examples | `.../boot/java/reconcilers/*Reconciler.java` |
| Old-style OpenRewrite recipes | `commons/commons-rewrite/src/main/java/org/springframework/ide/vscode/commons/rewrite/java/` |
