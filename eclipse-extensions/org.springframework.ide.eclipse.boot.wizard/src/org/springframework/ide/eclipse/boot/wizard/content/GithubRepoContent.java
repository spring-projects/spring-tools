/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.content;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet.CodeSetEntry;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet.Processor;
import org.springframework.ide.eclipse.boot.wizard.github.Repo;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadableItem;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

public abstract class GithubRepoContent extends AGSContent {

	private DownloadableItem zip;

	protected GithubRepoContent(DownloadManager dl) {
		super(dl);
	}

	@Override
	public URL getHomePage() {
		try {
			return new URL(getRepo().getHtmlUrl());
		} catch (MalformedURLException e) {
			BootWizardActivator.log(e);
			return null;
		}
	}

	public abstract Repo getRepo();

	/**
	 * Get a URL pointing to zip file where the entire contents of this
	 * repo (master branch) can be downloaded.
	 */
	@Override
	public DownloadableItem getZip() {
		if (zip == null) {
			String repoUrl = getRepo().getHtmlUrl();
			//repoUrl is something like "https://github.com/springframework-meta/gs-consuming-rest-android"
			//zipUrl is something like  "https://github.com/springframework-meta/gs-consuming-rest-android/archive/master.zip"
			try {
				DownloadableItem item = new DownloadableItem(getZipDownloadUrl(repoUrl), downloader);
				item.setFileName(getRepo().getName());
				zip = item;
			} catch (MalformedURLException e) {
				BootWizardActivator.log(e);
				return null;
			}
		}
		return zip;
	}

	private URL getZipDownloadUrl(String repoUrl) throws MalformedURLException {
		return new URL(repoUrl+"/archive/"+getBranch()+".zip");
	}

	@Override
	public String getName() {
		return getRepo().getName();
	}

	/**
	 * Defines the location of the 'root' relative to the zip file. The interesting contents
	 * of the zip file may not be directly at the root of the archive.
	 * <p>
	 * Note this method is made public for testing purposes only. Clients shouldn't really
	 * need to get at this information. Rather they should rely on the 'CodeSets' to
	 * access this data.
	 */
	public final IPath getRootPath() throws UIThreadDownloadDisallowed {
		//tmp codeset to aid in discovering the real root path.
		CodeSet tmp = CodeSet.fromZip("TEMP", zip, Path.ROOT);
		Path guessedRoot = new Path(getRepo().getName()+"-"+getBranch());
		if (tmp.hasFolder(guessedRoot)) {
			return guessedRoot;
		}
		try {
			return tmp.each(new Processor<IPath>() {
				@Override
				public IPath doit(CodeSetEntry e) throws Exception {
					return e.isDirectory() ? e.getPath() : null;
				}
			});
		} catch (UIThreadDownloadDisallowed e) {
			throw e;
		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}
	}

	protected String getBranch() {
		return "HEAD";
	}

	@Override
	public String getDescription() {
		return getRepo().getDescription();
	}

}
