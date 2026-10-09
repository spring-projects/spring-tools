## Explanations

This warning appears on a Spring AI annotation — `@Tool`, `@McpTool`, `@McpPrompt`, or `@McpResource` — whose `description` attribute is missing, empty, or contains only whitespace. Examples that trigger it are a bare `@Tool` marker annotation, `@Tool(description = "")`, or `@McpTool(name = "add", description = "   ")`.

The check applies to projects that have any dependency whose artifact name starts with `spring-ai-` on the classpath (for example `spring-ai-starter-model-openai`, `spring-ai-starter-mcp-server-webmvc`, or `spring-ai-model`). It is not tied to a specific Spring Boot version; it is enabled whenever Spring AI is present. The problem is reported with WARNING severity by default and can be adjusted through the `spring-boot.ls.problem.spring-ai.SPRING_AI_TOOL_MISSING_DESCRIPTION` setting.

The description is not documentation for humans — it is the primary signal a language model uses to decide *whether* and *when* to invoke a tool, prompt, or resource. The Spring AI reference documentation describes the `@Tool` attribute as follows:

> `description` — what the tool does and when to use it. Strongly recommended; without it the model has no guidance on when to call the tool.

The underlying `ToolDefinition` contract makes the role explicit: the description is the "Description used by the model to decide when to call the tool." For MCP server annotations the `description` attribute of `@McpTool` is likewise documented as the "Human-readable description of the tool", and `@McpPrompt` and `@McpResource` carry a `description` attribute with the same purpose for prompts and resources exposed to MCP clients.

Without a description, the model only sees the tool's name and JSON input schema. For a method such as `getWeather(String city)` that may be enough to guess, but for anything less self-explanatory the model will either ignore the tool, call it at the wrong moment, or hallucinate a purpose for it. Adding a description is the cheapest and most effective way to improve tool selection accuracy.

The language server detects this by resolving the type of every annotation in a Java source file and matching it against the fully qualified names `org.springframework.ai.tool.annotation.Tool`, `org.springframework.ai.mcp.annotation.McpTool`, `org.springframework.ai.mcp.annotation.McpPrompt`, and `org.springframework.ai.mcp.annotation.McpResource`. It then reads the `description` attribute as a string and reports the problem if the attribute is absent, the value is blank, or the value cannot be resolved to a compile-time string constant (for example a method call). The problem range covers the whole annotation. Only the annotation's own `description` is checked; parameter-level annotations such as `@ToolParam` and `@McpToolParam` are not part of this validation. Note that `@McpTool` falls back to the method name as its description at runtime (see the attribute table in the MCP server annotations documentation), so a bare `@McpTool` "works" but still hands the model nothing more than a method name; the language server therefore reports the missing attribute regardless. No quick fix is offered, because a meaningful description cannot be generated automatically.

A description that is present but very short is reported separately by `SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT`.

For more details, see:

- [Spring AI Reference: Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) (sections "Declarative: @Tool" and "ToolDefinition")
- [Spring AI Reference: MCP Server Annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) (annotation attribute tables for `@McpTool`, `@McpResource`, and `@McpPrompt`)

## Fixes

**Fix 1: Add a `description` that states what the tool does and when to use it**

Write the description from the model's point of view: name the action, the inputs it needs, and the situation in which it is the right choice. Describe individual parameters with `@ToolParam` (or `@McpToolParam` for MCP tools) so the model also knows how to fill in the arguments.

*Before:*

```java
package com.example.demo;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class WeatherTools {

	private final WeatherService weatherService;

	public WeatherTools(WeatherService weatherService) {
		this.weatherService = weatherService;
	}

	@Tool
	public String getWeather(String city) {
		return weatherService.fetch(city);
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
public class WeatherTools {

	private final WeatherService weatherService;

	public WeatherTools(WeatherService weatherService) {
		this.weatherService = weatherService;
	}

	@Tool(description = "Get the current weather for a given city")
	public String getWeather(@ToolParam(description = "City name") String city) {
		return weatherService.fetch(city);
	}

}
```

**Fix 2: Add a `description` to MCP server annotations**

The same rule applies to tools, prompts, and resources exposed through an MCP server. Each `@McpTool`, `@McpPrompt`, and `@McpResource` should describe what it provides so that connected MCP clients (and the models behind them) can select it correctly.

*Before:*

```java
package com.example.demo;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorMcpServer {

	@McpTool(name = "add")
	public int add(@McpToolParam(description = "First number") int a,
			@McpToolParam(description = "Second number") int b) {
		return a + b;
	}

	@McpPrompt(name = "greeting")
	public String greeting(String name) {
		return "Hello, " + name + "! How can I help you today?";
	}

	@McpResource(uri = "config://{key}", name = "Configuration", description = "")
	public String getConfig(String key) {
		return System.getProperty(key, "");
	}

}
```

*After:*

```java
package com.example.demo;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorMcpServer {

	@McpTool(name = "add", description = "Add two integer numbers together and return their sum")
	public int add(@McpToolParam(description = "First number") int a,
			@McpToolParam(description = "Second number") int b) {
		return a + b;
	}

	@McpPrompt(name = "greeting", description = "Generate a friendly greeting message for the given user name")
	public String greeting(String name) {
		return "Hello, " + name + "! How can I help you today?";
	}

	@McpResource(uri = "config://{key}", name = "Configuration",
			description = "Provides application configuration values looked up by key")
	public String getConfig(String key) {
		return System.getProperty(key, "");
	}

}
```

*Note: The `name` attribute defaults to the method name and must be unique within the set of tools handed to a model; it does not replace the description. A method name like `add` tells the model nothing about the numeric types, units, or side effects involved — put that information in `description`.*
