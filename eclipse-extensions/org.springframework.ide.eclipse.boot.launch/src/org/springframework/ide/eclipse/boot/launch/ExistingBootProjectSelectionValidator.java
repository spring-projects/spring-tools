/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import static org.springsource.ide.eclipse.commons.livexp.core.ValidationResult.error;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

/**
 * Validation for a 'existing spring-boot-project selection'.
 *
 * @author Kris De Volder
 */
public class ExistingBootProjectSelectionValidator extends Validator {

	private LiveExpression<IProject> selection;

	public ExistingBootProjectSelectionValidator(LiveExpression<IProject> selection) {
		Assert.isNotNull(selection);
		this.selection = selection;
		dependsOn(selection);
	}

	@Override
	protected ValidationResult compute() {
		IProject p = selection.getValue();
		if (p == null) {
			return error("No project selected");
		} else if (!p.exists()) {
			return error("Project '"+p.getName()+"' does not exist in the workspace");
		} else if (!p.isAccessible()) {
			return error("Project '"+p.getName()+"' is closed");
		} else if (!BootPropertyTester.isBootProject(p)) {
			return error("Project '"+p.getName()+"' does not look like a Boot project");
		}
		return ValidationResult.OK;
	}

}
