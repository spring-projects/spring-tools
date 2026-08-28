/*******************************************************************************
 * Copyright (c) 2014 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An instance of this class keeps track of a number of radio button models, grouped by their 'name'
 * attribute.
 *
 * @author Kris De Volder
 */
public class RadioGroups {


	//In the form the radios look like this:
	//  <label class="radio">
	//    <input type="radio" name="packaging" value="jar" checked="true"/>
	//    Jar
	//  </label>

	public interface GroupLabelProvider {
		/**
		 * Try to discover label somehow. If it can't be discovered, is allowed to return null.
		 * This function is only called when a new RadioGroupInfo is created.
		 */
		String discoverLabel();
	}

	//Maps 'name' attribute to a RadioGroupInfo
	private Map<String, RadioGroup> groups = new LinkedHashMap<String, RadioGroup>();

	/**
	 * Ensure a group with given name exists. Create it if needed.
	 * @return The RadioGroup, never null.
	 */
	public RadioGroup ensureGroup(String groupName) {
		RadioGroup group = groups.get(groupName);
		if (group == null) {
			group = new RadioGroup(groupName);
			groups.put(groupName, group);
		}
		return group;
	}

	public List<RadioGroup> getGroups() {
		return new ArrayList<RadioGroup>(groups.values());
	}

	public RadioGroup getGroup(String name) {
		return groups.get(name);
	}


}
