/*******************************************************************************
 * Copyright (c) 2015, 2022 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.test;

import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withImportStrategy;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.livebean.JmxBeanSupport;
import org.springframework.ide.eclipse.boot.launch.process.BootProcessFactory;
import org.springframework.ide.eclipse.boot.test.util.LaunchResult;
import org.springframework.ide.eclipse.boot.test.util.LaunchUtil;
import org.springsource.ide.eclipse.commons.frameworks.test.util.Timewatch;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

/**
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class BootLaunchConfigurationDelegateTest extends BootLaunchTestCase {

	private static final String TEST_MAIN_CLASS = "demo.DumpInfoApplication";
	private static final String TEST_PROJECT = "dump-info";

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		//The following disables some nasty popups that can cause test to hang rather than fail.
		//when project has errors upon launching it.
		InstanceScope.INSTANCE.getNode(DebugPlugin.getUniqueIdentifier())
			.putBoolean(IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, false);
		BootActivator.getDefault().getPreferenceStore().setToDefault(BootPreferences.PREF_BOOT_FAST_STARTUP_JVM_ARGS);
		BootActivator.getDefault().getPreferenceStore().setToDefault(BootPreferences.PREF_BOOT_FAST_STARTUP_DEFAULT);
	}

	@SafeVarargs
	private static final Properties buildPropewrties(Pair<String, String>... pvs) {
		Properties props = new Properties();
		for (Pair<String, String> pv : pvs) {
			props.setProperty(pv.getKey(), pv.getValue());
		}
		return props;
	}

	public void testGetSetProperties() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals("", BootLaunchConfigurationDelegate.getRawApplicationProperties(wc));

		BootLaunchConfigurationDelegate.setRawApplicationProperties(wc, null); //accepts null in lieu of empty list,
		assertEquals("", BootLaunchConfigurationDelegate.getRawApplicationProperties(wc));

		//store one single property
		doGetAndSetProps(wc,
				"foo=Hello",
				buildPropewrties(Pair.of("foo", "Hello"))
		);

		//store empty property list
		doGetAndSetProps(wc,
				"",
				buildPropewrties()
		);

		//store a few properties
		doGetAndSetProps(wc,
				"foo.bar: snuffer.nazz\n" +
				"#neala: nolo\n" +
				"#Hohoh: Santa Claus",
				buildPropewrties(Pair.of("foo.bar", "snuffer.nazz"))
		);

		//store properties with identical keys
		doGetAndSetProps(wc,
				"foo=snuffer.nazz\n" +
				"#foo=nolo\n" +
				"#bar=Santa Claus\n" +
				"#bar=Santkkk ",
				buildPropewrties(Pair.of("foo", "snuffer.nazz"))
		);
	}

	private ILaunchConfigurationWorkingCopy createWorkingCopy() throws CoreException {
		return createWorkingCopy(BootLaunchConfigurationDelegate.TYPE_ID);
	}

	private void doGetAndSetProps(ILaunchConfigurationWorkingCopy wc, String props, Properties expected) {
		BootLaunchConfigurationDelegate.setRawApplicationProperties(wc, props);
		Properties retrieved = BootLaunchConfigurationDelegate.getApplicationProperties(wc);
		assertEquals(expected, retrieved);
	}

	public void testSetGetProject() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals(null, BootLaunchConfigurationDelegate.getProject(wc));
		IProject project = getProject("foo");
		BootLaunchConfigurationDelegate.setProject(wc, project);
		assertEquals(project, BootLaunchConfigurationDelegate.getProject(wc));
	}

	public void testSetGetProfile() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals("", BootLaunchConfigurationDelegate.getProfile(wc));

		BootLaunchConfigurationDelegate.setProfile(wc, "deployment");
		assertEquals("deployment", BootLaunchConfigurationDelegate.getProfile(wc));

		BootLaunchConfigurationDelegate.setProfile(wc, null);
		assertEquals("", BootLaunchConfigurationDelegate.getProfile(wc));
	}

	public void testSetGetAnsiConsoleOutput() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		boolean ideSupportsAnsiConsoleOutput = BootLaunchConfigurationDelegate.supportsAnsiConsoleOutput();
		assertEquals(ideSupportsAnsiConsoleOutput, BootLaunchConfigurationDelegate.getEnableAnsiConsoleOutput(wc));

		BootLaunchConfigurationDelegate.setEnableAnsiConsoleOutput(wc, true);
		assertEquals(true, BootLaunchConfigurationDelegate.getEnableAnsiConsoleOutput(wc));

		BootLaunchConfigurationDelegate.setEnableAnsiConsoleOutput(wc, false);
		assertEquals(false, BootLaunchConfigurationDelegate.getEnableAnsiConsoleOutput(wc));
	}

	public void testSetGetFastStartup() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals(
				BootActivator.getDefault().getPreferenceStore()
						.getBoolean(BootPreferences.PREF_BOOT_FAST_STARTUP_DEFAULT),
				BootLaunchConfigurationDelegate.getFastStartup(wc));

		BootLaunchConfigurationDelegate.setFastStartup(wc, true);
		assertEquals(true, BootLaunchConfigurationDelegate.getFastStartup(wc));

		BootLaunchConfigurationDelegate.setFastStartup(wc, false);
		assertEquals(false, BootLaunchConfigurationDelegate.getFastStartup(wc));
	}

	public void testSetGetAutoConnect() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals(true, BootLaunchConfigurationDelegate.getAutoConnect(wc));

		BootLaunchConfigurationDelegate.setAutoConnect(wc, false);
		assertEquals(false, BootLaunchConfigurationDelegate.getAutoConnect(wc));

		IProject project = createLaunchReadyProject(TEST_PROJECT);

		//Creates a launch conf similar to that created by 'Run As' menu.
		wc = createWorkingCopy();
		BootLaunchConfigurationDelegate.setDefaults(wc, project, TEST_MAIN_CLASS);
		assertEquals(true, BootLaunchConfigurationDelegate.getAutoConnect(wc));
	}

	public void testClearProperties() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		BootLaunchConfigurationDelegate.setRawApplicationProperties(wc,
				"some=thing\n" +
				"#some.other=thing"
		);
		assertFalse(BootLaunchConfigurationDelegate.getApplicationProperties(wc).isEmpty());
		BootLaunchConfigurationDelegate.clearProperties(wc);
		assertTrue(BootLaunchConfigurationDelegate.getApplicationProperties(wc).isEmpty());
	}

	public void testGetSetDebug() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		boolean deflt = BootLaunchConfigurationDelegate.DEFAULT_ENABLE_DEBUG_OUTPUT;
		boolean other = !deflt;
		assertEquals(deflt,
				BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));

		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, other);
		assertEquals(other, BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));

		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, deflt);
		assertEquals(deflt, BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));
	}

	public void testGetSetLiveBean() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		boolean deflt = BootLaunchConfigurationDelegate.DEFAULT_ENABLE_LIVE_BEAN_SUPPORT();
		boolean other = !deflt;

		assertEquals(deflt, BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));

		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, other);
		assertEquals(other, BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));

		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, deflt);
		assertEquals(deflt, BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));
	}

	public void testGetSetJMXPort() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals("", BootLaunchConfigurationDelegate.getJMXPort(wc));

		BootLaunchConfigurationDelegate.setJMXPort(wc, "something");
		assertEquals("something", BootLaunchConfigurationDelegate.getJMXPort(wc));
	}

	public void testRunAsLaunch() throws Exception {
		IProject project = createLaunchReadyProject(TEST_PROJECT);

		//Creates a launch conf similar to that created by 'Run As' menu.
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		BootLaunchConfigurationDelegate.setDefaults(wc, project, TEST_MAIN_CLASS);

		LaunchResult result = LaunchUtil.synchLaunch(wc);
		System.out.println(result); //Great help in debugging this :-)
		assertContains(":: Spring Boot ::", result.out);
		assertOk(result);
	}

	public void testLaunchAllOptsDisable() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();
		LaunchResult result = LaunchUtil.synchLaunch(wc);
		assertContains(":: Spring Boot ::", result.out);
		assertOk(result);
	}

	public void testLaunchWithDebugOutput() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();

		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, true);

		LaunchResult result = LaunchUtil.synchLaunch(wc);

		assertContains(":: Spring Boot ::", result.out);
		assertContains("AUTO-CONFIGURATION REPORT", result.out);
		assertOk(result);
	}

	public void testLaunchWithLiveBeans() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();

		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, true);
		int port = JmxBeanSupport.randomPort();
		BootLaunchConfigurationDelegate.setJMXPort(wc, ""+port);

		LaunchResult result = LaunchUtil.synchLaunch(wc);

		System.out.println(result);

		assertContains(":: Spring Boot ::", result.out);
		//The following check doesn't real prove the live bean graph works, but at least it shows the VM args are
		//taking effect.
		assertContains("com.sun.management.jmxremote.port='"+port+"'", result.out);
		assertOk(result);
	}

	public void testLaunchWithProperties() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();

		BootLaunchConfigurationDelegate.setRawApplicationProperties(wc,
				"foo=foo is enabled\n" +
				"#bar=bar is not enabled\n" +
				"zor=zor enabled\n" +
				"#zor=zor disabled"
		);

		int jmxPort = JmxBeanSupport.randomPort(); // must set or it will be generated randomly
												   // and then we can't make the 'assert' below pass easily.
		BootLaunchConfigurationDelegate.setJMXPort(wc, ""+jmxPort);

		LaunchResult result = LaunchUtil.synchLaunch(wc);

		assertContains(":: Spring Boot ::", result.out);
		assertPropertyDump(result.out,
				"debug=null\n" +
				"zor='zor enabled'\n" +
				"foo='foo is enabled'\n" +
				"bar=null\n" +
				"com.sun.management.jmxremote.port='"+jmxPort+"'"
		);
		assertOk(result);
	}

	public void testLaunchWithEmptyProperties() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();

		BootLaunchConfigurationDelegate.setRawApplicationProperties(wc,
				"foo=\n" +
				"#bar=bar is not enabled\n"
		);

		int jmxPort = JmxBeanSupport.randomPort(); // must set or it will be generated randomly
												   // and then we can't make the 'assert' below pass easily.
		BootLaunchConfigurationDelegate.setJMXPort(wc, ""+jmxPort);

		LaunchResult result = LaunchUtil.synchLaunch(wc);

		assertContains(":: Spring Boot ::", result.out);
		assertPropertyDump(result.out,
				"debug=null\n" +
				"zor=null\n" +
				"foo=''\n" +
				"bar=null\n" +
				"com.sun.management.jmxremote.port='"+jmxPort+"'"
		);
		assertOk(result);
	}


	public void testLaunchWithProfile() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();

		BootLaunchConfigurationDelegate.setProfile(wc, "special");

		LaunchResult result = LaunchUtil.synchLaunch(wc);

		assertContains(":: Spring Boot ::", result.out);
		assertContains("foo='special foo'", result.out);
		assertOk(result);
	}

	public void testLaunchWithThinWrapper() throws Exception {
		BootProcessFactory.ENABLE_OUTPUT_DUMPING = true;
		try {
			URL thinWrapperUrl = new URL("https://repo1.maven.org/maven2/org/springframework/boot/experimental/spring-boot-thin-wrapper/1.0.9.RELEASE/spring-boot-thin-wrapper-1.0.9.RELEASE.jar");
			File thinWrapper = File.createTempFile("thin-wrapper", ".jar");
			try {
				FileUtils.copyURLToFile(thinWrapperUrl, thinWrapper);
				BootPreferences.getInstance().setThinWrapper(thinWrapper);

				doThinWrapperLaunchTest(thinWrapper, "MAVEN");
				//Not working: doThinWrapperLaunchTest(thinWrapper, "GRADLE");

			} finally {
				FileUtils.deleteQuietly(thinWrapper);
				BootPreferences.getInstance().setThinWrapper(null);
			}
		} finally {
			BootProcessFactory.ENABLE_OUTPUT_DUMPING = false;
		}
	}

	private void doThinWrapperLaunchTest(File thinWrapper, String importStrategy) throws Exception {
		System.out.println(">>> doThinWrapperLaunchTest: "+importStrategy);
		try {
			boolean reallyDoThinLaunch = true;
			Timewatch.monitor("Thin launch with "+importStrategy, Duration.ofMinutes(5), () -> {
				String buildType = importStrategy.split("\\-")[0].toLowerCase();
				IProject project = projects.createBootProject("thinly-wrapped-"+buildType, withImportStrategy(importStrategy));
				ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy(project.getName(), "com.example.demo.ThinlyWrapped"+ StringUtils.capitalize(buildType) +"Application");

				createFile(project, "src/main/java/com/example/demo/ShowMessage.java",
						"package com.example.demo;\n" +
						"\n" +
						"import org.springframework.boot.CommandLineRunner;\n" +
						"import org.springframework.stereotype.Component;\n" +
						"\n" +
						"@Component\n" +
						"public class ShowMessage implements CommandLineRunner {\n" +
						"\n" +
						"	@Override\n" +
						"	public void run(String... arg0) throws Exception {\n" +
						"		System.out.println(\"We have liftoff!\");\n" +
						"	}\n" +
						"\n" +
						"}\n"
				);
				StsTestUtil.assertNoErrors(project); // compile project (and check for errors)
				if (buildType.equals("maven")) {
					//In gradle its different location, but doens't really matter, this just a 'sanity' check to
					// see if class got compiled. If not, something else will fail later.
					assertTrue(project.getFile("target/classes/com/example/demo/ShowMessage.class").exists());
				}

				if (reallyDoThinLaunch) {
					BootLaunchConfigurationDelegate.setUseThinWrapper(wc, true);
					String[] classpath = getClasspath(new BootLaunchConfigurationDelegate(), wc);
					assertTrue(classpath.length == 1);
					assertEquals(thinWrapper.getAbsolutePath(), classpath[0]);
				}

				LaunchResult result = LaunchUtil.synchLaunch(wc);
				System.out.println(result);
				assertContains("We have liftoff!", result.out);
			});
		} finally {
			System.out.println("<<< doThinWrapperLaunchTest: "+importStrategy);
		}
	}

	/**
	 * Name fragments that, when seen in a dependency, probably mean the dependency is a test dependency
	 */
	private static final String[] testFragments = {
			"test",
			"junit",
			"assertj",
			"mockito",
	};

	private static boolean isTestClasspathEntry(String cpe) {
		for (String testFrag : testFragments) {
			String normalizedEntry = cpe.replace('\\', '/');
			int slash = normalizedEntry.lastIndexOf('/');
			if (slash >= 0) {
				normalizedEntry = normalizedEntry.substring(slash);
			}
			if (normalizedEntry.contains(testFrag)) {
				return true;
			}
		}
		return false;
	}


	public void testRuntimeClasspathNoTestStuffGradle() throws Exception {
		IProject project = projects.createBootProject("gradle-test-project", withImportStrategy("GRADLE"));
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy(project.getName(), "com.example.demo.GradleTestProjectApplication");
		String[] cp = getClasspath(new BootLaunchConfigurationDelegate(), wc);

		ArrayList<String> testDependencies = new ArrayList<>();
		for (String cpe : cp) {
			if (isTestClasspathEntry(cpe)) {
				testDependencies.add(cpe);
			}
		}
		if (!testDependencies.isEmpty()) {
			fail("Shouldn't have test dependencies but found: "+testDependencies);
		}
	}

	public void testRuntimeClasspathNoTestStuff() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();
		String[] cp = getClasspath(new BootLaunchConfigurationDelegate(), wc);
		assertClasspath(cp,
				//ignore:
				(s) -> s.contains("/jre/lib/") || s.contains("/Java/Extensions/"),
				//expect:
				"target/classes",
				"spring-boot-starter-1.2.1.RELEASE.jar",
				"spring-boot-1.2.1.RELEASE.jar",
				"spring-context-4.1.4.RELEASE.jar",
				"spring-aop-4.1.4.RELEASE.jar",
				"aopalliance-1.0.jar",
				"spring-beans-4.1.4.RELEASE.jar",
				"spring-expression-4.1.4.RELEASE.jar",
				"spring-boot-autoconfigure-1.2.1.RELEASE.jar",
				"spring-boot-starter-logging-1.2.1.RELEASE.jar",
				"jcl-over-slf4j-1.7.8.jar",
				"slf4j-api-1.7.8.jar",
				"jul-to-slf4j-1.7.8.jar",
				"log4j-over-slf4j-1.7.8.jar",
				"logback-classic-1.1.2.jar",
				"logback-core-1.1.2.jar",
				"spring-core-4.1.4.RELEASE.jar",
				"snakeyaml-1.14.jar"
		);
	}

	private static void assertClasspath(String[] _cp, Predicate<String> ignoring, String... expected) {
		List<String> cpList = new ArrayList<String>(_cp.length);
		for (int i = 0; i < _cp.length; i++) {
			String normalizedEntry = _cp[i].replace('\\', '/');
			if (!ignoring.test(normalizedEntry)) {
				cpList.add(normalizedEntry);
			}
		}
		String[] cp = cpList.toArray(new String[cpList.size()]);
		for (String e : expected) {
			assertClasspathHasEntry(cp, e);
		}
		for (String e : cp) {
			assertClasspathEntryExpected(e, expected);
		}

	}

	private static void assertClasspathEntryExpected(String e, String[] expected) {
		for (String expect : expected) {
			if (e.endsWith(expect)) {
				return;
			}
		}
		fail("Unexpected classpath entry: "+e);
	}

	private static  void assertClasspathHasEntry(String[] cp, String expect) {
		StringBuilder found = new StringBuilder();
		for (String actual : cp) {
			found.append(actual+"\n");
			if (actual.endsWith(expect)) {
				return;
			}
		}
		fail("Missing classpath entry: "+expect+"\n"
				+ "found: "+found);
	}

	private String[] getClasspath(JavaLaunchDelegate delegate,
			ILaunchConfigurationWorkingCopy wc) throws CoreException {
		System.out.println("\n====classpath according to "+delegate.getClass().getSimpleName());
		String[][] cpAndMp = delegate.getClasspathAndModulepath(wc);
		String[] classpath = cpAndMp[0];
		for (String element : classpath) {
			int chop = element.lastIndexOf('/');
			System.out.println('"'+element.substring(chop+1)+"\",");
		}
		return classpath;
	}

	private void assertPropertyDump(String out, String expected) {
		String BEG = ">>>properties";
		String END = "<<<properties";
		int beg = out.indexOf(BEG)+BEG.length();
		int end = out.indexOf(END);
		String found = out.substring(beg, end);
		assertEquals(windozify(expected), windozify(found));
	}

	/**
	 * Normalize crlf to just single newline for windoze's sake.
	 */
	private String windozify(String text) {
		text = text.trim();
		return text.replaceAll("\r", "");
	}

	private ILaunchConfigurationWorkingCopy createBaseWorkingCopy() throws Exception {
		return createBaseWorkingCopy(TEST_PROJECT, TEST_MAIN_CLASS);
	}

	private ILaunchConfigurationWorkingCopy createBaseWorkingCopy(String testProjectName, String testMainClass) throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		BootLaunchConfigurationDelegate.setDefaults(wc, getProject(testProjectName), testMainClass);

		//Explictly set all options in the config to 'disabled' irrespective of
		// their default values (tests will be more robust w.r.t changing of the defaults).
		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, false);
		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, false);
		BootLaunchConfigurationDelegate.setJMXPort(wc, "");
		BootLaunchConfigurationDelegate.setProfile(wc, "");
		BootLaunchConfigurationDelegate.setRawApplicationProperties(wc, "");
		return wc;
	}

	///////////////////////////////////////////////////////////////////////////////

	public void testFastStartupNoVmArgs() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();
		BootLaunchConfigurationDelegate.setFastStartup(wc, true);

		// Disable life-cycle vm args just to test VM args for fast startup
		BootLaunchConfigurationDelegate.setEnableLifeCycle(wc, false);
		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, false);
		BootLaunchConfigurationDelegate.setEnableJMX(wc, false);

		String fastStartupArgs = BootActivator.getDefault().getPreferenceStore().getDefaultString(BootPreferences.PREF_BOOT_FAST_STARTUP_JVM_ARGS);
		assertTrue(!fastStartupArgs.trim().isEmpty());
		String vmArgs = new BootLaunchConfigurationDelegate().getVMArguments(wc);
		assertContains(" " + fastStartupArgs+" ", vmArgs);

		LaunchResult result = LaunchUtil.synchLaunch(wc);
		assertOk(result);
	}

	public void testFastStartupCustomVmArgs() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();
		BootLaunchConfigurationDelegate.setFastStartup(wc, true);

		// Disable life-cycle vm args just to test VM args for fast startup
		BootLaunchConfigurationDelegate.setEnableLifeCycle(wc, false);
		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, false);
		BootLaunchConfigurationDelegate.setEnableJMX(wc, false);

		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "-Xmx1024M");

		String fastStartupArgs = BootActivator.getDefault().getPreferenceStore().getDefaultString(BootPreferences.PREF_BOOT_FAST_STARTUP_JVM_ARGS);
		assertTrue(!fastStartupArgs.trim().isEmpty());
		String vmArgs = new BootLaunchConfigurationDelegate().getVMArguments(wc);
		assertEquals("-Xmx1024M " + fastStartupArgs+" -Dspring.boot.project.name=dump-info", vmArgs);

		// Also try live beans/JMX arguments
		BootLaunchConfigurationDelegate.setEnableLifeCycle(wc, true);
		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, true);
		BootLaunchConfigurationDelegate.setEnableJMX(wc, true);
		LaunchResult result = LaunchUtil.synchLaunch(wc);
		assertOk(result);
	}

	public void testProjectWithSpaces() throws Exception {
		createLaunchReadyProject("project with spaces");
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy("project with spaces", TEST_MAIN_CLASS);
		String actualArgsStr = new BootLaunchConfigurationDelegate().getVMArguments(wc);
		String[] acutalArgs = DebugPlugin.parseArguments(actualArgsStr);
		boolean found = false;
		final String prefix = "-Dspring.boot.project.name=";
		for (String string : acutalArgs) {
			if (string.startsWith(prefix)) {
				String value = string.substring(prefix.length());
				assertEquals("project with spaces", value);
				found = true;
			}
		}
		assertTrue("Did not find expected vmargument "+prefix, found);
	}

	public void testEmptyFastStartupVmArgs() throws Exception {
		createLaunchReadyProject(TEST_PROJECT);
		ILaunchConfigurationWorkingCopy wc = createBaseWorkingCopy();
		BootLaunchConfigurationDelegate.setFastStartup(wc, true);

		// Disable life-cycle vm args just to test VM args for fast startup
		BootLaunchConfigurationDelegate.setEnableLifeCycle(wc, false);
		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, false);
		BootLaunchConfigurationDelegate.setEnableJMX(wc, false);

		String vmArgs = "-Xmx1024M";
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, vmArgs);

		BootActivator.getDefault().getPreferenceStore().setValue(BootPreferences.PREF_BOOT_FAST_STARTUP_JVM_ARGS, "   \t  ");
		String fastStartupArgs = BootActivator.getDefault().getPreferenceStore().getString(BootPreferences.PREF_BOOT_FAST_STARTUP_JVM_ARGS);
		assertTrue(!fastStartupArgs.isEmpty() && fastStartupArgs.trim().isEmpty());
		String actualArgs = new BootLaunchConfigurationDelegate().getVMArguments(wc);
		assertEquals(vmArgs+" -Dspring.boot.project.name="+TEST_PROJECT, actualArgs);

		LaunchResult result = LaunchUtil.synchLaunch(wc);
		assertOk(result);
	}
}
