/*******************************************************************************
 *  Copyright (c) 2013, 2016 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.importing;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet;
import org.springframework.ide.eclipse.boot.wizard.content.GSContent;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * @author Kris De Volder
 */
public class ImportUtils {

	public static ImportConfiguration importConfig(final IPath location, final String projectName, final CodeSet codeset) {
		ImportConfiguration conf = new ImportConfiguration() {

//			@Override
			public String getLocation() {
				return location.toString();
			}

//			@Override
			public String getProjectName() {
				return projectName;
			}

//			@Override
			public CodeSet getCodeSet() {
				return codeset;
			}
		};
		return conf;
	}

	/**
	 * Convenience method to create a import configuration that imports a particular codeset for a given guide into the
	 * default location in the workspace.
	 */
	public static ImportConfiguration importConfig(GSContent guide, CodeSet codeset) {
		Assert.isNotNull(guide);
		Assert.isNotNull(codeset);
		String csName = codeset.getName();
		String projectName;
		if ("default".equals(csName)) {
			projectName = guide.getName();
		} else if (csName.equals(guide.getName())) {
			//Don't create silly names like 'spring-pet-clinic-spring-pet-clinic'
			projectName = csName;
		} else {
			projectName = guide.getName()+"-"+codeset.getName();
		}
		return ImportUtils.importConfig(
				/*location*/
				Platform.getLocation().append(projectName),
				/*name*/
				projectName,
				/*codeset*/
				codeset
		);
	}

	/**
	 * Validates whether import configuration doesn't clash with existing workspace or
	 * file system content. I.e. proceeding with the import would result in possible
	 * data loss (overwriting existing file system content) or Eclipse errors because of project name
	 * clashes
	 */
	public static ValidationResult validateImportConfiguration(ImportConfiguration conf) {
		//Note: for now it is assumed all properties of the conf are set (i.e. not null) when this is called.
		// This true for current use cases but may not be true for more complex wizards
		// that allow specifying location and/or project name.

		//Three things to check:
		try {
			//1: workspace project name is available
			String name = conf.getProjectName();
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
			if (project.exists()) {
				return ValidationResult.error("Project '"+name+"' already exists in the workspace");
			}

			//2: The target location is non-existent (otherwise data loss may ensue)
			File loc = new File(conf.getLocation());
			if (loc.exists()) {
				return ValidationResult.error("File or directory exists at '"+loc+"'");
			}

			//3: Ensure the path is under the workspace root
			File workspaceRoot = Platform.getLocation().toFile();
			String canonicalWorkspaceRoot = workspaceRoot.getCanonicalPath()+File.separator;
			String canonicalLoc = loc.getCanonicalPath();
			if (!(canonicalLoc+File.separator).startsWith(canonicalWorkspaceRoot)) {
				return ValidationResult.error("Project location '"+loc+"' is outside of the workspace");
			}

			//4: default file system location in workspace is available. This must
			// be true even if that location won't actually be used. Eclipse simply will not allow creating a project
			// with this name unless the default location is available for use.
			File defaultLocation = Platform.getLocation().append(name).toFile();
			if (defaultLocation.exists()) {
				return ValidationResult.error("Project '"+name+"' unavailable: File exists at '"+defaultLocation+"'");
			}

			return ValidationResult.OK;
		} catch (Throwable e) {
			//Unexpected error. But try to produce a message from it anyway
			BootWizardActivator.log(e);
			return ValidationResult.error(ExceptionUtil.getMessage(e));
		}
	}
}
