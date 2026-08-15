/*******************************************************************************
 * Copyright (c) 2016, 2026 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Kris De Volder (Pivotal) - copied and adapted from org.eclipse.lsp4e.operations.symbols.LSPSymbolInFileDialog 
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolDialog;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolDialogModel;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.InFileSymbolsProvider;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.InProjectSymbolsProvider;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.InWorkspaceSymbolsProvider;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.SymbolsProviderScopePreference;

@SuppressWarnings("restriction")
public class GotoSymbolHandler extends AbstractHandler {
	
	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}
	private static GotoSymbolDialogModel currentDialog = null;

	public GotoSymbolHandler() {
		debug("Creating GotoSymbolHandler");
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		debug(">>>GotoSymbolHandler.execute");
		if (currentDialog!=null) {
			debug("GotoSymbolDialog already open: send 'toggle' signal");
			currentDialog.toggleSymbolsProvider();
		} else {
			IWorkbenchPart part = getActiveWorkbenchPart(HandlerUtil.getActiveEditor(event));
			if (part instanceof ITextEditor) {
				final Shell shell = HandlerUtil.getActiveShell(event);
				final ITextEditor textEditor = (ITextEditor) part;

				int lastSymbolsProviderIndex = SymbolsProviderScopePreference.INSTANCE.getIndex();
				GotoSymbolDialogModel model = new GotoSymbolDialogModel(getKeybindings(event), lastSymbolsProviderIndex, InWorkspaceSymbolsProvider.createFor(event), InProjectSymbolsProvider.createFor(event), InFileSymbolsProvider.createFor(textEditor))
				.setOkHandler(GotoSymbolDialogModel.OPEN_IN_EDITOR_OK_HANDLER);
				GotoSymbolDialog dialog = new GotoSymbolDialog(shell, textEditor, model, /*alignRight*/ false);
				currentDialog = model;
				dialog.open();
				debug("GotoSymbolDialog opened");
				dialog.getShell().addDisposeListener(de -> {
					debug("GotoSymbolDialog closed!");
					SymbolsProviderScopePreference.INSTANCE.setIndex(model.getCurrentSymbolsProviderIndex());
					currentDialog = null;
				});
			}
			debug("<<<GotoSymbolHandler.execute");
		}
		
		return null;
	}

	private String getKeybindings(ExecutionEvent event) {
		Command invokingCommand = event.getCommand();
		IBindingService service = PlatformUI.getWorkbench().getService(IBindingService.class);
		TriggerSequence[] bindings = service.getActiveBindingsFor(invokingCommand.getId());
		if (bindings!=null && bindings.length>0) {
			return bindings[0].toString();
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		boolean r = _isEnabled();
		return r;
	}
	public boolean _isEnabled() {
		IWorkbenchPart part = getActiveWorkbenchPart(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart());
		if (part instanceof ITextEditor) {
			debug("activePart instanceof ITextEditor");
			List<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
					LSPEclipseUtils.getDocument((ITextEditor) part),
					capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentSymbolProvider()));
			return !infos.isEmpty();
		}
		debug("activePart not ITextEditor: "+part);
		return false;
	}
	
	private static IWorkbenchPart getActiveWorkbenchPart(IWorkbenchPart part) {
		if (part instanceof MultiPageEditorPart) {
			MultiPageEditorPart multipageEditor = (MultiPageEditorPart) part;
			Object page = multipageEditor.getSelectedPage();
			if (page instanceof IWorkbenchPart) {
				part = (IWorkbenchPart) page;
			}			
		}
		return part;
	}
}
