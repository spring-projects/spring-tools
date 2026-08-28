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
package org.springframework.ide.eclipse.boot.validation.quickfix;

import static org.springframework.ide.eclipse.boot.validation.quickfix.GeneratorComposition.NULL_GENERATOR;
import static org.springframework.ide.eclipse.boot.validation.quickfix.GeneratorComposition.compose;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.springframework.ide.eclipse.boot.validation.framework.IValidationProblemMarker;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemType;

/**
 * Manages associations quickfix ids and {@link IMarkerResolutionGenerator2} instances
 *
 * @author Kris De Volder
 */
public class MarkerResolutionRegistry {

	public static final MarkerResolutionRegistry DEFAULT_INSTANCE = new MarkerResolutionRegistry();

	/**
	 * Marker attribute used to associate a resolution generator via registry to
	 * a marker. This attribute must be set on
	 */
	public static final String QUICK_FIX_ID_ATTR = IValidationProblemMarker.ERROR_ID;

	private Map<String, IMarkerResolutionGenerator2> registry;

	/**
	 * Obtain the generator for the given marker. Never returns null, will return
	 * NULL_GENERATOR instead
	 */
	public synchronized IMarkerResolutionGenerator2 generator(IMarker marker) {
		if (registry != null) {
			String id = marker.getAttribute(QUICK_FIX_ID_ATTR, null);
			if (id != null) {
				IMarkerResolutionGenerator2 registered = registry.get(id);
				if (registered != null) {
					return registered;
				}
			}
		}
		return NULL_GENERATOR;
	}

	/**
	 * Creates a quickfix id. Typically, the id identifies a problem type. The id should
	 * be registered by whoever/whatever generates the markers of that type. The id
	 * can also be used by the same entity to 'bind' the registered generator via its
	 * id to a specific marker.
	 *
	 * @param id
	 * @param generator
	 */
	public synchronized void register(ProblemType id, IMarkerResolutionGenerator2 generator) {
		if (registry == null) {
			registry = new HashMap<>();
		}
		IMarkerResolutionGenerator2 existing = registry.get(id);
		if (existing != null) {
			registry.put(id.getId(), compose(existing, generator));
		} else {
			registry.put(id.getId(),  generator);
		}
	}


}
