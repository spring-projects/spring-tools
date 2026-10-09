---
max_turns: 12
allowed_tools: [Read, Glob, Grep, Skill]
tags: [smoke, quickfix]
expected_outcome: Claude reads the plugin's playbook explanations/JAVA_AUTOWIRED_CONSTRUCTOR.md and explains that a class with a single constructor is autowired implicitly (Spring 4.3+), so the @Autowired annotation on it is redundant and can be removed - keeping it only when a second constructor exists.
---

Spring Tools reports the diagnostic code JAVA_AUTOWIRED_CONSTRUCTOR on the constructor of my Sf7ValidationApplication class. What does it mean and how do I fix it?
