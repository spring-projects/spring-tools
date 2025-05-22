/*******************************************************************************
 * Copyright (c) 2017, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.springframework.tooling.ls.eclipse.gotosymbol.GotoSymbolPlugin;
import org.springframework.tooling.ls.eclipse.gotosymbol.favourites.FavouritesPreference;
import org.springsource.ide.eclipse.commons.core.util.FuzzyMatcher;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;
import org.springsource.ide.eclipse.commons.livexp.core.HighlightedText;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

@SuppressWarnings("restriction")
public class GotoSymbolDialogModel {
	
	public static class Favourite {
		public final String name; //Descriptive name 
		public final String query; //The 'search string' it corresponds to
		public Favourite(String name, String query) {
			super();
			this.name = name;
			this.query = query;
		}
		
		@Override
		public String toString() {
			return query + " ("+name+")";
		}
	}
	
	public static class Match<T> {
		final double score;
		final String query;
		final T value;
		final List<Match<T>> children;
		public Match(double score, String query, T value, List<Match<T>> children) {
			super();
			this.score = score;
			this.query = query;
			this.value = value;
			this.children = children;
		}
		
	}
	
	public static Comparator<Match<SymbolContainer>> MATCH_COMPARATOR = (m1, m2) -> {
		int comp = Double.compare(m2.score, m1.score);
		if (comp != 0) return comp;
		
		String m1Name = m1.value.getName();
		String m2Name = m2.value.getName();
		
		return m1Name.compareTo(m2Name);
	};

	private static final String SEARCH_BOX_HINT_MESSAGE = "@/ -> request mappings, @+ -> beans, @> -> functions, @ -> all spring elements";

	@FunctionalInterface
	public interface OKHandler {
		/**
		 * Called by the ui to perform the dialog's action. The dialog will be
		 * closed by the ui this returns true, otherwise it remains open.
		 */
		boolean performOk(SymbolContainer selection);
	}
	
	private static final OKHandler DEFAULT_OK_HANDLER = (selection) -> true;
	
	public static final OKHandler OPEN_IN_EDITOR_OK_HANDLER = symbolInformation -> {

		if (symbolInformation != null && symbolInformation.isSymbolInformation()) {
			Location location = symbolInformation.getSymbolInformation().getLocation();

			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			LSPEclipseUtils.openInEditor(location, page);
		}

		else if (symbolInformation != null && symbolInformation.isWorkspaceSymbol()) {
			Location location = null;
			Either<Location, WorkspaceSymbolLocation> symbolLocation = symbolInformation.getWorkspaceSymbol().getLocation();

			if (symbolLocation.isLeft()) {
				location = symbolLocation.getLeft();
			}
			else {
				WorkspaceSymbolLocation workspaceSymbolLocation = symbolLocation.getRight();
				location = new Location(workspaceSymbolLocation.getUri(), null);
			}

			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			LSPEclipseUtils.openInEditor(location, page);
		} else if (symbolInformation.isDocumentSymbol()) {
			ITextEditor editor = UI.getActiveTextEditor();
			if (editor != null) {
				IDocument doc = LSPEclipseUtils.getDocument(editor);
				if (doc != null) {
					try {
						Range range = symbolInformation.getDocumentSymbol().getSelectionRange();
						int offset = LSPEclipseUtils.toOffset(range.getStart(), doc);
						int endOffset = LSPEclipseUtils.toOffset(range.getEnd(), doc);
						editor.selectAndReveal(offset, endOffset - offset);
					} catch (BadLocationException e) {
						LanguageServerPlugin.logError(e);
					}
				}
			}
		}
		return true;
	};

	private final SymbolsProvider[] symbolsProviders;
	private final LiveVariable<HighlightedText> status = new LiveVariable<>();
	private final LiveVariable<String> searchBox = new LiveVariable<>("");
	
	private int currentSymbolsProviderIndex;

	public final LiveVariable<SymbolsProvider> currentSymbolsProvider = new LiveVariable<>(null);

	public final ObservableSet<SymbolContainer> unfilteredSymbols = new ObservableSet<SymbolContainer>(ImmutableSet.of(), AsyncMode.ASYNC, AsyncMode.SYNC) {
		//Note: fetching is 'slow' so is done asynchronously
		{
			setRefreshDelay(100);
			dependsOn(searchBox);
			dependsOn(currentSymbolsProvider);
		}
		
		@Override
		protected ImmutableSet<SymbolContainer> compute() {
			status.setValue(HighlightedText.plain("Fetching symbols..."));
			try {
				SymbolsProvider sp = currentSymbolsProvider.getValue();
				if (sp!=null) {
					String currentProviderName = sp.getName();
					String query = searchBox.getValue();
					Collection<SymbolContainer> fetched = sp.fetchFor(query);
					if (keyBindings==null) {
						status.setValue(HighlightedText.plain(currentProviderName));
					} else {
						status.setValue(HighlightedText
									.create()
									.appendHighlight("Showing ")
									.appendHighlight(currentProviderName)
									.appendPlain(". Press [" + keyBindings + "] for ")
									.appendPlain(nextSymbolsProvider().getName())
								);
					}
					return ImmutableSet.copyOf(fetched);
				} else {
					status.setValue(HighlightedText.plain("No symbol provider"));
				}
			} catch (Exception e) {
				GotoSymbolPlugin.getInstance().getLog().log(ExceptionUtil.status(e));
				status.setValue(HighlightedText.plain(ExceptionUtil.getMessage(e)));
			}
			return ImmutableSet.of();
		}

		private SymbolsProvider nextSymbolsProvider() {
			int nextIndex = (currentSymbolsProviderIndex + 1)%symbolsProviders.length;
			return symbolsProviders[nextIndex];
		}
	};
	
	private LiveExpression<Collection<Match<SymbolContainer>>> filteredSymbols = new LiveExpression<Collection<Match<SymbolContainer>>>() {
		//Note: filtering is 'fast' so is done synchronously
		{
			dependsOn(searchBox);
			dependsOn(unfilteredSymbols);
		}
		
		@Override
		protected Collection<Match<SymbolContainer>> compute() {
			String query = searchBox.getValue();
			if (!StringUtil.hasText(query)) {
				query = "";
			}
			query = query.toLowerCase();
			
			return computeMatches(unfilteredSymbols.getValues(), query);
		}
		private List<Match<SymbolContainer>> computeMatches(Collection<SymbolContainer> c, String query) {
			List<Match<SymbolContainer>> matches = new ArrayList<>();
			for (SymbolContainer symbol : c) {
				
				String name = symbol.getName();
				
				if (name != null) {
					name = name.toLowerCase();
				}
				
				double score = FuzzyMatcher.matchScore(query, name);
				List<Match<SymbolContainer>> childrenMatches = computeMatches(symbol.getChildren(), query);
				if (score != 0.0 || !childrenMatches.isEmpty()) {
					matches.add(new Match<SymbolContainer>(score, query, symbol, childrenMatches));
				}
			}
			Collections.sort(matches, MATCH_COMPARATOR);
			return ImmutableList.copyOf(matches);
		}
	};
	
	private String keyBindings;
	private OKHandler okHandler = DEFAULT_OK_HANDLER;

	private FavouritesPreference favourites = null;

	public GotoSymbolDialogModel(String keyBindings, SymbolsProvider... symbolsProviders) {
		this.keyBindings = keyBindings;
		Assert.isLegal(symbolsProviders.length>0);		
		this.symbolsProviders = symbolsProviders;
		this.currentSymbolsProviderIndex = 0;
		this.currentSymbolsProvider.setValue(symbolsProviders[0]);
	}
	
	public GotoSymbolDialogModel setFavourites(FavouritesPreference favourites) {
		this.favourites = favourites;
		return this;
	}
	
	public FavouritesPreference getFavourites() {
		return favourites;
	}

	public LiveExpression<Collection<Match<SymbolContainer>>> getSymbols() {
		return filteredSymbols;
	}

	public LiveVariable<String> getSearchBox() {
		return searchBox;
	}
	
	public String getSearchBoxHintMessage() {
		return SEARCH_BOX_HINT_MESSAGE;
	}

	public LiveExpression<HighlightedText> getStatus() {
		return status;
	}
	
	public synchronized void toggleSymbolsProvider() {
		currentSymbolsProviderIndex = (currentSymbolsProviderIndex+1)%symbolsProviders.length;
		currentSymbolsProvider.setValue(symbolsProviders[currentSymbolsProviderIndex]);
	}
	
	public SymbolsProvider[] getSymbolsProviders() {
		return symbolsProviders;
	}

	/**
	 * Set an ok handler. The handler is meant to be called by the UI when user request to
	 * execute the dialogs action on its current selection. For example, by pressing 'ENTER'
	 * key, or by double-clicking an element.
	 * <p>
	 * If 'okHandler' returns 'true' then the dialog is closed. Otherwise it remains open. 
	 */
	public GotoSymbolDialogModel setOkHandler(OKHandler okHandler) {
		this.okHandler = okHandler == null ? DEFAULT_OK_HANDLER : okHandler;
		return this;
	}
	
	public boolean performOk(SymbolContainer selection) {
		return this.okHandler.performOk(selection);
	}

	public boolean fromFileProvider(SymbolContainer symbolInformation) {
		SymbolsProvider sp = currentSymbolsProvider.getValue();

		if (sp != null) {
			return sp.fromFile(symbolInformation);
		}
		return false;
	}
}
