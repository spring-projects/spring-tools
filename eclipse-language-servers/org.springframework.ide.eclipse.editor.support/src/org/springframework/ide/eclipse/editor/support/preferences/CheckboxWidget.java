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
package org.springframework.ide.eclipse.editor.support.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;

/**
 * Wraps a Checkbox widget, attached to a LiveVariable so the widget selection
 * state is mirrored in the LiveVariable and vice-versa.
 *
 * @author Kris De Volder
 */
public class CheckboxWidget {

	public final Button widget;

	public CheckboxWidget(Composite parent, final LiveVariable<Boolean> selected) {
		widget = new Button(parent, SWT.CHECK);
		selected.addListener(new UIValueListener<Boolean>() {
			@Override
			protected void uiGotValue(LiveExpression<Boolean> exp, Boolean ignore) {
				//since uivalue listener may be called 'later' the value may have changed since
				// the event. So get its current state.
				Boolean value = selected.getValue();
				if (value != null) {
					if (widget.isDisposed()) {
						selected.removeListener(this);
					} else {
						widget.setSelection(value);
					}
				}
			}
		});
		widget.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selected.setValue(widget.getSelection());
			}
		});
	}

	public void setText(String string) {
		widget.setText(string);
	}

}
