# Logical Structure Diff View (GH-1974)

This documents the design of the "diff" feature on top of the Logical Structure tree (jMolecules
stereotypes / Modulith modules), for whoever next touches this area. It focuses on *why* things are
built the way they are - the code itself is the source of truth for *what* they do.

Primary files:

- Backend (`headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/java/commands/`):
  `StructureViewProvider`, `StructureTreeDiffer`, `StructureSnapshotStore`, `StructureBaselineStorage`,
  `GitBaselineTracker`, `WorkingTreeStatus`, `JsonNodeHandler`, `SpringIndexCommands`
- Content hashing (`.../boot/java/utils/`): `ASTUtils.contentHash`, `SpringIndexerJavaContext`,
  `SpringIndexerJavaAstScanner`
- Per-element-kind indexers that populate hashes: `stereotypes/StereotypesIndexer`,
  `requestmapping/RequestMappingIndexer`, `springai/SpringAiIndexer`, `beans/ComponentIndexer`,
  `data/DataRepositoryIndexer`
- Client (`vscode-extensions/vscode-spring-boot/lib/explorer/`): `nodes.ts`,
  `structure-tree-manager.ts`, `explorer-tree-provider.ts`, `diff-decorations.ts`, `baseline-labels.ts`

## The problem this solves

The Logical Structure tree already existed (stereotypes/Modulith view). This feature adds: capture a
baseline snapshot of that tree, and later show what changed since then - added/removed/modified
nodes, highlighted in place, with an optional "hide everything else" mode. It's deliberately modeled
on git: a baseline is the commit you're diffing against, and "since my last commit" is the default
question being answered without the user doing anything.

## Node identity: how two trees are matched up for diffing

`StructureTreeDiffer` matches nodes across the "before" and "after" trees by `kind + ":" + text`
(label) - **not** by source location. Two consequences, both intentional:

- Moving code around inside a file, or reordering siblings, is not a change. Only content is.
- A node whose *label* changes (a renamed method, a request mapping whose path changed) is reported
  as one node removed and a different node added, never as one node modified in place. There's no
  stable ID that could have told them apart, and trying to guess (e.g. by position) would produce a
  much less predictable diff. This is also *why* a lone removal gets folded into `MODIFIED` on the
  parent (see below): the removal half of a rename is normal and shouldn't stand out on its own.

Duplicate sibling labels (two overloaded methods, say) are disambiguated positionally with a `#n`
suffix - see `StructureTreeDiffer.indexChildren`.

## Only the changed node lights up, not its containers

Early on this highlighted every container on the path to a change (the whole package chain down to a
modified method). That's noisy and doesn't tell you *what* changed. `ChangeType.CONTAINS_CHANGES` was
introduced to split "this exact node changed" from "something below this node changed" - clients
highlight only the former and use the latter solely to know a branch must stay visible/expanded
(`nodes.ts`'s `containsChanges`, used to avoid collapsing a path to a change under "hide unchanged").

The one deliberate exception: a **removed node with nothing added in its place** has nowhere to be
reported except on the node it was removed from, which is reported as `MODIFIED` rather than
`CONTAINS_CHANGES` (`StructureTreeDiffer.diffNodes`, the `unaccompaniedRemoval` check). If something
*was* added alongside the removal, that's what a rename looks like under label-based matching, and the
added node already shows it - so the parent is left at `CONTAINS_CHANGES` in that case.

## Content hashes: catching changes the tree shape doesn't show

The tree's shape (labels, icons, children) doesn't change when you edit a method body, tweak a field
type, or reword a Javadoc-adjacent comment. Without something extra, those edits would be invisible
to the diff. `ASTUtils.contentHash(doc, node)` hashes the exact source span of the AST node behind an
index element (MD5, truncated to 12 hex chars - plenty to detect a change, and it travels with every
node over the wire and into every persisted baseline, so kept small on purpose). It's stored as the
`contentHash` node attribute and compared alongside `icon`/`hover` in `StructureTreeDiffer.diffNodes`.

**The double-counting problem, and the post-pass fix.** A method that gets its own tree node (a
`@GetMapping` handler, an `@Tool`-annotated method, etc.) has its source counted twice: once in its
own node's hash, and again as part of its enclosing type's hash. Edit that method, and *both* the
method node and the type node would show as changed - which contradicts "only the node that actually
changed lights up." The fix is `ASTUtils.contentHash(doc, node, excluded)`: hash the type's span but
cut out the source ranges of every member that already has a node of its own, so a member's edit only
shows up on that member's node.

This can't be decided while a single indexer visits a type, because *which* methods end up with their
own node depends on annotations that different indexers look at independently (stereotype indexers,
`@RequestMapping`, Spring AI tool annotations, event listener/publisher detection, etc.) - by the time
`StereotypesIndexer` is building a type's element, it doesn't yet know what `RequestMappingIndexer`
or `SpringAiIndexer` will later decide to index inside the very same type. So this is a **two-phase,
whole-document post-pass**:

1. While indexing, every indexer that gives some inner node its own tree node calls
   `SpringIndexerJavaContext.markAsOwnIndexElement(node)` on that node, and every indexer that will
   need a type's hash calls `context.hashTypeAfterScanning(typeDeclaration, element)` to register it
   for phase 2.
2. After the whole document has been visited by every indexer (`SpringIndexerJavaAstScanner`, right
   after `cu.accept(...)`), `hashScannedTypes(context)` runs once and computes each registered type's
   hash, excluding every node that got marked in step 1 - except the type itself, which is filtered
   back in (`SpringIndexerJavaAstScanner.hashScannedTypes`: `.filter(node -> node != type.getKey())`)
   since a type is naturally in its own "own index element" list when it's a nested type of another
   type being hashed, and must not exclude itself from its own hash.

Both `nodesWithOwnIndexElement` and `typesToHash` on `SpringIndexerJavaContext` are cleared in
`resetDocumentRelatedElements` - they're document-scoped accumulators, not global state, and a stale
entry from a previous document (or an aborted first pass that gets retried with a complete AST) must
not leak into the next hash computation.

The same reasoning applies to **nested types**: `StereotypesIndexer.createStereotypeElementForType`
marks a nested `TypeDeclaration` as its own index element too, so its source is excluded from the
*enclosing* type's hash - otherwise editing a nested class would also flag the outer class.

## Baseline capture: modeled on git, not on "whenever you feel like it"

`StructureSnapshotStore` holds a **retained history** of snapshots per project (newest first, capped
at `boot-java.structure.baseline-history-size`, default 10). Diffing is always on-demand against the
current tree - nothing but that history is kept in memory, and it's persisted to disk
(`StructureBaselineStorage`, one JSON file per project under `~/.sts4`) so it survives a restart.

Two ways a snapshot gets created:

- **Manual** (`captureBaseline(project)`, no commit info): explicit user action, normally over
  uncommitted work. Deliberately records no commit sha/message even if some commit happens to be
  checked out at the time - a manual capture doesn't represent "the state of that commit", it
  represents "whatever's on disk right now."
- **Git-driven** (`GitBaselineTracker`, `captureBaseline(project, sha, message)`): the interesting
  one, described below.

### Why a poll, and why only over a clean tree

`GitBaselineTracker`'s job is "every stored baseline represents the state of one specific commit."
That's what makes "diff since my last commit" meaningful without the user managing baselines by hand.
Two consequences fall out of that one invariant:

- **A baseline is only captured while the working tree holds no pending source changes.** Capturing
  over a dirty tree would bake uncommitted edits into what's supposed to be "the state of commit X",
  permanently hiding them from every future diff against that baseline. So: `HEAD` moved and the tree
  is clean → capture. `HEAD` moved but the tree is dirty → capture nothing, wait for the *next* commit.
  This is also what makes a project opened for the first time with pending changes behave correctly:
  no baseline until the user commits, at which point those changes are exactly what shows up as
  "changed since baseline."
- **Commits are noticed by polling `HEAD` (`GitBaselineTracker.POLL_SECONDS`, 5s), not by reacting to
  file changes.** A `git commit` doesn't touch any file the index watches, so there's nothing to react
  to. Without the poll, the tracker would only notice a commit happened once the user starts editing
  again afterward - by which point the tree is already dirty and that commit never gets a baseline.
  Resolving `HEAD` is one small file read, so a short poll interval is cheap; the working-tree scan
  (the expensive part) only runs once `HEAD` is actually seen to have moved. It's `fixed delay`, not
  `fixed rate`, specifically so a slow scan on a huge repo can't cause ticks to pile up behind it.

`WorkingTreeStatus.IndexRelevant.isStructureClean` scopes the git status check to *just this project's
own directory* (`StatusCommand.addPath`) and filters the pending paths through the Java indexer's own
`isInterestedIn`. Both matter for multi-module/monorepo setups: without the path scoping, an unrelated
pending change anywhere else in the repository would block every project in it from ever getting a
baseline; without the extension filtering, a dirty README or CI file would do the same to a single
project that otherwise has nothing pending that could move its structure tree.

**The poll must not capture a project's very first baseline itself.** A project can look git-clean
(nothing pending on disk) well before its own initial indexing has actually finished - "clean"
reflects git status, not indexing progress. Bug once observed in practice: open a project with an
already-clean git working tree, and the first 5s poll tick would see "clean" before the language
server had finished building the Spring index for it, capture whatever near-empty tree existed at
that instant as the baseline for `HEAD`, and then never revisit it - every node in the *real* tree
would show up as "added" forever, since `capturedCommitShaOf` already matches `HEAD`. Fixed by only
letting the poll (`pollForCommits`) act on a project once at least one index update has actually been
observed for it (`GitBaselineTracker.indexedProjects`, filled in by the same `onIndexUpdate` listener
that reacts to incremental changes). A project's first-ever baseline is left entirely to that
listener, which by construction only fires once its indexing has genuinely completed; every commit
after that remains fair game for the poll, exactly as before. Deliberately not gating
`syncBaselineWithGit` itself, which the listener calls directly and unconditionally - only the poll's
blind "check every open project" needed the guard.

`capturedCommitShaOf` deliberately skips manual snapshots when answering "do I already have a
baseline for this commit?" - if it looked at the newest snapshot regardless of kind, taking a manual
snapshot would make the tracker think the current commit is uncovered and immediately capture a
duplicate, shoving the user's manual snapshot out of the retained history seconds after they took it.

Discovered `Repository` handles are cached per project for the server's lifetime
(`repositoriesByProject`) and released via `server.onShutdown` - each one holds pack file handles
open, so leaving them unclosed would leak file descriptors for the life of the process.

## Selecting a historical baseline to compare against

`StructureSnapshotStore.baselineOf(project, snapshotKey)` is keyed by a snapshot's **capture time**
(`capturedAt`, ISO-8601 string), not by commit sha. Two reasons: manual snapshots have no commit at
all and still need to be individually selectable, and two snapshots can in principle share a commit
sha (a commit that got a baseline, then later got another one captured manually) while the capture
time always identifies exactly one entry. If the requested key isn't found (evicted from history, or
simply unknown), lookup **fails open to the most recent snapshot** rather than showing nothing or
erroring - a stale reference to a snapshot that's aged out of history is a normal, expected occurrence
given the retention cap, not something worth surfacing as a hard failure.

## Wire format notes

- `JsonNodeHandler.CHANGE` / `HAS_BASELINE` / `COMPARED_AGAINST_SHA` / `_MESSAGE` /
  `_CAPTURED_AT` are the flat attributes a client reads off tree nodes. `HAS_BASELINE` and the
  `COMPARED_AGAINST_*` triple are only ever set on the root node - clients that need them elsewhere
  in the tree walk up to the root (`nodes.ts`'s `hasBaseline`, `comparedAgainstSha`, etc.).
  `HAS_BASELINE` exists as its own attribute (rather than being inferred from the absence of any
  `change` attribute) because "no baseline captured" and "baseline captured, nothing changed yet"
  look identical from that absence alone, and a client needs to tell them apart (e.g. for the project
  tooltip).
- **lsp4j's Gson cannot serialize `java.time.Instant`** (`module java.base does not "opens
  java.time"` at runtime, only discovered because a client actually round-tripped a command result
  over JSON-RPC - purely in-process tests never cross that boundary and can't catch it). Every DTO
  that travels over an LSP command result therefore carries timestamps as ISO-8601 `String`, not
  `Instant` - see `BaselineHistoryEntry.capturedAt` and `SpringIndexCommands.CaptureBaselineResult`.
  `StructureBaselineStorage`'s own Gson instance (used only for the on-disk file, never over
  JSON-RPC) *does* need a custom `Instant` `TypeAdapter`, since that one really does serialize
  `Instant` fields of the persisted `StructureSnapshot`.
- A persisted/transmitted node (`StructureViewProvider.toComparableNode`) deliberately drops
  `location` and `reference` - the differ never reads either, and on measured real baselines they
  account for roughly a third of the bytes. This matters because history entries are retained (up to
  10 by default) and each capture rewrites the whole per-project file.

## Client-side UI

- **Two independent toggles**, both persisted in workspace state and both **defaulting to off**:
  `hideUnchanged` (show only the path to changes) and `highlightChanges` (color/badge changed nodes).
  They default off so that opening the tree for the first time looks exactly like it did before this
  feature ever existed - diffing is something the user opts into, not something sprung on them the
  moment a baseline happens to exist. Deliberately independent of each other: either, both, or neither
  can be on.
- Root-level filtering by baseline presence was tried and **reverted**: hiding whole projects that
  have no captured baseline from the tree (even under "hide unchanged") was decided against - a
  project without a baseline should still show its full tree, not disappear.
- The project *row itself* is never filtered by `visibleChildren` - only children are - which is what
  keeps a project visible as the anchor/entry point even when it has no changes of its own to show
  (see `nodes.ts` `visibleChildren`'s doc comment for the reasoning between it and the reverted
  root-filtering attempt).
- `structureDiffUri` (`diff-decorations.ts`) manufactures a synthetic URI scheme
  (`spring-structure-diff:`) purely to hang a `FileDecoration` off of, because most structure nodes
  (stereotypes, groups, packages) have no real file URI at all, and for the ones that do, decorating
  the real file URI would collide with VSCode's own SCM decorations on that file. The change type is
  baked into the URI path itself so a node's decoration invalidates automatically when its change
  state changes, without needing an explicit decoration-changed event.
- "Show Changes" (originally named "Open Changes", renamed per user feedback) opens the git diff
  editor for the file behind a changed node - reusing VSCode's own diff view rather than building a
  custom one.
- Picking a historical baseline uses a `QuickPick` (`structure-tree-manager.ts`) rather than, say, an
  inline dropdown, listing short sha + commit message + capture time per retained entry, with a
  pinned "Latest commit" entry that clears the per-project override. The pick is **sticky**: it
  survives new commits until the user explicitly picks something else, or it falls off the retained
  history (in which case the backend's fail-open-to-newest behavior above takes over transparently).
- `baseline-labels.ts` exists purely to break an import cycle: both `nodes.ts` and
  `structure-tree-manager.ts` need the same "how do I describe a baseline to the user" formatting
  (`shortSha`, `formatCapturedAt`, `describeBaseline`), and having one import the other at runtime for
  just that created a value-level circular import.

## Preferences

- `boot-java.structure.git-baseline-enabled` (default **on**) - turns off all git-driven automatic
  capture/polling (`GitBaselineTracker.isEnabled`) for users who want manual-only baselines. There's
  also an internal `-Ddisable-structure-git-baseline` system property purely for the test suite: test
  project fixtures live inside this very repository's own git working tree, so without disabling it,
  every test that builds a structure tree would auto-capture a baseline against *this repository's*
  real commit history, which has nothing to do with what those tests are testing.
- `boot-java.structure.baseline-history-size` (default 10) - see the retained-history section above.

## Known deliberate non-goals (don't "fix" these without re-reading why)

- The MCP-facing tools (`getLogicalStructureChanges`, `diffAgainstBaseline`) always compare against
  the *newest* baseline - historical baseline selection is a VSCode-only concern for now.
- No visual cue in the tree beyond the project tooltip for "a non-default baseline is pinned."
- No explicit warning when a pinned snapshot has aged out of history; the tooltip just reports
  whichever snapshot the fail-open fallback actually used.
- The capture path (`StructureSnapshotStore.snapshotNow`) always builds its own, unfiltered tree
  rather than reusing a tree a caller might already have on hand - a request's tree is filtered down
  to whichever groups the client currently has selected, and a baseline captured from that would only
  ever be valid for whatever the user happened to have toggled on at capture time.
- No per-project eviction of the in-memory `history` map when a project is closed/removed - would
  need `ProjectObserver.Listener.deleted(IJavaProject)` wiring; judged out of scope so far since the
  in-memory footprint (structure trees stripped of location/reference) is small and per-project-name
  keyed, not per-open-instance.
