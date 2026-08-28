/*******************************************************************************
 * Copyright (c) 2015-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui.util;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.Reflowable;

/**
 * @author Kris De Volder
 */
public class ReflowUtil {

	public static void reflow(IPageWithSections owner, Control changed) {
		boolean reflowed = findAndReflowControl(changed);
		if (!reflowed && owner instanceof Reflowable) {
			((Reflowable) owner).reflow();
		}
	}

	public static boolean findAndReflowControl(Control control) {
		while (control != null) {
			if (control instanceof Reflowable) {
				if (((Reflowable)control).reflow()) {
					return true;
				}
			}
			control = control.getParent();
		}
		//No Reflowable found. Sorry!
		return false;
	}

	/**
	 * The simple reflow isn't very reliable. There are often components in the ui
	 * that 'refuse' to recompute their size. This one is more aggressive, walking
	 * up the parent-chain and asking every composite up the chain to layout iself
	 * until a proper 'Reflowable' is found.
	 */
	public static boolean reflowParents(IPageWithSections owner, Control control) {
		boolean layoutChildren = true;
		while (control != null) {
			if (control instanceof Reflowable) {
				if (((Reflowable)control).reflow()) {
					return true;
				}
			} else if (control instanceof Composite) {
				((Composite)control).layout(true, layoutChildren);
				layoutChildren = false;
			}
			control = control.getParent();
		}
		if (owner instanceof Reflowable) {
			return ((Reflowable) owner).reflow();
		}
		return false;
	}

}
