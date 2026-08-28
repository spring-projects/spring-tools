/*******************************************************************************
 * Copyright (c) 2020, 2021 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.DocumentProviderRegistry;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.livexp.util.OldValueDisposer;

@SuppressWarnings("restriction")
public class SelectionTracker extends AbstractDisposable {
	
	private static Map<IWorkbenchWindow, SelectionTracker> INSTANCES = new HashMap<>();
	
	public static synchronized SelectionTracker getInstance(IWorkbenchWindow wbw) {
		return INSTANCES.computeIfAbsent(wbw, _wbw -> {
			return new SelectionTracker(wbw);
		});
	}
	
	private static synchronized void disposeInstance(IWorkbenchWindow wbw) {
		SelectionTracker removed = INSTANCES.remove(wbw);
		if (removed != null) {
			removed.dispose();
		}
	}
	
	public static synchronized void disposeAll(IWorkbenchWindow[] workbenchWindows) {
		if (workbenchWindows != null) {
			for (IWorkbenchWindow wbw : workbenchWindows) {
				disposeInstance(wbw);
			}
			INSTANCES = new HashMap<>();
		}
	}
	
	// In order for the symbols view to correctly fetching information for a selection,  it needs to find an active language
	// server for the given selection via LSP4E API (see the symbols view model), and document is required to find that active language server
	// Typically this document will be available if an editor is open.
	// However, symbols view also supports the case of showing information on a selection that does not have an open editor.
	// In this case, a document needs to be available for that selection, which can be accomplished my performing part of the behaviour
	// that would occur when an actual editor opens: namely connecting to that document via a document provider. This is what the document
	// data does: it simulates "opening" an editor when there is a selection,and "closing" an editor for an old selection when
	// there is a selection change
	static class DocumentData implements Disposable {

		private final IFile file;
		
		private IFileEditorInput input;
		private IDocumentProvider documentProvider;

		public DocumentData(IFile file) {
			this.file = file;
		}
		
		private void init() {
			this.input = new FileEditorInput(file);
			this.documentProvider = DocumentProviderRegistry.getDefault().getDocumentProvider(input);
			if (this.documentProvider != null) {
				try {
					this.documentProvider.connect(input);		
					IDocument document = this.documentProvider.getDocument(input);
					// This step appears to be necessary to avoid having the current language server shutdown when disconnecting 
					// a document (see dispose()) and a new one start up again every time a user changes selection and no editor is open
					LanguageServers.forDocument(document).withFilter(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentSymbolProvider())).anyMatching();
//					LanguageServiceAccessor.getLanguageServers(document, capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentSymbolProvider())).get();
				} catch (Exception e) {
					Log.log(e);
				}
			}
		}

		@Override
		public void dispose() {
			if (input != null && documentProvider != null) {
				documentProvider.disconnect(input);
			}
		}

		/**
		 * May load the contents of the file into a document if there is no editor
		 * opened for the file therefore do not execute on the UI thread
		 * 
		 * @return the document
		 */
		public IDocument getDocument() {
			if (input == null) {
				init();
			}
			return this.documentProvider != null ? this.documentProvider.getDocument(input) : null;
		}

		@Override
		public String toString() {
			return "DocumentData [file=" + file + "]";
		}

	}
	
	private final LiveVariable<DocumentData> documentData = new OldValueDisposer<DocumentData>(this).getVar();
	{
		addDisposableChild(documentData);
	}
	
	private final LiveVariable<IResource> currentResource = new LiveVariable<>();
	{
		currentResource.onChange(this, (e, v) -> {
			IResource value = currentResource.getValue();
			if (value instanceof IFile) {
				documentData.setValue(new DocumentData((IFile) value));
			} else {
				documentData.setValue(null);
			}			
		});
	}
	
	public final LiveExpression<IProject> currentProject = currentResource.apply(r -> r == null ? null : r.getProject()); 

	private SelectionTracker(IWorkbenchWindow wbw) {
		ISelectionService selectionService = wbw.getSelectionService();
		ISelectionListener selectionListener = new ISelectionListener() {
			
			@Override
			public void selectionChanged(IWorkbenchPart arg0, ISelection selection) {
				if (selection instanceof IStructuredSelection) {
					IStructuredSelection ss = (IStructuredSelection) selection;
					Object element = ss.getFirstElement();
					IResource rsrc = getResource(element);
					if (rsrc != null) {
						currentResource.setValue(rsrc);
					}
				} else if (selection instanceof ITextSelection) {
					//Let's assume the selection is in the active editor
					try {
						IEditorPart editor = wbw.getActivePage().getActiveEditor();
						if (editor != null) {
							IEditorInput input = editor.getEditorInput();
							currentResource.setValue(input.getAdapter(IResource.class));
						}
					} catch (Exception e) {
						Log.log(e);
					}
				}
			}

			private IResource getResource(Object element) {
				if (element instanceof IResource) {
					return (IResource) element;
				} else if (element instanceof IAdaptable) {
					return ((IAdaptable) element).getAdapter(IResource.class);
				}
				return null;
			}
		};
		selectionService.addSelectionListener(selectionListener);
		onDispose(de -> {
			selectionService.removeSelectionListener(selectionListener);
		});
		wbw.getShell().addDisposeListener(de -> {
			disposeInstance(wbw);
		});
		
		//Code below tries to determine the 'initial' selection. 
		//Unfortunately, doesn't work. Mostly just getting null here, seems to be no way to get
		// 'initial' selection reliably.
		ISelection initialSelection = selectionService.getSelection();
		if (initialSelection != null) {
			selectionListener.selectionChanged(null, initialSelection);
		}
	}
	
	public LiveExpression<IResource> currentResource() {
		return currentResource;
	}

	public LiveExpression<DocumentData> getDocumentData() {
		return documentData;
	}
}
