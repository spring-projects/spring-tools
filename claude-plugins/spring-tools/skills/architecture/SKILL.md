---
name: architecture
description: Use when a user or agent asks about the architecture, logical structure, layers, stereotypes or components of a Spring Boot project (all controllers, all repositories, aggregates, modules), or wants to know how the structure changed since a baseline or commit. Uses the spring-tools MCP server's logical structure and stereotype tools.
allowed-tools:
  - Read
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getLogicalStructure
  - mcp__plugin_spring-tools_spring-tools-mcp__getStructureDependencies
  - mcp__plugin_spring-tools_spring-tools-mcp__getStereotypesList
  - mcp__plugin_spring-tools_spring-tools-mcp__findComponentsByStereotype
  - mcp__plugin_spring-tools_spring-tools-mcp__getListOfComponentsAndTheirStereotypes
  - mcp__plugin_spring-tools_spring-tools-mcp__captureLogicalStructureBaseline
  - mcp__plugin_spring-tools_spring-tools-mcp__getLogicalStructureChanges
  - mcp__plugin_spring-tools_spring-tools-mcp__getLogicalStructureBaselineHistory
  - mcp__plugin_spring-tools_spring-tools-mcp__clearLogicalStructureBaseline
---

Answer architecture questions from the `spring-tools` MCP server. The language server classifies every Spring component with stereotypes (Controller, Service, Repository, Component, Entity, Aggregate, Module, ... as defined by the project's `spring-stereotype` or JMolecules metadata plus the built-ins) and renders them as the same logical structure tree that Spring Tools shows in VS Code and Eclipse.

1. Resolve the project name with `getProjectList` (use the `projectName` field). Retry a few times while the list is still empty right after the session started.
2. Pick the tool that matches the question:
   - "Show me the architecture / structure of the project" - `getLogicalStructure`. Present the tree top-down (modules or packages, then stereotype groups, then components). For large projects summarize each group with counts and only expand what the user asked about.
   - "Does the structure include our other modules or dependencies?" - `getStructureDependencies` lists the workspace projects and libraries the project depends on; pass their `id` values to `getLogicalStructure` to fold their components into the tree when the user wants the wider picture.
   - "Which stereotypes exist here?" - `getStereotypesList`; it also reveals custom stereotypes the project defines.
   - "List all repositories / controllers / aggregates" - `findComponentsByStereotype` with the stereotype name. Use `getListOfComponentsAndTheirStereotypes` when you need every class with all of its stereotypes (e.g. to find classes that carry two roles).
   - "What did this refactoring change structurally?" - before a larger change call `captureLogicalStructureBaseline`; afterwards `getLogicalStructureChanges` reports added, removed and moved components (pass `includeUnchanged: true` only when the user wants the full tree). For git-backed projects a baseline is derived from the last commit automatically, so `getLogicalStructureChanges` works without a capture as well. `getLogicalStructureBaselineHistory` lists retained snapshots with their commits; `clearLogicalStructureBaseline` discards a manual capture.
3. Read source files only to back a specific finding (e.g. show the class that violates a layering rule); tool output already contains file paths.
4. Combine with the `beans` skill for wiring between components, the `endpoints` skill for the HTTP surface of controllers, and the `validate` skill for stereotype- or structure-related problems reported by the language server.

The index follows on-disk changes automatically (the language server watches the workspace), so the structure reflects the files as they are on disk. If a very recent edit is not reflected yet, wait a moment and call the tool again.
