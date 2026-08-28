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
package org.springframework.ide.vscode.commons.languageserver.util;

import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.text.IRegion;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Represents a string with placeholder inside. Provides methods to retrieve
 * the location of a placeholder given its 'id'.
 */
public class PlaceHolderString {

	public static class PlaceHolder {
		public final Object id; // object used to identify the placeholder
		public final IRegion location;

		public PlaceHolder(Object id, IRegion location) {
			super();
			this.id = id;
			this.location = location;
		}

		public int getOffset() {
			return location.getOffset();
		}

		public int getEnd() {
			return location.getOffset() + location.getLength();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
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
			PlaceHolder other = (PlaceHolder) obj;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			return true;
		}

	}

	private final ImmutableMultimap<Object, PlaceHolder> placeHolders;
	private final String string;

	public PlaceHolderString(Multimap<Object, PlaceHolder> placeHolders, String string) {
		super();
		this.placeHolders = ImmutableMultimap.copyOf(placeHolders);
		this.string = string;
	}

	public boolean hasPlaceHolders() {
		if (placeHolders.isEmpty()) {
			return false;
		}
		if (placeHolders.size() == 1) {
			PlaceHolder placeHolder = CollectionUtil.getAny(placeHolders.values());
			if (string.length() == placeHolder.getEnd()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		if (placeHolders.size() == 1) {
			PlaceHolder placeHolder = CollectionUtil.getAny(placeHolders.values());
			if (string.length() == placeHolder.getEnd()) {
				return string.substring(0, placeHolder.getOffset());
			}
		}
		return string;
	}

	public PlaceHolder getPlaceHolder(Object id) {
		ImmutableCollection<PlaceHolder> all = placeHolders.get(id);
		if (all != null && !all.isEmpty()) {
			return all.iterator().next();
		}
		return null;
	}

}
