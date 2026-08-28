/*******************************************************************************
 * Copyright (c) 2015, 2018 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.ui;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.MavenCoordinates;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.core.SpringBootStarter;
import org.springframework.ide.eclipse.boot.util.version.VersionParser;
import org.springframework.ide.eclipse.boot.util.version.VersionRange;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.utils.ProjectFilter;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.utils.SelectionUtils;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class EnableDisableBootDevtools extends AbstractHandler {

	private static final VersionRange DEVTOOLS_SUPPORTED = VersionParser.DEFAULT.parseRange("1.3.0");
	private static final SpringBootStarter DEVTOOLS_STARTER = new SpringBootStarter("devtools",
		new MavenCoordinates(BootPropertyTester.SPRING_BOOT_DEVTOOLS_GID, BootPropertyTester.SPRING_BOOT_DEVTOOLS_AID, null),
		"compile", /*bom*/null, /*repo*/null
	);

	private SpringBootCore springBootCore = SpringBootCore.getDefault();

	/**
	 * Constructor that eclipse calls when it instantiates the delegate
	 */
	public EnableDisableBootDevtools() {
		this(SpringBootCore.getDefault());
	}

	/**
	 * Constructor that test code can use to inject mocks etc.
	 */
	public EnableDisableBootDevtools(SpringBootCore springBootCore) {
		this.springBootCore = springBootCore;
	}

	private String explainFailure(ISpringBootProject bootProject) throws Exception {
		IProject project = bootProject.getProject();
		if (project == null) {
			return "No project selected";
		} else if (!BootPropertyTester.isBootProject(project)) {
			return "Project '"+project.getProject().getName()+"' does not seem to be a Spring Boot project";
		} else if (!project.hasNature(SpringBootCore.M2E_NATURE)) {
			return "Project '"+project.getProject().getName()+"' is not an Maven/m2e enabled project. This action's implementation requires m2e to add/remove "
					+ "the DevTools as a dependency to your project.";
		} else {
			String version = bootProject.getBootVersion();
			return "Boot DevTools are provided by Spring Boot version 1.3.0 or later. "
					+ "Project '"+project.getProject().getName()+"' uses Boot Version "+version;
		}
	}

	private SpringBootStarter getAvaibleDevtools(ISpringBootProject project) {
		try {
			String versionString = project.getBootVersion();
			if (StringUtils.isNotBlank(versionString) && DEVTOOLS_SUPPORTED.match(VersionParser.DEFAULT.parse(versionString))) {
				return DEVTOOLS_STARTER;
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	private ISpringBootProject getBootProject(IProject project) {
		try {
			if (project != null) {
				return springBootCore.project(project);
			}
		} catch (Exception e) {
			if (!isExpected(e)) {
				Log.log(e);
			}
		}
		return null;
	}

	private boolean isExpected(Exception e) {
		//See https://issuetracker.springsource.com/browse/STS-4263
		String msg = ExceptionUtil.getMessage(e);
		return msg != null && msg.contains("only implemented for m2e");
	}

	@Override
	public Object execute(ExecutionEvent event) throws org.eclipse.core.commands.ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		Shell shell = HandlerUtil.getActiveShell(event);
		execute(selection, shell);
		return null;
	}

	public void execute(ISelection selection, Shell shell) {
		if (selection != null) {
			List<IProject> projects = SelectionUtils.getProjects(selection, ProjectFilter.anyProject);
			List<ISpringBootProject> bootProjects = new ArrayList<>(projects.size());
			for (IProject project : projects) {
				ISpringBootProject bootProject = getBootProject(project);
				if (bootProject != null) {
					bootProjects.add(bootProject);
				}
			}
			bootProjects.forEach(bootProject -> {
				try {
					SpringBootStarter devtools = getAvaibleDevtools(bootProject);
					if (BootPropertyTester.hasDevTools(bootProject)) {
						bootProject.removeMavenDependency(devtools.getMavenId());
					} else {
						if (devtools != null) {
							bootProject.addMavenDependency(devtools.getDependency(), /*preferManaged*/true);
						} else {
							if (shell != null) {
								MessageDialog.openError(shell, "Boot DevTools Dependency could not be added", explainFailure(bootProject));
							}
						}
					}
				} catch (Exception e) {
					Log.log(e);
					if (shell != null) {
						MessageDialog.openError(shell, "Unexpected failure",
								"The action to add/remove devtools unexpectedly failed with an error:\n" +
								ExceptionUtil.getMessage(e) + "\n" +
								"The error log may contain further information.");
					}
				}
			});
		}
	}

}
