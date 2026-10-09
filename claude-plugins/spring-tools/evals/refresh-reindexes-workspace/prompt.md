---
max_turns: 10
allowed_tools: [Read, Glob, Grep, Skill]
tags: [smoke, refresh]
expected_outcome: Claude asks the language server to re-index the workspace (refreshWorkspace) instead of merely explaining what a refresh is, and confirms that the index was refreshed.
---

I just switched git branches outside of this session and the Spring diagnostics look stale. Make the Spring Tools language server re-index the workspace from disk.
