/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.initializr;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Jackson 'templates' for parsing json data from start.spring.io 'dependencies' endpoint.
 *
 * @author Kris De Volder
 */
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonInclude(value=Include.NON_NULL)
public class InitializrDependencySpec {

	/* Some examples:
	{
	    "id": "activiti-basic",
	    "name": "Activiti",
	    "groupId": "org.activiti",
	    "artifactId": "activiti-spring-boot-starter-basic",
	    "version": "5.19.0",
	    "scope": "compile"
	  },
	{
	  "id": "lombok",
	  "name": "Lombok",
	  "groupId": "org.projectlombok",
	  "artifactId": "lombok",
	  "scope": "compile",
	  "bom": "cloud-bom"
	},
	{
	  "id": "postgresql",
	  "name": "PostgreSQL",
	  "groupId": "org.postgresql",
	  "artifactId": "postgresql",
	  "version": "9.4-1201-jdbc41",
	  "scope": "runtime"
	},
		 */

	@JsonIgnoreProperties(ignoreUnknown=true)
	@JsonInclude(value=Include.NON_NULL)
	public static class BomInfo {
		private String groupId;
		private String artifactId;
		private String version;
		private String scope;
		private String classifier;
		private String[] repositories;
		public String getGroupId() {
			return groupId;
		}
		public void setGroupId(String groupId) {
			this.groupId = groupId;
		}
		public String getArtifactId() {
			return artifactId;
		}
		public void setArtifactId(String artifactId) {
			this.artifactId = artifactId;
		}
		public String getVersion() {
			return version;
		}
		public void setVersion(String version) {
			this.version = version;
		}
		public String getScope() {
			return scope;
		}
		public void setScope(String scope) {
			this.scope = scope;
		}
		public String getClassifier() {
			return classifier;
		}
		public void setClassifier(String classifier) {
			this.classifier = classifier;
		}
		public String[] getRepositories() {
			return repositories;
		}
		public void setRepositories(String[] repositories) {
			this.repositories = repositories;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	@JsonInclude(value=Include.NON_NULL)
	public static class DependencyInfo {
		private String groupId;
		private String artifactId;
		private String version;
		private String scope;
		private String bom;
		private String classifier;
		private String repository;

		public String getGroupId() {
			return groupId;
		}
		public void setGroupId(String groupId) {
			this.groupId = groupId;
		}
		public String getArtifactId() {
			return artifactId;
		}
		public void setArtifactId(String artifactId) {
			this.artifactId = artifactId;
		}
		public String getVersion() {
			return version;
		}
		public void setVersion(String version) {
			this.version = version;
		}
		public String getScope() {
			return scope;
		}
		public void setScope(String scope) {
			this.scope = scope;
		}
		public String getBom() {
			return bom;
		}
		public void setBom(String bom) {
			this.bom = bom;
		}
		public String getClassifier() {
			return this.classifier;
		}
		public void setClassifier(String classifier) {
			this.classifier = classifier;
		}
		public String getRepository() {
			return repository;
		}
		public void setRepository(String repository) {
			this.repository = repository;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	@JsonInclude(value=Include.NON_NULL)
	public static class RepoInfo {
		private String name;
		private String url;
		private Boolean snapshotEnabled;
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getUrl() {
			return url;
		}
		public void setUrl(String url) {
			this.url = url;
		}
		public Boolean getSnapshotEnabled() {
			return snapshotEnabled;
		}
		public void setSnapshotEnabled(Boolean snapshotEnabled) {
			this.snapshotEnabled = snapshotEnabled;
		}
	}

	private static final String JSON_CONTENT_TYPE_HEADER = "application/json";

	private String bootVersion;
	private Map<String, DependencyInfo> dependencies;
	private Map<String, BomInfo> boms;
	private Map<String, RepoInfo> repositories;


	public static InitializrDependencySpec parseFrom(InputStream input) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(input, InitializrDependencySpec.class);
	}

	public Map<String, DependencyInfo> getDependencies() {
		return dependencies;
	}

	public void setDependencies(Map<String, DependencyInfo> dependencies) {
		this.dependencies = dependencies;
	}

	@Override
	public String toString() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
			return mapper.writeValueAsString(this);
		} catch (Exception e) {
			Log.log(e);
			return super.toString();
		}
	}

	public static InitializrDependencySpec parseFrom(URLConnectionFactory urlConnectionFactory, URL url) throws Exception {
		URLConnection conn = null;
		InputStream input = null;
		try {
			conn = urlConnectionFactory.createConnection(url);
			conn.addRequestProperty("Accept", JSON_CONTENT_TYPE_HEADER);
			conn.connect();
			input = conn.getInputStream();
			return parseFrom(input);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static InitializrDependencySpec parseFrom(URLConnectionFactory urlConnectionFactory, String url) throws Exception {
		Exception exception = null;
		for (int i = 0; i < 5; i++) { //TODO: remove this, but at the moment this service seems unreliable for an unknown reason
			try {
				return parseFrom(urlConnectionFactory, new URL(url));
			} catch (Exception e) {
				exception = e;
			}
		}
		throw exception;
	}

	public Map<String, BomInfo> getBoms() {
		return boms;
	}

	public void setBoms(Map<String, BomInfo> boms) {
		this.boms = boms;
	}

	public Map<String, RepoInfo> getRepositories() {
		return repositories;
	}

	public void setRepositories(Map<String, RepoInfo> repositories) {
		this.repositories = repositories;
	}

	public String getBootVersion() {
		return bootVersion;
	}
	public void setBootVersion(String bootVersion) {
		this.bootVersion = bootVersion;
	}

}
