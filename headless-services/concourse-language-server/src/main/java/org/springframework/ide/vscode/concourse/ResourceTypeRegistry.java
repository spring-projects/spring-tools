/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.concourse;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.AbstractType;

/**
 * Keeps track of known resource types.
 *
 * @author Kris De Volder
 */
public class ResourceTypeRegistry {

	private static class ResourceTypeInfo {
		private final AbstractType source;
		private final AbstractType in;
		private final AbstractType out;

		public ResourceTypeInfo(AbstractType source, AbstractType in, AbstractType out) {
			super();
			this.source = source;
			this.in = in;
			this.out = out;
		}

		public AbstractType getSource() {
			return source;
		}

		public AbstractType getIn() {
			return in;
		}

		public AbstractType getOut() {
			return out;
		}
	}

	private Map<String, ResourceTypeInfo> resourceTypes = new HashMap<>();


	public ResourceTypeRegistry() {
	}

	public void def(String resourceTypeName, AbstractType source, AbstractType in, AbstractType out) {
		Assert.isLegal(!resourceTypes.containsKey(resourceTypeName), "Multiple definitions for '"+resourceTypeName+"'");
		resourceTypes.put(resourceTypeName, new ResourceTypeInfo(source, in, out));
	}

	public YType getSourceType(String typeTag) {
		if (typeTag != null) {
			ResourceTypeInfo v = resourceTypes.get(typeTag);
			if (v != null) {
				return v.getSource();
			}
		}
		return null;
	}

	public YType getInParamsType(String typeTag) {
		if (typeTag != null) {
			ResourceTypeInfo v = resourceTypes.get(typeTag);
			if (v != null) {
				return v.getIn();
			}
		}
		return null;
	}

	public YType getOutParamsType(String typeTag) {
		if (typeTag != null) {
			ResourceTypeInfo v = resourceTypes.get(typeTag);
			if (v != null) {
				return v.getOut();
			}
		}
		return null;
	}

}
