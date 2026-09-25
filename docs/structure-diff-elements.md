# Element-Based Structure Snapshots

Design plan for changing *what* a logical structure baseline stores and *how* the diff against it is
computed — from a snapshot of the rendered tree to a snapshot of the elements the tree is built
from.

Status: **implemented** (R0-R4; R5 turned out to need no code changes - see that step's note; R6 is
this file plus the update to `structure-diff-view.md`). Commits landed under `GH-1974` directly,
each step its own commit, in the order below.

- Reworks the internals of the diff feature (`GH-1974`) described in
  [`structure-diff-view.md`](structure-diff-view.md). That document describes today's
  implementation and has to be updated as part of this work (see R6).
- **Prerequisite** for [`structure-view-dependencies.md`](structure-view-dependencies.md): it
  introduces the element-source abstraction that the dependency feature builds on, and is done
  first.

## Goal and constraints

1. **A pure internal refactoring.** From the user's point of view the diff feature behaves exactly
   as before — same highlighting, same "hide unchanged", same baseline commands, same git-driven
   capture, same MCP tools — apart from the short list of deliberate deviations below.
2. **Fixes the grouping bug as a side effect.** Changing the group selection must no longer produce
   change markers. After this refactoring it structurally cannot.
3. **Lays the groundwork for the dependency feature**, by routing everything the tree builder reads
   through one abstraction with swappable implementations.

## The problem

A baseline today is a `StructureNode` tree: the rendered structure view tree, minus locations
(`StructureViewProvider.toComparableNode`), built by `createCompleteTree(project)` with *all*
groups. The live tree is diffed against it by `StructureTreeDiffer`, matching nodes by
`kind + text` along the path.

That makes the diff depend on **presentation**, not only on code. Anything that changes the shape
or labels of the tree without the code changing shows up as a change.

**The grouping bug.** `SpringIndexCommands.createAnnotatedTree` builds the live tree with the
client's `selectedGroups`, then `StructureSnapshotStore.annotateWithChangesSinceBaseline` diffs it
against a baseline built with all groups. Deselect a group and its stereotype nodes exist only in
the baseline (reported as removed, so their parent lights up as modified), while the types beneath
them move under other stereotypes or "Other" (reported as added). No code changed. No existing
test combines a group selection with a baseline, which is how this went unnoticed.

The same mechanism would hit every future presentation change: including dependency elements,
different package roots, a jMolecules upgrade that shapes the tree differently.

## The approach

1. **The snapshot stores elements, not a tree.** Per project: the types with their methods and
   members, their content hashes, and their *resolved* stereotypes — everything needed to rebuild
   the tree, nothing that is merely presentation.
2. **To diff, rebuild the baseline tree from the snapshot's elements, using the *current*
   presentation settings** — the same groups, the same catalog, the same tree builder — and diff it
   against the live tree with the **existing, unchanged `StructureTreeDiffer`**.

Because both trees are built by the same code with the same settings, the shape of the tree can no
longer be a source of differences. Only the elements can. That fixes the grouping bug by
construction.

**Why rebuild the baseline tree rather than project element-level changes onto the live tree.**
Projecting changes element by element would need new diff semantics: how a removed type is reported
when its stereotype group has no counterpart, what happens when a type moves between stereotypes,
and so on. Rebuilding the baseline tree keeps every rule `StructureTreeDiffer` implements today —
removals reported on the parent unless accompanied by an addition, renames as removal plus addition,
`CONTAINS_CHANGES` on containers, `#n` disambiguation of duplicate labels — working identically,
because the differ does not change at all. That is what makes "pure refactoring" achievable rather
than approximate.

## What a snapshot contains

Not the index elements themselves, and not the rendered tree. Small, dedicated records, persisted
as JSON through `StructureBaselineStorage`. **Two records, not one** - the envelope
(`StructureSnapshotStore.StructureSnapshot`, keeping its existing name) and the actual captured
project state (`StructureElementSnapshot`, a new, standalone type) - so the latter can also be
`StructureSnapshotBuilder`'s and `SnapshotStructureElements`' return/parameter type without dragging
commit metadata along:

```
StructureSnapshot {                     // StructureSnapshotStore's own record, name unchanged
  capturedAt, commitSha, commitMessage  // unchanged
  elements: StructureElementSnapshot    // was `root: StructureNode` before this rework
}

StructureElementSnapshot {
  mainApplicationPackage: String                 // as identified at capture time
  types: [ SnapshotType ]                        // in index order
  stereotypeDefinitions: [ SnapshotStereotype ]  // see "stereotypes that disappeared"
}

SnapshotType {
  fqn: String                  // the unabbreviated type name
  contentHash: String
  stereotypes: [ String ]      // resolved stereotype identifiers
  methods: [ SnapshotMethod ]  // the type's StereotypeMethodElement children
  members: [ SnapshotMember ]  // what createTypeSubnotes shows for it
}

SnapshotMethod {
  name, label, signature: String   // label = what getMethodLabel produced, e.g. a mapping label
  contentHash: String
  stereotypes: [ String ]
}

SnapshotMember {
  label: String                // DocumentSymbol name
  contentHash: String
}

SnapshotStereotype {           // one per stereotype identifier referenced above - the data backing
  identifier, displayName: String  // the lightweight Stereotype objects SnapshotStereotypeFactory
  priority: int                    // hands back (see "The element-source abstraction"), not an
  groups: [ String ]               // overlay into the catalog - see "Stereotypes that disappeared"
}
```

In other words: a mirror of `StereotypeClassElement` / `StereotypeMethodElement` and the bean-member
`SymbolElement`s, **without locations, plus resolved stereotypes**.

Choices in there, and why:

- **Resolved stereotype identifiers, not raw annotations and supertypes.** Resolution through
  `IndexBasedStereotypeFactory` already folds in package-level stereotypes and source-defined
  stereotype definitions, so a stereotype gained through a `package-info` or a new definition is
  captured as a change of the affected type — as it is today, where that type moves in the tree.
  Resolved stereotypes are also independent of the group selection: groups only decide what the tree
  is grouped *by*, never which stereotypes an element has.
- **The FQN, not the displayed label.** `StructureViewUtil.abbreviate` shortens type names relative
  to the main application package; that is applied at render time. The main application package
  itself *is* stored, so the baseline tree is rendered with the package name and abbreviation it had
  at capture time — which keeps a change of the main package reported as it is today.
- **Method labels are stored as rendered.** The mapping label of a request-mapping method comes from
  `RequestMappingIndexElement`, which the snapshot does not otherwise carry. Storing the result keeps
  the baseline tree's labels exactly as they were, and label-based matching in the differ unchanged —
  so a changed mapping path is still a removal plus an addition, as today.
- **Members use the same selection rule as the tree.** Only the members `createTypeSubnotes` would
  show: children of the bean whose type matches. That rule becomes one shared function, used by both
  the tree and the snapshot builder (see the `membersOf` row below), so the two cannot drift apart.
- **All of the project's types, unfiltered.** The live tree filters types by package (main
  application package, or module base packages). The snapshot does not; the rebuilt baseline tree
  applies the same filter as the live one. This keeps the snapshot independent of how the top level
  of the tree is organized — which is exactly what step 3 of the dependency plan changes.
- **Element order is preserved.** `StructureTreeDiffer` disambiguates duplicate sibling labels
  positionally; both trees must receive their elements in index order for that to line up, as it
  does today.

What is **not** in a snapshot: application, package, stereotype-group and named-interface nodes.
They are derived when the tree is built, on both sides, from the same settings.

Why not the index elements as-is, even though they are Gson-serializable already
(`IndexGsonTypeFactories`): they carry locations (roughly a third of a baseline's size, which is why
`toComparableNode` drops them today); the tree reads from four different element types plus bean
graphs; and their shape is internal. The index cache can afford that coupling because it is
disposable — keyed by indexer version and thrown away on upgrade. A baseline has to stay comparable
across a language server upgrade.

**Size.** Each element is stored once. Today a type with two stereotypes appears twice in a
baseline, once under each stereotype node — so snapshots should get smaller, not larger.

### Stereotypes that disappeared — resolved, no overlay needed

The obvious worry: rebuilding the baseline tree groups stored stereotype identifiers using the
*current* catalog (`StereotypeGrouper.group`, matching by `definitions.stream().anyMatch(d ->
d.refersTo(stereotype))`), and if a stereotype no longer exists there — a source-defined stereotype
that was deleted, a catalog entry gone after a dependency upgrade — that match fails.

Traced through jMolecules' `AbstractStereotypeCatalog`, `StereotypeGroup` and
`StereotypeGrouper`/`StereotypeGrouped` (`jmolecules-stereotype` sources), this needs **no special
handling and no catalog overlay**:

- An unmatched stereotype simply groups into jMolecules' own `OtherStereotype.INSTANCE` bucket
  (`StereotypeGrouper.group`, `skipOthers = false`) — the same "Other" a live type with no
  recognized stereotype lands in today. `JsonNodeHandler.handleStereotype` special-cases exactly
  that identifier and never calls `catalog.getDefinition` for anything else, so there's no crash
  path here at all: `catalog.getDefinition(stereotype)` is called only for stereotypes
  `StereotypeGrouped` actually produced as a key, and those are, by construction, either matched by
  a definition or `OtherStereotype.INSTANCE`.
- The stereotype's **group-derived label suffix** (" (Design, DDD)" etc., via
  `StructureViewUtil.getStereotypeLabeler`) does not depend on the identifier being registered
  either: `AbstractStereotypeCatalog.getGroupsFor(Stereotype)` matches by asking each
  `StereotypeGroup.contains(stereotype)`, which is `stereotype.getGroups().contains(identifier)` —
  a property of the `Stereotype` object itself (which the snapshot supplies), not a catalog lookup.
- **If the catalog itself changed with no code change** — the exact scenario this worried about —
  both sides degrade together: the live tree's `IndexBasedStereotypeFactory` also stops resolving
  the deleted stereotype for that type (detection runs against the same current catalog), so live
  and rebuilt-baseline agree and nothing is reported as changed. That is deviation 2 below, and it
  falls out for free.

So `stereotypeDefinitions` is not an overlay into `StereotypeCatalogRegistry`'s shared catalog - it
is simply where `SnapshotStereotypeFactory` gets the `getDisplayName()` / `getPriority()` /
`getGroups()` of the lightweight `Stereotype` objects it hands back, since those can't be looked up
from the catalog once an identifier is gone. R3's "verify early" is resolved: no work on
`AbstractStereotypeCatalog` is needed.

## The element-source abstraction

Everything the tree builder reads from the index goes through one interface, with two
implementations: the live index, and a snapshot.

```java
public interface StructureElements {

    List<StereotypeClassElement> types();

    StereotypePackageElement mainApplicationPackage();

    StereotypePackageElement packageNode(String packageName);

    String methodLabel(StereotypeMethodElement method, StereotypeClassElement type);

    List<StructureMember> membersOf(StereotypeClassElement type);

    StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> stereotypeFactory();
}

public record StructureMember(String label, Location location, String contentHash) {}
```

- `IndexStructureElements(project, cachedIndex, factory)` — today's behavior, moved behind the
  interface. Deliberately a thin, side-effect-free view: unlike this sketch, it does **not** build
  its own `IndexBasedStereotypeFactory` or decide whether to call `registerStereotypeDefinitions()` -
  it receives an already-built `StereotypeFactory`, so that decision (and the
  `disable-source-defined-stereotypes` toggle behind it) stays exactly where it already lived,
  in `StructureViewProvider`, rather than being duplicated inside this class.
- `SnapshotStructureElements(snapshot)` — no `catalog` parameter either, and for the same reason:
  resolving a stored identifier needs no catalog at all (see "Stereotypes that disappeared"), only
  the snapshot's own `stereotypeDefinitions`. Constructs `StereotypeClassElement` /
  `StereotypeMethodElement` instances from the stored data (location `null`, empty annotation and
  supertype sets, methods attached via `addChild` so `type.getMethods()` still finds them), and a
  `SnapshotStereotypeFactory` that answers `fromType` / `fromMethod` with the *stored* stereotypes,
  keyed by the element instances it constructed (an identity map, since a reconstructed element has
  no other stable key). `methodLabel`, `membersOf` and `mainApplicationPackage` answer from stored
  data.

Every place the tree builder reads the index today, and what it becomes:

| Location | Reads today | Becomes |
|---|---|---|
| `ToolsStructureProvider.extractTypes` | `springIndex.getClassesForProject(project)` | `types()` |
| `SimpleApplicationModulesStructureProvider.extractTypes` | `getNodesOfType(project, StereotypeClassElement.class)` | `types()` |
| `ApplicationModulesNamedInterfacesGroupingProvider` (two places) | `getClassesForProject(project)` | `types()` |
| `ApplicationModulesStructureProvider.extractPackages` → `StructureViewUtil.findPackageNode` | `findPackageNode(pkg, project)` | `packageNode(pkg)` |
| `StructureViewUtil.identifyMainApplicationPackage` (from `JMoleculesStructureView` and `ApplicationModulesLabelProvider`) | `SpringBootApplicationIndexElement`s + `findPackageNode` | `mainApplicationPackage()` |
| `StructureViewUtil.getMethodLabel` (from both label providers) | `RequestMappingIndexElement`s | `methodLabel(m, type)` |
| `JsonNodeHandler.createTypeSubnotes` | `getBeansOfDocument(type.getLocation().getUri())`, and bails out when the location is `null` | `membersOf(type)` — must no longer bail out on a `null` location, which every snapshot type has |
| `IndexBasedStereotypeFactory` and its `registerStereotypeDefinitions` | catalog matching on annotations/supertypes; `StereotypeDefinitionElement`s | `stereotypeFactory()` |

`StructureViewProvider` gains a second `createTree` overload that builds a tree *from a
`StructureElements`* directly:

```java
Node createTree(IJavaProject project, StructureElements elements, Collection<String> selectedGroups, boolean updateMetadata)
```

with the existing entry point - `createTree(project, cachedIndex, updateMetadata, selectedGroups)` -
building an `IndexStructureElements` (via a small shared `indexElementsOf` helper that also owns the
`registerStereotypeDefinitions()` decision) and delegating to the new overload. `updateMetadata`
still exists on the new overload too, since `ModulithStructureView.createTree` needs it regardless
of which kind of `StructureElements` it was handed - but every *rebuild* call (from
`annotateWithChangesSinceBaseline` and `diffAgainstBaseline`) always passes `false`: a rebuilt
baseline tree is diffed against whatever module metadata is current, never against a fresh request
of its own. `catalog.getGroups()` (the "`null` means every group" resolution) also moved into the
new overload, so it happens identically regardless of which `StructureElements` is in play.

This is the abstraction the dependency feature needs: there, a composite `StructureElements` over the
host and its selected dependencies is simply a third implementation.

## Capture

`StructureSnapshotStore.snapshotNow` stops building a tree; it calls the new
`StructureViewProvider.captureSnapshot(project)`, which builds an `IndexStructureElements` for the
project exactly as `createTree` does (same `indexElementsOf` helper) and hands it to
`StructureSnapshotBuilder.capture` - all types, their methods, `membersOf`, `methodLabel`, the
stereotypes from `stereotypeFactory()`, the main application package - which writes the records
above.

Side effect worth having: **capture no longer needs Spring Modulith metadata.** Today a Modulith
project's tree cannot be built without it, which is why `createCompleteTree` retries with
`updateMetadata`. The snapshot needs only the index and the catalog.

Unchanged: *when* capture happens — `GitBaselineTracker`, `WorkingTreeStatus`, the settled-index
handling (`GH-1987`, `GH-1994`), manual capture, history size and eviction.

## Diff

`annotateWithChangesSinceBaseline(project, root, snapshotKey, selectedGroups)` gains that last
parameter - the group selection `root` (the live tree) was built with - and:

1. picks the baseline snapshot as today (most recent, or the pinned `snapshotKey`, with today's
   fall-back);
2. builds the baseline tree:
   `structureViewProvider.createTree(project, new SnapshotStructureElements(baseline.elements()), selectedGroups, false)`;
3. diffs `toComparableNode(baselineTree)` against `toComparableNode(root)` with
   `StructureTreeDiffer.diffTree` — unchanged;
4. applies the result with `changesByNodeId` / `applyChanges` — unchanged.

`diffAgainstBaseline(project)`, used by the `getLogicalStructureChanges` MCP tool, builds the
baseline tree the same way with `selectedGroups = null` (every group), and the "current" tree via
the ordinary `createCompleteTree(project)` - no snapshot round-trip needed for a side that is never
persisted. `AsciiStructureRenderer` still receives a `StructureTreeDiff` and does not change.

For the Modulith view, both trees are built with the *current* module metadata
(`modulithService.getModulesData`) — see the deviations below.

## Storage and migration

`StructureBaselineStorage` already versions its files and discards on mismatch
(`SCHEMA_VERSION`, with the changelog in its javadoc, currently at version 4). This becomes
**version 5**, "baselines store elements instead of a rendered tree", and the existing
discard-on-mismatch path does the migration:

- Existing baselines and their history are discarded, **once**. A version-4 tree cannot be converted:
  its stereotype nodes carry pluralized display labels, not stereotype identifiers.
- `GitBaselineTracker` captures a fresh baseline as soon as no source changes are pending, or at the
  next commit, so diffs come back without user action.
- A `compareAgainst` pin in the client that refers to a discarded snapshot already falls back to the
  most recent one (`StructureSnapshotStore.baselineOf`).

Storage stays keyed by project name and location (`GH-1989`); only the payload changes.

## Behavior: what stays the same, and the deliberate deviations

The same as today, because the differ, the diff-application code, the wire format and the client
are all unchanged:

- which nodes are highlighted as added, removed-from (modified parent), modified, or containing
  changes;
- content-hash detection of edits that do not change the tree's shape, including the
  double-counting exclusion;
- renames and changed mapping paths reported as removal plus addition;
- "hide unchanged", baseline selection, "Show Changes", capture/clear baseline, all MCP tools;
- a change of the main application package.

Deliberate deviations:

1. **Changing the group selection no longer produces change markers.** The bug fix.
2. **Catalog-only changes are no longer diffed.** A stereotype's display name, its hover
   ("defined in: …"), or which group it belongs to, changed in a catalog without any code change:
   both trees are rendered with the current catalog. Changes to *which stereotypes an element has*
   are still reported, since those are stored. Arguably a correction: the diff is about the
   project's code.
3. **Spring Modulith module-metadata changes without code changes are no longer diffed.** Module
   boundaries and named interfaces come from the current metadata on both sides. Redrawing a module
   usually means moving packages, which *is* reported, as types removed and added.
4. **Capture messages report elements, not nodes.** "Captured logical structure baseline for
   '…' (N node(s))" in `structure-tree-manager.ts`, and the corresponding MCP message, count
   snapshot elements. Rename the field to `elementCount` in `CaptureBaselineResult`,
   `BaselineHistoryEntry` and the client interfaces — client and server ship together — and adjust
   the message wording.
5. **Baseline history is reset once** on upgrade (see migration).
## Performance

Annotating a project now builds two trees instead of one — the baseline tree is rebuilt from its
snapshot on every structure request, where today it is simply held in memory. Both builds work from
in-memory data, and tree creation time is already logged per project. **Measure before
optimizing.** If it matters, cache the rebuilt baseline tree per (project, snapshot key, selected
groups), invalidated when the project's catalog is reset or its Modulith metadata changes.

Capture gets cheaper: no tree build, no Modulith metadata round trip.

## Implementation sequence

Each step is its own commit, and every step up to R4 left the product behaving exactly as before.
All done; notes below record what actually happened, where it differs from the plan as written.

- **R0 — done.** Characterization tests against the pre-refactor code:
  - `StructureGroupSelectionDiffTest` reproduces the grouping bug (deselect one group at a time on a
    project with a baseline and no source changes, assert no `change` attribute anywhere) - confirmed
    to fail pre-refactor, `@Disabled` with a reason pointing at R4, then the annotation removed once
    R4 made it pass.
  - Parity scenarios landed as new tests on the existing `SpringIndexCommandsCaptureBaselineTest`
    (add a type, remove a type, move a type between stereotypes via an annotation change - the last
    one also covers "remove the last type of a stereotype" and "add the first type of a new
    stereotype" in one scenario) rather than a separate file, per the "extend rather than duplicate"
    guidance - that file already covered most of the other scenarios listed (method body edits,
    renames, mapping path changes) before this work started. Each new scenario asserts a
    golden-master dump of every `change` attribute in the tree
    (`StructureTreeTestFixture.describeChanges`), which is the actual mechanism enforcing "must pass
    unchanged after R4" - not a re-derivation of expected values by hand, but the real, observed
    pre-refactor output. `ModulithStructureTreeTest` was added too: no existing test exercised the
    Modulith branch of the structure command at all.
- **R1 — done**, exactly as planned: `StructureViewUtil.membersOf`.
- **R2 — done**, exactly as planned. `ModulithStructureTreeTest` (see R0) is what actually caught
  regressions here, since the Modulith branch touches four of the changed classes and had no other
  coverage.
- **R3 — done.** `SnapshotStructureElementsParityTest` checks the invariant, but not via a raw
  `StructureNode` equality as first written: a snapshot-rebuilt element's `nodeId` legitimately
  differs in format from the live one's (it embeds a source location, which a rebuilt element has
  none of), and `nodeId` plays no part in how `StructureTreeDiffer` matches two trees. So the test
  diffs the two `toComparableNode` trees with the real, unmodified `StructureTreeDiffer` and asserts
  no changes - both a more accurate check of the invariant and immune to that difference. Also
  confirmed here: no catalog overlay is needed for a stereotype identifier that has since disappeared
  from the catalog (see "Stereotypes that disappeared" above) - traced through
  `StereotypeGrouper`/`StereotypeGrouped`/`AbstractStereotypeCatalog`, it degrades to the same
  "Other" bucket an unrecognized stereotype falls into today, on both sides of a diff.
- **R4 — done**, exactly as planned: `SCHEMA_VERSION` 5, `elementCount` everywhere `nodeCount` was.
  The R0 grouping test now passes (confirmed); the parity tests still pass unchanged (confirmed via
  the golden-master assertions from R0).
- **R5 — turned out to need no code changes.** Both candidates for cleanup are still genuinely
  needed: `StructureNode` still backs `toStructureNode` (the MCP `getLogicalStructure` tool's output)
  as well as `toComparableNode`, and the `updateMetadata` retry in `createCompleteTree` is still used
  by that same MCP tool and by `diffAgainstBaseline`'s "current" tree - capture was never its only
  reason to exist, since it's a general "give me the tree, retrying once for Modulith" helper.
- **R6 — done**: `structure-diff-view.md` updated (a note at the top plus its "Wire format notes"
  and "Known deliberate non-goals" sections, which described the old tree-snapshot behavior), and
  this file's own status brought up to date.

## Tests touched

- `StructureTreeDifferTest`, `AsciiStructureRendererTest` — **unchanged**, confirmed: they work on
  `StructureNode` trees, and the differ never changed.
- `StructureBaselineStorageTest` — updated to build `StructureElementSnapshot` payloads instead of
  `StructureNode` ones; schema version bump needed no test change (the mismatch test's hand-written
  JSON didn't need updating either - deserializing an unrecognized field is silently ignored by
  Gson, so only the deliberately-wrong `schemaVersion` in that JSON actually matters).
- `StructureSnapshotStoreTest` — the `StructureViewProvider` mock stubs `captureSnapshot` instead of
  `createCompleteTree`; `annotateWithChangesSinceBaseline` calls pass the new `selectedGroups`
  argument (`null` throughout, since none of these tests exercise group filtering);
  `nodeCount()`/`.root()` assertions became `elementCount()`/`.elements()`.
- `GitBaselineTrackerTest` — passed unchanged except its `FakeBaselineAccess`/`SlowBaselineAccess`
  test doubles, which had to build a `StructureSnapshot` with the new payload shape to compile; no
  behavioral assertion in that file needed to change.
- `SpringIndexCommandsCaptureBaselineTest`, `StereotypeInformationTest` (MCP) — `nodeCount`/"node(s)"
  → `elementCount`/"element(s)".

## Files touched

Language server
(`headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/java/`):

- `commands/`: `StructureViewProvider`, `JMoleculesStructureView`, `ModulithStructureView`,
  `ToolsStructureProvider`, `ApplicationModulesStructureProvider`, `ApplicationModulesLabelProvider`,
  `ApplicationModulesNamedInterfacesGroupingProvider`, `ModulithStereotypeFactoryAdapter`
  (widened to take a `StereotypeFactory` interface, not the concrete `IndexBasedStereotypeFactory`),
  `StructureViewUtil`, `JsonNodeHandler`, `StructureSnapshotStore`, `StructureBaselineStorage`,
  `SpringIndexCommands` (`CaptureBaselineResult`, passing the selected groups to the annotation);
  new: `StructureElements`, `IndexStructureElements`, `SnapshotStructureElements`,
  `StructureMember`, `StructureElementSnapshot`, `StructureSnapshotBuilder`,
  `SnapshotStereotypeFactory`
- `../mcp/StereotypeInformation` — capture message

Client: `vscode-extensions/vscode-spring-boot/lib/explorer/structure-tree-manager.ts` — `elementCount`
and message wording only.

Not touched: `StructureTreeDiffer`, `AsciiStructureRenderer`, `GitBaselineTracker`,
`WorkingTreeStatus`, `nodes.ts`, `diff-decorations.ts`, `package.json`.

## Open questions

- **Caching the rebuilt baseline tree**: only if measurements call for it - not yet done; no
  performance issue has been observed so far.
