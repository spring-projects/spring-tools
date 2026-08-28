/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.sections;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelectionSource;
import org.springsource.ide.eclipse.commons.livexp.core.CompositeValidator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.PageSection;

/**
 * A SashSection is a composite consisting out of two resizable subsection,
 * with a dragable divider in the middle.
 *
 * @author Kris De Volder
 */
public class SashSection extends PageSection implements Disposable, MultiSelectionSource {

	//TODO: The sash is always aligning view in a column. Generalize this to
	// support both horizontal and vertical orientations.

	public SashSection(IPageWithSections owner, IPageSection top, IPageSection bottom) {
		super(owner);
		this.top = top;
		this.bottom = bottom;
	}

	private IPageSection top;
	private IPageSection bottom;
	private int topWeight = 70;
	private int bottomWeight = 30;

	private CompositeValidator validator;

	private SashForm sashForm;
	private Composite topComposite;
	private Composite bottomComposite;
	private int sashWidth = 8;
	private MultiSelection<?> selection;

	@Override
	public synchronized LiveExpression<ValidationResult> getValidator() {
		if (validator == null) {
			validator = new CompositeValidator();
			validator.addChild(top.getValidator());
			validator.addChild(bottom.getValidator());
		}
		return validator;
	}

	@Override
	public void createContents(Composite page) {
		sashForm = createSashForm(page);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(sashForm);
		sashForm.setLayout(new FillLayout());

		this.topComposite = createChildComposite(sashForm);
		top.createContents(topComposite);
//		topComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_GRAY));
		this.bottomComposite = createChildComposite(sashForm);
		bottom.createContents(bottomComposite);
		sashForm.setWeights(new int[] {topWeight, bottomWeight});
//		bottomComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_GRAY));
	}

	protected SashForm createSashForm(Composite page) {
		SashForm sf = new SashForm(page, SWT.VERTICAL);
		sf.setSashWidth(getSashWidth());
		return sf;
	}

	protected Composite createChildComposite(SashForm sashForm) {
		Composite child = new Composite(sashForm, SWT.NONE);
		child.setLayout(GridLayoutFactory.fillDefaults().create());
		return child;
	}

	@Override
	public void dispose() {
		if (top != null) {
			if (top instanceof Disposable) {
				((Disposable) top).dispose();
			}
			top = null;
		}
		if (bottom != null) {
			if (bottom instanceof Disposable) {
				((Disposable) bottom).dispose();
			}
			bottom = null;
		}
	}

	public int getTopWeight() {
		return topWeight;
	}

	public void setTopWeight(int topWeight) {
		this.topWeight = topWeight;
	}

	public int getBottomWeight() {
		return bottomWeight;
	}

	public void setBottomWeight(int bottomWeight) {
		this.bottomWeight = bottomWeight;
	}

	public int getSashWidth() {
		return sashWidth;
	}

	public void setSashWidth(int sashWidth) {
		this.sashWidth = sashWidth;
	}

	@Override
	public synchronized MultiSelection<?> getSelection() {
		//for now we keep it simple and only propagate selection from the 'dominant' child (i.e. the one at the top).
		if (selection == null) {
			if (top instanceof MultiSelectionSource) {
				selection = ((MultiSelectionSource) top).getSelection();
			} else {
				selection = MultiSelection.empty(Object.class);
			}
		}
		return selection;
	}

}
