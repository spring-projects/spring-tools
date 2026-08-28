/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.*;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withImportStrategy;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withStarters;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.assertContains;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.assertNotContains;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.createFile;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.graphics.Color;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.ide.eclipse.beans.ui.live.model.LiveBeansModel;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.RemoteBootDashModel;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.deployment.DeployToRemoteTargetAction;
import org.springframework.ide.eclipse.boot.dash.console.ApplicationLogConsole;
import org.springframework.ide.eclipse.boot.dash.console.CloudAppLogManager;
import org.springframework.ide.eclipse.boot.dash.devtools.DevtoolsUtil;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerApp;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerContainer;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerImage;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerRunTarget;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerRunTargetType;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerTargetParams;
import org.springframework.ide.eclipse.boot.dash.docker.ui.SelectDockerDaemonDialog.Model;
import org.springframework.ide.eclipse.boot.dash.labels.BootDashLabels;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElement;
import org.springframework.ide.eclipse.boot.dash.model.Failable;
import org.springframework.ide.eclipse.boot.dash.model.RefreshState;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.Taggable;
import org.springframework.ide.eclipse.boot.dash.model.actuator.RequestMapping;
import org.springframework.ide.eclipse.boot.dash.model.actuator.env.LiveEnvModel;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteBootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.remote.RefreshStateTracker;
import org.springframework.ide.eclipse.boot.dash.model.remote.RemoteJavaLaunchUtil;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RemoteRunTarget;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RemoteRunTarget.ConnectMode;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetTypes;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashElementsAction;
import org.springframework.ide.eclipse.boot.dash.views.AddRunTargetAction;
import org.springframework.ide.eclipse.boot.dash.views.BootDashActions;
import org.springframework.ide.eclipse.boot.dash.views.DeleteElementsAction;
import org.springframework.ide.eclipse.boot.dash.views.EnableRemoteDevtoolsAction;
import org.springframework.ide.eclipse.boot.dash.views.RestartDevtoolsClientAction;
import org.springframework.ide.eclipse.boot.dash.views.RunStateAction;
import org.springframework.ide.eclipse.boot.launch.devtools.BootDevtoolsClientLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness;
import org.springframework.ide.eclipse.boot.test.util.TestBracketter;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder.Contributor;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder.RemoteAppData;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSets;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.tests.util.StsTestCase;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.*;

public class BootDashDockerTests {

	private static final int BUILD_IMAGE_TIMEOUT = 30_000;
	private static final String DEFAULT_DOCKER_URL = "unix:///var/run/docker.sock";

//	@Rule public LaunchCleanups launches = new LaunchCleanups();
	@Rule public TestBracketter bracks = new TestBracketter();

	@Test
	public void testCreateDockerTarget() throws Exception {
		createDockerTarget();
	}

	@Test
	public void projectWithdDockerFile() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		IProject project = projects.createBootWebProject("webby",
				bootVersionAtLeast("2.3.0"),
				withJavaVersion("11")
		);
		createExeFile(project, "sts-docker-build.sh",
				"#!/bin/bash\n" +
				"docker build -t webby ."
		);
		createFile(project, "Dockerfile",
				"FROM openjdk:11 as builder\n" +
				"CMD mkdir /source\n" +
				"COPY . /source\n" +
				"WORKDIR /source\n" +
				"RUN ls -la\n" +
				"RUN ./mvnw clean package\n" +
				"FROM openjdk:11\n" +
				"COPY --from=builder /source/target/*.jar /app.jar\n" +
				"ENTRYPOINT java $JAVA_OPTS -jar /app.jar"
		);

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		assertContains("webby:latest", img.getStyledName(null).getString());

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});
	}


	@Test
	public void missingBuildTagException() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		IProject project = projects.createBootWebProject("webby",
				bootVersionAtLeast("2.3.0"),
				withJavaVersion("11")
		);
		createExeFile(project, "sts-docker-build.sh",
				"#!/bin/bash\n" +
				"# do nothing"
		);
		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		ACondition.waitFor("error marker", 3_000, () -> {
			RefreshState s = dep.getRefreshState();
			assertTrue(s.isError());
			assertEquals("MissingBuildTagException: Couldn't detect the image id or tag", s.getMessage());
		});
	}

	@Test
	public void projectWithdDockerFileNoTag() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		IProject project = projects.createBootWebProject("webby",
				bootVersionAtLeast("2.3.0"),
				withJavaVersion("11")
		);
		createExeFile(project, "sts-docker-build.sh",
				"#!/bin/bash\n" +
				"docker build ."
		);
		createFile(project, "Dockerfile",
				"FROM openjdk:11 as builder\n" +
				"CMD mkdir /source\n" +
				"COPY . /source\n" +
				"WORKDIR /source\n" +
				"RUN ls -la\n" +
				"RUN ./mvnw clean package\n" +
				"FROM openjdk:11\n" +
				"COPY --from=builder /source/target/*.jar /app.jar\n" +
				"ENTRYPOINT java $JAVA_OPTS -jar /app.jar"
		);

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});
	}

	@Test
	public void noAutoLiveHoverConnectWithoutActuator() throws Exception {
		IProject project = projects.createBootWebProject("webby-no-actuators",
				withJavaVersion("11")
		);
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		List<RemoteBootAppsDataHolder.Contributor> remoteAppDataProviders = harness.context.injections.getBeans(RemoteBootAppsDataHolder.Contributor.class);
		ObservableSet<RemoteAppData> remoteAppDataExp = RemoteBootAppsDataHolder.union(remoteAppDataProviders);
		DockerContainer conData = (DockerContainer) con.getAppData();
		String conName = conData.getName();
		AtomicReference<RemoteAppData> found = new AtomicReference<>();
		ACondition.waitFor("Remote app data", 2000, () -> {
			for (RemoteAppData remoteApp : remoteAppDataExp.getValues()) {
				String pid = remoteApp.getProcessId();
				if (pid.equals(conName)) {
					found.set(remoteApp);
				}
			}
			assertThat("remoteApp data not found", found.get() != null);
		});
		assertThat("manual connect should be enabled", found.get().isManualConnect());
	}

	@Test
	public void autoLiveHoverConnectWithActuator() throws Exception {
		IProject project = projects.createBootWebProject("webby-with-actuators",
				withJavaVersion("11"),
				withStarters("actuator")
		);
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		List<RemoteBootAppsDataHolder.Contributor> remoteAppDataProviders = harness.context.injections.getBeans(RemoteBootAppsDataHolder.Contributor.class);
		ObservableSet<RemoteAppData> remoteAppDataExp = RemoteBootAppsDataHolder.union(remoteAppDataProviders);
		DockerContainer conData = (DockerContainer) con.getAppData();
		String conName = conData.getName();
		AtomicReference<RemoteAppData> found = new AtomicReference<>();
		ACondition.waitFor("Remote app data", 2000, () -> {
			for (RemoteAppData remoteApp : remoteAppDataExp.getValues()) {
				String pid = remoteApp.getProcessId();
				if (pid.equals(conName)) {
					found.set(remoteApp);
				}
			}
			assertThat("remoteApp data not found", found.get() != null);
		});
		assertThat("manual connect should NOT be enabled", !found.get().isManualConnect());
	}


	@Test
	public void devtoolsFullScenario() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"),
				withStarters("devtools")
		);

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		EnableRemoteDevtoolsAction disableDevtools = actions().getEnableDevtoolsAction();

		harness.selection.setElements(con);
		assertFalse(disableDevtools.isVisible());
		harness.selection.setElements(img);
		assertFalse(disableDevtools.isVisible());

		harness.selection.setElements(dep);
		assertTrue(disableDevtools.isVisible());
		assertTrue(disableDevtools.isEnabled());
		assertEquals("Disable Remote DevTools Server", disableDevtools.getText());

		assertNotNull(getDevtoolsSecret(dep));

		RestartDevtoolsClientAction restartClient = actions().getRestartDevtoolsClientAction();
		harness.selection.setElements(dep);
		assertFalse(restartClient.isVisible());
		harness.selection.setElements(img);
		assertFalse(restartClient.isVisible());
		harness.selection.setElements(con);
		assertTrue(restartClient.isVisible());
		assertTrue(restartClient.isEnabled());

		Color grey = BootDashLabels.colorGrey();
		Color green = BootDashLabels.colorGreen();

		ACondition.waitFor("devtools client started / icons", 5_000, () -> {
			assertNotNull(dep.getRunStateImageDecoration());
			assertNotNull(img.getRunStateImageDecoration());
			assertNotNull(con.getRunStateImageDecoration());
		});
		harness.assertLabelContains("devtools", green, dep); // green means... hasdependency and has secret.
		harness.assertLabelContains("devtools", grey, img);  // grey means... hasdependency but not secret.
		harness.assertLabelContains("devtools", green, con);

		{
			ILaunch firstLaunch = assertActiveDevtoolsClientLaunch(con);
			assertTrue(firstLaunch.canTerminate());
			firstLaunch.terminate();
			ACondition.waitFor("client termination reflected in icons", 5_000, () -> {
				assertTrue(firstLaunch.isTerminated());
				assertNull(dep.getRunStateImageDecoration());
				assertNull(img.getRunStateImageDecoration());
				assertNull(con.getRunStateImageDecoration());
			});
		}

		harness.selection.setElements(con);
		restartClient.run();
		ACondition.waitFor("active devtools client", 10_000, () -> {
			assertActiveDevtoolsClientLaunch(con);
			assertNotNull(dep.getRunStateImageDecoration());
			assertNotNull(img.getRunStateImageDecoration());
			assertNotNull(con.getRunStateImageDecoration());

			harness.assertLabelContains("devtools", green, dep);
			harness.assertLabelContains("devtools", grey, img);
			harness.assertLabelContains("devtools", green, con);
		});
		ILaunch launch = assertActiveDevtoolsClientLaunch(con);
		try {
			ILaunchConfiguration conf = launch.getLaunchConfiguration();

			String deploymentSecret = getDevtoolsSecret(dep);
			String containerSecret = getDevtoolsSecret(con);
			assertNotNull(deploymentSecret);
			assertEquals(containerSecret, BootDevtoolsClientLaunchConfigurationDelegate.getRemoteSecret(conf));

			createFile(project, "src/main/java/com/example/demo/HelloController.java", helloController("Good"));
			ACondition.waitFor("Good controller", 15_000, () -> {
				String url = con.getUrl();
				assertEquals("Good", IOUtils.toString(new URI(url), "UTF8"));
			});

			createFile(project, "src/main/java/com/example/demo/HelloController.java", helloController("Better"));
			ACondition.waitFor("Better controller", 15_000, () -> {
				String url = con.getUrl();
				assertEquals("Better", IOUtils.toString(new URI(url), "UTF8"));
			});

			harness.selection.setElements(con);
			assertFalse(disableDevtools.isVisible());
			harness.selection.setElements(img);
			assertFalse(disableDevtools.isVisible());
			harness.selection.setElements(dep);
			assertTrue(disableDevtools.isVisible());
			assertTrue(disableDevtools.isEnabled());

			disableDevtools.run();
			disableDevtools.lastOperation.get();

			assertNull(getDevtoolsSecret(dep));

			ACondition.waitFor("old container stopped", 5_000, () -> {
				assertEquals(RunState.INACTIVE, con.getRunState());
			});

			GenericRemoteAppElement img2 = waitForChild(dep, d -> d instanceof DockerImage && !d.getName().equals(img.getName()));
			GenericRemoteAppElement con2 = waitForChild(img2, d -> d instanceof DockerContainer);
			containerSecret = getDevtoolsSecret(con2);
			assertNull(containerSecret);

			ACondition.waitFor("second container running", 10_000, () -> {
				assertEquals(RunState.RUNNING, con2.getRunState());
				assertEquals(RunState.INACTIVE, con.getRunState());
			});

			harness.selection.setElements(con2);
			assertTrue(restartClient.isVisible());
			assertFalse(restartClient.isEnabled());

			ACondition.waitFor("devtools client terminated", 10_000, () -> {
				assertTrue(launch.isTerminated());
				assertNull(dep.getRunStateImageDecoration());
				assertNull(img.getRunStateImageDecoration());
				assertNull(con.getRunStateImageDecoration());
				assertNull(img2.getRunStateImageDecoration());
				assertNull(con2.getRunStateImageDecoration());

				// text labels and colors
				harness.assertLabelContains("devtools", grey, dep);
				harness.assertLabelContains("devtools", grey, img);
				harness.assertLabelNotContains("devtools", img2);
				harness.assertLabelContains("devtools", green, con);
				harness.assertLabelNotContains("devtools", con2);
			});

		} finally {
			launch.terminate();
			ACondition.waitFor("launch terminated", 2_000, () -> {
				assertTrue(launch.isTerminated());
			});
		}
	}

	private static String helloController(String message) {
		return "package com.example.demo;\n" +
				"\n" +
				"import org.springframework.web.bind.annotation.GetMapping;\n" +
				"import org.springframework.web.bind.annotation.RestController;\n" +
				"\n" +
				"@RestController\n" +
				"public class HelloController {\n" +
				"\n" +
				"	@GetMapping(\"/\")\n" +
				"	public String hello() {\n" +
				"		return \""+message+"\";\n" +
				"	}\n" +
				"}";
	}

	private String getDevtoolsSecret(GenericRemoteAppElement el) {
		App data = el.getAppData();
		System.out.println("getDevtoolsSecret data ="+data);
		if (data instanceof DockerApp) {
			@SuppressWarnings("resource")
			DockerApp app = (DockerApp)data;
			return app.deployment().getSystemProperties().getOrDefault(DevtoolsUtil.REMOTE_SECRET_PROP, null);
		} else if (data instanceof DockerContainer) {
			DockerContainer con = (DockerContainer)data;
			return con.getSystemProps().getOrDefault(DevtoolsUtil.REMOTE_SECRET_PROP, null);
		} else {
			return null;
		}
	}

	@Test
	public void dragAndDropAProject() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		// PT 174387686 - Disable show/link console context menu and tool bar actions for docker images
		AbstractBootDashElementsAction openConsoleAction = actions().getOpenConsoleAction();
		AbstractBootDashElementsAction linkWithConsoleAction = actions().getLinkWithConsoleAction();
		harness.selection.setElements(dep);
		assertTrue(openConsoleAction.isVisible());
		assertTrue(openConsoleAction.isEnabled());
		assertTrue(linkWithConsoleAction.isVisible());
		assertTrue(linkWithConsoleAction.isEnabled());

		harness.selection.setElements(img);
		assertFalse(openConsoleAction.isVisible());
		assertFalse(openConsoleAction.isEnabled());
		assertFalse(linkWithConsoleAction.isVisible());
		assertFalse(linkWithConsoleAction.isEnabled());

		harness.selection.setElements(con);
		assertTrue(openConsoleAction.isVisible());
		assertTrue(openConsoleAction.isEnabled());
		assertTrue(linkWithConsoleAction.isVisible());
		assertTrue(linkWithConsoleAction.isEnabled());

		assertConsoleName(dep, "webby - image build output @ unix:///var/run/docker.sock", false);
		assertConsoleContains(dep, "Successfully built image 'docker.io/library/webby");
		assertConsoleContains(dep, "Starting WebbyApplication");

		assertNoConsole(img);

		assertConsoleName(con, "webby - in container "+con.getStyledName(null).getString()+ " @ unix:///var/run/docker.sock", true);
		ACondition.waitFor("expected output in container", 5_000, () -> {
			assertConsoleContains(con, "Starting WebbyApplication");
			assertConsoleNotContains(con, "Successfully built image 'docker.io/library/webby");
		});

		assertFalse(harness.getLabel(dep).contains("devtools"));
		assertFalse(harness.getLabel(img).contains("devtools"));
		assertFalse(harness.getLabel(con).contains("devtools"));

		verifyNoMoreInteractions(ui());
	}

	@Test
	public void consoleStopAndStartContainer() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		ACondition.waitFor("consoel output from starting app", 5_000, () -> {
			assertConsoleContains(dep, "Successfully built image 'docker.io/library/webby");
			assertConsoleContains(dep, "Starting WebbyApplication");
			assertNoConsole(img);
			assertConsoleName(con, "webby - in container "+con.getStyledName(null).getString()+ " @ unix:///var/run/docker.sock", true);
			assertConsoleContains(con, "Starting WebbyApplication");
			assertConsoleNotContains(con, "Successfully built image 'docker.io/library/webby");
		});

		clearConsole(con);
		clearConsole(dep);

		RunStateAction stopAction = stopAction();
		harness.selection.setElements(con);
		stopAction.run();

		ACondition.waitFor("all stopped", 5_000, () -> {
			assertEquals(RunState.INACTIVE, dep.getRunState());
			assertEquals(RunState.INACTIVE, img.getRunState());
			assertEquals(RunState.INACTIVE, con.getRunState());
		});

//		assertConsoleContains(con, "[extShutdownHook]");
		assertConsoleNotContains(con, "Starting WebbyApplication");
		assertNoConsole(img);
//		assertConsoleContains(dep, "[extShutdownHook]");
		assertConsoleNotContains(dep, "Starting WebbyApplication");

		RunStateAction startAction = restartAction();
		harness.selection.setElements(con);
		startAction.run();

		ACondition.waitFor("all started", 15_000, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		ACondition.waitFor("output from restarting app", 5_000, () -> {
			assertConsoleNotContains(dep, "Successfully built image 'docker.io/library/webby");
			assertConsoleNotContains(dep, "Starting WebbyApplication");
			assertNoConsole(img);
			assertConsoleName(con, "webby - in container "+con.getStyledName(null).getString()+ " @ unix:///var/run/docker.sock", true);
			assertConsoleNotContains(con, "Successfully built image 'docker.io/library/webby");
			assertConsoleContains(con, "Starting service [Tomcat]");
		});
	}

	private void clearConsole(GenericRemoteAppElement element) throws Exception {
		CloudAppLogManager logManager = context.injections.getBean(CloudAppLogManager.class);
		ApplicationLogConsole console = logManager.getExisitingConsole(element);
		assertNotNull("No console for " + element.getStyledName(null).getString(), console);
		console.clearConsole();
		ACondition.waitFor("clear console", 5_000, () -> {
			assertEquals("", console.getDocument().get().trim());
		});
	}


	private void assertConsoleName(GenericRemoteAppElement element, String expectedName, boolean create) throws Exception {
		CloudAppLogManager logManager = context.injections.getBean(CloudAppLogManager.class);
		ApplicationLogConsole console = create
				? logManager.getOrCreateConsole(element)
				: logManager.getExisitingConsole(element);
		assertNotNull("No console for " + element.getStyledName(null).getString(), console);
		assertEquals(expectedName, console.getName());
	}

	private void assertConsoleContains(GenericRemoteAppElement element, String expectSnippet) {
		CloudAppLogManager logManager = context.injections.getBean(CloudAppLogManager.class);
		ApplicationLogConsole console = logManager.getExisitingConsole(element);
		assertNotNull("No console for " + element.getStyledName(null).getString(), console);
		String text = console.getDocument().get();
		assertContains(expectSnippet, text);
	}

	private void assertConsoleNotContains(GenericRemoteAppElement element, String expectSnippet) {
		CloudAppLogManager logManager = context.injections.getBean(CloudAppLogManager.class);
		ApplicationLogConsole console = logManager.getExisitingConsole(element);
		assertNotNull("No console for " + element.getStyledName(null).getString(), console);
		String text = console.getDocument().get();
		assertNotContains(expectSnippet, text);
	}

	private void assertNoConsole(GenericRemoteAppElement element) {
		CloudAppLogManager logManager = context.injections.getBean(CloudAppLogManager.class);
		ApplicationLogConsole console = logManager.getExisitingConsole(element);
		assertNull("Console for " + element.getStyledName(null).getString() + " exists!", console);
	}

	@Test
	public void deployAndDebugOnTarget() throws Exception {
		RemoteBootDashModel model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		BootProjectDashElement localElement = harness.waitForElement(2_000, project);
		DeployToRemoteTargetAction<?,?> a = debugOnDockerAction();
		harness.selection.setElements(localElement);
		assertTrue(a.isEnabled());
		a.run();

		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all debugging", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.DEBUGGING, dep.getRunState());
			assertEquals(RunState.DEBUGGING, img.getRunState());
			assertEquals(RunState.DEBUGGING, con.getRunState());
		});
		ACondition.waitFor("remote debug launch", 5_000, () -> assertActiveDebugLaunch(con));


		ILaunch launch = assertActiveDebugLaunch(con);
		ILaunchConfiguration conf = launch.getLaunchConfiguration();
		assertEquals(IJavaLaunchConfigurationConstants.ID_REMOTE_JAVA_APPLICATION, conf.getType().getIdentifier());

		assertEquals(con.getStyledName(null).toString(), conf.getName());
		ACondition.waitFor("launch can terminate", 2_000, () -> {
			assertTrue(launch.canTerminate());
		});
		launch.terminate();
		ACondition.waitFor("launch termination", 5_000, () -> {
			assertTrue(launch.isTerminated());
		});

		assertEquals(ImmutableSet.of(conf), con.getLaunchConfigs());

		ACondition.waitFor("all stopped", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.INACTIVE, dep.getRunState());
			assertEquals(RunState.INACTIVE, img.getRunState());
			assertEquals(RunState.INACTIVE, con.getRunState());
		});
	}

	@Test
	public void devtoolsAndDebug() throws Exception {
		RemoteBootDashModel model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"),
				withStarters("devtools"));

		BootProjectDashElement localElement = harness.waitForElement(2_000, project);
		DeployToRemoteTargetAction<?,?> a = debugOnDockerAction();
		harness.selection.setElements(localElement);
		assertTrue(a.isEnabled());
		a.run();

		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all debugging", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.DEBUGGING, dep.getRunState());
			assertEquals(RunState.DEBUGGING, img.getRunState());
			assertEquals(RunState.DEBUGGING, con.getRunState());
		});
		ACondition.waitFor("remote debug launch", 5_000, () -> assertActiveDebugLaunch(con));

		InspectContainerResponse containerInfo = client().inspectContainerCmd(con.getName()).exec();
		String JAVA_OPTS = getEnv(containerInfo, "JAVA_OPTS");

		String jmxPort = containerInfo.getConfig().getLabels().get(DockerApp.JMX_PORT);
		String debugPort = containerInfo.getConfig().getLabels().get(DockerApp.DEBUG_PORT);
		String devtoolsSecret = getDevtoolsSecret(con);
		assertNotNull(devtoolsSecret);
		assertEquals(
				"-Dcom.sun.management.jmxremote.ssl=false "+
				"-Dcom.sun.management.jmxremote.authenticate=false "+
				"-Dcom.sun.management.jmxremote.port="+jmxPort+" "+
				"-Dcom.sun.management.jmxremote.rmi.port="+jmxPort+" "+
				"-Djava.rmi.server.hostname=localhost "+
				"-Dcom.sun.management.jmxremote.local.only=false "+
				"-Dspring.jmx.enabled=true "+
				"-Dspring.application.admin.enabled=true "+
				"-Xdebug "+
				"-Xrunjdwp:server=y,transport=dt_socket,suspend=n,address=*:"+debugPort+" "+
				"-Dspring.devtools.remote.secret="+devtoolsSecret,

				JAVA_OPTS
		);

		ILaunch launch = assertActiveDebugLaunch(con);
		ILaunchConfiguration conf = launch.getLaunchConfiguration();
		assertEquals(IJavaLaunchConfigurationConstants.ID_REMOTE_JAVA_APPLICATION, conf.getType().getIdentifier());

		assertEquals(con.getStyledName(null).toString(), conf.getName());
		ACondition.waitFor("launch can terminate", 2_000, () -> {
			assertTrue(launch.canTerminate());
		});
		launch.terminate();
		ACondition.waitFor("launch termination", 5_000, () -> {
			assertTrue(launch.isTerminated());
		});

		assertEquals(ImmutableSet.of(conf), con.getLaunchConfigs());

		ACondition.waitFor("all stopped", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.INACTIVE, dep.getRunState());
			assertEquals(RunState.INACTIVE, img.getRunState());
			assertEquals(RunState.INACTIVE, con.getRunState());
		});
	}



	private String getEnv(InspectContainerResponse imageInspect, String name) {
		String[] envs = imageInspect.getConfig().getEnv();
		for (String string : envs) {
			if (string.startsWith(name+"=")) {
				return string.substring(name.length()+1);
			}
		}
		return null;
	}

	@Test
	public void deleteRunningContainer() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		verifyNoMoreInteractions(ui());
		Mockito.reset(ui());

		String containerId = con.getName();
		assertEquals(1, listContainersWithId(containerId).size());

		DeleteElementsAction<?> delete = actions().getDeleteAppsAction();
		harness.selection.setElements(con);
		assertTrue(delete.isEnabled());
		delete.run();

		ACondition.waitFor("container deleted", 5_000, () -> {
			assertTrue(img.getChildren().getValue().isEmpty());
			assertEquals(0, listContainersWithId(containerId).size());
		});
		verifyNoMoreInteractions(ui());
	}

	private List<Container> listContainersWithId(String containerId)
			throws Exception {
		return
				client().listContainersCmd()
					.withShowAll(true)
					.withIdFilter(ImmutableList.of(containerId))
					.withLabelFilter(ImmutableList.of(DockerApp.APP_NAME))
				.exec();
	}

	@Test
	public void stopAppCurrentSession() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});
		verifyNoMoreInteractions(ui());
		reset(ui());

		RunStateAction stop = stopAction();
		harness.selection.setElements(dep);
		assertTrue(stop.isEnabled());
		stop.run();

		ACondition.waitFor("all started", 5_000, () -> {
			assertEquals(RunState.INACTIVE, dep.getRunState());
			assertEquals(RunState.INACTIVE, img.getRunState());
			assertEquals(RunState.INACTIVE, con.getRunState());
		});
	}

	@Test
	public void stopAppPreviousSession() throws Exception {
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));
		String firstSession;

		{
			GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
			Mockito.reset(ui());
			dragAndDrop(project, model);
			GenericRemoteAppElement dep = waitForDeployment(model, project);
			GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
			GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

			ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
				assertEquals(RunState.RUNNING, dep.getRunState());
				assertEquals(RunState.RUNNING, img.getRunState());
				assertEquals(RunState.RUNNING, con.getRunState());
			});
			verifyNoMoreInteractions(ui());
			reset(ui());

			firstSession = getSessionId(model);
		}
		harness.reload();
		{
			RemoteBootDashModel model = (RemoteBootDashModel) harness.getRunTargetModel(DockerRunTargetType.class);

			assertFalse(firstSession.equals(getSessionId(model)));

			GenericRemoteAppElement dep = waitForDeployment(model, project);
			GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
			GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);
			ACondition.waitFor("all started", 5_000, () -> {
				assertEquals(RunState.RUNNING, dep.getRunState());
				assertEquals(RunState.RUNNING, img.getRunState());
				assertEquals(RunState.RUNNING, con.getRunState());
			});
			RunStateAction stop = stopAction();
			harness.selection.setElements(dep);
			assertTrue(stop.isEnabled());
			stop.run();

			ACondition.waitFor("all stopped", 5_000, () -> {
				assertEquals(RunState.INACTIVE, dep.getRunState());
				assertEquals(RunState.INACTIVE, img.getRunState());
				assertEquals(RunState.INACTIVE, con.getRunState());
			});
		}
	}

	@Test
	public void liveBeans() throws Exception {
		IProject project = projects.createBootProject("webby-actuator",
				bootVersionAtLeast("2.3.0"),
				withStarters("web", "actuator")
		);
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);
		GenericRemoteAppElement[] nodes = {
				con, img, dep
		};

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});
		verifyNoMoreInteractions(ui());

		String jmxUrl = con.getJmxUrl();
		ACondition.waitFor("live beans model", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				assertEquals(ImmutableSet.of(jmxUrl), node.getActuatorUrls().getValue());
				LiveBeansModel beans = node.getLiveBeans().orElse(null);
				assertNotNull(beans);
				assertFalse(beans.getBeans().isEmpty());
			}
		});

		RunStateAction stop = stopAction();
		harness.selection.setElements(dep);
		stop.run();

		ACondition.waitFor("Container stopped", 15_000, () -> { //Sometimes stopping container takes a long time. Not sure why.
			assertEquals(RunState.INACTIVE, con.getRunState());
		});
		ACondition.waitFor("live beans gone", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				assertTrue(node.getActuatorUrls().getValue().isEmpty());
				assertNull(node.getLiveBeans().orElse(null));
			}
		});
	}

	@Test
	public void liveRequestMappings() throws Exception {
		IProject project = projects.createBootProject("webby-actuator",
				bootVersionAtLeast("2.3.0"),
				withStarters("web", "actuator")
		);
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);
		GenericRemoteAppElement[] nodes = {
				con, img, dep
		};

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});
		verifyNoMoreInteractions(ui());

		String jmxUrl = con.getJmxUrl();
		ACondition.waitFor("live requestmappings", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				assertEquals(ImmutableSet.of(jmxUrl), node.getActuatorUrls().getValue());
				List<RequestMapping> rm = node.getLiveRequestMappings().orElse(null);
				assertNotNull(rm);
				assertFalse(rm.isEmpty());
			}
		});

		RunStateAction stop = stopAction();
		harness.selection.setElements(dep);
		stop.run();

		ACondition.waitFor("Container stopped", 15_000, () -> { //Sometimes stopping container takes a long time. Not sure why.
			assertEquals(RunState.INACTIVE, con.getRunState());
		});
		ACondition.waitFor("live requestmappings gone", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				assertTrue(node.getActuatorUrls().getValue().isEmpty());
				assertNull(node.getLiveBeans().orElse(null));
			}
		});
	}

	@Test
	public void liveDataNotAvailable() throws Exception {
		IProject project = projects.createBootProject("webby-actuator",
				bootVersionAtLeast("2.3.0"),
				withStarters("web")
		);
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);
		GenericRemoteAppElement[] nodes = {
				con, img, dep
		};

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});
		verifyNoMoreInteractions(ui());

		ACondition.waitFor("live requestmappings", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				Failable<ImmutableList<RequestMapping>> rm = node.getLiveRequestMappings();
				Failable<LiveBeansModel> beans = node.getLiveBeans();
				Failable<LiveEnvModel> env = node.getLiveEnv();

				assertTrue(rm.hasFailed());
				assertContains("Enable actuator endpoint <b>mappings</b>", rm.getErrorMessage().toHtml());

				assertTrue(beans.hasFailed());
				assertContains("Enable actuator endpoint <b>beans</b>", beans.getErrorMessage().toHtml());

				assertTrue(env.hasFailed());
				assertContains("Enable actuator endpoint <b>env</b>", env.getErrorMessage().toHtml());
			}
		});

		RunStateAction stop = stopAction();
		harness.selection.setElements(dep);
		stop.run();

		ACondition.waitFor("Container stopped", 15_000, () -> { //Sometimes stopping container takes a long time. Not sure why.
			assertEquals(RunState.INACTIVE, con.getRunState());
		});
		ACondition.waitFor("live requestmappings gone", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				assertTrue(node.getActuatorUrls().getValue().isEmpty());
				assertNull(node.getLiveBeans().orElse(null));
			}
		});
	}

	@Test
	public void liveEnv() throws Exception {
		IProject project = projects.createBootProject("webby-actuator",
				bootVersionAtLeast("2.3.0"),
				withStarters("web", "actuator")
		);
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);
		GenericRemoteAppElement[] nodes = {
				con, img, dep
		};

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});
		verifyNoMoreInteractions(ui());

		String jmxUrl = con.getJmxUrl();
		ACondition.waitFor("live env", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				assertEquals(ImmutableSet.of(jmxUrl), node.getActuatorUrls().getValue());
				LiveEnvModel env = node.getLiveEnv().orElse(null);
				assertNotNull(env);
				assertFalse(env.getPropertySources().getPropertySources().isEmpty());
			}
		});

		RunStateAction stop = stopAction();
		harness.selection.setElements(dep);
		stop.run();

		ACondition.waitFor("Container stopped", 15_000, () -> { //Sometimes stopping container takes a long time. Not sure why.
			assertEquals(RunState.INACTIVE, con.getRunState());
		});
		ACondition.waitFor("live env gone", 5_000, () -> {
			for (GenericRemoteAppElement node : nodes) {
				assertTrue(node.getActuatorUrls().getValue().isEmpty());
				assertNull(node.getLiveEnv().orElse(null));
			}
		});
	}

	@Test
	public void dragAndDropGradleProject() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"), withImportStrategy("GRADLE"));

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		verifyNoMoreInteractions(ui());
	}

	@Test
	public void tagsPersistence() throws Exception {
	IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

	{
			GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
			Mockito.reset(ui());

			dragAndDrop(project, model);
			GenericRemoteAppElement dep = waitForDeployment(model, project);
			GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
			GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

			assertTrue("Tags empty initally", dep.getTags().isEmpty());
			assertTrue("Tags empty initally", img.getTags().isEmpty());
			assertTrue("Tags empty initally", con.getTags().isEmpty());

			setTags(dep, "tag1", "tag2");
			assertTags(dep, "tag1", "tag2");
			harness.assertLabelContains("[tag1, tag2]", dep);
		}

		harness.reload();

		{
			RemoteBootDashModel model = (RemoteBootDashModel) harness
					.getRunTargetModel(DockerRunTargetType.class);
			GenericRemoteAppElement dep = waitForDeployment(model, project);
			GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
			GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);
			assertTags(dep, "tag1", "tag2");
			assertTrue("Should be still empty", img.getTags().isEmpty());
			assertTrue("Should be still empty", con.getTags().isEmpty());
			harness.assertLabelContains("[tag1, tag2]", dep);
		}

	}

	@Test
	public void urlComputation() throws Exception {
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		img.setDefaultRequestMappingPath("/hello");

		ACondition.waitFor("Wait for port to be defined", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(1, con.getLivePorts().size());
			assertEquals(1, img.getLivePorts().size());
			assertEquals(1, dep.getLivePorts().size());
		});

		int port = con.getLivePort();
		assertEquals(ImmutableSet.of(port), img.getLivePorts());
		assertEquals(ImmutableSet.of(port), dep.getLivePorts());
		assertEquals(ImmutableSet.of(port), con.getLivePorts());

		assertEquals("http" + "://localhost:" + port + "/", dep.getUrl());
		assertEquals("http" + "://localhost:" + port + "/hello", img.getUrl());

		harness.assertLabelContains("/hello", img);

	}

	@Test
	public void instanceCount() throws Exception {
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("Previous container stopped and deployment started", BUILD_IMAGE_TIMEOUT, () -> {
			assertTrue(dep.getRunState() == RunState.RUNNING);
			assertEquals(1, dep.getDesiredInstances());
			assertEquals(-1, img.getDesiredInstances());
			assertEquals(-1, con.getDesiredInstances());
			assertEquals(1, dep.getActualInstances());
			assertEquals(1, img.getActualInstances());
			assertEquals(1, con.getActualInstances());
		});

		harness.selection.setElements(dep);
		restartAction().run();

		ACondition.waitFor("Second container to appear", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(2, img.getChildren().getValues().size());
		});

		String id = con.getName();

		GenericRemoteAppElement con2 = getChild(img, d -> d instanceof DockerContainer && !id.equals(d.getName()));

		ACondition.waitFor("Previous container stopped and deployment started", BUILD_IMAGE_TIMEOUT, () -> {
			assertTrue(con.getRunState() == RunState.INACTIVE);
			assertTrue(con2.getRunState() == RunState.RUNNING);
			// No diffs in project hence same image but another container started
			assertEquals(1, dep.getChildren().getValues().size());

			assertEquals(1, dep.getDesiredInstances());
			assertEquals(-1, img.getDesiredInstances());
			assertEquals(-1, con.getDesiredInstances());
			assertEquals(-1, con2.getDesiredInstances());
			assertEquals(1, dep.getActualInstances());
			assertEquals(1, img.getActualInstances());
			assertEquals(0, con.getActualInstances());
			assertEquals(1, con2.getActualInstances());

		});

		harness.assertInstancesLabel("1/1", "", dep);
		harness.assertInstancesLabel("1", "", img);
		harness.assertInstancesLabel("0", "", con);
		harness.assertInstancesLabel("1", "", con2);

		harness.selection.setElements(con);
		restartAction().run();

		ACondition.waitFor("Both containers running", BUILD_IMAGE_TIMEOUT, () -> {
			assertTrue(con.getRunState() == RunState.RUNNING);
			assertTrue(con2.getRunState() == RunState.RUNNING);
			// No diffs in project hence same image but another container started
			assertEquals(1, dep.getChildren().getValues().size());

			assertEquals(1, dep.getDesiredInstances());
			assertEquals(-1, img.getDesiredInstances());
			assertEquals(-1, con.getDesiredInstances());
			assertEquals(-1, con2.getDesiredInstances());
			assertEquals(2, dep.getActualInstances());
			assertEquals(2, img.getActualInstances());
			assertEquals(1, con.getActualInstances());
			assertEquals(1, con2.getActualInstances());

		});

		harness.assertInstancesLabel("2/1", "2/1", dep);
		harness.assertInstancesLabel("2", "2", img);
		harness.assertInstancesLabel("1", "", con);
		harness.assertInstancesLabel("1", "", con2);
	}

	@Test
	public void deleteDeployment() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		verifyNoMoreInteractions(ui());
		Mockito.reset(ui());

		String imageId = ((DockerImage)img.getAppData()).getName();

		DeleteElementsAction<?> deleteAction = actions().getDeleteAppsAction();
		assertTrue(harness.selection.getElements().isEmpty());
		assertFalse(deleteAction.isEnabled());
		harness.selection.setElements(dep);
		assertTrue(deleteAction.isEnabled());

		//if (ui().confirmOperation("Deleting Elements", modifiable.getDeletionConfirmationMessage(workitem.getValue()))) {

		RunTargetType rtt = model.getRunTarget().getType();
		Mockito.when(ui().confirmWithToggle(eq(DeleteElementsAction.PREF_SKIP_CONFIRM_DELETE(rtt)),
				// String title,
				eq("Deleting Elements"),
				// String message,
				anyString(),
				// String toggleMessage
				anyString()
		)).thenReturn(true);
		deleteAction.run();

		ACondition.waitFor("Everything is deleted", 5_000, () -> {
			assertTrue(model.getElements().getValues().isEmpty()); // deployment node disapear from model
			assertNoImage(imageId);

			assertTrue(
				client().listContainersCmd().withShowAll(true).exec()
				.isEmpty()
			);

//			client().listImages(ListImagesParam.allImages()).stream().
		});
//		verifyNoMoreInteractions(ui());
	}

	@Test
	public void deleteRunningImage() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		dragAndDrop(project, model);
		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		String imgId = img.getName();

		ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
			assertEquals(RunState.RUNNING, dep.getRunState());
			assertEquals(RunState.RUNNING, img.getRunState());
			assertEquals(RunState.RUNNING, con.getRunState());
		});

		DeleteElementsAction<?> delete = actions().getDeleteAppsAction();
		harness.selection.setElements(img);
		assertTrue(delete.isEnabled());
		assertTrue(delete.isVisible());

		Mockito.when(ui().confirmWithToggle(eq(DeleteElementsAction.PREF_SKIP_CONFIRM_DELETE(model.getRunTarget().getType())),
				// String title,
				eq("Deleting Elements"),
				// String message,
				anyString(),
				// String toggleMessage
				anyString()
		)).thenReturn(true);
		delete.run();

		ACondition.waitFor("Image and container deletion", 10_000, () -> {
			assertEquals(RunState.INACTIVE, dep.getRunState());
			assertTrue(dep.getChildren().getValues().isEmpty());
			assertTrue(img.isDisposed());
			assertTrue(con.isDisposed());

			assertNoImage(imgId);
			assertTrue(
					client().listContainersCmd().withShowAll(true).exec()
					.isEmpty()
			);
		});
	}

	@Test
	public void noAutoStartForMismatchingSession() throws Exception {
		GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model = createDockerTarget();
		Mockito.reset(ui());
		IProject project = projects.createBootWebProject("webby", bootVersionAtLeast("2.3.0"));

		dragAndDrop(project, model);
		{
			GenericRemoteAppElement dep = waitForDeployment(model, project);
			GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
			GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

			ACondition.waitFor("all started", BUILD_IMAGE_TIMEOUT, () -> {
				assertEquals(RunState.RUNNING, dep.getRunState());
				assertEquals(RunState.RUNNING, img.getRunState());
				assertEquals(RunState.RUNNING, con.getRunState());
			});

			verifyNoMoreInteractions(ui());
			Mockito.reset(ui());

			String sessionId = getSessionId(model);
			assertNotNull(sessionId);
			assertEquals(sessionId, getSessionId(dep));

			String containerId = con.getName();
			client().stopContainerCmd(containerId).withTimeout(2).exec();
			ACondition.waitFor("Container stopped", 5_000, () -> {
				InspectContainerResponse info = client().inspectContainerCmd(containerId).exec();
				String status = info.getState().getStatus();
				System.out.println("status = "+status);
				assertEquals("exited", status);
			});
		}

		model.disconnect();

		ACondition.waitFor("Docker app dispaeared", 2_000, () -> {
			assertTrue(model.getElements().getValues().isEmpty());
		});

		CompletableFuture<Void> deploymentSynchronized = RefreshStateTracker.waitForOperation("Synchronizing deployment "+project.getName());
		model.connect(ConnectMode.INTERACTIVE);

		GenericRemoteAppElement dep = waitForDeployment(model, project);
		GenericRemoteAppElement img = waitForChild(dep, d -> d instanceof DockerImage);
		GenericRemoteAppElement con = waitForChild(img, d -> d instanceof DockerContainer);

		deploymentSynchronized.get(1, TimeUnit.SECONDS);

		assertEquals(RunState.INACTIVE, dep.getRunState());
		assertEquals(RunState.INACTIVE, img.getRunState());
		assertEquals(RunState.INACTIVE, con.getRunState());
	}

	@Test
	public void canceledDockerTargetCreation() throws Exception {
		DockerRunTargetType target = injections().getBean(DockerRunTargetType.class);
		AddRunTargetAction createTarget = getCreateTargetAction(target);
		assertNotNull(createTarget);
		assertTrue(createTarget.isEnabled());

		doAnswer(invocation -> {
			Model model = (Model) invocation.getArguments()[0];
			//model.performOk(); //no clicking ok, so that's like when dialog is 'canceled'.
			return null;
		}).when(ui()).selectDockerDaemonDialog(any());

		createTarget.run();
		createTarget.waitFor(Duration.ofMillis(2000));

		assertTrue(harness.getRunTargetModels(target).isEmpty());
	}

	//////////////////////////////////////////////
	/// harness

	@After
	public void cleanup() {
		RefreshStateTracker.clearDebugObservers();
	}

	private void assertNoLaunchConfigs(String typeId) throws CoreException {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = lm.getLaunchConfigurationType(typeId);
		ILaunchConfiguration[] confs = lm.getLaunchConfigurations(type);
		assertTrue(confs == null || confs.length == 0);
	}

	private void assertNoActiveDevtoolsClientLaunch(GenericRemoteAppElement el) throws CoreException {
		List<ILaunch> launches = getDevtoolsClientLaunches(el);
		assertTrue(launches.isEmpty());
	}

	private ILaunch assertActiveDevtoolsClientLaunch(GenericRemoteAppElement el) throws CoreException {
		List<ILaunch> launches = getDevtoolsClientLaunches(el);
		assertEquals(1, launches.size());
		return launches.get(0);
	}

	private List<ILaunch> getDevtoolsClientLaunches(GenericRemoteAppElement el) throws CoreException {
		List<ILaunch> launches = new ArrayList<>();
		for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			if (!l.isTerminated()) {
				ILaunchConfiguration conf = l.getLaunchConfiguration();
				if (conf.getType().getIdentifier().equals(BootDevtoolsClientLaunchConfigurationDelegate.TYPE_ID)) {
					String url = DevtoolsUtil.remoteUrl(el);
					assertTrue(url.startsWith("http"));
					if (url.equals(conf.getAttribute(BootDevtoolsClientLaunchConfigurationDelegate.REMOTE_URL, ""))) {
						launches.add(l);
					}
				}
			}
		}
		return launches;
	}

	private ILaunch assertActiveDebugLaunch(GenericRemoteAppElement el) throws CoreException {
		DockerContainer container = (DockerContainer)el.getAppData();
		String containerId = container.getName();
		List<ILaunch> launches = new ArrayList<>();
		for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			if (!l.isTerminated()) {
				ILaunchConfiguration conf = l.getLaunchConfiguration();
				if (conf != null && containerId.equals(conf.getAttribute(RemoteJavaLaunchUtil.APP_NAME, ""))) {
					launches.add(l);
				}
			}
		}
		assertEquals(1, launches.size());
		return launches.get(0);
	}

	private String getSessionId(GenericRemoteAppElement dep) {
		App data = dep.getAppData();
		assertTrue(data instanceof DockerApp);
		return ((DockerApp)data).deployment().getSessionId();
	}

	private String getSessionId(RemoteBootDashModel model) {
		DockerRunTarget target = (DockerRunTarget) model.getRunTarget();
		return target.getSessionId();
	}

	private GenericRemoteBootDashModel<DockerClient, DockerTargetParams> createDockerTarget() throws Exception {
		DockerRunTargetType target = injections().getBean(DockerRunTargetType.class);
		AddRunTargetAction createTarget = getCreateTargetAction(target);
		assertNotNull(createTarget);
		assertTrue(createTarget.isEnabled());

		doAnswer(invocation -> {
			Model model = (Model) invocation.getArguments()[0];
			model.performOk();
			return null;
		}).when(ui()).selectDockerDaemonDialog(Matchers.any());

		createTarget.run();
		createTarget.waitFor(Duration.ofMillis(5000));

		BootDashModel model;
			//		ACondition.waitFor("Run target model to appear", 2000, () -> {
			assertNotNull(model = harness.getRunTargetModel(target));
//		});
		return (GenericRemoteBootDashModel<DockerClient, DockerTargetParams>) model;
	}

	private void assertNoImage(String imageId) throws Exception {
		for (Image img : client().listImagesCmd().withShowAll(true).exec()) {
			assertFalse(imageId.equals(img.getId()));
		}
	}

	private GenericRemoteAppElement waitForChild(GenericRemoteAppElement dep, Predicate<App> selector) throws Exception {
		ACondition.waitFor("node to appear", 120_000, () -> {
			getChild(dep, selector);
		});
		return getChild(dep, selector);
	}

	private GenericRemoteAppElement getChild(GenericRemoteAppElement node, Predicate<App> selector) throws Exception {
		List<GenericRemoteAppElement> selected = new ArrayList<>();
		for (BootDashElement _child : node.getChildren().getValues()) {
			if (_child instanceof GenericRemoteAppElement) {
				GenericRemoteAppElement child = (GenericRemoteAppElement) _child;
				App data = child.getAppData();
				if (selector.test(data)) {
					selected.add(child);
				}
			}
		}
		assertEquals(1, selected.size());
		return selected.get(0);
	}

	private GenericRemoteAppElement waitForDeployment(RemoteBootDashModel model,
			IProject project) throws Exception {
		ACondition.waitFor("project deployment node", 2_000, () -> {
			getDeployment(model, project);
		});
		GenericRemoteAppElement d = getDeployment(model, project);
		return d;
	}

	DockerClient _client;

	private DockerClient client() {
		if (_client == null) {
			_client = DockerRunTargetType.createDockerClient(DEFAULT_DOCKER_URL);
		}
		return _client;
	}

	@After
	public void tearDown() throws Exception {
		try {
			List<Container> cons = client().listContainersCmd()
					.withShowAll(true)
					.withLabelFilter(ImmutableList.of(DockerApp.APP_NAME))
					.exec();
			//Delete all 'our' containers
			for (Container c : cons) {
				String label = c.getLabels().getOrDefault(DockerApp.APP_NAME, "");
				assertTrue(StringUtil.hasText(label));
				System.out.println("removing container: "+c.getId());
				client().removeContainerCmd(c.getId()).withForce(true).withRemoveVolumes(true).exec();
			}
			//Delete all dangling images
			for (Image img : client().listImagesCmd().withDanglingFilter(true).exec()) {
				System.out.println("removing image: "+img.getId());
				client().removeImageCmd(img.getId()).withForce(true).withNoPrune(false).exec();
			}
		} finally {
			if (_client != null) {
				_client.close();
			}
		}
	}

	private GenericRemoteAppElement getDeployment(BootDashModel model, IProject project) {
		for (BootDashElement e : model.getElements().getValues()) {
			if (project.equals(e.getProject())) {
				return (GenericRemoteAppElement) e;
			}
		}
		throw new NoSuchElementException("No element for project "+project.getName());
	}


	private void dragAndDrop(IProject project, GenericRemoteBootDashModel<DockerClient, DockerTargetParams> model) throws Exception {
		assertTrue(model.canBeAdded(ImmutableList.of(project)));
		model.add(ImmutableList.<Object>of(project));
	}

	private TestBootDashModelContext context = new TestBootDashModelContext(
			ResourcesPlugin.getWorkspace(),
			DebugPlugin.getDefault().getLaunchManager()
	);
	{
		context.injections.def(DockerRunTargetType.class, DockerRunTargetType::new);
		context.injections.defInstance(RunTargetType.class, RunTargetTypes.LOCAL);
	}
	BootDashViewModelHarness harness = new BootDashViewModelHarness(context);
	BootProjectTestHarness projects = new BootProjectTestHarness(ResourcesPlugin.getWorkspace());
	BootDashActions actions;

	private BootDashActions actions() {
		if (actions == null) {
			actions = new BootDashActions(harness.model, harness.selection.forReading(), injections(), null);
		}
		return actions;
	}

	private SimpleDIContext injections() {
		return context.injections;
	}

	private AllUserInteractions ui() {
		return injections().getBean(AllUserInteractions.class);
	}

	private AddRunTargetAction getCreateTargetAction(RunTargetType<?> type) {
		for (AddRunTargetAction a : actions().getAddRunTargetActions()) {
			if (a.runTargetType.equals(type)) {
				return a;
			}
		}
		throw new NoSuchElementException("Add target action not found for "+type);
	}


	private DeployToRemoteTargetAction debugOnDockerAction() {
		ImmutableList<IAction> as = actions().getDebugOnTargetActions();
		for (IAction a : as) {
			if (a instanceof DeployToRemoteTargetAction) {
				DeployToRemoteTargetAction deployAction = (DeployToRemoteTargetAction) a;
				RemoteRunTarget target = ((DeployToRemoteTargetAction) a).getTarget();
				if (DockerRunTargetType.class == target.getType().getClass()) {
					return deployAction;
				}
			}
		}
		throw new NoSuchElementException("Debug On Docker Target Action not found");
	}

	private RunStateAction runstateAction(RunState state, String expectedLabel) {
		for (RunStateAction a : actions().getRunStateActions()) {
			if (a.getGoalState() == state) {
				assertEquals(expectedLabel, a.getText());
				return a;
			}
		}
		fail("Cannot find runstate action for "+state);
		return null;
	}

	private RunStateAction stopAction() {
		return runstateAction(RunState.INACTIVE, "Stop");
	}

	private RunStateAction restartAction() {
		return runstateAction(RunState.RUNNING, "(Re)start");
	}

	private void setTags(Taggable e, String... tags) {
		LinkedHashSet<String> tagSet = new LinkedHashSet<>();
		for (String tag : tags) {
			tagSet.add(tag);
		}
		e.setTags(tagSet);
	}

	private void assertTags(Taggable e, String... tags) {
		LinkedHashSet<String> tagSet = new LinkedHashSet<>();
		for (String tag : tags) {
			tagSet.add(tag);
		}
		assertEquals(tagSet, e.getTags());
	}
}
