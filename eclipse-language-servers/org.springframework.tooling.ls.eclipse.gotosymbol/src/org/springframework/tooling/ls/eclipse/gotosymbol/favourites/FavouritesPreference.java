/*******************************************************************************
 * Copyright (c) 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.favourites;

import java.util.ArrayList;
import java.util.List;

import org.springframework.tooling.ls.eclipse.gotosymbol.GotoSymbolPlugin;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolDialogModel.Favourite;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class FavouritesPreference  {
	
	/**
	 * The singleton 'Production' instance of {@link FavouritesPreference}. This stores/reads favourites to/from 
	 * the Eclipse preference store for {@link GotoSymbolPlugin}.
	 */
	public static final FavouritesPreference INSTANCE = new FavouritesPreference(
			PropertyStores.backedBy(GotoSymbolPlugin.getInstance().getPreferenceStore())
	);

	public static final Favourite[] DEFAULT = {
			new Favourite("Request Mappings", "@/"),
			new Favourite("Beans", "@+"),
			new Favourite("All Spring Elements", "@")
	};
	private static final String KEY = "favourites";
	private static final String[] NO_STRINGS = {};
	
	private PropertyStoreApi prefs;

	public FavouritesPreference(IPropertyStore backingStore) {
		this.prefs = new PropertyStoreApi(backingStore);
	}

	public Favourite[] getFavourites() {
		try {
			String[] strings = prefs.get(KEY, NO_STRINGS);
			if (strings != null && strings.length > 0) {
				Favourite[] favs = new Favourite[strings.length/2];
				for (int i = 0; i < strings.length; i+=2) {
					favs[i/2] = new Favourite(strings[i], strings[i+1]);
				}
				return favs;
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return DEFAULT;
	}

	public void setFavourites(Favourite[] favourites) {
		try {
			String[] strings = new String[favourites.length*2];
			for (int i = 0; i < strings.length; i+=2) {
				Favourite fav = favourites[i/2];
				strings[i] = fav.name;
				strings[i+1] = fav.query;
			}
			prefs.put(KEY, strings);
		} catch (Exception e) {
			Log.log(e);
		}
	}

	public void add(String name, String currentSearch) {
		Favourite[] oldFavs = getFavourites();
		Favourite[] newFavs = new Favourite[oldFavs.length+1];
		for (int i = 0; i < oldFavs.length; i++) {
			newFavs[i] = oldFavs[i];
		}
		newFavs[oldFavs.length] = new Favourite(name, currentSearch);
		setFavourites(newFavs);
 	}

	public void remove(String currentSearch) {
		List<Favourite> retain = new ArrayList<>();
		for (Favourite favourite : getFavourites()) {
			if (!favourite.query.equals(currentSearch)) {
				retain.add(favourite);
			}
		}
		setFavourites(retain.toArray(new Favourite[retain.size()]));
	}
}
