/*******************************************************************************
 *  Copyright (c) 2013 Pivotal Software, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager;

import java.io.File;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.FrameworkCoreActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager.DownloadRequestor;

/**
 * A DownloadableItem is something that can be downloaded and
 * stored in the local file system as a file.
 *
 * @author Kris De Volder
 */
public class DownloadableItem {

	private final URL url;
	protected final DownloadManager downloader;
	private String name; //optional name. If set this name will be used as filename in the cache otherwise
						// suitable name will be computed.
	protected IStatus downloadStatus = Status.OK_STATUS; //error message if download failed. Otherwise contains 'OK'.

	public DownloadableItem(URL url, DownloadManager downloader) {
		this.url = url;
		this.downloader = downloader;
	}

	public void setFileName(String n) {
		this.name = n;
	}

	/**
	 * A downloadable item must provide a URI where its contents can be fetched from.
	 */
	public URL getURL() {
		return url;
	}

	/**
	 * Force the item to be downloaded to a local File. If an item is already downloaded
	 * the cached local file will be returned immediately. Otherwise the method will block
	 * until the download is complete or an error occurs.
	 */
	public File getFile() throws Exception {
		try {
			final File[] fileBox = new File[1];
			downloader.doWithDownload(this, new DownloadRequestor() {
				public void exec(File downloadedFile) throws Exception {
					fileBox[0] = downloadedFile;
					//TODO; validate file contents?
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

	protected IStatus error(String message) {
		return new Status(IStatus.ERROR, FrameworkCoreActivator.PLUGIN_ID, message);
	}

	/**
	 * A downloadable item must provide a filename where its cached downloaded contents
	 * should be stored. The name must uniquely identify the downloadable item
	 * within the scope of the DownloadManager used to download this item.
	 * <p>
	 * The name must also be a legal file name on the current OS.
	 * <p>
	 * A default implementation is provided that uses sha1 and Base64 encoding
	 * to generate a name from the uri.
	 * <p>
	 * These generated names are not guaranteed to be unique, but the chance of
	 * a collisions is astronomically small.
	 */
	protected String getFileName() {
		if (name == null) {
			try {
				MessageDigest sha1encoder = MessageDigest.getInstance("sha1");
				byte[] bytes = sha1encoder.digest((""+getURL()).getBytes());
				name = new String(Base64.encodeBase64(bytes));
				name = name.replace('/', '_'); //slashes are trouble in file names.
			} catch (NoSuchAlgorithmException e) {
				//This should not be possible
				throw new Error(e);
			}
		}
		return name;
	}

	@Override
	public String toString() {
		return url.toString();
	}

	public boolean isDownloaded() {
		return downloader != null && downloader.isDownloaded(this);
	}

	/**
	 * Error message if download failed. Other
	 * @return
	 */
	public IStatus getDownloadStatus() {
		return downloadStatus;
	}

	public void clearCache() {
		//Take care not to delete the original file if was local to begin with (in that case we don't 
		// copy it into the cache dir so there is no cache to clear!
		if (url != null && !"file".equals(url.getProtocol())) {
			//Avoid race conditions when someone is downloading this item at the moment.
			synchronized (downloader) {
				File localFile = downloader.getLocalLocation(this);
				FileUtils.deleteQuietly(localFile);
			}
		}
	}

	/**
	 * Determine the location to unzip to (without actually doing any unzipping).
	 */
	public File getUnzipDir() {
		String fileName = getFileName();
		Assert.isTrue(fileName.endsWith(".zip"));
		File unzipLocation = new File(downloader.getCacheDir(), fileName.substring(0, fileName.length()-4));
		return unzipLocation;
	}

}
