/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.modulith.NamedInterface;

public class NamedInterfaceNodeTests {

	@Test
	void ordersInternalsLast() {

		var internal = new NamedInterfaceNode(null);
		var api = new NamedInterfaceNode(new NamedInterface("<<UNNAMED>>", Collections.emptyList()));
		var namedBeforeApi = new NamedInterfaceNode(new NamedInterface("A", Collections.emptyList()));
		var namedAfterApi = new NamedInterfaceNode(new NamedInterface("B", Collections.emptyList()));

		var source = new TreeSet<>(List.of(internal, api, namedBeforeApi, namedAfterApi));

		assertThat(source)
				.extracting(NamedInterfaceNode::toString)
				.containsExactly("A", "API", "B", "Internal");
	}
}
