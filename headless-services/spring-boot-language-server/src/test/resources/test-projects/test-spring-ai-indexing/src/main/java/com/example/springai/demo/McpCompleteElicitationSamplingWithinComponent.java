package com.example.springai.demo;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.stereotype.Component;

@Component
public class McpCompleteElicitationSamplingWithinComponent {

	@McpComplete(name = "completeCode", description = "Provide code completion suggestions")
	public String completeCode(String prefix) {
		return prefix + "completed";
	}

	@McpElicitation(name = "getUserInfo", description = "Elicit information from the user")
	public String getUserInfo(String prompt) {
		return prompt;
	}

	@McpSampling(name = "sampleText", description = "Sample text from a language model")
	public String sampleText(String context) {
		return context;
	}

}
