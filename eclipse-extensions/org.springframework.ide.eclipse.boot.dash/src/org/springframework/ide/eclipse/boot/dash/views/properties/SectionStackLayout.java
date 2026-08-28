/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.properties;

import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

/**
 * Layout variant to have owner composite size to be equal the client area size
 * of the next upper level ScrolledComposite
 *
 * @author Alex Boyko
 *
 */
class SectionStackLayout extends StackLayout {

	/**
	 * Resets the layout for controls inside properties page. Also recalculates scroll-bars
	 * @param page properties page
	 */
	static void reflow(TabbedPropertySheetPage page) {
		final Composite target = page.getControl().getParent();
		if (target != null) {
			target.getDisplay().asyncExec(new Runnable() {
				public void run() {
					if (!target.isDisposed()) {
						target.layout(true, true);
						page.resizeScrolledComposite();
					}
				}
			});
		}
	}

	@Override
	protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) {
		Point size = super.computeSize(composite, wHint, hHint, flushCache);
		Composite container = getScrolledComposite(composite.getParent());
		Rectangle r = container.getClientArea();
		size = new Point(r.width, r.height);
		return size;
	}

	/**
	 * Searches for the upper level <code>ScrolledComposite</code>. Returns the passed composite if not found.
	 * @param composite
	 * @return
	 */
	private Composite getScrolledComposite(Composite composite) {
		Composite c = composite;
		while(c != null && !(c instanceof ScrolledComposite)) {
			c = c.getParent();
		}
		return c == null ? composite : c;
	}


}
