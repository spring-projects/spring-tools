/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.springframework.ide.eclipse.boot.core.IMavenCoordinates;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.MavenId;
import org.springframework.ide.eclipse.boot.core.SpringBootStarter;
import org.springframework.ide.eclipse.boot.core.SpringBootStarters;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrService;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public abstract class SpringBootProject implements ISpringBootProject {

	static final List<SpringBootStarter> NO_STARTERS = Collections.emptyList();
	private final InitializrService initializr;
	protected final IProject project;
	private CompletableFuture<SpringBootStarters> cachedStarterInfos;

	public SpringBootProject(IProject project, InitializrService initializr) {
		Assert.isNotNull(project);
		this.project = project;
		this.initializr = initializr;
	}

	@Override
	public List<SpringBootStarter> getKnownStarters() throws Exception {
		SpringBootStarters infos = getStarterInfos();
		if (infos != null) {
			List<String> knownIds = infos.getStarterIds();
			List<SpringBootStarter> starters = new ArrayList<>(knownIds.size());
			for (String id : knownIds) {
				SpringBootStarter starter = infos.getStarter(id);
				starters.add(starter);
			}
			return starters;
		}
		return NO_STARTERS;
	}

	@Override
	public SpringBootStarters getStarterInfos() throws Exception {
		boolean firstAccess = false;
		synchronized (this) {
			if (cachedStarterInfos == null) {
				firstAccess = true;
				cachedStarterInfos = new CompletableFuture<>();
			}
		}
		if (firstAccess) {
			try {
				cachedStarterInfos.complete(fetchStarterInfos());
			} catch (Throwable e) {
				cachedStarterInfos.completeExceptionally(e);
			}
		}
		return cachedStarterInfos.get();
	}

	private SpringBootStarters fetchStarterInfos() throws Exception {
		String bootVersion = getBootVersion();
		if (bootVersion != null) {
			return initializr.getStarters(bootVersion);
		}
		throw new IllegalStateException("Couldn't determine boot version for '"+project.getName()+"'");
	}

	@Override
	public List<SpringBootStarter> getBootStarters() throws Exception {
		SpringBootStarters infos = getStarterInfos();
		List<IMavenCoordinates> deps = getDependencies();
		ArrayList<SpringBootStarter> starters = new ArrayList<>();
		for (IMavenCoordinates dep : deps) {
			String aid = dep.getArtifactId();
			String gid = dep.getGroupId();
			if (aid != null && gid != null) {
				MavenId mavenId = new MavenId(gid, aid);
				SpringBootStarter starter = infos.getStarter(mavenId);
				if (starter != null) {
					starters.add(starter);
				}
			}
		}
		return starters;
	}

	@Override
	public String generatePom(List<Dependency> initialDependencies) throws Exception {
		return initializr.getPom(pomGenerationParameters(initialDependencies));
	}

	protected Map<String, Object> pomGenerationParameters(List<Dependency> initialDependencies) throws Exception {
		SpringBootStarters knownStarters = getStarterInfos();

		Map<String, Object> parameters = new HashMap<>();

		parameters.put("name", project.getName());
		parameters.put("bootVersion", getBootVersion());

		List<String> starterIds = new ArrayList<>();
		for (Dependency dep : initialDependencies) {
			String id = dep.getId();
			//ignore unkown deps
			if (knownStarters.getStarter(id) != null) {
				starterIds.add(id);
			}
		}
		parameters.put("dependencies", starterIds);

		return parameters;
	}

	public boolean isKnownStarter(MavenId mavenId) {
		return getStarter(mavenId) != null;
	}

	protected SpringBootStarter getStarter(MavenId mavenId) {
		try {
			SpringBootStarters infos = getStarterInfos();
			if (infos != null) {
				return infos.getStarter(mavenId);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	@Override
	public String packageName() throws CoreException {
		IJavaProject javaProject = JavaCore.create(project);
		List<String> packageNames = new ArrayList<>();
		for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE
					&& !root.isExternal()
					&& root.hasChildren()
					&& root.getChildren().length > 0) {
				for (IJavaElement e : root.getChildren()) {
					if (e instanceof IPackageFragment) {
						IPackageFragment pkg = (IPackageFragment) e;
						if (pkg.containsJavaResources()) {
							String name = pkg.getElementName();
							packageNames.add(name);
						}
					}
				}
			}
		}

		String pkgName = packageNames.stream()
			.min((p1, p2) -> p1.length() - p2.length())
			.orElse(null);
		return pkgName;

	}

	@Override
	public IProject getProject() {
		return project;
	}

}
