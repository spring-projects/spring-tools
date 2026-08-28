/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.dialogs.WorkingSetGroup;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * Wraps a org.eclipse.ui.dialogs.WorkingSetConfigurationBlock so that it can
 * inserted into a WizardPageWithSections
 *
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class WorkingSetSection extends WizardPageSection {

	private WorkingSetGroup group;
	private final IStructuredSelection selection;

	public WorkingSetSection(IPageWithSections owner, IStructuredSelection selection) {
		super(owner);
		this.selection = selection;
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return Validator.OK;
	}

	@Override
	public void createContents(Composite page) {
		String[] workingSetTypes= new String[] { IWorkingSetIDs.JAVA, IWorkingSetIDs.RESOURCE };
		this.group = new WorkingSetGroup(page, selection, workingSetTypes);
	}

	public IWorkingSet[] getWorkingSets() {
		if (group != null) {
			return group.getSelectedWorkingSets();
		}
		return new IWorkingSet[0];
	}

}
