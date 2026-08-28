/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.sections;

import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;

public class CursorProviders {

	/**
	 * A mouse cursor provider that shows the hand cursor, always.
	 */
	public static final CursorProvider HAND_CURSOR = forStyle(SWT.CURSOR_HAND);

	/**
	 * Create a cursor provider for given
	 */
	public static CursorProvider forStyle(final int cursorStyle) {
		return new CursorProvider() {

			private Cursor cursor;

			@Override
			public Cursor getCursor(ViewerCell cell) {
				if (cursor == null) {
					cursor = new Cursor(cell.getControl().getDisplay(), cursorStyle);
				}
				return cursor;
			}
		};
	};

}
