---
name: validat
description: Fixture skill with several configuration mistakes.
allowed-tools:
  - Read
  - mcp__spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getDiagnostics
  - Skill(spring-tools:nope)
  - Skill(quickfix)
---

Use the `getDiagnostics` tool from the spring tools MCP server.
