---
max_turns: 8
allowed_tools: [Read, Glob, Grep, Skill]
tags: [routing]
description: Routing only - Bash is not granted in eval runs, so the skill cannot download from start.spring.io here; this case checks that a scaffolding request reaches the create-spring-boot-project skill at all.
expected_outcome: Claude invokes the create-spring-boot-project skill for the request (it may then report that it cannot run curl in this session).
---

Create a new Spring Boot project called inventory-service with Spring Web and Spring Data JPA, using Maven and Java 21, in a folder named inventory-service.
