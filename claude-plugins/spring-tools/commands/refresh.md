---
description: Refresh the Spring Tools workspace so the language server re-indexes files on disk (use after /rewind, git checkout, external edits, etc.).
allowed-tools:
  - mcp__spring-tools-mcp__refreshWorkspace
---

Call the `refreshWorkspace` tool on the `spring-tools-mcp` MCP server to force the Spring Tools language server to re-read project state from disk. Do not do anything else. After the tool returns, briefly confirm to the user that the Spring Tools workspace was refreshed.
