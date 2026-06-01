/*******************************************************************************
 * Copyright (c) 2017, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Basic implementation of File Observer interface
 * 
 * @author Alex Boyko
 *
 */
public class BasicFileObserver implements FileObserver, FileChangeNotifier {
	
	protected ConcurrentHashMap<String, ImmutablePair<List<PathMatcher>, Consumer<String[]>>> createRegistry = new ConcurrentHashMap<>(); 
	protected ConcurrentHashMap<String, ImmutablePair<List<PathMatcher>, Consumer<String[]>>> deleteRegistry = new ConcurrentHashMap<>(); 
	protected ConcurrentHashMap<String, ImmutablePair<List<PathMatcher>, Consumer<String[]>>> changeRegistry = new ConcurrentHashMap<>(); 
	
	@Override
	public String onFilesCreated(List<String> globPattern, Consumer<String[]> handler) {
		return registerFileListener(createRegistry, globPattern, handler);
	}

	@Override
	public String onFilesChanged(List<String> globPattern, Consumer<String[]> handler) {
		return registerFileListener(changeRegistry, globPattern, handler);
	}

	@Override
	public String onFilesDeleted(List<String> globPattern, Consumer<String[]> handler) {
		return registerFileListener(deleteRegistry, globPattern, handler);
	}

	@Override
	public boolean unsubscribe(String subscriptionId) {
		if (createRegistry.remove(subscriptionId) != null) {
			return true;
		}
		if (changeRegistry.remove(subscriptionId) != null) {
			return true;
		}
		if (deleteRegistry.remove(subscriptionId) != null) {
			return true;
		}
		return false;
	}
	
	private static String registerFileListener(Map<String, ImmutablePair<List<PathMatcher>, Consumer<String[]>>> registry, List<String> globPattern, Consumer<String[]> handler) {
		String subscriptionId = UUID.randomUUID().toString();
		registry.put(subscriptionId, ImmutablePair.of(globPattern.stream().map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g)).collect(Collectors.toList()), handler));
		return subscriptionId;
	}
	
	final public void notifyFileCreated(String uri) {
		notify(createRegistry, new String[] {uri});
	}
	
	final public void notifyFilesCreated(String[] uris) {
		notify(createRegistry, uris);
	}
	
	final public void notifyFileChanged(String uri) {
		notify(changeRegistry, new String[] {uri});
	}
	
	final public void notifyFilesChanged(String[] uris) {
		notify(changeRegistry, uris);
	}
	
	final public void notifyFileDeleted(String uri) {
		notify(deleteRegistry, new String[] {uri});
	}
	
	final public void notifyFilesDeleted(String[] uris) {
		notify(deleteRegistry, uris);
	}
	
	private static void notify(Map<String, ImmutablePair<List<PathMatcher>, Consumer<String[]>>> registry, String[] uris) {
//		Path path = Paths.get(URI.create(uris));
//		registry.values().stream()
//			.filter(pair -> pair.left.stream()
//					.filter(matcher -> 
//						matcher.matches(path)
//					)
//					.findFirst()
//					.isPresent())
//			.forEach(pair -> pair.right.accept(uris));
//		
		registry.values().stream()
		
			// create for each consumer with multiple pattern patchers a pair that contains the pair + an array of matching doc URIs
			.map(pair -> ImmutablePair.of(
					pair,
					
					// this creates an array of those doc URIs that match at least to one of those pattern matchers
					Arrays.stream(uris)
						.filter(uri ->
						
								// keep only URIs for which a matcher succeeds
								pair.left.stream()
									.filter(matcher -> matcher.matches(paths_get(uri)))
										.findFirst()
										.isPresent())
						.toArray(String[]::new)))
			
			.filter(superPair -> superPair.right.length > 0)
			
			// then call the accept method of each consumer with the generated array of doc URIs
			.forEach(superPair -> superPair.left.right.accept(superPair.right));
	}

	// Added in an attempt to learn what causes: https://www.pivotaltracker.com/story/show/175715622
	private static Path paths_get(String uri) {
		try {
			return new File(new URI(uri)).toPath();
		} catch (IllegalArgumentException | URISyntaxException e) {
			//make the error more specific (reveal the uri string that causes it)
			throw new IllegalArgumentException("uri = '"+uri+"'", e);
		}
	}
}
