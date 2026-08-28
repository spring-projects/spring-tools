/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.assertElements;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElement;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.RunTargets;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel.FilterChoice;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetTypes;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.TargetProperties;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockBootDashModel;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class BootDashViewModelTest {

	private TestBootDashModelContext context;
	private BootDashViewModelHarness harness = null;
	private BootProjectTestHarness projects = new BootProjectTestHarness(ResourcesPlugin.getWorkspace());

	@Before
	public void setup() throws Exception {
		context =  new TestBootDashModelContext(
				ResourcesPlugin.getWorkspace(),
				DebugPlugin.getDefault().getLaunchManager()
		);
		StsTestUtil.cleanUpProjects();
	}

	@After
	public void teardown() throws Exception {
		if (harness != null) {
			harness.dispose();
		}
	}

	@Test
	public void testCreate() throws Exception {
		harness = new BootDashViewModelHarness(context.withTargetTypes(RunTargetTypes.LOCAL));
		BootDashModel localModel = harness.getRunTargetModel(RunTargetTypes.LOCAL);
		assertNotNull(localModel);

		assertElements(harness.model.getRunTargets().getValues(),
				RunTargets.LOCAL
		);
	}

	@Test
	public void testGetTargetTypes() throws Exception {
		RunTargetType targetType = mock(RunTargetType.class);
		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL,
				targetType
		));

		assertElements(harness.model.getRunTargetTypes(),
				RunTargetTypes.LOCAL,
				targetType
		);

	}

	@Test
	public void testAddAndRemoveRunTarget() throws Exception {
		RunTargetType targetType = mock(RunTargetType.class);
		RunTarget target = mock(RunTarget.class);
		BootDashModel bootDashModel = mock(BootDashModel.class);

		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL,
				targetType
		));

		assertEquals(0, harness.getRunTargetModels(targetType).size());

		when(target.getId()).thenReturn("target_id");
		when(target.getType()).thenReturn(targetType);
		when(target.createSectionModel(harness.model)).thenReturn(bootDashModel);

		when(targetType.canInstantiate()).thenReturn(true);
		when(targetType.createRunTarget(any(TargetProperties.class))).thenReturn(target);

		when(bootDashModel.getRunTarget()).thenReturn(target);

		LiveSetVariable<RunTarget> runTargets = harness.model.getRunTargets();

		//Adding...
		runTargets.add(target);
		List<BootDashModel> models = harness.getRunTargetModels(targetType);
		assertEquals(1, models.size());

		BootDashModel targetModel = models.get(0);
		assertEquals(target, targetModel.getRunTarget());

		//Removing...
		runTargets.remove(target);

		models = harness.getRunTargetModels(targetType);
		assertEquals(0, models.size());

		assertEquals(1, harness.getRunTargetModels(RunTargetTypes.LOCAL).size());
	}

	@Test
	public void testElementStateListenerAddedAfterModel() throws Exception {
		RunTargetType targetType = mock(RunTargetType.class);
		RunTarget target = mock(RunTarget.class);
		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL,
				targetType
		));
		//We need a more fleshed-out BootDashModel mock for this test, so not using mockito here:
		MockBootDashModel bdm = new MockBootDashModel(target, harness.context, harness.model);

		when(target.getId()).thenReturn("target_id");
		when(target.getType()).thenReturn(targetType);
		when(target.createSectionModel(harness.model)).thenReturn(bdm);

		//////Add target///////

		harness.model.getRunTargets().add(target);

		//Make sure the model got added as expected
		List<BootDashModel> models = harness.getRunTargetModels(targetType);
		assertEquals(1, models.size());

		BootDashElement element = mock(BootDashElement.class);
		bdm.add(element);

		/////Add listener////////

		ElementStateListener listener = mock(ElementStateListener.class);
		harness.model.addElementStateListener(listener);

		////Fire event///////////

		bdm.notifyElementChanged(element, "FIRST event fired by the test");

		/////Verify listener

		verify(listener).stateChanged(element);

		////////////////////////////////////////////////////////////////////////////

		reset(listener);

		harness.model.removeElementStateListener(listener);
		bdm.notifyElementChanged(element, "SECOND event fired by the test");

		verifyZeroInteractions(listener);

	}

	@Test
	public void testElementStateListenerAddedBeforeModel() throws Exception {
		RunTargetType targetType = mock(RunTargetType.class);
		RunTarget target = mock(RunTarget.class);
		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL,
				targetType
		));
		//We need a more fleshed-out BootDashModel mock for this test, so not using mockito here:
		MockBootDashModel bdm = new MockBootDashModel(target, harness.context, harness.model);

		when(target.getId()).thenReturn("target_id");
		when(target.getType()).thenReturn(targetType);
		when(target.createSectionModel(harness.model)).thenReturn(bdm);

		/////Add listener////////

		ElementStateListener listener = mock(ElementStateListener.class);
		harness.model.addElementStateListener(listener);

		//////Add target///////

		harness.model.getRunTargets().add(target);

		//Make sure the model got added as expected
		List<BootDashModel> models = harness.getRunTargetModels(targetType);
		assertEquals(1, models.size());

		BootDashElement element = mock(BootDashElement.class);
		bdm.add(element);

		////Fire event///////////

		bdm.notifyElementChanged(element, "event fired by the test");

		/////Verify listener

		verify(listener).stateChanged(element);
	}

	@Test
	public void testRemoveTargetToleratesNull() throws Exception {
		UserInteractions ui = mock(UserInteractions.class);
		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL
		));
		harness.model.removeTarget(null, ui);

		verifyZeroInteractions(ui);
	}

	@Test
	public void testRemoveTargetCanceled() throws Exception {
		RunTargetType targetType = mock(RunTargetType.class);
		RunTarget target = mock(RunTarget.class);
		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL,
				targetType
		));
		BootDashModel bdm = new MockBootDashModel(target, harness.context, harness.model);

		when(target.getId()).thenReturn("target_id");
		when(target.getName()).thenReturn("target_name");
		when(target.getType()).thenReturn(targetType);
		when(target.createSectionModel(harness.model)).thenReturn(bdm);

		//////Add target///////
		harness.model.getRunTargets().add(target);

		//Make sure the model got added as expected
		List<BootDashModel> models = harness.getRunTargetModels(targetType);
		assertEquals(1, models.size());

		/////Remove target operation CANCELED ////

		UserInteractions ui = mock(UserInteractions.class);
		when(ui.confirmOperation(
			contains("Deleting run target: target_name"),
			contains("Are you sure")
		)).thenReturn(false);

		harness.model.removeTarget(target, ui);

		//Since user canceled, the target should NOT actually have been removed
		assertTrue(harness.model.getRunTargets().contains(target));

//		verify(ui).confirmOperation(anyString(), anyString());
//		verifyNoMoreInteractions(ui);
	}

	@Test
	public void testRemoveTargetConfirmed() throws Exception {
		RunTargetType targetType = mock(RunTargetType.class);
		RunTarget target = mock(RunTarget.class);
		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL,
				targetType
		));
		BootDashModel bdm = new MockBootDashModel(target, harness.context, harness.model);

		when(target.getId()).thenReturn("target_id");
		when(target.getName()).thenReturn("target_name");
		when(target.getType()).thenReturn(targetType);
		when(target.createSectionModel(harness.model)).thenReturn(bdm);

		//////Add target///////
		harness.model.getRunTargets().add(target);

		//Make sure the model got added as expected
		List<BootDashModel> models = harness.getRunTargetModels(targetType);
		assertEquals(1, models.size());

		/////Remove target operation CANCELED ////

		UserInteractions ui = mock(UserInteractions.class);
		when(ui.confirmOperation(
			contains("Deleting run target: target_name"),
			contains("Are you sure")
		)).thenReturn(true);

		harness.model.removeTarget(target, ui);

		//Since user confirmed, the target should have been removed
		assertFalse(harness.model.getRunTargets().contains(target));

//		verify(ui).confirmOperation(anyString(), anyString());
//		verifyNoMoreInteractions(ui);
	}


	@Test
	public void testRemoveTargetToleratesRemovingNonContainedElement() throws Exception {
		RunTargetType targetType = mock(RunTargetType.class);
		RunTarget target = mock(RunTarget.class);
		RunTarget otherTarget = mock(RunTarget.class);

		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL,
				targetType
		));
		BootDashModel bdm = new MockBootDashModel(target, harness.context, harness.model);

		when(target.getId()).thenReturn("target_id");
		when(target.getName()).thenReturn("target_name");
		when(target.getType()).thenReturn(targetType);
		when(target.createSectionModel(harness.model)).thenReturn(bdm);

		when(otherTarget.getId()).thenReturn("other_id");

		//////Add target///////
		harness.model.getRunTargets().add(target);

		//Make sure the model got added as expected
		List<BootDashModel> models = harness.getRunTargetModels(targetType);
		assertEquals(1, models.size());

		/////Remove target operation ////

		UserInteractions ui = mock(UserInteractions.class);

		ImmutableSet<RunTarget> targetsBefore = harness.getRunTargets();
		int numTargetsBefore = targetsBefore.size();

		harness.model.removeTarget(otherTarget, ui);
		ImmutableSet<RunTarget> targetsAfter = harness.getRunTargets();

		//Since target is not in the model, nothing should happen.
		assertEquals(targetsBefore, targetsAfter);
		assertEquals(numTargetsBefore, targetsAfter.size());
		verifyZeroInteractions(ui);

//		verify(ui).confirmOperation(anyString(), anyString());
//		verifyNoMoreInteractions(ui);
	}

	@Test
	public void testFilterBox() throws Exception {
		//Note: this test check the relationship between
		// filterBox and filter. It does so indirectly by observing
		// that changing the filterText makes the filter behave as expected.
		//However, it is not (intended to be) an in-depth test of the way the
		// filter matches elements. So it only does some basic test cases.
		//There are more in-depth tests for the filters elsewhere.

		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL
		));

		LiveVariable<String> filterText = harness.model.getFilterBox().getText();
		LiveExpression<Filter<BootDashElement>> filter = harness.model.getFilter();

		assertEquals("", filterText.getValue());
		assertFilterAccepts(true, filter, "a-tag");

		filterText.setValue("foo");
		assertFilterAccepts(false, filter, "a-tag");
		assertFilterAccepts(true, filter, "foo");
	}

	@Test
	public void testToggleFilters() throws Exception {
		//Note: this test check the relationship between
		// filterBox and filter. It does so indirectly by observing
		// that changing the filterText makes the filter behave as expected.
		//However, it is not (intended to be) an in-depth test of the way the
		// filter matches elements. So it only does some basic test cases.
		//There are more in-depth tests for the filters elsewhere.

		harness = new BootDashViewModelHarness(context.withTargetTypes(
				RunTargetTypes.LOCAL
		));

		LiveSetVariable<FilterChoice> toggleFilters = harness.model.getToggleFilters().getSelectedFilters();
		toggleFilters.replaceAll(ImmutableSet.<FilterChoice>of());
		LiveExpression<Filter<BootDashElement>> filter = harness.model.getFilter();

		assertTrue(toggleFilters.getValue().isEmpty());
		assertFilterAccepts(true, filter, "a-tag");

		FilterChoice toggleFilter = new FilterChoice("foo", "Foo filter", "description", new Filter<BootDashElement>() {
			public boolean accept(BootDashElement t) {
				return t.getTags().contains("foo");
			}
		});
		toggleFilters.add(toggleFilter);
		assertFilterAccepts(false, filter, "app-name", "a-tag");
		assertFilterAccepts(true, filter, "app-name", "foo");
	}

	@Test
	public void testFilterIsTreeAware() throws Exception {
		harness = new BootDashViewModelHarness(context.reload().withTargetTypes(
				RunTargetTypes.LOCAL
		));

		LiveVariable<String> filterBox = harness.model.getFilterBox().getText();
		LiveExpression<Filter<BootDashElement>> filter = harness.model.getFilter();

		IProject project = projects.createBootProject("parent");
		ILaunchConfiguration conf1 = BootLaunchConfigurationDelegate.createConf(project);
		ILaunchConfiguration conf2 = BootLaunchConfigurationDelegate.createConf(project);

		BootProjectDashElement vader = harness.getElementFor(project);
		BootDashElement luke = harness.getElementFor(conf1);
		BootDashElement leia = harness.getElementFor(conf2);

		setTags(vader, "vader");
		setTags(luke, "luke");
		setTags(leia, "leia");

		//Blank filter set, filter matches anything
		filterBox.setValue("");
		assertFilterAccepts(true, filter, vader, luke, leia);

		//Filter non-existing tag matches nothing
		filterBox.setValue("not-exist");
		assertFilterAccepts(false, filter, vader, luke, leia);

		//Filter matching a parent also causes children to match
		filterBox.setValue("vader");
		assertFilterAccepts(true, filter, vader, luke, leia);

		//Filter matching a child also causes parent to match...
		filterBox.setValue("luke");
		assertFilterAccepts(true, filter, vader, luke);
		//... but not siblings
		assertFilterAccepts(false, filter, leia);

	}

	private void assertFilterAccepts(boolean expected, LiveExpression<Filter<BootDashElement>> filter, BootDashElement... elements) {
		for (BootDashElement bde : elements) {
			assertEquals("element '"+bde.getName()+"'with tags "+bde.getTags(), expected, filter.getValue().accept(bde));
		}
	}

	private void setTags(BootDashElement element, String... tags) {
		element.setTags(new LinkedHashSet<>(Arrays.asList(tags)));
	}

	private void assertFilterAccepts(boolean expectedAccept, LiveExpression<Filter<BootDashElement>> filter, String elementName, String... tags) {
		BootDashElement element = mock(BootDashElement.class);
		when(element.getName()).thenReturn(elementName);
		when(element.getCurrentChildren()).thenReturn(ImmutableSet.<BootDashElement>of());
		when(element.getTags()).thenReturn(new LinkedHashSet<>(Arrays.asList(tags)));
		assertEquals(expectedAccept, filter.getValue().accept(element));
	}

	@Test
	public void testGetSectionByTargetId() throws Exception {
		BootDashViewModel view = mock(BootDashViewModel.class);
		LiveSetVariable<BootDashModel> sections = new LiveSetVariable<>();
		when(view.getSectionModels()).thenReturn(sections);
		when(view.getSectionByTargetId(anyString())).thenCallRealMethod();

		assertNull(view.getSectionByTargetId("some-id"));

		BootDashModel bdm = mockBDMWithTargetId("some-id");
		BootDashModel other_bdm = mockBDMWithTargetId("other-id");

		sections.add(bdm);
		sections.add(other_bdm);

		assertEquals(bdm,       view.getSectionByTargetId("some-id"));
		assertEquals(other_bdm, view.getSectionByTargetId("other-id"));
		assertEquals(null,      view.getSectionByTargetId("not-found-id"));
	}

	private BootDashModel mockBDMWithTargetId(String id) {
		BootDashModel bdm = mock(BootDashModel.class);
		RunTarget target = mock(RunTarget.class);
		when(bdm.getRunTarget()).thenReturn(target);
		when(target.getId()).thenReturn(id);
		return bdm;
	}


	@Test
	public void testModelComparator() throws Exception {
		//View model is expected to provide a comparator that is based on
		// the list of target-types it is initialized with. It should sort
		//models based on type first then runtarget id second.

		context.withTargetTypes(RunTargetTypes.LOCAL);
		harness = new BootDashViewModelHarness(context);

		Comparator<BootDashModel> comparator = harness.model.getModelComparator();

		RunTargetType fooType = context.getRargetTypeWithName("foo-type");
		RunTargetType barType = context.getRargetTypeWithName("bar-type");

		BootDashModel[] sortedModels = {
			//These are arranged in the order we expect them to be when
			// sorted properly

			harness.getRunTargetModel(RunTargetTypes.LOCAL), //first!

			//foo comes before bar (not alphabetic but based on BDVM construction)
			sortableModel(fooType, "different"), //ids are alphabetic within each type
			sortableModel(fooType, "other"),
			sortableModel(fooType, "something"),

			sortableModel(barType, "different"),
			sortableModel(barType, "other"),
			sortableModel(barType, "something")
		};

		//Create a array with same elements in the wrong order...
		int len = sortedModels.length;
		BootDashModel[] reverseModels = new BootDashModel[len];
		for (int i = 0; i < reverseModels.length; i++) {
			reverseModels[i] = sortedModels[len - i - 1];
		}

		assertFalse(reverseModels[0].equals(sortedModels[0])); //Sanity check

		Arrays.sort(reverseModels, comparator);

		assertArrayEquals(sortedModels, reverseModels);
	}

	/**
	 * Create mock BootDashModel that is sufficiently fleshed-out to
	 * allow sorting using the comparator provided by BootDashViewModel
	 */
	private BootDashModel sortableModel(RunTargetType type, String displayName) {
		BootDashModel model = mock(BootDashModel.class);
		RunTarget target = mock(RunTarget.class);

		when(target.getType()).thenReturn(type);
		when(model.getRunTarget()).thenReturn(target);
		when(target.getDisplayName()).thenReturn(displayName);

		return model;
	}

}
