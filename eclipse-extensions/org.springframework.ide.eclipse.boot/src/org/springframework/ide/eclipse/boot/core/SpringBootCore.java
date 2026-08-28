/*******************************************************************************
 * Copyright (c) 2013, 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.osgi.framework.Bundle;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrService;
import org.springsource.ide.eclipse.commons.core.preferences.StsProperties;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class SpringBootCore {

	public static final String M2E_NATURE = "org.eclipse.m2e.core.maven2Nature";
	public static final String BUILDSHIP_NATURE = "org.eclipse.buildship.core.gradleprojectnature";

	private static final StsProperties stsProps = StsProperties.getInstance();
	private InitializrService initializr;

	@SuppressWarnings("unchecked")
	private Supplier<Map<String, Supplier<Class<? extends ISpringBootProject>>>> registry = Suppliers.memoize(() -> {
		Map<String, Supplier<Class<? extends ISpringBootProject>>> map = new HashMap<>();
		for (IConfigurationElement projectConfig : Platform.getExtensionRegistry().getConfigurationElementsFor("org.springframework.ide.eclipse.boot.project")) {
			if ("project".equals(projectConfig.getName())) {
				Bundle bundle = Platform.getBundle(projectConfig.getContributor().getName());
				map.put(projectConfig.getAttribute("nature"), Suppliers.memoize(() -> {
					String projectClass = projectConfig.getAttribute("projectClass");
					try {
						return (Class<? extends ISpringBootProject>) bundle.loadClass(projectClass);
					} catch (ClassNotFoundException e) {
						Log.log(e);
						return null;
					}
				}));
			}
		}
		return map;
	});

	public SpringBootCore(InitializrService initializr) {
		this.initializr = initializr;
	}

	/**
	 * Deprecated, use of this method hampers testability. Instead 'good' code
	 * should use a SpringBootCore instance from its context to create a project.
	 *
	 * @return a SpringBoot centric view on a eclipse project.
	 */
	public static ISpringBootProject create(IProject project) throws CoreException {
		return getDefault().project(project);
	}

	/**
	 * Deprecated, use of this method hampers testability. Instead 'good' code
	 * should use a SpringBootCore instance from its context to create a project.
	 *
	 * @return a SpringBoot centric view on a eclipse project.
	 */
	@Deprecated
	public static ISpringBootProject create(IJavaProject project) throws CoreException {
		return getDefault().project(project);
	}

	private static SpringBootCore instance;

	/**
	 * Gets the default instance. Callers should be careful calling this method as this
	 * makes it hard to test the calling code (i.e. dependency injection of mocks).
	 * <p>
	 * Generally callers should instead seek to obtain a reference to a {@link SpringBootCore}
	 * instance from their context.
	 */
	public static SpringBootCore getDefault() {
		if (instance == null) {
			instance = new SpringBootCore(InitializrService.DEFAULT);
		}
		return instance;
	}

	/**
	 * @return a SpringBoot centric view on a eclipse project.
	 */
	public ISpringBootProject project(IJavaProject project) throws CoreException {
		return project(project.getProject());
	}

	/**
	 * @return a SpringBoot centric view on a eclipse project.
	 */
	public ISpringBootProject project(IProject project) throws CoreException {
		for (Entry<String, Supplier<Class<? extends ISpringBootProject>>> e : registry.get().entrySet()) {
			if (project.hasNature(e.getKey())) {
				Class<? extends ISpringBootProject> clazz = e.getValue().get();
				if (clazz != null) {
					try {
						Constructor<? extends ISpringBootProject> constructor = clazz.getConstructor(IProject.class, InitializrService.class);
						return constructor.newInstance(project, initializr);
					} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
						try {
							Constructor<? extends ISpringBootProject> constructor = clazz.getConstructor(IProject.class);
							return constructor.newInstance(project);
						} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e2) {
							Log.log(e2);
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Normally we determine the version of spring boot used by a project based on its
	 * classpath. But spring-boot jar is not yet on the classpath we use the defaultVersion
	 */
	public static String getDefaultBootVersion() {
		return stsProps.get("spring.boot.default.version");
	}


}
