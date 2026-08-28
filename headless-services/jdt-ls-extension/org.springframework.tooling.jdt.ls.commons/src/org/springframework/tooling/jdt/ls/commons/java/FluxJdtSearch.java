/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.java;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.springframework.tooling.jdt.ls.commons.Logger;

import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.util.concurrent.Queues;

/**
 * Helper class to perform a search using Eclipse JDT search engine returning
 * the search results as a Flux.
 * <p>
 * The conversion from Eclipse callback style using {@link SearchRequestor} involves
 * a buffer that allows subscribers to attach to the Flux after the search has already
 * started without loosing results. However, if the buffer overflows then results will
 * be lost.
 * <p>
 * Clients should therfore start consuming the results as soon as possible and avoid
 * blocking the pipeline to avoid the loss of results they may care about.
 * <p>
 * Alternatively, client can specify a large enough buffer size so that the buffer can hold
 * at least as many results as the client may care to retrieve. This will allow the returned
 * Flux to be reused any number of times without timing constraints, provided that the
 * consumer never requests more than the number of buffered results.
 *
 * @author Kris De Volder
 */
public class FluxJdtSearch {

	private SearchEngine engine = new SearchEngine();
	private IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
	private SearchPattern pattern = null;
	private SearchParticipant[] participants = new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()};
	private int bufferSize = Queues.SMALL_BUFFER_SIZE;
	private boolean useSystemJob = false;
	private int jobPriority = Job.INTERACTIVE;
	private Logger logger;

	public FluxJdtSearch(Logger logger) {
		this.logger = logger;
	}

	public FluxJdtSearch engine(SearchEngine engine) {
		this.engine = engine;
		return this;
	}

	public FluxJdtSearch scope(IJavaSearchScope scope) {
		this.scope = scope;
		return this;
	}

	public FluxJdtSearch bufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
		return this;
	}

	public FluxJdtSearch pattern(SearchPattern pattern) {
		this.pattern = pattern;
		return this;
	}

	/**
	 * Create a search scope that includes a given project and its dependencies.
	 */
	public static IJavaSearchScope searchScope(IJavaProject javaProject, boolean includeBinaries, boolean includeSystemLibs) throws JavaModelException {
		int includeMask =
				IJavaSearchScope.REFERENCED_PROJECTS |
				IJavaSearchScope.SOURCES;
		if (includeBinaries) {
			includeMask = includeMask | IJavaSearchScope.APPLICATION_LIBRARIES;
		}
		if (includeSystemLibs) {
			includeMask = includeMask | IJavaSearchScope.SYSTEM_LIBRARIES; 
		}
		return SearchEngine.createJavaSearchScope(new IJavaElement[] {javaProject}, includeMask);
	}

	/**
	 * Implementation of {@link SearchRequestor} that emits search results to an {@link ReplayProcessor}
	 * with replay capability.
	 *
	 * @author Kris De Volder
	 */
	class FluxSearchRequestor extends SearchRequestor {

		private boolean isCanceled = false;
		private ReplayProcessor<SearchMatch> emitter = ReplayProcessor.<SearchMatch>create(bufferSize);
		private Flux<SearchMatch> flux = emitter.doOnCancel(() -> isCanceled=true);

		public Flux<SearchMatch> asFlux() {
			return flux;
		}

		@Override
		public void acceptSearchMatch(SearchMatch match) throws CoreException {
			if (isCanceled) {
				//Stop searching
				throw new OperationCanceledException();
			}
			emitter.onNext(match);
		}

		public void cancel() {
			isCanceled = true;
		}

		public void done() {
			emitter.onComplete();
		}
	}

	protected SearchEngine searchEngine() {
		return new SearchEngine();
	}

	protected SearchParticipant[] participants() {
		return new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()};
	}

	public Flux<SearchMatch> search() {
		validate();
		if (scope == null) {
			return Flux.empty();
		}
		final FluxSearchRequestor requestor = new FluxSearchRequestor();
		Job job = new Job("Search for "+pattern) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					searchEngine().search(pattern, participants, scope, requestor, monitor);
					requestor.done();
				} catch (Exception e) {
					requestor.cancel();
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(useSystemJob);
		job.setPriority(jobPriority);
		job.schedule();
		return requestor.asFlux();
	}

	private void validate() {
		Assert.isNotNull(engine, "engine");
		//We allow scope to be set to null. This means there's no valid scope (or empty scope)
		// and the search should just return no results.
		//		Assert.isNotNull(scope, "scope");
		Assert.isNotNull(pattern, "pattern");
		Assert.isNotNull(participants, "participants");
		Assert.isLegal(bufferSize > 0);
	}

	public IJavaSearchScope workspaceScope(boolean includeBinaries) {
		if (includeBinaries) {
			return SearchEngine.createWorkspaceScope();
		} else {
			List<IJavaProject> projects = new ArrayList<>();
			for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				try {
					if (p.isAccessible() && p.hasNature(JavaCore.NATURE_ID)) {
						IJavaProject jp = JavaCore.create(p);
						projects.add(jp);
					}
				} catch (Exception e) {
					logger.log(e);
				}
			}
			int includeMask = IJavaSearchScope.SOURCES;
			return SearchEngine.createJavaSearchScope(projects.toArray(new IJavaElement[projects.size()]), includeMask);
		}
	}

}
