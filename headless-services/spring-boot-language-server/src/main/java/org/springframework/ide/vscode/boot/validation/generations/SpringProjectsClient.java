/*******************************************************************************
 * Copyright (c) 2020, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import java.net.URI;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.ide.vscode.boot.app.ClientHttpRequestFactoryProvider;
import org.springframework.ide.vscode.boot.validation.generations.json.Generations;
import org.springframework.ide.vscode.boot.validation.generations.json.Releases;
import org.springframework.ide.vscode.boot.validation.generations.json.SpringProjects;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class SpringProjectsClient {

	private static final MediaType HAL_JSON = MediaType.parseMediaType("application/hal+json");

	private final String url;
	private final URI baseUri;
	private final RestClient restClient;

	public SpringProjectsClient(String url, ClientHttpRequestFactoryProvider requestFactoryProvider) {
		this.url = url;
		this.baseUri = URI.create(url);
		ClientHttpRequestFactory requestFactory = requestFactoryProvider.createRequestFactory(baseUri.getHost());
		this.restClient = RestClient.builder()
				.baseUrl(baseUri.getScheme() + "://" + baseUri.getAuthority())
				.requestFactory(requestFactory)
				.build();
	}

	public String getUrl() {
		return url;
	}

	public SpringProjects getSpringProjects() throws Exception {
		return fromEmbedded(baseUri.getRawPath(), baseUri.getRawQuery(), SpringProjects.class);
	}

	public Generations getGenerations(String generationsUrl) throws Exception {
		return fromEmbeddedHref(generationsUrl, Generations.class);
	}

	public Releases getReleases(String releasesUrl) throws Exception {
		return fromEmbeddedHref(releasesUrl, Releases.class);
	}

	private <T> T fromEmbeddedHref(String href, Class<T> clazz) throws Exception {
		if (href == null) {
			return null;
		}
		URI hrefUri = URI.create(href);
		return fromEmbedded(hrefUri.getRawPath(), hrefUri.getRawQuery(), clazz);
	}

	private <T> T fromEmbedded(String path, String query, Class<T> clazz) throws Exception {
		Map<?, ?> result = get(path, query, Map.class);
		if (result != null) {
			Object obj = result.get("_embedded");
			if (obj != null) {
				ObjectMapper mapper = JsonMapper.builder()
						.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.build();
				return mapper.convertValue(obj, clazz);
			}
		}
		return null;
	}

	private <T> T get(String path, String query, Class<T> clazz) throws Exception {
		return restClient.get()
				.uri(uriBuilder -> uriBuilder.replacePath(path).replaceQuery(query).build())
				.accept(HAL_JSON)
				.retrieve()
				.body(clazz);
	}

}
