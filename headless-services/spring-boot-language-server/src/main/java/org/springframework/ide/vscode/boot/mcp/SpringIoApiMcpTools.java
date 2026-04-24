/*
 * Copyright 2025, 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ide.vscode.boot.mcp;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * MCP tools that query live data from the Spring IO API and the Spring release calendar,
 * ported from <a href="https://github.com/martinlippert/spring-io-api-mcp">spring-io-api-mcp</a>
 * (same behavior as {@code SpringIoApi} in that project).
 * <p>
 * API origin follows {@link BootJavaConfig#getSpringIOApiUrl()} via {@link SpringIoMcpUrls}.
 * Calendar base URL follows {@link BootJavaConfig#getSpringCalendarApiUrl()} (workspace:
 * {@code boot-java.io.calendar-api}). Both are refreshed when workspace configuration changes.
 *
 * @author Martin Lippert
 */
@Component
public class SpringIoApiMcpTools {

	private static final MediaType HAL_JSON = MediaType.parseMediaType("application/hal+json");

	private static final Logger logger = LoggerFactory.getLogger(SpringIoApiMcpTools.class);

	private final BootJavaConfig bootJavaConfig;
	private final long daysFromToday;
	private final Clock clock;

	private volatile RestClient apiClient;
	private volatile RestClient calClient;

	@Autowired
	public SpringIoApiMcpTools(BootJavaConfig bootJavaConfig,
			@Value("${calendar.window.days:180}") long daysFromToday,
			ObjectProvider<Clock> clockProvider) {
		this.bootJavaConfig = Objects.requireNonNull(bootJavaConfig);
		this.daysFromToday = daysFromToday;
		this.clock = clockProvider.getIfAvailable(() -> Clock.systemDefaultZone());
		rebuildClients();
		bootJavaConfig.addListener(v -> rebuildClients());
	}

	/**
	 * Builds a fixed instance for tests (no {@link BootJavaConfig}, no network).
	 */
	public static SpringIoApiMcpTools forTesting(RestClient apiClient, RestClient calClient, long daysFromToday, Clock clock) {
		return new SpringIoApiMcpTools(apiClient, calClient, daysFromToday, clock);
	}

	private SpringIoApiMcpTools(RestClient apiClient, RestClient calClient, long daysFromToday, Clock clock) {
		this.bootJavaConfig = null;
		this.apiClient = Objects.requireNonNull(apiClient);
		this.calClient = Objects.requireNonNull(calClient);
		this.daysFromToday = daysFromToday;
		this.clock = Objects.requireNonNull(clock);
	}

	private synchronized void rebuildClients() {
		if (bootJavaConfig == null) {
			return;
		}
		String apiBase = SpringIoMcpUrls.deriveApiBaseFromProjectsListUrl(bootJavaConfig.getSpringIOApiUrl());
		String calendarBase = bootJavaConfig.getSpringCalendarApiUrl().trim();
		while (calendarBase.endsWith("/")) {
			calendarBase = calendarBase.substring(0, calendarBase.length() - 1);
		}
		logger.debug("Spring IO MCP RestClient bases: api={}, calendar={}", apiBase, calendarBase);
		this.apiClient = RestClient.builder().baseUrl(apiBase).build();
		this.calClient = RestClient.builder().baseUrl(calendarBase).build();
	}

	public record ReleasesRoot(ReleasesEmbedded _embedded) {
	}

	public record ReleasesEmbedded(Release[] releases) {
	}

	public record Release(String version, String status, boolean current) {
	}

	public record GenerationsRoot(GenerationsEmbedded _embedded) {
	}

	public record GenerationsEmbedded(Generation[] generations) {
	}

	public record Generation(String name, String initialReleaseDate, String ossSupportEndDate, String commercialSupportEndDate) {
	}

	public record UpcomingRelease(boolean allDay, String backgroundColor, LocalDate start, String title, String url) {
	}

	@Tool(description = """
			Get the full list of releases for a Spring project from the live Spring IO API (HAL).
			For only the current GA release plus OSS/commercial support end dates for that line, prefer getLatestReleaseInformation instead.
			""")
	public Release[] getReleases(
			@ToolParam(description = """
					Spring IO API project slug (path segment under /projects), e.g. "spring-boot", "spring-framework", "spring-data-jpa".
					This is not the IDE workspace project name from getProjectList; it is the Spring portfolio project id from Spring IO.
					Base URL is configured as workspace setting boot-java.io.api (projects list URL; API base is derived from it).
					""")
			String project) {
		logger.info("get Spring project releases for: {}", project);
		ReleasesRoot release = apiClient.get()
				.uri(uriBuilder -> uriBuilder.path("/projects/" + project + "/releases").build())
				.accept(HAL_JSON)
				.retrieve()
				.body(ReleasesRoot.class);

		return Objects.requireNonNull(release)._embedded.releases;
	}

	@Tool(description = """
			Get support generation windows (OSS and commercial end dates) for a Spring project from the live Spring IO API.
			For the current GA line's support dates tied to the latest release only, prefer getLatestReleaseInformation instead.
			""")
	public Generation[] getGenerations(
			@ToolParam(description = """
					Spring IO API project slug, e.g. "spring-boot", "spring-framework". Same semantics as getReleases project parameter.
					""")
			String project) {
		logger.info("get Spring project support dates for: {}", project);
		GenerationsRoot generations = apiClient.get()
				.uri(uriBuilder -> uriBuilder.path("/projects/" + project + "/generations").build())
				.accept(HAL_JSON)
				.retrieve()
				.body(GenerationsRoot.class);

		return Objects.requireNonNull(generations)._embedded.generations;
	}

	@Tool(description = """
			Get upcoming Spring ecosystem releases from the Spring release calendar API (date range from today for calendar.window.days).
			Neither getReleases nor getLatestReleaseInformation provide this calendar view; use this for forward-looking dates.
			Calendar base URL is workspace setting boot-java.io.calendar-api.
			""")
	public List<UpcomingRelease> getUpcomingReleases() {
		LocalDate start = LocalDate.now(clock);
		LocalDate end = start.plusDays(this.daysFromToday);
		logger.info("Get information about upcoming releases for Spring projects in the next {} days", this.daysFromToday);

		return Objects.requireNonNull(calClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/releases")
						.queryParam("start", start)
						.queryParam("end", end)
						.build())
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(new ParameterizedTypeReference<List<UpcomingRelease>>() {
				}));
	}
}
