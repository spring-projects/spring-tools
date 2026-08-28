/*******************************************************************************
 * Copyright (c) 2017, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.livexp.ui;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Layout;
import org.springsource.ide.eclipse.commons.livexp.core.DelegatingLiveExp;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.ReflowableSection;

public class DynamicSection extends ReflowableSection {

	public static final Point DEFAULT_MIN_SIZE = new Point(500, 500);

	private LiveExpression<IPageSection> content;
	private Composite composite;

	private final DelegatingLiveExp<ValidationResult> validator = new DelegatingLiveExp<>();

	private Point minSize = DEFAULT_MIN_SIZE;
	private Integer widthHint = DEFAULT_MIN_SIZE.y;
	private Integer heightHint = DEFAULT_MIN_SIZE.x;

	private boolean isFocused;

	public DynamicSection(IPageWithSections owner, LiveExpression<IPageSection> content) {
		super(owner);
		this.content = content;
	}

	@Override
	public void createContents(Composite page) {
		composite = new Composite(page, SWT.NONE);
		Layout l = GridLayoutFactory.fillDefaults().margins(0, 0).create();
		composite.setLayout(l);
		// To set a minimum size in general and avoid the parent composite from being too small and having scrollbars before the dynamic content is created,
		// set a minimum size. This means setting both minSize and hint, along with grab,  for it to work properly.
		GridData gd = GridDataFactory.fillDefaults().grab(true, true).minSize(minSize).create();
		if (widthHint != null) {
			gd.widthHint = widthHint;
		}
		if (heightHint != null) {
			gd.heightHint = heightHint;
		}
		composite.setLayoutData(gd);

		content.addListener(UIValueListener.from((e, newContents) -> updateContent(newContents)));
	}

	public DynamicSection setMinimumSize(Point minSize) {
		Assert.isLegal(minSize != null);
		this.minSize  = minSize;
		return this;
	}

	public DynamicSection setWidthHint(Integer sz) {
		this.widthHint = sz;
		return this;
	}
	public DynamicSection setHeightHint(Integer sz) {
		this.heightHint = sz;
		return this;
	}

	private void updateContent(IPageSection newContents) {
		if (composite != null && !composite.isDisposed()) {
			validator.setDelegate(newContents.getValidator());
			for (Control oldWidget : composite.getChildren()) {
				oldWidget.dispose();
			}
			newContents.createContents(composite);
			reflow(owner, composite);
		} else {
			validator.setDelegate(null);
		}
		if (isFocused) {
			newContents.setFocus();
		}
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return validator;
	}

	@Override
	public void dispose() {
		//Detach validator wiring from nested validator (if any is still attached).
		validator.setDelegate(null);
		super.dispose();
	}

	@Override
	public void setFocus() {
		isFocused = true;
		if (content.getValue() != null) {
			content.getValue().setFocus();
		}
	}

}
