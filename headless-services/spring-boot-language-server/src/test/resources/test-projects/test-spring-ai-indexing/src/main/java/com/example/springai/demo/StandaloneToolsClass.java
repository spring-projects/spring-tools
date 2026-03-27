package com.example.springai.demo;

import org.springframework.ai.tool.annotation.Tool;

public class StandaloneToolsClass {

	@Tool(name = "search", description = "Search for information")
	public String search(String query) {
		return "Results for: " + query;
	}

}
