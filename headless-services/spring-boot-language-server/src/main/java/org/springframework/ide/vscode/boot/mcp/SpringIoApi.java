/*
* Copyright 2025 - 2025 the original author or authors.
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

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * @author Martin Lippert
 */
@Component
public class SpringIoApi {

	private final RestClient apiClient;
	private final RestClient calClient;
	private final Long daysFromToday;

	private static final Logger logger = LoggerFactory.getLogger(SpringIoApi.class);

	public SpringIoApi(@Value("${calendar.window.days:180}") Long daysFromToday) {
		this.apiClient = RestClient.builder().baseUrl("https://api.spring.io").build();
		this.calClient = RestClient.builder().baseUrl("https://calendar.spring.io").build();
		this.daysFromToday = daysFromToday;
	}

	public record ReleasesRoot(ReleasesEmbedded _embedded) {}
	public record ReleasesEmbedded(Release[] releases) {}
	public record Release(String version, String status, boolean current) {}
	
	public record GenerationsRoot(GenerationsEmbedded _embedded) {}
	public record GenerationsEmbedded(Generation[] generations) {}
	public record Generation(String name, String initialReleaseDate, String ossSupportEndDate, String commercialSupportEndDate) {}

	public record UpcomingRelease(boolean allDay, String backgroundColor, LocalDate start, String title, String url) {}

	@Tool(description = "Get information about Spring project releases")
	public Release[] getReleases(String project) {
		logger.info("get Spring project releases for: " + project);
		ReleasesRoot release = apiClient.get()
			.uri(uriBuilder -> uriBuilder.path("/projects/" + project + "/releases").build())
			.accept(MediaType.valueOf("application/hal+json"))
			.retrieve()
			.body(ReleasesRoot.class);
		
		return Objects.requireNonNull(release)._embedded.releases;
	}

	@Tool(description = "Get information about support ranges and dates for Spring projects")
	public Generation[] getGenerations(String project) {
		logger.info("get Spring project support dates for: " + project);
		GenerationsRoot release = apiClient.get()
			.uri(uriBuilder -> uriBuilder.path("/projects/" + project + "/generations").build())
			.accept(MediaType.valueOf("application/hal+json"))
			.retrieve()
			.body(GenerationsRoot.class);
		
		return Objects.requireNonNull(release)._embedded.generations;
	}

	@Tool(description = "Get information about upcoming releases for Spring projects in the near future")
	public List<UpcomingRelease> getUpcomingReleases() {
		LocalDate start = LocalDate.now();
		LocalDate end = start.plusDays(this.daysFromToday);
		logger.info("Get information about upcoming releases for Spring projects in the next " + this.daysFromToday + " days");

		return calClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/releases")
						.queryParam("start", start)
						.queryParam("end", end)
						.build())
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
                });
	}
}
