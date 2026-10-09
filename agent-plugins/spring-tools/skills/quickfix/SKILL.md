---
name: quickfix
description: Retrieves the explanation and fix instructions for a specific Spring Boot diagnostic error code and applies the fix to a specific file. Use this when you encounter a Spring Boot warning or error and need to know how to fix it.
argument-hint: "<error_code> <file_path> [range]"
arguments: [code, file, range]
allowed-tools:
  - Read
  - mcp__plugin_spring-tools_spring-tools-mcp__getProjectList
  - mcp__plugin_spring-tools_spring-tools-mcp__getSpringBootVersion
---

Use the Spring Boot diagnostic code, file path, and text range from the current request or conversation. If the code or target file is missing, ask for the diagnostic or use the Spring Tools diagnostics before proposing a fix. A missing range means the whole relevant construct may be considered.

MUST DO: If the error code includes a prefix like `errorCode=` or `code=`, strip it out before you continue to use it anywhere.

To find the official explanation and potential fixes for this issue, read its explanation file from `explanations/<CODE>.md` in this plugin. Locate it using the absolute explanations directory named in the Spring Tools MCP server instructions. If that path is unavailable, use the skill's plugin-relative path:

- `${CLAUDE_PLUGIN_ROOT}/explanations/<CODE>.md` in Claude Code, replacing `<CODE>` with the diagnostic code, or
- `../../explanations/<CODE>.md` relative to this skill directory in other plugin hosts.

If that explanation refers to another diagnostic code (for example "see `JAVA_LAMBDA_DSL`"), also read the explanation file of that code before deciding on a fix; such hints are usually meant to be applied together.

If the file does not exist, use the diagnostic message reported by the spring-tools MCP server (it often names the exact element, path, or version involved) together with your general Spring Boot knowledge to fix the issue.

Based on the provided "Explanations" and "Fixes" in that file:

1. Determine which Spring Boot, Spring Framework and Spring Security versions the project uses (use the `getSpringBootVersion` tool of the spring-tools MCP server, or read the build file). The explanations contain version-specific instructions - for example APIs that only exist from a certain version on, or that were removed later - and the fix must compile on the project's versions.
2. Analyze the context of the user's project and the diagnosed file to determine which of the suggested fixes is the most appropriate.
3. If there are multiple potential fixes and it is unclear which one to apply based on the project context, stop and ask the user which solution they prefer.
4. Once a solution is chosen (either by your analysis or the user's choice), apply the fix to the diagnosed file.
5. If a text range is provided, keep the fix focused on that range.
6. The language server watches the workspace and picks up the edited files by itself; to confirm that the diagnostic is gone, re-run the `validate` skill after all fixes have been applied rather than after each single one.
