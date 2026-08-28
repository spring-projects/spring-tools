/*******************************************************************************
 * Copyright (c) 2017, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CFTarget;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CFTargetCache;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.ConnectionException;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.NoTargetsException;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;

import com.google.common.collect.ImmutableList;

public abstract class AbstractCFHintsProvider implements Callable<Collection<YValueHint>> {

	public static final String EMPTY_VALUE = "";
	public static final String PROBLEM_RESOLVING_FROM_TARGETS = "Unable to resolve hints from: ";

	protected final CFTargetCache targetCache;
	private Logger logger = LoggerFactory.getLogger(AbstractCFHintsProvider.class);

	public AbstractCFHintsProvider(CFTargetCache targetCache) {
		Assert.isNotNull(targetCache);
		this.targetCache = targetCache;
	}

	/**
	 * Used in error messages. For example "Failed to get ${type-name}s from Cloudfoundry".
	 * @return
	 */
	protected abstract String getTypeName();

	@Override
	public Collection<YValueHint> call() throws Exception {

		try {
			List<CFTarget> targets = targetCache.getOrCreate();

			// Do NOT wrap the results in another list. Allow null values to return
			// as the reconcile framework expects null if hints failed to be resolved
			return getHints(targets);
		} catch (Throwable e) {
			// Convert any error into something readable to the user as it may
			// appear in the content assist
			// UI. Do NOT wrap the original exception as the framework may look
			// for the deepest cause when
			// resolving the error message. Instead, log the full error, and
			// only throw a
			// new exception with a "nicer" message
			Throwable targetErrors = getErrorOfType(e, ImmutableList.of(NoTargetsException.class, ConnectionException.class));
			if (targetErrors != null) {
				// This error may appear in the UI (e.g. hover pop-up) so only show the error message , not the full error with stack trace
				throw new ValueParseException(ExceptionUtil.getMessageNoAppendedInformation(targetErrors));
			} else {
				// Log any other error
				//logger.log(Level.SEVERE, ExceptionUtil.getMessage(e), e);
				throw new ValueParseException(
						"Failed to get "+getTypeName()+"s from Cloud Foundry: "+ExceptionUtil.getMessage(e));
			}
		}
	}

	/**
	 *
	 * @param e
	 * @return an error that requires no additional information when showing its
	 *         message, or null if no such error is found
	 */
	protected Throwable getErrorOfType(Throwable e, List<Class<? extends Throwable>> toLookFor) {
		return ExceptionUtil.findThrowable(e,
				toLookFor);
	}

	/**
	 *
	 * @return non-null list of hints. Return empty if no hints available
	 */
	protected Collection<YValueHint> getHints(List<CFTarget> targets) throws Exception {
		if (targets == null || targets.isEmpty()) {
			//no targets... means we don't know anything. Indicate this by returning null...
			// this "don't know" value will suppress bogus warnings in the reconciler.
			return null;
		}
		List<YValueHint> hints = new ArrayList<>();
		boolean validTargetsPresent = false;
		Exception lastErrorEncountered = null;

		for (CFTarget cfTarget : targets) {
			try {
				// TODO: check if duplicate proposals can be the list of all hints. Duplicates don't seem to cause duplicate proposals. Verify this!
				getHints(cfTarget).stream().filter(hint -> !hints.contains(hint)).forEach(hint -> hints.add(hint));
				validTargetsPresent = true;
			} catch (Exception e) {
				// PT 156579665 - Log the Connection exceptions, as it means there are existing targets that have connection errors
				// and this information could be useful to the user,
				// but don't log the "NoTarget" errors, as they may be logged frequently and dont necessarily indicate an issue (e.g. cf CLI is not installed).
				Throwable connectionError = getErrorOfType(e, ImmutableList.of(ConnectionException.class));
				if (connectionError != null) {
					logger.error("{}", ExceptionUtil.getMessageNoAppendedInformation(connectionError));
				}
				lastErrorEncountered = e;
			}
		}

		if (validTargetsPresent) {
			return hints;
		} else if (lastErrorEncountered != null){
			throw lastErrorEncountered;
		} else {
			throw new ConnectionException(PROBLEM_RESOLVING_FROM_TARGETS + " " + targets.toString());
		}
	}

	abstract Collection<YValueHint> getHints(CFTarget target) throws Exception;

}
