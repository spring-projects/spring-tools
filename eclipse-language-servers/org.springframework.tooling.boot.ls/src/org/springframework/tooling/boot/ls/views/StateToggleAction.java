/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.eclipse.jface.action.Action;

/**
 * An action that switches something of the {@link StructureViewState} on and off.
 * 
 * @author Martin Lippert
 */
class StateToggleAction extends Action {

	private final BooleanSupplier state;
	private final Consumer<Boolean> setState;

	StateToggleAction(String text, String toolTipText, BooleanSupplier state, Consumer<Boolean> setState) {
		super(text, AS_CHECK_BOX);
		setToolTipText(toolTipText);
		this.state = state;
		this.setState = setState;
		update();
	}

	/**
	 * Shows what the state is now, which can be a result of something else being switched.
	 */
	void update() {
		setChecked(state.getAsBoolean());
	}

	@Override
	public void run() {
		setState.accept(isChecked());
	}

}
