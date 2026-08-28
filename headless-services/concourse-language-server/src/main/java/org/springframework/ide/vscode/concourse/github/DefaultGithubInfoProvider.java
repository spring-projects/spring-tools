/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.concourse.github;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class DefaultGithubInfoProvider implements GithubInfoProvider {
	
	private static final Logger log = LoggerFactory.getLogger(DefaultGithubInfoProvider.class);

	//TODO: we only try to connect to github once and cache the connection.
	//This means that, if creating the connection fails we won't try again.
	//It would be nice to cache connection only for a limited amount of time
	//especially when connecting fails. So that user may try to address the
	//issue and try again.

	private static class Result<T> {
		private Object result;

		public Result(Object valueOrTrowable) {
			this.result = valueOrTrowable;
		}

		@SuppressWarnings("unchecked")
		public T get() throws Exception {
			if (result instanceof Throwable) {
				throw ExceptionUtil.exception((Throwable) result);
			}
			return (T)result;
		}
	}

	private <T> Callable<Result<T>> loader(Callable<T> callable) {
		return () -> load(callable);
	}

	private static <T> Result<T> load(Callable<T> callable) {
		try {
			return new Result<>(callable.call());
		} catch (Throwable e) {
			return new Result<>(e);
		}
	}

	private GitHub github;
	private IOException connectionError;
	private Collection<String> owners;

	private Cache<String, Result<Collection<String>>> reposByOwner = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.MINUTES)
			.build();

	{
		try {
			checkConfiguration();
			github = GitHub.connect();
		} catch (IOException e) {
			connectionError = e;
		}
	}

	@Override
	public Collection<String> getOwners() throws Exception {
		if (connectionError != null) {
			throw connectionError;
		}
		if (github != null) {
			if (owners == null) {
				ImmutableSet.Builder<String> owners = ImmutableSet.builder();
				for (GHRepository repo : github.getMyself().listRepositories()) {
					owners.add(repo.getOwnerName());
				}
				this.owners = owners.build();
			}
			return owners;
		}
		return ImmutableList.of();
	}

	private void checkConfiguration() throws IOException {
		File configFile = new File(System.getProperty("user.home"));
		configFile = new File(configFile, ".github");
		if (!configFile.isFile()) {
			throw new IOException("`~/.github` doesn't exist: You will get better content assist for github " +
						"repos if you create a file at `~/.github` containing your github login and password:\n"+
						"\n" +
						"    login=...username...\n" +
						"    password=...password...\n"+
						"\n" +
						"Note: Github connection data is cached indefinitely, so the editor will need to be restarted for " +
						"this to take effect."
			);
		}
	}

	@Override
	public Collection<String> getReposForOwner(String ownerName) throws Exception {
		if (connectionError != null) {
			throw connectionError;
		}
		try {
			if (github != null) {
				return reposByOwner.get(ownerName, loader(() -> {
					GHPerson owner = getOwner(ownerName);
					if (owner != null) {
						ImmutableList.Builder<String> builder = ImmutableList.builder();
						for (GHRepository repo : owner.listRepositories()) {
							builder.add(repo.getName());
						}
						return builder.build();
					}
					return null;
				}))
				.get();
			}
		} catch (Exception e) {
			if (!isMissingOwnerException(e)) {
				log.error("", e);
			}
		}
		return ImmutableList.of();
	}

	private GHPerson getOwner(String ownerName) throws IOException {
		try {
			return github.getUser(ownerName);
		} catch (IOException e1) {
			if (isMissingOwnerException(e1)) {
				try {
					return github.getOrganization(ownerName);
				} catch (IOException e2) {
					if (isMissingOwnerException(e2)) {
						return null;
					}
					throw e2;
				}
			}
			throw e1;
		}
	}

	protected boolean isMissingOwnerException(Throwable e) {
		return e instanceof GHFileNotFoundException;
	}

}
