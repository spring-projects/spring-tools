package com.example.springai.demo;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

@Component
public class McpResourceWithinComponent {

	@McpResource(name = "getProfile", description = "Get the user profile resource")
	public String getProfile(String userId) {
		return userId;
	}

}
