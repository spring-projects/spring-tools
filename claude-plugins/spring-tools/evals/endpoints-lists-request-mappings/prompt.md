---
max_turns: 12
allowed_tools: [Read, Glob, Grep, Skill]
tags: [smoke, endpoints]
expected_outcome: Claude lists the single endpoint of the workspace project, GET /greeting (API version 1) handled by TestController.sayHello(), from the language server's request mappings.
---

Which HTTP endpoints does this project expose, and which controller methods handle them?
