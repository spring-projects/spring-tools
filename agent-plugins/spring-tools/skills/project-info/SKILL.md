---
name: project-info
description: Use when a user or agent needs facts about the Spring Boot projects in the workspace - which projects exist and where they are, whether a directory is a Spring Boot project, the Java version, the Spring Boot version, or the resolved classpath (which library or version of a dependency is on it). Uses the spring-tools MCP server's project model instead of parsing build files by hand.
allowed-tools:
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getJavaVersion
  - mcp__plugin_spring-tools_spring-tools-mcp__getSpringBootVersion
  - mcp__plugin_spring-tools_spring-tools-mcp__getResolvedProjectClasspath
  - Skill(spring-tools:refresh)
---

Answer project and build questions from the project model of the `spring-tools` MCP server. The language server resolves every Maven (`pom.xml`) and Gradle (`build.gradle`, `build.gradle.kts`) project below the workspace directory, including dependencies from private repositories the build is configured for, so prefer it over reading build files or running the build.

1. `getProjectList` returns one entry per project with `projectName`, `isSpringBootProject`, `javaVersion` and `location` (the project root directory). Use `location` to map a file path to its project (the longest matching prefix wins in multi-module workspaces). Retry a few times while the list is still empty right after the session started - the project model is being resolved, which can take a couple of minutes on a cold dependency cache.
2. `getJavaVersion` and `getSpringBootVersion` (workspace project name) answer "which Java / Spring Boot version does this project target?" precisely; the Spring Boot version is resolved from the classpath, not from the parent POM declaration, so it is correct for BOM-managed and Gradle projects as well.
3. `getResolvedProjectClasspath` lists the resolved dependency JARs with their paths. Use it to answer "is library X on the classpath?", "which version of X is actually used?" (read the version from the JAR file name) or "which starters does this project pull in?"; for a large classpath summarize by group instead of listing every JAR.
4. When a project you expect is missing - e.g. it was just created or checked out and the list has not caught up - use the `refresh` skill and call `getProjectList` again. The server also watches the workspace, so new `pom.xml`/`build.gradle` files are normally discovered within a second by themselves.
5. For version support questions continue with the `spring-versions` skill; for validation problems use the `validate` skill.
