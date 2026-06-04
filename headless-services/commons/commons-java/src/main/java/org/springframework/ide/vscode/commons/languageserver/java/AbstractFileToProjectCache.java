/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.java;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.IndefiniteProgressTask;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.Sts4LanguageServer;

/**
 * Cache for java projects. The key for the cache is a "project" specific file
 * 
 * @author Alex Boyko
 *
 * @param <P> java project sub-class
 */
public abstract class AbstractFileToProjectCache<P extends IJavaProject> extends AbstractJavaProjectCache<File, P> {
	
	private List<String> subscriptions;	
	protected boolean asyncUpdate;
	protected final Path projectCacheFolder;
	private boolean alwaysFireEventOnUpdate;

	private static AtomicInteger progressIdCt = new AtomicInteger(0);

	public AbstractFileToProjectCache(Sts4LanguageServer server, boolean asyncUpdate, Path projectCacheFolder) {
		super(server);
		this.projectCacheFolder = projectCacheFolder;
		this.asyncUpdate = asyncUpdate;
		this.subscriptions = new ArrayList<>();
	}
	
	
	final public void setAlwaysFireEventOnFileChanged(boolean alwaysFireEventOnUpdate) {
		this.alwaysFireEventOnUpdate = alwaysFireEventOnUpdate;
	}

	@Override
	protected void attachListeners(File file, P project) {
		super.attachListeners(file, project);
		List<String> globPattern = Arrays.asList(file.toString().replace(File.separator, "/"));
		subscriptions.add(getFileObserver().onFilesChanged(globPattern, (uris) -> performUpdate(project, asyncUpdate, true)));
		subscriptions.add(getFileObserver().onFilesDeleted(globPattern, (uris) -> {
			cache.invalidate(file);
			notifyProjectDeleted(project);
			dispose();
		}));
	}
	
	private void dispose() {
		subscriptions.forEach(s -> getFileObserver().unsubscribe(s));
		subscriptions.clear();
	}
	
	final protected void performUpdate(P project, boolean async, boolean notify) {
		final ProgressService progressService = server.getProgressService();
		final IndefiniteProgressTask progress = progressService == null ? null
				: progressService.createIndefiniteProgressTask(getProgressId(),
						"Updating data for project '" + project.getElementName() + "'", null);
		if (async) {
			CompletableFuture.supplyAsync(() -> update(project)).thenAccept((changed) -> afterUpdate(project, changed, notify, progress));
		} else {
			boolean changed = update(project);
			afterUpdate(project, changed, notify, progress);
		}
	}
	
	private void afterUpdate(P project, boolean changed, boolean notify, IndefiniteProgressTask progress) {
		if (progress != null) {
			progress.done();
		}
		if (changed || alwaysFireEventOnUpdate) {
			if (notify) {
				notifyProjectChanged(project, false);
			}
		}
	}
	
	private static String getProgressId() {
		return AbstractFileToProjectCache.class.getName()+ (progressIdCt.incrementAndGet());
	}

	
	abstract protected boolean update(P project);
	
}
