/*******************************************************************************
 * Copyright (c) 2020, 2023 Pivotal, Inc.
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

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ide.vscode.boot.app.RestTemplateFactory;
import org.springframework.ide.vscode.boot.validation.generations.json.Generations;
import org.springframework.ide.vscode.boot.validation.generations.json.Releases;
import org.springframework.ide.vscode.boot.validation.generations.json.SpringProjects;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class SpringProjectsClient {

	private final String url;
	private RestTemplateFactory restTemplateFactory;

	public SpringProjectsClient(String url, RestTemplateFactory restTemplateFactory) {
		this.url = url;
		this.restTemplateFactory = restTemplateFactory;
	}

	public String getUrl() {
		return url;
	}

	public SpringProjects getSpringProjects() throws Exception {
		return fromEmbedded(url, SpringProjects.class);
	}

	public Generations getGenerations(String generationsUrl) throws Exception {
		return fromEmbedded(generationsUrl, Generations.class);
	}
	
	public Releases getReleases(String releasesUrl) throws Exception {
		return fromEmbedded(releasesUrl, Releases.class);
	}
	
	private <T> T fromEmbedded(String url, Class<T> clazz) throws Exception {
		if (url != null) {
			Map<?, ?> result = get(url, Map.class);
			if (result != null) {
				Object obj = result.get("_embedded");
				if (obj != null) {
					
					ObjectMapper mapper = JsonMapper.builder()
						    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						    .build();
					
					return mapper.convertValue(obj, clazz);
				}
			}
		}
		return null;
	}
	
	private <T> T get(String url, Class<T> clazz) throws Exception {
		HttpHeaders headers = new HttpHeaders();

		headers.setAccept(MediaType.parseMediaTypes("application/hal+json"));

		@SuppressWarnings({ "rawtypes" })
		HttpEntity<?> entity = new HttpEntity(headers);

		URI uri = URI.create(url);
		RestTemplate restTemplate = restTemplateFactory.createRestTemplate(uri.getHost());
		ResponseEntity<T> response = restTemplate.exchange(uri, HttpMethod.GET, entity, clazz);

		return response.getBody();
	}

}
