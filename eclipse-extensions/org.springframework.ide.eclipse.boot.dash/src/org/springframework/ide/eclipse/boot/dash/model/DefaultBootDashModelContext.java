/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.core.cli.BootInstallManager;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.console.CloudAppLogManager;
import org.springframework.ide.eclipse.boot.dash.di.EclipseBeanLoader;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.metadata.PropertyStoreFactory;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElementDataContributor;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetTypes;
import org.springframework.ide.eclipse.boot.dash.views.DefaultUserInteractions;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.IScopedPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.ui.UIContext;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * @author Kris De Volder
 */
public class DefaultBootDashModelContext extends BootDashModelContext {

	private IScopedPropertyStore<IProject> projectProperties = PropertyStores.createForProjects();

	private IScopedPropertyStore<RunTargetType> runTargetProperties = PropertyStoreFactory.createForRunTargets();

	private SecuredCredentialsStore securedStore = PropertyStoreFactory.createSecuredCredentialsStore();

	private IPropertyStore viewProperties = PropertyStores.backedBy(BootDashActivator.getDefault().getPreferenceStore());

	private BootInstallManager bootInstalls = BootInstallManager.getInstance();

	public static BootDashModelContext create() {
		SimpleDIContext injections = new SimpleDIContext();
		injections.defInstance(UIContext.class, UIContext.DEFAULT);
		injections.defInstance(UserInteractions.class, new DefaultUserInteractions(injections));
		injections.def(BootDashViewModel.class, BootDashViewModel::new);
		injections.def(BootDashModelContext.class, DefaultBootDashModelContext::new);
		injections.defInstance(RunTargetType.class, RunTargetTypes.LOCAL);
		injections.def(CloudAppLogManager.class, CloudAppLogManager::new);
		injections.def(RemoteBootAppsDataHolder.Contributor.class, GenericRemoteAppElementDataContributor::new);
		new EclipseBeanLoader(injections).loadFromExtensionPoint(BootDashActivator.INJECTIONS_EXTENSION_ID);
		return new DefaultBootDashModelContext(injections);
	}

	private DefaultBootDashModelContext(SimpleDIContext injections) {
		super(injections);
	}

	@Override
	public IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	@Override
	public IPath getStateLocation() {
		return BootDashActivator.getDefault().getStateLocation();
	}

	@Override
	public ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	@Override
	public IScopedPropertyStore<IProject> getProjectProperties() {
		return projectProperties;
	}

	@Override
	public IScopedPropertyStore<RunTargetType> getRunTargetProperties() {
		return runTargetProperties;
	}

	@Override
	public SecuredCredentialsStore getSecuredCredentialsStore() {
		return securedStore;
	}

	@Override
	public void log(Exception e) {
		Log.log(e);
	}

	@Override
	public LiveExpression<Pattern> getBootProjectExclusion() {
		return BootPreferences.getInstance().getProjectExclusionExp();
	}

	@Override
	public IPropertyStore getViewProperties() {
		return viewProperties;
	}

	@Override
	public BootInstallManager getBootInstallManager() {
		return bootInstalls;
	}

}
