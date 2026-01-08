package com.example.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationPropertiesSource;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationPropertiesSource
public class HttpClientProperties {

	/**
	 * Base url to set in the underlying HTTP client group. By default, set to
	 * {@code null}.
	 */
	private @Nullable String baseUrl;

	/**
	 * Default request headers for interface client group. By default, set to empty
	 * {@link Map}.
	 */
	private Map<String, List<String>> defaultHeader = new LinkedHashMap<>();

	/**
	 * API version properties.
	 */
	@NestedConfigurationProperty
	private final ApiversionProperties apiversion = new ApiversionProperties();

	public @Nullable String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(@Nullable String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public Map<String, List<String>> getDefaultHeader() {
		return this.defaultHeader;
	}

	public void setDefaultHeader(Map<String, List<String>> defaultHeaders) {
		this.defaultHeader = defaultHeaders;
	}

	public ApiversionProperties getApiversion() {
		return this.apiversion;
	}

}