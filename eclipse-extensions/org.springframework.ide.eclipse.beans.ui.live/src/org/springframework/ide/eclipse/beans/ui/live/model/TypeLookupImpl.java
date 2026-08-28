/*******************************************************************************
 * Copyright (c) 2012, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.beans.ui.live.model;

import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.IType;
import org.springframework.ide.eclipse.beans.ui.live.utils.JdtUtils;

/**
 * Type Lookup implementation
 * 
 * @author Alex Boyko
 *
 */
public class TypeLookupImpl implements TypeLookup {
	
	private final String appName;
	
	private final IProject project;

	public TypeLookupImpl(String appName, IProject project) {
		this.appName = appName;
		this.project = project;
	}

	public String getApplicationName() {
		return appName;
	}

	public IProject getProject() {
		return project;
	}
	
	protected String cleanClassName(String className) {
		String cleanClassName = className;
		if (className != null) {
			int ix = className.indexOf('$');
			if (ix > 0) {
				cleanClassName = className.substring(0, ix);
			}
			else {
				ix = className.indexOf('#');
				if (ix > 0) {
					cleanClassName = className.substring(0, ix);
				}
			}
		}
		return cleanClassName;
	}

	@Override
	public IType findType(String fqName) {
		IProject[] projects = relatedProjects();
		for (IProject project : projects) {
			IType type = JdtUtils.getJavaType(project, cleanClassName(fqName));
			if (type != null) {
				return type;
			}
		}
		return null;
	}

	@Override
	public IProject[] relatedProjects() {
		if (project != null) {
			return new IProject[] { project };
		} else {
			return ResourcesPlugin.getWorkspace().getRoot().getProjects();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TypeLookupImpl) {
			TypeLookupImpl other = (TypeLookupImpl) obj;
			return Objects.equals(appName, other.appName) && Objects.equals(project, other.project);
		}
		return super.equals(obj);
	}

	
}
