---
name: refresh
description: Refresh the Spring Tools workspace so the language server re-indexes files on disk (use after /rewind, a large git checkout, external edits the watcher may have missed, or when a project is missing from getProjectList).
allowed-tools:
  - mcp__plugin_spring-tools_spring-tools-mcp__refreshWorkspace
  - mcp__plugin_spring-tools_spring-tools-mcp__fileChanged
  - mcp__plugin_spring-tools_spring-tools-mcp__fileDeleted
---

Bring the Spring Tools index back in sync with the files on disk. The language server watches the workspace directory and the plugin's hooks report Claude's own edits, so this is only needed when the index looks stale (a diagnostic refers to code that is gone, a new project or class is missing, the tree changed massively after `/rewind` or a branch switch).

- Without arguments, or after a git checkout/pull/stash, `/rewind` or a build tool regenerating sources: call the `refreshWorkspace` tool on the `spring-tools-mcp` MCP server. It re-reads the project model (discovering added and removed projects) and re-indexes every source file.
- When the user names one file or a small set of files that changed or disappeared outside of Claude's edits: call `fileChanged` (absolute path) for each file that exists and `fileDeleted` for each file that was removed instead of a full refresh - this is much cheaper on large workspaces.

Do not do anything else. After the tool returns, briefly confirm to the user what was refreshed.
