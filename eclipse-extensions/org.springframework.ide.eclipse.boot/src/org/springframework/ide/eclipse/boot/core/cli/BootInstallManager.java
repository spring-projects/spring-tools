/*******************************************************************************
 *  Copyright (c) 2013, 2016 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.cli;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.cli.install.GenericBootInstall;
import org.springframework.ide.eclipse.boot.core.cli.install.IBootInstall;
import org.springframework.ide.eclipse.boot.core.cli.install.ZippedBootInstall;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.core.preferences.StsProperties;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.util.ArrayEncoder;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * Manages the boot installations that are configured in this workspace.
 *
 * @author Kris De Volder
 */
public class BootInstallManager implements IBootInstallFactory {

	public interface BootInstallListener {
		public void defaultInstallChanged();
	}

	private static final String BOOT_INSTALLS = "installs"; //Key used to store info in the prefs.

	private static final String DEFAULT_BOOT_INSTALL = "default.install";

	private static BootInstallManager instance;

	public static File determineCacheDir() {
		IPath stateLocation = BootActivator.getDefault().getStateLocation();
		return stateLocation.append("installs").toFile();
	}

	public static synchronized BootInstallManager getInstance() {
		try {
			if (instance == null) {
				IPreferenceStore prefsStore = BootActivator.getDefault().getPreferenceStore();
				instance = new BootInstallManager(determineCacheDir(), PropertyStores.backedBy(prefsStore));
			}
			return instance;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private DownloadManager downloader;

	private PropertyStoreApi prefs;
	private List<IBootInstall> installs = null;
	private LiveVariable<IBootInstall> _defaultInstall = new LiveVariable<>();


	public BootInstallManager(File cacheDir, IPropertyStore propStore) throws Exception {
		prefs = new PropertyStoreApi(propStore);
		downloader = new DownloadManager(null, cacheDir);
		installs = new ArrayList<>();
		read();
		if (installs.isEmpty()) {
			setDefaultInstall(newInstall(StsProperties.getInstance().get("spring.boot.install.url"), null));
		}
	}

	/**
	 * Initializes the manager by reading Eclipse preferences.
	 */
	private void read() {
		IEclipsePreferences prefs = getPrefs();
		String defaultName = prefs.get(DEFAULT_BOOT_INSTALL, "");
		String encoded = prefs.get(BOOT_INSTALLS, null);
		if (encoded != null) {
			String[] encodedInstalls = ArrayEncoder.decode(encoded);
			for (String encodedInstall : encodedInstalls) {
				try {
					String[] parts = ArrayEncoder.decode(encodedInstall);
					IBootInstall install = newInstall(parts[0], parts[1]);
					installs.add(install);
					if (defaultName.equals(install.getName())) {
						setDefaultInstall(install);
					}
				} catch (Exception e) {
					Log.log(e);
				}
			}
		}
	}

	protected IEclipsePreferences getPrefs() {
		return InstanceScope.INSTANCE.getNode(BootActivator.PLUGIN_ID);
	}

	/**
	 * Saves current installs by writing them into the Eclipse preferences.
	 * This is not done automatically when values in the manager are set.
	 * The client is repsonsible for calling this method.
	 */
	public void save() {
		List<IBootInstall> local = installs;
		String[] encodedInstalls = new String[local.size()];
		for (int i = 0; i < encodedInstalls.length; i++) {
			encodedInstalls[i] = encodeInstall(local.get(i));
		}
		String encoded = ArrayEncoder.encode(encodedInstalls);
		IEclipsePreferences prefs = getPrefs();
		prefs.put(BOOT_INSTALLS, encoded);
		IBootInstall di = null;
		try {
			di = getDefaultInstall();
		} catch (Exception e) {
			Log.log(e);
		}
		if (di == null) {
			prefs.remove(DEFAULT_BOOT_INSTALL);
		} else {
			prefs.put(DEFAULT_BOOT_INSTALL, di.getName());
		}
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			Log.log(e);
		}
	}

	private String encodeInstall(IBootInstall install) {
		return ArrayEncoder.encode(new String[] { install.getUrl(), install.getName() });
	}

	@Override
	public IBootInstall newInstall(String url, String name) {
		try {
			URI uri = new URI(url);
			if ("file".equals(uri.getScheme())) {
				File file = new File(uri);
				if (file.isDirectory()) {
					return new LocalBootInstall(file, name);
				} else {
					return new ZippedBootInstall(downloader, url, name);
				}
			} else {
				String path = uri.getPath();
				if (path.toLowerCase().endsWith(".zip")) {
					return new ZippedBootInstall(downloader, url, name);
				} else {
					return new GenericBootInstall(url, null, "Not a zip url: "+url);
				}
			}
		} catch (Exception e) {
			Log.log(e);
			return new GenericBootInstall(url, name, ExceptionUtil.getMessage(e));
		}
	}

	public synchronized IBootInstall getDefaultInstall() throws Exception {
		return _defaultInstall.getValue();
	}

	public void setDefaultInstall(IBootInstall defaultInstall) {
		if (!installs.contains(defaultInstall)) {
			installs.add(defaultInstall);
		}
		this._defaultInstall.setValue(defaultInstall);
	}

	public void setInstalls(Collection<IBootInstall> newInstalls) {
		this.installs = new ArrayList<>(newInstalls);
	}

	public Collection<IBootInstall> getInstalls() {
		return new ArrayList<>(installs);
	}

	public DownloadManager getDownloader() {
		return downloader;
	}

	public Disposable addBootInstallListener(BootInstallListener listener) {
		return _defaultInstall.onChange((e, v) -> listener.defaultInstallChanged());
	}

	public LiveExpression<IBootInstall> getDefaultInstallExp() {
		return _defaultInstall;
	}

}
