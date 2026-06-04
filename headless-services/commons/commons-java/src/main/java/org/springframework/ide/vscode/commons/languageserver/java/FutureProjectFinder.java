/*******************************************************************************
 * Copyright (c) 2021, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.java;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver.Listener;

public class FutureProjectFinder implements DisposableBean {
	
	private JavaProjectFinder projectFinder;
	private ProjectObserver projectObserver;
	
	final private Object LOCK = new Object();
	private WeakHashMap<URI, CompletableFuture<IJavaProject>> pendingFindProjectRequests = new WeakHashMap<>();
	
	private final Listener LISTENER = new Listener() {
		
		@Override
		public void deleted(IJavaProject project) {
		}
		
		@Override
		public void created(IJavaProject project) {
			resolvePendingRequests(project);
		}
		
		@Override
		public void changed(IJavaProject project, boolean clean) {
		}

		@Override
		public void supported() {
			if (!projectObserver.isSupported()) {
				resolveAllPendingRquests();
			}
		}
		
	};
	
	
	public FutureProjectFinder(JavaProjectFinder projectFinder, Optional<ProjectObserver> projectObserver) {
		this.projectFinder = projectFinder;
		this.projectObserver = projectObserver.orElse(null);
		if (this.projectObserver != null) {
			this.projectObserver.addListener(LISTENER);
		}
	}
	
	private void resolveAllPendingRquests() {
		synchronized(LOCK) {
			for (Map.Entry<URI, CompletableFuture<IJavaProject>> e : pendingFindProjectRequests.entrySet()) {
				Optional<IJavaProject> jp = projectFinder.find(new TextDocumentIdentifier(e.getKey().toASCIIString()));
				e.getValue().complete(jp.orElse(null));
				pendingFindProjectRequests.remove(e.getKey());
			}
		}
	}

	private void resolvePendingRequests(IJavaProject project) {
		synchronized(LOCK) {
			for (Map.Entry<URI, CompletableFuture<IJavaProject>> e : pendingFindProjectRequests.entrySet()) {
				Optional<IJavaProject> jp = projectFinder.find(new TextDocumentIdentifier(e.getKey().toASCIIString()));
				if (jp.isPresent()) {
					e.getValue().complete(jp.get());
					pendingFindProjectRequests.remove(e.getKey());
				}
			}
		}
	}

	@Override
	public void destroy() throws Exception {
		if (projectObserver != null) {
			projectObserver.removeListener(LISTENER);
		}
	}
	
	public CompletableFuture<IJavaProject> findFuture(URI uri) {		
		TextDocumentIdentifier id = new TextDocumentIdentifier(uri.toASCIIString());
		Optional<IJavaProject> jp = projectFinder.find(id);
		if (jp.isPresent()) {
			return CompletableFuture.completedFuture(jp.get());
		} else {
			if (projectObserver == null) {
				throw new IllegalStateException("Future project lookup not supported without ProjectObserver bean present");
			}
			if (projectObserver.isSupported()) {
				synchronized(LOCK) {
					CompletableFuture<IJavaProject> cf = pendingFindProjectRequests.get(uri);
					if (cf == null) {
						cf = new CompletableFuture<IJavaProject>();
						pendingFindProjectRequests.put(uri, cf);
					}
					return cf;
				}
			} else {
				return CompletableFuture.completedFuture(null);
			}
		}
	}


}
