/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.util;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.*;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCY;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChilds;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.getChild;
import static org.springframework.ide.eclipse.boot.util.PomUtils.*;

import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.ide.eclipse.boot.core.MavenId;
import org.springsource.ide.eclipse.commons.frameworks.core.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@SuppressWarnings("restriction")
public class DependencyDelta {

	private final Set<MavenId> removed;
	public final Map<MavenId, Optional<String>> added; // mavenId -> scope mapping
	public final Set<MavenId> removedBoms;
	public final Set<MavenId> addedBoms;

	public DependencyDelta(Set<MavenId> removed, Map<MavenId, Optional<String>> added,
			Set<MavenId> removedBom, Set<MavenId> addedBom) {
		super();
		this.removed = removed;
		this.added = added;
		this.removedBoms = removedBom;
		this.addedBoms = addedBom;
	}

	public static DependencyDelta create(String referencePomString, String targetPomString) throws Exception {
		Document referencePom = parsePom(referencePomString);
		Document targetPom = parsePom(targetPomString);
		Map<MavenId, Optional<String>> referenceDeps = parseDependencies(referencePom);
		Map<MavenId, Optional<String>> targetDeps = parseDependencies(targetPom);
		Set<MavenId> removed = new HashSet<>();
		Map<MavenId, Optional<String>> added = new LinkedHashMap<>(); //use linked hashmap. Not really important, but gives more predictable/stable ordering
		for (MavenId oldDep : referenceDeps.keySet()) {
			if (targetDeps.containsKey(oldDep)) {
				//it still exists
			} else {
				removed.add(oldDep);
			}
		}
		for (Entry<MavenId, Optional<String>> newDep : targetDeps.entrySet()) {
			if (referenceDeps.containsKey(newDep.getKey())) {
				//it already existed before
			} else {
				added.put(newDep.getKey(), newDep.getValue());
			}
		}

		Set<MavenId> referenceBoms = parseBoms(referencePom);
		Set<MavenId> targetBoms = parseBoms(targetPom);
		ImmutableSet.Builder<MavenId> addedBoms = ImmutableSet.builder();
		ImmutableSet.Builder<MavenId> removedBoms = ImmutableSet.builder();
		for (MavenId oldDep : referenceBoms) {
			if (!targetBoms.contains(oldDep)) {
				removedBoms.add(oldDep);
			}
		}
		for (MavenId newDep : targetBoms) {
			if (!referenceBoms.contains(newDep)) {
				addedBoms.add(newDep);
			}
		}

		return new DependencyDelta(removed, added, removedBoms.build(), addedBoms.build());
	}

	private static Set<MavenId> parseBoms(Document pom) {
		ImmutableSet.Builder<MavenId> boms = ImmutableSet.builder();
		try {
			Element depsEl = getChild(pom.getDocumentElement(), DEPENDENCY_MANAGEMENT, DEPENDENCIES);
			List<Element> deps = findChilds(depsEl, DEPENDENCY);
			for (Element bom : deps) {

				if (
						"import".equals(getScope(bom)) &&
						"pom".equals(getType(bom))
				) {
					String gid = getGroupId(bom);
					String aid = getArtifactId(bom);
					if (gid != null && aid != null) {
						boms.add(new MavenId(gid, aid));
					}
				}
			}
		} catch (Exception e) {
			//some issue with the pom structure, probably no DEPENDENCY_MANAGEMENT section.
			//This is sort of expected as not every pom has such a section,
			//So just ignore.
		}
		return boms.build();
	}

	private static Map<MavenId, Optional<String>> parseDependencies(Document pom) throws Exception {
		ImmutableMap.Builder<MavenId, Optional<String>> builder = ImmutableMap.builder();
		Element depsEl = getChild(
				pom.getDocumentElement(), DEPENDENCIES);
		List<Element> deps = findChilds(depsEl, DEPENDENCY);
		for (Element dep : deps) {
			String gid = PomUtils.getGroupId(dep);
			String aid = PomUtils.getArtifactId(dep);
			if (aid != null && gid != null) {
				builder.put(new MavenId(gid, aid), Optional.ofNullable(PomUtils.getScope(dep)));
			}
		}
		return builder.build();
	}

	private static Document parsePom(String pomContents) throws Exception {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		XmlUtils.configureDocumentBuilderFactory(dbFactory);
		return dbFactory.newDocumentBuilder().parse(new InputSource(new StringReader(pomContents)));
	}

	public boolean isRemoved(MavenId id) {
		return removed.contains(id);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("DependencyDelta(");
		for (MavenId mavenId : removed) {
			builder.append(" -"+mavenId.getGroupId()+":"+mavenId.getArtifactId());
		}
		for (MavenId mavenId : added.keySet()) {
			builder.append(" +"+mavenId.getGroupId()+":"+mavenId.getArtifactId());
		}
		if (!addedBoms.isEmpty() || !removedBoms.isEmpty()) {
			builder.append(" boms:");
			for (MavenId mavenId : removedBoms) {
				builder.append(" -"+mavenId.getGroupId()+":"+mavenId.getArtifactId());
			}
			for (MavenId mavenId : addedBoms) {
				builder.append(" +"+mavenId.getGroupId()+":"+mavenId.getArtifactId());
			}
		}
		builder.append(" )");
		return builder.toString();
	}

}
