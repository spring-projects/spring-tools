/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.async;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.core.search.BasicSearchEngine;
import org.eclipse.jdt.internal.core.search.IRestrictedAccessConstructorRequestor;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.util.concurrent.Queues;

/**
 * 
 * NOTE:
 * This is derived from:
 * org.springsource.ide.eclipse.commons.frameworks.core.async.FluxJdtSearch
 * <p>
 * TODO:
 * There should only one reusable Flux JDT Search. Refactor so that FluxJdtSearch and FluxConstructorSearch
 * share a common base
 * <p>
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
@SuppressWarnings("restriction")
public class FluxConstructorSearch {

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	private BasicSearchEngine engine = new BasicSearchEngine();
	private IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
	private String pattern = null;
	private int patternRule = SearchPattern.R_PATTERN_MATCH;
	private int bufferSize = Queues.SMALL_BUFFER_SIZE;
	private boolean useSystemJob = false;
	private int jobPriority = Job.INTERACTIVE;

	public FluxConstructorSearch engine(BasicSearchEngine engine) {
		this.engine = engine;
		return this;
	}

	public FluxConstructorSearch scope(IJavaSearchScope scope) {
		this.scope = scope;
		return this;
	}

	public FluxConstructorSearch bufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
		return this;
	}

	public FluxConstructorSearch patternRule(int patternRule) {
		this.patternRule = patternRule;
		return this;
	}
	
	public FluxConstructorSearch pattern(String pattern) {
		this.pattern = pattern;
		return this;
	}


	/**
	 * Implementation of {@link SearchRequestor} that emits search results to an {@link ReplayProcessor}
	 * with replay capability.
	 *
	 * @author Kris De Volder
	 */
	class FluxSearchRequestor  {

		private boolean isCanceled = false;
		private ReplayProcessor<JavaConstructorHint> emitter = ReplayProcessor.<JavaConstructorHint>create(bufferSize);
		private Flux<JavaConstructorHint> flux = emitter.doOnCancel(() -> isCanceled=true);

		public Flux<JavaConstructorHint> asFlux() {
			return flux;
		}

		public void acceptSearchMatch(JavaConstructorHint match) {
			if (isCanceled) {
				debug("!!!! canceling search !!!!");
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

	public Flux<JavaConstructorHint> search() {
		validate();
		if (scope == null) {
			return Flux.empty();
		}
		final FluxSearchRequestor requestor = new FluxSearchRequestor();
		Job job = new Job("Search for "+pattern) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				long start = System.currentTimeMillis();
				debug("Starting search for '"+pattern+"'");
				try {
					engine.searchAllConstructorDeclarations(null, pattern.toCharArray(), patternRule,
							scope, new IRestrictedAccessConstructorRequestor() {

								@Override
								public void acceptConstructor(int modifiers, char[] simpleTypeName, int parameterCount,
										char[] signature, char[][] parameterTypes, char[][] parameterNames, int typeModifiers,
										char[] packageName, int extraFlags, String path, AccessRestriction access) {
									requestor.acceptSearchMatch(JavaConstructorHint.asHint(modifiers, simpleTypeName, parameterCount, signature, parameterTypes, parameterNames, typeModifiers, packageName, extraFlags, path, access));
								}

							}, IJavaSearchConstants.FORCE_IMMEDIATE_SEARCH, monitor);					
					requestor.done();
				} catch (Exception e) {
					debug("Canceled search for: "+pattern);
					debug("          exception: "+ExceptionUtil.getMessage(e));
					long duration = System.currentTimeMillis() - start;
					debug("          duration: "+duration+" ms");
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
		Assert.isNotNull(pattern, "pattern");
		Assert.isLegal(bufferSize > 0);
	}
}
