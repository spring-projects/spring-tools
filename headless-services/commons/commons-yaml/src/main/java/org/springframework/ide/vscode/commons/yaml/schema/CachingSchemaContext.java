/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.schema;

import java.util.Set;

public abstract class CachingSchemaContext implements DynamicSchemaContext {

	private Set<String> definedProps;

	protected abstract Set<String> computeDefinedProperties();

	@Override
	final public Set<String> getDefinedProperties() {
		if (definedProps == null) {
			definedProps = computeDefinedProperties();
		}
		return definedProps;
	}
}
