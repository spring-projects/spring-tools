# Including Project Dependencies in the Logical Structure View

Design plan for letting users pick, per project, a set of *dependencies* whose elements are included
in that project's tree in the Logical Structure view.

Issue: `GH-2004`.

Status: **steps 1, 2, 1b and 4 implemented**; step 3 (root packages) deliberately deferred - see its
section below. Companion document to
[`structure-diff-view.md`](structure-diff-view.md), which describes the existing diff feature on the
same tree and is a good model for how this area is built and documented.

**Prerequisite, done:** [`structure-diff-elements.md`](structure-diff-elements.md) — element-based
structure snapshots — has landed. It introduced the `StructureElements` abstraction that everything
the tree builder reads goes through, and
`StructureViewProvider.createTree(project, StructureElements, groups, updateMetadata)`, which builds
a tree from any `StructureElements`. This plan plugs into exactly that seam (see 1.5 and 2.3).

## Goal

1. For each project in the structure view, the user can select which of its dependencies to include
   in the tree.
2. The selectable list identifies a dependency either by **group id + artifact id** (a JAR
   dependency) or by **workspace project name** (a dependency that resolves to an open project).
3. The classpath model must let us tell **JAR dependencies** apart from **dependencies resolved to
   open projects in the workspace**.
4. The mechanism that produces the elements for a dependency is **decoupled behind an SPI**, so
   several implementations can coexist: one reading from open workspace projects, one reading from
   JAR dependencies.

**The elements of an included dependency appear as elements of the project itself — there is no
distinction in the tree.** No "Dependencies" section, no dependency-specific node kinds, no
separately built subtrees. The tree is computed on the backend in one go, by feeding the host's and
all selected dependencies' stereotype elements into the same project tree creator that builds the
tree today.

## Including dependencies and the diff feature are mutually exclusive

The user turns on one or the other, never both. Turning one on turns the other off. The structure
view is therefore always in exactly one of two modes, for the whole view (not per project):

| | **Diff mode** (default) | **Dependency mode** |
|---|---|---|
| Tree content | each project's own elements only | each project's own elements **plus** those of its selected dependencies, in one tree |
| Change information | as today: per project, against that project's own baseline | none — no change attributes, no baseline information on the tree |
| Diff UI (highlighting, hide unchanged, baseline commands, "Show Changes") | available | hidden |
| Baseline capture and `GitBaselineTracker` | as today | as today, untouched, in the background |

Why this cut: a baseline describes one project's own structure. A composed tree mixes several
projects' elements, so no single baseline describes it, and merely including a dependency would make
all of its elements look "new".

What the exclusion buys - everything below is simpler because of it:

- **The diff feature is not touched at all.** No code in `StructureSnapshotStore`,
  `StructureBaselineStorage`, `GitBaselineTracker` or `StructureTreeDiffer` changes. Baselines never
  see a dependency selection: capture goes through `StructureViewProvider.captureSnapshot`, which
  only ever reads the project's own index.
- **Dependency mode never diffs.** A composed tree is built and returned; no baseline is looked up,
  no baseline tree is rebuilt, no change markers are computed. Composed elements therefore need
  nothing the diff needs (stable node identities across captures, content hashes that mean
  something, snapshot support) - which matters most for JAR elements in step 4.
- **The selection is a pure view setting**, like `groups`: it lives in the client and travels with
  each request (see 1.3). No server-side storage.
- **Switching back to diff mode is instant and correct**: baselines kept being captured all along.
- **The protocol needs no mode flag.** The client sends the `dependencies` selection only while in
  dependency mode; the server treats a request carrying a non-empty selection as a dependency-mode
  request and skips baseline annotation for every project in it (see 2.1).

Deliberately not planned: diffing a composed tree. It would be possible on top of the element
snapshots, but it is exactly the combination this design avoids.

## The four steps

| Step | Scope | Outcome |
|---|---|---|
| **0** | **Done.** Element-based snapshots ([`structure-diff-elements.md`](structure-diff-elements.md)) | Group selection no longer disturbs the diff; `StructureElements` in place |
| **1** | Selection UI + persistence + the selection reaching the tree builder, which ignores it | Nothing changes in the tree. Everything around it is in place. |
| **2** | **Done.** Dependency mode: include the stereotype elements of selected **workspace project** dependencies; mutual exclusion with the diff feature | Works wherever the dependency's packages nest under the host's; disjoint packages contribute nothing yet |
| **1b** | **Done.** JAR dependencies offered in the picker, with group/artifact ids (pulled forward from step 4) | Jars can be selected |
| **4** | **Done, out of order** (see below). JAR dependencies: scanning JARs for stereotype elements | A selected JAR's own types contribute, same package-scoping as step 2 |
| **3** | Root packages - **deferred, not started** | Dependencies with disjoint package roots would appear too |

Step 0 has its own document. Steps 1, 1b, 2 and 4 are detailed below in that order; step 3, done
last of the four, is detailed last.

---

## What exists today (the starting point)

Tree construction, in
`headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/java/commands/`:

- `StructureViewProvider.createTree(project, cachedIndex, updateMetadata, selectedGroups)` builds a
  project's live tree: it wraps the index in an `IndexStructureElements` and delegates to
  `createTree(project, StructureElements, selectedGroups, updateMetadata)`, which picks
  `ModulithStructureView` or `JMoleculesStructureView` and returns a `JsonNodeHandler.Node` root.
  `createCompleteTree(project)` backs the MCP `getLogicalStructure` tool and `diffAgainstBaseline`;
  baseline capture uses `captureSnapshot(project)` and builds no tree at all.
- `JMoleculesStructureView` drives jMolecules' `ProjectTree`, which calls
  `StructureProvider.extractPackages(application)` once, renders one package node per returned
  package, and then groups `extractTypes(pkg)` by stereotype beneath it (`ProjectTree.process`).
  `TreeConfig.defaults()` has `skipSinglePackageNode == false`, so the single main application
  package node is rendered today.
- `ToolsStructureProvider` is that provider for the jMolecules view: `extractPackages` returns the
  one main application package, `extractTypes` returns the indexed `StereotypeClassElement`s **of
  that one project** whose FQN starts with the package name.
- `ApplicationModulesStructureProvider` is the Modulith equivalent: `extractPackages` returns one
  package per application module, `extractTypes` filters that same single project's types by the
  module's base package.
- `SpringIndexCommands` exposes `sts/spring-boot/structure` (all/affected projects, with `groups`
  and `compareAgainst` maps keyed by project name) and `sts/spring-boot/structure/groups`.
  `createAnnotatedTree` builds a project's tree and then annotates it against the project's baseline
  (`HAS_BASELINE`, `COMPARED_AGAINST_*`, per-node `change`).
- `StructureSnapshotStore` captures element snapshots via `captureSnapshot`; `GitBaselineTracker`
  captures automatically from git activity, per project, with no client request involved.
  `annotateWithChangesSinceBaseline` rebuilds a baseline into a tree and diffs it against the live
  one.
- `CachedSpringMetamodelIndex` is keyed by project name already, so reading another project's index
  elements through the same cache instance costs nothing extra.
- `StereotypeCatalogRegistry.getCatalogOf(project)` builds one catalog per project from
  `ProjectBasedCatalogSource(project)`, i.e. from that project's **classpath** — which contains the
  dependencies' jars and output folders.

VSCode client, `vscode-extensions/vscode-spring-boot/lib/explorer/`:

- `structure-tree-manager.ts`: the `structure.grouping` command is the template for a per-project
  multi-select picker persisted in `workspaceState` and sent with every `structure` request. The
  diff UI state is two `PersistedToggle`s — `highlightChanges` and `hideUnchanged` — each mirrored
  into a `when`-clause context key of the same name. Baseline commands: `captureBaseline`,
  `clearBaseline`, `selectBaseline`, `showChanges`.
- `nodes.ts`: `contextValue` markers (`project`, `changed`, …), and the project-row tooltip line
  "Comparing against …" / "No logical structure baseline captured yet".
- `package.json`: the corresponding command, `view/title` and `view/item/context` contributions.

Eclipse: `eclipse-language-servers/org.springframework.tooling.boot.ls/src/.../views/`
(`StructureClient`, `GroupingDialogModel`, `LogicalStructureView`).

### What the classpath already tells us

- **Workspace project dependencies are already on the classpath, as such**, whenever it comes from
  the Java tooling (JDT-LS in VSCode, the Eclipse plugin - both via
  `jdt-ls-extension/.../ClasspathUtil`). `ClasspathUtil.resolve` reads
  `javaProject.getResolvedClasspath(true)`, which expands the Maven and Gradle classpath containers.
  With m2e's workspace resolution on (and Buildship for Gradle project dependencies), a dependency
  on an open workspace module arrives as a `CPE_PROJECT` entry, not a jar. `ClasspathUtil`
  flattens it into the referenced project's source folders (`resolveDependencyProjectCPEs`), each a
  CPE with `kind=source`, `isOwn()==false` and `extra["project"]` = the referenced project's
  location. A project's own source folders carry `extra["project"]` too, but with `isOwn()==true`.
  So *"source, not own, with `extra["project"]`"* already means *"comes from workspace project
  X"* - and `SpringProjectUtil.hasDependencyStartingWith` already relies on exactly that.
  - Not yet confirmed: that JDT-LS enables m2e's workspace resolution by default (the only m2e prefs
    in this repo are for the Eclipse plugin's own projects, set to `true`). Check early in step 1.
  - The project **name** is not carried, only its location - the index is keyed by name.
- **The standalone LS does not have this.** `MavenProjectClasspath` / `GradleProjectClasspath` - the
  standalone LS used by the Claude plugin, and **the test harness** - do no workspace resolution;
  inter-module dependencies arrive as jars from the local repository. Accepted for now: the feature
  simply offers no workspace-project dependencies there (see "Decisions").
- **JAR dependencies had no GAV** - `CPE` knew a path plus a name/version *guessed from the jar
  file name*. Step 1b added it, see there.

---

## Step 1 — Selecting dependencies

**Isolated, shippable, and changes nothing about the tree.** The user can open a quick pick on any
project row, multi-select its dependency projects, and have that selection survive a restart. The
language server discovers the candidates and receives the selection with every structure request,
and the tree builder ignores it. No mode switch yet, and no interaction with the diff feature — that
arrives with step 2, when the selection starts to have an effect.

> **As implemented** - where it differs from the text below:
> - No `ProjectDependency`/`DependencyKind` types in `commons-java`. `ClasspathDependencyResolver`
>   (commons-java) returns a plain `WorkspaceProjectDependency(projectName, location)` record, and
>   the kind lives on the language server's `DependencyDescriptor.Kind`. A JAR model gets added in
>   step 4, when something produces JAR dependencies.
> - `ClasspathUtil.createSourceCPE` writes the name key (`CPE.EXTRA_PROJECT_NAME`) for *every*
>   source entry, own ones included - simpler, and harmless. `CPE` gained `getProjectLocation()`,
>   `getProjectName()` and `Classpath.isWorkspaceProjectDependency(cpe)`.
> - `DependencyDescriptor` has a `location` field (shown in the picker) and no `supported` flag -
>   at the time this step was written, no source could supply a JAR's elements yet; step 4 has
>   since closed that gap (see step 1b and step 4).
> - `SpringIndexCommands` resolves the selected ids (`StructureDependencySources.resolve`) and
>   hands `StructureViewProvider.createTree` the resolved descriptors, which it ignores.
> - Still to check against a real JDT-LS: that m2e's workspace resolution is on by default there.

### 1.1 Classpath dependency model and discovery

New in `commons-java`, package `org.springframework.ide.vscode.commons.java`:

```java
public enum DependencyKind { WORKSPACE_PROJECT, JAR }

public sealed interface ProjectDependency {

    /**
     * Stable identifier used to select this dependency; deliberately version-free so that a
     * selection survives a version bump.
     *   workspace project: "project:<projectName>"
     *   jar with a GAV:    "gav:<groupId>:<artifactId>"
     *   jar without a GAV: "jar:<fileName-without-version>"
     */
    String id();
    DependencyKind kind();
    String displayName();
    Gav gav();          // may be null

    record WorkspaceProject(String projectName, URI location, Gav gav) implements ProjectDependency {}
    record Jar(Gav gav, String name, String version, Path path) implements ProjectDependency {}
}
```

No Maven- or Gradle-specific resolution is needed: the classpath already says which dependencies
are workspace projects (see "What the classpath already tells us").

**One small addition to the classpath**, in `ClasspathUtil.resolveDependencyProjectCPEs`: add the
referenced project's **name** (`projectPath.segment(0)`) as a second extra key, e.g.
`extra["projectName"]`, next to the existing `extra["project"]` location. `CPE` gets typed accessors
for both (`getDependencyProjectName()`, `getDependencyProjectLocation()`). Keep `extra["project"]`
exactly as it is: `SpringProjectUtil` reads it, and it crosses the wire. Fallback for a classpath
sent by an older JDT-LS extension without the name: look the project up by location among
`projectFinder.all()`.

```java
public class ClasspathDependencyResolver {
    public ClasspathDependencyResolver(JavaProjectFinder projectFinder) {...}
    public List<ProjectDependency> dependenciesOf(IJavaProject project);
}
```

It folds the flat CPE list into distinct dependencies:

- source CPEs with `!isOwn()` and `extra["project"]` → one `WorkspaceProject` per distinct
  referenced project (a project contributes several source folders), named from
  `extra["projectName"]` or, failing that, by location lookup;
- non-system, non-test binary CPEs → one `Jar` each (listed only from step 4 on).

Test-scope (`isTest()`) and system entries are excluded: the structure view is about the
application. That is all - no GAV matching, no GAV cache, no heuristics.

### 1.2 The discovery half of the SPI

Requirement 4's decoupling starts here, with only the half step 1 needs. Step 2 adds the
element-supplying half to the same interface; defining that half now, with nothing calling it, would
be speculative.

```java
public record DependencyDescriptor(
        String id,                 // ProjectDependency.id()
        DependencyKind kind,
        String displayName,        // project name, or artifactId
        String groupId,            // null when unknown
        String artifactId,
        String version,
        String projectName,        // null for a JAR dependency
        boolean supported          // false when no source can supply elements for it yet
) {}

public interface StructureDependencySource {
    List<DependencyDescriptor> discover(IJavaProject project);
    boolean supports(DependencyDescriptor dependency);
}

public class StructureDependencySources {   // Spring List<StructureDependencySource> injection
    List<DependencyDescriptor> discoverAll(IJavaProject project);  // union, deduped by id,
                                                                   // projects first then jars,
                                                                   // each alphabetical
}
```

`DependencyDescriptor` is what the client renders: requirement 2 is the `groupId`/`artifactId` pair
*or* the `projectName`, discriminated by `kind`.

Step 1 ships one implementation, `WorkspaceProjectDependencySource`: `discover` returns the
`WORKSPACE_PROJECT` entries from `ClasspathDependencyResolver` that `projectFinder.all()` actually
knows (open *and* indexed), each with `supported = true`. `JarDependencySource` followed in step 1b.

> **Superseded by step 1b:** jars were first meant to stay out of the picker until step 4. They are
> now offered already, and simply contribute nothing until jar scanning exists.

### 1.3 Persisting the selection — client-side, like `groups`

The selection is persisted in the VSCode client's `workspaceState` under
`vscode-spring-boot.structure.dependencies`, as `Record<projectName, dependencyId[]>` — the same
shape and mechanics as `vscode-spring-boot.structure.group` — and sent with every
`sts/spring-boot/structure` request.

This is consistent with how the view's other per-project settings (`groups`, `compareAgainst`) work,
and needs no new server-side storage. It is safe because of the mutual exclusion: the selection
never reaches baseline capture (`captureSnapshot` reads only the project's own index), so nothing
server-side needs to know it outside of a request.

Accepted consequence: the MCP tools do not share the IDE's selection. They get their own explicit
parameter instead (step 2), which is the more predictable contract for an agent anyway.

**Stale ids are kept, not pruned.** A selected dependency can vanish because its project was closed
or the dependency was removed from the build file. Discovery simply will not list it, so it silently
stops contributing; keeping the id means the selection comes back when the project is reopened. The
server filters the received ids against discovery, so nothing downstream ever sees a stale id.

### 1.4 Protocol

New command in `SpringIndexCommands`, on the existing `messageWorkerThreadPool`, mirroring
`.../structure/groups`:

- `sts/spring-boot/structure/dependencies` — argument: a project name (or none for all projects).
  Returns `Dependencies(String projectName, List<DependencyDescriptor> dependencies)`, or a list of
  those.

`StructureCommandArgs` gains:

- `Map<String, List<String>> dependencies` — project name → selected dependency ids.

Its javadoc must spell out the **deliberate asymmetry with `groups`**: `groups == null` means *all*
groups, `dependencies == null` (or a project missing from the map) means *no* dependencies.

That one field is the whole protocol change for both steps 1 and 2 - there is no separate mode flag
(see 2.1).

### 1.5 The tree builder receives the selection and ignores it

`StructureViewProvider.createTree(project, cachedIndex, updateMetadata, selectedGroups)` gains a
`Collection<String> selectedDependencyIds` parameter; the existing callers (`createCompleteTree`,
the no-cache overload) pass `null`, so "no dependencies unless asked for" holds for the MCP tools
too. Inside, the ids are resolved to descriptors (`discoverAll` → filter by id), and held — nothing
more. The tree is still built from the host's `IndexStructureElements`, so it is provably unchanged.

No further threading is needed: step 0 already made the tree builder take a `StructureElements`.
Step 2 swaps in a composite at exactly that point.

### 1.6 VSCode client

Modeled on the existing `structure.grouping` command:

- Command `vscode-spring-boot.structure.dependencies`, title *"Select Dependencies to Include"*,
  icon `$(library)`, contributed to `view/item/context` with `viewItem =~ /\bproject\b/` and hidden
  from the command palette — same contributions as `structure.grouping`.
- Handler in `structure-tree-manager.ts`: fetch candidates via `.../structure/dependencies`; show
  `window.showQuickPick` with `canPickMany: true`, where `label` = `displayName`,
  `description` = `groupId:artifactId` when known, `detail` = the project location, and `picked`
  from the persisted selection. On confirm, persist and `this.refresh(false)`.
- `StructureCommandParams` gains `dependencies?: Record<string, string[]>`, sent from `refresh`
  alongside `groups`. In step 1 it can be sent unconditionally, since the server ignores it; from
  step 2 on it is sent only in dependency mode, because its presence is what switches the server
  into that mode (2.1).

Eclipse gets the same via a `DependenciesDialogModel` alongside `GroupingDialogModel` — a later
increment, as with the diff feature, which proved its protocol in VSCode first.

### 1.7 Tests for step 1

- `ClasspathDependencyResolverTest` — synthetic CPE lists covering own source, several source
  folders of one foreign project (one dependency, not several), foreign source without the name key
  (location lookup), binary, system and test-scoped entries. **This is where requirement 3 is
  pinned down.**
- `ClasspathUtilTest` (jdt-ls-extension) — a project referencing another workspace project yields
  source CPEs carrying both `extra["project"]` and the new name key.
- `WorkspaceProjectDependencySourceTest` — only projects `projectFinder.all()` knows are offered.
- Command test for `.../structure/dependencies`, and parsing of the new `dependencies` argument.
- A regression test that the structure tree is identical with no selection and with a non-empty
  one — the contract of step 1.

**Test fixtures.** The `spring-boot-language-server` harness goes through `MavenProjectClasspath`,
which does no workspace resolution - the existing multi-module fixtures
(`gs-multi-module-complete`, `test-annotation-indexing-large-multiproject-*`) produce jars, not
workspace-project entries. So discovery is tested against hand-built CPE lists, and anything that
needs a real multi-project setup injects such a classpath into the harness rather than relying on
`MavenProjectClasspath`.

---

## Step 1b — JAR dependencies in the picker (done)

Pulled forward from step 4: the picker lists the project's jars as well, identified by group and
artifact id, so users can select them now. Scanning jars for stereotype elements followed as step 4,
also done - see that section.

- **Coordinates on binary CPEs.** `CPE` gained the extras `groupId`, `artifactId`, `version` and
  `scope` (`CPE.EXTRA_*`), read back through `getGav()` and `getScope()`. Where they come from:
  - JDT side (`ClasspathUtil`): m2e puts `maven.groupId`/`maven.artifactId`/`maven.version`/
    `maven.scope` classpath attributes on the entries of its Maven container; they are copied over.
  - Standalone Maven (`MavenProjectClasspath`): from the resolved `Artifact` (base version).
  - Gradle - Buildship on the JDT side and `GradleProjectClasspath` standalone - carries no
    coordinates, but its jars live in the Gradle cache, laid out as
    `.../files-2.1/<group>/<artifact>/<version>/<hash>/<file>`. `ClasspathDependencyResolver`
    reads the coordinates from that path when the CPE has none.
- **Discovery.** `ClasspathDependencyResolver.jarDependenciesOf` returns one `JarDependency(gav,
  name, path)` per group:artifact (first wins), leaving out the JRE (`isSystem`) and test-only jars
  (`isTest`, or Maven scope `test`). `JarDependencySource` maps them to descriptors:
  `gav:<groupId>:<artifactId>` when the coordinates are known, `jar:<name>` otherwise.
- **Picker.** Workspace projects and libraries under separate separators; a jar shows
  `groupId:artifactId:version` as its description, and the filter matches on it.
- Not covered: a sibling module the standalone LS sees as a jar in the local repository is offered
  as a jar, not as a workspace project (see "Decisions").

---

## Step 2 — Dependency mode with elements from workspace project dependencies

Makes the selection do something: in dependency mode, the stereotype elements of the selected
projects are merged into the host's tree as if they were the host's own, and the diff feature is
switched off.

> **As implemented** - where it differs from the text below:
> - `StructureDependencySource.elementsOf(dependency, cachedIndex, catalog)` also takes the catalog
>   to resolve against, and returns null for a dependency it doesn't supply (`JarDependencySource`
>   always in this step - `JarDependencySource` supplies them too as of step 4). Also takes the
>   *including* project (a JAR has no classpath of its own to resolve its classes' annotations
>   against - see step 4). `StructureDependencySources.elementsOf` picks the first source that
>   answers. `StructureViewProvider` does the composing and gets `StructureDependencySources`
>   injected.
> - **A catalog of its own for a composed tree:**
>   `StereotypeCatalogRegistry.getCatalogOf(project, includedDependencyIds)`, cached per set of ids
>   and reset along with the project's own. Registering a dependency's source-defined stereotypes in
>   the project's own catalog would leave them there after leaving dependency mode, so the project's
>   own tree would depend on what was included before.
> - `IndexStructureElements.of(project, cachedIndex, catalog)` builds the factory and registers the
>   source-defined stereotypes. Host and dependencies share it; `StructureViewProvider` no longer
>   has its own copy.
> - `CompositeStructureElements` routes by element identity. Stereotypes and method labels follow
>   the part the type (or method) came from. Method labels route by the *method* first: in the
>   "Request Mappings" group the contextual type is not the method's own type. A dependency's type
>   also gets the stereotypes of the host's package of the same name, when the host has that package
>   (a package split across both, with the host's `package-info` carrying the stereotype). This
>   takes the place of `packageNode` looking at "the first part that knows the package":
>   `packageNode` stays the host's, which is all the tree builders need while the tree is rooted in
>   the host's main package.
> - The ids decide the mode: the server is in dependency mode whenever any project's selection in
>   the request is non-empty, even if it resolves to nothing.
> - A project listed in its own selection is ignored. The client picker's `matchOnDescription` and
>   separators came with step 1b.
> - Client: the partial-refresh merge now also takes every tree the server returned. The existing
>   restbucks TODO there is untouched.
> - Not done: client-side tests. The extension has no test setup for the tree manager.

### 2.1 The mode

One global mode for the whole view, not per project: the diff toggles are already global, and a tree
where some project rows diff and others do not would be hard to read.

**Client** (`structure-tree-manager.ts`) owns the mode:

- A third `PersistedToggle`, `vscode-spring-boot.structure.includeDependencies`, default **off**, so
  the view looks exactly as today until the user opts in. Mirrored into a `when`-clause context key
  of the same name, like the other two.
- Title-bar command pair *"Include Dependencies"* / *"Exclude Dependencies"*, shown/hidden by that
  context key, like the highlight and hide-unchanged pairs.
- **Selecting one turns the other off:**
  - turning on *Include Dependencies* turns off `highlightChanges` and `hideUnchanged`;
  - turning on *Highlight Changes* or *Hide Unchanged Nodes* turns off `includeDependencies`;
  - confirming a non-empty selection in the dependency picker turns on *Include Dependencies*
    (and with it off the diff toggles) - otherwise the user picks something and sees nothing
    happen.

  Selections are never cleared by switching; they are simply not sent in diff mode.
- In dependency mode, `package.json` `when` clauses hide the diff-only entries: the two diff
  toggles' "on" commands stay visible (clicking one is how you switch back), but `captureBaseline`,
  `clearBaseline`, `selectBaseline` and `showChanges` are hidden.
- `nodes.ts`: no baseline line in the project-row tooltip in dependency mode (the server sends no
  `HAS_BASELINE` then, which would otherwise read as "no baseline captured").
- `refresh` sends `dependencies` only while in dependency mode; `compareAgainst` only while not.

**Server** (`SpringIndexCommands.createAnnotatedTree`) needs just one rule, and no new argument:

- the request carries a non-empty `dependencies` selection for any project → dependency mode for the
  whole request: build each tree with its project's selection and **skip
  `annotateWithChangesSinceBaseline` entirely** (no `HAS_BASELINE`, no `COMPARED_AGAINST_*`, no
  `change`; `compareAgainst` ignored);
- otherwise → exactly today's behavior.

The server enforcing this itself, rather than trusting the client's toggles, means a stray
`compareAgainst` from a stale client can never produce change markers on a composed tree.

Nothing in the diff machinery changes.

### 2.2 The element half of the SPI

The unit a source supplies is a `StructureElements` — the interface step 0 introduces — so the
dependency feature has no element abstraction of its own. `StructureDependencySource` gains:

```java
StructureElements elementsOf(DependencyDescriptor dependency, CachedSpringMetamodelIndex cachedIndex);
```

`WorkspaceProjectDependencySource.elementsOf` returns an `IndexStructureElements` for the dependency
project: the very class that serves the host, pointed at another project name. The index is keyed by
project name already, so there is essentially nothing to write.

Step 3 adds `rootPackages()` for dependency roots.

### 2.3 A composite `StructureElements`

`CompositeStructureElements(host, dependencies)` is the third implementation of the interface, next
to the index and the snapshot ones. `StructureViewProvider.createTree` hands it to
`createTree(project, StructureElements, groups, updateMetadata)` in place of the host's
`IndexStructureElements` whenever a selection is present. Since a composed tree is never diffed, the
composite is only ever used for display - it never meets `StructureSnapshotBuilder` or
`SnapshotStructureElements`:

| Query | Composite answer |
|---|---|
| `types()` | host's, then each dependency's, in selection order |
| `membersOf(type)` | delegated to whichever part the type came from — members are bean children, document-scoped |
| `methodLabel(m, type)` | delegated likewise — otherwise a dependency's mapping methods would fall back to the plain method label |
| `packageNode(pkg)` | first part that knows the package |
| `mainApplicationPackage()` | **the host's only** — it decides the tree's root label and the abbreviation base, both properties of the host |
| `stereotypeFactory()` | the **host's** catalog, with the stereotype definitions of *all* parts registered — otherwise a dependency's own source-defined stereotypes are unknown and its types land in "Other" |

Resolving dependency elements through the host's catalog is deliberate: the elements appear as the
host's own, so they are grouped by the same stereotypes and groups as the host's.

`StructureViewUtil.abbreviate` shortening a dependency's FQN relative to the *host's* main package
is the right outcome: a type in a disjoint root keeps its full name, which distinguishes it visually
without needing a dedicated node.

### 2.4 What step 2 does and does not cover

`extractTypes` selects types by `type.getType().startsWith(pkg.getPackageName())`, and in step 2 the
only package is still the host's main application package. So:

- a dependency rooted **inside** the host's package (`com.example.myapp.shared` under
  `com.example.myapp`) works with no further change — its types land in the host's existing package
  node, grouped by stereotype next to the host's own. This is the common multi-module case, and
  exactly the "no difference" behavior;
- a dependency rooted **outside** it (`com.acme.shared`) contributes nothing visible — filtered
  away. That is step 3.

Worth stating in the step's commit message, because "I selected it and nothing happened" is
otherwise a confusing intermediate state.

### 2.5 Refresh when a dependency project changes

`onSpringIndexUpdated` reports `affectedProjects`, and both the client and `SpringIndexCommands`
filter to those. In dependency mode, if `shared-domain` changes and `my-app` includes it, `my-app`
has to be rebuilt too.

Handle it server-side in `SpringIndexCommands`: in a dependency-mode request, widen the
`affectedProjects` filter to also include any project whose selection — present in the very same
request — names one of the affected projects. The client keeps sending what it does today, but its
partial-merge path in `structure-tree-manager.ts` then has to key off the *returned* project list
rather than the requested one — worth checking against the existing partial-refresh TODO in that
file (`// TODO: Partial tree refresh didn't work for restbucks`), which this touches.

`awaitSettledIndex()` is called once per request and drains the whole index, so it already covers
the dependency projects.

### 2.6 MCP

`StereotypeInformation`:

- New tool `getStructureDependencies(projectName)` — the descriptors, so an agent can find valid ids.
- `getLogicalStructure` gains an optional `dependencies` parameter (a list of ids). When given, it
  returns the composed tree. The tree returned there carries no change information today anyway, so
  the exclusion holds naturally.
- `getLogicalStructureChanges`, `captureLogicalStructureBaseline` and the other baseline tools stay
  exactly as they are: always per project, own elements only.

### 2.7 Two things to verify early in step 2

- **Stereotype catalog.** The host's catalog is built from the host's classpath, which contains the
  dependencies' jars and output folders, so a dependency's jMolecules catalog files are *expected*
  to be visible already. Check this for a workspace project dependency specifically: if
  `ProjectBasedCatalogSource` does not pick up `META-INF` resources from a dependency project's
  output folder, the catalog source has to be widened to the scope too, which enlarges the step.
- **Cycles.** A → B → A is legal on the classpath. Direct dependencies only (an included dependency
  does not pull in its own), plus a visited-set guard.

### 2.8 Tests for step 2

- `CompositeStructureElementsTest` — the union and delegation rules of 2.3.
- A dependency nested under the host's package contributes its types into the host's existing
  package node, grouped by stereotype, with no extra node.
- A dependency's source-defined stereotypes are honored rather than falling into "Other".
- A dependency's request-mapping methods get their mapping label, not the plain method label.
- `nodeId`s stay unique when host and dependency contain same-named types.
- An A ↔ B cycle terminates.
- **Mode exclusion, server side:** a request with a non-empty `dependencies` selection returns trees
  with no `change`, `HAS_BASELINE` or `COMPARED_AGAINST_*` attributes - for every project in it,
  even ones with a baseline and even with a `compareAgainst` sent along; a request without one is
  today's, change annotations included.
- A change in a dependency project refreshes the host's tree in dependency mode.
- Client: the toggles turn each other off; a non-empty picker selection switches into dependency
  mode; `dependencies` is only sent in dependency mode.

---

## Step 3 — Root packages

**Deferred.** Picked up out of order: step 4 (JAR scanning) was done first, on request, keeping JAR
elements subject to the same "nests under the host's main package or is left out" rule step 2
already applies to workspace-project dependencies - deliberately, so this section's own design work
stays separate from where elements come from. The text below is therefore still a plan, not a
report of what exists.

Makes a dependency whose packages do not nest under the host's appear at all.

`ProjectTree` renders one package node per package returned from `extractPackages`. So the
dependency's root package is added to that set and becomes a **sibling package node** of the host's
— the same node kind, produced by the same code path, carrying its own stereotype-grouped types. No
new node kind, no dependency-specific rendering.

`StructureElements` gains `rootPackages()`. For the index and snapshot implementations it is the
main application package, exactly as today, so a project's own tree does not change.
`CompositeStructureElements.rootPackages()` = the host's main application package plus each
dependency's root packages — computed by the composite as described below, **not** by asking the
dependency's own `IndexStructureElements`, whose main-package logic is wrong for a library — and
**reduced to the minimal set of non-overlapping prefixes**: drop any candidate that is a descendant
of another candidate. Without the reduction, a nested dependency root produces a duplicate package
node whose types also appear under its ancestor.

**Determining a dependency's root package.** *Not* with
`StructureViewUtil.identifyMainApplicationPackage`: it looks for `@SpringBootApplication` elements,
which a library project does not have, and falls back to
`new StereotypePackageElement("", null, true)` — an **empty** package name, whose `startsWith("")`
matches every type of every project and would collapse the whole tree into a single node. Instead
the dependency's root is derived from its elements themselves: the longest common
package prefix of the dependency's indexed types. Robust, needs no build metadata, and is the
library's base package in practice. If the common prefix comes out empty (types under genuinely
disjoint roots), fall back to the distinct prefixes one segment below.

**Modulith.** The Modulith view's top level is the host's application modules, and a dependency's
types belong to none of them. Options: (a) append the dependency's root packages to
`extractPackages(ApplicationModules)` as extra package nodes alongside the module nodes —
consistent with the jMolecules view, but mixes "module" and "package" nodes at one level; or (b)
leave dependencies out of the Modulith view. Recommendation: **(a)**, since it needs no new node
kind and a Modulith project depending on a shared library is a normal setup. Check what
`ApplicationModulesLabelProvider` labels a non-module package node as.

**Tests:** nested root collapses into the host's package node (no duplicate); disjoint root becomes
a sibling package node; degenerate/empty common prefix falls back safely; the Modulith variant.

---

## Step 4 — JAR dependencies (done, ahead of step 3)

`JarDependencySource.elementsOf` reads a selected JAR's classes with
[Jandex](https://github.com/smallrye/jandex) and turns them into the exact same
`StereotypeClassElement`/`StereotypeMethodElement`s a workspace project's source contributes, so
everything downstream of step 2 (`CompositeStructureElements`, package-scoped filtering, the shared
catalog) needs no JAR-specific handling at all. Only *which elements a JAR source hands the tree
builder* is new; the elements themselves are, as far as the rest of the tree is concerned,
indistinguishable from an `IndexStructureElements` project's.

### 4.1 Reading a JAR with Jandex

Built directly on the public `org.jboss.jandex` API (`Indexer`/`Index`/`ClassInfo`), not on
`commons-java`'s own `commons.jandex` package: that package's classpath-wide indexing exists for a
different job (type resolution for completion/hover, with JRT-module support and an on-disk index
cache neither needed here), and its classes are package-private anyway.

`JarStereotypeScanner` (in `boot.java.stereotypes`, alongside `StereotypesIndexer`, the equivalent
for source) computes, per class:

- **`annotationTypes`** - the class's own direct annotations, expanded through their meta-annotation
  chain, plus the direct (not meta-expanded) annotations of every type in its superclass/interface
  hierarchy. This mirrors `StereotypesIndexer.getAnnotationTypes` exactly - including its one
  asymmetry (meta-expansion only for the class's own annotations, not a supertype's) - so a class is
  never attributed differently depending on whether it came from a workspace project or a JAR.
- **`supertypes`** - every superclass and interface in the hierarchy, recursively, by name -
  regardless of whether that name is itself indexed. A supertype outside what got indexed (most
  commonly a JDK type) still has to count for a stereotype assignment that matches on it
  (`doesImplement`), and Jandex reads a class's supertype *names* straight from its bytecode without
  needing the supertype's own class file at all.
- Annotation type declarations and module-info classes are left out - matching source, where an
  `AnnotationTypeDeclaration` becomes a stereotype *definition* candidate, never a tree node
  (`StereotypesIndexer.index(AnnotationTypeDeclaration, ...)` never calls
  `createStereotypeElementForType`).
- A method becomes a `StereotypeMethodElement` only if it carries some (non-`java.*`) annotation -
  same rule as `annotatedMethodsOf` for source.

**Cross-JAR meta-annotations.** An annotation's own meta-annotations are often declared several
JARs away from where the annotation is used - the textbook case is Spring's own
`@RestController` (in `spring-web`) being meta-annotated with `@Controller` (in `spring-context`).
Verified directly against those two real JARs while building this: indexing `spring-web` alone,
`@Controller`'s own class is simply not found, and its meta-annotation (`@Component`) is invisible;
indexing both JARs into the same `Indexer` resolves it correctly. So a JAR is never indexed in
isolation - see 4.2.

### 4.2 Where the annotation-resolution scope comes from

`JarDependencySource.scan(including, jarFile)` builds one combined Jandex `Index` over every
non-system, non-test binary classpath entry of `including` (the project the JAR is being included
in) - not just the selected JAR. Every JAR a project's dependency's annotations could reference is,
by construction, already resolvable somewhere on that same project's own classpath (the JVM has to
be able to resolve them too, for the annotation to work at compile/runtime in the first place), so
this is always sufficient without needing to model a dependency's own transitive closure
separately. The JDK itself is skipped: its classes are never stereotype-relevant, and a supertype
name is captured regardless of whether the JDK is indexed (see 4.1).

If the selected JAR happens not to be found among `including`'s own classpath entries at all (a
stale selection, or a discovery/classpath mismatch), it is indexed on its own, into the same
`Indexer` that already holds the rest of the classpath - so it still benefits from whatever
cross-referencing that combined index can offer, just without a guarantee of completeness.

### 4.3 What is cached, and what deliberately is not

Two different things could be cached here, and only one of them is:

- **The scan** (Jandex parsing a JAR's classes and walking their hierarchies) is genuinely
  expensive and does not depend on anything that changes often - so `JarDependencySource` caches
  the *unfiltered* list of every `StereotypeClassElement` a JAR's classes produce, keyed by the
  JAR's own identity (path, size, last-modified time). Shared across every project that selects the
  same JAR, and only ever populated the first time a JAR is actually selected for some project's
  tree - never for one merely offered in a picker's "Libraries" list, where most JARs will sit
  unselected forever.
- **Which of those elements currently match a stereotype is *never* cached.** A user can define
  their own stereotypes - including by `implements`, not just by annotation - in a JSON catalog file
  or in source, and that catalog can change at any point in a session (a file edited, a project's
  own source-defined stereotype added or removed, a different set of dependencies selected -
  composed catalogs are per set of included ids, see 2.2's "As implemented" note). Filtering a
  JAR's classes against the catalog *once*, at scan time, and keeping only the matches would mean a
  class that starts matching only after such a change can never be recovered without a full
  rescan - and detecting exactly when a rescan is warranted would mean fingerprinting every possible
  source of a catalog change, which is easy to get subtly wrong. `JarStructureElements.types()`
  instead filters the cached, unfiltered list against whichever catalog is current, fresh on every
  call - exactly what `IndexBasedStereotypeFactory` already does for a project's own source-indexed
  types (never pre-filtered either), just scaled up to a JAR's class count. If that filtering ever
  proves too slow for a very large JAR, caching the *matching* subset, keyed additionally by a
  fingerprint of the catalog's own `getDefinitions()` (already an authoritative, already-computed
  value - nothing to separately track), is the fallback to reach for then; not worth building before
  it is shown to matter.

### 4.4 The stereotype factory

`JarStereotypeFactory` mirrors `IndexBasedStereotypeFactory`'s detection - both catalog queries a
type needs (`getTypeBasedStereotypes` for `implements`-based assignments,
`getAnnotationBasedStereotypes` for annotation-based ones; missing either would silently drop one
kind of assignment) - without needing a live `SpringMetamodelIndex`, since a JAR element's
`annotationTypes`/`supertypes` already mean the same thing `IndexBasedStereotypeFactory` would
compute from source. It does no package-level detection of its own: the host's package-info of the
same name is what a composed tree actually consults for that (`CompositeStructureElements`, see
2.3), one level up.

### 4.5 What a JAR element still cannot do

- **No source location** - `membersOf` and `JsonNodeHandler`'s location attribute already handle a
  null location gracefully (a type reconstructed from a baseline snapshot has the same property).
- **No request-mapping *label***, though the node now exists (see 4.7's amendment below): the
  pretty route label (`@/greeting -- GET`) comes from a dedicated lookup
  (`StructureViewUtil.getMethodLabel` → `RequestMappingIndexElement`) that resolves the mapping
  annotation's own `path`/HTTP-method attribute values - a JAR-scanned mapping method shows up
  under "Request Mappings" correctly, just with a plain signature label instead. Could be added
  later by reading the same attribute values via Jandex; not done here.
- **No content hash, never diffed** - the mode exclusion (see above) means a JAR element never
  meets `StructureSnapshotBuilder`.
- **A JAR's own `@Stereotype`-annotated custom annotation types are not picked up as stereotype
  *definitions*.** A JAR's JSON catalog contribution (`META-INF/jmolecules-stereotypes.json`) is
  unaffected by any of this and already worked before this step:
  `ProjectBasedCatalogSource.getSources()` already reads it from any binary classpath entry,
  selected or not. Only a definition declared by a custom annotation type *inside* the JAR's own
  bytecode - the source-side equivalent of `IndexBasedStereotypeFactory.registerStereotypeDefinitions()`
  - is out of scope: JAR catalogs seen so far all use the JSON file, not that mechanism, and it can
  be added later if a real one needs it. Confirmed to matter in practice: the first real dependency
  tried against this feature relied entirely on its own custom stereotype, and scanned it as
  matching nothing until that turned out to be a build problem with the JAR itself, not this gap -
  worth revisiting if a real case actually needs it.

### 4.6 Method-level information: two bugs found by using this for real, both fixed

Trying this against a real dependency (not a test fixture) surfaced two gaps between the AST-based
and JAR-based paths that no test had caught, because nothing had compared their output side by
side:

- **Method annotations were never meta-expanded.** `annotationTypesOf` (4.1) meta-expands a
  *class's* own annotations, but the method loop in `ownClassesOf` originally used only
  `method.declaredAnnotations()` directly. A convenience annotation like `@GetMapping` (itself
  meta-annotated with `@RequestMapping`) never resolved to the base annotation a "Request Mappings"
  stereotype is assigned to - so a JAR-scanned handler method using `@GetMapping`/`@PostMapping`
  never matched at all, while the exact same method indexed from source (where
  `StereotypesIndexer.getAnnotationTypes` already meta-expands a method's own annotations too)
  matched correctly. Fixed by giving methods the same meta-expansion classes already had
  (`JarStereotypeScanner.annotationTypesOf(MethodInfo, Index)`).
- **The method label was missing its declaring class prefix.** `ASTUtils.getMethodSignature(method,
  false)` - `StereotypeMethodElement.methodLabel`'s source - is `ClassName.method(Type) : Return`;
  the JAR-scanned label was originally just `method(Type) : Return`. Fixed to match exactly.

Both are now covered by tests that assert the exact label/matching format, citing the AST-side
source they have to match (`JarStereotypeScannerTest`).

**On the duplication risk this exposes.** Two independent implementations of "how does a method's
stereotype membership get decided" is exactly the kind of thing that drifts, and just did. What
keeps this bounded rather than open-ended:
- *Class-level* detection (is this a Controller, a `@ConfigurationProperties` class, a Repository)
  was never duplicated - it already goes through one shared thing, the stereotype catalog matched
  against whichever `annotationTypes`/`supertypes` a `StereotypeClassElement` carries, computed
  either from AST or from Jandex. Only the *data-producing* half exists twice; the *matching* half
  never did.
- Where a genuinely shared decision exists (the `@ConfigurationProperties` `prefix`/`value`
  resolution rule - see 4.7), it is extracted into one function both sides call, not written twice.
- Where the remaining duplication is close to irreducible (Jandex vs. JDT bindings have no common
  type to share code against without a much larger abstraction), the acceptance is: keep the
  AST-side format as the documented source of truth, and test the JAR side against it explicitly by
  hardcoded, comment-linked assertions - not a guarantee against drift, but a fast, specific failure
  when it happens, which is what actually caught this the first time.
- The larger alternative considered - decompiling a JAR's classes to synthetic source and running
  them through the existing AST-based indexers unchanged, eliminating the duplication rather than
  managing it - was deliberately not pursued now: a bigger, less certain change (decompiler
  correctness on real-world bytecode, a new dependency, unclear whether the existing indexing
  pipeline can run against a non-file-backed document without deeper changes) that is worth a
  dedicated spike if this keeps recurring, not a prerequisite for the two cases below.

### 4.7 Members: `@ConfigurationProperties` fields and repository query methods

Neither goes through stereotype matching at all - confirmed by investigation before writing any of
this, specifically to avoid guessing at the wrong mechanism:

- A `@ConfigurationProperties` class's "properties" are its **fields** (or record components), not
  its methods - a completely separate, field-driven indexer (`ConfigurationPropertiesIndexer`)
  keyed off the annotation's `prefix`/`value` attribute.
- A Spring Data repository's "query methods" (`findByXxx`) usually carry **no annotation at all** -
  recognized purely by the interface implementing `org.springframework.data.repository.Repository`,
  then listing every non-`default` method (`DataRepositoryIndexer`).

Both are rendered as **members** (`StructureElements.membersOf`), a different path than stereotype
grouping - plain nodes attached directly under the type, independent of the catalog. `JarStructureElements.membersOf`
previously hard-coded an empty list; it now looks up a `Map<StereotypeClassElement,
List<StructureMember>>` computed once per scan.

- **`JarConfigurationPropertiesScanner`** (`boot.java.beans`, alongside `ConfigurationPropertiesIndexer`):
  gated on the class's already-computed, meta-annotation-expanded `annotationTypes` containing
  `@ConfigurationProperties` (consistent with how the class itself gets grouped); reads
  `ClassInfo.fields()` (or `recordComponents()` for a record) and prefixes each with the resolved
  prefix. The one piece of real logic here - resolving `prefix`, falling back to `value` - is
  **shared**: `ConfigurationPropertiesIndexer.resolvePrefix(String, String)`, extracted out of
  that indexer and called from both sides, so it cannot drift between them the way the method
  annotation rule just did.
- **`JarDataRepositoryScanner`** (`boot.java.data`, alongside `DataRepositoryIndexer`): reuses the
  class's own `doesImplement(Constants.REPOSITORY_TYPE)` (the exact same `supertypes` walk the
  class-level stereotype match already does - no second interface-hierarchy walk), excludes
  `@NoRepositoryBean`, and lists every non-`default` method via `MethodInfo.isDefault()`. This rule
  has almost no logic to duplicate in the first place, so it is written directly on both sides
  rather than extracted.
- **Not reconstructed from bytecode**: a repository method's resolved SQL/JPQL - Spring Data's
  AOT-generated metadata is a build output that does not generally ship inside a plain dependency
  JAR. Investigation also found this is not actually wired into the tree even for a workspace
  project today (`QueryMethodIndexElement`'s nested query-string `DocumentSymbol` is computed but
  never read by `IndexStructureElements.membersOf`), so nothing here is a regression.

**Tests**: `JarConfigurationPropertiesScannerTest` (regular class fields; record components instead
of backing fields; `prefix` over `value`; no annotation → no members; no attribute → unprefixed
name) and `JarDataRepositoryScannerTest` (non-default methods become members; the label has no
class prefix, unlike a stereotype-grouped method's; `@NoRepositoryBean` excluded; a plain interface
contributes nothing) - the latter compiles against a throwaway stub of
`org.springframework.data.repository.Repository`/`NoRepositoryBean` (this module has no real
dependency on `spring-data-commons`), left out of the packaged JAR, which doubles as another live
check of 4.1's "a supertype need not itself be indexed" claim. Plus one end-to-end case in
`StructureDependenciesJarTreeTest`: a JAR-scanned `@ConfigurationProperties` class's field reaches
the rendered tree as a member node.

### 4.8 Tests

- `JarStereotypeScannerTest` - meta-annotations resolved across two JARs indexed together; a
  missing meta-annotation's JAR skipped without failing the scan; supertypes collected recursively,
  by name, even for a type outside the indexed set; annotation types and modules excluded from the
  result; only annotated methods kept; **a method's own annotations meta-expanded the same way a
  class's are** and **the method label carries its declaring class's simple name** (4.6's two
  fixes).
- `JarStereotypeFactoryTest` - a type matching through both catalog buckets at once; **the same
  element matching differently against two different catalogs, with no rescanning** (4.3's central
  claim); methods matching only annotation-based assignments.
- `JarStructureElementsTest` - `types()` filtering live against the given catalog; the same
  scanned list answering differently for a different catalog; the no-live-index fallbacks (method
  label, members, package node).
- `JarDependencySourceTest` - a JAR's elements scanned and matched; a non-JAR dependency and a
  missing JAR file both handled without throwing; the same JAR scanned only once across two calls
  (proven by object identity of the resulting elements, not by deleting the file - a cache keyed by
  file identity necessarily misses once the file's identity itself has changed); a JAR not found on
  the including project's classpath still scanned, in isolation.
- `StructureDependenciesJarTreeTest` - end to end through `StructureViewProvider`: a JAR class
  implementing `test-stereotypes-support`'s own, already-catalog-assigned `DescribedStereotype`
  interface appears in the composed tree, nested under the host's main package; a class outside
  that package does not (yet - step 3); nothing appears without a selection. The fixture JAR is
  compiled and packaged at test time (`JarFixtureBuilder`, using `javax.tools.JavaCompiler`), not
  Maven-installed - keeping the test independent of the shared local Maven repository and of Maven
  reactor ordering (see the "shared Maven repo across worktrees" note elsewhere in this repo's
  memory of past sessions). `DescribedStereotype` itself is compiled alongside the fixture class,
  from a copy of its source, purely so the fixture class type-checks - and deliberately left out of
  the packaged JAR, doubling as a live demonstration of 4.1's "a supertype need not itself be
  indexed" claim. Extended with a members case: a JAR-scanned `@ConfigurationProperties` class's
  field reaches the tree as a member node.
- `JarConfigurationPropertiesScannerTest`, `JarDataRepositoryScannerTest` - see 4.7.

---

## Decisions taken, and their alternatives

| Decision | Alternative rejected |
|---|---|
| Including dependencies and the diff feature are **mutually exclusive** - selecting one turns the other off | Diffing a composed tree — no single project's baseline describes it, and including a dependency would show all its elements as new; making it work would pull the diff machinery into this feature |
| **One global mode**, not per project | Per-project mode — some rows diffing and others not, with "hide unchanged" behaving differently per row |
| Selections survive switching modes | Clearing them when entering diff mode — forces the user to re-pick every time |
| No mode flag in the protocol: a non-empty `dependencies` selection *is* dependency mode, and the server skips annotation for the whole request | A separate `includeDependencies` flag — two fields that must agree, and one more combination to handle |
| Baseline capture and git tracking keep running in dependency mode | Pausing them — switching back would show stale or missing diffs |
| Merge at **element** level; the tree-building logic is unchanged in shape | A synthetic `Dependencies` container with per-dependency subtrees — makes dependencies a visibly separate thing, which is not what this feature is for |
| The SPI supplies stereotype **elements**, not nodes | An SPI returning finished subtrees — forces the separate-subtree shape and duplicates the Modulith/jMolecules branch |
| Selection persisted **client-side** in `workspaceState` and sent per request, like `groups` | Server-side persistence — only needed if baselines had to see the selection, which the mode exclusion rules out; costs a new store and read/write commands |
| Dependencies plug in as a composite `StructureElements`, reusing step 0's abstraction | A dependency-specific element abstraction, or passing project-name collections to each call site — duplicates what step 0 already threads through the tree builder |
| Jars offered in the picker before they can contribute elements (step 1b) | Keeping them out until jar scanning exists — the selection UI and ids would change once more later |
| Jar ids are version-free (`gav:<g>:<a>`, else `jar:<name>`); coordinates from m2e attributes, the Maven `Artifact`, or the Gradle cache path | Ids with versions — a selection would get lost on every version bump |
| A dependency's root package is the longest common prefix of its indexed types (step 3, deferred) | Reusing `identifyMainApplicationPackage` — a library has no `@SpringBootApplication`, and its empty-package fallback would collapse the tree |
| Root packages reduced to non-overlapping prefixes (step 3, deferred) | Adding every dependency root unconditionally — a nested root yields a duplicate package node |
| Step 4 (JAR scanning) done before step 3 (root packages), keeping JAR elements under the same main-package-only scoping step 2 already has | Doing step 3 first — the two are independent: where elements come from vs. which packages of the composed tree are shown |
| A JAR is read with the public Jandex API directly, not `commons-java`'s own `commons.jandex` package | Reusing that package's classpath-wide indexing — built for a different job (completion/hover type resolution, with JRT-module support and an on-disk cache neither needed here), and its classes are package-private |
| A JAR's classes are resolved against a combined index over the *including* project's whole classpath, not the JAR in isolation | Indexing the JAR alone — an annotation's own meta-annotations are frequently declared in a different JAR (`@RestController`/`@Controller` across `spring-web`/`spring-context`, verified directly), so isolated scanning would silently miss them |
| The unfiltered scan of a JAR's classes is cached by the JAR's own identity; which of them currently match a stereotype is never cached, only computed fresh per catalog | Caching the filtered (matching) result — a stereotype catalog can change at any point in a session (a JSON file edited, a source-defined stereotype added/removed, a different dependency selection), and detecting exactly when to invalidate that cache means fingerprinting every possible source of such a change |
| A JAR is only ever scanned once it is actually selected for some project's tree | Scanning (or prefetching) every JAR a picker offers — most JARs on a typical classpath (third-party frameworks especially) are never selected at all |
| Members (`@ConfigurationProperties` fields, repository query methods) computed once alongside the scan and cached by JAR identity, unlike `types()` | Filtering/computing them live like stereotype matching — unlike stereotype matching, neither is catalog-dependent (a fact of the class's own bytecode), so there is nothing to go stale |
| The `@ConfigurationProperties` prefix-resolution rule extracted into one function both the AST-based and JAR-based scanners call | Writing it twice — the one piece of real, non-mechanical logic in that case, and exactly the kind of thing that drifts (demonstrated by the method meta-annotation bug, 4.6) |
| The decompile-to-source alternative (run JARs through the existing AST indexers unchanged) considered and deliberately not pursued | Adopting it now — would eliminate JAR/AST duplication entirely, but is a bigger, less certain change (decompiler correctness, a new dependency, unclear whether the indexing pipeline runs against a non-file-backed document); worth a dedicated spike only if duplication keeps recurring |
| Default is diff mode with no dependencies included | Defaulting to dependency mode — changes the view for every existing user without being asked |
| Direct dependencies only, depth 1 | Transitive expansion — unbounded trees, and the classpath is already flattened differently per build system |
| Workspace-project dependencies come straight from the classpath the Java tooling sends (`extra["project"]`, plus a new name key) | Matching jars to open projects by GAV - only needed for the standalone LS, which does no workspace resolution; accepted there: no workspace-project dependencies offered, for now |

## Open questions

- Should there be an "include all workspace project dependencies" shortcut in the picker, given that
  a large reactor build can have dozens of modules?
- Performance (from step 2 on): a project's tree covers N projects' elements, and
  `SpringIndexCommands` parallelizes across projects, so a workspace where every project includes
  every other one multiplies the work. Measure before optimizing; `CachedSpringMetamodelIndex`
  being shared across the whole request and keyed per project name is most of the mitigation — and
  dependency mode skips the diff work entirely, which offsets some of it.
