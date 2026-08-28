///*******************************************************************************
// * Copyright (c) 2015, 2020 Pivotal, Inc.
// * All rights reserved. This program and the accompanying materials
// * are made available under the terms of the Eclipse Public License v1.0
// * which accompanies this distribution, and is available at
// * https://www.eclipse.org/legal/epl-v10.html
// *
// * Contributors:
// *     Pivotal, Inc. - initial API and implementation
// *******************************************************************************/
//package org.springframework.ide.eclipse.boot.test;
//
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.ARTIFACT_ID;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCIES;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCY;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCY_MANAGEMENT;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.GROUP_ID;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.SCOPE;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.TYPE;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.VERSION;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.childEquals;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChild;
//import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChilds;
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertNull;
//import static org.junit.Assert.fail;
//import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.bootVersion;
//import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withStarters;
//import static org.springframework.ide.eclipse.boot.util.PomUtils.getArtifactId;
//import static org.springframework.ide.eclipse.boot.util.PomUtils.getGroupId;
//import static org.springframework.ide.eclipse.boot.util.PomUtils.getScope;
//import static org.springframework.ide.eclipse.boot.util.PomUtils.getTextChild;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.Set;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//import org.apache.commons.lang3.tuple.Pair;
//import org.eclipse.core.resources.IProject;
//import org.eclipse.core.runtime.jobs.Job;
//import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
//import org.junit.AfterClass;
//import org.junit.Before;
//import org.junit.BeforeClass;
//import org.junit.Ignore;
//import org.junit.Rule;
//import org.junit.Test;
//import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
//import org.springframework.ide.eclipse.boot.core.MavenId;
//import org.springframework.ide.eclipse.boot.core.SpringBootCore;
//import org.springframework.ide.eclipse.boot.core.initializr.InitializrService;
//import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
//import org.springframework.ide.eclipse.boot.test.util.TestBracketter;
//import org.springframework.ide.eclipse.boot.wizard.CheckBoxesSection.CheckBoxModel;
//import org.springframework.ide.eclipse.boot.wizard.NewSpringBootWizardModel;
//import org.springframework.ide.eclipse.boot.wizard.PopularityTracker;
//import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersInitializrService;
//import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersPreferences;
//import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersWizardModel;
//import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
//import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
//import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;
//import org.w3c.dom.Element;
//
//import com.google.common.collect.ImmutableSet;
//
//import junit.framework.AssertionFailedError;
//
///**
// * @author Kris De Volder
// */
//@SuppressWarnings("restriction")
//public class EditStartersModelTest extends AddStartersTestHarness {
//
//	private static final String REPOSITORY = "repository";
//	private static final String REPOSITORIES = "repositories";
//
//
//	private static boolean wasAutobuilding;
//
//	@BeforeClass
//	public static void setupClass() throws Exception {
//		wasAutobuilding = StsTestUtil.isAutoBuilding();
//		StsTestUtil.setAutoBuilding(false);
//	}
//
//	@AfterClass
//	public static void teardownClass() throws Exception {
//		StsTestUtil.setAutoBuilding(wasAutobuilding);
//	}
//
//	@Before
//	public void setup() throws Exception {
//		StsTestUtil.cleanUpProjects();
//	}
//
//	@Rule
//	public TestBracketter testBracketer = new TestBracketter();
//
//
//
//	/**
//	 * Tests that we are able to remove a starter.
//	 */
//	@Test
//	public void removeStarter() throws Exception {
//		IProject project = harness.createBootProject("removeStarter", withStarters("web", "actuator"));
//		String starterZipFile = STARTER_ZIP_WEB_ACTUATOR;
//		String validInitializrUrl = MOCK_VALID_INITIALIZR_URL;
//		String[] supportedBootVersions = SUPPORTED_BOOT_VERSIONS;
//		String[] dependenciesToSelect = new String[] {"web", "actuator"};
//
//
//		InitializrService wrappedService = new MockInitializrService(supportedBootVersions, mavenStartersHarness,
//				INITIALIZR_INFO_INPUT);
//
//		ISpringBootProject bootProject = new SpringBootCore(wrappedService).project(project);
//
//		AddStartersInitializrService initializrService = new MockAddStartersInitializrService(starterZipFile,
//				validInitializrUrl, supportedBootVersions, dependenciesToSelect, (MockInitializrService)wrappedService);
//		AddStartersPreferences preferences = createPreferences(validInitializrUrl, prefs);
//		AddStartersWizardModel wizard = createAndLoadWizard(project, preferences, initializrService,
//				dependenciesToSelect);
//
//
//		initializrHarness.assertStarterDeps(wizard.dependencies.getCurrentSelection(), "web", "actuator");
//
//		wizard.removeDependency("web");
//		initializrHarness.assertStarterDeps(wizard.dependencies.getCurrentSelection(), /* removed: "web",*/ "actuator");
//		performOk(wizard);
//
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		initializrHarness.assertStarters(bootProject.getBootStarters(), "actuator");
//	}
//
//	private void performOk(EditStartersModel wizard) throws Exception {
//		wizard.performOk();
//		Job.getJobManager().join(EditStartersModel.JOB_FAMILY, null);
//	}
//
//	@Test
//	public void crossSelection() throws Exception {
//		//Test case derived from bug report: https://www.pivotaltracker.com/story/show/157257493
//
//		IProject project = harness.createBootProject("cross-select", withStarters());
//		initializrHarness.assertMavenDeps(project,
//				"org.springframework.boot:spring-boot-starter",
//				"org.springframework.boot:spring-boot-starter-test@test"
//		);
//
//		{	//Add 'cloud-stream'
//			EditStartersModel wizard = createWizard(project);
//			initializrHarness.assertStarterDeps(wizard.dependencies.getCurrentSelection() /*NONE*/);
//			wizard.addDependency("cloud-stream");
//			performOk(wizard);
//			StsTestUtil.assertNoErrors(project); //force project build
//
//			initializrHarness.assertMavenDeps(project,
//					"org.springframework.cloud:spring-cloud-stream",
//					"org.springframework.boot:spring-boot-starter-test@test",
//					"org.springframework.cloud:spring-cloud-stream-test-support@test" //comes in automatically with spring-cloud-stream
//			);
//		}
//
//
//		{	//Add 'rabbitmq'
//			EditStartersModel wizard = createWizard(project);
//			initializrHarness.assertStarterDeps(wizard.dependencies.getCurrentSelection(), "cloud-stream");
//			wizard.addDependency("amqp");
//			performOk(wizard);
//			StsTestUtil.assertNoErrors(project); //force project build
//
//			initializrHarness.assertMavenDeps(project,
//					//old:
//					"org.springframework.cloud:spring-cloud-stream",
//					"org.springframework.boot:spring-boot-starter-test@test",
//					"org.springframework.cloud:spring-cloud-stream-test-support@test",
//					//added:
//					"org.springframework.boot:spring-boot-starter-amqp", //by user selection (amqp)
//					"org.springframework.amqp:spring-rabbit-test@test", //by user selection (amqp)
//					"org.springframework.cloud:spring-cloud-stream-binder-rabbit" // cross-selection from rabbit + spring-cloud-stream
//			);
//		}
//	}
//
//	@Test
//	public void addCloudFunctionWebDependency() throws Exception {
//		//Test case derived from bug report: https://github.com/spring-projects/sts4/issues/313
//		IProject project;
//
//		project = harness.createBootProject("web-function", withStarters("webflux"));
//		initializrHarness.assertMavenDeps(project,
//				"io.projectreactor:reactor-test@test",
//				"org.springframework.boot:spring-boot-starter-test@test",
//				"org.springframework.boot:spring-boot-starter-webflux"
//		);
//		initializrHarness.assertBoms(project /*NONE*/);
//
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("cloud-function");
//		performOk(wizard);
//
//		System.out.println(">>> pom.xml (after dialog closed)");
//		System.out.println(IOUtil.toString(project.getFile("pom.xml").getContents()));
//		System.out.println("<<< pom.xml (after dialog closed)");
//
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		initializrHarness.assertMavenDeps(project,
//				"io.projectreactor:reactor-test@test",
//				"org.springframework.boot:spring-boot-starter-test@test",
//				"org.springframework.boot:spring-boot-starter-webflux",
//				"org.springframework.cloud:spring-cloud-function-web"
//		);
//
//		initializrHarness.assertBoms(project, "org.springframework.cloud:spring-cloud-dependencies");
//	}
//
//	//Unfortunately this test takes much too long to run to be practical. There's 6961 pairs to check,
//	// each pair creates and imports the project to verify the 'reverse' pom analysis returns
//	// the initial selection. This takes 3 to 4 seconds each. So to this test would take around
//	// 6 to 7 hours to run.
//	// If we want to make this test more practical we would have to try and implement it more directly
//	// (using only the pom and metadata from initializer app) and not do a full on maven project
//	// creation (this is what takes most of the time).
//	// In the mean time this test has been replaced with a more focussed test, see focussedCrossSelectionTest
//	// below.
//	@Test
//	@Ignore
//	public void exhaustiveCrossSelectionTest() throws Exception {
//		//Creates a project for every possible pairs of two dependencies. Then verifies whether we
//		//EditStartersModel correctly detects that pair as 'selected'.
//		List<Pair<CheckBoxModel<Dependency>, CheckBoxModel<Dependency>>> allPairs = allDependencyPairs();
//		doMultiplCrossSelectionTests(allPairs);
//	}
//
//	private List<Pair<CheckBoxModel<Dependency>, CheckBoxModel<Dependency>>> allDependencyPairs() throws Exception {
//		NewSpringBootWizardModel newWizard = new NewSpringBootWizardModel(new MockPrefsStore());
//		Collection<CheckBoxModel<Dependency>> allDependencies = newWizard.dependencies.getAllBoxes();
//		List<Pair<CheckBoxModel<Dependency>, CheckBoxModel<Dependency>>> allPairs = new ArrayList<>();
//		for (CheckBoxModel<Dependency> cb1 : allDependencies) {
//			for (CheckBoxModel<Dependency> cb2 : allDependencies) {
//				Dependency d1 = cb1.getValue();
//				Dependency d2 = cb2.getValue();
//				if (d1.getId().compareTo(d2.getId()) <= 0) {
//					allPairs.add(Pair.of(cb1, cb2));
//				}
//			}
//		}
//		return allPairs;
//	}
//
//	@Test
//	@Ignore //Not normally run during builds to save some time. You can run it manually
//	// to determine the values of the 'badIds' in 'focussedCrossSelectionTest'.
//	// If this test passes then the badIds is empty. Otherwise, you can chech the failure message
//	// to see which ids result in project import issues, if they are not already on the list
//	// report them to the intializer team.
//	public void isolatedDependencies() throws Exception {
//		doMultiplCrossSelectionTests(allDependencyPairs().stream().filter(pair -> pair.getLeft().equals(pair.getRight())).collect(Collectors.toList()));
//	}
//
//	@Test
//	public void webAndFunctionCrossSelectionTest() throws Exception {
//		NewSpringBootWizardModel newWizard = new NewSpringBootWizardModel(new MockPrefsStore());
//		doSingleCrossSelectionTest(newWizard.getDependencyBox("cloud-function"), newWizard.getDependencyBox("web"));
//	}
//
//	@Test
//	@Ignore //Initializer folks want us to discontinue this feaure so why worry about it's test any more?
//	public void focussedCrossSelectionTest() throws Exception {
//		ImmutableSet<String> badIds = ImmutableSet.of(
//				//See: https://github.com/spring-io/start.spring.io/issues/191
//				//"cloud-starter-zookeeper-config", "cloud-starter-consul-config", "data-rest-hal"
//		);
//
//		ImmutableSet<Pair<String,String>> badPairs = ImmutableSet.of();
//
//		ImmutableSet<String> interestingIds = ImmutableSet.of(
//				"cloud-function", "session"
//		);
//
//		Stream<Pair<CheckBoxModel<Dependency>, CheckBoxModel<Dependency>>> webPairs = allDependencyPairs().stream()
//		.filter(pair ->
//			interestingIds.contains(pair.getLeft().getValue().getId()) ||
//			interestingIds.contains(pair.getRight().getValue().getId())
////		)
////		.filter(pair ->
////			!badIds.contains(pair.getLeft().getValue().getId()) &&
////			!badIds.contains(pair.getRight().getValue().getId())
//		);
//		doMultiplCrossSelectionTests(webPairs.collect(Collectors.toList()), badIds, badPairs);
//	}
//
//	private void doMultiplCrossSelectionTests(Collection<Pair<CheckBoxModel<Dependency>,CheckBoxModel<Dependency>>> pairs) {
//		doMultiplCrossSelectionTests(pairs, ImmutableSet.of(), ImmutableSet.of());
//	}
//
//	private void doMultiplCrossSelectionTests(Collection<Pair<CheckBoxModel<Dependency>,CheckBoxModel<Dependency>>> pairs, ImmutableSet<String> expectedBadIds, ImmutableSet<Pair<String,String>> expectedBadPairs) {
//		Map<Pair<Dependency, Dependency>, String> badPairs = new HashMap<>();
//		Set<String> actualBadIds = new HashSet<>();
//		System.out.println("Number of pairs to test: "+pairs.size());
//		int testedPairs = 0;
//		for (Pair<CheckBoxModel<Dependency>, CheckBoxModel<Dependency>> pair : pairs) {
//			try {
//				testedPairs++;
//				System.out.println(testedPairs+"/"+pairs.size()+": "+describe(pair.getLeft().getValue())+" + "+describe(pair.getRight().getValue()));
//				doSingleCrossSelectionTest(pair.getLeft(), pair.getRight());
//			} catch (Throwable e) {
//				System.err.println(ExceptionUtil.getMessage(e));
//				if (pair.getLeft().getValue().getId().equals(pair.getRight().getValue().getId())) {
//					actualBadIds.add(pair.getLeft().getValue().getId());
//				}
//				boolean expectedProblem = expectedBadIds.contains(pair.getLeft().getValue().getId()) ||
//						expectedBadIds.contains(pair.getRight().getValue().getId());
//				if (!expectedProblem) {
//					badPairs.put(Pair.of(pair.getLeft().getValue(), pair.getRight().getValue()), ExceptionUtil.getMessage(e));
//				}
//			}
//		}
//		ImmutableSet.Builder<Pair<String,String>> actualBadPairs = ImmutableSet.builder();
//		for (Pair<Dependency, Dependency> badPair : badPairs.keySet()) {
//			actualBadPairs.add(Pair.of(badPair.getLeft().getId(), badPair.getRight().getId()));
//		}
//
//		if (!expectedBadPairs.equals(actualBadPairs.build())) {
//			StringBuilder message = new StringBuilder("--- Problem summary ----\n");
//			for (Entry<Pair<Dependency, Dependency>, String> badEndtry : badPairs.entrySet()) {
//				Pair<Dependency, Dependency> pair = badEndtry.getKey();
//				message.append(describe(pair.getLeft()) +" + "+describe(pair.getRight())+": "+badEndtry.getValue());
//				message.append("\n");
//			}
//			fail(message.toString());
//		}
//		assertEquals(expectedBadIds, actualBadIds);
//	}
//
//	private void doSingleCrossSelectionTest(
//			CheckBoxModel<Dependency> cb1,
//			CheckBoxModel<Dependency> cb2) throws Exception {
//		Dependency d1 = cb1.getValue();
//		Dependency d2 = cb2.getValue();
//		String projectName = "test-"+d1.getId()+"-"+d2.getId();
//		//Assuming that it doesn't matter what order two deps are selected so
//		//only test one of a pairs (d1,d2) and (d2,d1)
//		if (!cb1.getEnablement().getValue()) {
//			System.out.println("Skipping because "+d1+" is not enabled");
//			return;
//		}
//		if (!cb2.getEnablement().getValue()) {
//			System.out.println("Skipping because "+d2+" is not enabled");
//			return;
//		}
//		Set<String> starters = new HashSet<>(Arrays.asList(d1.getId(), d2.getId()));
//		try {
//			IProject project;
//			try {
//				project = harness.createBootProject(projectName, withStarters(starters.toArray(new String[starters.size()])));
//			} catch (Throwable e) {
//				throw new AssertionFailedError("Project creation failed");
//			}
//			EditStartersModel wizard = createWizard(project);
//			Set<String> selectedDeps = wizard.dependencies.getCurrentSelection().stream().map(d -> d.getId()).collect(Collectors.toSet());
//			assertEquals(starters, selectedDeps);
//			System.out.println("OK");
//		} finally {
//			StsTestUtil.deleteAllProjects();
//		}
//	}
//
//	private String describe(Dependency d) {
//		return d.getId() +"["+d.getName()+"]";
//	}
//
//	/**
//	 * Tests that we are able to add a basic starter.
//	 */
//	@Test
//	public void addStarter() throws Exception {
//		IProject project = harness.createBootProject("addStarter", withStarters("web"));
//		final ISpringBootProject bootProject = springBootCore.project(project);
//		EditStartersModel wizard = createWizard(project);
//		assertEquals(bootProject.getBootVersion(), wizard.getBootVersion());
//		initializrHarness.assertStarterDeps(wizard.dependencies.getCurrentSelection(), "web");
//
//		PopularityTracker popularities = new PopularityTracker(prefs);
//		initializrHarness.assertUsageCounts(bootProject, popularities  /*none*/);
//
//		wizard.addDependency("actuator");
//		initializrHarness.assertStarterDeps(wizard.dependencies.getCurrentSelection(), "web", "actuator");
//		performOk(wizard);
//
//		initializrHarness.assertUsageCounts(bootProject, popularities, "actuator:1");
//
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		initializrHarness.assertStarters(bootProject.getBootStarters(), "web", "actuator");
//
//		//check that the 'scope' is not set in the pom.xml:
//		IDOMDocument pom = initializrHarness.parsePom(project);
//
//		Element depEl = initializrHarness.findDependency(bootProject, pom, "actuator");
//		assertEquals("org.springframework.boot", getGroupId(depEl));
//		assertEquals("spring-boot-starter-actuator", getArtifactId(depEl));
//		assertEquals(null, getScope(depEl));
//	}
//
//	@Test
//	public void addStarterWithTestScope() throws Exception {
//		IProject project = harness.createBootProject("addStarterWithTestScope", withStarters("web"));
//		final ISpringBootProject bootProject = springBootCore.project(project);
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("restdocs");
//		performOk(wizard);
//
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		initializrHarness.assertStarters(bootProject.getBootStarters(), "web", "restdocs");
//
//		//check that the 'scope' is set properly
//		IDOMDocument pom = initializrHarness.parsePom(project);
//		Element depEl = initializrHarness.findDependency(bootProject, pom, "restdocs");
//		assertEquals("spring-restdocs-mockmvc", getArtifactId(depEl));
//		assertEquals("test", getScope(depEl));
//	}
//
//	@Test
//	public void addStarterWithBom() throws Exception {
//// We'll be adding this starter:
////	      "id": "cloud-eureka",
////	      "groupId": "org.springframework.cloud",
////	      "artifactId": "spring-cloud-starter-eureka",
////	      "scope": "compile",
////	      "bom": "cloud-bom"
//
//		IProject project = harness.createBootProject("addStarterWithBom", withStarters("web"));
//		final ISpringBootProject bootProject = springBootCore.project(project);
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("cloud-eureka");
//		performOk(wizard);
//
//		System.out.println(">>> pom.xml (after dialog closed)");
//		System.out.println(IOUtil.toString(project.getFile("pom.xml").getContents()));
//		System.out.println("<<< pom.xml (after dialog closed)");
//
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		initializrHarness.assertStarters(bootProject.getBootStarters(), "web", "cloud-eureka");
//	}
//
//	@Test
//	public void addMultipleStartersWithSameBom() throws Exception {
//		//This test uses more 'controlled' parameters:
//		IProject project = harness.createBootProject("addMultipleStartersWithSameBom",
//				bootVersion(BOOT_CURRENT_RELEASE), // boot version fixed
//				withStarters("web")
//		);
//		initializr.setInputs("sample"); // sample intializr json captured for this version
//		final ISpringBootProject bootProject = springBootCore.project(project);
//		int initialBomCount = getBomCount(initializrHarness.parsePom(project));
//
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("cloud-eureka");
//		wizard.addDependency("cloud-config-client");
//		performOk(wizard);
//
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		initializrHarness.assertStarters(bootProject.getBootStarters(), "web", "cloud-eureka", "cloud-config-client");
//
//		IDOMDocument pom = initializrHarness.parsePom(project);
//		int finalBomCount = getBomCount(pom);
//		assertEquals(initialBomCount+1, finalBomCount);
//
//		//check that one repo got added
//		assertRepoCount(1, pom);
//
////		Element repo = getRepo(pom, "spring-snapshots");
////		assertNotNull(repo);
////		assertEquals("Spring Snapshots", getTextChild(repo, "name"));
////		assertEquals("https://repo.spring.io/snapshot", getTextChild(repo, "url"));
////		assertEquals("true", getSnapshotsEnabled(repo));
//
//		Element repo = getRepo(pom, "spring-milestones");
//		assertNotNull(repo);
//		assertEquals("Spring Milestones", getTextChild(repo, "name"));
//		assertEquals("https://repo.spring.io/milestone", getTextChild(repo, "url"));
//		assertEquals("false", getSnapshotsEnabled(repo));
//	}
//
//	@Test
//	public void addMultipleStartersWithDifferentBom() throws Exception {
//		//This test uses more 'controlled' parameters:
//		IProject project = harness.createBootProject("addMultipleStartersWithDifferentBom",
//				bootVersion(BOOT_CURRENT_RELEASE), // boot version fixed
//				withStarters("web")
//		);
//		initializr.setInputs("sample"); // sample intializr json captured for this version
//		initializr.enableFakePomGenerator();
//		final ISpringBootProject bootProject = springBootCore.project(project);
//		int initialBomCount = getBomCount(initializrHarness.parsePom(project));
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("cloud-eureka");
//		wizard.addDependency("vaadin");
//		performOk(wizard);
//
////		System.out.println("-- pom after ---");
////		System.out.println(IOUtil.toString(project.getFile("pom.xml").getContents()));
//
//// Note Doing 'fake' stuff causes broken project. So the below will fail. We should test
//// pom content more directly for this test.
////		StsTestUtil.assertNoErrors(project); //force project build
////		assertStarters(bootProject.getBootStarters(), "web", "cloud-eureka", "vaadin");
//
//		IDOMDocument pom = initializrHarness.parsePom(project);
//		initializrHarness.assertMavenDeps(project,
//				"org.springframework.boot:spring-boot-starter-web",
//				"org.springframework.boot:spring-boot-starter-test@test",
//				"org.springframework.cloud:spring-cloud-starter-netflix-eureka-client",
//				"com.vaadin:vaadin-spring-boot-starter"
//		);
//		int finalBomCount = getBomCount(pom);
//		assertEquals(initialBomCount+2, finalBomCount);
//		{
//			Element bom = getBom(pom, "spring-cloud-dependencies");
//			assertEquals("org.springframework.cloud", getTextChild(bom,GROUP_ID));
//			assertEquals("Finchley.RC2", getTextChild(bom,VERSION));
//			assertEquals("pom", getTextChild(bom,TYPE));
//			assertEquals("import", getTextChild(bom,SCOPE));
//		}
//		{
//			Element bom = getBom(pom, "vaadin-bom");
//			assertEquals("com.vaadin", getTextChild(bom,GROUP_ID));
//			assertEquals("8.4.1", getTextChild(bom,VERSION));
//			assertEquals("pom", getTextChild(bom,TYPE));
//			assertEquals("import", getTextChild(bom,SCOPE));
//		}
//	}
//
//	@Test
//	public void addBomWithSubsetOfRepos() throws Exception {
//		//This test uses more 'controlled' parameters:
//		String bootVersion = BOOT_CURRENT_RELEASE;
//		IProject project = harness.createBootProject("addBomWithSubsetOfRepos",
//				bootVersion(bootVersion), // boot version fixed
//				withStarters("web")
//		);
//
//		initializr.setInputs("sample-with-fakes"); // must use 'fake' data because the situation we are after doesn't exist in the real data
//		initializr.enableFakePomGenerator();
//
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("cloud-eureka");
//		performOk(wizard);
//
//		System.out.println("-- pom after ---");
//		System.out.println(IOUtil.toString(project.getFile("pom.xml").getContents()));
//
//		//!!! fake data may not produce a project that builds without
//		//!!! problem so don't check for build errors in this test
//
//		//check that only ONE repo got added
//		IDOMDocument pom = initializrHarness.parsePom(project);
//
//		//check the dependency got added to the pom
//		assertNotNull(initializrHarness.findDependency(pom, new MavenId("org.springframework.cloud", "spring-cloud-starter-eureka")));
//
//		//check that both repos got added
//		assertRepoCount(2, pom);
//
//		Element repo = getRepo(pom, "spring-snapshots");
//		assertNotNull(repo);
//		assertEquals("Spring Snapshots", getTextChild(repo, "name"));
//		assertEquals("https://repo.spring.io/snapshot", getTextChild(repo, "url"));
//		assertEquals("true", getSnapshotsEnabled(repo));
//
//		repo = getRepo(pom, "spring-milestones");
//		assertNotNull(repo);
//		assertEquals("Spring Milestones", getTextChild(repo, "name"));
//		assertEquals("https://repo.spring.io/milestone", getTextChild(repo, "url"));
//		assertEquals("false", getSnapshotsEnabled(repo));
//	}
//
//	@Test
//	public void addDependencyWithRepo() throws Exception {
//		//This test uses more 'controlled' parameters:
//		String bootVersion = BOOT_CURRENT_RELEASE;
//		IProject project = harness.createBootProject("addDependencyWithRepo",
//				bootVersion(bootVersion), // boot version fixed
//				withStarters("web")
//		);
//
//		initializr.setInputs("sample-with-fakes"); // must use 'fake' data because the situation we are after doesn't exist in the real data
//		initializr.enableFakePomGenerator();
//
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("fake-dep");
//		performOk(wizard);
//
//		//!!! fake data may not produce a project that builds without
//		//!!! problem so don't check for build errors in this test
//
//		IDOMDocument pom = initializrHarness.parsePom(project);
//
//		//check the dependency got added to the pom
//		assertNotNull(initializrHarness.findDependency(pom, new MavenId("org.springframework.fake", "spring-fake-dep")));
//
//		//check that just the expected repo got added
//		assertNotNull(getRepo(pom, "spring-milestones"));
//		assertNull(getRepo(pom, "spring-snapshots"));
//		assertRepoCount(1, pom);
//	}
//
//	@Test
//	public void unselectAllDependencies() throws Exception {
//		//See: https://github.com/spring-projects/sts4/issues/52
//		IProject project = harness.createBootProject("projectToUnselectoAll", withStarters("web", "actuator"));
//		StsTestUtil.assertNoErrors(project); //force project build
//
//		EditStartersModel wizard = createAndLoadWizard(project);
//		wizard.removeDependency("web");
//		wizard.removeDependency("actuator");
//
//		performOk(wizard);
//
//		final ISpringBootProject bootProject = springBootCore.project(project);
//		StsTestUtil.assertNoErrors(project);
//		initializrHarness.assertStarters(bootProject.getBootStarters() /*NONE*/);
//
//		IDOMDocument pom = initializrHarness.parsePom(project);
//		assertNotNull(initializrHarness.findDependency(pom, new MavenId("org.springframework.boot", "spring-boot-starter")));
//	}
//
//	@Test
//	public void addDependenciesFromEmpty() throws Exception {
//		//See: https://github.com/spring-projects/sts4/issues/52
//		IProject project = harness.createBootProject("projectToUnselectoAll");
//		final ISpringBootProject bootProject = springBootCore.project(project);
//		StsTestUtil.assertNoErrors(project); //force project build
//		initializrHarness.assertStarters(bootProject.getBootStarters() /*NONE*/);
//		{	//Check the state of the default dependencies
//			IDOMDocument pom = initializrHarness.parsePom(project);
//			assertNotNull(initializrHarness.findDependency(pom, new MavenId("org.springframework.boot", "spring-boot-starter")));
//			assertEquals("test", getScope(initializrHarness.findDependency(pom, new MavenId("org.springframework.boot", "spring-boot-starter-test"))));
//		}
//
//		EditStartersModel wizard = createWizard(project);
//		wizard.addDependency("web");
//		wizard.addDependency("actuator");
//
////		System.out.println("-- pom before ---");
////		System.out.println(IOUtil.toString(project.getFile("pom.xml").getContents()));
//
//		performOk(wizard);
//
////		System.out.println("-- pom after ---");
////		System.out.println(IOUtil.toString(project.getFile("pom.xml").getContents()));
//
//		StsTestUtil.assertNoErrors(project);
//		initializrHarness.assertStarters(bootProject.getBootStarters(), "web", "actuator");
//		{	//Check the state of the default dependencies
//			IDOMDocument pom = initializrHarness.parsePom(project);
//			assertNull(initializrHarness.findDependency(pom, new MavenId("org.springframework.boot", "spring-boot-starter")));
//			assertEquals("test", getScope(initializrHarness.findDependency(pom, new MavenId("org.springframework.boot", "spring-boot-starter-test"))));
//		}
//	}
//
////	@Test
////	public void serviceUnavailable() throws Exception {
////		String bootVersion = BOOT_1_3_X_RELEASE;
////		IProject project = harness.createBootProject("serviceUnavailable",
////				bootVersion(bootVersion), // boot version fixed
////				withStarters("web")
////		);
////
////		initializr.makeUnavailable();
////
////		EditStartersModel wizard = createWizard(project);
////		assertFalse(wizard.isSupported());
////	}
//
//	//TODO: testing of...
//	// - repository field in individual dependency taken into account?
//
//	////////////// Harness code below ///////////////////////////////////////////////
//
//
//	private String getSnapshotsEnabled(Element repo) {
//		if (repo != null) {
//			Element snapshots = findChild(repo, "snapshots");
//			if (snapshots != null) {
//				return getTextChild(snapshots, "enabled");
//			}
//		}
//		return null;
//	}
//
//	private Element getRepo(IDOMDocument pom, String id) {
//		Element doc = pom.getDocumentElement();
//		Element repos = findChild(doc, REPOSITORIES);
//		if (repos != null) {
//			return findChild(repos, REPOSITORY, childEquals("id", id));
//		}
//		return null;
//	}
//
//
//
//
//
//
//	private void assertRepoCount(int expect, IDOMDocument pom) {
//		assertEquals(expect, getRepoCount(pom));
//	}
//
//	private int getRepoCount(IDOMDocument pom) {
//		Element reposEl = findChild(pom.getDocumentElement(), REPOSITORIES);
//		if (reposEl != null) {
//			List<Element> repos = findChilds(reposEl, REPOSITORY);
//			if (repos != null) {
//				return repos.size();
//			}
//		}
//		return 0;
//	}
//
//	/**
//	 * Find a bom element in pom based on its artifactId (can't use its own id as that isn't
//	 * found anywhere in the element's xml).
//	 */
//	private Element getBom(IDOMDocument pom, String aid) {
//		Element depman = findChild(pom.getDocumentElement(), DEPENDENCY_MANAGEMENT);
//		if (depman != null) {
//			Element deps = findChild(depman, DEPENDENCIES);
//			if (deps != null) {
//				return findChild(deps, DEPENDENCY, childEquals(ARTIFACT_ID, aid));
//			}
//		}
//		return null;
//	}
//
//	private int getBomCount(IDOMDocument pom) {
//		Element depman = findChild(pom.getDocumentElement(), DEPENDENCY_MANAGEMENT);
//		if (depman != null) {
//			Element deps = findChild(depman, DEPENDENCIES);
//			if (deps != null) {
//				List<Element> boms = findChilds(deps, DEPENDENCY);
//				if (boms != null) {
//					return boms.size();
//				}
//			}
//		}
//		return 0;
//	}
//
//
//
//
//
//
//
//
//
//
//}
