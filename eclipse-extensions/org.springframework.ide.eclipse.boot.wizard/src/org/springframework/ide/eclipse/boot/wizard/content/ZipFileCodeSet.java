/*******************************************************************************
 *  Copyright (c) 2013 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.content;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadableItem;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;

/**
 * A CodeSet stored in a Downloadable Zip File. The interesting data in the
 * code set may be somewhere inside the zip file and is pointed to by
 * a path relative to the zipfile root.
 */
public class ZipFileCodeSet extends CodeSet {

	private static final boolean DEBUG = false; //(""+Platform.getLocation()).contains("kdvolder");

	private void debug(String msg) {
		if (DEBUG) {
			System.out.println(msg);
		}
	}

	private final DownloadableItem zipDownload;
	private final IPath root;

	private Map<String, CodeSetEntry> entries = null;

	/**
	 * Ensures that zip file is downloaded and entries are parsed
	 * into a map. Only the first time this method is called do
	 * we do any actual work. The zip entries are cached
	 * after that.
	 */
	private synchronized void ensureEntryCache() throws Exception, UIThreadDownloadDisallowed {
		//Careful... if called in UIThred this may throw an exception because downloading content
		// in the UI thread is not allowed. Callers generally know how to deal with that but
		// we should take care not to accidentally leave an empty hashmap around as well.
		// I.e. take care not to install the map unless reading download etc of the
		// zip succeeded.
		if (entries == null) {
			debug(">>> caching codeset entries "+this);
			final HashMap<String, CodeSetEntry> newEntries = new HashMap<String, CodeSetEntry>(1024);
			each(new Processor<Void>() {
				@Override
				public Void doit(CodeSetEntry e) throws Exception {
					debug(e.getPath().toString());
					newEntries.put(e.getPath().toString(), e);
					return null;
				}
			});
			//Nothing bad happened. The cache map is ready now.
			this.entries = newEntries;
			debug("<<< cached codeset entries "+entries.size());
		}
	}

	ZipFileCodeSet(String name, DownloadableItem zip, IPath root) {
		super(name);
		this.zipDownload = zip;
		this.root = root.makeRelative();
	}

	@Override
	public String toString() {
		return "ZipCodeSet("+name+", "+zipDownload.getURL()+"@"+root+")";
	}

	@Override
	public boolean exists() throws Exception {
		ensureEntryCache();
		return !entries.isEmpty();
	}

	@Override
	public boolean hasFile(IPath path) throws UIThreadDownloadDisallowed {
		try {
			ensureEntryCache();
			return entries.containsKey(fileKey(path));
		} catch (UIThreadDownloadDisallowed e) {
			throw e;
		} catch (Exception e) {
			BootWizardActivator.log(e);
		}
		return false;
	}

	@Override
	public boolean hasFolder(IPath path) {
		try {
			ensureEntryCache();
			return entries.containsKey(folderKey(path));
		} catch (UIThreadDownloadDisallowed e) {
			//ignore. Sort of expected in some scenarios (waiting for download).
		} catch (Exception e) {
			BootWizardActivator.log(e);
		}
		return false;
	}

	private String folderKey(IPath _path) {
		String path = fileKey(_path);
		if (!path.endsWith("/")) {
			//ZipEntries for dirs always end with a "/"
			path = path+"/";
		}
		return path;
	}

	private String fileKey(IPath path) {
		path = path.makeRelative();
		return path.toString();
	}

	@Override
	public <T> T each(Processor<T> processor) throws Exception {
		T result = null;
		ZipFile zip = new ZipFile(zipDownload.getFile());
		try {
			Enumeration<ZipArchiveEntry> iter = zip.getEntries();
			while (iter.hasMoreElements() && result == null) {
				ZipArchiveEntry el = iter.nextElement();
				Path zipPath = new Path(el.getName());
				if (root.isPrefixOf(zipPath)) {
					String key = zipPath.removeFirstSegments(root.segmentCount()).toString();
					if ("".equals(key)) {
						//path maches exactly, this means we hit the root of the
						// code set. Do not store it because the root of a codeset
						// is not actually an element of the codeset!
					} else {
						CodeSetEntry cse = csEntry(zip, el);
						result = processor.doit(cse);
						if (result != null) {
							//Bail out early when result found
							return result;
						}
					}
				}
			}
			return result;
		} finally {
			try {
				zip.close();
			} catch (IOException e) {
			}
		}
	}

	@Override
	public <T> T readFileEntry(String path, Processor<T> processor) throws Exception {
		ZipFile zip = new ZipFile(zipDownload.getFile());
		try {
			String entryName = root.append(path).toString();
			ZipArchiveEntry entry = zip.getEntry(entryName);
			return processor.doit(entry == null ? null : csEntry(zip, entry));
		} finally {
			try {
				zip.close();
			} catch (IOException e) {
			}
		}
	}

	@Override
	protected void assertCorrectOutputLocation(java.nio.file.Path normalizedLocation, java.nio.file.Path normalizedTarget) throws Exception {
		if (!normalizedTarget.startsWith(normalizedLocation)) {
			throw new ZipException("The file " + normalizedTarget
					+ " is trying to leave the target output directory of " + normalizedLocation);
		}
	}

	/**
	 * Create a CodeSetEntry that wraps a ZipEntry
	 */
	private CodeSetEntry csEntry(final ZipFile zip, final ZipArchiveEntry e) {
		IPath zipPath = new Path(e.getName()); //path relative to zip file
		Assert.isTrue(root.isPrefixOf(zipPath));
		final IPath csPath = zipPath.removeFirstSegments(root.segmentCount());
		return new CodeSetEntry() {
			@Override
			public IPath getPath() {
				return csPath;
			}

			@Override
			public String toString() {
				return getPath()+" in "+zipDownload;
			}

			@Override
			public boolean isDirectory() {
				return e.isDirectory();
			}

			@Override
			public int getUnixMode() {
				return e.getUnixMode();
			}

			@Override
			public InputStream getData() throws IOException {
				return zip.getInputStream(e);
			}
		};
	}

}