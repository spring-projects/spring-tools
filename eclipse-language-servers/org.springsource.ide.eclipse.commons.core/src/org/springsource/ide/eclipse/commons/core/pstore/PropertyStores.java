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
package org.springsource.ide.eclipse.commons.core.pstore;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Provides static helper methods to create IPropertyStore instances,
 */
public class PropertyStores {

	private static final String DEFAULT_QUALIFIER = "microservice-metadata";

	public static IScopedPropertyStore<IProject> createForProjects() {
		return createForProjects(DEFAULT_QUALIFIER);
	}

	public static IScopedPropertyStore<IProject> createForProjects(String qualifier) {
		return new PreferenceBasedStore<IProject>() {
			@Override
			protected IEclipsePreferences createPrefs(IProject p) {
				IEclipsePreferences prefs = new ProjectScope(p).getNode(qualifier);
				return prefs;
			}
		};
	}

	public static <S> IPropertyStore createForScope(final S scope, final IScopedPropertyStore<S> scopedStore) {
		Assert.isNotNull(scopedStore);
		return new IPropertyStore() {

			@Override
			public void put(String key, String value) throws Exception {
				scopedStore.put(scope, key, value);
			}

			@Override
			public String get(String key) {
				return scopedStore.get(scope, key);
			}
		};
	}

	public static PropertyStoreApi createApi(IPropertyStore backingStore) {
		return new PropertyStoreApi(backingStore);
	}

	public static IPropertyStore createSubStore(final String subStoreId, final IPropertyStore backingStore) {
		return new IPropertyStore() {

			private String subkey(String key) {
				return subStoreId+":"+key;
			}

			@Override
			public void put(String key, String value) throws Exception {
				backingStore.put(subkey(key), value);
			}

			@Override
			public String get(String key) {
				return backingStore.get(subkey(key));
			}
		};
	}

	public static IPropertyStore createFor(final ILaunchConfiguration launchConf) {
		return new IPropertyStore() {

			private static final String prefix = "boot.dash.";
			private ILaunchConfiguration conf = launchConf;

			@Override
			public synchronized void put(String key, String value) throws Exception {
				ILaunchConfigurationWorkingCopy wc = conf.getWorkingCopy();
				wc.setAttribute(prefix+key, value);
				wc.doSave();
			}


			@Override
			public String get(String key) {
				try {
					return conf.getAttribute(prefix+key,(String)null);
				} catch (Exception e) {
					Log.log(e);
				};
				return null;
			}
		};
	}

	public static IPropertyStore backedBy(final IPreferenceStore preferenceStore) {
		return new IPropertyStore() {
			@Override
			public void put(String key, String value) throws Exception {
				if (value==null) {
					preferenceStore.setToDefault(key);
				} else {
					preferenceStore.setValue(key, value);
				}
			}
			@Override
			public String get(String key) {
				if (preferenceStore.contains(key)) {
					return preferenceStore.getString(key);
				}
				return null;
			}
		};
	}

}
