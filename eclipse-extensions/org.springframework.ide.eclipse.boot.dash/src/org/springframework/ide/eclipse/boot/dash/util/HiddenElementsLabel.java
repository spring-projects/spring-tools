/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

public class HiddenElementsLabel implements ValueListener<Integer> {
	private Label label;
	private LiveVariable<Integer> hiddenElementCount;

	public HiddenElementsLabel(Composite page, LiveVariable<Integer> hiddenElementCount) {
		this.label = new Label(page, SWT.NONE);
		label.setBackground(page.getBackground());
		this.hiddenElementCount = hiddenElementCount;
		hiddenElementCount.addListener(this);
	}

	@Override
	public void gotValue(LiveExpression<Integer> exp, Integer value) {
		if (label.isDisposed()) {
			hiddenElementCount.removeListener(this);
		} else {
			label.setText(value+" elements hidden by filter");
			hide(value == 0);
			label.getParent().layout(new Control[]{label});
			//May need this is we make element 'disapear' from layout:
			// ReflowUtil.reflow(owner, this);
		}
	}

	private void hide(boolean shouldHide) {
		if (isHide() != shouldHide) {
			GridData d = new GridData();
			d.exclude = shouldHide;
			label.setLayoutData(d);
		}
	}

	private boolean isHide() {
		if (label != null) {
			Object d = label.getLayoutData();
			if (d instanceof GridData) {
				return ((GridData) d).exclude;
			}
		}
		return false;
	}
}