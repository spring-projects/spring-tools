/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.livexp;

import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSets;

/**
 * Represents a selection of zero or more elements of type T in some UI component.
 *
 * @author Kris De Volder
 */
public final class MultiSelection<T> {

	public static <T> MultiSelection<T> empty(Class<T> type) {
		return new MultiSelection<>(type, LiveSets.emptySet(type));
	}

	/**
	 * Converts a 'single' selection to a multi selection.
	 */
	public static <T> MultiSelection<T> singletonOrEmpty(Class<T> type,
			LiveExpression<T> singleSelection) {
		return new MultiSelection<>(type, org.springsource.ide.eclipse.commons.livexp.core.LiveSets.singletonOrEmpty(singleSelection));
	}



	public static <T> MultiSelection<T> union(MultiSelection<T> a, MultiSelection<T> b) {
		Assert.isLegal(a.getElementType().equals(b.getElementType()));
		return from(a.getElementType(), LiveSets.union(a.getElements(), b.getElements()));
	}

	private final Class<T> elementType;
	private final ObservableSet<T> elements;

	public MultiSelection(Class<T> elementType, ObservableSet<T> elements) {
		this.elementType = elementType;
		this.elements = elements;
	}

	public Class<T> getElementType() {
		return elementType;
	}

	/**
	 * Filter a selection to retain only elements of a given type.
	 */
	public <U> MultiSelection<U> filter(Class<U> retainType) {
		MultiSelection<U> converted = this.as(retainType);
		if (converted != null) {
			//Don't need to filter element-by-element since the selection only
			// can contain elements of 'retainType'.
			return converted;
		} else {
			//Selection may contain objects that are not instances of retainType.
			return from(retainType, org.springsource.ide.eclipse.commons.livexp.core.LiveSets.filter(getElements(), retainType));
		}
	}

	/**
	 * Convert a selection of one type into a selection of a different
	 * type. The conversion only succeeds if the target-type is assignment
	 * compatible with the source type.
	 *
	 * @return Converted selection or null if the conversion is not legal.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <U> MultiSelection<U> as(Class<U> toElementType) {
		if (toElementType.isAssignableFrom(elementType)) {
			return new MultiSelection<U>(toElementType, ((ObservableSet) elements));
		} else {
			return null;
		}
	}

	/**
	 * Convert a selection of one type into a selection of a different
	 * type. The conversion only succeeds if the target-type is assignment
	 * compatible with the source type.
	 *
	 * @return Converted selection
	 */
	public <U> MultiSelection<U> cast(Class<U> toElementType) throws ClassCastException {
		MultiSelection<U> converted = as(toElementType);
		if (converted == null) {
			throw new ClassCastException(getElementType().getName()+" => "+toElementType.getName());
		}
		return converted;
	}


	public ObservableSet<T> getElements() {
		return elements;
	}

	public static <T> MultiSelection<T> from(Class<T> type, ObservableSet<T> elements) {
		return new MultiSelection<>(type, elements);
	}

	public Set<T> getValue() {
		return getElements().getValue();
	}

	/**
	 * @return The only element in the selection, if exactly one element is selected; or null
	 * otherwise.
	 */
	public T getSingle() {
		Set<T> es = getValue();
		if (es.size() == 1) {
			for (T t : es) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Converts a 'multi' selection to a selection of a single element. The single selection
	 * will be the selected element of the multi-selection if exactly one element is
	 * currently selected, and 'null' otherwise.
	 */
	public LiveExpression<T> toSingleSelection() {
		LiveExpression<T> singleSelect = new LiveExpression<T>() {
			protected T compute() {
				return MultiSelection.this.getSingle();
			}
		};
		singleSelect.dependsOn(getElements());
		return singleSelect;
	}

}
