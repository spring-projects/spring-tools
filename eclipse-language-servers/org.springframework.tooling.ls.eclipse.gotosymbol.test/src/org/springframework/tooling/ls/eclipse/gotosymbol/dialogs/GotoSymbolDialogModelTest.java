/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Tests the scope selection of the go-to-symbol dialog model.
 *
 * @author Broadcom, Inc.
 */
public class GotoSymbolDialogModelTest {

	@Test
	public void startsWithRememberedSymbolsProvider() {
		SymbolsProvider workspace = provider("Workspace");
		SymbolsProvider project = provider("Project");
		SymbolsProvider file = provider("File");

		GotoSymbolDialogModel model = new GotoSymbolDialogModel(null, 1, workspace, project, file);

		assertSame(project, model.currentSymbolsProvider.getValue());
		assertEquals(1, model.getCurrentSymbolsProviderIndex());
	}

	@Test
	public void togglesFromRememberedSymbolsProvider() {
		SymbolsProvider workspace = provider("Workspace");
		SymbolsProvider project = provider("Project");
		SymbolsProvider file = provider("File");
		GotoSymbolDialogModel model = new GotoSymbolDialogModel(null, 1, workspace, project, file);

		model.toggleSymbolsProvider();

		assertSame(file, model.currentSymbolsProvider.getValue());
		assertEquals(2, model.getCurrentSymbolsProviderIndex());
	}

	private SymbolsProvider provider(String name) {
		return new SymbolsProvider() {

			@Override
			public String getName() {
				return name;
			}

			@Override
			public List<SymbolContainer> fetchFor(String query) {
				return Collections.emptyList();
			}

			@Override
			public boolean fromFile(SymbolContainer symbol) {
				return false;
			}
		};
	}
}
