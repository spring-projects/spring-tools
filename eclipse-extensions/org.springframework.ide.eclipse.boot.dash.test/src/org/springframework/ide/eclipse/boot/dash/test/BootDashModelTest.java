/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.ide.eclipse.boot.dash.test.actuator.RequestMappingAsserts.assertRequestMappingWithPath;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.bootVersionAtLeast;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.setPackage;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withStarters;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.assertElements;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.createFile;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.setContents;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.core.IMavenCoordinates;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.MavenId;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.core.cli.BootCliCommand;
import org.springframework.ide.eclipse.boot.core.cli.BootInstallManager;
import org.springframework.ide.eclipse.boot.core.cli.install.CloudCliInstall;
import org.springframework.ide.eclipse.boot.core.cli.install.IBootInstall;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElementsFilterBoxModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElement;
import org.springframework.ide.eclipse.boot.dash.model.ButtonModel;
import org.springframework.ide.eclipse.boot.dash.model.LaunchConfDashElement;
import org.springframework.ide.eclipse.boot.dash.model.LocalCloudServiceDashElement;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel.FilterChoice;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.actuator.RequestMapping;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetTypes;
import org.springframework.ide.eclipse.boot.dash.util.CollectionUtils;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.util.PortFinder;
import org.springframework.ide.eclipse.boot.test.AutobuildingEnablement;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.WizardConfigurer;
import org.springframework.ide.eclipse.boot.test.util.TestBracketter;
import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springsource.ide.eclipse.commons.core.ZipFileUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.maintype.MainTypeFinder;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class BootDashModelTest {

	private static final long MODEL_UPDATE_TIMEOUT = 10000; // short, should be nearly instant
	private static final long RUN_STATE_CHANGE_TIMEOUT = 40000;
	private static final long MAVEN_BUILD_TIMEOUT = 80000;

	private SpringBootCore springBootCore = SpringBootCore.getDefault(); // should be getting this via projects harness?
	private TestBootDashModelContext context;
	private BootProjectTestHarness projects;
	private BootDashModel model;

	private PortFinder portFinder = new PortFinder();

	@Rule
	public AutobuildingEnablement autobuild = new AutobuildingEnablement(false);

	@Rule
	public TestBracketter testBracketer = new TestBracketter();

	static final String ENABLE_CLOUD_CLI_BUTTON = "Install local cloud services";

	@Rule
	public DumpBootProcessOutput processOutput = new DumpBootProcessOutput();


	@Test public void testAutoInstallSpringCloudCLI() throws Exception {
		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		assertNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));
		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		model.getViewModel().getToggleFilters().getSelectedFilters().remove(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES);
		ACondition.waitFor("Cloud CLI Button to appear", 500, () -> harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON));

		ButtonModel button = harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON);

		when(ui().confirmOperation(contains("Confirm Installation of Spring Cloud CLI"), contains("will be installed into"))).thenReturn(true);
		//Actual confirm dialog prompts example: [Confirm Installation of Spring Cloud CLI?, Spring Cloud CLI version 1.3.3.RELEASE will be installed into Boot 1.5.4.RELEASE.]

		button.perform(ui());
		assertFalse(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		ACondition.waitFor("Local services to appear", MAVEN_BUILD_TIMEOUT, () -> {
			assertServiceElements("dataflow", "zipkin", "eureka", "kafka", "h2", "configserver", "hystrixdashboard", "stubrunner");
		});
		assertNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));

		verify(ui()).confirmOperation(anyString(), anyString());
		verifyNoMoreInteractions(ui());
		reset(ui());

		// State preserved/restored on reload?
		harness.reload();
		model = harness.getRunTargetModel(RunTargetTypes.LOCAL);

		assertServiceElements(/*NONE*/); //TODO: This test is subject to race condition, the elements will appear, we don't know how fast.
				//If very fast... then this test will fail! To avoid the race condition we must somehow add a mechanic in the harness to
				//to be able to hold-up the stuff that resolves local services until we explicitly allow it to proceed.
		assertFalse(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));

		ACondition.waitFor("Local services to appear", MAVEN_BUILD_TIMEOUT, () -> {
			assertServiceElements("dataflow", "zipkin", "eureka", "kafka", "h2", "configserver", "hystrixdashboard", "stubrunner");
		});
		assertNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));
		verifyZeroInteractions(ui()); //Since cloud cli already installed we should not be prompted whether we want to install it.
	}

	@Test public void testAutoInstallSpringCloudCLIRejected() throws Exception {
		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		assertNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));
		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		model.getViewModel().getToggleFilters().getSelectedFilters().remove(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES);
		ACondition.waitFor("Cloud CLI Button to appear", 500, () -> harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON));

		ButtonModel button = harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON);
		when(ui().confirmOperation(contains("Confirm Installation of Spring Cloud CLI"), contains("will be installed into"))).thenReturn(false);
		button.perform(ui());

		harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON);
		assertFalse(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));

		assertServiceElements(/*NONE*/);
		verify(ui()).confirmOperation(anyString(), anyString());
		verifyNoMoreInteractions(ui());
	}

	@Test public void testAutoInstallSpringCloudCLICustom() throws Exception {
		//We support installing spring-cloud CLI into a custom configured spring boot cli
		String cliVersion = "2.1.4.RELEASE"; // consider updating once in a while to remain 'current'.
		URL customZipUrl = new URL("https://repo.spring.io/release/org/springframework/boot/spring-boot-cli/"+cliVersion+"/spring-boot-cli-"+cliVersion+"-bin.zip");
		IBootInstall customCliInstall = performCustomCliInstall(customZipUrl);

		//Some sanity checks... to see if performCustomCliInstall works as expected.
		assertEquals("custom-spring-"+cliVersion, customCliInstall.getName());
		assertEquals(cliVersion, customCliInstall.getVersion());
		assertEquals(customCliInstall, context.getBootInstallManager().getDefaultInstall());
		assertTrue(context.getBootInstallManager().getInstalls().contains(customCliInstall));

		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		assertNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));
		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		model.getViewModel().getToggleFilters().getSelectedFilters().remove(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES);
		ACondition.waitFor("Cloud CLI Button to appear", 500, () -> harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON));

		when(ui().confirmOperation(contains("Confirm Installation of Spring Cloud CLI"), contains("will be installed into"))).thenReturn(true);
		ButtonModel button = harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON);
		button.perform(ui());
		assertFalse(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));

		ACondition.waitFor("Local services to appear", MAVEN_BUILD_TIMEOUT, () -> {
			assertServiceElements("dataflow", "zipkin", "eureka", "kafka", "h2", "configserver", "hystrixdashboard", "stubrunner");
		});
		assertNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));

		verify(ui()).confirmOperation(anyString(), anyString());
		verifyNoMoreInteractions(ui());
	}

	@Test public void testAutoInstallSpringCloudCLINotSupported() throws Exception {
		//Behaves reasonable with ancient versions of boot?

		String cliVersion = "1.0.0.RELEASE"; // Deliberately ancient
		URL customZipUrl = new URL("https://repo.spring.io/release/org/springframework/boot/spring-boot-cli/"+cliVersion+"/spring-boot-cli-"+cliVersion+"-bin.zip");
		IBootInstall customCliInstall = performCustomCliInstall(customZipUrl);

		//Some sanity checks... to see if performCustomCliInstall works as expected.
		assertEquals("custom-spring-"+cliVersion, customCliInstall.getName());
		assertEquals(cliVersion, customCliInstall.getVersion());
		assertEquals(customCliInstall, context.getBootInstallManager().getDefaultInstall());
		assertTrue(context.getBootInstallManager().getInstalls().contains(customCliInstall));

		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		assertNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));
		assertTrue(model.getViewModel().getToggleFilters().getSelectedFilters().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES));
		model.getViewModel().getToggleFilters().getSelectedFilters().remove(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES);
		ACondition.waitFor("Cloud CLI Button to appear", 500, () -> harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON));

		ButtonModel button = harness.assertButton(model, ENABLE_CLOUD_CLI_BUTTON);
		button.perform(ui());
		assertNotNull(harness.getButton(model, ENABLE_CLOUD_CLI_BUTTON));
		verify(ui()).errorPopup(contains("Auto installation of Spring Cloud CLI not possible"), contains("Couldn't determine a compatible Spring Cloud CLI version"));
		verifyNoMoreInteractions(ui());
	}

	private IBootInstall performCustomCliInstall(URL customZipUrl) throws Exception {
		File workdir = StsTestUtil.createTempDirectory();
		File customZip = new File(workdir, "cli.zip");
		File installLocation = new File(workdir, "cli");
		FileUtils.copyURLToFile(customZipUrl, customZip);
		ZipFileUtil.unzip(customZip, installLocation, new NullProgressMonitor());
		installLocation = installLocation.listFiles()[0];

		BootInstallManager bootInstallMgr = context.getBootInstallManager();
		IBootInstall customInstall = bootInstallMgr.newInstall(installLocation.toURI().toString(), "custom-"+installLocation.getName());
		bootInstallMgr.setDefaultInstall(customInstall); //Note: implictly adds the default install if its not there yet,
		FileUtils.deleteQuietly(customZip);
		return customInstall;
	}

	@Test public void testInvalidCloudCliInstall() throws Exception {
		String error = "com.google.common.util.concurrent.UncheckedExecutionException: java.lang.NullPointerException\n" +
				"	at com.google.common.cache.LocalCache$Segment.get(LocalCache.java:2199)\n" +
				"	at com.google.common.cache.LocalCache.get(LocalCache.java:3932)\n" +
				"	at com.google.common.cache.LocalCache.getOrLoad(LocalCache.java:3936)";
		model.getViewModel().getToggleFilters().getSelectedFilters().remove(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES);

		BootCliCommand mockCommand = mock(BootCliCommand.class);
		doReturn(error).when(mockCommand).getOutput();
		doReturn(0).when(mockCommand).execute(CloudCliInstall.LIST_SERVICES_COMMAND);

		IBootInstall defaultInstall = spy(context.getBootInstallManager().getDefaultInstall());
		CloudCliInstall mockCloudCliInstall = spy(new CloudCliInstall(defaultInstall));
		doReturn(new Version(1, 5, 1, null)).when(mockCloudCliInstall).getVersion();
		doReturn(mockCommand).when(mockCloudCliInstall).createCommand();
		doReturn(mockCloudCliInstall).when(defaultInstall).getExtension(CloudCliInstall.class);

		assertEquals(0, mockCloudCliInstall.getCloudServices().length);

		context.getBootInstallManager().setDefaultInstall(defaultInstall);

		assertServiceElements();
	}

	/**
	 * Test that newly created spring boot project gets added to the model.
	 */
	@Test public void testNewSpringBootProject() throws Exception {

//		assertWorkspaceProjects(/*none*/);
		assertNonServiceElements(/*none*/);

		String projectName = "testProject";
		createBootProject(projectName);
		new ACondition("Model update", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				assertNonServiceElements("testProject");
				return true;
			}
		};

		BootDashElement projectEl = getElement("testProject");
		assertTrue(projectEl.getCurrentChildren().isEmpty());
	}

	/**
	 * Test that project with multiple associated launch configs has
	 * a child for each config.
	 */
	@Test
	public void testSpringBootProjectChildren() throws Exception {

//		assertWorkspaceProjects(/*none*/);
		assertNonServiceElements(/*none*/);

		String projectName = "testProject";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		new ACondition("Model update", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				assertNonServiceElements("testProject");
				return true;
			}
		};

		BootDashElement projectEl = getElement("testProject");
		assertTrue(projectEl.getCurrentChildren().isEmpty());

		ILaunchConfiguration conf1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		ILaunchConfiguration conf2 = BootLaunchConfigurationDelegate.createConf(javaProject);
		assertFalse(conf1.equals(conf2));

		ACondition.waitFor("Children to appear", 3000, () -> {
			assertEquals(2, projectEl.getCurrentChildren().size());
		});

		conf1.delete();

		ACondition.waitFor("Child to disappear", 3000, () -> {
			assertEquals(1, projectEl.getCurrentChildren().size());
		});
	}


	/**
	 * Test that when a launch config is marked as 'hidden' it is not part of the model.
	 */
	@Test
	public void testSpringBootProjectHiddenChildren() throws Exception {
		assertNonServiceElements(/*none*/);

		String projectName = "testProject";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		new ACondition("Model update", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				assertNonServiceElements("testProject");
				return true;
			}
		};

		final BootDashElement projectEl = getElement("testProject");
		assertTrue(projectEl.getCurrentChildren().isEmpty());

		final ILaunchConfiguration[] conf = new ILaunchConfiguration[3];
		final BootDashElement[] el = new BootDashElement[conf.length];
		for (int i = 0; i < conf.length; i++) {
			conf[i] =  BootLaunchConfigurationDelegate.createConf(javaProject);
			el[i] = harness.getElementFor(conf[i]);
		}

		new ACondition("Wait for children", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				assertEquals(ImmutableSet.copyOf(el), projectEl.getCurrentChildren());
				return true;
			}
		};

		hide(conf[2]);
		new ACondition("Wait for child to disapear", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				assertEquals(ImmutableSet.of(el[0], el[1]), projectEl.getCurrentChildren());
				return true;
			}
		};

		hide(conf[1]);
		new ACondition("Wait for another child to disapear", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				//since there's just one conf left it is not shown as a child
				assertEquals(ImmutableSet.of(el[0]), projectEl.getCurrentChildren());
				return true;
			}
		};

		hide(conf[0]);
		new ACondition("Wait for last child to disapear", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				//since there's just one conf left it is not shown as a child
				assertEquals(ImmutableSet.of(), projectEl.getCurrentChildren());
				return true;
			}
		};
	}

	private void hide(ILaunchConfiguration conf) throws Exception {
		ILaunchConfigurationWorkingCopy wc = conf.getWorkingCopy();
		BootLaunchConfigurationDelegate.setHiddenFromBootDash(wc, true);
		wc.doSave();
	}

	private IProject createBootProject(String projectName, WizardConfigurer... extraConfs) throws Exception {
		return projects.createBootWebProject(projectName, extraConfs);
	}

	/**
	 * Test that when project is deleted from workspace it also deleted from the model
	 */
	@Test public void testDeleteProject() throws Exception {
		String projectName = "testProject";
		IProject project = createBootProject(projectName);

		waitNonServiceElements("testProject");
		project.delete(/*delete content*/true, /*force*/true, /*progress*/null);
		waitNonServiceElements(/*none*/);
	}

	/**
	 * Test that when closed/opened it is removed/added to the model
	 */
	@Test public void testCloseAndOpenProject() throws Exception {
		String projectName = "testProject";
		IProject project = createBootProject(projectName);

		waitNonServiceElements("testProject");

		project.close(null);
		waitNonServiceElements();

		project.open(null);
		waitNonServiceElements("testProject");
	}


	/**
	 * Test that deleting a running launch conf works properly:
	 *  1) orphaned launch is terminated
	 *  2) BootProjectDashElement runstate is updated.
	 */
	@Test public void testDeleteRunningLaunchConfig() throws Exception {
		doTestDeleteRunningLaunchConf(RunState.RUNNING);
	}

	/**
	 * Test that deleting a running launch conf, (in debug mode) works properly:
	 *  1) orphaned launch is terminated
	 *  2) BootProjectDashElement runstate is updated.
	 */
	@Test public void testDeleteDebuggingLaunchConfig() throws Exception {
		doTestDeleteRunningLaunchConf(RunState.DEBUGGING);
	}

	private void doTestDeleteRunningLaunchConf(RunState runState) throws Exception, CoreException {
		String projectName = "testProject";
		createBootProject(projectName);
		waitNonServiceElements(projectName);

		BootProjectDashElement element = getElement(projectName);
		element.openConfig(ui()); //Ensure that at least one launch config exists.
		verify(ui()).openLaunchConfigurationDialogOnGroup(any(ILaunchConfiguration.class), any(String.class));
		verifyNoMoreInteractions(ui());
		ACondition.waitFor("child", 3000, () -> {
			assertNotNull(getSingleValue(element.getCurrentChildren()));
		});
		LaunchConfDashElement launchConfElement = (LaunchConfDashElement) getSingleValue(element.getCurrentChildren());
		ILaunchConfiguration launchConf = getSingleValue(launchConfElement.getLaunchConfigs());

		element.restart(runState, null);
		waitForState(element, runState);
		waitForState(launchConfElement, runState);

		ILaunch launch = getSingleValue(launchConfElement.getLaunches());
		assertFalse(launch.isTerminated());

		launchConf.delete();

		ACondition.waitFor("Expectations after launchConf deleted", 2000, () -> {
			assertTrue("launch terminated", launch.isTerminated());
			assertEquals(ImmutableSet.of(), element.getChildren().getValues());
			assertEquals(RunState.INACTIVE, element.getRunState());
		});
	}


	private UserInteractions ui() {
		return context.injections.getBean(AllUserInteractions.class);
	}

	/**
	 * Test that element state listener for launch conf element is notified when it is
	 * launched via its project.
	 */
	@Test public void testLaunchConfRunStateChanges() throws Exception {
		doTestLaunchConfRunStateChanges(RunState.RUNNING);
	}

	/**
	 * Test that element state listener for launch conf element is notified when it is
	 * launched via its project.
	 */
	@Test public void testLaunchConfDebugStateChanges() throws Exception {
		doTestLaunchConfRunStateChanges(RunState.DEBUGGING);
	}

	protected void doTestLaunchConfRunStateChanges(RunState runState) throws Exception {
		String projectName = "testProject";
		createBootProject(projectName);
		waitNonServiceElements(projectName);

		BootProjectDashElement element = getElement(projectName);
		element.openConfig(ui()); //Ensure that at least one launch config exists.
		verify(ui()).openLaunchConfigurationDialogOnGroup(any(ILaunchConfiguration.class), any(String.class));
		verifyNoMoreInteractions(ui());
		ACondition.waitFor("child", 3000, () -> {
			assertNotNull(getSingleValue(element.getCurrentChildren()));
		});
		BootDashElement childElement = getSingleValue(element.getCurrentChildren());

		ElementStateListener listener = mock(ElementStateListener.class);
		model.addElementStateListener(listener);
//		System.out.println("Element state listener ADDED");
//		model.addElementStateListener(new ElementStateListener() {
//			public void stateChanged(BootDashElement e) {
//				System.out.println("Changed: "+e);
//			}
//		});

		element.restart(runState, null);
		waitForState(element, runState);
		waitForState(childElement, runState);

		ElementStateListener oldListener = listener;

		ACondition.waitFor("listener calls", 1000, () -> {
			verify(oldListener, times(4)).stateChanged(element);
			verify(oldListener, times(4)).stateChanged(childElement);
		});
		model.removeElementStateListener(oldListener);

//		System.out.println("Element state listener REMOVED");

		listener = mock(ElementStateListener.class);
		model.addElementStateListener(listener);

		element.stop();
		waitForState(element, RunState.INACTIVE);
		waitForState(childElement, RunState.INACTIVE);

		//4 changes:  INACTIVE -> STARTING, STARTING -> RUNNING, livePort(set), actualInstances
		verify(oldListener, times(4)).stateChanged(element);
		verify(oldListener, times(4)).stateChanged(childElement);
		//3 changes: RUNNING -> INACTIVE, liveport(unset), actualInstances
		verify(listener, times(3)).stateChanged(element);
		verify(listener, times(3)).stateChanged(childElement);
	}


	private <T> T getSingleValue(ImmutableSet<T> values) {
		assertEquals("Unexpected number of values in "+values, 1, values.size());
		for (T e : values) {
			return e;
		}
		throw new IllegalStateException("This code should be unreachable");
	}

	@Test @Ignore /* Ignored because unstable in CI builds, and its of diminishing importance */
	public void startLifeCycleDisabledApp() throws Exception {
		String projectName = "some-app";
		IProject project = createBootProject(projectName, bootVersionAtLeast("1.3"));

		BootProjectDashElement element = getElement(projectName);
		element.openConfig(ui()); //Ensure that at least one launch config exists.
		verify(ui()).openLaunchConfigurationDialogOnGroup(any(ILaunchConfiguration.class), any(String.class));
		verifyNoMoreInteractions(ui());

		//Disable lifecycle mgmt on config
		ACondition.waitFor("child", 3000, () -> {
			assertNotNull(getSingleValue(element.getCurrentChildren()));
		});
		LaunchConfDashElement childElement = (LaunchConfDashElement) getSingleValue(element.getCurrentChildren());
		ILaunchConfigurationWorkingCopy wc = childElement.getActiveConfig().getWorkingCopy();
		assertNotNull(BootLaunchConfigurationDelegate.getMainType(wc));
		BootLaunchConfigurationDelegate.setEnableLifeCycle(wc, false);
		wc.doSave();

		doStartBootAppWithoutLifeCycleTest(element, RunState.RUNNING);
		doStartBootAppWithoutLifeCycleTest(element, RunState.DEBUGGING);
	}

	@Test @Ignore /* Ignored because unstable in CI builds, and its of diminishing importance */
	public void startOldBootApp() throws Exception {
		String projectName = "boot12";
		createPredefinedMavenProject(projectName);
		BootProjectDashElement element = getElement(projectName);
		doStartBootAppWithoutLifeCycleTest(element, RunState.RUNNING);
		doStartBootAppWithoutLifeCycleTest(element, RunState.DEBUGGING);
	}

	private void doStartBootAppWithoutLifeCycleTest(BootProjectDashElement app, RunState runOrDebug) throws Exception {
		try {
			waitForState(app, RunState.INACTIVE);
			app.restart(runOrDebug, ui());
			Thread.sleep(2000); //Give app a little bit of time to get going.
			waitForState(app, runOrDebug);
		} finally {
			ACondition.waitFor("stop hammering", 20000, () -> {
				app.stop(false);
				assertEquals(RunState.INACTIVE, app.getRunState());
			});
		}
	}

	private IProject createPredefinedMavenProject(String projectName) throws Exception {
		return BootProjectTestHarness.createPredefinedMavenProject(projectName, "org.springframework.ide.eclipse.boot.dash.test");
	}

	/**
	 * Test that element state listener is notified when a project is launched and terminated.
	 */
	@Test public void testRunStateChanges() throws Exception {
		doTestRunStateChanges(RunState.RUNNING);
	}

	/**
	 * Test that element state listener is notified when a project is launched in Debug mode and terminated.
	 */
	@Test public void testDebugStateChanges() throws Exception {
		doTestRunStateChanges(RunState.DEBUGGING);
	}

	protected void doTestRunStateChanges(RunState runState) throws Exception {
		String projectName = "testProject";
		createBootProject(projectName);
		waitNonServiceElements(projectName);

		ElementStateListener listener = mock(ElementStateListener.class);
		model.addElementStateListener(listener);
		//System.out.println("Element state listener ADDED");
		BootDashElement element = getElement(projectName);
		element.restart(runState, null);
		waitForState(element, runState);

		ElementStateListener oldListener = listener;
		ACondition.waitFor("listener calls", 1000, () ->
			verify(oldListener, times(4)).stateChanged(element)
		);
		model.removeElementStateListener(oldListener);
		//System.out.println("Element state listener REMOVED");

		listener = mock(ElementStateListener.class);
		model.addElementStateListener(listener);

		element.stop();
		waitForState(element, RunState.INACTIVE);

		//4 changes:  INACTIVE -> STARTING, STARTING -> RUNNING, livePort(set), actualInstances++
		verify(oldListener, times(4)).stateChanged(element);
		//3 changes: RUNNING -> INACTIVE, liveport(unset), actualInstances--
		ElementStateListener newListener = listener;
		ACondition.waitFor("listener calls", 1000, () ->
			verify(newListener, times(3)).stateChanged(element)
		);
	}

	@Test public void projectElementDisposedWhenProjectClosed() throws Exception {
		String projectName = "testProject";
		IProject project = createBootProject(projectName);
		waitNonServiceElements(projectName);

		BootProjectDashElement projectElement = getElement(projectName);
		LiveVariable<Boolean> disposed = new LiveVariable<>(false);
		projectElement.onDispose((d) -> disposed.setValue(true));

		project.close(new NullProgressMonitor());

		ACondition.waitFor("Element disposed", 100, () -> {
			assertTrue(disposed.getValue());
		});
	}

	@Test public void testRestartRunningProcessTest() throws Exception {
		String projectName = "testProject";
		createBootProject(projectName);
		waitNonServiceElements(projectName);

		final RunState[] RUN_STATES = {
				RunState.RUNNING,
				RunState.DEBUGGING
		};

		for (RunState fromState : RUN_STATES) {
			for (RunState toState : RUN_STATES) {
				doRestartTest(projectName, fromState, toState);
			}
		}
	}

	@Test public void testDevtoolsPortRefreshedOnRestart() throws Exception {
		//Test that the local bootdash element 'liveport' is updated when boot devtools
		// does an in-place restart of the app, changing the port that it runs on.
		String projectName = "some-project-with-devtools";
		createBootProject(projectName, bootVersionAtLeast("1.3.0"),  //1.3.0 required for lifecycle & devtools support
				withStarters("devtools")
		);

		final BootProjectDashElement project = getElement(projectName);
		try {
			waitForState(project, RunState.INACTIVE);
			System.out.println("Starting "+project);
			project.restart(RunState.RUNNING, ui());
			waitForState(project, RunState.RUNNING);

			BootDashElement launch = CollectionUtils.getSingle(project.getChildren().getValues());

			int defaultPort = 8080;
			int changedPort = 8765;

			waitForPort(project, defaultPort);

			System.out.println("Changing port in application.properties to "+changedPort);
			IFile props = project.getProject().getFile(new Path("src/main/resources/application.properties"));
			setContents(props, "server.port="+changedPort);
			System.out.println("Rebuilding project...");
			StsTestUtil.assertNoErrors(project.getProject());
			System.out.println("Rebuilding project... DONE");
			   //builds the project... should trigger devtools to 'refresh'.

			waitForPort(project, changedPort);
			waitForPort(launch, changedPort);

			//Now try that this also works in debug mode...
			System.out.println("Restart project in DEBUG mode...");
			project.restart(RunState.DEBUGGING, ui());
			waitForState(project, RunState.DEBUGGING);

			waitForPort(project, changedPort);
			waitForPort(launch, changedPort);

			System.out.println("Changing port in application.properties to "+defaultPort);
			setContents(props, "server.port="+defaultPort);
			System.out.println("Rebuilding project...");
			StsTestUtil.assertNoErrors(project.getProject());
			System.out.println("Rebuilding project... DONE");
			   //builds the project... should trigger devtools to 'refresh'.
			waitForPort(project, defaultPort);
			waitForPort(launch, defaultPort);

		} finally {
			System.out.println("Cleanup: stop "+project);
			project.stop();
			waitForState(project, RunState.INACTIVE);
		}
	}

	protected void waitForPort(final BootDashElement element, final int expectedPort) throws Exception {
		new ACondition("Wait for port on "+element.getName()+" to change to "+expectedPort, 5000) { //Devtools should restart really fast
			@Override
			public boolean test() throws Exception {
				assertEquals(ImmutableSet.of(expectedPort), element.getLivePorts());
				assertEquals(expectedPort, element.getLivePort());
				return true;
			}
		};
	}

	@Test public void testStartingStateObservable() throws Exception {
		//Test that, for boot project that supports it, the 'starting' state
		// is observable in the model.
		String projectName = "some-project";
		createBootProject(projectName,
				bootVersionAtLeast("1.3.0") //1.3.0 required for lifecycle support
		);
		BootProjectDashElement element = getElement(projectName);
		try {
			waitForState(element, RunState.INACTIVE);

			RecordingElementChangedListener recordedChanges1 = new RecordingElementChangedListener();
			model.addElementStateListener(recordedChanges1);

			element.restart(RunState.RUNNING, ui());
			waitForState(element, RunState.RUNNING);

			List<RunState> states1 = recordedChanges1.getRecordedRunStates();
			assertEquals(4, states1.size());
			assertEquals(RunState.STARTING, states1.get(0));
			assertEquals(RunState.RUNNING, states1.get(1));

			model.removeElementStateListener(recordedChanges1);

			RecordingElementChangedListener recordedChanges2 = new RecordingElementChangedListener();
			model.addElementStateListener(recordedChanges2);

			element.restart(RunState.DEBUGGING, ui());
			waitForState(element, RunState.DEBUGGING);

			List<RunState> states2 = recordedChanges2.getRecordedRunStates();
			assertEquals(7, states2.size());
			assertEquals(RunState.STARTING, states2.get(3));
			assertEquals(RunState.DEBUGGING, states2.get(4));

			model.removeElementStateListener(recordedChanges2);

		} finally {
			element.stop();
			waitForState(element, RunState.INACTIVE);
		}
	}

	private void doRestartTest(String projectName, RunState fromState, RunState toState) throws Exception {
		BootProjectDashElement element = getElement(projectName);
		try {
			element.restart(fromState, ui());
			waitForState(element, fromState);

			final ILaunch launch = getActiveLaunch(element);

			element.restart(toState, ui());

			//Watch out for race conditions... we can't really reliably observe the
			// 'terminated' state of the element, as we don't know how long it will
			// last and the 'restart' operation may happen concurrently with the testing
			// thread. Therefore we observe the terminated state of the actual launch.
			// Restarting the project will/should terminate the old launch and then
			// create a new launch.

			new ACondition("Wait for launch termination", RUN_STATE_CHANGE_TIMEOUT) {
				public boolean test() throws Exception {
					return launch.isTerminated();
				}
			};

			waitForState(element, toState);
		} finally {
			element.stop();
			waitForState(element, RunState.INACTIVE);
		}
	}

	@Test public void livePortSummaryAndInstanceCounts() throws Exception {
		String projectName = "some-project";
		createBootProject(projectName, bootVersionAtLeast("1.3.0")); //1.3.0 required for lifecycle support.
		final BootProjectDashElement project = getElement(projectName);
		try {
			assertEquals(RunState.INACTIVE, project.getRunState());
			assertTrue(project.getLivePorts().isEmpty()); // live port is 'unknown' if app is not running
			assertInstances("0/1", project);
			assertInstancesLabel("", project); //label hidden for ?/1 case

			IType mainType = MainTypeFinder.guessMainTypes(project.getJavaProject(), new NullProgressMonitor())[0];

			final int port1 = portFinder.findUniqueFreePort();
			ILaunchConfiguration config1 = BootLaunchConfigurationDelegate.createConf(mainType);
			setPort(config1, port1);

			final int port2 = portFinder.findUniqueFreePort();
			ILaunchConfiguration config2 = BootLaunchConfigurationDelegate.createConf(mainType);
			setPort(config2, port2);

			final BootDashElement el1 = harness.getElementFor(config1);
			final BootDashElement el2 = harness.getElementFor(config2);

			assertInstances("0/1", el1);
			assertInstancesLabel("", el1); // hidden label for ?/1 case
			assertInstances("0/1", el2);
			assertInstancesLabel("", el2); // hidden label for ?/1 case

			el1.restart(RunState.RUNNING, ui());
			el2.restart(RunState.RUNNING, ui());

			waitForState(el1, RunState.RUNNING);
			waitForState(el2, RunState.RUNNING);

			new ACondition("check port summary", MODEL_UPDATE_TIMEOUT) {
				public boolean test() throws Exception {
					assertInstances("2/2", project);
					assertInstancesLabel("2/2", project);
					assertInstances("1/1", el1);
					assertInstancesLabel("", el1); // hidden label for ?/1 case
					assertInstances("1/1", el2);
					assertInstancesLabel("", el2); // hidden label for ?/1 case

					assertEquals(port1, el1.getLivePort());
					assertEquals(port2, el2.getLivePort());

					assertEquals(ImmutableSet.of(port1, port2), project.getLivePorts());
					return true;
				}
			};

			el1.stop();
			new ACondition("check port summary", MODEL_UPDATE_TIMEOUT) {
				public boolean test() throws Exception {
					assertEquals(ImmutableSet.of(port2), project.getLivePorts());
					assertEquals(1, project.getActualInstances());
					assertEquals(2, project.getDesiredInstances());

					assertInstances("1/2", project);
					assertInstancesLabel("1/2", project);
					assertInstances("0/1", el1);
					assertInstancesLabel("", el1); // hidden label for ?/1 case
					assertInstances("1/1", el2);
					assertInstancesLabel("", el2); // hidden label for ?/1 case

					return true;
				}
			};


		} finally {
			project.stopSync();
		}
	}

	private void assertInstances(String expect, BootDashElement e) {
		assertEquals(expect, e.getActualInstances()+"/"+e.getDesiredInstances());
	}

	private void assertInstancesLabel(String expect, BootDashElement e) {
		harness.assertInstancesLabel(expect, e);
	}

	@Test public void livePort() throws Exception {
		String projectName = "some-project";
		createBootProject(projectName, bootVersionAtLeast("1.3.0")); //1.3.0 required for lifecycle support.

		final BootProjectDashElement element = getElement(projectName);
		assertEquals(RunState.INACTIVE, element.getRunState());
		assertEquals(-1, element.getLivePort()); // live port is 'unknown' if app is not running
		try {
			waitForState(element, RunState.INACTIVE);

			element.restart(RunState.RUNNING, ui());
			waitForState(element, RunState.RUNNING);

			new ACondition(4000) {
				public boolean test() throws Exception {
					assertEquals(8080, element.getLivePort());
					return true;
				}
			};

			//Change port in launch conf and restart
			ILaunchConfiguration conf = element.getActiveConfig();
			ILaunchConfigurationWorkingCopy wc = conf.getWorkingCopy();
			BootLaunchConfigurationDelegate.setRawApplicationProperties(wc, "server.port=6789");
			wc.doSave();
			final BootDashElement childElement = getSingleValue(element.getCurrentChildren());

			new ACondition(4000) {
				public boolean test() throws Exception {
					assertEquals(8080, element.getLivePort()); // port still the same until we restart
					assertEquals(8080, childElement.getLivePort());
					return true;
				}
			};

			element.restart(RunState.RUNNING, ui());
			waitForState(element, RunState.RUNNING);
			new ACondition(4000) {
				public boolean test() throws Exception {
					assertEquals(6789, element.getLivePort());
					assertEquals(6789, childElement.getLivePort());
					return true;
				}
			};

		} finally {
			element.stop();
			waitForState(element, RunState.INACTIVE);
		}
	}

	@Test public void testRequestMappings_2_x() throws Exception {
		String projectName = "actuated-project";
		IProject project = createBootProject(projectName,
				bootVersionAtLeast("2.0"), //Ensure we test with 2.0, actuator endpoints are changed in 2.0!
				withStarters("web", "actuator"),     //required to actually *have* an actuator
				setPackage("com.example.demo")
		);
		createFile(project, "src/main/java/com/example/demo/HelloController.java",
				"package com.example.demo;\n" +
				"\n" +
				"import org.springframework.web.bind.annotation.RequestMapping;\n" +
				"import org.springframework.web.bind.annotation.RestController;\n" +
				"\n" +
				"@RestController\n" +
				"public class HelloController {\n" +
				"\n" +
				"	@RequestMapping(\"/hello\")\n" +
				"	public String hello() {\n" +
				"		return \"Hello, World!\";\n" +
				"	}\n" +
				"\n" +
				"}\n"
		);
		StsTestUtil.assertNoErrors(project);
		final BootDashElement element = getElement(projectName);
		try {
			waitForState(element, RunState.INACTIVE);
			assertNull(element.getLiveRequestMappings().orElse(null)); // unknown since can only be determined when app is running

			element.restart(RunState.RUNNING, ui());
			waitForState(element, RunState.RUNNING);
			new ACondition("Wait for request mappings", MODEL_UPDATE_TIMEOUT) {
				public boolean test() throws Exception {
					List<RequestMapping> mappings = element.getLiveRequestMappings().orElse(null);
					assertNotNull(mappings); //Why is the test sometimes failing here?
					assertTrue(!mappings.isEmpty()); //Even though this is an 'empty' app should have some mappings,
					                                 // for example an 'error' page.
					return true;
				}
			};

			List<RequestMapping> mappings = element.getLiveRequestMappings().orElse(null);
			System.out.println(">>> Found RequestMappings");
			for (RequestMapping m : mappings) {
				System.out.println(m.getPath());
				assertNotNull(m.getPath());
			}
			System.out.println("<<< Found RequestMappings");

			RequestMapping rm;
			//Case 2 examples (path extracted from 'pseudo' json in the key)
			rm = assertRequestMappingWithPath(mappings, "/hello"); //We defined it so should be there
			assertEquals("com.example.demo.HelloController", rm.getFullyQualifiedClassName());
			assertEquals("hello", rm.getMethodName());
			assertEquals("com.example.demo.HelloController", rm.getType().getFullyQualifiedName());

			IMethod method = rm.getMethod();
			assertEquals(rm.getType(), method.getDeclaringType());
			assertEquals("hello", method.getElementName());

			assertTrue(rm.isUserDefined());

			rm = assertRequestMappingWithPath(mappings, "/error"); //Even empty apps should have a 'error' mapping
			assertFalse(rm.isUserDefined());

			rm = assertRequestMappingWithPath(mappings, "/actuator/health");
//			assertNotNull(rm.getMethod());
//			assertNotNull(rm.getType());
			assertFalse(rm.isUserDefined());

			rm = assertRequestMappingWithPath(mappings, "/actuator/info");
//			assertNotNull(rm.getMethod());
//			assertNotNull(rm.getType());
			assertFalse(rm.isUserDefined());

			//Case 1 example (path represented directly in the json key).
			rm = assertRequestMappingWithPath(mappings, "/actuator/health");
			assertFalse(rm.isUserDefined());

		} finally {
			element.stop();
			waitForState(element, RunState.INACTIVE);
		}
	}

	@Test public void testRequestMappingsOnlyJmxEnabled() throws Exception {
		do_testRequestMappingsOnlyJmxEnabled(RunState.RUNNING);
	}

	@Test public void testRequestMappingsOnlyJmxEnabled_DEBUG() throws Exception {
		do_testRequestMappingsOnlyJmxEnabled(RunState.DEBUGGING);
	}

	private void do_testRequestMappingsOnlyJmxEnabled(RunState runMode) throws Exception, CoreException {
		String projectName = "actuated-project";
		IProject project = createBootProject(projectName,
				bootVersionAtLeast("1.3.0"), //required for us to be able to determine the actuator port
				withStarters("web", "actuator"), //required to actually *have* an actuator
				setPackage("com.example.demo")
		);
		createFile(project, "src/main/java/com/example/demo/HelloController.java",
				"package com.example.demo;\n" +
				"\n" +
				"import org.springframework.web.bind.annotation.RequestMapping;\n" +
				"import org.springframework.web.bind.annotation.RestController;\n" +
				"\n" +
				"@RestController\n" +
				"public class HelloController {\n" +
				"\n" +
				"	@RequestMapping(\"/hello\")\n" +
				"	public String hello() {\n" +
				"		return \"Hello, World!\";\n" +
				"	}\n" +
				"\n" +
				"}\n"
		);
		StsTestUtil.assertNoErrors(project);
		final BootProjectDashElement element = getElement(projectName);
		ILaunchConfigurationWorkingCopy wc = element.createLaunchConfigForEditing().getWorkingCopy();
		BootLaunchConfigurationDelegate.setEnableLifeCycle(wc, false);
		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc,false);
		wc.doSave();
		try {
			waitForState(element, RunState.INACTIVE);
			assertNull(element.getLiveRequestMappings().orElse(null)); // unknown since can only be determined when app is running

			element.restart(runMode, ui());
			waitForState(element, runMode);
			ACondition.waitFor("Wait for request mappings", MODEL_UPDATE_TIMEOUT, () -> {
				List<RequestMapping> mappings = element.getLiveRequestMappings().orElse(null);
				assertNotNull(mappings);
				assertTrue(!mappings.isEmpty()); //Even though this is an 'empty' app should have some mappings,
				                                 // for example an 'error' page.
			});

			List<RequestMapping> mappings = element.getLiveRequestMappings().orElse(null);
			System.out.println(">>> Found RequestMappings");
			for (RequestMapping m : mappings) {
				System.out.println(m.getPath());
				assertNotNull(m.getPath());
			}
			System.out.println("<<< Found RequestMappings");

			RequestMapping rm = assertRequestMappingWithPath(mappings, "/hello"); //We defined it so should be there
			assertEquals("com.example.demo.HelloController", rm.getFullyQualifiedClassName());
			assertEquals("hello", rm.getMethodName());
			assertEquals("com.example.demo.HelloController", rm.getType().getFullyQualifiedName());

			IMethod method = rm.getMethod();
			assertEquals(rm.getType(), method.getDeclaringType());
			assertEquals("hello", method.getElementName());

			assertTrue(rm.isUserDefined());

			rm = assertRequestMappingWithPath(mappings, "/error"); //Even empty apps should have a 'error' mapping
			assertFalse(rm.isUserDefined());

		} finally {
			element.stop();
			waitForState(element, RunState.INACTIVE);
		}
	}


	@Test public void testDefaultRequestMapping() throws Exception {
		String projectName = "sdfsd-project";
		createBootProject(projectName);
		BootDashElement element = getElement(projectName);

		assertNull(element.getDefaultRequestMappingPath());
		element.setDefaultRequestMappingPath("something");
		assertProjectProperty(element.getProject(), "default.request-mapping.path", "something");

		assertEquals("something", element.getDefaultRequestMappingPath());
	}

	@Test public void testDevtoolsTextDecorationOnLocalElements() throws Exception {
		final String projectName = "project-hahaha";
		IProject project = createBootProject(projectName, withStarters("web", "actuator", "devtools"));
		final BootDashElement element = getElement(projectName);
		assertTrue(element.projectHasDevtoolsDependency());
		harness.assertLabelContains("[devtools]", element);

		//Also check that we do not add 'devtools' label to launch configs.
		ILaunchConfiguration conf = BootLaunchConfigurationDelegate.createConf(project);
		String confName = conf.getName();

		assertEquals(confName, harness.getLabel(harness.getElementFor(conf)));

		//Try and see that if we remove the devtools dependency from the project then the label updates.
		StsTestUtil.setAutoBuilding(true); // so that autobuild causes classpath update as would
											// happen in a 'real' workspace when pom is changed.
		IMavenCoordinates devtools = removeDevtools(project);
		new ACondition("Wait for devtools to disapear", MAVEN_BUILD_TIMEOUT) {
			public boolean test() throws Exception {
				assertFalse(element.projectHasDevtoolsDependency());
				assertEquals(projectName, harness.getLabel(element));
				return true;
			}
		};

		springBootCore.project(project).addMavenDependency(devtools, true);
		new ACondition("Wait for devtools to re-apear", MAVEN_BUILD_TIMEOUT) {
			public boolean test() throws Exception {
				assertFalse(element.projectHasDevtoolsDependency());
				assertEquals(projectName, harness.getLabel(element));
				return true;
			}
		};

	}

	/**************************************************************************************
	 * TAGS Tests START
	 *************************************************************************************/

	private void testSettingTags(String[] tagsToSet, String[] expectedTags) throws Exception {
		String projectName = "alex-project";
		createBootProject(projectName);
		BootDashElement element = getElement(projectName);
		IProject project = element.getProject();

		if (tagsToSet == null || tagsToSet.length == 0) {
			element.setTags(new LinkedHashSet<>(Arrays.asList("foo", "bar")));
			assertFalse(element.getTags().isEmpty());
		} else {
			assertArrayEquals(new String[]{}, element.getTags().toArray(new String[0]));
		}

		element.setTags(linkedHashSet(tagsToSet));
		waitForJobsToComplete();
		assertArrayEquals(expectedTags, element.getTags().toArray(new String[0]));

		// Reopen the project to load tags from the resource
		project.close(null);
		project.open(null);
		element = getElement(projectName);
		assertArrayEquals(expectedTags, element.getTags().toArray(new String[0]));
	}

	private LinkedHashSet<String> linkedHashSet(String[] tagsToSet) {
		if (tagsToSet != null) {
			return new LinkedHashSet<>(Arrays.asList(tagsToSet));
		}
		return null;
	}

	@Test
	public void setUniqueTagsForProject() throws Exception {
		testSettingTags(new String[] {"xd", "spring"}, new String[] {"xd", "spring"});
	}

	@Test
	public void setDuplicateTagsForProject() throws Exception {
		testSettingTags(new String[] {"xd", "spring", "xd", "spring", "spring"}, new String[] {"xd", "spring"});
	}

	@Test
	public void setTagsWithWhiteSpaceCharsForProject() throws Exception {
		testSettingTags(new String[] {"#xd", "\tspring", "xd ko ko", "spring!!-@", "@@@ - spring"}, new String[] {"#xd", "\tspring", "xd ko ko", "spring!!-@", "@@@ - spring"});
	}

	@Test
	public void setNoTags() throws Exception {
		testSettingTags(new String[0], new String[0]);
	}

	@Test
	public void setNullTags() throws Exception {
		testSettingTags(null, new String[0]);
	}

	private class BdeInfo {
		String name;
		String[] tags;
		String workingSet;

		BdeInfo(String name, String[] tags, String workingSet) {
			this.name = name;
			this.tags = tags;
			this.workingSet = workingSet;
		}
	}

	/**************************************************************************************
	 * TAGS Tests END
	 *************************************************************************************/

	/**************************************************************************************
	 * BDEs Filtering Tests START
	 *************************************************************************************/

	private void testBdeFiltering(BdeInfo[] bdeInfo, String filterText, String[] expectedBdes) throws Exception {
		Map<String, List<IProject>> wsMap = new HashMap<>();
		List<BootDashElement> bdes = new ArrayList<>(bdeInfo.length);
		for (BdeInfo info : bdeInfo) {
			createBootProject(info.name);
			BootDashElement element = getElement(info.name);
			IProject project = element.getProject();
			if (info.tags != null && info.tags.length > 0) {
				element.setTags(new LinkedHashSet<>(Arrays.asList(info.tags)));
			}
			if (info.workingSet != null && !info.workingSet.isEmpty() && project != null) {
				List<IProject> projects = wsMap.get(info.workingSet);
				if (projects == null) {
					projects = new ArrayList<>();
					wsMap.put(info.workingSet, projects);
				}
				projects.add(project);
			}
			bdes.add(element);
		}
		IWorkingSetManager wsManager = PlatformUI.getWorkbench().getWorkingSetManager();
		for (Map.Entry<String, List<IProject>> entry : wsMap.entrySet()) {
			IWorkingSet ws = wsManager.getWorkingSet(entry.getKey());
			if (ws == null) {
				ws = wsManager.createWorkingSet(entry.getKey(), entry.getValue().toArray(new IProject[entry.getValue().size()]));
				wsManager.addWorkingSet(ws);
			} else {
				ws.setElements(entry.getValue().toArray(new IProject[entry.getValue().size()]));
			}
		}
		waitForJobsToComplete();

		BootDashElementsFilterBoxModel filterModel = new BootDashElementsFilterBoxModel();
		filterModel.getText().setValue(filterText);
		Filter<BootDashElement> filter = filterModel.getFilter().getValue();

		List<String> result = new ArrayList<>();
		for (BootDashElement bde : bdes) {
			if (filter.accept(bde) && bde.getProject() != null) {
				result.add(bde.getProject().getName());
			}
		}
		String[] actualBdes = result.toArray(new String[result.size()]);
		assertArrayEquals(expectedBdes, actualBdes);
	}

	@Test
	public void testNoWorkingSetMatch_1() throws Exception {
		testBdeFiltering(new BdeInfo[]{new BdeInfo("a", null, null), new BdeInfo("b", null, null)}, "x", new String[0]);
	}

	@Test
	public void testNoWorkingSetMatch_2() throws Exception {
		testBdeFiltering(new BdeInfo[]{new BdeInfo("a", null, "xxx"), new BdeInfo("b", null, "xxx")}, "x,", new String[0]);
	}

	@Test
	public void testNoWorkingSetMatch_3() throws Exception {
		testBdeFiltering(
				new BdeInfo[]{
						new BdeInfo("b", null, "xxx"),
						new BdeInfo("c", null, "xxx")
				},
				"xxx, a",
				new String[0]
		);
	}

	@Test
	public void testNoWorkingSetMatch_4() throws Exception {
		testBdeFiltering(
			new BdeInfo[]{
				new BdeInfo("a", null, "xxx"),
				new BdeInfo("b", null, "xxx")
			},
			"c, x",
			new String[0]
		);
	}

	@Test
	public void testWorkingSetMatch_1() throws Exception {
		testBdeFiltering(new BdeInfo[]{new BdeInfo("a", null, "x"), new BdeInfo("b", null, null), new BdeInfo("c", null, "x")}, "x", new String[]{"a", "c"});
	}

	@Test
	public void testWorkingSetMatch_2() throws Exception {
		testBdeFiltering(new BdeInfo[]{new BdeInfo("a", null, "xxx"), new BdeInfo("b", null, null), new BdeInfo("c", null, "xxxx")}, "xxx,", new String[]{"a"});
	}

	@Test
	public void testWorkingSetMatch_3() throws Exception {
		testBdeFiltering(
				new BdeInfo[]{
						new BdeInfo("a", new String[]{"aaa", "bbb"}, "xxx"),
						new BdeInfo("b", new String[]{"a", "c"}, "xxx")
				},
				"xxx, a",
				new String[]{"a", "b"});
	}

	@Test
	public void testWorkingSetMatch_4() throws Exception {
		testBdeFiltering(
				new BdeInfo[]{
					new BdeInfo("z",
							new String[]{"aaa", "bbb"},
							"xxx"
					),
					new BdeInfo("b",
							new String[]{"a", "c"},
							"xxx")
				},
				"a, x",
				new String[]{"b"}
		);
	}

	/**************************************************************************************
	 * BDEs Filtering Tests END
	 *************************************************************************************/

	///////////////// harness code ////////////////////////

	private void assertProjectProperty(IProject project, String prop, String value) {
		assertEquals(value, context.getProjectProperties().get(project, prop));
	}

	@Rule
	public TestRule listenerLeakDetector = new ListenerLeakDetector();

	@Rule
	public LaunchCleanups launchCleanups = new LaunchCleanups();

	private BootDashViewModelHarness harness;

	@Before
	public void setup() throws Exception {
		//As part of its normal operation, devtools will throw some uncaucht exceptions.
		// We don't want our tests to be disrupted when running the process in debug mode... so disable
		// suspending on such exceptions:
		suspendOnUncaughtException(false);

		StsTestUtil.deleteAllProjects();
		this.context = new TestBootDashModelContext(
				ResourcesPlugin.getWorkspace(),
				DebugPlugin.getDefault().getLaunchManager()
		);
		this.harness = new BootDashViewModelHarness(context.withTargetTypes(RunTargetTypes.LOCAL));
		this.model = harness.getRunTargetModel(RunTargetTypes.LOCAL);
		this.projects = new BootProjectTestHarness(context.getWorkspace());
		StsTestUtil.setAutoBuilding(false);

	}

	public static void suspendOnUncaughtException(boolean enable) {
		String suspendOption = "org.eclipse.jdt.debug.ui.javaDebug.SuspendOnUncaughtExceptions";
		IEclipsePreferences debugPrefs = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.debug.ui");
		debugPrefs.putBoolean(suspendOption, enable);
	}

	@After
	public void tearDown() throws Exception {
		/*
		 * Remove any working sets created by the tests (BDEs filtering tests create working sets)
		 */
		IWorkingSetManager wsManager = PlatformUI.getWorkbench().getWorkingSetManager();
		for (IWorkingSet ws : wsManager.getAllWorkingSets()) {
			if (!ws.isAggregateWorkingSet()) {
				wsManager.removeWorkingSet(ws);
			}
		}

		this.harness.dispose();
	}

	/**
	 * Returns the only active (i.e. not terminated launch for a project). If there is more
	 * than one active launch, or no active launch this returns null.
	 */
	public ILaunch getActiveLaunch(BootDashElement element) {
		ImmutableSet<ILaunch> ls = getBootLaunches(element);
		ILaunch activeLaunch = null;
		for (ILaunch l : ls) {
			if (!l.isTerminated()) {
				if (activeLaunch == null) {
					activeLaunch = l;
				} else {
					//More than one active launch
					return null;
				}
			}
		}
		return activeLaunch;
	}

	private ImmutableSet<ILaunch> getBootLaunches(BootDashElement element) {
		if (element instanceof BootProjectDashElement) {
			BootProjectDashElement project = (BootProjectDashElement) element;
			return project.getLaunches();
		}
		return ImmutableSet.of();
	}

	private static void waitForState(final BootDashElement element, final RunState state) throws Exception {
		new ACondition("Wait for state "+state, RUN_STATE_CHANGE_TIMEOUT) {
			@Override
			public boolean test() throws Exception {
				assertEquals(state, element.getRunState());
				return true;
			}
		};
	}

	private BootProjectDashElement getElement(String name) {
		for (BootDashElement el : model.getElements().getValues()) {
			if (name.equals(el.getName())) {
				return (BootProjectDashElement) el;
			}
		}
		return null;
	}

	private void assertServiceElements(String... expectedElementNames) {
		Set<BootDashElement> elements = model.getElements().getValue();
		Set<String> names = new HashSet<>();
		for (BootDashElement e : elements) {
			if (isService(e)) {
				names.add(e.getName());
			}
		}
		assertElements(names, expectedElementNames);
	}

	private void assertNonServiceElements(String... expectedElementNames) {
		Set<BootDashElement> elements = model.getElements().getValue();
		Set<String> names = new HashSet<>();
		for (BootDashElement e : elements) {
			if (!isService(e)) {
				names.add(e.getName());
			}
		}
		assertElements(names, expectedElementNames);
	}

	private boolean isService(BootDashElement e) {
		return e instanceof LocalCloudServiceDashElement;
	}

	public void waitNonServiceElements(final String... expectedElementNames) throws Exception {
		new ACondition("Model update", MODEL_UPDATE_TIMEOUT) {
			public boolean test() throws Exception {
				assertNonServiceElements(expectedElementNames);
				return true;
			}
		};
	}

	public static void waitForJobsToComplete() throws Exception {
		new ACondition("Wait for Jobs", 3 * 60 * 1000) {
			@Override
			public boolean test() throws Exception {
				assertJobManagerIdle();
				return true;
			}
		};
	}

	private void setPort(ILaunchConfiguration conf, int port) throws Exception {
		ILaunchConfigurationWorkingCopy wc = conf.getWorkingCopy();
		assertTrue("Only supported on 'empty' configs", BootLaunchConfigurationDelegate.getApplicationProperties(wc).isEmpty());
		BootLaunchConfigurationDelegate.setRawApplicationProperties(wc, "server.port=" + port);
		wc.doSave();
	}

	private IMavenCoordinates removeDevtools(IProject project) throws Exception {
		ISpringBootProject bootProject = springBootCore.project(project);
		MavenId devtoolsId = new MavenId(
				BootPropertyTester.SPRING_BOOT_DEVTOOLS_GID,
				BootPropertyTester.SPRING_BOOT_DEVTOOLS_AID
		);

		IMavenCoordinates devtools = null;
		for (IMavenCoordinates dep : bootProject.getDependencies()) {
			if (new MavenId(dep.getGroupId(), dep.getArtifactId()).equals(devtoolsId)) {
				devtools = dep;
			}
		}
		assertNotNull("Devtools dependency not found, so can't remove it", devtools);
		bootProject.removeMavenDependency(devtoolsId);
		return devtools;
	}


}
