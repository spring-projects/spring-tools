/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveProperties;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveProperty;
import org.springframework.ide.vscode.boot.java.livehover.v2.LivePropertySource;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveRequestMappingBoot2xDispatcherServletMapping;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveData;
import org.springframework.ide.vscode.boot.java.requestmapping.VersionedPaths;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.project.harness.SpringProcessLiveDataBuilder;

/**
 * @author Seonwoo Jung
 */
public class VersionedPathsTest {

	@Test
	void apiVersionFromBoot4Predicate() {
		assertEquals("1.1", mapping("{GET [/{version}/greeting], version [1.1]}").getApiVersion());
		assertEquals("1.1", mapping("{GET [/{version}/greeting], version [1.1+]}").getApiVersion());
		assertEquals("2", mapping("{GET [/{version}/greeting], produces [application/json], version [2], and [custom]}").getApiVersion());
		assertNull(mapping("{GET [/greeting]}").getApiVersion());
	}

	@Test
	void versionIsPutIntoUriVariableSegment() {
		assertEquals("/1.1/greeting", versionedPath("/{version}/greeting", "1.1", 0));
		assertEquals("/api/v2/greeting", versionedPath("/api/{v}/greeting", "v2", 1));
		assertEquals("/1/users/{id}", versionedPath("/{version:\\d+}/users/{id}", "1", 0));

		// only URI variables are replaced
		assertEquals("/api/{v}/greeting", versionedPath("/api/{v}/greeting", "1", 0));
		assertEquals("/{*path}", versionedPath("/{*path}", "1", 0));

		// index out of range
		assertEquals("/{version}", versionedPath("/{version}", "1", 1));
	}

	@Test
	void unversionedMappingOrUnknownIndexKeepsPath() {
		VersionedPaths unversioned = new VersionedPaths(mapping("{GET [/{version}/greeting]}"), null, List.of(webConfig("Path Segment: 0")));
		assertEquals("/{version}/greeting", unversioned.getVersionedPath("/{version}/greeting"));

		VersionedPaths noIndex = new VersionedPaths(mapping("{GET [/{version}/greeting], version [1]}"), null, List.of());
		assertEquals("/{version}/greeting", noIndex.getVersionedPath("/{version}/greeting"));
	}

	@Test
	void indexFromWebConfigs() {
		assertEquals("/1/greeting", versionedPath("/{version}/greeting", List.of(webConfig("Path Segment: 0"), webConfig("Path Segment: 0", "Request Header: X-API-Version"))));
		assertEquals("/api/1/greeting", versionedPath("/api/{version}/greeting", List.of(webConfig("Request Header: X-API-Version"), webConfig("Path Segment: 1"))));

		// conflicting or unparsable indices
		assertEquals("/{version}/greeting", versionedPath("/{version}/greeting", List.of(webConfig("Path Segment: 0"), webConfig("Path Segment: 1"))));
		assertEquals("/{version}/greeting", versionedPath("/{version}/greeting", List.of(webConfig("Path Segment: SEGMENT_INDEX"))));
	}

	@Test
	void liveIndexTakesPrecedenceOverConfiguredIndex() {
		SpringProcessLiveData liveData = liveDataWithProperty("spring.mvc.apiversion.use.path-segment", "1");
		VersionedPaths versionedPaths = new VersionedPaths(mapping("{GET [/api/{version}/greeting], version [1.1]}"), liveData, List.of(webConfig("Path Segment: 0")));
		assertEquals("/api/1.1/greeting", versionedPaths.getVersionedPath("/api/{version}/greeting"));

		// no project at hand
		assertEquals("/api/1.1/greeting", new VersionedPaths(mapping("{GET [/api/{version}/greeting], version [1.1]}"), liveData, List.of())
				.getVersionedPath("/api/{version}/greeting"));
	}

	@Test
	void sanitizedLivePropertyIsIgnored() {
		SpringProcessLiveData liveData = liveDataWithProperty("spring.mvc.apiversion.use.path-segment", "******");
		VersionedPaths versionedPaths = new VersionedPaths(mapping("{GET [/{version}/greeting], version [1.1]}"), liveData, List.of(webConfig("Path Segment: 0")));
		assertEquals("/1.1/greeting", versionedPaths.getVersionedPath("/{version}/greeting"));
	}

	@Test
	void splitPathOfMapping() {
		VersionedPaths versionedPaths = new VersionedPaths(mapping("{GET [/greeting]}"), null, List.of());
		assertArrayEquals(new String[] {"/greeting"}, versionedPaths.getSplitPath());
	}

	private static String versionedPath(String path, String version, int index) {
		return new VersionedPaths(mapping("{GET [/greeting], version [%s]}".formatted(version)), null, List.of(webConfig("Path Segment: " + index)))
				.getVersionedPath(path);
	}

	private static String versionedPath(String path, List<WebConfigIndexElement> webConfigs) {
		return new VersionedPaths(mapping("{GET [/greeting], version [1]}"), null, webConfigs).getVersionedPath(path);
	}

	private static LiveRequestMappingBoot2xDispatcherServletMapping mapping(String predicate) {
		return new LiveRequestMappingBoot2xDispatcherServletMapping(new JSONObject("""
			{
			  "predicate": "%s",
			  "handler": "com.example.Api#greeting()",
			  "details": {
			    "handlerMethod": { "className": "com.example.Api", "name": "greeting", "descriptor": "()Ljava/lang/String;" },
			    "requestMappingConditions": { "consumes": [], "headers": [], "methods": [ "GET" ], "params": [], "patterns": [ "/greeting" ], "produces": [] }
			  }
			}
			""".formatted(predicate)));
	}

	private static WebConfigIndexElement webConfig(String... strategies) {
		WebConfigIndexElement.Builder builder = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG);
		for (String strategy : strategies) {
			builder.versionStrategy(strategy, null);
		}
		return builder.buildFor(null);
	}

	private static SpringProcessLiveData liveDataWithProperty(String property, String value) {
		LiveProperty liveProperty = LiveProperty.builder().source("applicationConfig").property(property).value(value).build();
		LiveProperties properties = new LiveProperties(List.of(new LivePropertySource("applicationConfig", List.of(liveProperty))));
		return new SpringProcessLiveDataBuilder().getLiveProperties(properties).build();
	}

}
