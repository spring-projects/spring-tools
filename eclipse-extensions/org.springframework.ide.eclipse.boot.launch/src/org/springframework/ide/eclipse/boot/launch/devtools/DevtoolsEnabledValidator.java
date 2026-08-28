/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.devtools;

import org.eclipse.core.resources.IProject;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

public class DevtoolsEnabledValidator extends Validator {

	private LiveVariable<IProject> project;

	public DevtoolsEnabledValidator(LiveVariable<IProject> project) {
		this.project = project;
		dependsOn(project);
	}

	@Override
	protected ValidationResult compute() {
		IProject p = project.getValue();
		if (p != null) {
			if (!BootPropertyTester.hasDevtools(p)) {
				return ValidationResult.error("Project '"+p.getName()+"' does not have spring-boot-devtools on its classpath");
			}
		}
		return ValidationResult.OK;
	}

}
