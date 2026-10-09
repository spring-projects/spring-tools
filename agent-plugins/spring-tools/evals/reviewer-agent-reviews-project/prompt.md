---
max_turns: 20
timeout_seconds: 600
allowed_tools: [Read, Glob, Grep, Skill, Agent]
tags: [agent]
expected_outcome: Claude delegates to the plugin's spring-reviewer subagent, which collects diagnostics, bean, endpoint and version information from the language server and returns a review with the redundant @Autowired constructor as the finding and the available Spring Boot updates as a recommendation; Claude relays that review.
---

Use the spring-reviewer agent to review this project and give me its findings.
