/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.springframework.tooling.boot.ls.views.StructureClient.Groups;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;

class GroupingDialogModel {
	
	interface TreeItem {
		Boolean getChecked();
		String getLabel();
		void setChecked(boolean checked);
	}

	static class ProjectItem implements TreeItem {
		
		private final String name;
		private final List<GroupItem> groups;
		
		public ProjectItem(String name) {
			this.name = name;
			this.groups = new ArrayList<>();
		}
		
		public Boolean getChecked() {
			if (groups.isEmpty()) {
				return true;
			}
			boolean checked = groups.get(0).checked;
			for (GroupItem g : groups) {
				if (g.checked != checked) {
					return null;
				}
			}
			return checked;
		}
		
		public GroupItem addGroup(String id, String label) {
			GroupItem groupItem = new GroupItem(this, id, label, false);
			groups.add(groupItem);
			return groupItem;
		}
		
		public String getLabel() {
			return name;
		}
		
		public List<GroupItem> getGroups() {
			return groups;
		}
		
		void apply(List<String> checkedGroups) {
			groups.forEach(g -> g.checked = checkedGroups == null || checkedGroups.contains(g.id));
		}
		
		List<String> extract() {
			List<String> checkedGroups = groups.stream().filter(g -> g.checked).map(g -> g.id).toList();
			return checkedGroups.size() == groups.size() ? null : checkedGroups;
		}

		@Override
		public void setChecked(boolean checked) {
			groups.forEach(g -> g.setChecked(checked));
		}
		
	}
	
	static class GroupItem implements TreeItem {
		
		private final ProjectItem projectItem;
		private final String id;
		private final String label;
		private boolean checked;
		
		private GroupItem(ProjectItem projectItem, String id, String label, boolean checked) {
			this.projectItem = projectItem;
			this.id = id;
			this.label = label;
			this.checked = checked;
		}
		
		public void setChecked(boolean c) {
			this.checked = c;
		}
		
		public Boolean getChecked() {
			return this.checked;
		}

		public String getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}
		
		ProjectItem getProjectItem() {
			return projectItem;
		}
		
	}
	
	private final Supplier<CompletableFuture<List<Groups>>> client;
	
	private final LiveSetVariable<ProjectItem> projectItems;

	private final Supplier<Map<String, List<String>>> groupings;
	
	private final LiveVariable<Boolean> loaded;
	
	public GroupingDialogModel(Supplier<CompletableFuture<List<Groups>>> client, Supplier<Map<String, List<String>>> groupings) {
		this.client = client;
		this.groupings = groupings;
		this.projectItems = new LiveSetVariable<>();
		this.loaded = new LiveVariable<>(false);
	}
	
	void load() {
		loaded.setValue(false);
		Map<String, List<String>> groupingsMap = groupings.get();
		client.get().thenApply(allGroups -> {
			return allGroups.stream().map(groups -> {
				String projectName = groups.projectName();
				ProjectItem projectItem = new ProjectItem(projectName);
				groups.groups().stream().forEach(g -> projectItem.addGroup(g.identifier(), g.displayName()));
				projectItem.apply(groupingsMap.get(projectName));
				return projectItem;
			}).toList();
		}).thenAccept(items -> {
			projectItems.replaceAll(items);
			loaded.setValue(true);
		});
	}
	
	LiveSetVariable<ProjectItem> getLiveSet() {
		return projectItems;
	}
	
	LiveVariable<Boolean> getLoaded() {
		return loaded;
	}
	
	public Map<String, List<String>> getResult() {
		// Cannot use `Collectors.toMap()` due to NPE with null values
		Map<String, List<String>> res = new HashMap<>();
		for (ProjectItem p : projectItems.getValue()) {
			res.put(p.getLabel(), p.extract());
		}
		return res;
	}
	
}
