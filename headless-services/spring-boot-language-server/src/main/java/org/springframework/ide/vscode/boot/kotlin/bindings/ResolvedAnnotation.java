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
package org.springframework.ide.vscode.boot.kotlin.bindings;

import java.util.List;

/**
 * An annotation, as resolved for the source code that is being indexed.
 * <p>
 * Two resolved annotations are equal if they stand for the same annotation.
 *
 * @author Martin Lippert
 */
public interface ResolvedAnnotation {

	/** the simple name of the annotation type */
	String getName();

	/** the annotation type, null if it could not be resolved */
	ResolvedType getAnnotationType();

	/** the attributes that are set explicitly on the annotation, with their values */
	List<ResolvedMemberValuePair> getDeclaredMemberValuePairs();

}
