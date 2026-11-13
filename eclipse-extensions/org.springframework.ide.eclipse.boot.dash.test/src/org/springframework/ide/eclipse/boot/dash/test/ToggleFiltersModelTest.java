/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElement;
import org.springframework.ide.eclipse.boot.dash.model.LaunchConfDashElement;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel.FilterChoice;
import org.springsource.ide.eclipse.commons.core.pstore.InMemoryPropertyStore;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;

import com.google.common.collect.ImmutableSet;

@SuppressWarnings("unchecked")
public class ToggleFiltersModelTest {

	private static final String HIDE_NON_WORKSPACE_ELEMENTS = "Hide non-workspace elements";
	private static final String HIDE_SOLITARY_CONF = "Hide solitary launch configs";
	private static final String HIDE_NOT_RUNNABLE_APPS = "Hide local non-runnable apps";

	private InMemoryPropertyStore propertyStore = new InMemoryPropertyStore();

	@Test
	public void testAvailableFilters() throws Exception {
		ToggleFiltersModel model = new ToggleFiltersModel(propertyStore);
		assertThat(model.getAvailableFilters(),
			arrayContaining(
					hasToString("FilterChoice("+HIDE_NON_WORKSPACE_ELEMENTS+")"),
					hasToString("FilterChoice("+HIDE_SOLITARY_CONF+")"),
					hasToString("FilterChoice("+HIDE_NOT_RUNNABLE_APPS+")")
			)
		);
	}

	@Test
	public void testRemovedLocalServicesFilterIsIgnored() throws Exception {
		propertyStore.put("hide.local-cloud-services", "true");
		propertyStore.put(ToggleFiltersModel.FILTER_CHOICE_HIDE_NON_WORKSPACE_ELEMENTS.getId(), "true");

		ToggleFiltersModel model = new ToggleFiltersModel(propertyStore);

		assertEquals(ImmutableSet.of(
				ToggleFiltersModel.FILTER_CHOICE_HIDE_SOLITARY_CONFS,
				ToggleFiltersModel.FILTER_CHOICE_HIDE_NON_WORKSPACE_ELEMENTS), model.getSelectedFilters().getValues());
	}

	@Test
	public void testHideNonWorkspaceElementsToleratesNull() throws Exception {
		//Case: element == null
		//  Basically this shouldn't happen, but if it does, the filter does
		//  something sensible.

		Filter<BootDashElement> f = getFilter(HIDE_NON_WORKSPACE_ELEMENTS);

		assertEquals(false, f.accept(null));
	}

	@Test
	public void testHideNonWorkspaceElementsNullProject() throws Exception {
		//Case: project == null means not associated with ws project

		Filter<BootDashElement> f = getFilter(HIDE_NON_WORKSPACE_ELEMENTS);

		BootDashElement e = mock(BootDashElement.class);
		when(e.getProject()).thenReturn(null); // unnessary, but just for clarity
		assertEquals(false, f.accept(e));
	}

	@Test
	public void testHideNonWorkspaceElementsProjectNoExist() throws Exception {
		//Case 2" project that doesn't exist.
		// => may have been associated with a project, but project no longer in workspace

		Filter<BootDashElement> f = getFilter(HIDE_NON_WORKSPACE_ELEMENTS);
		BootDashElement e = mock(BootDashElement.class);
		IProject p = mock(IProject.class);

		when(e.getProject()).thenReturn(p);
		when(p.exists()).thenReturn(false);

		assertEquals(false, f.accept(e));
	}

	@Test
	public void testHideNonWorkspaceElementsProjectExist() throws Exception {
		//Case 2" project that doesn't exist.
		// => may have been associated with a project, but project no longer in workspace

		Filter<BootDashElement> f = getFilter(HIDE_NON_WORKSPACE_ELEMENTS);
		BootDashElement e = mock(BootDashElement.class);
		IProject p = mock(IProject.class);

		when(e.getProject()).thenReturn(p);
		when(p.exists()).thenReturn(true);

		assertEquals(true, f.accept(e));
	}

	@Test
	public void testHideSolitaryConfEnabledByDefault() throws Exception {
		ToggleFiltersModel model = new ToggleFiltersModel(propertyStore);
		assertThat(model.getAvailableFilters(),
				hasItemInArray(
						hasToString("FilterChoice("+HIDE_SOLITARY_CONF+")")
				)
		);
	}

	@Test
	public void testHideSolitaryConf() throws Exception {
		Filter<BootDashElement> filter = getFilter(HIDE_SOLITARY_CONF);
		BootProjectDashElement project = mock(BootProjectDashElement.class);
		LaunchConfDashElement conf1 = mock(LaunchConfDashElement.class);

		when(conf1.getParent()).thenReturn(project);
		when(project.getCurrentChildren()).thenReturn(ImmutableSet.<BootDashElement>of(conf1));

		assertTrue(filter.accept(project));
		assertFalse(filter.accept(conf1));
	}

	@Test
	public void testShowNonSolitaryConf() throws Exception {
		Filter<BootDashElement> filter = getFilter(HIDE_SOLITARY_CONF);
		BootProjectDashElement project = mock(BootProjectDashElement.class);
		LaunchConfDashElement conf1 = mock(LaunchConfDashElement.class);
		LaunchConfDashElement conf2 = mock(LaunchConfDashElement.class);

		when(conf1.getParent()).thenReturn(project);
		when(conf2.getParent()).thenReturn(project);

		when(project.getCurrentChildren()).thenReturn(ImmutableSet.<BootDashElement>of(conf1, conf2));

		assertTrue(filter.accept(project));
		assertTrue(filter.accept(conf1));
		assertTrue(filter.accept(conf2));
	}

	@Test
	public void testToggleFiltersPersistAndRestore() throws Exception {
		ToggleFiltersModel model = new ToggleFiltersModel(propertyStore);

		FilterChoice nonWorkspace = getFilter(model, HIDE_NON_WORKSPACE_ELEMENTS);
		FilterChoice solitaryConf = getFilter(model, HIDE_SOLITARY_CONF);

		//initially the defaults should be set.
		assertEquals(ImmutableSet.of(solitaryConf), model.getSelectedFilters().getValues());

		model.getSelectedFilters().remove(solitaryConf);
		model.getSelectedFilters().add(nonWorkspace);

		//Simulate model reload (i.e. just instantiate it with the same property store).
		model = new ToggleFiltersModel(propertyStore);

		assertEquals(ImmutableSet.of(nonWorkspace), model.getSelectedFilters().getValues());
	}

	private Filter<BootDashElement> getFilter(String withLabel) {
		ToggleFiltersModel model = new ToggleFiltersModel(propertyStore);
		FilterChoice selectFilter = getFilter(model, withLabel);
		model.getSelectedFilters().replaceAll(ImmutableSet.of(selectFilter));
		Filter<BootDashElement> effectiveFilter = model.getFilter().getValue();
		return effectiveFilter;
	}

	private FilterChoice getFilter(ToggleFiltersModel model, String withLabel) {
		for (FilterChoice choice : model.getAvailableFilters()) {
			if (choice.getLabel().equals(withLabel)) {
				return choice;
			}
		}
		fail("No available filter has this label '"+withLabel+"'");
		return null;
	}

}
