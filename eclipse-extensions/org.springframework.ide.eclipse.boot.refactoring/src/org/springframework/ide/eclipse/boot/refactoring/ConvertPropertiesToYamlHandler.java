/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.refactoring;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.ui.handlers.HandlerUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class ConvertPropertiesToYamlHandler extends AbstractHandler {

	private static ITextFileBuffer getDirtyFileBuffer(IFile file) {
		ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
		if (buffer != null && buffer.isDirty()) {
			return buffer;
		}
		return null;
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IFile file = getTarget(event);
		try {
			if (file != null) {
				ITextFileBuffer dirtyBuffer = getDirtyFileBuffer(file);
				if (dirtyBuffer != null) {
					dirtyBuffer.commit(null, true);
				}
				ConvertPropertiesToYamlRefactoring refactoring = new ConvertPropertiesToYamlRefactoring(file);
				RefactoringWizard wizard = new RefactoringWizard(refactoring,
						RefactoringWizard.DIALOG_BASED_USER_INTERFACE |
						RefactoringWizard.CHECK_INITIAL_CONDITIONS_ON_OPEN |
						RefactoringWizard.NO_BACK_BUTTON_ON_STATUS_DIALOG
				) {

					@Override
					protected void addUserInputPages() {
						//no inputs required
					}
				};
				new RefactoringWizardOpenOperation(wizard).run(HandlerUtil.getActiveShell(event), "Convert '"+file.getName()+"' to .yaml");
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	private IFile getTarget(ExecutionEvent event) {
		ISelection selection = HandlerUtil.getActiveMenuSelection(event);
		IStructuredSelection ss = null;
		if (selection instanceof IStructuredSelection) {
			ss = (IStructuredSelection) selection;
		} else {
			selection = HandlerUtil.getActiveMenuEditorInput(event);
			if (selection instanceof IStructuredSelection) {
				ss = (IStructuredSelection) selection;
			}
		}
		if (ss != null && !ss.isEmpty()) {
			return asFile(ss.getFirstElement());
		}
		return null;
	}

	private IFile asFile(Object selectedElement) {
		if (selectedElement instanceof IFile) {
			return (IFile) selectedElement;
		}
		if (selectedElement instanceof IAdaptable) {
			return ((IAdaptable) selectedElement).getAdapter(IFile.class);
		}
		return null;
	}
}
