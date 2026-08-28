/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.test;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.springframework.ide.eclipse.boot.launch.BootLaunchActivator;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.BootLaunchUIModel;
import org.springframework.ide.eclipse.boot.launch.IProfileHistory;
import org.springframework.ide.eclipse.boot.launch.LaunchTabSelectionModel;
import org.springframework.ide.eclipse.boot.launch.MainTypeNameLaunchTabModel;
import org.springframework.ide.eclipse.boot.launch.ProfileLaunchTabModel;
import org.springframework.ide.eclipse.boot.launch.SelectProjectLaunchTabModel;
import org.springframework.ide.eclipse.boot.launch.livebean.EnableJmxFeaturesModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

/**
 * @author Kris De Volder
 */
public class BootLaunchUIModelTest extends BootLaunchTestCase {

	private static final String[] NO_PROFILES =  new String[0];

	public class TestProfileHistory implements IProfileHistory {

		private Map<String, String[]> map = new HashMap<>();

		@Override
		public String[] getHistory(IProject project) {
			String[] h = map.get(project.getName());
			if (h != null) {
				return h;
			}
			return NO_PROFILES;
		}

		public void setHistory(IProject p, String... history) {
			map.put(p.getName(), history);
		}

	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	protected BootLaunchUIModel model;
	protected TestProfileHistory profileHistory;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		profileHistory = new TestProfileHistory();
		model = new BootLaunchUIModel(profileHistory);
	}

	///// project ////////////////////////////////////////////////////////////

	public void testProjectValidator() throws Exception {
		createPredefinedProject("empty-boot-project");
		createGeneralProject("general");
		assertError("No project selected", model.project.validator);

		model.project.selection.setValue(getProject("non-existant"));
		assertError("does not exist", model.project.validator);

		model.project.selection.setValue(getProject("general"));
		assertError("does not look like a Boot project", model.project.validator);

		model.project.selection.setValue(getProject("empty-boot-project"));
		assertOk(model.project.validator);

		getProject("empty-boot-project").close(new NullProgressMonitor());
		model.project.validator.refresh(); //manual refresh is needed
											// no auto refresh when closing project. This is normal
											// and it is okay since user can't open/close projects
											// while using launch config dialog.
		assertError("is closed", model.project.validator);
	}

	private ILaunchConfigurationWorkingCopy createWorkingCopy() throws CoreException {
		return createWorkingCopy(BootLaunchConfigurationDelegate.TYPE_ID);
	}

	public void testProjectInitializeFrom() throws Exception {
		IProject fooProject = getProject("foo");

		SelectProjectLaunchTabModel project = model.project;
		LiveVariable<Boolean> dirtyState = model.project.getDirtyState();

		dirtyState.setValue(false);
		project.selection.setValue(fooProject);
		assertTrue(dirtyState.getValue());

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		project.initializeFrom(wc);
		assertEquals(null, project.selection.getValue());
		assertFalse(dirtyState.getValue());

		BootLaunchConfigurationDelegate.setProject(wc, fooProject);
		project.initializeFrom(wc);
		assertEquals(fooProject, project.selection.getValue());
	}

	public void testProjectPerformApply() throws Exception {
		IProject fooProject = getProject("foo");

		SelectProjectLaunchTabModel project = model.project;
		LiveVariable<Boolean> dirtyState = model.project.getDirtyState();

		dirtyState.setValue(false);
		project.selection.setValue(fooProject);
		assertTrue(dirtyState.getValue());

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals(null, BootLaunchConfigurationDelegate.getProject(wc));

		project.performApply(wc);

		assertFalse(dirtyState.getValue());
		assertEquals(fooProject, BootLaunchConfigurationDelegate.getProject(wc));
	}

	public void testProjectSetDefaults() throws Exception {
		IProject fooProject = getProject("foo");

		SelectProjectLaunchTabModel project = model.project;

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		BootLaunchConfigurationDelegate.setProject(wc, fooProject);
		assertEquals(fooProject, BootLaunchConfigurationDelegate.getProject(wc));
		project.setDefaults(wc);
		assertEquals(null, BootLaunchConfigurationDelegate.getProject(wc));
	}

	public void testProjectDirtyState() throws Exception {
		SelectProjectLaunchTabModel project = model.project;
		LiveVariable<Boolean> dirtyState = model.project.getDirtyState();

		dirtyState.setValue(false);
		project.selection.setValue(getProject("nono"));
		assertTrue(dirtyState.getValue());
	}

	////// main type //////////////////////////////////////////////////////////

	public void testMainTypeValidator() throws Exception {
		assertEquals("", model.mainTypeName.selection.getValue());
		assertError("No Main type selected", model.mainTypeName.validator);
		model.mainTypeName.selection.setValue("something");
		assertOk(model.mainTypeName.validator);
	}

	public void testMainTypeInitializeFrom() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		LiveVariable<Boolean> dirtyState = model.mainTypeName.getDirtyState();

		BootLaunchConfigurationDelegate.setMainType(wc, "Snuggem");
		dirtyState.setValue(true);

		mainTypeName.initializeFrom(wc);

		assertFalse(dirtyState.getValue());
		assertEquals("Snuggem", mainTypeName.selection.getValue());
	}

	public void testMainTypePerformApply() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		LiveVariable<Boolean> dirtyState = model.mainTypeName.getDirtyState();

		mainTypeName.selection.setValue("Koko");
		assertTrue(dirtyState.getValue());

		mainTypeName.performApply(wc);

		assertEquals("Koko", getMainTypeName(wc));
		assertFalse(dirtyState.getValue());
	}

	public void testMainTypeSetDefaults() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		mainTypeName.setDefaults(wc);
		assertEquals("", getMainTypeName(wc));
	}

	protected String getMainTypeName(ILaunchConfigurationWorkingCopy wc)
			throws CoreException {
		return wc.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String)null);
	}

	public void testMainTypeDirtyState() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;
		LiveVariable<Boolean> dirtyState = model.mainTypeName.getDirtyState();

		dirtyState.setValue(false);
		mainTypeName.selection.setValue("something");
		assertTrue(dirtyState.getValue());
	}

	////// profile ////////////////////////////////////////////////////////////////////

	public void testProfileValidator() throws Exception {
		assertEquals(Validator.OK, model.profile.validator);
		//not much to test here, we don't validate profiles at all.
	}

	public void testProfileSetDefaults() throws Exception {
		ProfileLaunchTabModel profile = model.profile;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		profile.setDefaults(wc);
		assertEquals("", BootLaunchConfigurationDelegate.getProfile(wc));
	}

	public void testProfileInitializeFrom() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		ProfileLaunchTabModel profile = model.profile;
		LiveVariable<Boolean> dirty = profile.getDirtyState();

		dirty.setValue(true);
		BootLaunchConfigurationDelegate.setProfile(wc,"some-profile");
		profile.initializeFrom(wc);
		assertFalse(dirty.getValue());
		assertEquals("some-profile", BootLaunchConfigurationDelegate.getProfile(wc));
	}

	public void testProfilePerformApply() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		ProfileLaunchTabModel profile = model.profile;
		LiveVariable<Boolean> dirty = profile.getDirtyState();

		profile.selection.setValue("some-other-profile");
		assertTrue(dirty.getValue());

		profile.performApply(wc);

		assertFalse(dirty.getValue());
		assertEquals("some-other-profile", BootLaunchConfigurationDelegate.getProfile(wc));
	}

	public void testProfileDirtyState() throws Exception {
		ProfileLaunchTabModel profile = model.profile;
		LiveVariable<Boolean> dirty = profile.getDirtyState();
		dirty.setValue(false);

		profile.selection.setValue("olla-polla");

		assertTrue(dirty.getValue());
	}

	public void testProfilePulldownOptions() throws Exception {
		IProject bootProject = createPredefinedProject("empty-boot-project");
		IProject generalProject = createGeneralProject("general");

		LiveVariable<IProject> project = model.project.selection;
		ProfileLaunchTabModel profile = model.profile;

		assertPulldown(profile /*empty*/);

		createEmptyFile(bootProject, "src/main/resources/application-foo.properties");
		createEmptyFile(bootProject, "src/main/resources/application-bar.properties");

		project.setValue(bootProject);
		assertPulldown(profile, "foo", "bar");

		project.setValue(getProject("invalid"));
		assertPulldown(profile /*empty*/);

		profileHistory.setHistory(generalProject, "something", "borker");
		project.setValue(generalProject);
		assertPulldown(profile, "something", "borker");

		profileHistory.setHistory(bootProject, "old", "older");
		project.setValue(bootProject);
		assertPulldown(profile, "foo", "bar", "old", "older");

		profileHistory.setHistory(bootProject, "new", "newer", "foo");
		profile.profileOptions().refresh(); // See [*] below
		assertPulldown(profile, "foo", "bar", "new", "newer"); //only a single 'foo'!

		// [*] Changing only the history doesn't trigger pull-down options to recompute.
		//This is fine since its not possible to change the history while launch dialog is open.
		//However, it means test code must force a refresh in cases where only the history
		//changed.
	}

	///// EnableDebugSection///////////////////////////////////////////////////////

	public void testDebugValidator() throws Exception {
		assertEquals(Validator.OK, model.enableDebug.validator);
	}

	public void testDebugSetDefaults() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		enableDebug.setDefaults(wc);
		assertEquals(false, BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));
	}

	public void testDebugInitializeFrom() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		LiveVariable<Boolean> dirty = enableDebug.getDirtyState();
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();

		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, true);
		dirty.setValue(true);

		enableDebug.initializeFrom(wc);
		assertFalse(dirty.getValue());
		assertTrue(enableDebug.selection.getValue());

		dirty.setValue(true);
		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, false);

		enableDebug.initializeFrom(wc);

		assertFalse(dirty.getValue());
		assertFalse(enableDebug.selection.getValue());
	}

	public void testDebugPerformApply() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		LiveVariable<Boolean> dirty = enableDebug.getDirtyState();
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();

		enableDebug.selection.setValue(true);
		assertTrue(dirty.getValue());

		enableDebug.performApply(wc);

		assertFalse(dirty.getValue());
		assertTrue(BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));

		enableDebug.selection.setValue(false);
		assertTrue(dirty.getValue());

		enableDebug.performApply(wc);

		assertFalse(dirty.getValue());
		assertFalse(BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));
	}

	public void testDebugDirtyState() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		LiveVariable<Boolean> dirty = enableDebug.getDirtyState();
		dirty.setValue(false);

		enableDebug.selection.setValue(true);

		assertTrue(dirty.getValue());
	}

	///// EnableJmxSectiom /////////////////////////////////////////////

	public void testJmxValidator() throws Exception {
		EnableJmxFeaturesModel eJmxModel = this.model.enableJmx;
		LiveVariable<Boolean> jmx = eJmxModel.jmxEnabled;
		LiveVariable<Boolean> liveBean = eJmxModel.liveBeanEnabled;
		LiveVariable<Boolean> lifeCycle = eJmxModel.lifeCycleEnabled;
		LiveVariable<String> port = eJmxModel.port;
		LiveVariable<String> timeout = eJmxModel.terminationTimeout;
		LiveExpression<ValidationResult> validator = eJmxModel.getValidator();

		liveBean.setValue(true);
		lifeCycle.setValue(true);
		timeout.setValue("5000");
		port.setValue("8888");
		assertOk(validator);

		port.setValue("Unparseable");
		assertError("can't be parsed as an Integer", validator);

		jmx.setValue(false);
		liveBean.setValue(false);
		lifeCycle.setValue(false);
		assertOk(validator); //if disabled we shouldn't check the port as it doesn't matter.

		jmx.setValue(true);
		port.setValue("10000000");
		liveBean.setValue(true);
		assertError("should be smaller than", validator);

		jmx.setValue(false);
		liveBean.setValue(false);
		port.setValue("-111");
		assertOk(validator); //if disabled we shouldn't check the port as it doesn't matter.

		jmx.setValue(true);
		lifeCycle.setValue(true); //only enable lifeCycle
		assertFalse(liveBean.getValue());
		assertError("should be a positive", validator);

		port.setValue("0");
		assertOk(validator); // 0 is acceptable as it means 'choose dynamically'.

		port.setValue("  8888   ");
		assertOk(validator); // tolerate spaces

		port.setValue(null); //
		assertError("JMX Port must be specified", validator);

		port.setValue("   "); //
		assertError("JMX Port must be specified", validator);

		port.setValue("");
		assertError("JMX Port must be specified", validator);

		port.setValue("8888");
		assertOk(validator);

		timeout.setValue(null);
		assertError("Termination timeout must be specified", validator);

		timeout.setValue("");
		assertError("Termination timeout must be specified", validator);

		timeout.setValue("    ");
		assertError("Termination timeout must be specified", validator);

		timeout.setValue("   8888   "); // tolerate spaces
		assertOk(validator);

		timeout.setValue(" -555");
		assertError("Termination timeout must be positive", validator);

		timeout.setValue("abc");
		assertError("Termination timeout can't be parsed as an Integer", validator);
	}

	public void testJmxSetDefaults() throws Exception {
		EnableJmxFeaturesModel eJmxModel = this.model.enableJmx;

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		eJmxModel.setDefaults(wc);
		assertEquals(BootLaunchActivator.getInstance().isLiveBeanSupported(), BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));
		assertTrue(BootLaunchConfigurationDelegate.getEnableLifeCycle(wc));
		int jmxPort = Integer.parseInt(BootLaunchConfigurationDelegate.getJMXPort(wc));
		assertEquals(BootLaunchConfigurationDelegate.DEFAULT_JMX_PORT, jmxPort);
	}

	public void testJmxInitializeFrom() throws Exception {
		EnableJmxFeaturesModel eJmxModel = this.model.enableJmx;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		LiveVariable<Boolean> liveBean = eJmxModel.liveBeanEnabled;
		LiveVariable<Boolean> lifeCycle = eJmxModel.lifeCycleEnabled;
		LiveVariable<String> port = eJmxModel.port;
		LiveVariable<Boolean> dirty = eJmxModel.getDirtyState();

		boolean[] bools = {true, false};
		for (boolean b1 : bools) {
			for (boolean b2 : bools) {
				BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, b1);
				BootLaunchConfigurationDelegate.setEnableLifeCycle(wc, b2);
				BootLaunchConfigurationDelegate.setJMXPort(wc, "3456");
				dirty.setValue(true);

				eJmxModel.initializeFrom(wc);

				assertFalse(dirty.getValue());
				assertEquals("3456", port.getValue());
				assertEquals(b1, (boolean)liveBean.getValue());
				assertEquals(b2, (boolean)lifeCycle.getValue());
			}
		}
	}

	public void testJmxPerformApply() throws Exception {
		EnableJmxFeaturesModel eJmxModel = this.model.enableJmx;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();

		LiveVariable<Boolean> liveBean = eJmxModel.liveBeanEnabled;
		LiveVariable<Boolean> lifeCycle = eJmxModel.lifeCycleEnabled;
		LiveVariable<String> port = eJmxModel.port;
		LiveVariable<Boolean> dirty = eJmxModel.getDirtyState();

		port.setValue("1234");

		boolean[] bools = {true, false};
		for (boolean b1 : bools) {
			for (boolean b2 : bools) {
				liveBean.setValue(b1);
				lifeCycle.setValue(b2);
				assertTrue(dirty.getValue());

				eJmxModel.performApply(wc);

				assertFalse(dirty.getValue());
				assertEquals("1234", BootLaunchConfigurationDelegate.getJMXPort(wc));
				assertEquals(b1, BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));
				assertEquals(b2, BootLaunchConfigurationDelegate.getEnableLifeCycle(wc));
			}
		}

		//port
		port.setValue("   8888   ");
		assertTrue(dirty.getValue());
		eJmxModel.performApply(wc);
		assertFalse(dirty.getValue());
		assertEquals("8888", BootLaunchConfigurationDelegate.getJMXPort(wc));
	}

	public void testLiveBeanDirtyState() throws Exception {
		EnableJmxFeaturesModel eJmxModel = this.model.enableJmx;

		LiveVariable<Boolean> liveBean = eJmxModel.liveBeanEnabled;
		LiveVariable<Boolean> lifeCycle = eJmxModel.lifeCycleEnabled;
		LiveVariable<String> port = eJmxModel.port;
		LiveVariable<Boolean> dirty = eJmxModel.getDirtyState();

		dirty.setValue(false);
		port.setValue("something");
		assertTrue(dirty.getValue());

		dirty.setValue(false);
		liveBean.setValue(!liveBean.getValue());
		assertTrue(dirty.getValue());

		dirty.setValue(false);
		lifeCycle.setValue(!lifeCycle.getValue());
		assertTrue(dirty.getValue());
	}

	///// PropertiesTableSection ??? can't be tested in its current form (no separate 'model' to test)


	/**
	 * Verify contents of 'pulldown' menu. This ignores the order of the elements
	 * because discovered elements may come in different orders... but does not
	 * ignore when there are duplicates.
	 */
	private void assertPulldown(ProfileLaunchTabModel profile, String... expecteds) {
		Arrays.sort(expecteds);
		String [] actuals = profile.profileOptions().getValue();
		actuals = Arrays.copyOf(actuals, actuals.length);
		Arrays.sort(actuals);
		assertArrayEquals(expecteds, actuals);
	}

}
