/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.Collection;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.DeletionCapabableModel;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class DeleteElementsAction<T extends RunTargetType> extends AbstractBootDashElementsAction {

	public static String PREF_SKIP_CONFIRM_DELETE(RunTargetType rtt) {
		return "boot.dash."+rtt.getName()+".delete.confirm.skip";
	}

	private Class<T> targetTypeClass;

	public DeleteElementsAction(BootDashActions actions, Class<T> targetType, MultiSelection<BootDashElement> selection, SimpleDIContext context) {
		super(new Params(actions)
				.setSelection(selection)
				.setContext(context)
		);
		this.targetTypeClass = targetType;
		context.assertDefinitionFor(UserInteractions.class);
		this.setText("Delete Elements");
		this.setToolTipText("Delete the selected elements.");
		this.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/delete_app.png"));
		this.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/delete_app_disabled.png"));
	}

	@Override
	public void run() {
		//Deletes are implemented per BootDashModel. So sort selection into bins per model.
		Multimap<BootDashModel, BootDashElement> sortingBins = HashMultimap.create();
		for (BootDashElement e : getSelectedElements()) {
			BootDashModel model = e.getBootDashModel();
			//We are only capable of removing elements from a DeleteCapabableModel.
			if (model instanceof DeletionCapabableModel) {
				sortingBins.put(model, e);
			}
		}
		//Now delete elements from corresponding models.
		for (final Entry<BootDashModel, Collection<BootDashElement>> workitem : sortingBins.asMap().entrySet()) {
			BootDashModel model = workitem.getKey();
			final DeletionCapabableModel modifiable = (DeletionCapabableModel)model; //cast is safe. Only DeleteCapabableModel are added to sortingBins
			String confirmationMsg = modifiable.getDeletionConfirmationMessage(workitem.getValue());
			RunTargetType runTargetType = model.getRunTarget().getType();
			if (confirmationMsg == null || ui().confirmWithToggle(PREF_SKIP_CONFIRM_DELETE(runTargetType), "Deleting Elements", confirmationMsg, "Skip this dialog next time")) {
				Job job = new Job("Deleting Elements from " + model.getRunTarget().getName()) {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						modifiable.delete(workitem.getValue(), ui());
						return Status.OK_STATUS;
					}

				};
				job.schedule();
			}
		}
	}

	@Override
	public void updateEnablement() {
		this.setEnabled(shouldEnableFor(getSelectedElements()));
	}

	@Override
	public void updateVisibility() {
		this.setVisible(shouldShowFor(getSelectedElements()));
	}

	protected boolean shouldShowFor(Collection<BootDashElement> selectedElements) {
		//Only visible if all selected elements belong to the correct runtarget type
		if (selectedElements.isEmpty()) {
			return false;
		}
		for (BootDashElement e : selectedElements) {
			if (!isCorrectTargetType(e.getBootDashModel())) {
				return false;
			}
		}
		return true;
	}

	protected boolean shouldEnableFor(Collection<BootDashElement> selectedElements) {
		if (selectedElements.isEmpty()) {
			//If no elements are selected, then action would do nothing, so disable it.
			return false;
		}
		//All selected elements must be deletable, then this action is enabled.
		for (BootDashElement bde : selectedElements) {
			if (!canDelete(bde)) {
				return false;
			}
		}
		return true;
	}

	private boolean canDelete(BootDashElement bde) {
		BootDashModel model = bde.getBootDashModel();
		return isCorrectTargetType(model) && model instanceof DeletionCapabableModel &&
				((DeletionCapabableModel)model).canDelete(bde);
	}

	private boolean isCorrectTargetType(BootDashModel model) {
		return model != null && targetTypeClass.isAssignableFrom(model.getRunTarget().getType().getClass());
	}

}
