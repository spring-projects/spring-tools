/*******************************************************************************
 * Copyright (c) 2014, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.metadata;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndex.Builder;
import org.springframework.ide.vscode.boot.metadata.util.Listener;
import org.springframework.ide.vscode.boot.metadata.util.ListenerManager;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.IndefiniteProgressTask;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.util.FileObserver;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;

/**
 * Support for Reconciling, Content Assist and Hover Text in spring properties
 * file all make use of a per-project index of spring properties metadata extracted
 * from project's classpath. This Index manager is responsible for keeping at most
 * one index per-project and to keep the index up-to-date.
 *
 * @author Kris De Volder
 */
public class SpringPropertiesIndexManager extends ListenerManager<Listener<Collection<IJavaProject>>> {
		
	private static final Logger log = LoggerFactory.getLogger(SpringPropertiesIndexManager.class);

	private Cache<IJavaProject, SpringPropertyIndex> indexes;
	private final ValueProviderRegistry valueProviders;
	private static int progressIdCt = 0;
	
	private Path commonPropertiesFile;
	private Supplier<ConfigurationMetadataRepository> commonPropertiesMetadata;

	public SpringPropertiesIndexManager(ValueProviderRegistry valueProviders, ProjectObserver projectObserver, FileObserver fileObserver) {
		this.valueProviders = valueProviders;
		this.indexes = CacheBuilder.newBuilder()
				.build();
		if (projectObserver != null) {
			projectObserver.addListener(ProjectObserver.onAny(project -> indexes.invalidate(project)));
		}
		if (fileObserver != null) {
			fileObserver.onAnyChange(ImmutableList.of("**/*spring-configuration-metadata.json"), this::metadataFilesChanged);
		}
	}

	public synchronized SpringPropertyIndex get(IJavaProject project, ProgressService progressService) {
		try {
			return indexes.get(project, () -> initIndex(project, progressService));
		} catch (ExecutionException e) {
			log.error("", e);
			return null;
		}
	}

	private SpringPropertyIndex initIndex(IJavaProject project, ProgressService progressService) {
		log.info("Indexing Spring Boot Properties for {}", project.getElementName());

		IndefiniteProgressTask progress = progressService == null ? null
				: progressService.createIndefiniteProgressTask(getProgressId(), "Indexing Spring Boot Properties",
						null);
		try {
			Builder builder = SpringPropertyIndex.builder(valueProviders).withClasspath(project.getClasspath());
			if (commonPropertiesMetadata != null) {
				builder.withMetadata(commonPropertiesMetadata.get());
			}
			SpringPropertyIndex index = builder.build();

			log.info("Indexing Spring Boot Properties for {} DONE", project.getElementName());
			log.info("Indexed {} properties.", index.size());

			return index;
		} finally {
			if (progress != null) {
				progress.done();
			}
		}
	}

	/**
	 * Discards all indexes, unless all the changed files are ones the indexes were built from and
	 * still have the content they were built from. Annotation processors rewrite their metadata
	 * file on every compile, mostly with the same content, and rebuilding the indexes of all
	 * projects (and reconciling their config files again) for that would be wasted.
	 */
	synchronized void metadataFilesChanged(String[] changedUris) {
		if (changedUris.length > 0 && Arrays.stream(changedUris).allMatch(this::isUnchanged)) {
			log.debug("Spring metadata files rewritten without changes, keeping the properties indexes: {}", (Object) changedUris);
			return;
		}
		clear();
	}

	private boolean isUnchanged(String uri) {
		Path file;
		try {
			file = PropertiesLoader.normalize(Paths.get(URI.create(uri)));
		} catch (Exception e) {
			return false;
		}
		List<String> builtFrom = indexes.asMap().values().stream()
				.map(index -> index.getMetadataFileHashes().get(file))
				.filter(hash -> hash != null)
				.toList();
		if (builtFrom.isEmpty()) {
			return false; // no index was built from this file (e.g. it is new): could be relevant for any of them
		}
		String current = PropertiesLoader.contentHash(file);
		return current != null && builtFrom.stream().allMatch(current::equals);
	}

	/**
	 * Discards all indexes and notifies the listeners with the projects whose index had been built,
	 * since only those can have results depending on it.
	 */
	public synchronized void clear() {
		if (indexes != null) {
			List<IJavaProject> affected = new ArrayList<>(indexes.asMap().keySet());
			indexes.invalidateAll();
			if (!affected.isEmpty()) {
				for (Listener<Collection<IJavaProject>> l : getListeners()) {
					l.changed(affected);
				}
			}
		}
	}

	private static synchronized String getProgressId() {
		return DefaultSpringPropertyIndexProvider.class.getName()+ (progressIdCt++);
	}

	public synchronized void setCommonPropertiesFile(Path commonPropertiesFile) {
		if (!Objects.equal(commonPropertiesFile, this.commonPropertiesFile)) {
			this.commonPropertiesFile = commonPropertiesFile;
			commonPropertiesMetadata = commonPropertiesFile == null ? null : Suppliers.memoize(() -> new PropertiesLoader().loadJson(commonPropertiesFile));
			clear();
		}
	}

	public synchronized boolean reloadCommonProperties() {
		if (commonPropertiesFile != null) {
			commonPropertiesMetadata = Suppliers.memoize(() -> new PropertiesLoader().loadJson(commonPropertiesFile));
			clear();
			return true;
		}
		return false;
	}

}
