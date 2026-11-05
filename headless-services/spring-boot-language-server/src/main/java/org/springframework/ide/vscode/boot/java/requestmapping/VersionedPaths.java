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
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.ide.vscode.boot.java.livehover.v2.LiveProperties;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveProperty;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveRequestMapping;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveData;

/**
 * The paths of a live request mapping, with the API version of the mapping put into the path when the
 * application uses path segment based API versioning (<code>usePathSegment(int)</code> or
 * <code>spring.mvc|webflux.apiversion.use.path-segment</code>), so that a mapping like
 * <code>/{version}/greeting</code> with <code>version = "1.1"</code> links to <code>/1.1/greeting</code>.
 * <p>
 * The index of the version path segment is taken from the properties of the running app and, if not
 * available there, from the given web configs of the project.
 *
 * @author Seonwoo Jung
 */
public class VersionedPaths {

	private static final String STRATEGY_PREFIX = "Path Segment: ";

	private static final List<String> PATH_SEGMENT_PROPERTIES = List.of(
			"spring.mvc.apiversion.use.path-segment",
			"spring.webflux.apiversion.use.path-segment");

	// a URI variable like {version} or {version:\d+}, but not a capturing pattern like {*path}
	private static final Pattern URI_VARIABLE = Pattern.compile("\\{[^*/{}][^/]*\\}");

	private final LiveRequestMapping mapping;
	private final SpringProcessLiveData liveData;
	private final Collection<WebConfigIndexElement> webConfigs;

	private Integer pathSegmentIndex;
	private boolean pathSegmentIndexComputed;

	/**
	 * @param webConfigs the web configs of the project (from java config and properties), empty if no project is at hand
	 */
	public VersionedPaths(LiveRequestMapping mapping, SpringProcessLiveData liveData, Collection<WebConfigIndexElement> webConfigs) {
		this.mapping = mapping;
		this.liveData = liveData;
		this.webConfigs = webConfigs == null ? List.of() : webConfigs;
	}

	/**
	 * @return the paths of the mapping as reported by the running app, <code>""</code> if the path is unconstrained
	 */
	public String[] getSplitPath() {
		String[] paths = mapping.getSplitPath();
		if (paths == null || paths.length == 0) {
			//Technically, this means the path 'predicate' is unconstrained, meaning any path matches.
			//So this is not quite the same as the case where path=""... but...
			//It is better for us to show one link where any path is allowed, versus showing no links where any link is allowed.
			//So we'll pretend this is the same as path="" as that gives a working link.
			return new String[] {""};
		}
		return paths;
	}

	/**
	 * @return the given path of the mapping with the API version put into the version path segment,
	 * or the unchanged path if the mapping is not versioned or the version path segment is unknown
	 */
	public String getVersionedPath(String path) {
		String version = mapping.getApiVersion();
		if (path == null || version == null) {
			return path;
		}
		Integer index = getPathSegmentIndex();
		if (index == null) {
			return path;
		}
		return insertVersion(path, version, index);
	}

	private Integer getPathSegmentIndex() {
		if (!pathSegmentIndexComputed) {
			Integer liveIndex = indexFromLiveData();
			pathSegmentIndex = liveIndex != null ? liveIndex : indexFromWebConfigs();
			pathSegmentIndexComputed = true;
		}
		return pathSegmentIndex;
	}

	/**
	 * @return the path segment index from the properties of the running app, <code>null</code> if not
	 * configured via properties or not readable (e.g. values sanitized by the env endpoint)
	 */
	private Integer indexFromLiveData() {
		LiveProperties liveProperties = liveData == null ? null : liveData.getLiveProperties();
		if (liveProperties == null) {
			return null;
		}
		Set<Integer> indices = new HashSet<>();
		for (String property : PATH_SEGMENT_PROPERTIES) {
			for (LiveProperty liveProperty : liveProperties.getProperties(property)) {
				Integer index = parseIndex(liveProperty.getValue());
				if (index != null) {
					indices.add(index);
					// the first property source wins, as in the environment of the app
					break;
				}
			}
		}
		return indices.size() == 1 ? indices.iterator().next() : null;
	}

	/**
	 * @return the path segment index configured in the web configs, <code>null</code> if none
	 * or if different indices are configured
	 */
	private Integer indexFromWebConfigs() {
		Set<Integer> indices = new HashSet<>();
		for (WebConfigIndexElement webConfig : webConfigs) {
			for (String strategy : webConfig.getVersionSupportStrategies()) {
				if (strategy != null && strategy.startsWith(STRATEGY_PREFIX)) {
					Integer index = parseIndex(strategy.substring(STRATEGY_PREFIX.length()));
					if (index != null) {
						indices.add(index);
					}
				}
			}
		}
		return indices.size() == 1 ? indices.iterator().next() : null;
	}

	/**
	 * Replaces the path segment at the given index (counted like
	 * <code>org.springframework.web.accept.PathApiVersionResolver</code> does, i.e. within the application path)
	 * with the version, but only if that segment is a URI variable.
	 */
	private static String insertVersion(String path, String version, int index) {
		String[] segments = path.split("/", -1);
		int segmentIndex = 0;
		for (int i = 0; i < segments.length; i++) {
			if (segments[i].isEmpty()) {
				continue;
			}
			if (segmentIndex++ == index) {
				if (!URI_VARIABLE.matcher(segments[i]).matches()) {
					return path;
				}
				segments[i] = version;
				return String.join("/", segments);
			}
		}
		return path;
	}

	private static Integer parseIndex(String value) {
		if (value == null) {
			return null;
		}
		try {
			int index = Integer.parseInt(value.trim());
			return index >= 0 ? index : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
