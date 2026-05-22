---
name: quickfix
description: Retrieves the explanation and fix instructions for a specific Spring Boot diagnostic error code and applies the fix to a specific file. Use this when you encounter a Spring Boot warning or error and need to know how to fix it.
arguments: [error_code, file_path, range]
allowed-tools: View
---
You have encountered the Spring Boot diagnostic error code: `$ARGUMENTS[0]`.
The problem is located in the file: `$ARGUMENTS[1]`.
The text range of the problem is: `$ARGUMENTS[2]` (if provided).

To find the official explanation and potential fixes for this issue, you must read the explanation file located at:
`${CLAUDE_PLUGIN_ROOT}/explanations/$ARGUMENTS[0].md`

(Note: If the error code includes a prefix like `errorCode=`, strip it out before forming the file path).

If the file does not exist, use your general Spring Boot knowledge to fix the issue.

Based on the provided "Explanations" and "Fixes" in that file:
1. Analyze the context of the user's project and the specific file (`$ARGUMENTS[1]`) to determine which of the suggested fixes is the most appropriate.
2. If there are multiple potential fixes and it is unclear which one to apply based on the project context, stop and ask the user which solution they prefer.
3. Once a solution is chosen (either by your analysis or the user's choice), apply the fix to the codebase, specifically targeting the file `$ARGUMENTS[1]`.
4. If a text range (`$ARGUMENTS[2]`) is provided, ensure your fix is applied only around that specific text range.