package com.example.demo;

import org.springframework.boot.context.properties.bind.Name;
import org.springframework.lang.Nullable;

public class ApiversionProperties {

	/**
	 * Default version that should be used for each request.
	 */
	@Name("default")
	private String defaultVersion;

	public @Nullable String getDefaultVersion() {
		return this.defaultVersion;
	}

	public void setDefaultVersion(@Nullable String defaultVersion) {
		this.defaultVersion = defaultVersion;
	}

}
