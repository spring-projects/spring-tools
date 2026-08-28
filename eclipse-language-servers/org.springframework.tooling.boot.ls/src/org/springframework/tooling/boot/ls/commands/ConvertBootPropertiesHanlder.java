/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.commands;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.ui.handlers.HandlerUtil;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;

@SuppressWarnings("restriction")
public abstract class ConvertBootPropertiesHanlder extends AbstractHandler {
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IFile sourceFile = getSourceFile(event);
		IProject project = sourceFile.getProject();
		if (project != null) {
			
			// No replace functionality for Eclipse due to issues with undoing the change
			final boolean replace = false;
			
//			ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(sourceFile.getFullPath(), LocationKind.IFILE);
//			if (buffer != null && buffer.isDirty()) {
//				try {
//					buffer.commit(null, true);
//				} catch (CoreException e) {
//					BootLanguageServerPlugin.getDefault().getLog().log(e.getStatus());
//				}
//			}			
//
//			final boolean replace = BootLanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(Constants.PREF_REPLACE_CONVERTED_PROPS_FILE);
//			
//			if (replace) {
//				// Workaround: LSP4E LSPTextEdit doesn't work well with non opened files in the workspace in this particular case
//				// Therefore, open the file in an editor thus changes are applied to the document.
//				try {
//					IDE.openEditor(UI.getActivePage(), sourceFile);
//				} catch (PartInitException e) {
//					BootLanguageServerPlugin.getDefault().getLog().error("", e);
//				}
//			}
			
			IFile targetFile = getTargetFile(sourceFile, getTargetExtension());
			
			LanguageServerDefinition def = LanguageServersRegistry.getInstance().getDefinition(BootLanguageServerPlugin.BOOT_LS_DEFINITION_ID);
			Assert.isLegal(def != null, "No definition found for Boot Language Server");

			ExecuteCommandParams commandParams = new ExecuteCommandParams();
			commandParams.setCommand(getCommandId());
			commandParams.setArguments(List.of(
					sourceFile.getLocationURI().toASCIIString(),
					targetFile.getLocationURI().toASCIIString(),
					replace
			));
			
			LanguageServers.forProject(project).withPreferredServer(def).computeFirst(ls -> ls.getWorkspaceService().executeCommand(commandParams));
			
		}
		
		return null;
	}
	
	abstract protected String getTargetExtension();
	
	abstract protected String getCommandId();
	
	private IFile getTargetFile(IFile sourceFile, String ext) {
		IProject project = sourceFile.getProject();
		IPath eclipsePath = sourceFile.getProjectRelativePath();
		IPath dir = eclipsePath.removeLastSegments(1);
		String fileName = sourceFile.getName();
		String fileNoExt = fileName.substring(0, fileName.length() - sourceFile.getFileExtension().length() - 1);
		IFile target = project.getFile(dir.append("%s.%s".formatted(fileNoExt, ext)));
		for (int i = 1; i < Integer.MAX_VALUE && target.exists(); i++) {
			target = project.getFile(dir.append("%s-%d.%s".formatted(fileNoExt, i, ext)));
		}
		return target;
	}
	
	private IFile getSourceFile(ExecutionEvent event) {
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
		
	public static class ConvertPropertiesToYamlHandler extends ConvertBootPropertiesHanlder {

		@Override
		protected String getTargetExtension() {
			return "yml";
		}

		@Override
		protected String getCommandId() {
			return "sts/boot/props-to-yaml";
		}
		
	}
	
	public static class ConvertYamlToPropertiesHanlder extends ConvertBootPropertiesHanlder {

		@Override
		protected String getTargetExtension() {
			return "properties";
		}

		@Override
		protected String getCommandId() {
			return "sts/boot/yaml-to-props";
		}
		
	}


}
