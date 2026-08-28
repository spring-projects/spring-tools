/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.schema;

import org.springframework.ide.vscode.commons.languageserver.util.PlaceHolderString;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.Renderable;

import com.google.common.base.Supplier;

public class BasicYValueHint implements YValueHint {

	private final String value;
	private String label;
	private Supplier<PlaceHolderString> extraInsertion = null;
	private Renderable documentation;

	public BasicYValueHint(String value, String label) {
		this.value = value;
		this.label = label;
	}

	public BasicYValueHint(String value) {
		this.value = value;
		this.label = value;
	}

	/* (non-Javadoc)
	 * @see org.springframework.ide.eclipse.cloudfoundry.manifest.editor.YValueHint#getValue()
	 */
	@Override
	public String getValue() {
		return value;
	}

	/* (non-Javadoc)
	 * @see org.springframework.ide.eclipse.cloudfoundry.manifest.editor.YValueHint#getLabel()
	 */
	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BasicYValueHint other = (BasicYValueHint) obj;
		if (label == null) {
			if (other.label != null)
				return false;
		} else if (!label.equals(other.label))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "BasicYValueHint [value=" + value + ", label=" + label + "]";
	}

	@Override
	public PlaceHolderString getExtraInsertion() {
		Supplier<PlaceHolderString> ei = this.extraInsertion;
		return ei == null ? null : ei.get();
	}

	public BasicYValueHint setExtraInsertion(Supplier<PlaceHolderString> insertions) {
		Assert.isLegal(this.extraInsertion == null);
		this.extraInsertion = insertions;
		return this;
	}

	public BasicYValueHint setDocumentation(Renderable docs) {
		this.documentation = docs;
		return this;
	}

	@Override
	public Renderable getDocumentation() {
		return documentation;
	}
}
