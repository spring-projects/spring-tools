package com.example.springai.demo;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ToolWithMcpToolParam {

	@McpTool(description = "Divide one number by another")
	public double divide(
			@McpToolParam(description = "The dividend") double dividend,
			@McpToolParam(description = "The divisor", required = false) double divisor) {
		return dividend / divisor;
	}

}
