/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.refactoring.typerename;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdating;
import org.eclipse.jdt.ui.refactoring.RenameSupport;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class TypeRenameParticipant extends RenameParticipant {

	private static final String[] POSTFIXES = { "Test", "Tests"};
	private IType renamedType;
	private IType companionType;

	public TypeRenameParticipant() {
	}

	@Override
	protected boolean initialize(Object element) {
		try {
			if (element instanceof IType) {
				this.renamedType = (IType) element;
				this.companionType = findCompanion(renamedType);
				return companionType != null;
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	private IType findCompanion(IType renamedType) throws Exception {
		if (!renamedType.isMember() && !renamedType.isAnonymous()) {
			IJavaProject project = renamedType.getJavaProject();
			for (String postfix : POSTFIXES) {
				String baseName = renamedType.getFullyQualifiedName();
				String companionName = baseName+postfix;
				IType companion = project.findType(companionName);
				if (companion != null && !companion.isBinary()) {
					return companion;
				}
			}
		}
		return null;
	}

	@Override
	public String getName() {
		return "Rename Type Participant";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws OperationCanceledException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	private void schedulePromptForCompanionTypeRename() {
		System.out.println("DDDD");
		UIJob job = new UIJob("Prompt for Companion Type Rename") {
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				try {
					String oldName = renamedType.getElementName();
					String oldCompanionName = companionType.getElementName();
					String newName = getArguments().getNewName();
					Assert.isLegal(oldCompanionName.startsWith(oldName));
					String postfix = oldCompanionName.substring(oldName.length());
					String newCompanionName = newName+postfix;
					boolean answeredYes = MessageDialog.openQuestion(getShell(), "Also Rename '"+companionType.getElementName()+"'?",
							"Type '"+oldName+"' was renamed to '"+newName+"'." +
							"Do you also want to rename '"+companionType.getElementName()+
							"' from package '"+companionType.getPackageFragment().getElementName()+"' "+
							"to '"+newCompanionName+"'?"
					);
					if (answeredYes) {
						int options = 0;
						if ( getArguments().getUpdateReferences()) {
							options |= RenameSupport.UPDATE_REFERENCES;
						}
						if (getUpdateTextualMatches()) {
							options |= RenameSupport.UPDATE_TEXTUAL_MATCHES;
						}
						RenameSupport renameSupport = RenameSupport.create(companionType, newCompanionName, options);
						renameSupport.perform(getShell(), PlatformUI.getWorkbench().getProgressService());
					}
					return Status.OK_STATUS;
				} catch (Exception e) {
					return ExceptionUtil.status(e);
				}
			}

			private Shell getShell() {
				try {
					return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
				} catch (Exception e) {
					//troubles getting shell... no worries we'll just pass null.
				}
				return null;
			}
		};
		job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
		job.schedule();
	}


	private boolean getUpdateTextualMatches() {
		RefactoringProcessor processor = getProcessor();
		if (processor instanceof ITextUpdating) {
			return ((ITextUpdating) processor).getUpdateTextualMatches();
		}
		return false;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		//This is not an active participant. It does not contribute changes to the refactoring. It will just schedule
		return new Change() {
			@Override
			public Change perform(IProgressMonitor pm) throws CoreException {
				schedulePromptForCompanionTypeRename();
				return new NullChange();
			}

			@Override
			public String getName() {
				return "Prompt rename companion: '"+companionType.getElementName()+"'";
			}

			@Override
			public void initializeValidationData(IProgressMonitor pm) {
			}

			@Override
			public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
				return new RefactoringStatus();
			}

			@Override
			public Object getModifiedElement() {
				return null;
			}
		};
	}

}
