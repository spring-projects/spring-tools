/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.reconcile;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.editor.support.util.ValueParseException;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;

/**
 * Exception if there is a failure when parsing a value. It does not wrap
 * other exceptions such that when thrown, the parse exception is the "deepest"
 * error.
 *
 */
public class ReconcileException extends ValueParseException implements ProblemTypeProvider {

	private static final long serialVersionUID = 1L;
	private final ProblemType problemType;

	private ReplacementQuickfix replacement = null; //Optional info to create a 'replacement quickfix' for the value

	public ReconcileException(String message, ProblemType problemType) {
		super(message);
		this.problemType = problemType;
	}

	public ReconcileException(String message, ProblemType problemType, int start, int end) {
		super(message, start, end);
		this.problemType = problemType;
	}

	@Override
	public ProblemType getProblemType() {
		return problemType;
	}

	public ReconcileException fixWith(ReplacementQuickfix replacement) {
		//Silently ignore if the fix is either null or doesn't provide a proper replacement text.
		if (replacement != null && StringUtil.hasText(replacement.replacement)) {
			Assert.isLegal(this.replacement == null, "Multiple fixes not yet supported");
			this.replacement = replacement;
		}
		return this;
	}

	public ReplacementQuickfix getReplacement() {
		return replacement;
	}
}
