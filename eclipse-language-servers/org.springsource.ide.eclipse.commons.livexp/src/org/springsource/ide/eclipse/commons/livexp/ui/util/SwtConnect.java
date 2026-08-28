/*******************************************************************************
 * Copyright (c) 2017, 2021 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui.util;

import java.time.Duration;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.springsource.ide.eclipse.commons.livexp.core.HighlightedText;
import org.springsource.ide.eclipse.commons.livexp.core.HighlightedText.Style;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.livexp.util.Filters;

/**
 * Convenience methods to attach LiveExp / LiveVar model elements to SWT widgets.
 */
public class SwtConnect {
	
	/**
	 * 
	 * @param text
	 * @param model
	 * @param cursorAtEnd true if cursor has to be placed at the end of text when its set in the control
	 */
	public static void connect(Text text, LiveVariable<String> model, boolean cursorAtEnd) {
		if (!text.isDisposed()) {
			text.addDisposeListener(de -> model.dispose());
			ModifyListener widgetListener = (me) -> {
				if (!text.isDisposed()) {
					model.setValue(text.getText());
				}
			};
			text.addModifyListener(widgetListener);
			Disposable disconnect = model.onChange(UIValueListener.from((e,v) -> {
				String oldText = text.getText();
				String newText = model.getValue();
				if (newText == null) {
					newText = "";
				}
				if (!oldText.equals(newText)) {
					text.setText(newText);
					if (cursorAtEnd) {
						text.setSelection(text.getText().length());
						text.setFocus();
					}
				}
			}));
			text.addDisposeListener(de -> disconnect.dispose());
			model.onDispose(de -> text.removeModifyListener(widgetListener));
		}
	}
	
	public static void connectEnablement(Control control, LiveExpression<Boolean> enabler) {
        if (!control.isDisposed()) {
            control.setEnabled(enabler.getValue());
            control.addDisposeListener(de -> enabler.dispose());
            Disposable disconnect = enabler.onChange(UIValueListener.from((e,v) -> {
                control.setEnabled(e.getValue());
            }));
            control.addDisposeListener(de -> disconnect.dispose());
        }
	}
	
	public static void connect(Text text, LiveVariable<String> model) {
		connect(text, model, false);
	}
	
	
	/**
	 * Connect a filterbox model to a treeviewer. This assumes that the filter is text-based. The filter is applied to the labels of the elements in the tree.
	 * <p>
	 * For the viewer filter to work correctly the ITreeContentProvider must provide a proper implementation of the 'getParent' method. If getParent only
	 * returns null the viewer filter will not be able to check whether an element should be shown when a parent element is selected by the search filter.
	 * <p>
	 * Note: you can use {@link TreeElementWrappingContentProvider} in order to ensure that ITreeContentProvider keeps track of parent nodes properly.
	 */
	public static void connectTextBasedFilter(TreeViewer viewer, LiveExpression<Filter<String>> searchBoxModel, LabelProvider labels, ITreeContentProvider treeContent) {
 		TreeAwareViewerFilter viewerFilter = new TreeAwareViewerFilter(viewer, Filters.acceptAll(), labels, treeContent);
		Disposable disposable = searchBoxModel.onChange(UIValueListener.from((e, filter) -> {
			viewerFilter.setFilter(searchBoxModel.getValue());
			viewer.refresh(true);
		}));
		viewer.setFilters(viewerFilter); //TODO: what if there are existing filters?
		viewer.getControl().addDisposeListener(de -> {
			disposable.dispose();
		});
		Stylers stylers = new Stylers(viewer.getTree().getFont());
		viewer.getControl().addDisposeListener(de -> {
			disposable.dispose();
			stylers.dispose();
		});
		ILabelProvider baseLabels = (ILabelProvider) viewer.getLabelProvider();
		Assert.isNotNull(baseLabels); //Can't add bolding support without this! Ensure label provider is set before calling this method
		
		viewer.setLabelProvider(boldMatchedElements(stylers, baseLabels, Filters.delegatingTo(searchBoxModel)));
	}
	
	/**
	 * Decorate a basic LabelProvider so that it bolds matched elements based on a text-based filter applied to its labels.
	 */
	public static StyledCellLabelProvider boldMatchedElements(Stylers stylers, ILabelProvider baseLabels, Filter<String> filter) {
		return new StyledCellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Object element = cell.getElement();
				
				//image
				cell.setImage(baseLabels.getImage(element));
				
				//styled label
				String label = baseLabels.getText(element);
				StyledString styledLabel = new StyledString(label);
				if (filter.accept(label)) {
					Styler bold = stylers.bold();
					for (IRegion r : filter.getHighlights(label)) {
						styledLabel.setStyle(r.getOffset(), r.getLength(), bold);
					}
				}
				cell.setStyleRanges(styledLabel.getStyleRanges());
				cell.setText(styledLabel.getString());
				cell.getControl().redraw(); 
								//^^^ Sigh... Yes, this is needed. It seems SWT/Jface isn't smart enough to itself figure out that if 
								//the styleranges change a redraw is needed to make the change visible.
			}
			
		};
	}

	public static void connect(Label widget, LiveExpression<String> model) {
		ValueListener<String> modelListener = new UIValueListener<String>() {
			@Override
			protected void uiGotValue(LiveExpression<String> exp, String value) {
				String newText = model.getValue();
				if (newText == null) {
					newText = "";
				}
				if (!widget.isDisposed()) {
					widget.setText(newText);
				}
			}
		};
		model.addListener(modelListener);
		widget.addDisposeListener(xx -> model.removeListener(modelListener));
	}
	
	public static void connectHighlighted(Styler highlightStyle, StyledText widget, LiveExpression<HighlightedText> model) {
		ValueListener<HighlightedText> modelListener = new UIValueListener<HighlightedText>() {
			@Override
			protected void uiGotValue(LiveExpression<HighlightedText> exp, HighlightedText value) {
				HighlightedText highlightedText = model.getValue();
				StyledString newText = new StyledString("", Stylers.NULL);
				if (highlightedText != null) {
					StyledString styledString = new StyledString();
					highlightedText.build().stream().forEach(segment -> {
						if (segment.getStyle() == Style.HIGHLIGHT) {
							styledString.append(segment.getText(), highlightStyle);
						} else {
							styledString.append(segment.getText(), Stylers.NULL);
						}
					});
					newText = styledString;
				}
				if (!widget.isDisposed()) {
					widget.setText(newText.getString());
					// IMPORTANT: set style ranges AFTER the text, not before, otherwise SWT may
					// throw exception
					widget.setStyleRanges(newText.getStyleRanges());
				}
			}

		};
		model.addListener(modelListener);
		widget.addDisposeListener(xx -> model.removeListener(modelListener));
	}

	public static void connect(Label widget, LiveExpression<String> model, Duration delay) {
		if (delay == null || delay.isZero() || delay.isNegative()) {
			connect(widget, model);
		} else {
			LiveExpression<String> delayedModel = model.delay(delay);
			widget.addDisposeListener(de -> delayedModel.dispose());
			connect(widget, delayedModel);
		}
	}
	
	public static void connectHighlighted(Styler highlightStyle, StyledText widget, LiveExpression<HighlightedText> model, Duration delay) {
		if (delay == null || delay.isZero() || delay.isNegative()) {
			connectHighlighted(highlightStyle, widget, model);
		} else {
			LiveExpression<HighlightedText> delayedModel = model.delay(delay);
			widget.addDisposeListener(de -> delayedModel.dispose());
			connectHighlighted(highlightStyle, widget, delayedModel);
		}
	}

	public static void checkbox(Button checkbox, LiveVariable<Boolean> model) {
		ValueListener<Boolean> modelListener = new UIValueListener<Boolean>() {
			@Override
			protected void uiGotValue(LiveExpression<Boolean> exp, Boolean value) {
				Boolean newValue = model.getValue();
				boolean select = newValue != null && newValue;
				if (!checkbox.isDisposed()) {
					checkbox.setSelection(select);
				}
			}
		};
		model.addListener(modelListener);
		SelectionListener widgetListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				model.setValue(checkbox.getSelection());
			}
		};
		checkbox.addSelectionListener(widgetListener);
		checkbox.addDisposeListener(xx -> model.removeListener(modelListener));
	}
}
