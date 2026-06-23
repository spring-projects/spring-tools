package com.example.configproperties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "com.example.config.inner.records")
public class ConfigurationPropertiesWithInnerRecords {

	private String name;
	private Server server;

	public record Server(String host, int port) {}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Server getServer() {
		return server;
	}

	public void setServer(Server server) {
		this.server = server;
	}
}
