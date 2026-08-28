/*******************************************************************************
 * Copyright (c) 2013, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.CommentSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.ui.HtmlTooltip;

/**
 * A section that shows a series of checkboxes in rows and columns.
 * <p>
 * The section has an optional label. If the label is provided then
 * the section will be created as a labeled group with a bounding border
 * around it. Otherwise a plain composite will be used to host the
 * rows/columns of checkboxes.
 *
 * @author Kris De Volder
 */
public class CheckBoxesSection<T> extends WizardPageSection {

	public static class CheckBoxModel<T> {
		private final String label;
		private final LiveVariable<Boolean> selection;
		private final LiveExpression<Boolean> enablement;
		private final T value;
		private Supplier<String> tooltipHtml; //optional HTML tooltip supplier
		private String requirementTooltip; //optional, a user-readable string explaining the requirements (this can be shown as a explanation to the user for a disabled checkbox).
		public CheckBoxModel(String label, T value, LiveVariable<Boolean> selection, LiveExpression<Boolean> enablement) {
			this.label = label;
			this.value = value;
			this.selection = selection;
			this.enablement = enablement;
		}
		public String getLabel() {
			return label;
		}
		public LiveVariable<Boolean> getSelection() {
			return selection;
		}
		public LiveExpression<Boolean> getEnablement() {
			return enablement;
		}
		public Supplier<String> getTooltipHtml() {
			return tooltipHtml;
		}
		public void setTooltipHtml(Supplier<String> tooltipHtml) {
			this.tooltipHtml = tooltipHtml;
		}
		public T getValue() {
			return value;
		}
		@Override
		public String toString() {
			return "CheckBox("+label+", "+getSelection().getValue()+")" ;
		}
		public String getRequirementTooltip() {
			return requirementTooltip;
		}
		public void setRequirementTooltip(String requirementTooltip) {
			this.requirementTooltip = requirementTooltip;
		}
	}

	private List<CheckBoxModel<T>> model;
	private Composite composite;
	private WizardPageSection[] subsections;
	private int numCols;
	private String label;

	public CheckBoxesSection(IPageWithSections owner, List<CheckBoxModel<T>> model, String label) {
		super(owner);
		this.label = label;
		this.model = model;
	}

	public CheckBoxesSection(IPageWithSections owner, List<CheckBoxModel<T>> model) {
		this(owner, model, /*label*/null);
	}

	public CheckBoxesSection<T> columns(int howMany) {
		Assert.isLegal(howMany > 0);
		this.numCols = howMany;
		return this;
	}

	private static class CheckBox<T> extends WizardPageSection {

		private Button cb;
		private CheckBoxModel<T> model;
		private LiveVariable<Boolean> isVisible = new LiveVariable<>(true);
		private ValueListener<Boolean> selectionListener;
		private ValueListener<Boolean> enablementListener;

		public CheckBox(IPageWithSections owner, CheckBoxModel<T> model) {
			super(owner);
			this.model = model;
		}

		@Override
		public LiveExpression<ValidationResult> getValidator() {
			return Validator.OK;
		}

		@Override
		public void createContents(Composite page) {
			if (page != null && !page.isDisposed()) {
				Composite composite = new Composite(page, SWT.NONE);
				composite.setLayout(new FillLayout());
				this.cb = new Button(composite, SWT.CHECK);
				cb.setText(model.getLabel());

				Supplier<String> html = model.getTooltipHtml();
				if (html != null) {
					// Setup HTML tooltip and its content
					{
						HtmlTooltip tooltip = new HtmlTooltip(cb);
						tooltip.setMaxSize(400, 400);
						tooltip.setHtml(html);
					}
					String requirements = model.getRequirementTooltip();
					if (StringUtil.hasText(requirements)) {
						//The tooltip on the composite will only be shown when the checkbox widget itself is disabled:
						//See https://stackoverflow.com/questions/39096291/tooltip-is-not-visible-for-a-disabled-control-in-java-swt
						composite.setToolTipText(requirements);
					}
				}

				model.getSelection().addListener(selectionListener = new ValueListener<Boolean>() {
					@Override
					public void gotValue(LiveExpression<Boolean> exp, Boolean value) {
						// note: checkbox button may be null in cases where the
						// model keeps changing in the container section and the selection
						// listener is notified after the button was disposed
						if (value != null && cb != null && !cb.isDisposed()) {
							cb.setSelection(value);
						}
					}
				});
				GridDataFactory.fillDefaults().grab(true, false).applyTo(composite);
				cb.addSelectionListener(new SelectionListener() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						handleSelection();
					}

					@Override
					public void widgetDefaultSelected(SelectionEvent e) {
						handleSelection();
					}

					private void handleSelection() {
						model.getSelection().setValue(cb.getSelection());
					}
				});
				LiveExpression<Boolean> enablement = model.getEnablement();
				if (enablement != null) {
					enablement.addListener(enablementListener = new ValueListener<Boolean>() {
						@Override
						public void gotValue(LiveExpression<Boolean> exp, Boolean value) {
							if (value != null) {
								cb.setEnabled(value);
							}
						}
					});
				}
				isVisible.addListener(new ValueListener<Boolean>() {
					@Override
					public void gotValue(LiveExpression<Boolean> exp, Boolean reveal) {
						if (reveal != null && composite != null && !composite.isDisposed()) {
							composite.setVisible(reveal);
							GridData data = (GridData) composite.getLayoutData();
							data.exclude = !reveal;
						}
					}
				});
			}
		}

		@Override
		public void dispose() {
			if (cb != null && !cb.isDisposed()) {
				cb.dispose();
				cb = null;
			}
			if (selectionListener != null) {
				model.getSelection().removeListener(selectionListener);
			}
			if (enablementListener != null) {
				model.getEnablement().removeListener(enablementListener);
			}
		}

		/**
		 * Apply filter and return whether this widget's visibility has changed as a result.
		 * @return Whether visibility of this widget changed.
		 */
		public boolean applyFilter(Filter<T> filter) {
			boolean wasVisible = isVisible.getValue();
			isVisible.setValue(filter.accept(model.getValue()));
			boolean changed = wasVisible != isVisible.getValue();
			return changed;
		}

		public boolean isVisible() {
			return isVisible.getValue();
		}

		@Override
		public String toString() {
			return "CheckBox("+model.getLabel()+")";
		}
	}

	protected GridLayout createLayout() {
		return new GridLayout(numCols, true);
	}

//	@Override
//	public LiveExpression<ValidationResult> getValidator() {
//		return model.getValidator();
//	}

	@Override
	public void createContents(Composite page) {
		composite = createComposite(page);
		GridLayout layout = createLayout();
		layout.marginHeight = 0;
		composite.setLayout(layout);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(composite);
		createSubsections();
	}

	private void createSubsections() {
		subsections = new WizardPageSection[Math.max(1, model.size())];

//		 GridData gd = (GridData) group.getLayoutData();
//		 boolean visible = checkboxes.length>0;
//		 gd.exclude = !visible;

		if (model.isEmpty()) {
			// don't leave section empty it looks ugly
			subsections[0] = new CommentSection(owner, "No choices available");
			subsections[0].createContents(composite);
		}

		for (int i = 0; i < model.size(); i++) {
			subsections[i] = new CheckBox<>(owner, model.get(i));
			subsections[i].createContents(composite);
		}
	}

	protected Composite createComposite(Composite page) {
		if (this.label != null) {
			Group comp = new Group(page, SWT.NONE);
			comp.setText(label);
			return comp;
		} else {
			return new Composite(page, SWT.NONE);
		}
	}

	public boolean applyFilter(Filter<T> filter) {
		if (subsections != null) {
			boolean visibilityChanged = false;
			for (WizardPageSection subsection : subsections) {
				if (subsection instanceof CheckBox) {
					@SuppressWarnings("unchecked")
					CheckBox<T> cb = (CheckBox<T>) subsection;
					visibilityChanged |= cb.applyFilter(filter);
				}
			}
			if (visibilityChanged) {
				composite.layout(true);
			}
			return visibilityChanged;
		}
		return false;
	}

	public boolean hasVisible() {
		if (subsections != null) {
			for (WizardPageSection s : subsections) {
				if (s instanceof CheckBox) {
					@SuppressWarnings("unchecked")
					CheckBox<T> cb = ((CheckBox<T>) s);
					if (cb.isVisible()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean isCreated() {
		return composite != null;
	}

	@Override
	public void dispose() {

		if (subsections != null) {
			for (WizardPageSection subsection : subsections) {
				subsection.dispose();
			}
		}
		super.dispose();
	}

	public void setModel(List<CheckBoxModel<T>> model) {
		dispose();
		this.model = model;
		createSubsections();
		composite.layout();
	}
}
