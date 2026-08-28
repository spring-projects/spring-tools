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
package org.springsource.ide.eclipse.commons.livexp.core;

public class OrExpression extends CompositeExpression<Boolean> {

	public OrExpression() {
		super(false);
	}

	public OrExpression(LiveExpression<Boolean>... children) {
		this();
		for (LiveExpression<Boolean> c : children) {
			addChild(c);
		}
	}

	@Override
	protected Boolean compute() {
		//TODO: could be smarter about how we manage dependencies on
		// our children. As soon as value is computed we really only depend
		// on changes to the first n children used during the computation.
		for (LiveExpression<Boolean> c : getChildren()) {
			if (isTrue(c.getValue())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Null-safe check for truth value of boxed Boolean.
	 */
	private static boolean isTrue(Boolean value) {
		if (value != null) {
			return value;
		}
		return false;
	}

}
