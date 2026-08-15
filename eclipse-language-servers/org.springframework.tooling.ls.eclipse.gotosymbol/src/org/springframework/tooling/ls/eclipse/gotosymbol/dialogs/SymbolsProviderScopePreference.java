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

import org.springframework.tooling.ls.eclipse.gotosymbol.GotoSymbolPlugin;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Remembers which {@link SymbolsProvider} (workspace / project / file scope) was last selected
 * in the Goto Symbol dialog, so it can be restored the next time the dialog is opened. Backed by
 * the {@link GotoSymbolPlugin}'s preference store, which persists to the workspace metadata and
 * therefore survives across Eclipse restarts (unlike keeping this in a static field in memory).
 *
 * @author Martin Lippert
 */
public class SymbolsProviderScopePreference {

	public static final SymbolsProviderScopePreference INSTANCE = new SymbolsProviderScopePreference(
			PropertyStores.backedBy(GotoSymbolPlugin.getInstance().getPreferenceStore())
	);

	private static final String KEY = "symbolsProviderIndex";
	private static final int DEFAULT_INDEX = 0;

	private final PropertyStoreApi prefs;

	public SymbolsProviderScopePreference(IPropertyStore backingStore) {
		this.prefs = new PropertyStoreApi(backingStore);
	}

	public int getIndex() {
		return prefs.get(KEY, DEFAULT_INDEX);
	}

	public void setIndex(int index) {
		try {
			prefs.put(KEY, Integer.toString(index));
		} catch (Exception e) {
			Log.log(e);
		}
	}
}
