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

import org.junit.Test;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.SymbolsProviderScopePreference;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.InMemoryPropertyStore;

/**
 * @author Martin Lippert
 */
public class SymbolsProviderScopePreferenceTest {

	@Test
	public void defaultsToZeroWhenNothingWasStoredYet() {
		SymbolsProviderScopePreference prefs = new SymbolsProviderScopePreference(new InMemoryPropertyStore());

		assertEquals(0, prefs.getIndex());
	}

	@Test
	public void remembersTheIndexAcrossInstancesBackedByTheSamePersistentStore() {
		// An InMemoryPropertyStore stands in here for the real, persistent backing store
		// (the plugin's preference store, backed by workspace metadata on disk) - the point
		// being verified is that the index survives a fresh SymbolsProviderScopePreference
		// instance, i.e. a new Eclipse session, rather than only living in memory.
		IPropertyStore backingStore = new InMemoryPropertyStore();

		new SymbolsProviderScopePreference(backingStore).setIndex(2);

		SymbolsProviderScopePreference nextSession = new SymbolsProviderScopePreference(backingStore);
		assertEquals(2, nextSession.getIndex());
	}

}
