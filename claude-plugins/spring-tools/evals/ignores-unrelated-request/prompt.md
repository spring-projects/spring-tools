---
max_turns: 5
allowed_tools: [Read, Glob, Grep, Skill]
tags: [smoke, negative-control]
expected_outcome: Claude answers the question directly without invoking any Spring Tools skill or MCP tool.
---

Explain the difference between a Python list and a tuple in two sentences.
