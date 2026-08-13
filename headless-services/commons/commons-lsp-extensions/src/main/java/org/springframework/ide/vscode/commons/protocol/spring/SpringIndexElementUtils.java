/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class SpringIndexElementUtils {

	public static <T extends SpringIndexElement> List<T> getNodesOfType(Class<T> type, Collection<SpringIndexElement> rootNodes) {
		return getNodesOfType(type, rootNodes, element -> true);
	}

	public static <T extends SpringIndexElement> List<T> getNodesOfType(Class<T> type, Collection<SpringIndexElement> rootNodes, Predicate<T> predicate) {
		List<T> result = new ArrayList<>();

		ArrayDeque<SpringIndexElement> elementsToVisit = new ArrayDeque<>();
		elementsToVisit.addAll(rootNodes);

		while (!elementsToVisit.isEmpty()) {
			SpringIndexElement element = elementsToVisit.pop();

			if (type.isInstance(element) && predicate.test(type.cast(element))) {
				result.add(type.cast(element));
			}

			elementsToVisit.addAll(element.getChildren());
		}

		return result;
	}

}
