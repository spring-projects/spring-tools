/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.java;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ide.vscode.commons.Version;

public class Classpath {
	
	// Pattern copied from https://semver.org/
	private static final Pattern VERSION_PATTERN = Pattern.compile("^.+-(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:(-|\\.)((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?\\.jar$");

	public static final String ENTRY_KIND_SOURCE = "source";
	public static final String ENTRY_KIND_BINARY = "binary";
	public static final Classpath EMPTY = new Classpath(Collections.<CPE>emptyList(), null);

	private List<CPE> entries;
	private Jre jre;

	public Classpath(List<CPE> entries, Jre jre) {
		this.entries = entries;
		this.jre = jre;
	}

	public List<CPE> getEntries() {
		return entries;
	}

	public void setEntries(List<CPE> entries) {
		this.entries = entries;
	}
	
	public Jre getJre() {
		return jre;
	}

	public void setJre(Jre jre) {
		this.jre = jre;
	}

	@Override
	public String toString() {
		return "Classpath [entries=" + entries + ", jre=" + jre + "]";
	}

	public static class CPE {

		// TODO: it seems like a good idea to make all classpath entries the same in that they all have
		// - a place with source code
		// - a place with compiled code
		// - a place with java doc
		// So it seems like we should be able to chnage this so that the same named attribute is used
		// in both cases (rather then one be 'getOutputFolder' and one 'getPath' to obtain location of the compiled code).

		private String kind;

		private String path; // TODO: Change to File, Path or URL?
		private String outputFolder;
		private URL sourceContainerUrl;
		private URL javadocContainerUrl;
		private boolean isSystem = false;
		private boolean isOwn = false;
		private boolean isTest = false;
		private boolean isJavaContent = false;
		
		private Map<String, String> extra;
		
		transient private Version version;
		transient private String name;

		public CPE() {}

		public CPE(String kind, String path) {
			super();
			this.kind = kind;
			setPath(path);
		}

		public CPE(String kind, String path, Map<String, String> extra) {
			super();
			this.kind = kind;
			this.extra = extra;
			setPath(path);
		}
		
		public Map<String, String> getExtra() {
			return extra;
		}

		public void setExtra(Map<String, String> extra) {
			this.extra = extra;
		}

		public String getOutputFolder() {
			return outputFolder;
		}

		public void setOutputFolder(String outputFolder) {
			this.outputFolder = outputFolder;
		}

		public String getKind() {
			return kind;
		}

		public void setKind(String kind) {
			this.kind = kind;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public URL getJavadocContainerUrl() {
			return javadocContainerUrl;
		}

		public void setJavadocContainerUrl(URL javadocContainerUrl) {
			this.javadocContainerUrl = javadocContainerUrl;
		}

		public URL getSourceContainerUrl() {
			return sourceContainerUrl;
		}

		public void setSourceContainerUrl(URL sourceContainerUrl) {
			this.sourceContainerUrl = sourceContainerUrl;
		}

		public static CPE binary(String path) {
			return new CPE(ENTRY_KIND_BINARY, path);
		}

		public static CPE source(File sourceFolder, File outputFolder) {
			CPE cpe = new CPE(ENTRY_KIND_SOURCE, sourceFolder.getAbsolutePath());
			cpe.setOutputFolder(outputFolder.getAbsolutePath());
			return cpe;
		}

		public static CPE source(File sourceFolder, File outputFolder, Map<String, String> extra) {
			CPE cpe = new CPE(ENTRY_KIND_SOURCE, sourceFolder.getAbsolutePath(), extra);
			cpe.setOutputFolder(outputFolder.getAbsolutePath());
			return cpe;
		}
		
		public boolean isSystem() {
			return isSystem;
		}

		public void setSystem(boolean isSystem) {
			this.isSystem = isSystem;
		}

		public boolean isOwn() {
			return isOwn;
		}

		public void setOwn(boolean isOwn) {
			this.isOwn = isOwn;
		}

		public boolean isTest() {
			return isTest;
		}

		public void setTest(boolean isTest) {
			this.isTest = isTest;
		}

		public boolean isJavaContent() {
			return isJavaContent;
		}

		public void setJavaContent(boolean isJavaContent) {
			this.isJavaContent = isJavaContent;
		}

		@Override
		public String toString() {
			return "CPE [kind=" + kind + ", path=" + path + ", outputFolder=" + outputFolder + ", sourceContainerUrl="
					+ sourceContainerUrl + ", javadocContainerUrl=" + javadocContainerUrl + ", isSystem=" + isSystem
					+ ", isOwn=" + isOwn + ", isTest=" + isTest + ", isJavaContent=" + isJavaContent + ", extra="
					+ extra + "]";
		}

		@Override
		public int hashCode() {
			return Objects.hash(extra, isJavaContent, isOwn, isSystem, isTest, javadocContainerUrl, kind, outputFolder,
					path, sourceContainerUrl);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CPE other = (CPE) obj;
			return Objects.equals(extra, other.extra) && isJavaContent == other.isJavaContent && isOwn == other.isOwn
					&& isSystem == other.isSystem && isTest == other.isTest
					&& Objects.equals(javadocContainerUrl, other.javadocContainerUrl)
					&& Objects.equals(kind, other.kind) && Objects.equals(outputFolder, other.outputFolder)
					&& Objects.equals(path, other.path) && Objects.equals(sourceContainerUrl, other.sourceContainerUrl);
		}
		
		public Version getVersion() {
			if (version == null) {
				if (ENTRY_KIND_BINARY.equals(getKind()) && !isSystem) {
					version = getDependencyVersion(new File(getPath()).getName());
				}
			}
			return version;
		}

		public String getName() {
			if (name == null) {
				if (ENTRY_KIND_BINARY.equals(getKind()) && !isSystem) {
					name = getDependencyName(new File(getPath()).getName());
				}
			}
			return name;
		}

	}

	public static boolean isSource(CPE e) {
		return e!=null && Classpath.ENTRY_KIND_SOURCE.equals(e.getKind());
	}

	public static boolean isBinary(CPE e) {
		return e!=null && Classpath.ENTRY_KIND_BINARY.equals(e.getKind());
	}
	
	public static boolean isProjectSource(CPE e) {
		return isSource(e) && e.isOwn();
	}
	
	public static boolean isProjectJavaSource(CPE cpe) {
		return isProjectSource(cpe) && cpe.isJavaContent();
	}
	
	public static boolean isProjectNonTestJavaSource(CPE cpe) {
		return isProjectJavaSource(cpe) && !cpe.isTest();
	}
	
	public static boolean isProjectTestJavaSource(CPE cpe) {
		return isProjectJavaSource(cpe) && cpe.isTest();
	}

	static Version getDependencyVersion(String fileName) {
		Matcher matcher = VERSION_PATTERN.matcher(fileName);
		if (matcher.find() && matcher.groupCount() > 5) {
			String major = matcher.group(1);
			String minor = matcher.group(2);
			String patch = matcher.group(3);
			String qualifier = matcher.group(5);
			return new Version(Integer.parseInt(major), Integer.parseInt(minor), Integer.parseInt(patch), qualifier);
		}
		return null;
	}

	static String getDependencyName(String fileName) {
		Matcher matcher = VERSION_PATTERN.matcher(fileName);
		if (matcher.find()) {
			// Extract the name part before the version
			int versionStart = matcher.start(1);
			if (versionStart > 0) {
				// Remove the trailing dash or dot before the version
				String nameWithSeparator = fileName.substring(0, versionStart);
				if (nameWithSeparator.endsWith("-") || nameWithSeparator.endsWith(".")) {
					return nameWithSeparator.substring(0, nameWithSeparator.length() - 1);
				}
				return nameWithSeparator;
			}
		}
		// If no version pattern found, return filename without .jar extension
		if (fileName.endsWith(".jar")) {
			return fileName.substring(0, fileName.length() - 4);
		}
		return fileName;
	}

}