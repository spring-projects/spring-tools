---
name: beans
description: Use when a user or agent asks how Spring beans are wired in a Spring Boot project - which beans exist, where a bean is defined, where it is injected, which beans implement an interface or match a type, or why a dependency cannot be resolved. Uses the spring-tools MCP server's bean index instead of grepping the sources.
allowed-tools:
  - Read
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getBeanDetails
  - mcp__plugin_spring-tools_spring-tools-mcp__findBeansByType
  - mcp__plugin_spring-tools_spring-tools-mcp__getBeanUsageInfo
---

Answer bean wiring questions from the Spring index of the `spring-tools` MCP server. The index knows every bean the language server found (stereotype-annotated classes, `@Bean` methods, configuration classes) together with their injection points, so prefer it over grepping the sources.

1. Resolve the project name with `getProjectList` (use the `projectName` field; `location` is the project root, pick the project whose location contains the file the user is talking about). Retry a few times while the list is still empty right after the session started.
2. Pick the tool that matches the question:
   - "Which beans are there / what does the application context contain?" - `getBeanDetails` with the project name. It returns every indexed bean with its type, defining element and injection points. Summarize by stereotype or package for large projects instead of listing everything.
   - "Which beans implement `X` / which bean of type `X` will be injected?" - `findBeansByType` with the fully qualified type name. Several matches mean an `@Autowired` field of that type needs `@Qualifier`, `@Primary` or a `List<X>`/`Map<String, X>` injection.
   - "Where is bean `foo` defined and where is it used?" - `getBeanUsageInfo` with the bean name. Bean names default to the decapitalized simple class name (`orderService` for `OrderService`) or the `@Bean` method name.
3. Read the defining source file only when the answer needs code (e.g. to show the constructor or the `@Bean` method); the tool output already contains file paths and line numbers.
4. When the question is about a missing or ambiguous dependency, cross-check with the `validate` skill - the language server reports codes such as `JAVA_AUTOWIRED_CONSTRUCTOR` or `JAVA_PUBLIC_BEAN_METHOD` for related problems.

The index follows on-disk changes automatically (the language server watches the workspace), so results reflect files edited during the session. If a very recent edit is not reflected yet, wait a moment and call the tool again.
