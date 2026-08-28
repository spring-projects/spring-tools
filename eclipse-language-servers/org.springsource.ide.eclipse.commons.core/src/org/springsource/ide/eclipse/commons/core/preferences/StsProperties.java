/*******************************************************************************
 * Copyright (c) 2012, 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.preferences;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;

/**
 * An instance of this class provides a mechanism to retrieve String properties.
 * This is similar to a Java system properties. However it has support for reading these
 * properties from a fixed url. This allows us to change the properties after release.
 * <p>
 * Properties in this class can come from several different sources, listed here in
 * decreasing order of priority:
 *
 *  1) Java System properties (set via -Dmy.prop.name=value) in STS.ini
 *     (properties set this way override anything else).
 *  2) default values hard-coded in this class.
 *     (used only if property was not set via either 1 or 2).
 *
 * @since 3.4.M1
 * @since 3.8.4 : no more reading props from url. Only hard-coded or system props used (too many
 *   issues with the network accesses blocking UI thread.)
 *
 * @author Kris De Volder
 */
public class StsProperties {


	//Note: there is also a class called 'ResourceProvider'.. which reads various properties
	// from eclipse extension points. This is different because the STSProperties themselves
	// are read from an external url.
	//The ResourceProvider only allows properties to defined by extensions contained in plugins
	// installed into the Ecliple platform.

	/**
	 * This class is a singleton. This holds the instance once created.
	 */
	private static StsProperties instance = null;

	public static StsProperties getInstance(IProgressMonitor mon) {
		if (instance == null) {
			StsProperties newInstance = new StsProperties(mon);
			instance = newInstance;
		}
		return instance;
	}

	private final Properties props;

	private StsProperties(IProgressMonitor mon) {
		mon.beginTask("Read Sts Properties", 11);
		try {
			props = createProperties();
		} finally {
			mon.done();
		}
	}

	protected Properties createProperties() {
		Properties props = new Properties();

		// Default properties (guarantees certain properties have a value no
		// matter what).

		props.put("spring.site.url", "https://spring.io");
		props.put("spring.initializr.json.url", "https://start.spring.io");

		//Urls used in the dashboard. For each XXX.url=... property, if
		//  - XXX.url.label is defined that label will be used for the corresponding
		//     dashboard tab instead of the html page title (title tends to be too long).
		//  - XXX.url.external is defined that url will always be openened in an external browser.

		//Docs
		props.put("spring.docs.url", "https://spring.io/docs");
		props.put("spring.docs.url.label", "Spring Docs");
		props.put("spring.docs.url.external", "false");

		//Blog
		props.put("spring.blog.url", "https://spring.io/blog");
		props.put("spring.blog.url.label", "Blog");
		props.put("spring.blog.url.external", "false");

		//Tracker:
		props.put("sts.tracker.url", "https://github.com/spring-projects/spring-ide/issues");
		props.put("sts.tracker.url.label", "Issues");
		props.put("sts.tracker.url.external", "true");

		//New and Noteworthy
		props.put("sts.nan.url", "https://static.springsource.org/sts/nan/latest/NewAndNoteworthy.html");
		props.put("sts.nan.url.label", "New and Noteworthy");
		props.put("sts.nan.url.external", "false");

		//Forum:
		props.put("sts.forum.url", "https://forum.springsource.org/forumdisplay.php?32-SpringSource-Tool-Suite");
		props.put("sts.forum.url.label", "Forum");
		props.put("sts.forum.url.external", "false");

		//Guides
		props.put("spring.guides.url", "https://spring.io/guides");
		props.put("spring.guides.url.label", "Guides");
		props.put("spring.guides.url.external", "true");

		//Spring boot runtime
		props.put("spring.boot.install.url", "https://repo.spring.io/release/org/springframework/boot/spring-boot-cli/2.1.15.RELEASE/spring-boot-cli-2.1.15.RELEASE-bin.zip");
		//Discovery url for spring reference app
		props.put("spring.reference.app.discovery.url", "https://raw.github.com/kdvolder/spring-reference-apps-meta/master/reference-apps.json");

		//Url for webservice that generates typegraph for spring boot jar type content assist
		props.put("spring.boot.typegraph.url", "https://aetherial.cfapps.io/boot/typegraph");

		//Default version of spring boot, assumed when we need a version but can't determine it from the classpath of the project.
		//Typically this should point to the latest version of spring-boot (the one used by spring-initialzr app).
		props.put("spring.boot.default.version", "2.1.15.RELEASE");
		props.put("spring.boot.cloud.default.version", "2.1.0.RELEASE");

		return props;
	}

	/**
	 * Procudes names of properties that have explicitly been set, either from properties file
	 * or by the explicitly provided defaults.  More precisely this does not return
	 * properties simply inherited from Java system properties.
	 */
	public Collection<String> getExplicitProperties() {
		ArrayList<String> keys = new ArrayList<String>();
		for (Object string : props.keySet()) {
			if (string instanceof String) {
				keys.add((String) string);
			}
		}
		return keys;
	}

	public String get(String key) {
		String value = System.getProperty(key);
		if (value == null) {
			value = props.getProperty(key);
		}
		return value;
	}

	public boolean get(String key, boolean deflt) {
		String value = get(key);
		if (value != null) {
			return Boolean.valueOf(value);
		}
		return deflt;
	}

	public static StsProperties getInstance() {
		return getInstance(new NullProgressMonitor());
	}

	public URL url(String prop) {
		String s = get(prop);
		try {
			if (s != null) {
				return new URL(get(prop));
			}
		} catch (Exception e) {
			CorePlugin.log(e);
		}
		return null;
	}

}
