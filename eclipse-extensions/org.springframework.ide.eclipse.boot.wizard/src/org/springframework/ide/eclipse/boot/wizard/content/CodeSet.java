/*******************************************************************************
 *  Copyright (c) 2013, 2018 Pivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.content;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.springsource.ide.eclipse.commons.core.util.OsUtils;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadableItem;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;

/**
 * A CodeSet represents a bunch of content that can somehow be imported into
 * the workspace to create a project.
 * <p>
 * A CodeSet instance is more like a handle to content than actual content.
 * I.e. it contains enough information to fetch the content but doesn't guarantee that the
 * content actually exists.
 * <p>
 * This means it is possible to create CodeSet instances without having to pay the
 * cost of downloading the Zip file upfront. Some operations on CodeSets however
 * will force download to be attempted.
 *
 * @author Kris De Volder
 */
public abstract class CodeSet {

	public interface AfterCreateHook {
		void afterCreateAt(final File location) throws Exception;
	}

	public static AfterCreateHook afterCreateHook = null;

	private static final boolean IS_WINDOWS = OsUtils.isWindows();

	/**
	 * Represents an Entry in a CodeSet. API similar to ZipEntry but paths may be remapped
	 * as they are relative to the root of the code set not the Zip (or other source of data)
	 */
	public abstract class CodeSetEntry {

		public abstract IPath getPath();

		public abstract boolean isDirectory();

		public abstract InputStream getData() throws IOException;

		/**
		 * If a given implementation of CodeSetEntry has the means to determine
		 * permission flags, then it can override this method.
		 */
		public int getUnixMode() {
			return 0;
		}

	}

	public static abstract class Processor<T> {

		/**
		 * Do some work an a ZipEntry in a CodeSet.
		 */
		public abstract T doit(CodeSetEntry e) throws Exception;
	}

	protected String name;
	private List<BuildType> buildTypes;

	public CodeSet(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public static CodeSet fromZip(String name, DownloadableItem zip, IPath root) {
		return new ZipFileCodeSet(name, zip, root);
	}

	/**
	 * Fetch list of build types that are supported by the CodeSet. Note that
	 * determining this information requires inspecting some of the directory
	 * entries in the CodeSet and therefore it requires downloading the
	 * content / zip / repo the CodeSet is contained in (unless it is
	 * already cached locally).
	 */
	public List<BuildType> getBuildTypes() throws UIThreadDownloadDisallowed {
		//Only use 'default' build type if no other build type works.
		if (this.buildTypes == null) {
			//Careful if the code in here throws (e.g. because not yet downloaded and in UI thread...
			//then do NOT stick an empty list into this.buildTypes!
			ArrayList<BuildType> buildTypes = new ArrayList<BuildType>();
			for (BuildType type : BuildType.values()) {
				IPath scriptFile = type.getBuildScript();
				if (scriptFile != null && hasFile(scriptFile)) {
					buildTypes.add(type);
				}
			}
			if (buildTypes.isEmpty()) {
				buildTypes.add(BuildType.GENERAL);
			}
			this.buildTypes = buildTypes;
		}
		return this.buildTypes;
	}

	/**
	 * Returns true if content for this codeset can be downloaded and
	 * contains some data.
     *
     * If the CodeSet was not previously downloaded this will force a
     * download.
	 */
	public abstract boolean exists() throws Exception;

	/**
	 * Returns true if this codeset has a file with a given path.
	 *
     * If the CodeSet was not previously downloaded this will force a
     * download.
	 * @throws UIThreadDownloadDisallowed
	 */
	public abstract boolean hasFile(IPath path) throws UIThreadDownloadDisallowed;

	/**
	 * Returns true if this codeset has a folder with a given path.
	 *
     * If the CodeSet was not previously downloaded this will force a
     * download.
	 */
	public abstract boolean hasFolder(IPath path);

	/**
	 * Convenience method that allows passing paths as Strings instead of IPath instances
	 */
	public final boolean hasFile(String path) throws UIThreadDownloadDisallowed {
		return hasFile(new Path(path));
	}

	/**
	 * Perform some work on all content element in this codeSet.
	 * The processor may return a non-null value or throw an Exception to
	 * stop processing in the middle of the iteration.
	 */
	public abstract <T> T each(Processor<T> processor) throws Exception;

	/**
	 * Copies the contents of codeset to a given filesystem location
	 */
	public void createAt(final File location) throws Exception {
		if (location.exists()) {
			if (!FileUtils.deleteQuietly(location)) {
				throw new IOException("Data already exists at location and it could not be deleted: "+location);
			}
		}
		java.nio.file.Path normalizedLocationPath = location.toPath().normalize();
		each(new CodeSet.Processor<Void>() {
			@Override
			public Void doit(CodeSetEntry e) throws Exception {
				IPath path = e.getPath();
				File target = new File(location, path.toString());
				assertCorrectOutputLocation(normalizedLocationPath, target.toPath().normalize());
				if (e.isDirectory()) {
					target.mkdirs();
				} else {
					IOUtil.pipe(e.getData(), target);
					if (!IS_WINDOWS) {
						int mode = e.getUnixMode();
						if (mode > 0) {
							Files.setPosixFilePermissions(target.toPath(),  OsUtils.posixFilePermissions(mode));
						}
					}
				}
				return null;
			}
		});
		if (afterCreateHook != null) {
			afterCreateHook.afterCreateAt(location);
		}
	}

	/**
	 * Checks output location folder and target file location
	 * @param normalizedLocation normalized location folder path
	 * @param normalizedTarget normalized target file path
	 * @throws Exception throws exception if there is a problem
	 */
	protected void assertCorrectOutputLocation(java.nio.file.Path normalizedLocation, java.nio.file.Path normalizedTarget) throws Exception {

	}

	public ValidationResult validateBuildType(BuildType buildType) throws UIThreadDownloadDisallowed {
		List<BuildType> validBuildTypes = getBuildTypes();
		if (validBuildTypes.contains(buildType)) {
			return ValidationResult.OK;
		}
		//Not valid formulate a nice explanation
		IPath expectedScript = buildType.getBuildScript();
		if (expectedScript != null) {
			return ValidationResult.error("Can't use '"+buildType.displayName()+"': There is no '"+expectedScript+"'");
		} else {
			StringBuilder message = new StringBuilder("Should be imported as ");
			boolean first = true;
			for (BuildType bt : validBuildTypes) {
				if (!first) {
					message.append(" or ");
				}
				message.append("'"+bt.displayName()+"'");
				first = false;
			}
			return ValidationResult.error(message.toString());
		}
	}

	/**
	 * Read a single entry from a codeset. Note that this operation might be expensive because
	 * it might open and close a zipfile for a ZipFileCodeSet).
	 */
	public abstract <T> T readFileEntry(String path, Processor<T> processor) throws Exception;

}
