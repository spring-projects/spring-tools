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
package org.springframework.ide.eclipse.boot.dash.model;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.springframework.ide.eclipse.boot.dash.api.Deletable;
import org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.util.BootLaunchUtils;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableSet;

/**
 * Concrete {@link BootDashElement} that wraps a launch config.
 *
 * @author Kris De Volder
 */
public class LaunchConfDashElement extends AbstractLaunchConfigurationsDashElement<ILaunchConfiguration> implements Deletable {

	private static final BootDashColumn[] COLUMNS = ArrayUtils.removeElement(LocalRunTarget.DEFAULT_COLUMNS,
			BootDashColumn.DEVTOOLS
	);

	private static final boolean DEBUG = false; //(""+Platform.getLocation()).contains("kdvolder");
	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	@Override
	public BootDashColumn[] getColumns() {
		return COLUMNS;
	}

	public LaunchConfDashElement(LocalBootDashModel bootDashModel, ILaunchConfiguration delegate) {
		super(bootDashModel, delegate);
	}

	@Override
	protected IPropertyStore createPropertyStore() {
		return PropertyStores.createFor(delegate);
	}

	@Override
	public ImmutableSet<ILaunchConfiguration> getLaunchConfigs() {
		return ImmutableSet.of(delegate);
	}

	@Override
	public IProject getProject() {
		return BootLaunchConfigurationDelegate.getProject(delegate);
	}

	@Override
	public String getName() {
		return delegate.getName();
	}

	@Override
	public ImmutableSet<ILaunch> getLaunches() {
		return ImmutableSet.copyOf(BootLaunchUtils.getLaunches(ImmutableSet.of(delegate)));
	}

	@Override
	public void dispose() {
		super.dispose();
		debug("Disposing: "+this);
	}

	@Override
	public BootProjectDashElement getParent() {
		IProject p = getProject();
		if (p != null) {
			return getBootDashModel().getProjectElementFactory().createOrGet(p);
		}
		return null;
	}

	@Override
	public void delete() throws CoreException {
		delegate.delete();
	}

}
