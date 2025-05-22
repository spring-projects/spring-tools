/*******************************************************************************
 * Copyright (c) 2016, 2022 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Kris De Volder (Pivotal Inc) - Copied and adapted 
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolDialogModel.Favourite;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolDialogModel.Match;
import org.springframework.tooling.ls.eclipse.gotosymbol.favourites.FavouritesPreference;
import org.springsource.ide.eclipse.commons.core.util.FuzzyMatcher;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.util.SwtConnect;

@SuppressWarnings("restriction")
public class GotoSymbolSection extends WizardPageSection {

	private static class SymbolsContentProvider implements ITreeContentProvider {
		
		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof Match<?> m) {
				return m.children.toArray(new Object[m.children.size()]);
			}
			return null;
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			if (element instanceof Match<?> m ) {
				return !m.children.isEmpty();
			}
			return false;
		}

		@Override
		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof GotoSymbolDialogModel) {
				GotoSymbolDialogModel model = (GotoSymbolDialogModel) inputElement;
				return model.getSymbols().getValue().toArray();
			}
			return null;
		}
	}
	
	private  class GotoSymbolsLabelProvider extends StyledCellLabelProvider {
		
		private Stylers stylers;
		private SymbolsLabelProvider symbolsLabelProvider;
		
		public GotoSymbolsLabelProvider(Font base) {
			stylers = new Stylers(base);
			boolean showSymbolsLabelProviderLocation  = false; /* dont show full location. we show relative location in our own implementation below */
			boolean showKindInformation = false;
			
			symbolsLabelProvider = new SymbolsLabelProvider(showSymbolsLabelProviderLocation, showKindInformation) {
				@Override
				protected int getMaxSeverity(IResource resource, IDocument doc, Range range)
						throws CoreException, BadLocationException {
					int maxSeverity = -1;
					for (IMarker marker : resource.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
						int offset = marker.getAttribute(IMarker.CHAR_START, -1);
						if (offset != -1) {
							maxSeverity = Math.max(maxSeverity, marker.getAttribute(IMarker.SEVERITY, -1));
						}
					}
					return maxSeverity;
				}
			};
		}
		
		@Override
		public Color getToolTipBackgroundColor(Object object) {
			return JFaceColors.getInformationViewerBackgroundColor(Display.getDefault());
		}
		
		@Override
		public Color getToolTipForegroundColor(Object object) {
			return JFaceColors.getInformationViewerForegroundColor(Display.getDefault());
		}
		
		@Override
		public String getToolTipText(Object element) {
			if (element instanceof Match) {
				SymbolContainer symbol = getSymbolContainer((Match<?>)element);
				if (symbol != null) {
					return symbol.getName();
				}
			}
			return null;
		}
	
		@Override
		public void update(ViewerCell cell) {
			super.update(cell);
			Object obj = cell.getElement();
			
			if (obj instanceof Match) {
				Match<?> match = (Match<?>) obj;
				cell.setImage(getImage(match.value));
				StyledString styledString = getStyledText(match);
				cell.setText(styledString.getString());
				cell.setStyleRanges(styledString.getStyleRanges());
				cell.getControl().redraw(); 
				//^^^ Sigh... Yes, this is needed. It seems SWT/Jface isn't smart enough to itself figure out that if 
				//the styleranges change a redraw is needed to make the change visible.
			} else {
				super.update(cell);
			}
		}

		private StyledString getStyledText(Match<?> element) {
			SymbolContainer symbol = getSymbolContainer(element);

			if (symbol != null) {
				String name = symbol.getName();
				StyledString s = new StyledString(name);
				Collection<IRegion> highlights = FuzzyMatcher.highlights(element.query, name.toLowerCase());

				for (IRegion hl : highlights) {
					s.setStyle(hl.getOffset(), hl.getLength(), stylers.bold());
				}

				String locationText = getSymbolLocationText(symbol);
				if (locationText != null) {
					s = s.append(locationText, stylers.italicColoured(SWT.COLOR_DARK_GRAY));
				}
				return s;
			} else {
				return null;
//				Object symbolObject = symbol.get();
//				return symbolsLabelProvider.getStyledText(symbolObject);
			}
		}
		
		/**
		 * this is a workaround because LSP4Es symbol label provider does not support workspace symbols yet.
		 * Once LSP4Es symbol label provider has support for workspace symbols, this can be removed again
		 * and replaced by a simple call to LSP4E label provider.getImage(SymbolContainer.get())
		 */
		private Image getImage(Object element) {
			if (element instanceof SymbolContainer) {
				SymbolContainer container = (SymbolContainer) element;
				if (container.isSymbolInformation()) {
					return symbolsLabelProvider.getImage(container.getSymbolInformation());
				}
				else if (container.isDocumentSymbol()) {
					return symbolsLabelProvider.getImage(container.getDocumentSymbol());
				}
				else if (container.isWorkspaceSymbol()) {
					WorkspaceSymbol workspaceSymbol = container.getWorkspaceSymbol();
					
					Location location = workspaceSymbol.getLocation().isLeft() ? workspaceSymbol.getLocation().getLeft() : new Location();
					SymbolInformation tempSymbol = new SymbolInformation(workspaceSymbol.getName(), workspaceSymbol.getKind(), location, workspaceSymbol.getContainerName());
					return symbolsLabelProvider.getImage(tempSymbol);
				}
				else {
					return null;
				}
			}
			else {
				return symbolsLabelProvider.getImage(element);
			}
		}

		@Override
		public void dispose() {
			stylers.dispose();
			symbolsLabelProvider.dispose();
			super.dispose();
		}
		
		protected String getSymbolLocationText(SymbolContainer symbol) {
			Optional<String> location = GotoSymbolSection.this.getSymbolLocation(symbol);
			if (location.isPresent()) {
				return " -- [" + location.get() + "]";
			}
			return null;
		}
	}

	private final GotoSymbolDialogModel model;
	private boolean enableStatusLine = true;

	public GotoSymbolSection(IPageWithSections owner, GotoSymbolDialogModel model) {
		super(owner);
		this.model = model;
	}

	@Override
	public void createContents(Composite dialogArea) {
		List<Disposable> disposables = new ArrayList<>();
		dialogArea.addDisposeListener(de -> {
			for (Disposable d : disposables) {
				d.dispose();
			}
		});	
		
		//Favourites pulldown composite
		Composite sbComposite = dialogArea;
		if (model.getFavourites()!=null) {
			sbComposite = new Composite(dialogArea, SWT.NONE);
			GridLayout layout = new GridLayout(2, false);
			layout.marginWidth = 0; layout.marginHeight = 0;
			sbComposite.setLayout(layout);
			GridDataFactory.fillDefaults().grab(true, false).applyTo(sbComposite);
		}
		
		//Search box:
		Text pattern = new Text(sbComposite, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
//		pattern.getAccessible().addAccessibleListener(new AccessibleAdapter() {
//			public void getName(AccessibleEvent e) {
//				e.result = LegacyActionTools.removeMnemonics(headerLabel)
//						.getText());
//			}
//		});
		pattern.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		pattern.setMessage(model.getSearchBoxHintMessage());
		SwtConnect.connect(pattern, model.getSearchBox(), true);
		
		//Favourites pulldown
		if (model.getFavourites()!=null) {
			createFavouritesPulldown(sbComposite, model.getFavourites(), model.getSearchBox());
		}

		//Tree viewer with results
		TreeViewer viewer = new TreeViewer(dialogArea, SWT.SINGLE);
		ColumnViewerToolTipSupport.enableFor(viewer);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(viewer.getControl());
		viewer.setContentProvider(new SymbolsContentProvider());
		viewer.setLabelProvider(new GotoSymbolsLabelProvider(viewer.getTree().getFont()));
		viewer.setUseHashlookup(true);
		viewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS);
		disposables.add(model.getSymbols().onChange(UIValueListener.from((e, v) -> {
			if (!viewer.getControl().isDisposed()) {
				viewer.refresh();
				viewer.expandToLevel(AbstractTreeViewer.ALL_LEVELS);
			}
		})));
		
		installWidgetListeners(pattern, viewer);

		//Status label
		if (enableStatusLine) {
			StyledText statusLabel = new StyledText(dialogArea, SWT.NONE);
			// Allow for some extra space for highlight fonts
			statusLabel.setLeftMargin(3);
			statusLabel.setBottomMargin(2);
			statusLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			
			Stylers stylers = new Stylers(dialogArea.getFont());
			disposables.add(stylers);
			
			SwtConnect.connectHighlighted(stylers.bold(), statusLabel, model.getStatus(), Duration.ofMillis(500));
		}
		
		viewer.setInput(model);
	}

	private void createFavouritesPulldown(Composite parent, FavouritesPreference favouritePrefs, LiveVariable<String> searchBox) {
		Button btn = new Button(parent, SWT.ARROW | SWT.DOWN);
		btn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);
				Menu menu = new Menu(btn);
				// create item for each of the known favourites
				Favourite[] favourites = favouritePrefs.getFavourites();
				Set<String> existingFavs = new HashSet<>();
				for (Favourite f : favourites) {
					MenuItem item = new MenuItem(menu, SWT.PUSH);
					item.setText(f.toString());
					item.addSelectionListener(new SelectionAdapter() {
						public void widgetSelected(SelectionEvent e) {
							searchBox.setValue(f.query);
						}
					});
					existingFavs.add(f.query);
				}
				//separator 
				new MenuItem(menu, SWT.SEPARATOR);
				
				String currentSearch = searchBox.getValue();
				
				if (StringUtil.hasText(currentSearch)) {
					if (!existingFavs.contains(currentSearch)) {
						//create a 'add favourite' menu item
						MenuItem item = new MenuItem(menu, SWT.PUSH);
						item.setText("Add Favourite...");
						item.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								String name = inputDialog("Add '"+currentSearch+"' as a favourite", "Name:", "");
								if (StringUtil.hasText(name)) {
									favouritePrefs.add(name, currentSearch);
								}
							}
						});
					} else { // the currentSearch is already a favourite
						//create a 'remove favourite' menu item
						MenuItem item = new MenuItem(menu, SWT.PUSH);
						item.setText("Remove '"+currentSearch+"' Favourite");
						item.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								favouritePrefs.remove(currentSearch);
							}
						});
					}
				}
				
				Point loc = btn.getLocation();
				Rectangle rect = btn.getBounds();
				Point mLoc = new Point(loc.x-1, loc.y+rect.height);
				menu.setLocation(btn.getDisplay().map(btn.getParent(), null, mLoc));
				menu.setVisible(true);
			}
		});		
	}
	
	private String inputDialog(String dialogTitle, String prompt, String defaultValue) {
		AtomicReference<String> result = new AtomicReference<>();
		owner.getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				InputDialog dlg = new InputDialog(owner.getShell(), dialogTitle, prompt, defaultValue, null);
				int code = dlg.open();
				if (code == IDialogConstants.OK_ID) {
					result.set(dlg.getValue());
				}
			}
		});
		return result.get();
	}

	private void installWidgetListeners(Text pattern, TreeViewer list) {
		pattern.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.ARROW_DOWN) {
					if (list.getTree().getItemCount() > 0) {
						list.getTree().setFocus();
						TreeItem[] items = list.getTree().getItems();
						if (items != null && items.length > 0) {
							list.getTree().setSelection(items[0]);
							//programatic selection may not fire selection events so...
							list.getTree().notifyListeners(SWT.Selection,
									new Event());
						}
					}
				} else if (e.character == '\r') {
					performOk(list);
				}
			}
		});

		list.getTree().addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {

				if (e.keyCode == SWT.ARROW_UP && (e.stateMask & SWT.SHIFT) == 0
						&& (e.stateMask & SWT.CTRL) == 0) {
					StructuredSelection selection = (StructuredSelection) list
							.getSelection();

					if (selection.size() == 1) {
						Object element = selection.getFirstElement();
						if (element.equals(getFirstElement(list))) {
							pattern.setFocus();
							list.setSelection(new StructuredSelection());
							list.getTree().notifyListeners(SWT.Selection,
									new Event());
						}

					}
				} else if (e.character == '\r') {
					performOk(list);
				}

//				if (e.keyCode == SWT.ARROW_DOWN
//						&& (e.stateMask & SWT.SHIFT) != 0
//						&& (e.stateMask & SWT.CTRL) != 0) {
//
//					list.getTree().notifyListeners(SWT.Selection, new Event());
//				}

			}
		});
		
		list.addDoubleClickListener(e -> performOk(list));
	}
	
	private void performOk(TreeViewer list) {
		if (model.performOk(getTarget(list))) {
			close();
		}
	}

	/**
	 * Determine the 'target' for the dialog's action.
	 */
	private SymbolContainer getTarget(TreeViewer list) {
		ISelection sel = list.getSelection();
		
		if (sel instanceof IStructuredSelection) {

			IStructuredSelection ss = (IStructuredSelection) sel;
			Object selected = ss.getFirstElement();

			if (selected instanceof Match) {
				SymbolContainer symbol = getSymbolContainer((Match<?>) selected);
				if (symbol != null) {
					return symbol;
				}
			}
		}
		//No element selected, target the first element in the list instead.
		//This allows user to execute the action without explicitly selecting an element.
		return getFirstElement(list);
	}

	private SymbolContainer getFirstElement(TreeViewer list) {
		TreeItem[] items = list.getTree().getItems();

		if (items != null && items.length > 0) {
			TreeItem item = items[0];
			Object data = item.getData();

			if (data instanceof Match) {
				SymbolContainer symbol = getSymbolContainer((Match<?>) data);
				if (symbol != null) {
					return symbol;
				}
			}
		}
		return null;
	}

	private SymbolContainer getSymbolContainer(Match<?> element) {
		if (element.value instanceof SymbolContainer) {
			return (SymbolContainer) element.value;
		}
		return null;
	}
	
	private Optional<String> getSymbolLocation(SymbolContainer symbolInformation) {
		String val = null;

		if (!model.fromFileProvider(symbolInformation)) {
			String uri = null;
			if (symbolInformation.isSymbolInformation()) {
				uri = symbolInformation.getSymbolInformation().getLocation().getUri();
			}
			else if (symbolInformation.isWorkspaceSymbol()) {
				Either<Location, WorkspaceSymbolLocation> location = symbolInformation.getWorkspaceSymbol().getLocation();
				if (location.isLeft()) {
					uri = location.getLeft().getUri();
				}
				else {
					location.getRight().getUri();
				}
			}
			
			IResource targetResource = LSPEclipseUtils.findResourceFor(uri);
			if (targetResource != null && targetResource.getFullPath() != null) {
				val = targetResource.getFullPath().toString();
			}	
		}

		return val != null ? Optional.of(val) : Optional.empty();
	}
	

	/**
	 * Enable or disable displaying status line at the bottom of the goto symbols view/section.
	 */
	public GotoSymbolSection enableStatusLine(boolean enable) {
		this.enableStatusLine = enable;
		return this;
	}
	
}
