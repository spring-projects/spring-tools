/*******************************************************************************
 * Copyright (c) 2013, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springframework.ide.eclipse.boot.wizard.CheckBoxesSection.CheckBoxModel;
import org.springframework.ide.eclipse.boot.wizard.HierarchicalMultiSelectionFieldModel;
import org.springframework.ide.eclipse.boot.wizard.MultiSelectionFieldModel;
import org.springframework.ide.eclipse.boot.wizard.NewSpringBootWizardModel;
import org.springframework.ide.eclipse.boot.wizard.PopularityTracker;
import org.springframework.ide.eclipse.boot.wizard.RadioGroup;
import org.springframework.ide.eclipse.boot.wizard.RadioGroups;
import org.springframework.ide.eclipse.boot.wizard.RadioInfo;
import org.springframework.ide.eclipse.boot.wizard.content.BuildType;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportStrategy;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;
import org.springsource.ide.eclipse.commons.frameworks.core.util.StringUtils;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.livexp.util.Filters;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

import junit.framework.TestCase;

/**
 * Tests whether NewSpringBootWizardModel adequately parses initializer form data.
 *
 * @author Kris De Volder
 */
public class NewSpringBootWizardModelTest extends TestCase {

	//private static final String INITIALIZR_JSON = "initializr.json";
	private static final String INITIALIZR_JSON = "initializr-v2.1.json";
	private static final String INITIALIZR_V_2_2_JSON = "initializr-v2.2.json";

	private static final String[] testDataFiles = {
			INITIALIZR_JSON, INITIALIZR_V_2_2_JSON
	};

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		StsTestUtil.cleanUpProjects();
	}

	public static NewSpringBootWizardModel parseFrom(String resourcePath, IPreferenceStore store) throws Exception {
		AtomicReference<Throwable> firstError = new AtomicReference<>();
		Log.errorHandler = error -> {
			firstError.getAndUpdate(existing -> existing!=null?existing:error);
		};
		try {
			URL formUrl = resourceUrl(resourcePath);
			return new NewSpringBootWizardModel(new URLConnectionFactory(), formUrl.toString(), store) {

				@Override
				protected void importProject(org.eclipse.core.runtime.IProgressMonitor mon) throws java.lang.reflect.InvocationTargetException ,InterruptedException {
					//do nothing (this is a fake wizard, not meant to create real projects).
				}
			};
		} finally {
			Log.errorHandler = null;
			if (firstError.get()!=null) {
				throw ExceptionUtil.exception(firstError.get());
			}
		}
	}

	public static NewSpringBootWizardModel parseFrom(String resourcePath) throws Exception {
		return parseFrom(resourcePath, new MockPrefsStore());
	}

	public static URL resourceUrl(String resourcePath) {
		URL formUrl = NewSpringBootWizardModelTest.class.getResource(resourcePath);
		return formUrl;
	}

	public void testParsedRadios() throws Exception {
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		RadioGroups radioGroups = model.getRadioGroups();
		assertGroupNames(radioGroups, "type", "packaging", "javaVersion", "language", "bootVersion");
	}

	public void testPackagingRadios() throws Exception {
		for (String f : testDataFiles) {
			NewSpringBootWizardModel model = parseFrom(f);
			RadioGroup packagingTypes = model.getRadioGroups().getGroup("packaging");
			assertNotNull(packagingTypes);
			assertGroupValues(packagingTypes, "jar", "war");
			assertEquals("jar", packagingTypes.getDefaultValue().getValue());
		}
	}

	public void testJavaVersionRadios() throws Exception {
		for (String f : testDataFiles) {
			NewSpringBootWizardModel model = parseFrom(f);
			RadioGroup group = model.getRadioGroups().getGroup("javaVersion");
			assertNotNull(group);
			assertGroupValues(group, "1.6", "1.7", "1.8");
			assertEquals("1.8", group.getDefaultValue().getValue());
		}
	}

	public void testBuildTypeRadios() throws Exception {
		for (String jsonFile : testDataFiles) {
			NewSpringBootWizardModel model = parseFrom(jsonFile);
			String starterZipUrl = resourceUrl(jsonFile).toURI().resolve("/starter.zip").toString();
			assertEquals(starterZipUrl, model.baseUrl.getValue());

			RadioGroup group = model.getRadioGroups().getGroup("type");
			assertNotNull(group);
			assertGroupValues(group, "MAVEN", "GRADLE");
			assertEquals("MAVEN", group.getDefaultValue().getValue());

			group.getSelection().selection.setValue(group.getRadio("MAVEN"));
			assertEquals(BuildType.MAVEN, model.getBuildType());
			assertEquals(starterZipUrl, model.baseUrl.getValue());

			for (ImportStrategy gradleStrategy : BuildType.GRADLE.getImportStrategies()) {
				group.getSelection().selection.setValue(group.getRadio(gradleStrategy.getId()));
				assertEquals(BuildType.GRADLE, model.getBuildType());
				assertEquals(gradleStrategy, model.getImportStrategy());
				assertEquals(starterZipUrl, model.baseUrl.getValue());
			}
		}
	}

	public void testBuildTypeRadiosVariant() throws Exception {
		//Hypothetical variant where the json "types" lists different actions for maven and gradle zip

		String jsonFile = "initializr-variant.json";

		String mavenZipUrl = resourceUrl(jsonFile).toURI().resolve("/maven.zip").toString();
		String gradleZipUrl = resourceUrl(jsonFile).toURI().resolve("/gradle.zip").toString();

		NewSpringBootWizardModel model = parseFrom(jsonFile);

		RadioGroup group = model.getRadioGroups().getGroup("type");
		assertNotNull(group);
		assertGroupValues(group, "MAVEN", "GRADLE");
		assertEquals("MAVEN", group.getDefaultValue().getValue());
		assertEquals(mavenZipUrl, model.baseUrl.getValue());

		for (ImportStrategy gradleStrat : BuildType.GRADLE.getImportStrategies()) {
			group.getSelection().selection.setValue(group.getRadio(gradleStrat.getId()));
			assertEquals(BuildType.GRADLE, model.getBuildType());
			assertEquals(gradleStrat, model.getImportStrategy());
			assertEquals(gradleZipUrl, model.baseUrl.getValue());
		}

		group.getSelection().selection.setValue(group.getRadio("MAVEN"));
		assertEquals(BuildType.MAVEN, model.getBuildType());
		assertEquals(mavenZipUrl, model.baseUrl.getValue());
	}

	public void testStarters() throws Exception {
		for (String jsonFile : testDataFiles) {
			NewSpringBootWizardModel model = parseFrom(jsonFile);

			Collection<Dependency> styles = getAllChoices(model.dependencies);
			assertNotNull(styles);
			assertTrue(styles.size()>7);

			for (String catName : model.dependencies.getCategories()) {
				String lastLabel = null; //check that style labels are sorted within each category
				MultiSelectionFieldModel<Dependency> cat = model.dependencies.getContents(catName);
				for (Dependency choice : cat.getChoices()) {
					String label = cat.getLabel(choice);
					if (lastLabel!=null) {
						assertTrue("Labels not sorted: '"+lastLabel+"' > '"+label+"'", lastLabel.compareTo(label)<0);
					}
					lastLabel = label;
					assertNotNull("No tooltip for: "+choice+" ["+label+"]", cat.getTooltipHtml(choice).get());
				}
			}
		}
	}

	public static <T> Collection<T> getAllChoices(
			HierarchicalMultiSelectionFieldModel<T> dependencies) {
		ArrayList<T> choices = new ArrayList<>();
		for (String catName : dependencies.getCategories()) {
			MultiSelectionFieldModel<T> cat = dependencies.getContents(catName);
			choices.addAll(Arrays.asList(cat.getChoices()));
		}
		return choices;
	}

	public void testVersionRangesBuildSnaphotBug() throws Exception {
		//See https://www.pivotaltracker.com/story/show/100963226
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		Dependency devtools = getDependencyById(model, "devtools");

		RadioGroup bootVersion = model.getBootVersion();
		RadioInfo newer1 = bootVersion.getRadio("1.3.0.M2");
		RadioInfo newer2 = bootVersion.getRadio("1.3.0.BUILD-SNAPSHOT");
		RadioInfo older = bootVersion.getRadio("1.2.6.BUILD-SNAPSHOT");

		LiveExpression<Boolean> devtoolsEnabled  = getEnablement(model.dependencies, devtools);

		bootVersion.setValue(older);
		assertFalse(devtoolsEnabled.getValue());

		bootVersion.setValue(newer1);
		assertTrue(devtoolsEnabled.getValue());

		bootVersion.setValue(newer2);
		assertTrue(devtoolsEnabled.getValue());
	}

	public void testVersionRanges() throws Exception {
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);

		Dependency bitronix = getDependencyById(model, "jta-bitronix");
		Dependency thymeleaf = getDependencyById(model, "thymeleaf");
		assertEquals("1.2.0.M1", bitronix.getVersionRange());
		assertFalse(StringUtils.hasText(thymeleaf.getVersionRange()));

		RadioGroup bootVersion = model.getBootVersion();
		assertNotNull(bootVersion);
		assertGroupValues(bootVersion,
		      "1.3.0.M2",
		      "1.3.0.BUILD-SNAPSHOT",
		      "1.2.6.BUILD-SNAPSHOT",
		      "1.2.5.RELEASE",
		      "1.1.12.RELEASE"
		);

		RadioInfo older = bootVersion.getRadio("1.1.12.RELEASE");
		RadioInfo newer = bootVersion.getRadio("1.2.5.RELEASE");

		LiveExpression<Boolean> bitronixEnabled  = getEnablement(model.dependencies, bitronix);
		LiveExpression<Boolean> thymeleafEnabled = getEnablement(model.dependencies, thymeleaf);

		bootVersion.setValue(older);
		assertFalse(bitronixEnabled.getValue());
		CheckBoxModel<Dependency> bitronixChekbox = getCheckboxById(model.dependencies.getAllBoxes(), "jta-bitronix");
		assertEquals("Requires Spring Boot >=1.2.0.M1", bitronixChekbox.getRequirementTooltip());
		assertTrue(thymeleafEnabled.getValue());

		bootVersion.setValue(newer);
		assertTrue(bitronixEnabled.getValue());
		assertTrue(thymeleafEnabled.getValue());

		bootVersion.setValue(older);

		Set<Dependency> selectedDepedencies = getSelecteds(model.dependencies);
		assertTrue(selectedDepedencies.isEmpty());
		select(model.dependencies, bitronix);
		select(model.dependencies, thymeleaf);

		selectedDepedencies = getSelecteds(model.dependencies);
		assertEquals(2, selectedDepedencies.size());

		String url = model.downloadUrl.getValue();
		assertContains("thymeleaf", url);
		assertFalse(url.contains("bitronix"));
	}

	public void testVersionRanges_V2_2() throws Exception {
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_V_2_2_JSON);

		Dependency acmeBiz = getDependencyById(model, "org.acme:biz");
		Dependency web = getDependencyById(model, "web");
		assertEquals("2.2.0.BUILD-SNAPSHOT", acmeBiz.getVersionRange());
		assertFalse(StringUtils.hasText(web.getVersionRange()));

		RadioGroup bootVersion = model.getBootVersion();
		assertNotNull(bootVersion);

		assertGroupValues(bootVersion,
		      "2.4.0-SNAPSHOT",
		      "2.1.4.RELEASE",
		      "1.5.17.RELEASE"
		);

		RadioInfo older = bootVersion.getRadio("1.5.17.RELEASE");
		RadioInfo newer = bootVersion.getRadio("2.4.0-SNAPSHOT");

		LiveExpression<Boolean> acmeBizEnabled  = getEnablement(model.dependencies, acmeBiz);
		LiveExpression<Boolean> webEnabled = getEnablement(model.dependencies, web);

		bootVersion.setValue(older);
		assertFalse(acmeBizEnabled.getValue());
		CheckBoxModel<Dependency> acmeBizChekbox = getCheckboxById(model.dependencies.getAllBoxes(), "org.acme:biz");
		assertEquals("Requires Spring Boot >=2.2.0.BUILD-SNAPSHOT", acmeBizChekbox.getRequirementTooltip());
		assertTrue(webEnabled.getValue());

		bootVersion.setValue(newer);
		assertTrue(acmeBizEnabled.getValue());
		assertTrue(webEnabled.getValue());

		bootVersion.setValue(older);

		Set<Dependency> selectedDepedencies = getSelecteds(model.dependencies);
		assertTrue(selectedDepedencies.isEmpty());
		select(model.dependencies, acmeBiz);
		select(model.dependencies, web);

		selectedDepedencies = getSelecteds(model.dependencies);
		assertEquals(2, selectedDepedencies.size());

		String url = model.downloadUrl.getValue();
		assertContains("web", url);
		assertFalse(url.contains("acme"));
	}

	public void testPopularityTracking() throws Exception {
		IPreferenceStore store = new MockPrefsStore();
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON, store);
		assertTrue(model.getMostPopular(10).isEmpty());
		Dependency web = getDependencyById(model, "web");
		Dependency actuator = getDependencyById(model, "actuator");
		Dependency thymeleaf = getDependencyById(model, "thymeleaf");

		select(model.dependencies, web);
		select(model.dependencies, thymeleaf);
		select(model.dependencies, actuator);

		model.updateUsageCounts();

		PopularityTracker tracker = new PopularityTracker(store);
		assertEquals(1, tracker.getUsageCount(web));
		assertEquals(1, tracker.getUsageCount(thymeleaf));
		assertEquals(1, tracker.getUsageCount(actuator));

		model.updateUsageCounts();
		assertEquals(2, tracker.getUsageCount(web));
		assertEquals(2, tracker.getUsageCount(thymeleaf));
		assertEquals(2, tracker.getUsageCount(actuator));

		unselect(model.dependencies, actuator);
		model.updateUsageCounts();

		unselect(model.dependencies, thymeleaf);
		model.updateUsageCounts();

		assertEquals(4, tracker.getUsageCount(web));
		assertEquals(3, tracker.getUsageCount(thymeleaf));
		assertEquals(2, tracker.getUsageCount(actuator));

		assertCheckboxes(model.getMostPopular(10),
				web, thymeleaf, actuator);

		/////////////////////////////////////////////////////////////////////////////
		// check that model counts are limitted according to 'howMany' argument

		assertCheckboxes(model.getMostPopular(3),
				web, thymeleaf, actuator);
		assertCheckboxes(model.getMostPopular(2),
				web, thymeleaf);
		assertCheckboxes(model.getMostPopular(1),
				web);
		assertCheckboxes(model.getMostPopular(0)
				/*nothing*/);

	}


	public void testPopularCheckboxSharesSelectionState() throws Exception {
		for (String f : testDataFiles) {
			IPreferenceStore store = new MockPrefsStore();
			NewSpringBootWizardModel model = parseFrom(f, store);
			assertTrue(model.getMostPopular(10).isEmpty());
			Dependency web = getDependencyById(model, "web");

			PopularityTracker tracker = new PopularityTracker(store);
			tracker.incrementUsageCount(web);

			String webCat = getCategory(model.dependencies, web);
			MultiSelectionFieldModel<Dependency> webGroup = model.dependencies.getContents(webCat);
			List<CheckBoxModel<Dependency>> allWebBoxes = webGroup.getCheckBoxModels();

			CheckBoxModel<Dependency> normalBox = getCheckboxById(allWebBoxes, web.getId());
			CheckBoxModel<Dependency> popularBox = getCheckboxById(model.getMostPopular(10), web.getId());

			assertFalse(normalBox.getSelection().getValue());
			assertFalse(popularBox.getSelection().getValue());

			normalBox.getSelection().setValue(true);
			assertTrue(normalBox.getSelection().getValue());
			assertTrue(popularBox.getSelection().getValue());

			popularBox.getSelection().setValue(false);
			assertFalse(normalBox.getSelection().getValue());
			assertFalse(popularBox.getSelection().getValue());
		}
	}

	public void testDefaultDependencies() throws Exception {
		IPreferenceStore store = new MockPrefsStore();
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON, store);
		assertTrue(model.getDefaultDependencies().isEmpty());
		select(model.dependencies, getDependencyById(model, "web"));
		select(model.dependencies, getDependencyById(model, "actuator"));
		select(model.dependencies, getDependencyById(model, "thymeleaf"));
		assertTrue(model.saveDefaultDependencies());

		// Initialize new model and check if default dependencies are selected
		model = parseFrom(INITIALIZR_JSON, store);
		assertEquals(3, model.getDefaultDependencies().size());

		Set<String> selectedIds = model.dependencies.getCurrentSelection().stream().map(Dependency::getId).collect(Collectors.toSet());
		Set<String> defaultIds = model.getDefaultDependencies().stream().map(checkboxModel -> {
			return checkboxModel.getValue().getId();
		}).collect(Collectors.toSet());
		assertEquals(defaultIds, selectedIds);
	}

	public void testUnselectAll() throws Exception {
		IPreferenceStore store = new MockPrefsStore();
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON, store);
		assertTrue(model.getDefaultDependencies().isEmpty());
		select(model.dependencies, getDependencyById(model, "web"));
		select(model.dependencies, getDependencyById(model, "actuator"));
		select(model.dependencies, getDependencyById(model, "thymeleaf"));
		assertEquals(3, model.dependencies.getCurrentSelection().size());

		model.dependencies.clearSelection();
		assertTrue(model.dependencies.getCurrentSelection().isEmpty());
	}

	public void testDependencySearchBox() throws Exception {
		// The trickies bit of implementing the search box is, unfortunately, making the
		// SWT gui widgetry apply the filter and hide / show corresponding ui elements.
		// This unfortunately not tested here. This test only verifies the filter's
		// matching logic.

		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);

		LiveVariable<String> searchBox = model.getDependencyFilterBoxText();
		LiveExpression<Filter<Dependency>> filter = model.getDependencyFilter();

		assertEquals("", searchBox.getValue());
		assertEquals(Filters.acceptAll(),filter.getValue());

		assertFilterAccepts(model, true, "web", "web", "Web", "Full stack yada yada");
		assertFilterAccepts(model, true, "WeB", "web", "Web", "Full stack yada yada");
		assertFilterAccepts(model, false, "ZZZZZZZZZZ", "web", "Web", "Full stack yada yada");

		assertFilterAccepts(model, true, "foo", "something", "label FoO label", "desc");
		assertFilterAccepts(model, true, "foo", "something", "label", "desc FOO desc");
		assertFilterAccepts(model, false, "foo", "foo", "label", "desc");

	}

	public void testValidDefaultProjectName() throws Exception {
		ensureProject("demo");
		ensureProject("demo-1");
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		assertEquals("demo-2", model.getProjectName().getValue());
		assertEquals("demo-2", model.getArtifactId().getValue());
		assertEquals(ValidationResult.OK, model.getProjectName().getValidator().getValue());
	}

	private void ensureProject(String name) throws CoreException {
		IProject p1 = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (!p1.exists()) {
			p1.create(new NullProgressMonitor());
		}
	}

	public void testArtifactIdSyncWithProjectName() throws Exception {
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		assertEquals("demo", model.getProjectName().getValue());
		assertEquals("demo", model.getArtifactId().getValue());

		model.getProjectName().setValue("demo-1");
		assertEquals("demo-1", model.getProjectName().getValue());
		assertEquals("demo-1", model.getArtifactId().getValue());

		model.getArtifactId().setValue("demo-project");
		assertEquals("demo-1", model.getProjectName().getValue());
		assertEquals("demo-project", model.getArtifactId().getValue());

		model.getProjectName().setValue("demo-2");
		assertEquals("demo-2", model.getProjectName().getValue());
		assertEquals("demo-project", model.getArtifactId().getValue());

		model.getArtifactId().setValue("demo-2");
		assertEquals("demo-2", model.getProjectName().getValue());
		assertEquals("demo-2", model.getArtifactId().getValue());

		model.getProjectName().setValue("demo-3");
		assertEquals("demo-3", model.getProjectName().getValue());
		assertEquals("demo-3", model.getArtifactId().getValue());
	}

	public void testProjectNameDefaultsToArtifactIdWhenNameDefaultIsBlank() throws Exception {
		//When initializr specifies a blank (empty string, not null) default for the 'name'
		//field, the project name should be seeded from the artifactId's default rather than
		//starting out blank (which used to cause NameGenerator to produce odd names like
		//"-1", "-2", ...).
		NewSpringBootWizardModel model = parseFrom("initializr-blank-name.json");

		assertEquals("demo", model.getArtifactId().getValue());
		assertEquals("demo", model.getProjectName().getValue());
		assertEquals(ValidationResult.OK, model.getProjectName().getValidator().getValue());
	}

	public void testProjectNameDefaultsToArtifactIdWhenNameHasNoDefault() throws Exception {
		//When initializr doesn't specify a default for the 'name' field at all, the project
		//name should be seeded from the artifactId's default instead of causing the wizard
		//model to fail to initialize.
		NewSpringBootWizardModel model = parseFrom("initializr-no-name-default.json");

		assertEquals("demo", model.getArtifactId().getValue());
		assertEquals("demo", model.getProjectName().getValue());
	}

	public void testProjectNameSavedAndRestored() throws Exception {
		//See: https://www.pivotaltracker.com/story/show/145645973
		IPreferenceStore prefs = new MockPrefsStore();
		{
			NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON, prefs);
			model.getProjectName().setValue("another-name");
			model.performFinish(new NullProgressMonitor());
			assertEquals("another-name", model.getProjectName().getValue());
		}

		{
			NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON, prefs);
			assertEquals("another-name", model.getProjectName().getValue());
		}

		ensureProject("another-name");
		{
			NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON, prefs);
			assertEquals("another-name-1", model.getProjectName().getValue());
		}
	}

	public void testRestoreFromOldBuildtypePreference() throws Exception {
		//We here test a weak requirement that the wizard model 'handles' it okay when
		// the preference saved in the workspace is from before the buildship refactoring.
		//The requirements are:
		// - it doesn't crash
		// - it just chooses the maven build type / strategy by default. We do not
		//   attempt to map the old type-ids to the new ones.
		MockPrefsStore prefs = new MockPrefsStore();
		prefs.putValue("org.springframework.ide.eclipse.wizard.gettingstarted.boot.PreferredSelections.type", "gradle-project");

		NewSpringBootWizardModel wizard = parseFrom(INITIALIZR_JSON, prefs);
		assertEquals(BuildType.MAVEN, wizard.getBuildType());
		assertEquals(BuildType.MAVEN.getDefaultStrategy(), wizard.getImportStrategy());
	}

//	private CheckBoxModel<Dependency> getCheckboxById(NewSpringBootWizardModel model, String id) {
//		CheckBoxModel<Dependency> found = null;
//		for (String cat : model.dependencies.getCategories()) {
//			for (CheckBoxModel<Dependency> cb : model.dependencies.getContents(cat).getCheckBoxModels()) {
//				if (cb.getValue().getId().equals(id)) {
//					Assert.assertNull(found);
//					found = cb;
//				}
//			}
//		}
//		return found;
//	}

	private void assertFilterAccepts(NewSpringBootWizardModel model, boolean expect, String pattern,
			String id, String label, String desc) {
		LiveVariable<String> searchBox = model.getDependencyFilterBoxText();
		searchBox.setValue(pattern);
		Filter<Dependency> filter = model.getDependencyFilter().getValue();

		Dependency dep = new Dependency();
		dep.setId(id);
		dep.setName(label);
		dep.setDescription(desc);

		assertEquals(expect, filter.accept(dep));
	}

	private CheckBoxModel<Dependency> getCheckboxById(List<CheckBoxModel<Dependency>> list, String id) {
		for (CheckBoxModel<Dependency> cb : list) {
			if (id.equals(cb.getValue().getId())) {
				return cb;
			}
		}
		return null;
	}

	private void assertCheckboxes(List<CheckBoxModel<Dependency>> actuals, Dependency... expecteds) {
		StringBuilder expectedIds = new StringBuilder();
		for (Dependency e : expecteds) {
			expectedIds.append(e.getId()+"\n");
		}
		StringBuilder actualIds = new StringBuilder();
		for (CheckBoxModel<Dependency> cb : actuals) {
			actualIds.append(cb.getValue().getId()+"\n");
		}
		assertEquals(expectedIds.toString(), actualIds.toString());
	}

	private void unselect(HierarchicalMultiSelectionFieldModel<Dependency> dependencies,
			Dependency dep) {
			String cat = getCategory(dependencies, dep);
			MultiSelectionFieldModel<Dependency> selecteds = dependencies.getContents(cat);
			selecteds.unselect(dep);
	}

	private void select(HierarchicalMultiSelectionFieldModel<Dependency> dependencies,
			Dependency dep) {
		String cat = getCategory(dependencies, dep);
		MultiSelectionFieldModel<Dependency> selecteds = dependencies.getContents(cat);
		selecteds.select(dep);
	}

	private Set<Dependency> getSelecteds(HierarchicalMultiSelectionFieldModel<Dependency> dependencies) {
		HashSet<Dependency> selecteds = new HashSet<>();
		for (String catName : dependencies.getCategories()) {
			MultiSelectionFieldModel<Dependency> cat = dependencies.getContents(catName);
			selecteds.addAll(cat.getCurrentSelection());
		}
		return selecteds;
	}

	private LiveExpression<Boolean> getEnablement(HierarchicalMultiSelectionFieldModel<Dependency> dependencies, Dependency dep) {
		String cat = getCategory(dependencies, dep);
		return dependencies.getContents(cat).getEnablement(dep);
	}

	private String getCategory( HierarchicalMultiSelectionFieldModel<Dependency> dependencies, Dependency dep) {
		for (String cat : dependencies.getCategories()) {
			if (contains(dependencies.getContents(cat).getChoices(), dep)) {
				return cat;
			}
		}
		throw new Error("Shouldn't get here");
	}

	private boolean contains(Dependency[] deps, Dependency find) {
		for (Dependency dep : deps) {
			if (find.equals(dep)) {
				return true;
			}
		}
		return false;
	}

	public void assertContains(String needle, String haystack) {
		if (haystack==null || !haystack.contains(needle)) {
			fail("Not found: "+needle+"\n in \n"+haystack);
		}
 	}

	private Dependency getDependencyById(NewSpringBootWizardModel model, String depId) {
		for (Dependency dep : getAllChoices(model.dependencies)) {
			if (dep.getId().equals(depId)) {
				return dep;
			}
		}
		return null;
	}

	public void testLabels() throws Exception {
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		Iterator<FieldModel<String>> stringInputs = model.stringInputs.iterator();
		while (stringInputs.hasNext()) {
			FieldModel<String> input = stringInputs.next();
			assertRealLabel(input.getLabel());
		}

		for (RadioGroup group : model.getRadioGroups().getGroups()) {
			String label = group.getLabel();
			assertRealLabel(label);

			for (RadioInfo radio : group.getRadios()) {
				label = radio.getLabel();
				assertRealLabel(label);
			}
		}
	}

	public void testPrintLabels() throws Exception {
		//print all radios in groups with lable for quick visual inspection.
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		for (RadioGroup group : model.getRadioGroups().getGroups()) {
			String label = group.getLabel();
			System.out.println(group + " -> "+label);

			for (RadioInfo radio : group.getRadios()) {
				label = radio.getLabel();
				System.out.println("  " + radio + " -> "+label);
			}
		}

	}

	/**
	 * Basic test of the 'spec parser'. Just check that it accepts a well-formed
	 * json spec document and doesn't crash on it.
	 */
	public void testInitializrSpecParser() throws Exception {
		for (String f : testDataFiles) {
			doParseTest(f);
		}
	}

	private void doParseTest(String resource) throws IOException, Exception {
		URL url = NewSpringBootWizardModelTest.class.getResource(resource);
		URLConnection conn = new URLConnectionFactory().createConnection(url);
		conn.connect();
		InputStream input = conn.getInputStream();
		try {
			InitializrServiceSpec spec = InitializrServiceSpec.parseFrom(input);
			assertNotNull(spec);
		} finally {
			input.close();
		}
	}

	/**
	 * Test that radio params are wired up in the model so that selecting them changes the downloadUrl.
	 */
	public void testRadioQueryParams() throws Exception {
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		RadioGroup packaging = model.getRadioGroups().getGroup("packaging");
		LiveVariable<RadioInfo> selection = packaging.getSelection().selection;

		assertEquals("jar", selection.getValue().getValue());
		String urlParam = getUrlParam(model.downloadUrl.getValue(), "packaging");
		assertEquals("jar", urlParam);

		selection.setValue(packaging.getRadio("war"));
		urlParam = getUrlParam(model.downloadUrl.getValue(), "packaging");
		assertEquals("war", urlParam);
	}

	/**
	 * Test that radio params for 'type' properly use the typenames from start.spring.io spec
	 * and not the ids used internally to distinguish between grade+buildship versus gradle+sts
	 */
	public void testTypeRadioQueryParams() throws Exception {
		NewSpringBootWizardModel model = parseFrom(INITIALIZR_JSON);
		RadioGroup type = model.getRadioGroups().getGroup("type");
		LiveVariable<RadioInfo> selection = type.getSelection().selection;

		assertEquals("MAVEN", selection.getValue().getValue());
		String urlParam = getUrlParam(model.downloadUrl.getValue(), "type");
		assertEquals("maven-project", urlParam);

		selection.setValue(type.getRadio("GRADLE"));
		urlParam = getUrlParam(model.downloadUrl.getValue(), "type");
		assertEquals("gradle-project", urlParam);
}

	public static Map<String, List<String>> getQueryParams(String url) throws Exception {
        Map<String, List<String>> params = new HashMap<>();
        String[] urlParts = url.split("\\?");
        if (urlParts.length > 1) {
            String query = urlParts[1];
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                String key = URLDecoder.decode(pair[0], "UTF-8");
                String value = "";
                if (pair.length > 1) {
                    value = URLDecoder.decode(pair[1], "UTF-8");
                }

                List<String> values = params.get(key);
                if (values == null) {
                    values = new ArrayList<>();
                    params.put(key, values);
                }
                values.add(value);
            }
        }

        return params;
	}

	private String getUrlParam(String url, String name) throws Exception {
		Map<String, List<String>> params = getQueryParams(url);
		List<String> values = params.get(name);
		if (values!=null && !values.isEmpty()) {
			assertEquals(1, values.size());
			return values.get(0);
		}
		return null;
	}

	private void assertRealLabel(String label) {
		assertNotNull("Label is null", label); //have a label
		assertFalse("Label is empty", "".equals(label.trim())); //label not empty
		if (Character.isDigit(label.charAt(0))) {
			//labels like '1.6.' are okay too.
			return;
		}
		assertTrue("Label doesn't start with uppercase: '"+label+"'", Character.isUpperCase(label.charAt(0))); //'real' label, not just the default taken from the name.
	}

	private void assertGroupValues(RadioGroup group, String... expecteds) {
		Set<String> expectedSet = new HashSet<>(Arrays.asList(expecteds));
		RadioInfo[] radios = group.getRadios();
		StringBuilder found = new StringBuilder();
		for (int i = 0; i < radios.length; i++) {
			String actual = radios[i].getValue();
			found.append(" "+actual);
			if (!expectedSet.contains(actual)) {
				fail("Unexpected: "+actual);
			}
			expectedSet.remove(actual);
		}
		if (!expectedSet.isEmpty()) {
			StringBuilder notFound = new StringBuilder();
			for (String missing : expectedSet) {
				notFound.append(" "+missing);
			}
			fail("Missing: "+notFound+"\n Found: "+found);
		}
	}

	private void assertGroupNames(RadioGroups radioGroups, String... expectNames) {
		List<RadioGroup> groups = radioGroups.getGroups();
		assertEquals(expectNames.length, groups.size());
		for (int i = 0; i < expectNames.length; i++) {
			assertEquals(expectNames[i], groups.get(i).getName());
		}
	}

	public void testTemplateVariableSubstitution() throws Exception {
		Map<String, String> values = new HashMap<>();
		values.put("bootVersion", "2.0.0");
		values.put("stars", "5");

		String actual = InitializrServiceSpec.substituteTemplateVariables("Here is Spring Boot {bootVersion} version. It's rated with {stars} stars.", values);
		assertEquals("Here is Spring Boot 2.0.0 version. It's rated with 5 stars.", actual);

		actual = InitializrServiceSpec.substituteTemplateVariables("Here is Spring Boot {bootVersion} version.", values);
		assertEquals("Here is Spring Boot 2.0.0 version.", actual);

		try {
			InitializrServiceSpec.substituteTemplateVariables("Here is Spring Boot {bootVersion} version. It's really {awesome} stuff!", values);
			fail("Should have failed the template string variable substitution! Unknown variable expected!");
		} catch (CoreException e) {
			// ignore - let the test pass
		}
	}

}
