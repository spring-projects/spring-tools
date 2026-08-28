/*******************************************************************************
 * Copyright (c) 2018, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.tooling.jdt.ls.commons.test.TestUtils.deleteAllProjects;
import static org.springframework.tooling.jdt.ls.commons.test.TestUtils.safe;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.tooling.jdt.ls.commons.Logger.TestLogger;
import org.springframework.tooling.jdt.ls.commons.classpath.ClasspathUtil;
import org.springframework.tooling.jdt.ls.commons.classpath.ClientCommandExecutor;
import org.springframework.tooling.jdt.ls.commons.classpath.ReusableClasspathListenerHandler;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;

import com.google.common.collect.ImmutableList;

import junit.framework.AssertionFailedError;

public class ClasspathListenerHandlerTest {

	private TestLogger logger = new TestLogger();
	private MockClientCommandExecutor commandExecutor = new MockClientCommandExecutor();
	private MockClasspathCache classpaths = new MockClasspathCache();
	private ReusableClasspathListenerHandler service = new ReusableClasspathListenerHandler(logger, commandExecutor);

	@Test public void classpathIsSentForExistingProject() throws Exception {
		String projectName = "classpath-test-simple-java-project";
		IProject project = createTestProject(projectName);
		File loc = project.getLocation().toFile();

		service.addClasspathListener(classpaths.commandId, false);
		ACondition.waitFor("Project with classpath to appear", Duration.ofSeconds(50), () -> {
			Classpath cp = classpaths.getFor(loc).classpath;
			for (CPE cpe : cp.getEntries()) {
				assertTrue(new File(cpe.getPath()).isAbsolute());
			}
			assertTrue(cp.getEntries().stream().filter(cpe -> Classpath.isSource(cpe)).count() == 1); //has 1 source entry
			assertClasspath(cp, cp.getEntries().stream().filter(cpe -> Classpath.isBinary(cpe) && cpe.isSystem()).count() >= 1); //has some system libraries
		});
	}


	@Ignore //TODO: fails randomly for unknown reason.
	@Test public void classpathIsSentForNewProject_and_removedForDeletedProject() throws Exception {
		service.addClasspathListener(classpaths.commandId, false);

		String projectName = "classpath-test-simple-java-project";
		IProject project = createTestProject(projectName);
		File loc = project.getLocation().toFile();
		ACondition.waitFor("Project with classpath to appear", Duration.ofSeconds(5), () -> {
			Classpath cp = classpaths.getFor(loc).classpath;
			for (CPE cpe : cp.getEntries()) {
				assertTrue(new File(cpe.getPath()).isAbsolute());
			}
			assertTrue(cp.getEntries().stream().filter(cpe -> Classpath.isSource(cpe)).count() == 1); //has 1 source entry
			assertClasspath(cp, cp.getEntries().stream().filter(cpe -> Classpath.isBinary(cpe) && cpe.isSystem()).count() >= 1); //has some system libraries
		});
		logger.log("=== Deleteing project");
		project.delete(false, true, null);
		ACondition.waitFor("Project classpath to disapear", Duration.ofSeconds(5), () -> {
			Info cp = classpaths.getFor(loc);
			assertNull(cp);
		});
	}

	@Ignore //TODO: fails randomly for unknown reason.
	@Test public void classpathIsRemovedWhenProjectDeletedFromFileSystem_twoSubscribers() throws Exception {
		MockClasspathCache secondListener = new MockClasspathCache();
		try {
			String projectName = "classpath-test-simple-java-project";
			IProject project = createTestProject(projectName);
			File loc = project.getLocation().toFile();

			service.addClasspathListener(classpaths.commandId, false);
			ACondition.waitFor("Project with classpath to appear", Duration.ofSeconds(5), () -> {
				Classpath cp = classpaths.getFor(loc).classpath;
				for (CPE cpe : cp.getEntries()) {
					assertTrue(new File(cpe.getPath()).isAbsolute());
				}
				assertTrue(cp.getEntries().stream().filter(cpe -> Classpath.isSource(cpe)).count() == 1); //has 1 source entry
				assertClasspath(cp, cp.getEntries().stream().filter(cpe -> Classpath.isBinary(cpe) && cpe.isSystem()).count() >= 1); //has some system libraries
			});

			FileUtils.deleteQuietly(loc);
			safe(() -> project.refreshLocal(IResource.DEPTH_INFINITE, null));

			ACondition.waitFor("Project to disapear", Duration.ofSeconds(5), () -> {
				{
					Info cp = classpaths.getFor(loc);
					assertNull(cp);
				}
				{
					Info cp = secondListener.getFor(loc);
					assertNull(cp);
				}
			});
		} finally {
			secondListener.dispose();
		}
	}

	@Ignore //TODO: fails randomly for unknown reason.
	@Test public void classpathIsRemovedWhenProjectDeletedFromFileSystem() throws Exception {
		String projectName = "classpath-test-simple-java-project";
		IProject project = createTestProject(projectName);
		File loc = project.getLocation().toFile();

		service.addClasspathListener(classpaths.commandId, false);
		ACondition.waitFor("Project with classpath to appear", Duration.ofSeconds(5), () -> {
			Classpath cp = classpaths.getFor(loc).classpath;
			for (CPE cpe : cp.getEntries()) {
				assertTrue(new File(cpe.getPath()).isAbsolute());
			}
			assertTrue(cp.getEntries().stream().filter(cpe -> Classpath.isSource(cpe)).count() == 1); //has 1 source entry
			assertClasspath(cp, cp.getEntries().stream().filter(cpe -> Classpath.isBinary(cpe) && cpe.isSystem()).count() >= 1); //has some system libraries
		});

		FileUtils.deleteQuietly(loc);
		safe(() -> project.refreshLocal(IResource.DEPTH_INFINITE, null));

		ACondition.waitFor("Project to disapear", Duration.ofSeconds(5), () -> {
			Info cp = classpaths.getFor(loc);
			assertNull(cp);
		});
	}

	@Ignore //TODO: fails randomly for unknown reason.
	@Test public void sourceJar() throws Exception {
		String projectName = "maven-with-jar-dependency";
		ClasspathUtil.enableDownloadSources();
		IProject project = createTestProject(projectName);
		File loc = project.getLocation().toFile();

		service.addClasspathListener(classpaths.commandId, false);
		ACondition.waitFor("Project with classpath to appear", Duration.ofSeconds(100), () -> {
			Classpath cp = classpaths.getFor(loc).classpath;
			for (CPE cpe : cp.getEntries()) {
				assertTrue(new File(cpe.getPath()).isAbsolute());
			}
			assertClasspath(cp, cp.getEntries().stream().filter(cpe -> Classpath.isSource(cpe)).count() >= 1); //has source entries
			CPE dependency = cp.getEntries().stream()
				.filter(Classpath::isBinary)
				.filter(cpe -> new File(cpe.getPath()).getName().startsWith("commons-io"))
				.findFirst().orElse(null);
			assertClasspath(cp, dependency != null);
			assertTrue(new File(dependency.getSourceContainerUrl().toURI()).exists());
		});
	}

	///////////// harness stuff below ///////////////////////////////////////////////

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	static class Info {
		public final String name;
		public final Classpath classpath;

		private Info(String name, Classpath cp) {
			super();
			this.name = name;
			this.classpath = cp;
		}
	}

	public class MockClientCommandExecutor implements ClientCommandExecutor {

		private Map<String,ClientCommandExecutor> handlers = new HashMap<>();

		@Override
		public synchronized Object executeClientCommand(String id, Object... params) throws Exception {
			ClientCommandExecutor handler = handlers.get(id);
			if (handler != null) {
				return handler.executeClientCommand(id, params);
			}
			throw new IllegalStateException("No handler for "+id);
		}

		public void addHandler(String id, ClientCommandExecutor handler) {
			Assert.assertFalse(handlers.containsKey(id));
			handlers.put(id, handler);
		}

		public void removeHandler(String commandId) {
			handlers.remove(commandId);
		}
	}

	public class MockClasspathCache implements ClientCommandExecutor {

		String commandId = RandomStringUtils.randomAlphabetic(8);

		Map<File, Info> classpaths = new HashMap<>();

		{
			commandExecutor.addHandler(commandId, this);
		}

		@Override
		public synchronized Object executeClientCommand(String id, Object... _params) throws Exception {
			if (id.equals(commandId)) {
				ImmutableList<Object> params = ImmutableList.copyOf(_params);
				System.out.println("received: "+Arrays.asList(params));
				File projectLoc = new File(new URI((String) params.get(0)));
				String name = (String) params.get(1);
				boolean deleted = (boolean) params.get(2);
				if (deleted) {
					System.out.println("DELETING "+name);
					classpaths.remove(projectLoc);
				} else {
					Classpath cp = (Classpath) params.get(3);
					System.out.println("PUT "+name+" "+cp.getEntries().size()+" entries");
					classpaths.put(projectLoc, new Info(name, cp));
				}
			}
			return "whatever";
		}

		public synchronized Info getFor(File location) {
			return classpaths.get(location);
		}

		public void dispose() throws Exception {
			service.removeClasspathListener(commandId);
			commandExecutor.removeHandler(commandId);
		}

	}

	@After
	public void tearDown() throws Exception {
		classpaths.dispose();
		deleteAllProjects();
		assertTrue(service.hasNoActiveSubscriptions());
		logger.assertNoErrors();
	}

	private static void assertClasspath(Classpath cp, boolean b) {
		if (!b) {
			StringBuilder buf = new StringBuilder();
			for (CPE cpe : cp.getEntries()) {
				buf.append("\n   ");
				buf.append(cpe);
			}
			throw new AssertionFailedError("Unexpected classpath:" + buf);
		}
	}

	private IProject createTestProject(String name) throws Exception {
		return TestUtils.createTestProject(name, tmp);
	}


}
