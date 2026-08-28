/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation.quickfix;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;

/**
 * Static helper methods and constants for composing {@link IMarkerResolutionGenerator2} instances
 * using a 'composit' design pattern implementation.
 *
 * @author Kris De Volder
 */
public class GeneratorComposition {

	public static final IMarkerResolution[] NO_RESOLUTIONS = {};

	/**
	 * Marker resolution generator which never generates any marker resolutions.
	 */
	public static final IMarkerResolutionGenerator2 NULL_GENERATOR = new IMarkerResolutionGenerator2() {
		public IMarkerResolution[] getResolutions(IMarker marker) {
			return NO_RESOLUTIONS;
		}
		public boolean hasResolutions(IMarker marker) {
			return false;
		}
	};

	public static IMarkerResolutionGenerator2 compose(
			IMarkerResolutionGenerator2 a,
			IMarkerResolutionGenerator2 b) {
		if (a == null || a == NULL_GENERATOR) {
			return b;
		} else if (b == null || b == NULL_GENERATOR) {
			return a;
		} else {
			return new CompositeGenerator(uncompose(a), uncompose(b));
		}
	}


	///////////////////////////////////////////////////////////////////////////////////////////////
	////// implementation stuffs below... should not concern the clients using this class's helper
	////// methods


	private static IMarkerResolutionGenerator2[] uncompose(IMarkerResolutionGenerator2 g) {
		if (g instanceof CompositeGenerator) {
			return ((CompositeGenerator)g).uncompose();
		}
		return new IMarkerResolutionGenerator2[] {g};
	}

	private static class CompositeGenerator implements IMarkerResolutionGenerator2 {

		private IMarkerResolutionGenerator2[] children;

		public CompositeGenerator(IMarkerResolutionGenerator2[] children) {
			this.children = children;
		}

		public CompositeGenerator(IMarkerResolutionGenerator2[] children, IMarkerResolutionGenerator2[] moreChildren) {
			this.children = Arrays.copyOf(children, children.length+moreChildren.length);
			System.arraycopy(moreChildren, 0, this.children, children.length, moreChildren.length);
		}

		@Override
		public IMarkerResolution[] getResolutions(IMarker marker) {
			ArrayList<IMarkerResolution> resolutions = new ArrayList<IMarkerResolution>();
			for (IMarkerResolutionGenerator2 generator : children) {
				IMarkerResolution[] additions = generator.getResolutions(marker);
				if (additions != null && additions.length > 0) {
					resolutions.addAll(Arrays.asList(additions));
				}
			}
			return resolutions.toArray(new IMarkerResolution[resolutions.size()]);
		}

		@Override
		public boolean hasResolutions(IMarker marker) {
			for (IMarkerResolutionGenerator2 generator : children) {
				if (generator.hasResolutions(marker)) {
					return true;
				}
			}
			return false;
		}

		public IMarkerResolutionGenerator2[] uncompose() {
			return children;
		}
	}

}
