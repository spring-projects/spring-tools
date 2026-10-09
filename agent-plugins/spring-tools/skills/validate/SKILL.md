---
name: validate
description: MUST be used whenever a user or agent asks to find problems, check for issues, or validate changes in a Spring Boot project. Uses the spring-tools MCP server to check for validation problems in the project's source files.
allowed-tools:
  - Read
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectDiagnostics
  - Skill(spring-tools:quickfix *)
---

CRITICAL REQUIREMENT: Whenever a user asks for problems or validations or diagnostics or problem markers in the project, or an agent is attempting to check if there are problems with the project, or attempts to validate changes to the project, IF the project is a Spring Boot project, then the `spring-tools` plugin MCP server MUST be used to check for validation problems in each source file of the project.

To perform this validation:

1. Use the `getProjectDiagnostics` tool from the spring tools MCP server to get a list of diagnostics for a specific project. You need to pass in the name of the project. If you are not sure about the exact name, you can invoke `getProjectList` from the spring tools MCP server to get the exact names of the projects in the current workspace (use the `projectName` field). If `getProjectList` returns an empty list right after the session started, the language server is still resolving the Maven/Gradle project model (this can take a couple of minutes on a cold dependency cache) - wait a few seconds and call it again, up to several times, before concluding that there is no Spring Boot project.
2. Carefully review the returned diagnostics.
3. Identify the specific error code, file path, and text range for each Spring-related diagnostic (e.g. error code "HTTP_SECURITY_AUTHORIZE_HTTP_REQUESTS").
4. For every Spring-specific error code you encounter, invoke the `quickfix` skill with the error code, file path, and text range from the diagnostic. Keep the diagnostic message at hand - it often contains the specifics needed for the fix (e.g. the detected common path or the affected version).
5. Apply the appropriate fix based on the instructions, or ask the user if a choice needs to be made.
6. Also ensure any standard Java compilation or build errors are addressed.
7. After all fixes have been applied, call `getProjectDiagnostics` again to confirm that the reported problems are gone (the plugin's hooks notify the language server about edited files automatically) and report any remaining ones.
