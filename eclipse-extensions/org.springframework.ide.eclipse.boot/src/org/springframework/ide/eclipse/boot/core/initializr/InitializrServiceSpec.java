/*******************************************************************************
 * Copyright (c) 2014, 2017 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.initializr;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springframework.ide.eclipse.boot.util.version.VersionParser;
import org.springframework.ide.eclipse.boot.util.version.VersionRange;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * This class is the 'parsed' form of the json metadata for spring intializr service.
 *
 * See: https://docs.spring.io/initializr/docs/current-SNAPSHOT/reference/html/#metadata-format
 *
 * @author Kris De Volder
 */
public class InitializrServiceSpec {

	private JSONObject data;

	//Prefer v2.2 format but fallback on v2.1 if the service does not yet support it.
	public static final String JSON_CONTENT_TYPE_HEADER = "application/vnd.initializr.v2.2+json,application/vnd.initializr.v2.1+json;q=0.9";

	/**
	 * Boot version link template variable
	 */
	public static final String BOOT_VERSION_LINK_TEMPLATE_VARIABLE = "bootVersion";

	/**
	 * Pattern matching link template variables
	 */
	private static final Pattern LINK_TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{(.*?)\\}");


	public InitializrServiceSpec(JSONObject jsonObject) {
		this.data = jsonObject;
	}

	public static InitializrServiceSpec parseFrom(URLConnectionFactory urlConnectionFactory, URL url) throws HttpRedirectionException, Exception {
		URLConnection conn = null;
		InputStream input = null;
		try {
			conn = urlConnectionFactory.createConnection(url);
			conn.addRequestProperty("Accept", JSON_CONTENT_TYPE_HEADER);
			conn.connect();
			String redirectedTo = getRedirected(conn);
			if (StringUtil.hasText(redirectedTo)) {
				throw new HttpRedirectionException(redirectedTo);
			}
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

	public static void checkBasicConnection(URLConnectionFactory urlConnectionFactory, URL url) throws Exception {
		URLConnection conn = null;
		InputStream input = null;
		try {
			conn = urlConnectionFactory.createConnection(url);
			conn.addRequestProperty("Accept", JSON_CONTENT_TYPE_HEADER);
			conn.connect();
			String redirectedTo = getRedirected(conn);
			// Don't handle redirect on basic connection check.
			if (!StringUtil.hasText(redirectedTo)) {
				input = conn.getInputStream();
			}
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static String getRedirected(URLConnection _conn) throws IOException {
		if (_conn instanceof HttpURLConnection) {
			HttpURLConnection conn = (HttpURLConnection) _conn;
			int status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
					status == HttpURLConnection.HTTP_MOVED_PERM ||
					status == HttpURLConnection.HTTP_SEE_OTHER
				) {
					return conn.getHeaderField("Location");
				}
			}
		}
		return null;
	}

	public static InitializrServiceSpec parseFrom(InputStream input) throws Exception {
		return new InitializrServiceSpec(new JSONObject(new JSONTokener(new InputStreamReader(input, "utf8"))));
	}

	/////////////////////////////////////////////////

	public static abstract class Nameable {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Type extends Option {
		private String action;
		private String build;

		public void setAction(String action) {
			this.action = action;
		}

		public String getAction() {
			return action;
		}

		public String getBuild() {
			return build;
		}

		public void setBuild(String build) {
			this.build = build;
		}

	}


	public static class Dependency extends Nameable implements IdAble{

		private String id;
		private String description;
		private String versionRange;
		private Links links;

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String getName() {
			String name = super.getName();
			return name != null ? name : getId();
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public Links getLinks() {
			return links;
		}

		public void setLinks(Links links) {
			this.links = links;
		}

		public static Dependency[] from(JSONArray values) throws JSONException {
			Dependency[] deps = new Dependency[values.length()];
			for (int i = 0; i < deps.length; i++) {
				JSONObject obj = values.getJSONObject(i);
				deps[i] = new Dependency();
				deps[i].setId(obj.getString("id"));
				deps[i].setName(obj.optString("name"));
				deps[i].setDescription(obj.optString("description"));
				deps[i].setVersionRange(obj.optString("versionRange"));
				JSONObject linksObject = obj.optJSONObject("_links");
				if (linksObject != null) {
					deps[i].setLinks(Links.from(linksObject));
				}
			}
			return deps;
		}

		public void setVersionRange(String range) {
			this.versionRange = range;
		}

		public String getVersionRange() {
			return versionRange;
		}

		@Override
		public String toString() {
			return "Dep("+id+","+getName()+","+versionRange+")";
		}

		public boolean isSupportedFor(String bootVersion) {
			try {
				if (StringUtils.isNotBlank(versionRange)) {
					final VersionRange range = VersionParser.DEFAULT.parseRange(versionRange);
					String versionStr = bootVersion;
					if (versionStr != null) {
						Version version = VersionParser.DEFAULT.parse(versionStr);
						//replacement of BS -> ZZ: see bug https://www.pivotaltracker.com/story/show/100963226
						return range.match(version);
					}
				}
			} catch (Exception e) {
				Log.log(e);
			}
			return true;
		}
	}

	public static class Links {
		private Link[] guides = new Link[0];
		private Link[] references = new Link[0];
		public Link[] getGuides() {
			return guides;
		}
		public void setGuides(Link[] guides) {
			this.guides = guides;
		}
		public Link[] getReferences() {
			return references;
		}
		public void setReferences(Link[] references) {
			this.references = references;
		}
		public static Links from(JSONObject json) throws JSONException {
			Links links = new Links();
			Object guidesObj = json.opt("guide");
			if (guidesObj instanceof JSONArray) {
				links.setGuides(Link.from((JSONArray)guidesObj));
			} else if (guidesObj instanceof JSONObject) {
				links.setGuides(new Link[] {Link.from((JSONObject)guidesObj)});
			}
			Object refsObj = json.opt("reference");
			if (refsObj instanceof JSONArray) {
				links.setReferences(Link.from((JSONArray)refsObj));
			} else if (refsObj instanceof JSONObject) {
				links.setReferences(new Link[] {Link.from((JSONObject)refsObj)});
			}
			return links;
		}
	}

	public static class Link {
		private String href;
		private String title;
		private boolean templated;
		public String getHref() {
			return href;
		}
		public void setHref(String href) {
			this.href = href;
		}
		public String getTitle() {
			return title;
		}
		public void setTitle(String description) {
			this.title = description;
		}
		public boolean isTemplated() {
			return templated;
		}
		public void setTemplated(boolean templated) {
			this.templated = templated;
		}
		public static Link[] from(JSONArray values) throws JSONException{
			Link[] links = new Link[values.length()];
			for (int i = 0; i < values.length(); i++) {
				JSONObject obj = values.getJSONObject(i);
				links[i] = from(obj);
			}
			return links;
		}
		public static Link from(JSONObject obj) throws JSONException{
			Link link = new Link();
			link.setHref(obj.optString("href", null));
			link.setTitle(obj.optString("title", null));
			link.setTemplated(obj.optBoolean("templated", false));
			return link;
		}
	}

	public static class DependencyGroup extends Nameable {

		private Dependency[] content;

		public Dependency[] getContent() {
			return content;
		}

		public void setContent(Dependency[] content) {
			this.content = content;
		}
	}

	public static class Option extends Nameable {
		private String id;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		private boolean isDefault;

		public boolean isDefault() {
			return isDefault;
		}

		public void setDefault(boolean isDefault) {
			this.isDefault = isDefault;
		}
	}


	/////////////////////////////////////////////////////////////////

	@SuppressWarnings("unchecked")
	public Map<String, String> getTextInputs() throws JSONException {
		Map<String,String> defaults = new HashMap<>();
		Iterator<String> props = data.keys();
		while(props.hasNext()) {
			String key = props.next();
			JSONObject obj = data.getJSONObject(key);
			String type = obj.optString("type");
			if ("text".equals(type)) {
				defaults.put(key, obj.optString("default", ""));
			}
		}
		return defaults;
	}

	public Type[] getTypeOptions(String groupName) {
		try {
			JSONObject obj = data.optJSONObject(groupName);
			if (obj != null && "action".equals(obj.optString("type"))) {
				String defaultValue = obj.optString("default", "");
				JSONArray arr = obj.getJSONArray("values");
				List<Type> options = new ArrayList<>(arr.length());
				for (int i = 0; i < arr.length(); i++) {
					JSONObject option = arr.getJSONObject(i);
					String id = option.getString("id");
					String name = option.getString("name");
					String action = option.getString("action");
					JSONObject tags = option.getJSONObject("tags");
					if ("project".equals(tags.optString("format", null))) {
						Type type = new Type();
						type.setId(id);
						type.setName(name);
						type.setAction(action);
						type.setBuild(tags.optString("build"));
						type.setDefault(id.equals(defaultValue));
						options.add(type);
					}
				}
				return options.toArray(new Type[options.size()]);
			}
		} catch (JSONException e) {
			//ignore
		}
		return new Type[0];
	}


	public Option[] getSingleSelectOptions(String groupName) {
		try {
			JSONObject obj = data.optJSONObject(groupName);
			if (obj != null && "single-select".equals(obj.optString("type"))) {
				String defaultValue = obj.optString("default", "");
				JSONArray arr = obj.getJSONArray("values");
				Option[] options = new Option[arr.length()];
				for (int i = 0; i < options.length; i++) {
					JSONObject option = arr.getJSONObject(i);
					options[i] = new Option();
					String id = option.getString("id");
					String name = option.getString("name");
					options[i].setId(id);
					options[i].setName(name);
					options[i].setDefault(id.equals(defaultValue));
				}
				return options;
			}
		} catch (JSONException e) {
			//ignore
		}
		return new Option[0];
	}

	public DependencyGroup[] getDependencies() {
		return getHierarchicalMultiSelect("dependencies");
	}

	private DependencyGroup[] getHierarchicalMultiSelect(String prop) {
		try {
			JSONObject obj = data.optJSONObject(prop);
			if (obj != null && "hierarchical-multi-select".equals(obj.optString("type"))) {
				JSONArray arr = obj.getJSONArray("values");
				DependencyGroup[] groups = new DependencyGroup[arr.length()];
				for (int i = 0; i < groups.length; i++) {
					JSONObject group = arr.getJSONObject(i);
					groups[i] = new DependencyGroup();
					String name = group.getString("name");
					JSONArray values = group.getJSONArray("values");
					groups[i].setName(name);
					groups[i].setContent(Dependency.from(values));
				}
				return groups;
			}
		} catch (JSONException e) {
			//ignore
		}
		return new DependencyGroup[0];
	}

	public static String substituteTemplateVariables(String template, Map<String, String> variableValues) throws CoreException {
		Matcher matcher = LINK_TEMPLATE_VARIABLE_PATTERN.matcher(template);
		String result = template;
		while (matcher.find()) {
			String variable = matcher.group(1);
			String value = variableValues.get(variable);
			if (value == null) {
				throw ExceptionUtil.coreException("Initializr link has unknown " + variable + " in the template " + template);
			} else {
				result = result.replaceAll("\\{" + variable + "\\}", value);
			}
		}
		return result;
	}

}
