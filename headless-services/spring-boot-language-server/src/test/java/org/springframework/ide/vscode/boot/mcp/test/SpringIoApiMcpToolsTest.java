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
package org.springframework.ide.vscode.boot.mcp.test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.ide.vscode.boot.mcp.SpringIoApiMcpTools;
import org.springframework.ide.vscode.boot.mcp.SpringIoApiMcpTools.Generation;
import org.springframework.ide.vscode.boot.mcp.SpringIoApiMcpTools.Release;
import org.springframework.ide.vscode.boot.mcp.SpringIoApiMcpTools.UpcomingRelease;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * HTTP behavior of {@link SpringIoApiMcpTools} using {@link MockRestServiceServer}.
 */
class SpringIoApiMcpToolsTest {

	private static final MediaType HAL_JSON = MediaType.parseMediaType("application/hal+json");

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void getReleases_returnsEmbeddedArray() {
		RestClient.Builder apiBuilder = RestClient.builder();
		MockRestServiceServer apiMock = MockRestServiceServer.bindTo(apiBuilder).build();
		RestClient.Builder calBuilder = RestClient.builder();
		MockRestServiceServer calMock = MockRestServiceServer.bindTo(calBuilder).build();

		String hal = """
				{"_embedded":{"releases":[{"version":"3.4.0","status":"GENERAL_AVAILABILITY","current":true}]}}
				""";
		apiMock.expect(requestTo("http://localhost/projects/spring-boot/releases"))
				.andRespond(withSuccess(hal, HAL_JSON));

		RestClient api = apiBuilder.baseUrl("http://localhost").build();
		RestClient cal = calBuilder.baseUrl("http://calendar").build();
		SpringIoApiMcpTools tools = SpringIoApiMcpTools.forTesting(api, cal, 30, FIXED_CLOCK);

		Release[] releases = tools.getReleases("spring-boot");
		assertEquals(1, releases.length);
		assertEquals("3.4.0", releases[0].version());
		assertTrue(releases[0].current());

		apiMock.verify();
		calMock.verify();
	}

	@Test
	void getGenerations_returnsEmbeddedArray() {
		RestClient.Builder apiBuilder = RestClient.builder();
		MockRestServiceServer apiMock = MockRestServiceServer.bindTo(apiBuilder).build();
		RestClient.Builder calBuilder = RestClient.builder();
		MockRestServiceServer calMock = MockRestServiceServer.bindTo(calBuilder).build();

		String hal = """
				{"_embedded":{"generations":[{"name":"3.4.x","initialReleaseDate":"2024-11-01","ossSupportEndDate":"2025-12-31","commercialSupportEndDate":"2026-12-31"}]}}
				""";
		apiMock.expect(requestTo("http://localhost/projects/spring-boot/generations"))
				.andRespond(withSuccess(hal, HAL_JSON));

		RestClient api = apiBuilder.baseUrl("http://localhost").build();
		RestClient cal = calBuilder.baseUrl("http://calendar").build();
		SpringIoApiMcpTools tools = SpringIoApiMcpTools.forTesting(api, cal, 7, FIXED_CLOCK);

		Generation[] generations = tools.getGenerations("spring-boot");
		assertEquals(1, generations.length);
		assertEquals("3.4.x", generations[0].name());

		apiMock.verify();
		calMock.verify();
	}

	@Test
	void getUpcomingReleases_queriesCalendarWithDateWindow() {
		RestClient.Builder apiBuilder = RestClient.builder();
		MockRestServiceServer apiMock = MockRestServiceServer.bindTo(apiBuilder).build();
		RestClient.Builder calBuilder = RestClient.builder();
		MockRestServiceServer calMock = MockRestServiceServer.bindTo(calBuilder).build();

		String calJson = """
				[{"allDay":false,"backgroundColor":"#abc","start":"2025-07-01","title":"Spring Boot 3.5","url":"https://calendar.spring.io/x"}]
				""";
		calMock.expect(requestTo(allOf(
				containsString("http://calendar/releases?"),
				containsString("start=2025-06-15"),
				containsString("end=2025-07-15"))))
				.andRespond(withSuccess(calJson, MediaType.APPLICATION_JSON));

		RestClient api = apiBuilder.baseUrl("http://localhost").build();
		RestClient cal = calBuilder.baseUrl("http://calendar").build();
		SpringIoApiMcpTools tools = SpringIoApiMcpTools.forTesting(api, cal, 30, FIXED_CLOCK);

		List<UpcomingRelease> upcoming = tools.getUpcomingReleases();
		assertEquals(1, upcoming.size());
		assertEquals("Spring Boot 3.5", upcoming.get(0).title());
		assertEquals(LocalDate.of(2025, 7, 1), upcoming.get(0).start());
		assertFalse(upcoming.get(0).allDay());

		apiMock.verify();
		calMock.verify();
	}
}
