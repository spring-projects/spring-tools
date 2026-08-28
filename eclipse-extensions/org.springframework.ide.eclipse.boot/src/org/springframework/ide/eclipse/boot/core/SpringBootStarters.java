/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.Platform;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrDependencySpec;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrDependencySpec.BomInfo;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrDependencySpec.DependencyInfo;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrDependencySpec.RepoInfo;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.DependencyGroup;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;

/**
 * A model containing data / knowledge about the 'starters' for a given spring boot version.
 * <p>
 * This knowledge is obtained from the 'intilizr' web service (start.spring.io).
 *
 * @author Kris De Volder
 */
public class SpringBootStarters {

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private InitializrDependencySpec dependencySpec;
	private InitializrServiceSpec initializrSpec;
	private HashMap<String, SpringBootStarter> byId;
	private HashMap<MavenId, SpringBootStarter> byMavenId;
	private HashMap<MavenId, Bom> bomsByMavenId;

	public SpringBootStarters(InitializrServiceSpec initializrSpec, InitializrDependencySpec dependencySpec) {
		this.dependencySpec = dependencySpec;
		this.initializrSpec = initializrSpec;
	}

	public static SpringBootStarters load(URL initializerUrl, URL dependencyUrl, URLConnectionFactory urlConnectionFactory) throws Exception {
		debug("Loading spring boot starters from: "+dependencyUrl);
		return new SpringBootStarters(
				InitializrServiceSpec.parseFrom(urlConnectionFactory, initializerUrl),
				InitializrDependencySpec.parseFrom(urlConnectionFactory, dependencyUrl)
		);
	}

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	public DependencyGroup[] getDependencyGroups() {
		return initializrSpec.getDependencies();
	}

	public MavenId getMavenId(String findId) {
		ensureIndexes();
		SpringBootStarter starter = byId.get(findId);
		if (starter != null) {
			return starter.getMavenId();
		}
		return null;
	}

	/**
	 * Ensures that the indexes 'byId' and 'byMavenId' have been created. Any method using
	 * one of the indexes should call this method first.
	 */
	private synchronized void ensureIndexes() {
		if (byId == null) {
			byId = new HashMap<>();
			byMavenId = new HashMap<>();
			bomsByMavenId = new HashMap<>();

			HashMap<String, Repo> reposById = new HashMap<>();
			for (Entry<String, RepoInfo> e : dependencySpec.getRepositories().entrySet()) {
				String id = e.getKey();
				RepoInfo repo = e.getValue();
				reposById.put(id, new Repo(id, repo));
			}
			HashMap<String, Bom> bomsById = new HashMap<>();
			for (Entry<String, BomInfo> e : dependencySpec.getBoms().entrySet()) {
				String id = e.getKey();
				BomInfo bomInfo = e.getValue();
				List<Repo> repos = new ArrayList<>();
				String[] repoIds = bomInfo.getRepositories();
				if (repoIds != null) {
					for (String repoId : repoIds) {
						Repo repo = reposById.get(repoId);
						if (repo != null) {
							repos.add(repo);
						}
					}
				}
				Bom bom = new Bom(id,
						new MavenCoordinates(bomInfo.getGroupId(), bomInfo.getArtifactId(), bomInfo.getClassifier(), bomInfo.getVersion()),
						repos
				);
				bomsById.put(e.getKey(), bom);
				bomsByMavenId.put(new MavenId(bom.getGroupId(), bom.getArtifactId()), bom);
			}

			for (Entry<String, DependencyInfo> e : dependencySpec.getDependencies().entrySet()) {
				String id = e.getKey();
				DependencyInfo dep = e.getValue();
				String groupId = dep.getGroupId();
				String artifactId = dep.getArtifactId();
				String scope = dep.getScope();
				String bom = dep.getBom();
				String repo = dep.getRepository();
				if (id != null && groupId != null && artifactId != null) {
					//ignore invalid looking entries. Should at least have an id, aid and gid
					SpringBootStarter starter = new SpringBootStarter(id, new MavenCoordinates(dep), scope, bomsById.get(bom), reposById.get(repo));
//					debug(id + " => "+groupId + ":"+artifactId);
					byId.put(id, starter);
					byMavenId.put(new MavenId(groupId, artifactId), starter);
				}
			}

			//'extraBackmappings' below are a hacky workaround for https://github.com/spring-projects/sts4/issues/315
			//Basically, we add some additional 'back mappings' from mavenId to starter here that are missing from
			//the metadata.
			extraBackMapping("cloud-function", "org.springframework.cloud", "spring-cloud-function-web");
			extraBackMapping("session", "org.springframework.session", "spring-session-data-redis");
			extraBackMapping("session", "org.springframework.session", "spring-session-jdbc");
		}
	}

	private void extraBackMapping(String starterId, String gid, String aid) {
		SpringBootStarter starter = byId.get(starterId);
		if (starter != null) {
			MavenId mid = new MavenId(gid, aid);
			Assert.isTrue(!byMavenId.containsKey(mid));
			byMavenId.put(mid, starter);
		}
	}

	public String getBootVersion() {
		return dependencySpec.getBootVersion();
	}

	public boolean contains(String id) {
		return getMavenId(id) != null;
	}

	public List<String> getStarterIds() {
		ensureIndexes();
		return Collections.unmodifiableList(new ArrayList<>(byId.keySet()));
	}

	public synchronized String getId(MavenId mavenId) {
		ensureIndexes();
		SpringBootStarter starter = byMavenId.get(mavenId);
		if (starter != null) {
			return starter.getId();
		}
		return null;
	}

	public SpringBootStarter getStarter(MavenId mavenId) {
		ensureIndexes();
		return byMavenId.get(mavenId);
	}

	public SpringBootStarter getStarter(String id) {
		ensureIndexes();
		return byId.get(id);
	}

	public Map<String, RepoInfo> getRepos() {
		return dependencySpec.getRepositories();
	}

	public Bom getBom(MavenId bomMavenId) {
		ensureIndexes();
		return bomsByMavenId.get(bomMavenId);
	}

}