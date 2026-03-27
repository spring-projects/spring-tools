package com.example.springai.demo;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class McpToolsWithinComponent {

	@McpTool(name = "calculate", description = "Perform a calculation")
	public int calculate(int x, int y) {
		return x + y;
	}

	@McpPrompt(name = "greeting", description = "Generate a greeting message")
	public String greeting(String name) {
		return "Hello, " + name + "!";
	}

}
