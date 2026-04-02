package com.example.springai.demo;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.stereotype.Component;

@Component
public class McpCompleteElicitationSamplingWithinComponent {

	@McpComplete(prompt = "completeCode")
	public String completeCode(String prefix) {
		return prefix + "completed";
	}

	@McpElicitation(clients = "my-client")
	public String getUserInfo(String prompt) {
		return prompt;
	}

	@McpSampling(clients = "my-client")
	public String sampleText(String context) {
		return context;
	}

}
