---
name: endpoints
description: Use when a user or agent asks which REST endpoints, request mappings, routes or HTTP APIs a Spring Boot project exposes, which controller handles a path or HTTP method, or wants an API inventory. Uses the spring-tools MCP server's request mapping index instead of grepping for mapping annotations.
allowed-tools:
  - Read
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getRequestMappings
  - mcp__plugin_spring-tools_spring-tools-mcp__findRequestMappingsByMethod
---

Answer endpoint questions from the request mapping index of the `spring-tools` MCP server. It resolves class-level and method-level `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` and `@PatchMapping` annotations (including combined paths, HTTP methods, API versions and content types), so prefer it over grepping the sources.

1. Resolve the project name with `getProjectList` (use the `projectName` field; `location` is the project root). Retry a few times while the list is still empty right after the session started.
2. Pick the tool that matches the question:
   - "Which endpoints does the application expose?" / "Give me the API inventory" - `getRequestMappings` with the project name. Present the result as a table of HTTP method, path, handler class and method; group by controller for large projects.
   - "Which endpoints accept `DELETE`?" / "Which handler serves `POST /orders`?" - `findRequestMappingsByMethod` with the project name and the HTTP method (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`), then filter by path.
3. Read the handler source file only when the answer needs code (request parameters, response type, security annotations); the tool output already contains file paths and line numbers.
4. For endpoint problems the language server also validates (e.g. `@RequestMapping` on a class without `@Controller`, or conflicting API versions), use the `validate` skill and fix the reported codes.

The index follows on-disk changes automatically (the language server watches the workspace), so a controller added during the session shows up without any refresh. If a very recent edit is not reflected yet, wait a moment and call the tool again.
