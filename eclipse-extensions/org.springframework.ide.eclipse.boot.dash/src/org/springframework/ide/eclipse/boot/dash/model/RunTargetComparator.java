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
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.Comparator;
import java.util.List;

import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.util.OrderBasedComparator;

public class RunTargetComparator implements Comparator<RunTarget> {

	private Comparator<RunTargetType> typeComparator;

	public RunTargetComparator(Comparator<RunTargetType> typeComparator) {
		this.typeComparator = typeComparator;
	}

	public RunTargetComparator(List<RunTargetType> runTargetTypes) {
		this(new OrderBasedComparator<>(
				runTargetTypes.toArray(new RunTargetType[runTargetTypes.size()])));
	}

	public int compare(RunTarget target1, RunTarget target2) {

		RunTargetType rtType1 = target1.getType();
		RunTargetType rtType2 = target2.getType();

		int result = typeComparator.compare(rtType1, rtType2);
		if (result == 0) {
			result = target1.getDisplayName().compareTo(target2.getDisplayName());
			if (result == 0) {
				result = target1.getId().compareTo(target2.getId());
			}
		}
		return result;
	}
}
