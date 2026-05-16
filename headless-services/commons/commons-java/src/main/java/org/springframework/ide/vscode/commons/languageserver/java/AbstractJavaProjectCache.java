/*******************************************************************************
 * Copyright (c) 2017, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.java;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.Sts4LanguageServer;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.commons.util.ListenerList;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Abstract implementation of java project cache indexed by keys
 *
 * @author Alex Boyko
 *
 * @param <K> key class
 * @param <P> project class
 */
public abstract class AbstractJavaProjectCache<K, P extends IJavaProject> implements JavaProjectCache<K, P>, ProjectChangeNotifier {

	private static final Logger log = LoggerFactory.getLogger(AbstractJavaProjectCache.class);

	protected Sts4LanguageServer server;

	private ListenerList<Listener> listeners = new ListenerList<>();

	protected Cache<K, P> cache = CacheBuilder.newBuilder().build();

	public AbstractJavaProjectCache(Sts4LanguageServer server) {
		this.server = server;
	}

	@Override
	public P project(K key) {
		if (key != null) {
			try {
				AtomicReference<P> createdProject = new AtomicReference<P>(null);
				try {
					return cache.get(key, () -> {
						try {
							P project = createProject(key);
							createdProject.set(project);
							attachListeners(key, project);
							return project;
						} catch (Throwable t) {
							throw new ExecutionException(t);
						}
					});
				} finally {
					if (createdProject.get()!=null) {
						notifyProjectCreated(createdProject.get());
					}
				}
			} catch (ExecutionException e) {
				log.error("", e);
				return null;
			}
		}
		return null;
	}

	public Optional<IJavaProject> projectByName(String name) {
		ConcurrentMap<K, P> map = cache.asMap();

		for (P project : map.values()) {
			if (project != null && project.getElementName().equals(name)) {
				return Optional.of(project);
			}
		}

		return Optional.empty();
	}

	abstract protected P createProject(K key) throws Exception;

	protected void attachListeners(K key, P project) {

	}

	@Override
	public void addListener(Listener listener) {
		log.debug("Add listener {} to {}", listener, this);
		listeners.add(listener);
	}

	@Override
	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	final protected void notifyProjectCreated(P project) {
		log.debug("project created {}", project);
		listeners.forEach(l -> l.created(project));
	}

	final protected void notifyProjectChanged(P project) {
		log.debug("project changed {}", project);
		listeners.forEach(l -> l.changed(project));
	}

	final protected void notifyProjectDeleted(P project) {
		log.debug("project deleted {}", project);
		listeners.forEach(l -> l.deleted(project));
	}

	@Override
	public void notifyProjectsChanged() {
		for (P project : cache.asMap().values()) {
			notifyProjectChanged(project);
		}
	}

	final protected FileObserver getFileObserver() {
		return server.getWorkspaceService().getFileObserver();
	}

	final public Collection<? extends IJavaProject> all() {
		return cache.asMap().values();
	}
}
