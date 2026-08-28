/*******************************************************************************
 *  Copyright (c) 2013 VMware, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.content;

import java.io.File;
import java.io.IOException;

import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springframework.ide.eclipse.boot.wizard.github.auth.AuthenticatedDownloader;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;

/**
 * Abstract base class for implementing a content manager for a given type of
 * Content.
 */
class TypedContentManager<T> {

	private T[] items; //In-memory cache of info about the content in this content manager.

	private final DownloadManager downloader; //Manages file-system cache where downloaded content is stored.

	private final ContentProvider<T> contentProvider;

	protected TypedContentManager(DownloadManager downloader, ContentProvider<T> contentProvider) {
		this.downloader = downloader;
		this.contentProvider = contentProvider;
	}

	public T[] getAll() {
		if (items == null) {
			items = fetch(downloader);
		}
		return items;
	}

	private T[] fetch(DownloadManager downloader) {
		return contentProvider.fetch(downloader);
	}

	/**
	 * Factory method to create a DownloadManager for a given content type name
	 */
	public static DownloadManager downloadManagerFor(String contentTypeName) throws IOException {
		return new DownloadManager(new AuthenticatedDownloader(),
				new File(BootWizardActivator.getDefault().getStateLocation().toFile(), "guides")
		);
	}


}
