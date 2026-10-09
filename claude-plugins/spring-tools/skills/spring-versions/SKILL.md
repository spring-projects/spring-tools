---
name: spring-versions
description: Use when a user or agent asks which Spring Boot or Spring project version is current, whether a project is on a supported version, when OSS or commercial support ends, which releases exist or are upcoming, or whether a newer Spring Boot version is available for a project's Maven repositories. Uses the spring-tools MCP server's Spring IO release, generation and calendar tools.
allowed-tools:
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getSpringBootVersion
  - mcp__plugin_spring-tools_spring-tools-mcp__getLatestReleaseInformation
  - mcp__plugin_spring-tools_spring-tools-mcp__getLatestBootVersionsFromMavenRepo
  - mcp__plugin_spring-tools_spring-tools-mcp__getReleases
  - mcp__plugin_spring-tools_spring-tools-mcp__getGenerations
  - mcp__plugin_spring-tools_spring-tools-mcp__getUpcomingReleases
---

Answer Spring version and support questions with the live data the `spring-tools` MCP server obtains from Spring IO and the project's Maven repositories. Never guess release dates or support windows from memory - they change with every release.

Two kinds of names are involved; do not mix them up:

- the workspace project name from `getProjectList` (`projectName` field, e.g. `order-service`) for tools that look at the user's project;
- the Spring portfolio slug (`spring-boot`, `spring-framework`, `spring-data-jpa`, `spring-security`, ...) for tools that query Spring IO.

1. "Which Spring Boot version does this project use?" - `getSpringBootVersion` with the workspace project name (resolve it with `getProjectList` first).
2. "Is it supported / when does support end / what is the current version?" - `getLatestReleaseInformation` with the portfolio slug. It returns the current GA version plus the OSS and commercial support end dates of that generation. For the generation the project is actually on, use `getGenerations` and pick the entry matching the project's major.minor.
3. "Which newer version can I upgrade to?" - `getLatestBootVersionsFromMavenRepo` with the workspace project name. It resolves the newest patch, minor and major versions that are actually available from the repositories configured in the project's `pom.xml` (including private mirrors); `null` means already up to date at that level. Gradle projects are not supported by this tool - fall back to `getLatestReleaseInformation`.
4. "Which releases exist?" - `getReleases` with the slug (every release record, including milestones and release candidates).
5. "What is coming up?" - `getUpcomingReleases` (the Spring release calendar for the next weeks; no parameter).
6. Report versions and dates verbatim from the tool output, state which repository or API they came from, and recommend the smallest upgrade that gets the project back onto a supported generation. Leave the actual build file change to the user unless they ask for it; after changing the Spring Boot version run the `validate` skill because version-dependent validations (e.g. `BOOT_VERSION_VALIDATION_CODE`) may change.
