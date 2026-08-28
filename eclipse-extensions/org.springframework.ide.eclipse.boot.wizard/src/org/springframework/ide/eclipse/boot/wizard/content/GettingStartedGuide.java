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

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet.CodeSetEntry;
import org.springframework.ide.eclipse.boot.wizard.github.Repo;
import org.springsource.ide.eclipse.commons.core.preferences.StsProperties;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadableItem;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Content for a GettingStartedGuide provided via a Github Repo
 *
 * @author Kris De Volder
 */
public class GettingStartedGuide extends GithubRepoContent {

	protected Repo repo;

	private List<CodeSet> codesets;

	private final String springGuidesUrl;

	public static final String GUIDE_DESCRIPTION_TEXT =
			"A guide is a short focussed tutorial "
			+ "on how to use Spring to accomplish a specific task. "
			+ "It has an 'initial' code set, a 'complete' code"
			+ "set and a readme file explaining how you get from "
			+ "one to the other.";

	/**
	 * All getting started guides are supposed to have the same codesets names. This constant defines those
	 * names.
	 */
	public static final CodeSetMetaData[] defaultCodesets = {
		new CodeSetMetaData("initial").desciption(
				"Import this if you want to work " +
				"through the bulk of the guide, but skip basic project setup"
		),
		new CodeSetMetaData("complete").desciption(
				"A project representing the code in its 'complete' state, at " +
				"the end of the guide."
		)
	};

	/**
	 * Relative path from the 'root' codeset to where the optional
	 * metadata file is that describes codeset layout for projects
	 * that don't follow the default layout.
	 */
	private static final String CODE_SET_METADATA = ".codesets.json";

	private static String[] _defaultCodesetNames;
	public static String[] defaultCodesetNames() {
		if (_defaultCodesetNames == null) {
			String[] defaultCodesetNames = new String[defaultCodesets.length];
			for (int i = 0; i < defaultCodesets.length; i++) {
				defaultCodesetNames[i] = defaultCodesets[i].name;
			}
			_defaultCodesetNames = defaultCodesetNames;
		}
		return _defaultCodesetNames;
	}


	public GettingStartedGuide(StsProperties props, Repo repo, DownloadManager dl) {
		super(dl);
		this.springGuidesUrl = props.get("spring.guides.url");
		this.repo = repo;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName()+"("+getName()+")";
	}

	@Override
	public List<CodeSet> getCodeSets() throws UIThreadDownloadDisallowed {
		if (codesets == null) {
			DownloadableItem zip = getZip();
			try {
				CodeSet root = CodeSet.fromZip("ROOT", zip, getRootPath());
				if (root.hasFile(CODE_SET_METADATA)) {
					//TODO: we have to parse the metadata file and extract the codeset names and locations from it.
					CodeSetMetaData[] metadata = root.readFileEntry(CODE_SET_METADATA, new CodeSet.Processor<CodeSetMetaData[]>() {
						@Override
						public CodeSetMetaData[] doit(CodeSetEntry e) throws Exception {
							InputStream in = e.getData();
							try {
								ObjectMapper mapper = new ObjectMapper();
								return mapper.readValue(in, CodeSetMetaData[].class);
							} finally {
								in.close();
							}
						}
					});
					if (metadata == null) {
						metadata = new CodeSetMetaData[0];
					}
					CodeSet[] array = new CodeSet[metadata.length];
					for (int i = 0; i < array.length; i++) {
						String name = metadata[i].name;
						String dir = metadata[i].dir;
						Assert.isLegal(name != null, ".codesets.json objects must specify at least a 'name'.");
						Assert.isLegal(isSafeCodeSetSegment(name), "Illegal codeset 'name' in .codesets.json: "+name);
						Assert.isLegal(dir == null || isSafeCodeSetSegment(dir), "Illegal codeset 'dir' in .codesets.json: "+dir);
						if (dir == null) {
							dir = name; //Use the name as the default. The convention is that a codeset is in a sudirectory with the same name as
							            // the codeset name.
						}
						//'dir' can't be null at this point because of the assert above and the default value computed from the name
						IPath zipPath = getRootPath().append(dir);
						array[i] = CodeSet.fromZip(name, getZip(), zipPath);
					}
					//Success parsing .codesets.json and initialising codesets field.
					codesets = Arrays.asList(array);
					return codesets;
				}
			} catch (UIThreadDownloadDisallowed e) {
				throw e;
			} catch (Throwable e) {
				BootWizardActivator.log(e);
			}
			//We get here if either
			//   - there's no .codeset.json
			//   - .codeset.json is broken.
			CodeSet[] array = new CodeSet[defaultCodesetNames().length];
			for (int i = 0; i < array.length; i++) {
				array[i] = CodeSet.fromZip(defaultCodesetNames()[i], getZip(), getRootPath().append(defaultCodesetNames()[i]));
			}
			codesets = Arrays.asList(array);
		}
		return codesets;
	}

	@Override
	public Repo getRepo() {
		return this.repo;
	}

	private static boolean isSafeCodeSetSegment(String segment) {
		return !segment.isEmpty()
				&& !segment.contains("/")
				&& !segment.contains("\\")
				&& !segment.equals("..")
				&& !segment.equals(".");
	}

	private String beatify(String name) {
		if (name.startsWith("gs-")) {
			name = name.substring(3);
		}
		String[] words = name.split("\\-");
		StringBuilder buf = new StringBuilder();
		for (String w : words) {
			if (w.length() > 0) {
				buf.append(w.substring(0,1).toUpperCase());
				buf.append(w.substring(1));
			}
			buf.append(' ');
		}
		return buf.toString();
	}

	@Override
	public URL getHomePage() {
		//Looks like this now:
		//https://${spring.guides.url}/gs/spring-boot/
		try {
			String gsGuideName = getName();
			if (gsGuideName.startsWith("gs-")) {
				String guideName = gsGuideName.substring(3);
				return new URL(springGuidesUrl+"/gs/"+guideName);
			}
		} catch (MalformedURLException e) {
			BootWizardActivator.log(e);
		}
		//Fallback on default implementation if custom logic failed
		return super.getHomePage();
	}

	/**
	 * A more 'beautiful' name derived from the guide's repository name.
	 */
	@Override
	public String getDisplayName() {
		return beatify(getName());
	}

	/**
	 * Metadata elements parsed from .codesets.json file are represented as instances of
	 * this class.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	static public class CodeSetMetaData {
		@JsonProperty
		public String name;
		@JsonProperty
		public String dir;

		@JsonProperty
		public String description;

		/**
		 * No args constructor (needed for Jackson mapper).
		 */
		public CodeSetMetaData() {
		}

		public CodeSetMetaData(String name) {
			this.name = name;
			this.dir = name;
		}

		public CodeSetMetaData desciption(String d) {
			description = d;
			return this;
		}

		@Override
		public String toString() {
			return "CodeSetMD(name = "+name+", dir = "+dir+")";
		}
	}


}
