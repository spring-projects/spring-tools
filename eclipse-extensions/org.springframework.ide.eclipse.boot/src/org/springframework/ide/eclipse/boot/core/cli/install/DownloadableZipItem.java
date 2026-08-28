/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.cli.install;

import java.io.File;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.core.ZipFileUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager.DownloadRequestor;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadableItem;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * TODO: DownloadableZipItem should probably be moved to commons
 * (same place as DownloadableItem).
 */
public class DownloadableZipItem extends DownloadableItem {

	private String fileName; //Cached value for getFileName method.

	public DownloadableZipItem(URL url, DownloadManager downloader) {
		super(url, downloader);
	}

	@Override
	protected String getFileName() {
		if (fileName == null) {
			try {
				//Try to create human friendly name
				String name = new Path(getURL().getPath()).lastSegment();
				if (name != null && name.endsWith(".zip")) {
					fileName = name;
				}
			} catch (Throwable e) {
				Log.log(e);
			}
			//Ensure that filename is at least set to something that ends with .zip
			if (fileName == null) {
				fileName = super.getFileName()+".zip";
			}
		}
		return fileName;
	}

	/**
	 * Force the item to be downloaded and unzipped locally. If an item is already downloaded
	 * the cached local file will be returned immediately. Otherwise the method will block
	 * until the download is complete or an error occurs.
	 * <p>
	 * The returned file will point to the location where the zipfile was expanded to.
	 */
	@Override
	public File getFile() throws Exception {
		try {
			final File[] fileBox = new File[1];
			downloader.doWithDownload(this, new DownloadRequestor() {
				@Override
				public void exec(File zipFile) throws Exception {
					File unzipDir = getUnzipDir();
					unzip(zipFile, unzipDir);
					fileBox[0] = unzipDir;
				}
			});
			downloadStatus = Status.OK_STATUS;
			return fileBox[0];
		} catch (UIThreadDownloadDisallowed e) {
			//Shouldn't affect download status since it means download was not attempted
			throw e;
		} catch (Exception e) {
			downloadStatus = error(ExceptionUtil.getMessage(e));
			throw e;
		}
	}

	public File getZipFile() throws Exception {
		try {
			final File[] fileBox = new File[1];
			downloader.doWithDownload(this, new DownloadRequestor() {
				@Override
				public void exec(File zipFile) throws Exception {
					fileBox[0] = zipFile;
				}
			});
			downloadStatus = Status.OK_STATUS;
			return fileBox[0];
		} catch (UIThreadDownloadDisallowed e) {
			//Shouldn't affect download status since it means download was not attempted
			throw e;
		} catch (Exception e) {
			downloadStatus = error(ExceptionUtil.getMessage(e));
			throw e;
		}
	}

	@Override
	public void clearCache() {
		synchronized (downloader) {
			super.clearCache();
			File unzipDir = getUnzipDir();
			FileUtils.deleteQuietly(unzipDir);
		}
	}

	protected synchronized void unzip(File zipFile, File unzipDir) throws Exception {
		if (unzipDir.exists()) {
			//Already unzipped by someone else.
			return;
		}
		try {
			ZipFileUtil.unzip(zipFile, unzipDir, null);
			return;
		} catch (Throwable e) {
			//If operation has an error or was aborted the unzipDir must be deleted because its probably junk.
			// data in it may not be valid.
			FileUtils.deleteQuietly(unzipDir);
			throw ExceptionUtil.exception(e);
		}
	}
}