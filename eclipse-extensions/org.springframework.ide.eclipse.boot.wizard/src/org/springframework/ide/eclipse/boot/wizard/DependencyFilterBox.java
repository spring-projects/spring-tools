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
package org.springframework.ide.eclipse.boot.wizard;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springsource.ide.eclipse.commons.livexp.core.FilterBoxModel;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.livexp.util.Filters;

public class DependencyFilterBox extends FilterBoxModel<Dependency> {

	@Override
	protected Filter<Dependency> createFilterForInput(String _text) {
		if (StringUtils.isNotBlank(_text)) {
			final String text = _text.toLowerCase();
			return (dependency) -> 
					  matches(text, dependency.getName())
						|| matches(text, dependency.getDescription());
		}
		return Filters.acceptAll();
	}

	protected boolean matches(String pattern, Dependency dep) {
		return matches(pattern, dep.getName())
			|| matches(pattern, dep.getDescription());
	}

	protected boolean matches(String pattern, String text) {
		if (text != null) {
			return text.toLowerCase().contains(pattern);
		}
		return false;
	}
}
