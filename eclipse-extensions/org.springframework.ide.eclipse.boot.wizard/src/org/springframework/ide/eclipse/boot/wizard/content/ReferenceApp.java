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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.boot.wizard.github.GithubClient;
import org.springframework.ide.eclipse.boot.wizard.github.Repo;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class ReferenceApp extends GithubRepoContent {

	/**
	 * We expect a readme file of sorts, at the root of the
	 * project, but the name may vary.
	 */
	public static final String[] readmes = {
		"README.md",
		"readme.md",
		"readme.MD",
		"README.MD",
		"readme.txt",
		"readme.TXT",
		"README.txt",
		"README.TXT",
		"readme",
		"README"
	};

	/**
	 * Optional additional metadata (if list of content was fetched from a random
	 * url, it may contain supplementary data. E.g. a name or description that
	 * is intended to override the description from the repo.
	 */
	private final ReferenceAppMetaData metadata;
	private final GithubClient github;

	/**
	 * The github repo containing the content of this ReferenceApp.
	 * We initialize this lazyly because it requires a github api
	 * call to create the repo instance.
	 * <p>
	 * If the metadata object contains enough info then we won't need
	 * to actually create the repo instance to construct the data.
	 */
	private Repo repo;

	/**
	 * Lazy intialized reference to the CodeSet with all the content
	 * of the reference app.
	 */
	private CodeSet codeset;

	public static final String REFERENCE_APP_DESCRIPTION = "A reference app is a larger, complete application that shows how to use spring properly in a more realistic context.";

	public ReferenceApp(ReferenceAppMetaData md, DownloadManager dl, GithubClient gh) {
		super(dl);
		Assert.isNotNull(md);
		Assert.isNotNull(md.getOwner());
		Assert.isNotNull(md.getRepo());
		this.metadata = md;
		this.github = gh;
	}

	@Override
	public String getName() {
		String name = metadata.getName();
		if (name != null) {
			return name;
		}
		return metadata.getRepo();
	}

	@Override
	public Repo getRepo() {
		if (this.repo == null) {
			this.repo = github.getRepo(metadata.getOwner(), metadata.getRepo());
		}
		return this.repo;
	}

	public CodeSet getCodeSet() throws UIThreadDownloadDisallowed {
		if (codeset == null) {
			codeset = CodeSet.fromZip(getName(), getZip(), getRootPath());
		}
		return codeset;
	}

	/**
	 * @return path to readme file in the codeset.
	 */
	public String getReadme() throws UIThreadDownloadDisallowed {
		CodeSet cs = getCodeSet();
		for (String name : readmes) {
			if (cs.hasFile(name)) {
				return name;
			}
		}
		return null;
	}

	public List<BuildType> getBuildTypes() throws UIThreadDownloadDisallowed {
		return getCodeSet().getBuildTypes();
	}

	@Override
	public String getDisplayName() {
		//TODO: beatify the name
		return getName();
	}

	@Override
	public List<CodeSet> getCodeSets() throws UIThreadDownloadDisallowed {
		return Arrays.asList(getCodeSet());
	}

	@Override
	protected String getBranch() {
		String b = this.metadata.getBranch();
		return StringUtil.hasText(b) ? b : "HEAD";
	}
}
