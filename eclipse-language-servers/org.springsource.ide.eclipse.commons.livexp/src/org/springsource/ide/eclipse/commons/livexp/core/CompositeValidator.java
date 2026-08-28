/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.core;

import org.eclipse.core.runtime.IStatus;

/**
 * A composite validator 'composes' the results of multiple validator by computing each
 * in order and only returning the worst result. If some results are equal in terms
 * of status priority then the first one is returned.
 * 
 * @author Kris De Volder
 */
public class CompositeValidator extends CompositeExpression<ValidationResult> {

	public CompositeValidator() {
		super(ValidationResult.OK);
	}

	public CompositeValidator addChild(LiveExpression<ValidationResult> child) {
		return (CompositeValidator) super.addChild(child);
	}
	
	@Override
	protected ValidationResult compute() {
		ValidationResult worst = ValidationResult.OK;
		for (LiveExpression<ValidationResult> v : getChildren()) {
			ValidationResult r = v.getValue();
			if (r != null && r.status > worst.status) {
				worst = r;
			}
			if (worst.status >= IStatus.ERROR) {
				//can't really get any worse than that
				return worst;
			}
		}
		return worst;
	}
}
