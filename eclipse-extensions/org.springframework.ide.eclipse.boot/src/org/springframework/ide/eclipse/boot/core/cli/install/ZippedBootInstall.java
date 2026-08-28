/*******************************************************************************
 *  Copyright (c) 2013, 2020 Pivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.cli.install;

import java.io.File;
import java.net.URL;

import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadableItem;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * A Boot Installation that is located in a zip file. It must be unzipped locally
 * before it can be used.
 * <p>
 * This class takes care of downloading and unzipping it automatically to a
 * cache directory located in the workspace.
 *
 * @author Kris De Volder
 */
public class ZippedBootInstall extends BootInstall {

	private Supplier<CloudCliInstall> cloudCliInstall;

	private DownloadableItem zip;
	private File home; //Will be set once the install is unzipped and ready for use.

	public ZippedBootInstall(DownloadManager downloader, String uri, String name) throws Exception {
		super(uri, name);
		cloudCliInstall = Suppliers.memoize(this::initCloudCliInstall);
		this.zip = new DownloadableZipItem(new URL(uri), downloader);
	}

	@Override
	public File getHome() throws Exception {
		if (home == null) {
			File unzipped = zip.getFile();
			//Assumes that unzipped will contain one directory 'spring-<version>'
			for (File dir : unzipped.listFiles()) {
				if (dir.isDirectory()) {
					String name = dir.getName();
					if (name.startsWith("spring-")) {
						home = dir;
					}
				}
			}
		}
		return home;
	}

	@Override
	public String getUrl() {
		return uriString;
	}

	@Override
	public boolean mayRequireDownload() {
		//We can do better than just looking at the url (as the super method does).
		//We can see whether or not the zip file was dowloaded already or not.
		if (zip != null) {
			return !zip.isDownloaded();
		} else {
			return super.mayRequireDownload();
		}
	}

	@Override
	public void clearCache() {
		if (zip != null) {
			zip.clearCache();
		}
	}

	synchronized private CloudCliInstall initCloudCliInstall() {
		if (super.getCloudCliInstall() == null) {
			return null;
		} else {
			return new CachingCloudCliInstall(this);
		}
	}

	@Override
	protected CloudCliInstall getCloudCliInstall() {
		return cloudCliInstall.get();
	}

	@Override
	public void refreshExtension(Class<? extends IBootInstallExtension> extensionType) {
		if (extensionType == CloudCliInstall.class) {
			cloudCliInstall = Suppliers.memoize(this::initCloudCliInstall);
		}
		super.refreshExtension(extensionType);
	}

}
