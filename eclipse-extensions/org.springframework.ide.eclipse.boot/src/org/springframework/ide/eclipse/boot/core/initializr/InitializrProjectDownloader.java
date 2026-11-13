/*******************************************************************************
 * Copyright (c) 2020, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.initializr;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

/**
 * Downloads projects from Initializr
 *
 */
public class InitializrProjectDownloader implements Disposable {

	private DownloadManager downloadManager;

	private final InitializrUrl initializrUrl;
	private final URLConnectionFactory urlConnectionFactory;

	public InitializrProjectDownloader(URLConnectionFactory urlConnectionFactory,
			InitializrUrl initializrUrl) {
		this.initializrUrl = initializrUrl;
		this.urlConnectionFactory = urlConnectionFactory;
	}

	/**
	 *
	 * @param dependencies
	 * @param bootProject
	 * @return generated project from initializr, using project information from the
	 *         given boot project and list of dependencies
	 * @throws Exception
	 */
	public File getProject(List<Dependency> dependencies, ISpringBootProject bootProject) throws Exception {
		DownloadManager manager = getDownloadManager();

		String url = initializrUrl
				.project(bootProject)
				.dependencies(dependencies)
				.build();

		DownloadableZipItem item = new DownloadableZipItem(new URL(url), manager);

		File file = item.getZipFile();

		return file;
	}

	private DownloadManager getDownloadManager() throws IOException {
		// dispose the existing one to clear the cache directory. Otherwise
		// subsequent download requests with different URLs will not download
		// as  the download manager relies  on the temp cache directory  and  file
		// name (as opposed to the download  URL) to decided  if  some
		if (downloadManager != null) {
			downloadManager.dispose();
		}
		downloadManager = new DownloadManager(urlConnectionFactory);

		return downloadManager;
	}

	@Override
	public void dispose() {
		if (downloadManager != null) {
			downloadManager.dispose();
		}
	}
}
