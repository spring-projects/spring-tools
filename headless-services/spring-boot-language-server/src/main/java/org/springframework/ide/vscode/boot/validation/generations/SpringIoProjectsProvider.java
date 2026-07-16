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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.ClientHttpRequestFactoryProvider;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.boot.validation.generations.json.SpringProject;
import org.springframework.ide.vscode.boot.validation.generations.json.SpringProjects;
import org.springframework.ide.vscode.commons.languageserver.IndefiniteProgressTask;
import org.springframework.ide.vscode.commons.languageserver.MessageService;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Provides Spring project definitions from a source like
 * "https://spring.io/api/projects"
 * <p/>
 * If a client is not provided, a default client will be used instead that will
 * point to "https://spring.io/api/projects"
 *
 */
public class SpringIoProjectsProvider implements SpringProjectsProvider {
	
	private SpringProjectsClient client;
	private Map<String, ResolvedSpringProject> cache;
	private ClientHttpRequestFactoryProvider requestFactoryProvider;
	final private ProgressService progressService;
	final private MessageService messageService;
	final private long errorStateCachingTime;

	private long lastErrorTime;
	private Optional<Throwable> errorState;

	public SpringIoProjectsProvider(BootJavaConfig config, ClientHttpRequestFactoryProvider requestFactoryProvider, ProgressService progressService, MessageService messageService, long errorStateCachingTime) {
		this.requestFactoryProvider = requestFactoryProvider;
		this.progressService = progressService;
		this.messageService = messageService;
		this.errorStateCachingTime = errorStateCachingTime;
		clearErrorState();
		updateIoApiUri(config.getSpringIOApiUrl());
		config.addListener(v -> updateIoApiUri(config.getSpringIOApiUrl()));
	}
	
	public synchronized void updateIoApiUri(String uri) {
		if (client == null || !uri.equals(client.getUrl())) {
			this.client = new SpringProjectsClient(uri, requestFactoryProvider);
			cache = null;
			clearErrorState();
		}
	}

	/**
	 * 
	 * @param Project slug. E.g. "spring-boot"
	 * @return
	 * @throws Exception
	 */
	@Override
	public synchronized ResolvedSpringProject getProject(String projectSlug) throws Exception {
		ResolvedSpringProject prj = cache().get(projectSlug);
		return prj;
	}

	private Map<String, ResolvedSpringProject> cache() throws Exception {
		if (cache == null) {
			if (lastErrorTime + errorStateCachingTime < System.currentTimeMillis()) {
				IndefiniteProgressTask progress = progressService.createIndefiniteProgressTask("fetching-from-spring-io", "Fetching Generations from Spring IO", null);
				try {
					SpringProjects springProjects = client.getSpringProjects();
					cache = asMap(springProjects);
					// Wipe out error state
					clearErrorState();
				} catch (Exception e) {
					messageService.error("Failed to fetch Generation from Spring IO: %s".formatted(e.getMessage()));
					setErrorState(e);
					throw e;
				} finally {
					progress.done();
				}
			} else {
				// The error state hasn't expired - throw a cached error state exception
				if (errorState.isPresent()) {
					throw new CachedErrorStateException(errorState.get());
				}
			}
		}
		return cache != null ? cache : ImmutableMap.of();
	}
	
	private void clearErrorState() {
		errorState = Optional.empty();
		lastErrorTime = 0;
	}
	
	private void setErrorState(Throwable t) {
		errorState = Optional.of(t);
		lastErrorTime = System.currentTimeMillis();
	}
	
	private Map<String, ResolvedSpringProject> asMap(SpringProjects springProjects) {
		Builder<String, ResolvedSpringProject> builder = ImmutableMap.builder();

		if (springProjects != null) {
			List<SpringProject> projects = springProjects.getProjects();
			if (projects != null) {
				for (SpringProject project : projects) {
					builder.put(project.getSlug(), new ResolvedSpringProject(project, client));
				}
			}
		}
		return builder.build();
	}

}
