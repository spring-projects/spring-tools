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
package org.springframework.tooling.ls.eclipse.gotosymbol.test;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolDialogModel;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.SymbolContainer;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.SymbolsProvider;

/**
 * @author Martin Lippert
 */
public class GotoSymbolDialogModelTest {

	private SymbolsProvider provider(String name) {
		return new SymbolsProvider() {
			@Override
			public String getName() {
				return name;
			}
			@Override
			public List<SymbolContainer> fetchFor(String query) throws Exception {
				return Collections.emptyList();
			}
			@Override
			public boolean fromFile(SymbolContainer symbol) {
				return false;
			}
		};
	}

	@Test
	public void defaultsToTheFirstSymbolsProviderWhenNoIndexIsGiven() {
		GotoSymbolDialogModel model = new GotoSymbolDialogModel(null, provider("workspace"), provider("project"), provider("file"));

		assertEquals(0, model.getCurrentSymbolsProviderIndex());
	}

	@Test
	public void initializesWithARememberedSymbolsProviderIndex() {
		GotoSymbolDialogModel model = new GotoSymbolDialogModel(null, 1, provider("workspace"), provider("project"), provider("file"));

		assertEquals(1, model.getCurrentSymbolsProviderIndex());
	}

	@Test
	public void outOfRangeRememberedIndexWrapsAroundInsteadOfFailing() {
		GotoSymbolDialogModel model = new GotoSymbolDialogModel(null, 5, provider("workspace"), provider("project"));

		assertEquals(1, model.getCurrentSymbolsProviderIndex());
	}

	@Test
	public void toggleCyclesThroughTheSymbolsProvidersAndWrapsAround() {
		GotoSymbolDialogModel model = new GotoSymbolDialogModel(null, provider("workspace"), provider("project"), provider("file"));

		model.toggleSymbolsProvider();
		assertEquals(1, model.getCurrentSymbolsProviderIndex());

		model.toggleSymbolsProvider();
		assertEquals(2, model.getCurrentSymbolsProviderIndex());

		model.toggleSymbolsProvider();
		assertEquals(0, model.getCurrentSymbolsProviderIndex());
	}

}
