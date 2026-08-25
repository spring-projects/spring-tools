/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.maven;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.cache.MavenPomCache;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.MavenMetadata;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.maven.tree.ResolvedGroupArtifactVersion;
import org.openrewrite.maven.tree.ResolvedPom;

/**
 * Wraps a {@link MavenPomCache} so concurrent misses on the same key wait for one
 * in-flight fetch instead of each fetching independently. Best-effort: since
 * {@code MavenPomDownloader} only calls {@code put*} on success, a claim whose fetch
 * throws is never completed - {@code timeout} bounds how long that can block others
 * before they fall back to fetching themselves.
 * <p>
 * {@code file://} repository lookups (the local {@code ~/.m2/repository} pseudo-repo,
 * always tried first) skip coalescing entirely: {@code MavenPomDownloader} never calls
 * {@code put*} for a local miss (it just moves to the next repository), so every such
 * claim would otherwise be abandoned - not occasionally, but on essentially every GAV
 * touched during resolution. A filesystem check is cheap enough that there's nothing to
 * de-duplicate anyway.
 */
public class ConcurrentMavenPomCache implements MavenPomCache {

	// Longer than a normal fetch, but shorter than MavenPomDownloader's own connect/read
	// timeouts (10s/30s), so a doomed fetch has usually already failed before this fires.
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

	private final MavenPomCache delegate;
	private final Duration timeout;

	private final ConcurrentHashMap<ResolvedGroupArtifactVersion, CompletableFuture<Optional<Pom>>> pomsInFlight = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<MetadataKey, CompletableFuture<Optional<MavenMetadata>>> metadataInFlight = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<MavenRepository, CompletableFuture<Optional<MavenRepository>>> repositoriesInFlight = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ResolvedGroupArtifactVersion, CompletableFuture<@Nullable ResolvedPom>> resolvedDependencyPomsInFlight = new ConcurrentHashMap<>();

	public ConcurrentMavenPomCache(MavenPomCache delegate) {
		this(delegate, DEFAULT_TIMEOUT);
	}

	public ConcurrentMavenPomCache(MavenPomCache delegate, Duration timeout) {
		this.delegate = delegate;
		this.timeout = timeout;
	}

	private record MetadataKey(URI repository, GroupArtifactVersion gav) {
	}

	/** Lets {@link #getPom} propagate the checked {@link MavenDownloadingException} through {@link #getOrWait}. */
	private static class WrappedMavenDownloadingException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		WrappedMavenDownloadingException(MavenDownloadingException cause) {
			super(cause);
		}
	}

	/**
	 * @return the already-cached value if present, {@code null} if this call is the
	 * first to miss (the caller must now fetch and eventually call the matching
	 * {@code put*} method), or the result of an equivalent already-in-flight fetch once
	 * it completes (or {@code null} if it doesn't complete within {@code timeout}).
	 */
	private <K, V> @Nullable V getOrWait(ConcurrentHashMap<K, CompletableFuture<V>> inFlight, K key, Supplier<@Nullable V> cachedLookup) {
		V cached = cachedLookup.get();
		if (cached != null) {
			return cached;
		}
		CompletableFuture<V> mine = new CompletableFuture<>();
		CompletableFuture<V> existing = inFlight.putIfAbsent(key, mine);
		if (existing == null) {
			// Self-expire: if the fetch throws, put* is never called, so nothing would
			// otherwise complete or remove this entry, blocking every later caller forever.
			mine.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
					.whenComplete((v, ex) -> inFlight.remove(key, mine));
			return null;
		}
		try {
			return existing.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (TimeoutException | ExecutionException e) {
			return null;
		}
	}

	private <K, V> void complete(ConcurrentHashMap<K, CompletableFuture<V>> inFlight, K key, V value) {
		CompletableFuture<V> pending = inFlight.remove(key);
		if (pending != null) {
			pending.complete(value);
		}
	}

	private static boolean isFileScheme(@Nullable String uri) {
		if (uri == null) {
			return false;
		}
		try {
			return "file".equals(URI.create(uri).getScheme());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public @Nullable Optional<Pom> getPom(ResolvedGroupArtifactVersion gav) throws MavenDownloadingException {
		if (isFileScheme(gav.getRepository())) {
			return delegate.getPom(gav);
		}
		try {
			return getOrWait(pomsInFlight, gav, () -> {
				try {
					return delegate.getPom(gav);
				} catch (MavenDownloadingException e) {
					throw new WrappedMavenDownloadingException(e);
				}
			});
		} catch (WrappedMavenDownloadingException e) {
			throw (MavenDownloadingException) e.getCause();
		}
	}

	@Override
	public void putPom(ResolvedGroupArtifactVersion gav, @Nullable Pom pom) {
		delegate.putPom(gav, pom);
		if (!isFileScheme(gav.getRepository())) {
			complete(pomsInFlight, gav, Optional.ofNullable(pom));
		}
	}

	@Override
	public @Nullable Optional<MavenMetadata> getMavenMetadata(URI repo, GroupArtifactVersion gav) {
		if ("file".equals(repo.getScheme())) {
			return delegate.getMavenMetadata(repo, gav);
		}
		return getOrWait(metadataInFlight, new MetadataKey(repo, gav), () -> delegate.getMavenMetadata(repo, gav));
	}

	@Override
	public void putMavenMetadata(URI repo, GroupArtifactVersion gav, @Nullable MavenMetadata metadata) {
		delegate.putMavenMetadata(repo, gav, metadata);
		if (!"file".equals(repo.getScheme())) {
			complete(metadataInFlight, new MetadataKey(repo, gav), Optional.ofNullable(metadata));
		}
	}

	@Override
	public @Nullable Optional<MavenRepository> getNormalizedRepository(MavenRepository repository) {
		if (isFileScheme(repository.getUri())) {
			return delegate.getNormalizedRepository(repository);
		}
		return getOrWait(repositoriesInFlight, repository, () -> delegate.getNormalizedRepository(repository));
	}

	@Override
	public void putNormalizedRepository(MavenRepository repository, @Nullable MavenRepository normalized) {
		delegate.putNormalizedRepository(repository, normalized);
		if (!isFileScheme(repository.getUri())) {
			complete(repositoriesInFlight, repository, Optional.ofNullable(normalized));
		}
	}

	@Override
	public @Nullable ResolvedPom getResolvedDependencyPom(ResolvedGroupArtifactVersion dependency) {
		if (isFileScheme(dependency.getRepository())) {
			return delegate.getResolvedDependencyPom(dependency);
		}
		return getOrWait(resolvedDependencyPomsInFlight, dependency, () -> delegate.getResolvedDependencyPom(dependency));
	}

	@Override
	public void putResolvedDependencyPom(ResolvedGroupArtifactVersion dependency, ResolvedPom resolved) {
		delegate.putResolvedDependencyPom(dependency, resolved);
		if (!isFileScheme(dependency.getRepository())) {
			complete(resolvedDependencyPomsInFlight, dependency, resolved);
		}
	}

}
