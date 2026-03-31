package com.example.springai.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ToolWithAnnotatedParam {

	@Tool(description = "Convert temperature from Celsius to Fahrenheit")
	public double convertTemperature(@JsonProperty("celsius") double celsius) {
		return celsius * 9.0 / 5.0 + 32;
	}

}
