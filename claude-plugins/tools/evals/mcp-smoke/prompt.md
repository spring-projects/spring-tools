---
max_turns: 4
timeout_seconds: 600
allowed_tools: []
tags: [smoke, mcp]
expected_outcome: The host connects to the mock Spring Tools MCP server, calls getProjectList once, and reports the recorded project name.
---

Call the Spring Tools MCP tool getProjectList exactly once, then answer only with the projectName from its result. Do not use any other tools.
