---
type: llm
weight: 2
---

The user asked what the Spring Tools diagnostic `JAVA_AUTOWIRED_CONSTRUCTOR` means and how to fix it.

PASS if the response explains that Spring injects a class's single constructor implicitly (implicit constructor injection, since Spring Framework 4.3), so the `@Autowired` annotation on that constructor is unnecessary, and recommends removing the annotation (optionally noting that `@Autowired` is only needed to pick one of several constructors).
FAIL if the response says the annotation is required, describes a different problem (for example field injection or a missing bean), or gives no concrete fix.
