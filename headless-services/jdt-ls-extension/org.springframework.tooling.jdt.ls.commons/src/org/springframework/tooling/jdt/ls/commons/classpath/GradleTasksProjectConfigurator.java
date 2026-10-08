/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.classpath;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.InitializationContext;
import org.eclipse.buildship.core.ProjectConfigurator;
import org.eclipse.buildship.core.ProjectContext;
import org.eclipse.buildship.core.internal.workspace.FetchStrategy;
import org.eclipse.buildship.core.internal.workspace.InternalGradleBuild;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.springframework.tooling.jdt.ls.commons.Logger;

/**
 * Runs in every synchronization of a Gradle project, after Buildship has loaded the Gradle model, which is when the
 * names of the tasks are known without running Gradle. They are remembered in the {@link GradleTasksStore} and sent
 * to the language server with the build info of the project.
 *
 * @author Alex Boyko
 */
public class GradleTasksProjectConfigurator implements ProjectConfigurator {

	@Override
	public void init(InitializationContext context, IProgressMonitor monitor) {
		// nothing to do
	}

	@Override
	public void configure(ProjectContext context, IProgressMonitor monitor) {
		Set<String> tasks = GradleTasksProjectConfigurator.readGradleTasksFromBuildship(context.getProject(), Logger.DEFAULT);
		if (tasks != null) {
			GradleTasksStore.INSTANCE.put(context.getProject().getName(), tasks);
		}
	}

	@Override
	public void unconfigure(ProjectContext context, IProgressMonitor monitor) {
		GradleTasksStore.INSTANCE.remove(context.getProject().getName());
	}

	private static EclipseProject findEclipseProject(EclipseProject project, File projectDir) {
		if (project.getProjectDirectory() != null && project.getProjectDirectory().toPath().normalize().equals(projectDir.toPath().normalize())) {
			return project;
		}
		for (EclipseProject child : project.getChildren()) {
			EclipseProject found = findEclipseProject(child, projectDir);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/**
	 * Names of the tasks of the Gradle project without running Gradle: from the model that Buildship has in memory,
	 * or else the tasks as of the last synchronization of the project (see {@link GradleTasksProjectConfigurator}).
	 *
	 * @return the task names or <code>null</code> if they are not known (yet)
	 */
	static Set<String> getGradleTasks(IProject project, Logger logger) {
		Set<String> tasks = readGradleTasksFromBuildship(project, logger);
		return tasks != null ? tasks : GradleTasksStore.INSTANCE.get(project.getName());
	}

	/**
	 * Names of the tasks of the Gradle project as far as Buildship has them in memory. Never runs Gradle (no
	 * daemon, no Tooling API call): Buildship caches the Eclipse model of a build when the project is
	 * synchronized, but not across restarts of the IDE.
	 *
	 * @return the task names or <code>null</code> if the model is not available
	 */
	@SuppressWarnings("restriction")
	private static Set<String> readGradleTasksFromBuildship(IProject project, Logger logger) {
		try {
			Optional<GradleBuild> build = GradleCore.getWorkspace().getBuild(project);
			if (build.isPresent() && build.get() instanceof InternalGradleBuild internalBuild) {
				// Whatever the cache has under the model type. Not casting to what is expected, in case the cache
				// holds a single model rather than a map of models of the build.
				Object cached = internalBuild.getModelProvider().fetchModels(EclipseProject.class, FetchStrategy.FROM_CACHE_ONLY,
						GradleConnector.newCancellationTokenSource(), new NullProgressMonitor());
				File projectDir = project.getLocation() == null ? null : project.getLocation().toFile();
				if (projectDir != null && cached != null) {
					Collection<?> models = cached instanceof Map<?, ?> map ? map.values() : List.of(cached);
					for (Object model : models) {
						if (model instanceof EclipseProject eclipseProject) {
							EclipseProject found = GradleTasksProjectConfigurator.findEclipseProject(eclipseProject, projectDir);
							if (found != null) {
								return found.getGradleProject().getTasks().stream().map(GradleTask::getName).collect(Collectors.toUnmodifiableSet());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			logger.log(e);
		}
		return null;
	}

}
