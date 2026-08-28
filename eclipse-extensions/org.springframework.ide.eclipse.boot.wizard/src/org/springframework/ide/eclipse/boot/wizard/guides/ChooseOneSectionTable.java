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
package org.springframework.ide.eclipse.boot.wizard.guides;

import java.util.function.Predicate;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.springframework.ide.eclipse.boot.wizard.content.Describable;
import org.springframework.ide.eclipse.boot.wizard.util.StringMatchers;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.SelectionModel;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.ui.ChooseOneSectionCombo;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;

/**
 * Wizard section to choose one element from list of elements. Uses a table viewer to allow selecting
 * an element.
 * <p>
 * This class is very similar in functionality (from client's point of view) to {@link ChooseOneSectionCombo}.
 * It should be possible to use either one of these classes as functional replacements for one another.
 */
@SuppressWarnings("restriction")
public class ChooseOneSectionTable<T> extends ChooseOneSection {

	private class ChoicesFilter extends ViewerFilter {

		private Predicate<String> matcher = null;

		public ChoicesFilter() {
			if (searchBox != null) {
				setSearchTerm(searchBox.getText());
			}
		}

		public void setSearchTerm(String text) {
			matcher = StringMatchers.caseInsensitiveSubstring(text);
		}

		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (matcher == null) {
				return true;
			} else {
				String label = labelProvider.getText(element);
				if (match(label)) {
					return true;
				} else if (element instanceof Describable) {
					String description = ((Describable) element).getDescription();
					return match(description);
				}
				return false;
			}
		}

		private boolean match(String text) {
			if (matcher == null) {
				return true; // Search term not set... anything is acceptable.
			} else {
				return matcher.test(text);
			}
		}

	}

	private SelectionModel<T> selection;
	private String label; //Descriptive Label for this section
	private T[] options; //The elements to choose from
	private Text searchBox;
	private ChoicesFilter filter;

	public ChooseOneSectionTable(IPageWithSections owner, String label, SelectionModel<T> selection, T[] options) {
		super(owner);
		this.label = label;
		this.selection = selection;
		this.options = options;
	}


	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return selection.validator;
	}

	@Override
	public void createContents(Composite page) {
		Composite field = new Composite(page, SWT.NONE);
		int cols = label == null ? 1 : 2;
		GridLayout layout = GridLayoutFactory.fillDefaults().numColumns(cols).create();
		field.setLayout(layout);

		searchBox = new Text(field, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(searchBox);

		Label fieldNameLabel = null;
		if (label != null) {
			fieldNameLabel = new Label(field, SWT.NONE);
			fieldNameLabel.setText(label);
		}

		final TableViewer tv = new TableViewer(field, SWT.SINGLE|SWT.BORDER|SWT.V_SCROLL);
		tv.addFilter(filter = new ChoicesFilter());
		tv.setLabelProvider(labelProvider);
		tv.setContentProvider(ArrayContentProvider.getInstance());
		tv.setInput(options);

		if (fieldNameLabel != null) {
			GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.BEGINNING).applyTo(fieldNameLabel);
		}
		GridDataFactory grab = GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 150);
		grab.applyTo(field);
		grab.applyTo(tv.getTable());

		whenVisible(tv.getControl(), new Runnable() {
			@Override
			public void run() {
				T preSelect = selection.selection.getValue();
				if (preSelect != null) {
					tv.setSelection(new StructuredSelection(preSelect), true);
				} else {
					tv.setSelection(StructuredSelection.EMPTY, true);
				}
			}
		});

		tv.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			@SuppressWarnings("unchecked")
			public void selectionChanged(SelectionChangedEvent event) {
				ISelection sel = tv.getSelection();
				if (sel.isEmpty()) {
					selection.selection.setValue(null);
				} else if (sel instanceof IStructuredSelection){
					IStructuredSelection ss = (IStructuredSelection) sel;
					selection.selection.setValue((T)ss.getFirstElement());
				}
			}
		});

		searchBox.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				filter.setSearchTerm(searchBox.getText());
				tv.refresh();
			}
		});
	}

	private void whenVisible(final Control control, final Runnable runnable) {
		PaintListener l = new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				runnable.run();
				control.removePaintListener(this);
			}
		};
		control.addPaintListener(l);
	}

//	private String[] getLabels() {
//		String[] labels = new String[options.length];
//		for (int i = 0; i < labels.length; i++) {
//			labels[i] = labelProvider.getText(options[i]);
//		}
//		return labels;
//	}

}
