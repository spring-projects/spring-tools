## Explanations

This warning appears on a Spring AI annotation — `@Tool`, `@McpTool`, `@McpPrompt`, or `@McpResource` — whose `description` attribute is present and non-blank but shorter than a configurable minimum length. By default the minimum is **30 characters**, so `@Tool(description = "Add numbers")` or `@McpTool(description = "Calc")` are flagged, while `@Tool(description = "Get the current weather for a given city")` is not.

The check applies to projects that have any dependency whose artifact name starts with `spring-ai-` on the classpath (for example `spring-ai-starter-model-openai` or `spring-ai-starter-mcp-server-webmvc`). It is not tied to a specific Spring Boot version; it is enabled whenever Spring AI is present. The problem is reported with WARNING severity by default and can be adjusted through the `spring-boot.ls.problem.spring-ai.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT` setting.

The reason for the check is the role that the description plays in tool calling. The Spring AI reference documentation describes the `@Tool` `description` attribute as "what the tool does and when to use it" and, in the `ToolDefinition` contract, as the "Description used by the model to decide when to call the tool." A two-word description such as `"Add numbers"` technically satisfies the "present" requirement but gives the model almost nothing to reason about: it does not say which numbers, of what type, whether the result is a sum or a concatenation, or when this tool is preferable to another one. In practice such descriptions lead to tools being skipped or invoked with the wrong intent. The length threshold is a heuristic that catches the most common form of this problem — a description that is really just a restatement of the method name.

The language server detects this by resolving the type of every annotation in a Java source file and matching it against `org.springframework.ai.tool.annotation.Tool`, `org.springframework.ai.mcp.annotation.McpTool`, `org.springframework.ai.mcp.annotation.McpPrompt`, and `org.springframework.ai.mcp.annotation.McpResource`. It reads the `description` attribute as a string, trims surrounding whitespace, and reports the problem when the trimmed length is smaller than the configured minimum. Descriptions that are absent or blank are **not** reported by this check; they are handled by `SPRING_AI_TOOL_MISSING_DESCRIPTION`. The problem range covers the whole annotation. Parameter-level annotations such as `@ToolParam` and `@McpToolParam` are not inspected. No quick fix is offered, because a good description has to be written by the author.

The minimum length can be changed through the setting `spring-boot.ls.problem-parameters.spring-ai.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT.minimum-length` (an integer, default `30`, must be at least `1`). Lower it if your team deliberately uses terse descriptions, or raise it to enforce more thorough ones.

For more details, see:

- [Spring AI Reference: Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) (sections "Declarative: @Tool" and "ToolDefinition")
- [Spring AI Reference: MCP Server Annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) (annotation attribute tables for `@McpTool`, `@McpResource`, and `@McpPrompt`)

## Fixes

**Fix 1: Expand the `@Tool` description to explain what the tool does and when to call it**

Turn the short label into a sentence that names the action, its inputs, its result, and the situation in which the model should reach for it. Adding `@ToolParam` descriptions for the parameters further improves how the model fills in arguments.

*Before:*

```java
package com.example.demo;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class MathTools {

	@Tool(description = "Add numbers")
	public int add(int a, int b) {
		return a + b;
	}

}
```

*After:*

```java
package com.example.demo;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MathTools {

	@Tool(description = "Add two integer values and return their sum. Use this whenever the user asks for the total of two whole numbers.")
	public int add(@ToolParam(description = "First addend") int a,
			@ToolParam(description = "Second addend") int b) {
		return a + b;
	}

}
```

**Fix 2: Expand the description of MCP server annotations**

The same threshold applies to `@McpTool`, `@McpPrompt`, and `@McpResource`. Describe the capability from the perspective of an MCP client that has never seen the implementation.

*Before:*

```java
package com.example.demo;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorMcpServer {

	@McpTool(name = "multiply", description = "Calc")
	public double multiply(@McpToolParam(description = "First factor") double a,
			@McpToolParam(description = "Second factor") double b) {
		return a * b;
	}

	@McpResource(uri = "config://{key}", name = "Configuration", description = "Config")
	public String getConfig(String key) {
		return System.getProperty(key, "");
	}

}
```

*After:*

```java
package com.example.demo;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorMcpServer {

	@McpTool(name = "multiply", description = "Multiply two decimal numbers and return the product")
	public double multiply(@McpToolParam(description = "First factor") double a,
			@McpToolParam(description = "Second factor") double b) {
		return a * b;
	}

	@McpResource(uri = "config://{key}", name = "Configuration",
			description = "Provides application configuration values looked up by key")
	public String getConfig(String key) {
		return System.getProperty(key, "");
	}

}
```

*Note: If a short description is genuinely sufficient for your use case, lower the threshold via `spring-boot.ls.problem-parameters.spring-ai.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT.minimum-length` rather than padding the text with filler words — the goal is a description that helps the model, not one that merely satisfies the length check.*
