package com.example.springai.demo;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ToolsWithinComponent {

	@Tool(name = "add", description = "Add two numbers together")
	public int add(int a, int b) {
		return a + b;
	}

	@Tool(description = "Fetch the current weather for a given city")
	public String getWeather(String city) {
		return "Sunny in " + city;
	}

}
