/*******************************************************************************
 * Copyright (c) 2015-2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveDataConnectionManagementActions;
import org.springframework.ide.eclipse.boot.dash.model.AbstractLaunchConfigurationsDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElement;
import org.springframework.ide.eclipse.boot.dash.model.LocalBootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.LocalRunTarget;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.RunTargets;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetTypes;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockLiveProcesses;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockLiveProcesses.MockProcess;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockMultiSelection;
import org.springframework.ide.eclipse.boot.dash.views.BootDashActions;
import org.springframework.ide.eclipse.boot.dash.views.BootDashActions.RunOrDebugStateAction;
import org.springframework.ide.eclipse.boot.dash.views.DuplicateConfigAction;
import org.springframework.ide.eclipse.boot.dash.views.OpenLaunchConfigAction;
import org.springframework.ide.eclipse.boot.dash.views.RunStateAction;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.livebean.JmxBeanSupport;
import org.springframework.ide.eclipse.boot.test.AutobuildingEnablement;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.WizardConfigurer;
import org.springframework.ide.eclipse.boot.test.util.TestBracketter;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class BootDashActionTests {

	@Test
	public void liveProcessManagementVisibilityForLocalElements() throws Exception {
		String projectName = "santa-baby";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		AbstractLaunchConfigurationsDashElement<?> projectElement = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);
		BootDashElement launchConfElement = harness.getElementFor(BootLaunchConfigurationDelegate.createConf(javaProject));
		LiveDataConnectionManagementActions liveActionsMenu = actions.getLiveDataConnectionManagement();

		MockMultiSelection<BootDashElement> selection = harness.selection;

		assertTrue(selection.getElements().isEmpty());
		assertTrue(liveActionsMenu.isVisible());

		selection.setElements(projectElement);
		assertTrue(liveActionsMenu.isVisible());

		selection.setElements(launchConfElement);
		assertFalse(liveActionsMenu.isVisible());
	}

	@Test
	public void liveProcessManagementEnablementForSingleLocalElement() throws Exception {
		LiveVariable<RunState> runState = new LiveVariable<>(RunState.INACTIVE);
//		runState.onChange((e, s) -> {
//			harness.model.notify();
//		});
		LiveDataConnectionManagementActions liveActionsMenu = actions.getLiveDataConnectionManagement();

		String projectName = "santa-baby";
		IProject project = mock(IProject.class);
		when(project.getName()).thenReturn(projectName);

		RunTarget runTarget = LocalRunTarget.INSTANCE;

		LocalBootDashModel bdm = mock(LocalBootDashModel.class);
		when(bdm.getRunTarget()).thenReturn(runTarget);

		//mocked BootProjectDashElement where we can easily control the runstate from the test case:
		BootProjectDashElement element = mockRunnableProjectElement(project, runState);

		harness.selection.setElements(element);


		for (RunState state : RunState.values()) {
			System.out.println("RunState = "+state);
			runState.setValue(state);
			Boolean enabled = liveActionsMenu.isEnabled().getValue();
			System.out.println("Enabled = "+enabled);
			assertEquals(state, element.getRunState());
			assertEquals(state == RunState.RUNNING || state == RunState.DEBUGGING, enabled);
		}
	}

	private BootProjectDashElement mockRunnableProjectElement(IProject project, LiveVariable<RunState> runState) {
		return new BootProjectDashElement(project, harness.getLocalTargetModel(), context.getProjectProperties(), null, null) {
			//This is what we care about really:
			protected LiveExpression<RunState> createRunStateExp() {
				return runState;
			}
		};
	}

	@Test
	public void liveProcessManagementEnablementForMultipleLocalElement() throws Exception {
		//setup:
		String projectName = "santa";
		IProject project = mock(IProject.class);
		when(project.getName()).thenReturn(projectName);

		String otherProjectName = "baby";
		IProject otherProject = mock(IProject.class);
		when(otherProject.getName()).thenReturn(otherProjectName);

		LiveVariable<RunState> elementState = new LiveVariable<>(RunState.INACTIVE);
		BootProjectDashElement element = mockRunnableProjectElement(project, elementState);

		LiveVariable<RunState> otherElementState = new LiveVariable<>(RunState.INACTIVE);
		BootProjectDashElement otherElement = mockRunnableProjectElement(otherProject, otherElementState);

		LiveDataConnectionManagementActions liveActionsMenu = actions.getLiveDataConnectionManagement();

		//test: two elements:
		harness.selection.setElements(element, otherElement);
		assertTrue(liveActionsMenu.isVisible());
		for (RunState s1 : RunState.values()) {
			for (RunState s2 : RunState.values()) {
				boolean expectEnabled = s1 == RunState.RUNNING || s2 == RunState.RUNNING || s1 == RunState.DEBUGGING || s2 == RunState.DEBUGGING;
				elementState.setValue(s1);
				otherElementState.setValue(s2);
				assertEquals(expectEnabled, liveActionsMenu.isEnabled().getValue());
			}
		}

		//test: no elements
		elementState.setValue(RunState.RUNNING);
		otherElementState.setValue(RunState.DEBUGGING);
		harness.selection.setElements(/*none*/);
		assertTrue(liveActionsMenu.isVisible());
		assertEquals(true, liveActionsMenu.isEnabled().getValue());
	}

	@Test
	public void liveProcessManagementSingleProjectSelection() throws Exception {
		//setup:
		String[] projectNames = { "a", "b", "c" };
		int[] processCounts = {1, 2, 0};
		List<List<MockProcess>> mockProcesses = new ArrayList<>();

		for (int i = 0; i < projectNames.length; i++) {
			String projectName = projectNames[i];
			IProject project = createBootProject(projectName);
			ArrayList<MockProcess> list = new ArrayList<>();
			mockProcesses.add(list);
			for (int p = 0; p < processCounts[i]; p++) {
				list.add(processes.newLocalProcess(projectName+":"+p, project, p));
			}
		}

		LiveDataConnectionManagementActions liveActionsMenu = actions.getLiveDataConnectionManagement();
		MockMultiSelection<BootDashElement> selection = harness.selection;

		//test:
		for (int i = 0; i < projectNames.length; i++) {
			BootDashElement projectElement = harness.getElementWithName(projectNames[i]);
			selection.setElements(projectElement);
			int numprocs = processCounts[i];
			if (numprocs == 0) {
				List<IAction> actions = liveActionsMenu.getActions();
				assertEquals(1, actions.size());
				IAction action = actions.get(0);
				assertEquals("No matching processes", action.getText());
				assertFalse(action.isEnabled());
			} else {
				Stream<String> processNames = mockProcesses.get(i).stream().map(MockProcess::getProcessKey);
				List<IAction> actions = liveActionsMenu.getActions();
				for (IAction a : actions) {
					assertTrue(a.isEnabled());
				}
				assertEquals(processCounts[i], actions.size());

				Set<String> expectedLabels = processNames.map(name -> "Connect "+name+" lbl").collect(Collectors.toSet());
				Set<String> actualLabels = actions.stream().map(a -> a.getText()).collect(Collectors.toSet());
				assertEquals(expectedLabels, actualLabels);
			}
		}
	}

	@Test
	public void liveProcessManagementMultipleOrEmptyProjectSelection() throws Exception {
		//setup:
		String[] projectNames = { "a", "b", "c" };
		int[] processCounts = {1, 2, 1};
		List<List<MockProcess>> mockProcesses = new ArrayList<>();

		for (int i = 0; i < projectNames.length; i++) {
			String projectName = projectNames[i];
			IProject project = createBootProject(projectName);
			ArrayList<MockProcess> list = new ArrayList<>();
			mockProcesses.add(list);
			for (int p = 0; p < processCounts[i]; p++) {
				list.add(processes.newLocalProcess(projectName+":"+p, project, p));
			}
		}

		LiveDataConnectionManagementActions liveActionsMenu = actions.getLiveDataConnectionManagement();
		MockMultiSelection<BootDashElement> selection = harness.selection;

		BootDashElement a = harness.getElementWithName("a");
		BootDashElement b = harness.getElementWithName("b");
		BootDashElement c = harness.getElementWithName("c");

		//test a and b selected
		{
			selection.setElements(a, b);
			List<IAction> actions = liveActionsMenu.getActions();
			Set<String> actualLabels = actions.stream().map(IAction::getText).collect(Collectors.toSet());
			assertEquals(ImmutableSet.of("Connect a:0 lbl", "Connect b:0 lbl", "Connect b:1 lbl"), actualLabels);
			for (IAction ac : actions) {
				assertTrue(ac.isEnabled());
			}
		}

		//test b and c selected
		{
			selection.setElements(b, c);
			List<IAction> actions = liveActionsMenu.getActions();
			Set<String> actualLabels = actions.stream().map(IAction::getText).collect(Collectors.toSet());
			assertEquals(ImmutableSet.of("Connect c:0 lbl", "Connect b:0 lbl", "Connect b:1 lbl"), actualLabels);
			for (IAction ac : actions) {
				assertTrue(ac.isEnabled());
			}
		}

		//no elements selected
		{
			selection.setElements(/*none*/);
			List<IAction> actions = liveActionsMenu.getActions();
			Set<String> actualLabels = actions.stream().map(IAction::getText).collect(Collectors.toSet());
			assertEquals(ImmutableSet.of("Connect a:0 lbl", "Connect c:0 lbl", "Connect b:0 lbl", "Connect b:1 lbl"), actualLabels);
			for (IAction ac : actions) {
				assertTrue(ac.isEnabled());
			}
		}
	}

	@Test
	public void liveProcessManagementProcessWithoutProjectName() throws Exception {
		IProject project = createBootProject("a");
		processes.newLocalProcess("whatever", null, 1);
		processes.newLocalProcess("a-process", project, 2);


		harness.selection.setElements(harness.getElementFor(project));
		Set<String> labels = actions.getLiveDataConnectionManagement().getActions().stream().map(a -> a.getText()).collect(Collectors.toSet());
		assertEquals(ImmutableSet.of("Connect a-process lbl"), labels);
	}

	@Test
	public void liveProcessManagementScenario() throws Exception {
		IProject project = createBootProject("a");
		MockProcess process = processes.newLocalProcess("a-process", project, 1);

		LiveDataConnectionManagementActions actionProvider = actions.getLiveDataConnectionManagement();
		harness.selection.setElements(harness.getElementFor(project));

		assertFalse(process.isConnected());
		process.assertRefreshed(0);

		IAction action = assertSingleEnabledActionWithLabel(actionProvider.getActions(), "Connect a-process lbl");
		action.run();
		assertTrue(process.isConnected());
		process.assertRefreshed(0);

		{
			List<IAction> actions = actionProvider.getActions();
			assertEquals(2, actions.size());
			IAction refreshAction = assertActionWithLabel(actions, "Refresh a-process lbl");
			IAction disconnectAction = assertActionWithLabel(actions, "Disconnect a-process lbl");
			refreshAction.run();

			assertTrue(process.isConnected());
			process.assertRefreshed(1);
		}

		{
			List<IAction> actions = actionProvider.getActions();
			assertEquals(2, actions.size());
			IAction refreshAction = assertActionWithLabel(actions, "Refresh a-process lbl");
			IAction disconnectAction = assertActionWithLabel(actions, "Disconnect a-process lbl");
			disconnectAction.run();

			assertFalse(process.isConnected());
		}

		assertSingleEnabledActionWithLabel(actionProvider.getActions(), "Connect a-process lbl");
	}

	@Test
	public void liveProcessManagementMixedLocalAndRemoteProcesses() throws Exception {
		BootProjectDashElement localEl = harness.getElementFor(projects.createBootProject("webaaa"));

		String appGuid = "7957d372-d68b-4233-aebc-41f94cdab318";
		MockProcess remoteApp = processes.newProcess(ImmutableMap.of(
				"processKey", "remote process 1",
				"label", "remote process 1 (lbl)",
				"processId", appGuid
		));

		MockProcess localApp = processes.newProcess(ImmutableMap.of(
			"processKey", "4433 - com.example.demo.WebaaaApplication",
			"label", "4433 (com.example.demo.WebaaaApplication)",
			"projectName", "webaaa",
			"processId", "4433"
		));

		LiveDataConnectionManagementActions liveActionsMenu = actions.getLiveDataConnectionManagement();

		MockMultiSelection<BootDashElement> selection = harness.selection;

		//no elements selected
		{
			selection.setElements(/*none*/);
			List<IAction> actions = liveActionsMenu.getActions();
			Set<String> actualLabels = actions.stream().map(IAction::getText).collect(Collectors.toSet());
			assertEquals(ImmutableSet.of("Connect remote process 1 (lbl)", "Connect 4433 (com.example.demo.WebaaaApplication)"), actualLabels);
			for (IAction ac : actions) {
				assertTrue(ac.isEnabled());
			}
		}

		//local element selected
		{
			selection.setElements(localEl);
			List<IAction> actions = liveActionsMenu.getActions();
			Set<String> actualLabels = actions.stream().map(IAction::getText).collect(Collectors.toSet());
			assertEquals(ImmutableSet.of("Connect 4433 (com.example.demo.WebaaaApplication)"), actualLabels);
			for (IAction ac : actions) {
				assertTrue(ac.isEnabled());
			}
		}

	}

	private IAction assertActionWithLabel(List<IAction> actions, String expectedLabel) {
		Optional<IAction> found = actions.stream().filter(a -> expectedLabel.equals(a.getText())).findFirst();
		assertTrue(found.isPresent());
		return found.get();
	}

	private IAction assertSingleEnabledActionWithLabel(List<IAction> list, String expectedLabel) {
		assertEquals(1, list.size());
		IAction action = list.get(0);
		assertEquals(expectedLabel, action.getText());
		return action;
	}

	@Test
	public void deleteConfigActionEnablementForProject() throws Exception {
		//At the moment, this action does not enable for projects at all
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final AbstractLaunchConfigurationsDashElement<?> element = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		BootLaunchConfigurationDelegate.createConf(javaProject);

		final IAction action = actions.getDeleteConfigsAction();
		action.setEnabled(true); //force it to true so we can tell that it actually changes.
 		selection.setElements(element);
 		new ACondition("Wait for disablement", 3000) {
			public boolean test() throws Exception {
				assertFalse(action.isEnabled());
				return true;
			}
		};
	}

	@Test
	public void deleteConfigActionEnablementForConf() throws Exception {
		//At the moment, this action always enables for one or more launch confs
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final AbstractLaunchConfigurationsDashElement<?> element = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final ILaunchConfiguration conf1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		BootDashElement el1 = harness.getElementFor(conf1);
		final ILaunchConfiguration conf2 = BootLaunchConfigurationDelegate.createConf(javaProject);
		BootDashElement el2 = harness.getElementFor(conf2);

		new ACondition("Wait for children", 3000) {
			public boolean test() throws Exception {
				assertEquals(2, element.getCurrentChildren().size());
				return true;
			}
		};

		final IAction action = actions.getDeleteConfigsAction();

		assertFalse(action.isEnabled());
		selection.setElements(el1);
		new ACondition("Wait for enablement", 3000) {
			public boolean test() throws Exception {
				assertTrue(action.isEnabled());
				return true;
			}
		};

		action.setEnabled(false);
		selection.setElements(el1, el2);
		new ACondition("Wait for enablement", 3000) {
			public boolean test() throws Exception {
				assertTrue(action.isEnabled());
				return true;
			}
		};

	}

	@Test
	public void deleteConfigAction() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final AbstractLaunchConfigurationsDashElement<?> element = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final ILaunchConfiguration conf1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		final BootDashElement el1 = harness.getElementFor(conf1);
		final ILaunchConfiguration conf2 = BootLaunchConfigurationDelegate.createConf(javaProject);
		final BootDashElement el2 = harness.getElementFor(conf2);

		new ACondition("Wait for children", 3000) {
			public boolean test() throws Exception {
				assertEquals(2, element.getCurrentChildren().size());
				assertEquals(ImmutableSet.of(el1, el2), element.getCurrentChildren());
				return true;
			}
		};

		final IAction action = actions.getDeleteConfigsAction();

		assertFalse(action.isEnabled());
		selection.setElements(el1);
		new ACondition("Wait for enablement", 3000) {
			public boolean test() throws Exception {
				assertTrue(action.isEnabled());
				return true;
			}
		};
		when(ui().confirmWithToggle(anyString(), eq("Deleting Elements"), anyString(), anyString())).thenReturn(true);
		action.run();

		ACondition.waitFor("Wait for config deletion", 3000, () -> {
			assertEquals(ImmutableSet.of(conf2), element.getLaunchConfigs());
			assertFalse(conf1.exists());
			assertTrue(element.getCurrentChildren().size() == 1);
		});
	}


	private UserInteractions ui() {
		return context.injections.getBean(AllUserInteractions.class);
	}

	@Test
	public void duplicateConfigAction() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final AbstractLaunchConfigurationsDashElement<?> element = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final DuplicateConfigAction action = actions.getDuplicateConfigAction();
		final ILaunchConfiguration conf1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		assertEquals(0, getJMXPortAsInt(conf1));
		selection.setElements(element);
		new ACondition("Wait for enablement", 3000) {
			public boolean test() throws Exception {
				assertTrue(action.isEnabled());
				return true;
			}
		};

		action.run();

		new ACondition("Wait for action post conditions", 3000) {
			public boolean test() throws Exception {
				ImmutableSet<ILaunchConfiguration> confs = element.getLaunchConfigs();
				assertEquals(2, confs.size());
				assertEquals(2, element.getCurrentChildren().size());
				assertTrue(confs.contains(conf1));
				for (ILaunchConfiguration other : confs) {
					assertEquals(0, getJMXPortAsInt(other));
				}
				return true;
			}

		};
	}

	@Test
	public void duplicateConfigActionWithJmxPortSet() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final AbstractLaunchConfigurationsDashElement<?> element = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final DuplicateConfigAction action = actions.getDuplicateConfigAction();
		final ILaunchConfiguration conf1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		assertEquals(0, getJMXPortAsInt(conf1));
		String randomPort = ""+JmxBeanSupport.randomPort();
		setJMXPort(conf1, randomPort);
		assertEquals(randomPort, BootLaunchConfigurationDelegate.getJMXPort(conf1));
		selection.setElements(element);
		new ACondition("Wait for enablement", 3000) {
			public boolean test() throws Exception {
				assertTrue(action.isEnabled());
				return true;
			}
		};

		action.run();

		new ACondition("Wait for action post conditions", 3000) {
			public boolean test() throws Exception {
				ImmutableSet<ILaunchConfiguration> confs = element.getLaunchConfigs();
				assertEquals(2, confs.size());
				assertEquals(2, element.getCurrentChildren().size());
				assertTrue(confs.contains(conf1));
				for (ILaunchConfiguration other : confs) {
					if (!other.equals(conf1)) {
						assertFalse(getJMXPortAsInt(conf1) == getJMXPortAsInt(other));
					}
				}
				return true;
			}

		};
	}

	private static void setJMXPort(ILaunchConfiguration conf, String port) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = conf.getWorkingCopy();
		BootLaunchConfigurationDelegate.setJMXPort(wc, port);
		wc.doSave();
	}

	private static int getJMXPortAsInt(ILaunchConfiguration conf) {
		try {
			String str = BootLaunchConfigurationDelegate.getJMXPort(conf);
			if (str != null) {
				return Integer.parseInt(str);
			}
		} catch (NumberFormatException e) {
			//couldn't parse
		}
		return -1;
	}

	@Test
	public void duplicateConfigActionEnablementForLaunchConf() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final AbstractLaunchConfigurationsDashElement<?> element = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final DuplicateConfigAction action = actions.getDuplicateConfigAction();

		ILaunchConfiguration conf1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		ILaunchConfiguration conf2 = BootLaunchConfigurationDelegate.createConf(javaProject);
		new ACondition("Wait for elements", 3000) {
			public boolean test() throws Exception {
				assertEquals(2, element.getLaunchConfigs().size());
				assertEquals(2, element.getCurrentChildren().size());
				return true;
			}
		};

		BootDashElement el1 = harness.getElementFor(conf1);
		BootDashElement el2 = harness.getElementFor(conf2);

		assertFalse(action.isEnabled()); // or test may pass vacuously without an actual update
		selection.setElements(el1);
		new ACondition("Wait for enablement", 3000) {
			public boolean test() throws Exception {
				assertTrue(action.isEnabled());
				return true;
			}
		};

		selection.setElements(el1, el2);
		new ACondition("Wait for disablement", 3000) {
			public boolean test() throws Exception {
				assertFalse(action.isEnabled());
				return true;
			}
		};
	}

	@Test
	public void duplicateConfigActionEnablementForProject() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final AbstractLaunchConfigurationsDashElement<?> element = (AbstractLaunchConfigurationsDashElement<?>) harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final DuplicateConfigAction action = actions.getDuplicateConfigAction();

		//If selection is empty the action must not be enabled
		assertTrue(selection.isEmpty());
		assertFalse(action.isEnabled());

		//If project is selected then...
		selection.setElements(element);
		// a) if project has no launch configs ...
		assertTrue(element.getLaunchConfigs().isEmpty());
		// then there's nothing to duplicate... so disabled
		assertFalse(action.isEnabled());

		// b) if project has exactly one launch config ...
		BootLaunchConfigurationDelegate.createConf(javaProject);
		// action enablement is updated as response to some asynchronous state changes
		// so may not happen immediately
		new ACondition("Wait for enablement", 3000) {
			public boolean test() throws Exception {
				assertEquals(1, element.getLaunchConfigs().size());
				assertTrue(action.isEnabled());
				return true;
			}
		};

		// c) if project has more than one launch config...
		BootLaunchConfigurationDelegate.createConf(javaProject);
		ACondition.waitFor("Launch conf elements", 3000, () -> {
			assertEquals(2, element.getLaunchConfigs().size());
		});
		// ... async update may not happen right away...
		new ACondition("Wait for disablement", 3000) {
			public boolean test() throws Exception {
				assertEquals(2, element.getCurrentChildren().size());
				assertFalse(action.isEnabled());
				return true;
			}
		};

	}

	@Test
	public void openConfigActionEnablementForProject() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final BootDashElement element = harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final OpenLaunchConfigAction action = actions.getOpenConfigAction();

		//If selection is empty the action must not be enabled
		assertTrue(selection.isEmpty());
		assertFalse(action.isEnabled());

		//If selection has more than one element... the action must not be enabled
		selection.setElements(element, mockLocalElement());
		assertFalse(action.isEnabled());

		//If selection has one element...
		selection.setElements(element);

		//a) and element has no launch configs...
		assertTrue(element.getLaunchConfigs().isEmpty());
		assertTrue(action.isEnabled());

		// Careful... when changing the launch configs of a element, the enablement state of
		// action should auto-refresh, but this happens asyncly so the tests sequences are put in such
		// a order that the enablement state changes on each (otherwise the ACondition may vacuously
		// pass immediately even if the enablement didn't get updated, as it was correct from
		// the start)

		//b) and element has multiple launch config
		assertTrue(action.isEnabled()); // make sure the test won't pass 'by accident'.
		final ILaunchConfiguration c1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		final ILaunchConfiguration c2 = BootLaunchConfigurationDelegate.createConf(javaProject);
		new ACondition(2000) {
			public boolean test() throws Exception {
				assertEquals(ImmutableSet.of(c1,c2), element.getLaunchConfigs());
				assertFalse(action.isEnabled());
				return true;
			}
		};

		//b) and element has a single launch config
		assertFalse(action.isEnabled()); // make sure the test won't pass 'by accident'.
		c2.delete();
		new ACondition(2000) {
			public boolean test() throws Exception {
				assertEquals(ImmutableSet.of(c1), element.getLaunchConfigs());
				assertTrue(action.isEnabled());
				return true;
			}
		};
	}

	@Test
	public void openConfigActionEnablementForLaunchConfig() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final BootDashElement element = harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final OpenLaunchConfigAction action = actions.getOpenConfigAction();

		BootLaunchConfigurationDelegate.createConf(javaProject);
		BootLaunchConfigurationDelegate.createConf(javaProject);

		//Check initial conditions are as expected:
		assertTrue(selection.isEmpty());
		assertFalse(action.isEnabled());
		ACondition.waitFor("children to appear", 3000, () -> {
			assertEquals(2, element.getChildren().getValues().size());
		});

		//Check action enablement for the children
		for (BootDashElement child : element.getChildren().getValues()) {
			selection.setElements(child);
			assertTrue(action.isEnabled());
		}
	}

	@Test
	public void openRedebugActionEnablementForLaunchConfig() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final BootDashElement element = harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final RunStateAction action = getRunStateAction(RunState.DEBUGGING);

		BootLaunchConfigurationDelegate.createConf(javaProject);
		BootLaunchConfigurationDelegate.createConf(javaProject);

		//Check initial conditions are as expected:
		assertTrue(selection.isEmpty());
		assertFalse(action.isEnabled());
		ACondition.waitFor("children", 3000, () -> {
			assertEquals(2, element.getChildren().getValues().size());
		});

		//Check action enablement for the children
		for (BootDashElement child : element.getChildren().getValues()) {
			selection.setElements(child);
			assertTrue(action.isEnabled());
		}
	}

	@Test
	public void redebugActionEnablementForMultipleProjects() throws Exception {
		IProject p1 = createBootProject("project1");
		IProject p2 = createBootProject("project2");

		BootDashElement e1 = harness.getElementWithName(p1.getName());
		BootDashElement e2 = harness.getElementWithName(p2.getName());

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final RunStateAction action = getRunStateAction(RunState.DEBUGGING);

		assertTrue(selection.isEmpty());
		assertFalse(action.isEnabled());

		selection.setElements(e1, e2);

		assertTrue(action.isEnabled());
	}

	@Test
	public void restartActionEnablementForProject() throws Exception {
		doRestartActionEnablementForProjectTest(RunState.RUNNING);
	}

	@Test
	public void redebugActionEnablementForProject() throws Exception {
		doRestartActionEnablementForProjectTest(RunState.DEBUGGING);
	}

	private void doRestartActionEnablementForProjectTest(RunState runOrDebug) throws Exception, CoreException {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		final BootDashElement element = harness.getElementWithName(projectName);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final RunStateAction action = getRunStateAction(runOrDebug);

		//If selection is empty the action must not be enabled
		assertTrue(selection.isEmpty());
		assertFalse(action.isEnabled());

		//If selection has one element...
		selection.setElements(element);

		//a) and element has no launch configs...
		assertTrue(element.getLaunchConfigs().isEmpty());
		assertTrue(action.isEnabled());

		//b) and element has multiple launch config
		action.setEnabled(false); // make sure the test won't pass 'by accident'.
		final ILaunchConfiguration c1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		final ILaunchConfiguration c2 = BootLaunchConfigurationDelegate.createConf(javaProject);
		new ACondition(2000) {
			public boolean test() throws Exception {
				assertEquals(ImmutableSet.of(c1,c2), element.getLaunchConfigs());
				assertTrue(action.isEnabled());
				return true;
			}
		};

		//b) and element has a single launch config
		action.setEnabled(false); // make sure the test won't pass 'by accident'.
		c2.delete();
		new ACondition(2000) {
			public boolean test() throws Exception {
				assertEquals(ImmutableSet.of(c1), element.getLaunchConfigs());
				assertTrue(action.isEnabled());
				return true;
			}
		};
	}

	@Test
	public void restartActionEnablementForMultipleProjects() throws Exception {
		IProject p1 = createBootProject("project1");
		IProject p2 = createBootProject("project2");

		BootDashElement e1 = harness.getElementWithName(p1.getName());
		BootDashElement e2 = harness.getElementWithName(p2.getName());

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final RunStateAction action = getRunStateAction(RunState.RUNNING);

		assertTrue(selection.isEmpty());
		assertFalse(action.isEnabled());

		selection.setElements(e1, e2);

		assertTrue(action.isEnabled());
	}

	@Test
	public void restartActionTargetsChildrenDirectly() throws Exception {
		String projectName = "hohoho";
		IProject project = createBootProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);

		MockMultiSelection<BootDashElement> selection = harness.selection;
		final BootDashElement element = harness.getElementWithName(projectName);
		final ILaunchConfiguration c1 = BootLaunchConfigurationDelegate.createConf(javaProject);
		final ILaunchConfiguration c2 = BootLaunchConfigurationDelegate.createConf(javaProject);
		BootDashElement child1 = harness.getElementFor(c1);
		BootDashElement child2 = harness.getElementFor(c2);

		ImmutableSet<BootDashElement> theChildren = ImmutableSet.of(
				child1, child2
		);
		ACondition.waitFor("children to appear", 3000, () -> {
			assertEquals(theChildren, element.getChildren().getValues());
		});

		for (RunState runOrDebug: EnumSet.of(RunState.RUNNING, RunState.DEBUGGING)) {
			final RunOrDebugStateAction action = (RunOrDebugStateAction) getRunStateAction(runOrDebug);
			selection.setElements(/*none*/);
			assertEquals(ImmutableSet.of(), action.getSelectedElements());
			assertEquals(ImmutableSet.of(), action.getTargetElements());

			selection.setElements(element);
			assertEquals(ImmutableSet.of(element), action.getSelectedElements());
			assertEquals(""+runOrDebug, theChildren, action.getTargetElements());

			selection.setElements(element, child1);
			assertEquals(ImmutableSet.of(element, child1), action.getSelectedElements());
			assertEquals(theChildren, action.getTargetElements());
		}
	}

	////////////////////////////////////////////////////////////////////////


	private TestBootDashModelContext context;
	private BootProjectTestHarness projects;
	private BootDashViewModelHarness harness;
	private BootDashActions actions;

	@Rule
	public AutobuildingEnablement autobuild = new AutobuildingEnablement(false);

	@Rule
	public TestBracketter bracketter = new TestBracketter();

	@Rule
	public LaunchCleanups launchCleanups = new LaunchCleanups();

	private MockLiveProcesses processes;

	@Before
	public void setup() throws Exception {
		StsTestUtil.deleteAllProjects();
		this.context = new TestBootDashModelContext(
				ResourcesPlugin.getWorkspace(),
				DebugPlugin.getDefault().getLaunchManager()
		);
		SimpleDIContext injections = context.injections;

		this.processes = new MockLiveProcesses();
		this.harness = new BootDashViewModelHarness(context.withTargetTypes(RunTargetTypes.LOCAL));
		this.projects = new BootProjectTestHarness(context.getWorkspace());
		this.actions = new BootDashActions(harness.model, harness.selection.forReading(), injections, processes.commandExecutor);
	}

	@After
	public void tearDown() throws Exception {
		this.harness.dispose();
		this.actions.dispose();
	}


	private IProject createBootProject(String projectName, WizardConfigurer... extraConfs) throws Exception {
		return projects.createBootWebProject(projectName, extraConfs);
	}

	private RunStateAction getRunStateAction(RunState goalState) {
		for (RunStateAction s : actions.getRunStateActions()) {
			if (s.getGoalState() == goalState) {
				return s;
			}
		}
		return null;
	}

	private BootDashElement mockLocalElement() {
		BootDashElement element = mock(BootDashElement.class);
		RunTarget target = RunTargets.LOCAL;
		when(element.getBootDashModel()).thenReturn(harness.getRunTargetModel(RunTargetTypes.LOCAL));
		when(element.getTarget()).thenReturn(target);
		when(element.supportedGoalStates()).thenReturn(RunTargets.LOCAL_RUN_GOAL_STATES);
		return element;
	}

}
