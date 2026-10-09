---
max_turns: 40
allowed_tools: [Read, Glob, Grep, Skill]
tags: [smoke, mcp-coverage]
expected_outcome: The agent calls every listed Spring Tools MCP tool at least once and summarizes the checked areas without editing files.
---

This eval runs against an isolated mock Spring Tools server. Call every MCP tool listed below at least once, using `sf7-validation` as the IDE project name and `spring-boot` as the Spring IO project slug where those parameters apply. For required type, bean, HTTP method, stereotype, and file-path arguments, use `org.example.Service`, `service`, `GET`, `Controller`, and `/tmp/spring-tools-eval/Probe.java` as appropriate. Use each tool's schema for any optional arguments.

Call the project discovery, diagnostics, information, component, endpoint, structure, Spring release, workspace refresh, and file-notification tools, including the tools that capture or clear a logical-structure baseline. The mocked calls are isolated and are safe for this eval. Do not edit project files. After the calls, briefly summarize which categories you checked.

The required tool calls are:

`getProjectList`, `getProjectDiagnostics`, `getSpringBootVersion`, `getJavaVersion`, `getResolvedProjectClasspath`, `getBeanDetails`, `getBeanUsageInfo`, `findBeansByType`, `getRequestMappings`, `findRequestMappingsByMethod`, `getStereotypesList`, `getListOfComponentsAndTheirStereotypes`, `findComponentsByStereotype`, `getLogicalStructure`, `getStructureDependencies`, `captureLogicalStructureBaseline`, `clearLogicalStructureBaseline`, `getLogicalStructureChanges`, `getLogicalStructureBaselineHistory`, `getReleases`, `getGenerations`, `getUpcomingReleases`, `getLatestReleaseInformation`, `getLatestBootVersionsFromMavenRepo`, `fileChanged`, `fileDeleted`, and `refreshWorkspace`.
