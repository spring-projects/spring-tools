---
max_turns: 12
allowed_tools: [Read, Glob, Grep, Skill]
tags: [smoke, versions]
expected_outcome: Claude compares the project's Spring Boot 4.0.0 with the latest release 4.1.1 reported by the language server, notes that the project is behind (4.0.8 is the latest patch) and gives the OSS support end date 2027-07-31 for the current generation.
---

Is this project on a current Spring Boot version? Tell me what it uses, what the latest release is, and how long the OSS support lasts.
