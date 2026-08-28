/*******************************************************************************
 *  Copyright (c) 2012, 2013 Pivotal Software, Inc.
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.swt.widgets.Display;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.FrameworkCoreActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.util.FileUtil;

/**
 * Manages a cache of downloaded content.
 *
 * @author Kris De Volder
 */
public class DownloadManager {

	/**
	 * An instance of this interface represent an action to execute on a downloaded
	 * File. The action may indicate failure by throwing an exception or by
	 * returning false. A failed action may trigger the DownloadManager to
	 * clear the cache and try again for a limited number of times.
	 */
	public interface DownloadRequestor {
		void exec(File downloadedFile) throws Exception;
	}

	/**
	 * An instance of this class represents some type of service able to fetch the
	 * content data.
	 */
	public interface DownloadService {
		void fetch(URL url, OutputStream writeTo) throws IOException;
	}

	private final File cacheDirectory;
	private final DownloadService downloader;
	private boolean deleteCacheOnDispose = false;
	private boolean allowUIThread = false;
	private int tries = 5; //5 retries by default
	private long retryInterval = 0; //no wait between retries by default.

	public DownloadManager(DownloadService downloader, File cacheDir) throws IOException {
		if (cacheDir == null) {
			cacheDir = FileUtil.createTempDirectoryWithTimestamp("downloadCache");
			this.deleteCacheOnDispose = true; // This dir can not be used by anynone after this DLM is diposed so better delete it.
		}
		if (downloader == null) {
			downloader = new SimpleDownloadService();
		}
		this.downloader = downloader;
		this.cacheDirectory = cacheDir;
		if (!cacheDir.isDirectory()) {
			Assert.isTrue(cacheDir.mkdirs(), "Couldn't create cache directory at "+cacheDir);
		}
	}

	public DownloadManager clearCache() {
		FileUtils.deleteQuietly(cacheDirectory);
		cacheDirectory.mkdirs();
		return this;
	}

	/**
	 * Deprecated use one of the other contructors (probably you want to use
	 * the one that takes a {@link URLConnectionFactory} if you where using
	 * this one.
	 */
	@Deprecated
	public DownloadManager() throws IOException {
		this(new URLConnectionFactory());
	}

	/**
	 * Create a Default downloadmanager. This downloadmanager does not use authentication and
	 * simply uses standard JavaApi to fetch url content.
	 * <p>
	 * A new temp directory is created to use as the cache directory.
	 * <p>
	 * When this DownloadManager is disposed the cache directory is deleted (since the next
	 * DownloadManager created in this way will use a different cache dir anyway there isn't
	 * much use leaving it around).
	 */
	public DownloadManager(URLConnectionFactory urlConnectionFactory) throws IOException {
		this(new SimpleDownloadService(urlConnectionFactory), null);
		this.deleteCacheOnDispose = true;
	}

	/**
	 * This method is deprecated, please use doWithDownload to provide proper recovery
	 * for cache corruption.
	 */
	@Deprecated
	public File downloadFile(DownloadableItem item) throws URISyntaxException, FileNotFoundException, CoreException, IOException, UIThreadDownloadDisallowed {
		File target = getLocalLocation(item);
		if (target.exists()) {
			return target;
		}

		if (!allowUIThread && Display.getCurrent() != null) {
			throw new UIThreadDownloadDisallowed("Don't call download manager from the UI Thread unless the data is already cached.");
		}
//
//		);
		//It is important not to lock the UI thread for downloads!!!
		//  If the UI thread is well behaved, we assume it will be careful not to call this method unless the
		//  content is already cached. So once we get past the exists check it is ok to grab the lock.
		synchronized (this) {
			//It is possible that multiple thread where waiting to enter here... only one of them should proceed
			// to actually download. So make sure to retest the target.exists() condition to avoid multiple downloads
			if (target.exists()) {
				return target;
			}
			if (!cacheDirectory.exists()) {
				cacheDirectory.mkdirs();
			}

			File targetPart = new File(target.toString()+".part");
			FileOutputStream out = new FileOutputStream(targetPart);
			try {
				URL url = item.getURL();
				System.out.println("Downloading " + url + " to " + target);

				downloader.fetch(url, out);
			}
			finally {
				out.close();
			}

			if (!targetPart.renameTo(target)) {
				throw new IOException("Error while renaming " + targetPart + " to " + target);
			}

			return target;
		}
	}

	/**
	 * Get the local location of given item. Note that the item may or may not yet be downloaded so
	 * there are no guarantees there is anything meaningful to be found there.
	 */
	public File getLocalLocation(DownloadableItem item) {
		URL url = item.getURL();
		String protocol = url.getProtocol();
		try {
			if ("file".equals(protocol)) {
				//already local, so don't bother downloading.
				return new File(URIUtil.toURI(url));
			}
		} catch (URISyntaxException e) {
			FrameworkCoreActivator.log(e);
		}

		String filename = item.getFileName();

		File target = new File(cacheDirectory, filename);
		return target;
	}

	/**
	 * This method tries to download or fetch a File from the cache, then passes the
	 * downloaded file to the DownloadRequestor.
	 * <p>
	 * If the requestor fails to properly execute on the downloaded file, the cache
	 * will be presumed to be corrupt. The file will be deleted from the cache
	 * and the download will be tried again. (for a limited number of times)
	 */
	public void doWithDownload(DownloadableItem target, DownloadRequestor action) throws Exception {
		int tries = getTries(); // try at most X times
		Throwable e = null;
		File downloadedFile = null;
		do {
			tries--;
			try {
				downloadedFile = downloadFile(target);
				action.exec(downloadedFile);
				return; // action succeeded without exceptions
			} catch (UIThreadDownloadDisallowed caught) {
				//No sense retrying this as it will just fail again...
				throw caught;
			} catch (Throwable caught) {
				FrameworkCoreActivator.log(caught);
				//Presume the cache may be corrupt!
				//System.out.println("Delete corrupt download: "+downloadedFile);
				if (downloadedFile != null) {
					downloadedFile.delete();
					downloadedFile = null;
				}
				e = caught;
				if (tries > 0 && retryInterval > 0) {
					Thread.sleep(retryInterval);
				}
			}
		} while (tries > 0);
		//Can only get here if action failed to execute on downloaded file...
		//thus, e can not be null.
		throw ExceptionUtil.exception(e);
	}

	/**
 	 * @since 3.6.3
	 */
	public int getTries() {
		return tries;
	}

	/**
	 * Sets the maximum number of times DownloadManager will try and retyr to fetch
	 * a failed download. Note that the first try also counts. So if 'tries' is
	 * set to 3 then a failed download will be retried upto 2 times. I.e. one
	 * try and then 2 retries.
	 *
	 * @since 3.6.3
	 */
	public DownloadManager setTries(int r) {
		//Setting to 0 is not sensible because it would mean to just fail without even trying.
		Assert.isLegal(r > 1);
		this.tries = r;
		return this;
	}

	/**
	 * Sets the time in milliseconds to wait between retries. The default is 0 meaning no
	 * wait.
	 *
	 * @since 3.6.3
	 */
	public void setRetryInterval(long retryInterval) {
		Assert.isLegal(retryInterval >= 0);
		this.retryInterval = retryInterval;
	}

	/**
	 * @since 3.6.3
	 */
	public long getRetryInterval(long retryInterval) {
		return retryInterval;
	}

	public File getCacheDir() {
		return cacheDirectory;
	}

	public boolean isDownloaded(DownloadableItem item) {
		File target = getLocalLocation(item);
		return target != null && target.exists();
	}

	/**
	 * Dispose this download manager. Optionally delete its cache directory as well.
	 */
	public void dispose() {
		if (deleteCacheOnDispose) {
			FileUtils.deleteQuietly(cacheDirectory);
			deleteCacheOnDispose = false;
		}
	}

	/**
	 * If this is false, an exception will be thrown if download is attempted on
	 * the UI thread. The default value is true.
	 */
	public DownloadManager allowUIThread(boolean allow) {
		allowUIThread = allow;
		return this;
	}

}
