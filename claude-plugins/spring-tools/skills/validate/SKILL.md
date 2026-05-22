---
description: MUST be used whenever a user or agent asks to find problems, check for issues, or validate changes in a Spring Boot project. Uses the spring-tools LSP plugin to check for validation problems in the project's source files.
allowed-tools: View
---
CRITICAL REQUIREMENT: Whenever a user asks for problems in the project, or an agent is attempting to check if there are problems with the project, or attempts to validate changes to the project, IF the project is a Spring Boot project, then the `spring-tools` LSP plugin MUST be used to check for validation problems in each source file of the project.

To perform this validation:

1. Use the `View` tool to open the modified files (or the files you want to check). In Claude Code, opening a file triggers the Language Server Protocol (LSP), and any errors or warnings will automatically appear in your context as diagnostics.
2. Carefully review the returned diagnostics for any diagnostic that originated from the `spring-tools` LSP plugin.
3. Identify the specific error code, file path, and text range for each Spring-related diagnostic (e.g. error code "WEB_SECURITY_CONFIGURER_ADAPTER").
4. For every Spring-specific error code you encounter, you MUST invoke the `/spring-tools:quickfix` skill, passing the error code, the file path, and the text range as arguments, to retrieve the official explanation and fix instructions.
5. Apply the appropriate fix based on the instructions, or ask the user if a choice needs to be made.
6. Also ensure any standard Java compilation or build errors are addressed.