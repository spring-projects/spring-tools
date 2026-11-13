/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchManager;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.console.CloudAppLogManager;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModelContext;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.SecuredCredentialsStore;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElementDataContributor;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockScopedPropertyStore;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockSecuredCredentialStore;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.IScopedPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.InMemoryPropertyStore;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

public class TestBootDashModelContext extends BootDashModelContext {

	private File stateLoc;
	private ILaunchManager launchManager;
	private IWorkspace workspace;
	SecuredCredentialsStore secureStore = new MockSecuredCredentialStore();
	private IScopedPropertyStore<IProject> projectProperties;
	private IScopedPropertyStore<RunTargetType> runTargetProperties;
	private LiveVariable<Pattern> bootProjectExclusion = new LiveVariable<>(BootPreferences.DEFAULT_BOOT_PROJECT_EXCLUDE);
	private IPropertyStore viewProperties = new InMemoryPropertyStore();
	private IPropertyStore privateProperties = new InMemoryPropertyStore();

	public TestBootDashModelContext(IWorkspace workspace, ILaunchManager launchMamager) {
		super(createInjections());
		try {
			this.workspace = workspace;
			this.launchManager = launchMamager;
			stateLoc = StsTestUtil.createTempDirectory("plugin-state", null);
			this.projectProperties = new MockScopedPropertyStore<>();
			this.runTargetProperties = new MockScopedPropertyStore<>();
			reload();
		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}
	}

	private static SimpleDIContext createInjections() {
		SimpleDIContext injections = new SimpleDIContext();
		injections.defInstance(AllUserInteractions.class, mock(AllUserInteractions.class));
		injections.def(BootDashViewModel.class, BootDashViewModel::new);
		injections.def(CloudAppLogManager.class, CloudAppLogManager::new); //TODO: replace with a mock?
		injections.def(GenericRemoteAppElementDataContributor.class, GenericRemoteAppElementDataContributor::new);
		return injections;
	}

	public IPath getStateLocation() {
		return new Path(stateLoc.toString());
	}

	public IWorkspace getWorkspace() {
		return workspace;
	}

	public void teardownn() throws Exception {
		FileUtils.deleteQuietly(stateLoc);
	}

	public ILaunchManager getLaunchManager() {
		return launchManager;
	}

	@Override
	public void log(Exception e) {
		// No implementation we'll use Mockito to spy on the method instead.
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
		return secureStore;
	}

	@Override
	public LiveExpression<Pattern> getBootProjectExclusion() {
		return bootProjectExclusion;
	}

	@Override
	public IPropertyStore getViewProperties() {
		return viewProperties;
	}

	@Override
	public IPropertyStore getPrivatePropertyStore() {
		return privateProperties;
	}

	/**
	 * Simulates reloading
	 * @return
	 */
	public TestBootDashModelContext reload() throws Exception {
		injections.reload();
		return this;
	}

	public TestBootDashModelContext withTargetTypes(RunTargetType... types) {
		assertFalse("Multiple initializations of RunTargetTypes ?", injections.hasDefinitionFor(RunTargetType.class));
		for (RunTargetType t : types) {
			injections.defInstance(RunTargetType.class, t);
		}
		return this;
	}

	public RunTargetType getRargetTypeWithName(String name) {
		return injections.getBeans(RunTargetType.class).stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
	}
}
