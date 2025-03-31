package com.example.configproperties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("com.example.config.prefix.simple")
public class ConfigurationPropertiesExample {
	
	private String simpleConfigProp = "default config value";
	
	public ConfigurationPropertiesExample(String simpleConfigProp) {
		this.simpleConfigProp = simpleConfigProp;
	}
	
	public String getSimpleConfigProp() {
		return simpleConfigProp;
	}
	
}
