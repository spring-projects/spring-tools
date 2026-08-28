/*******************************************************************************
 *  Copyright (c) 2013, 2023 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.importing;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.springframework.ide.eclipse.boot.wizard.content.BuildType;
import org.springsource.ide.eclipse.commons.core.IRunnableWithProgressAndResult;

/**
 * Strategy for importing a certain type of getting started content
 *
 * @author Kris De Volder
 */
public abstract class ImportStrategy {

	private final String name;
	private final BuildType buildType;
	private final String notInstalledMessage;

	public ImportStrategy(BuildType buildType, String name, String notInstalledMessage) {
		this.name = name;
		this.buildType = buildType;
		this.notInstalledMessage = notInstalledMessage;
	}

	public abstract IRunnableWithProgressAndResult<IProject> createOperation(ImportConfiguration conf);

	public boolean isSupported() {
		return true;
	}

	/**
	 * Subclasses should override to provide more precise message
	 */
	public final String getNotInstalledMessage() {
		return "Can not import using "+displayName()+" because "+notInstalledMessage;
	}

	public String getName() {
		return name;
	}

	public String displayName() {
		if (buildType.getImportStrategies().size() > 1) {
			return buildType.displayName() + " ("+name+")";
		}
		return buildType.displayName();
	}

	public IPath getBuildScript() {
		return buildType.getBuildScript();
	}

	public BuildType getBuildType() {
		return buildType;
	}

	@Override
	public String toString() {
		if (buildType.getImportStrategies().size() > 1) {
			return buildType + "-"+name;
		}
		return buildType.toString();
	}

	public String getId() {
		String id = buildType.toString();
		if (buildType.getImportStrategies().size() > 1) {
			id += "-" + name;
		}
		return id;
	}

}
