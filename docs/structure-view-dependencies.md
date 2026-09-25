# Including Project Dependencies in the Logical Structure View

Design plan for letting users pick, per project, a set of *dependencies* whose elements are included
in that project's tree in the Logical Structure view.

Status: **plan only**, nothing implemented yet. Companion document to
[`structure-diff-view.md`](structure-diff-view.md), which describes the existing diff feature on the
same tree and is a good model for how this area is built and documented.

**Prerequisite:** [`structure-diff-elements.md`](structure-diff-elements.md) — element-based
structure snapshots — is implemented **first**, as a pure internal refactoring of the diff feature.
It introduces the `StructureElements` abstraction that everything the tree builder reads goes
through, and this plan builds on it (see 1.5 and 2.2).

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

The structure view is in exactly one of two modes at any time:

| | **Diff mode** | **Dependency mode** |
|---|---|---|
| Tree content | each project's own elements only; dependency selections are ignored | each project's own elements **plus** those of its selected dependencies, in one tree |
| Change information | as today: per project, against that project's own baseline | none — no change attributes, no baseline information on the tree |
| Diff UI (highlighting, hide unchanged, baseline commands, "Show Changes") | available | disabled/hidden |
| Baseline capture and `GitBaselineTracker` | as today | **keep running unchanged in the background** |

Why this is the right cut: a baseline describes one project's own structure. A composed tree mixes
elements of several projects, so it cannot be diffed against any single project's baseline, and
merely including a dependency would otherwise make all of its elements show up as "new". Keeping the
modes apart means **the diff feature continues to work exactly as it does today, per project, and
nothing about baselines, snapshots or git tracking changes at all**.

Consequences that shape the rest of this plan:

- **Baselines never contain dependency elements, by construction.** `createCompleteTree(project)`
  — which every baseline capture goes through — never receives a dependency selection.
- **Baselines stay current while in dependency mode.** `GitBaselineTracker` keeps capturing in the
  background, since it works from each project's own tree. Switching back to diff mode shows
  up-to-date diffs immediately; nothing is lost by the detour.
- **The selection is a pure view concern.** It never influences what gets persisted as a baseline,
  so it can live in the client alongside `groups` (see 1.3).

**To revisit once step 2 works:** the element-based snapshots make lifting this exclusion feasible.
A composed tree's baseline side can be rebuilt from each contributing project's *own* snapshot plus
the current selection, and diffed with the same differ. Since the dependency is included on both
sides, including it is not a change, and a change in it shows up relative to its own project's last
commit. That stays out of scope for this plan; the exclusion is the design until then.

## The four steps

| Step | Scope | Outcome |
|---|---|---|
| **0** | Prerequisite: element-based snapshots, a separate plan ([`structure-diff-elements.md`](structure-diff-elements.md)) | No user-visible change except that group selection no longer disturbs the diff; `StructureElements` in place |
| **1** | Selection UI + persistence + the selection reaching the tree builder, which ignores it | Nothing changes in the tree. Everything around it is in place. |
| **2** | Dependency mode: include the stereotype elements of selected **workspace project** dependencies; mutual exclusion with the diff feature | Works wherever the dependency's packages nest under the host's; disjoint packages contribute nothing yet |
| **3** | Root packages | Dependencies with disjoint package roots appear too |
| **4** | JAR dependencies | The second SPI implementation |

Step 0 has its own document. Steps 1–3 are detailed below; step 4 is sketched.

---

## What exists today (the starting point)

Tree construction, in
`headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/java/commands/`:

- `StructureViewProvider.createTree(project, cachedIndex, updateMetadata, selectedGroups)` is the
  single entry point. It picks `ModulithStructureView` or `JMoleculesStructureView` per project and
  returns a `JsonNodeHandler.Node` root. `createCompleteTree(project)` is the variant that baseline
  capture goes through.
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
- `StructureSnapshotStore` captures baselines from `createCompleteTree`; `GitBaselineTracker`
  captures automatically from git activity, per project, with no client request involved.
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

### The two gaps in the classpath model

- **JAR dependencies have no GAV.** `CPE` knows a path plus a name/version *guessed from the jar
  file name* (`Classpath.getDependencyName`/`getDependencyVersion`). There is no group id anywhere.
- **Workspace project dependencies are only implicitly recognizable**, via `extra["project"]` +
  `!isOwn()`, and only on the JDT-LS path (`ClasspathUtil.createSourceCPE` /
  `resolveDependencyProjectCPEs` flatten a `CPE_PROJECT` entry into the referenced project's source
  CPEs). The project *name* is not carried, only its location. `MavenProjectClasspath` /
  `GradleProjectClasspath` — the standalone LS, the Claude plugin, **and the test harness** — carry
  nothing at all and resolve inter-module dependencies to jars in the local repository, so there
  they are indistinguishable from ordinary jars.

---

## Step 1 — Selecting dependencies

**Isolated, shippable, and changes nothing about the tree.** The user can open a quick pick on any
project row, multi-select its dependency projects, and have that selection survive a restart. The
language server discovers the candidates and receives the selection with every structure request,
and the tree builder ignores it. No mode switch yet, and no interaction with the diff feature — that
arrives with step 2, when the selection starts to have an effect.

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

`CPE` gains the raw fact the resolution needs:

- `String getDependencyProjectName()` / `URI getDependencyProjectLocation()` — set on *source*
  entries contributed by another workspace project, by adding the project name next to the existing
  `extra["project"]` location in `ClasspathUtil.createSourceCPE`. Keep writing `extra["project"]`
  unchanged: `SpringProjectUtil` reads it (`SpringProjectUtil.java:97`) and it crosses the LSP wire.
- `Gav getGav()` on binary entries is **step 4**; nothing here depends on it.

Resolution is *not* a pure `IClasspath` concern, because recognizing a workspace project needs the
set of open projects:

```java
public class ClasspathDependencyResolver {
    public ClasspathDependencyResolver(JavaProjectFinder projectFinder, ProjectGavCache gavs) {...}
    public List<ProjectDependency> dependenciesOf(IJavaProject project);
}
```

It folds the flat CPE list into distinct dependencies:

- source CPEs with `!isOwn()` and a dependency project name/location → one `WorkspaceProject` per
  distinct project (a project contributes several source folders);
- non-system, non-test binary CPEs → one `Jar` each;
- **promotion**: a `Jar` whose GAV matches the GAV of an open project in `projectFinder.all()` is
  reported as a `WorkspaceProject` instead. This is what makes requirement 3 hold on the
  Maven/Gradle path, where inter-module dependencies arrive as jars — without it the feature is
  dead on the standalone LS, the Claude plugin, and every test. Falls back to matching
  `cpe.getName()` against the candidate project's artifact id when no GAV is available; a
  documented heuristic, last resort only.

Test-scope (`isTest()`) and system entries are excluded: the structure view is about the
application.

`ProjectGavCache` is a small new service caching `Gav` per project, populated lazily from
`STS4LanguageClient.projectGAV` (JDT path) or `MavenCore.computeGav(pom)` (standalone path) and
invalidated on classpath change via `ProjectObserver` — the pattern `StereotypeCatalogRegistry`
already uses. It replaces the uncached ad-hoc request in
`WorkspaceBootExecutableProjects.findExecutableProjects`, which should move onto it here.

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
knows (open *and* indexed), each with `supported = true`. `JarDependencySource` arrives in step 4;
until then the picker lists workspace projects only.

> **Decision:** jars stay out of the picker until step 4 rather than appearing greyed out.
> Listing dozens of unusable rows is noise, and `kind`/`supported` are already on the descriptor, so
> switching them on later needs no protocol change and no client change beyond removing a filter.

### 1.3 Persisting the selection — client-side, like `groups`

The selection is persisted in the VSCode client's `workspaceState` under
`vscode-spring-boot.structure.dependencies`, as `Record<projectName, dependencyId[]>` — the same
shape and mechanics as `vscode-spring-boot.structure.group` — and sent with every
`sts/spring-boot/structure` request.

This is consistent with how the view's other per-project settings (`groups`, `compareAgainst`) work,
and needs no new server-side storage. It is safe because of the mutual exclusion: the selection
never reaches baseline capture, which only ever sees `createCompleteTree`, so there is nothing
server-side that would need to know it outside of a request.

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

### 1.5 The tree builder receives the selection and ignores it

`StructureViewProvider.createTree` gets the selection as a parameter:

```java
public Node createTree(IJavaProject project, CachedSpringMetamodelIndex cachedIndex,
        boolean updateMetadata, Collection<String> selectedGroups,
        Collection<String> selectedDependencyIds)
```

The existing overloads delegate with `null` (= none). `createCompleteTree` keeps its signature and
passes `null`. After step 0 capture no longer builds a tree at all, but `createCompleteTree` still
backs the MCP `getLogicalStructure` tool, and "no dependencies unless asked for" must hold there too.

Inside, the ids are resolved to descriptors (`discoverAll` → filter by id), and held — nothing more.
The threading this step would otherwise need is already done: step 0 routes everything the tree
builder reads through `StructureElements` (see
[`structure-diff-elements.md`](structure-diff-elements.md#the-element-source-abstraction)), so the
tree builder takes a `StructureElements` instead of a bare project. Step 2 plugs a composite
implementation in at exactly that point. In step 1 the tree is built from the host's
`IndexStructureElements`, as after step 0, so it is provably unchanged.

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
  alongside `groups` and `compareAgainst`.

Eclipse gets the same via a `DependenciesDialogModel` alongside `GroupingDialogModel` — a later
increment, as with the diff feature, which proved its protocol in VSCode first.

### 1.7 Tests for step 1

- `ClasspathDependencyResolverTest` — synthetic CPE lists covering own source, foreign source with a
  dependency project name, binary, system and test-scoped entries; GAV-based promotion of a jar to a
  workspace project; the name-based fallback. **This is where requirement 3 is pinned down.**
- `WorkspaceProjectDependencySourceTest` — discovery against a multi-project harness.
- Command test for `.../structure/dependencies`, and parsing of the new `dependencies` argument.
- A regression test that the structure tree is identical with no selection and with a non-empty
  one — the contract of step 1.
- `ProjectGavCacheTest` — caching and invalidation on classpath change.

**Test fixtures.** `gs-multi-module-complete` and
`test-annotation-indexing-large-multiproject-1/2` + `test-annotation-indexing-parent` already exist.
The harness goes through `MavenProjectClasspath`, so inter-module dependencies arrive as **jars, not
project references** — which makes these fixtures exactly the right test of the GAV-promotion path,
and means a JDT-flavored `extra["project"]` case has to be covered by a synthetic CPE-level test
instead.

---

## Step 2 — Dependency mode with elements from workspace project dependencies

Makes the selection do something: in dependency mode, the stereotype elements of the selected
projects are merged into the host's tree as if they were the host's own, and the diff feature is
switched off.

### 2.1 The mode

**One global mode for the whole view, not per project.** The diff toggles are already global, and a
tree in which some project rows show diffs and others silently do not would be hard to read —
"hide unchanged nodes" in particular would behave differently from row to row.

Client (`structure-tree-manager.ts`):

- A third `PersistedToggle`, `vscode-spring-boot.structure.includeDependencies`, default **off**, so
  the view looks exactly as it does today until the user opts in. Like the other two, it is
  mirrored into a `when`-clause context key of the same name.
- Title-bar command pair *"Include Dependencies"* / *"Exclude Dependencies"* (`$(library)` /
  a filled variant), shown/hidden by that context key the same way the highlight and hide-unchanged
  pairs are.
- **Mutual exclusion, last action wins:**
  - turning on *Include Dependencies* turns off `highlightChanges` and `hideUnchanged`;
  - turning on *Highlight Changes* or *Hide Unchanged Nodes* turns off `includeDependencies`.

  The per-project selections are untouched by either direction — they are simply inactive while in
  diff mode, and come back as they were.
- Confirming a **non-empty** selection in the picker while in diff mode switches to dependency mode,
  with an information message saying the diff view was turned off. Otherwise the user picks
  something and sees nothing happen.
- While in dependency mode, `package.json` `when` clauses hide the diff-specific context menu
  entries: `captureBaseline`, `clearBaseline`, `selectBaseline`, `showChanges`.
- `nodes.ts`: drop the project-row tooltip line about the baseline while in dependency mode.
  Otherwise, because the server sends no `HAS_BASELINE` in that mode, every project would claim
  "No logical structure baseline captured yet", which is false.
- `StructureCommandParams` gains `includeDependencies: boolean`.

Server (`SpringIndexCommands`) — **enforces the exclusion itself** rather than trusting the client to
send a consistent combination:

- `StructureCommandArgs` gains `boolean includeDependencies`.
- When `includeDependencies` is true: build each tree with its project's selected dependency ids,
  and **skip `annotateWithChangesSinceBaseline` entirely** — no `HAS_BASELINE`, no
  `COMPARED_AGAINST_*`, no `change` attributes. `compareAgainst` is ignored. This also means no
  diff work at all in that mode.
- When it is false (or absent, i.e. every existing client): exactly today's behavior, and the
  `dependencies` map is ignored.

Nothing in `StructureSnapshotStore`, `StructureBaselineStorage` or `GitBaselineTracker` changes.

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
to the index and the snapshot ones. The tree builder receives it in place of the host's
`IndexStructureElements` whenever dependency mode is on and something is selected:

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

Handle it server-side in `SpringIndexCommands`: when `includeDependencies` is set, widen the
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
- **Mode exclusion, server side:**
  - with `includeDependencies`, the tree carries no `change`, `HAS_BASELINE` or `COMPARED_AGAINST_*`
    attributes, even for a project with a baseline;
  - without it, a non-empty `dependencies` map is ignored and the tree is today's, including its
    change annotations;
  - capturing a baseline while dependencies are selected produces exactly the project's own tree.
- A change in a dependency project refreshes the host's tree in dependency mode.
- Client: the toggles flip each other off; a non-empty picker selection switches into dependency mode.

---

## Step 3 — Root packages

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

## Step 4 — JAR dependencies

Sketch only; details when the step is picked up.

- `Gav` on binary CPEs, end to end: m2e/Buildship on the JDT side, `MavenProjectClasspath` and
  `GradleProjectClasspath` on the standalone side. This is what makes the picker's
  `groupId:artifactId` identity real for jars.
- `JarDependencySource`: `discover` returns the `Jar` dependencies; `elementsOf` reads types from
  the jar (Jandex-style, as the standalone LS already does for type indexing) and synthesizes
  `StereotypeClassElement`s.
- Remove the picker's "supported only" filter from step 1.

The mode exclusion makes this step simpler than it would otherwise be: jar-derived elements never
take part in a diff, so they need no content hashes and no baseline story. Open questions for then:
nodes with no navigable source location; how deep to index a jar (cost vs. depth); and whether a
jar's stereotypes should come from its own catalog contribution or only the host's.

---

## Decisions taken, and their alternatives

| Decision | Alternative rejected |
|---|---|
| Including dependencies and the diff feature are **mutually exclusive** | Diffing the composed tree against the host's baseline — no single project's baseline describes it, and including a dependency would show all its elements as new. Rebuilding the composed baseline from each project's own element snapshot becomes possible after step 0, and is left as a later option (see the note under the mode table) |
| **One global mode**, not per project | Per-project mode — some rows diffing and others not, with "hide unchanged" behaving differently per row |
| Mode switch is **last action wins**; selections survive switching | Clearing the selections when entering diff mode — forces the user to re-pick every time |
| The server enforces the exclusion (no annotation when `includeDependencies`) | Relying on the client's toggles alone — a stray `compareAgainst` or stale client would produce misleading markers |
| Baseline capture and git tracking keep running in dependency mode | Pausing them — switching back would show stale or missing diffs |
| Merge at **element** level; the tree-building logic is unchanged in shape | A synthetic `Dependencies` container with per-dependency subtrees — makes dependencies a visibly separate thing, which is not what this feature is for |
| The SPI supplies stereotype **elements**, not nodes | An SPI returning finished subtrees — forces the separate-subtree shape and duplicates the Modulith/jMolecules branch |
| Selection persisted **client-side** in `workspaceState` and sent per request, like `groups` | Server-side persistence — only needed if baselines had to see the selection, which the mode exclusion rules out; costs a new store and read/write commands |
| Dependencies plug in as a composite `StructureElements`, reusing step 0's abstraction | A dependency-specific element abstraction, or passing project-name collections to each call site — duplicates what step 0 already threads through the tree builder |
| Jars excluded from the picker until step 4 | Listing them greyed out — noise, and `kind`/`supported` already allow switching them on with no protocol change |
| A dependency's root package is the longest common prefix of its indexed types (step 3) | Reusing `identifyMainApplicationPackage` — a library has no `@SpringBootApplication`, and its empty-package fallback would collapse the tree |
| Root packages reduced to non-overlapping prefixes (step 3) | Adding every dependency root unconditionally — a nested root yields a duplicate package node |
| Default is diff mode with no dependencies included | Defaulting to dependency mode — changes the view for every existing user without being asked |
| Direct dependencies only, depth 1 | Transitive expansion — unbounded trees, and the classpath is already flattened differently per build system |
| Jar-to-project promotion by GAV, with an artifact-id-name fallback | Relying on `extra["project"]` alone — leaves the feature dead on the standalone LS, the Claude plugin, and the test harness |

## Open questions

- **Issue number.** Commits in this area reference `GH-XXXX`; this feature needs its own issue (the
  diff feature was `GH-1974`, the LSP/MCP core split `GH-1965`). To be filled in before the first
  commit — and if the four steps get their own issues, they should still cross-reference.
- Should there be an "include all workspace project dependencies" shortcut in the picker, given that
  a large reactor build can have dozens of modules?
- Performance (from step 2 on): a project's tree covers N projects' elements, and
  `SpringIndexCommands` parallelizes across projects, so a workspace where every project includes
  every other one multiplies the work. Measure before optimizing; `CachedSpringMetamodelIndex`
  being shared across the whole request and keyed per project name is most of the mitigation — and
  dependency mode skips the diff work entirely, which offsets some of it.
