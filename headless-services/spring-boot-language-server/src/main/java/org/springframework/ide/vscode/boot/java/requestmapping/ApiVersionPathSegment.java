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
 * Puts the API version of a versioned request mapping into the path of a live URL when the
 * application uses path segment based API versioning (<code>usePathSegment(int)</code> or
 * <code>spring.mvc|webflux.apiversion.use.path-segment</code>), so that a mapping like
 * <code>/{version}/greeting</code> with <code>version = "1.1"</code> links to <code>/1.1/greeting</code>.
 *
 * @author Seonwoo Jung
 */
public class ApiVersionPathSegment {

	static final String STRATEGY_PREFIX = "Path Segment: ";

	private static final List<String> PATH_SEGMENT_PROPERTIES = List.of(
			"spring.mvc.apiversion.use.path-segment",
			"spring.webflux.apiversion.use.path-segment");

	// a URI variable like {version} or {version:\d+}, but not a capturing pattern like {*path}
	private static final Pattern URI_VARIABLE = Pattern.compile("\\{[^*/{}][^/]*\\}");

	/**
	 * @return the path with the API version of the mapping put into the version path segment,
	 * or the unchanged path if the mapping is not versioned or the version path segment is unknown
	 */
	public static String versionedPath(String path, LiveRequestMapping mapping, SpringProcessLiveData liveData, Integer configuredIndex) {
		String version = mapping.getApiVersion();
		if (version == null) {
			return path;
		}
		Integer liveIndex = fromLiveData(liveData);
		return insertVersion(path, version, liveIndex != null ? liveIndex : configuredIndex);
	}

	/**
	 * @return the path segment index from the properties of the running app, <code>null</code> if not
	 * configured via properties or not readable (e.g. values sanitized by the env endpoint)
	 */
	public static Integer fromLiveData(SpringProcessLiveData liveData) {
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
	 * @return the path segment index configured in the given web configs, <code>null</code> if none
	 * or if different indices are configured
	 */
	public static Integer fromWebConfigs(Collection<WebConfigIndexElement> webConfigs) {
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
	public static String insertVersion(String path, String version, Integer index) {
		if (path == null || version == null || index == null || index < 0) {
			return path;
		}
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
