package com.example.configproperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(value = "com.example.config.prefix.simple2")
public class ConfigurationPropertiesExampleWithConfigurationAnnotation {
	
	private String simpleConfigProp = "default config value";
	
	public ConfigurationPropertiesExampleWithConfigurationAnnotation(String simpleConfigProp) {
		this.simpleConfigProp = simpleConfigProp;
	}
	
	public String getSimpleConfigProp() {
		return simpleConfigProp;
	}
	
}
