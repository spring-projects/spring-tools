/*******************************************************************************
 *  Copyright (c) 2013, 2016 Pivotal Software, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubMonitor;
import org.springframework.ide.eclipse.boot.wizard.github.GithubClient;
import org.springframework.ide.eclipse.boot.wizard.github.Repo;
import org.springsource.ide.eclipse.commons.core.preferences.StsProperties;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Singleton class. The instance of this class provides access to all the
 * getting started content.
 *
 * NOTE: templates are not (yet?) included in this. Code to discover and
 * manage them already existed before this framework was implemented.
 */
public class GettingStartedContent extends ContentManager {

	 // IMPORTANT NOTE: Because this is a singleton class,
	// CARE needs to be taken with any listeners registered, especially in live expressions.
	// The ContentManager super class has two tracker LiveVariables to track
	// registration of content providers and downloading of content.
	// These two are publicly exposed and may result in listeners being registered by owners.
	// To avoid memory leaks, be sure that any live expressions that accumulate listeners are properly disposed

	private static GettingStartedContent INSTANCE = null;

	private final static boolean ADD_REAL =  true;
	private final static boolean ADD_MOCKS = false; // (""+Platform.getLocation()).contains("kdvolder")

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder")
				|| (""+Platform.getLocation()).contains("bamboo");

	public static GettingStartedContent getInstance() {
	   if (INSTANCE == null) {
			INSTANCE = new GettingStartedContent();
		}
		return INSTANCE;
	}

	@Override
	protected void prefetch(IProgressMonitor monitor) {
		// Register the content providers as part of prefetching, as registering
		// the providers may also require network access much like downloading content.
		String registeringProvidersLabel = "Registering content providers";
		registerAllContentProviders(SubMonitor.convert(monitor, registeringProvidersLabel, 50));
		super.prefetch(monitor);
	}

	private final GithubClient github = new GithubClient();


	/**
	 * We need this in multiple places. So cache it to avoid asking for it multiple times in a row.
	 */
	private Repo[] cachedRepos = null;

	private Repo[] getGuidesRepos() {
		if (cachedRepos == null) {
			Repo[] repos = github.getOrgRepos("spring-guides");
			Arrays.sort(repos, new Comparator<Repo>() {
				@Override
				public int compare(Repo o1, Repo o2) {
					return o1.getName().compareTo(o2.getName());
				}
			});
			if (DEBUG) {
//				System.out.println("==== spring-guides-repos ====");
//				int count = 1;
//				for (Repo r : repos) {
//					System.out.println(count++ + ":" + r.getName());
//				}
//				System.out.println("==== spring-guides-repos ====");
			}
			cachedRepos = repos;
		}
		return cachedRepos;
	}

	/**
	 * Registering content providers may require network access if content provider properties
	 * need to be fetched external. Avoid running in UI thread.
	 * @param monitor must not be null.
	 */
	protected void registerAllContentProviders(IProgressMonitor monitor) {
		// Avoid registering and downloading content properties if already registered
		if (prefetchContentProviderPropertiesTracker.getValue() != DownloadState.DOWNLOADED) {
			try {
				prefetchContentProviderPropertiesTracker.setValue(DownloadState.IS_DOWNLOADING);
				registerAllWithStsProperties(StsProperties.getInstance(monitor));
				prefetchContentProviderPropertiesTracker.setValue(DownloadState.DOWNLOADING_COMPLETED);
			} finally {
				prefetchContentProviderPropertiesTracker.setValue(DownloadState.DOWNLOADED);
			}
		}
	}

	/**
	 * Will register all content providers using STS properties.
	 *
	 */
	public void registerAllWithStsProperties(final StsProperties stsProps) {
		//Guides: are discoverable because they are all repos in org on github
		register(GettingStartedGuide.class, GettingStartedGuide.GUIDE_DESCRIPTION_TEXT,
			new ContentProvider<GettingStartedGuide>() {
//					@Override
				@Override
				public GettingStartedGuide[] fetch(DownloadManager downloader) {
					LinkedHashMap<String, GettingStartedGuide> guides = new LinkedHashMap<String, GettingStartedGuide>();
					if (ADD_MOCKS) {
						addGuidesFrom(github.getMyRepos(), guides, downloader);
					}
					if (ADD_REAL) {
						addGuidesFrom(getGuidesRepos(), guides, downloader);
					}
					return guides.values().toArray(new GettingStartedGuide[guides.size()]);
				}

				private LinkedHashMap<String, GettingStartedGuide> addGuidesFrom(Repo[] repos, LinkedHashMap<String, GettingStartedGuide> guides, DownloadManager downloader) {
					for (Repo repo : repos) {
						String name = repo.getName();
//					System.out.println("repo : "+name + " "+repo.getUrl());
						if (name.startsWith("gs-") && !guides.containsKey(name)) {
							guides.put(name, new GettingStartedGuide(stsProps, repo, downloader));
						}
					}
					return guides;
				}
			}
		);

// Commented out: there are no more tutorial guides.
//			register(TutorialGuide.class, TutorialGuide.GUIDE_DESCRIPTION_TEXT,
//				new ContentProvider<TutorialGuide>() {
//					public TutorialGuide[] fetch(DownloadManager downloader) {
//						LinkedHashMap<String, TutorialGuide> guides = new LinkedHashMap<String, TutorialGuide>();
//						addGuidesFrom(getGuidesRepos(), guides, downloader);
//						return guides.values().toArray(new TutorialGuide[guides.size()]);
//					}
//
//					private LinkedHashMap<String, TutorialGuide> addGuidesFrom(Repo[] repos, LinkedHashMap<String, TutorialGuide> guides, DownloadManager downloader) {
//						for (Repo repo : repos) {
//							String name = repo.getName();
//							if (name.startsWith("tut-") && !guides.containsKey(name)) {
//								guides.put(name, new TutorialGuide(stsProps, repo, downloader));
//							}
//						}
//						return guides;
//					}
//				}
//			);


		//References apps: are discoverable because we maintain a list of json metadata
		//that can be downloaded from some external url.
		register(ReferenceApp.class, ReferenceApp.REFERENCE_APP_DESCRIPTION,
			new ContentProvider<ReferenceApp>() {

			@Override
			public ReferenceApp[] fetch(DownloadManager downloader) {
				ReferenceAppMetaData[] infos = github.get(stsProps.get("spring.reference.app.discovery.url"), ReferenceAppMetaData[].class);
				ReferenceApp[] apps = new ReferenceApp[infos.length];
				for (int i = 0; i < apps.length; i++) {
					//TODO: it could be quite costly to create all these since each one
					// entails a request to obtain info about github repo.
					// Maybe this is a good reason to put a bit more info in the
					// json metadata and thereby avoid querying github to fetch it.
					apps[i] = create(downloader, infos[i]);
				}
				return apps;
			}

			private ReferenceApp create(DownloadManager dl, ReferenceAppMetaData md) {
				return new ReferenceApp(md, dl, github);
			}

		});
	}



	/**
	 * Get all getting started guides.
	 */
	public GettingStartedGuide[] getGSGuides() {
		return get(GettingStartedGuide.class);
	}

	/**
	 * Get all tutorial guides
	 */
	public TutorialGuide[] getTutorials() {
		return get(TutorialGuide.class);
	}

	public ReferenceApp[] getReferenceApps() {
		return get(ReferenceApp.class);
	}

	/**
	 * Get all guide content (i.e. tutorials + gs)
	 */
	public GithubRepoContent[] getAllGuides() {
		ArrayList<GithubRepoContent> all = new ArrayList<GithubRepoContent>();
		all.addAll(asList(getTutorials()));
		all.addAll(asList(getGSGuides()));
		return all.toArray(new GithubRepoContent[all.size()]);
	}

	private <A> Collection<A> asList(A[] tutorials) {
		if (tutorials == null) {
			return Collections.emptyList();
		} else {
			return Arrays.asList(tutorials);
		}
	}

//	public GettingStartedGuide getGuide(String guideName) {
//		GettingStartedGuide[] guides = getGuides();
//		if (guides != null) {
//			for (GettingStartedGuide g : guides) {
//				if (guideName.equals(g.getName())) {
//					return g;
//				}
//			}
//		}
//		return null;
//	}
}
