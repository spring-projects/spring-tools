/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.guides;

import java.util.HashSet;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.ui.CommentSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageWithSections;

/**
 * @author Kris De Volder
 */
public class CodeSetCheckBoxesSection extends WizardPageSection {

	public static class CheckBox extends WizardPageSection {

		private final String name;
		private final MultiSelectionModel<String> model;
		private Button cb;

		public CheckBox(IPageWithSections owner, String name, MultiSelectionModel<String> model) {
			super(owner);
			this.name = name;
			this.model = model;
		}

		@Override
		public LiveExpression<ValidationResult> getValidator() {
			return Validator.OK;
		}

		@Override
		public void createContents(Composite page) {
			if (page != null && !page.isDisposed()) {
				this.cb = new Button(page, SWT.CHECK);
				cb.setText(name);
				cb.setSelection(model.selecteds.contains(name));
				GridDataFactory.fillDefaults().grab(true, false).applyTo(cb);
				cb.addSelectionListener(new SelectionListener() {
					//@Override
					public void widgetSelected(SelectionEvent e) {
						handleSelection();
					}

					//@Override
					public void widgetDefaultSelected(SelectionEvent e) {
						handleSelection();
					}

					private void handleSelection() {
						boolean add = cb.getSelection();
						if (add) {
							model.selecteds.add(name);
						} else {
							model.selecteds.remove(name);
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
		}
	}

	private final LiveExpression<String[]> options;
	private final MultiSelectionModel<String> model;
	private Group group;


	public CodeSetCheckBoxesSection(WizardPageWithSections owner, LiveExpression<String[]> options, MultiSelectionModel<String> model) {
		super(owner);
		this.model = model;
		this.options = options;
	}

	private WizardPageSection[] subsections;

	protected GridLayout createLayout() {
		return new GridLayout(2, true);
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return model.validator;
	}

	@Override
	public void createContents(Composite page) {
		this.group = new Group(page, SWT.NONE);
		this.group.setText("Code Sets");
		GridLayout layout = createLayout();
		group.setLayout(layout);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		//This section is a bit special in that the contents of the group is only
		// filled in dynamically in response to events from the 'options' LiveExpression.
		options.addListener(new UIValueListener<String[]>() {
			@Override
			public void uiGotValue(LiveExpression<String[]> exp, String[] names) {
				if (group == null || group.isDisposed()) {
					//Don't bother. The UI is already gone.
					options.removeListener(this);
					return;
				}
				if (names == null) {
					names = new String[0];
				}
				//Dispose the checkboxes and create new ones.
				if (subsections != null) {
					for (WizardPageSection subsection : subsections) {
						subsection.dispose();
					}
				}
				subsections = new WizardPageSection[Math.max(1, names.length)];

//				GridData gd = (GridData) group.getLayoutData();
//				boolean visible = checkboxes.length>0;
//				gd.exclude = !visible;

				if (names.length == 0) {
					//don't leave section empty it looks ugly
					subsections[0] = new CommentSection(owner, "No codesets");
					subsections[0].createContents(group);
				}
				GridLayout newLayout = createLayout();
				newLayout.numColumns = names.length > 2 ? 3 : 2;
				group.setLayout(newLayout);
				for (int i = 0; i < names.length; i++) {
					subsections[i] = new CheckBox(owner, names[i], model);
					if (isNewName(names[i])) {
						model.selecteds.add(names[i]);
					}
					subsections[i].createContents(group);
				}
				//Note: code below removes invalid names from selection model.
				// Code has been commented out. We assume that any code
				// using the 'model.selecteds' will just ignore invalid
				// codeset names. That way we can leave the selecteds as is.
				// The benefit is that if one switches away from a guide
				// and then back again, then selected items will be 'preserved' even if
				// they weren't valid in between. (If the code below is reactivated invalid
				// names will be automatically cleared and the user will have to reselect them
				// when they return to the original guide).

//				HashSet<String> validNameSet = new HashSet<String>(Arrays.asList(names));
//				for (String selectedName : model.selecteds.getValues()) {
//					if (!validNameSet.contains(selectedName)) {
//						model.selecteds.remove(selectedName);
//					}
//				};
				group.getParent().layout(true, true);
			}

			private final HashSet<String> namesSeen = new HashSet<String>();

			/**
			 * Tracks codeset names that have (not) been seen before.
			 * This method returns true the first time it is called with a given
			 * name. Subsequent calls with the same name will return false
			 */
			private boolean isNewName(String name) {
				boolean seen = namesSeen.contains(name);
				if (!seen) {
					//Seen it now...next time its not new anymore
					namesSeen.add(name);
				}
				return !seen;
			}
		});
	}

	private void clear(Group group) {
		Control[] children = group.getChildren();
		for (Control c : children) {
			c.dispose();
		}
	}

}
