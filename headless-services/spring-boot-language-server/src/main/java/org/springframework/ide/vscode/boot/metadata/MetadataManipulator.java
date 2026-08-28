/*******************************************************************************
 * Copyright (c) 2015, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.metadata;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;

import org.springframework.ide.eclipse.org.json.JSONArray;
import org.springframework.ide.eclipse.org.json.JSONObject;

/**
 * Helper class to manipulate data in a file presumed to contain
 * spring-boot configuration data.
 *
 * @author Kris De Volder
 * @author Alex Boyko
 */
public class MetadataManipulator {

	private abstract class Content {
		@Override
		public abstract String toString();
		public abstract void addProperty(JSONObject jsonObject) throws Exception;
	}

	/**
	 * Content was parse as JSONObject.
	 */
	private class ParsedContent extends Content {

		private JSONObject object;

		public ParsedContent(JSONObject o) {
			this.object = o;
		}

		@Override
		public String toString() {
			return object.toString(indentFactor);
		}

		@Override
		public void addProperty(JSONObject propertyData) throws Exception {
			JSONArray properties = object.getJSONArray("properties");
			properties.put(properties.length(), propertyData);
		}
	}

	/**
	 * Content that is 'unparsed' and just a bunch of text.
	 * Used only as a fallback when data in file can't
	 * be parsed.
	 * <p>
	 * This content is manipulated by string manipulation.
	 * It is less reliable, but can be done even if the
	 * file data is not parseable.
	 */
	private class RawContent extends Content {

		private StringBuilder doc;

		public RawContent(String content) {
			this.doc = new StringBuilder(content);
		}

		@Override
		public String toString() {
			return doc.toString();
		}

		@Override
		public void addProperty(JSONObject propertyData) throws Exception {
			int insertAt = findLast(']');
			if (insertAt < 0) {
				//although we're not looking for much, we didn't find it!
				//Funky file contents. Let's just insert something at end of file in a 'best effort' spirit.
				insertAt = doc.length();
			}
			insert(insertAt, "\n");

			insert(insertAt, propertyData.toString(indentFactor));

			int insertComma = findInsertCommaPos(insertAt);
			if (insertComma >= 0) {
				insert(insertComma, ",");
			}
		}

		/**
		 * Maybe we need to add a comma in front of the new entry. This
		 * method finds if/where to stick this comma.
		 * @throws Exception
		 */
		private int findInsertCommaPos(int pos) throws Exception {
			pos--;
			while (pos >= 0 && Character.isWhitespace(doc.charAt(pos))) {
				pos--;
			}
			if (pos >= 0) {
				char c = doc.charAt(pos);
				if (c == '}') {
					//Add a comma after a '}'
					return pos+1;
				}
			}
			return -1;
		}

		private int insert(int insertAt, String str) throws Exception {
			if (insertAt < doc.length()) {
				doc.replace(insertAt, insertAt, str);
			} else {
				doc.append(str);
			}
			return insertAt + str.length();
		}

		private int findLast(char toFind) throws Exception {
			int pos = doc.length()-1;
			while (pos >= 0 && doc.charAt(pos) != toFind) {
				pos--;
			}
			//We got here either because
			//  - we found char at pos or..
			//  - we reached position *before* start of file (i.e. -1)
			return pos;
		}

	}

	public interface ContentStore {
		String getContents() throws Exception;
		void setContents(String content) throws Exception;
	}

	private static final String INITIAL_CONTENT =
			"{\"properties\": [\n" +
			"]}";

	private static final String ENCODING = "UTF8";
	private ContentStore contentStore;
	private Content fContent;
	private int indentFactor = 2;

	public MetadataManipulator(ContentStore contentStore) {
		this.contentStore = contentStore;
	}

	public MetadataManipulator(final File file) {
		this(new ContentStore() {

			@Override
			public String getContents() throws Exception {
				return new String(Files.readAllBytes(file.toPath()), ENCODING);
			}

			@Override
			public void setContents(String content) throws Exception {
				Files.write(file.toPath(), content.getBytes(ENCODING));
			}

		});
	}

	private Content getContent() throws Exception {
		if (fContent == null) {
			fContent = readContent();
		}
		return fContent;
	}

	private Content readContent() throws Exception {
		String content = contentStore.getContents();
		if (content.trim().isEmpty()) {
			JSONObject o = initialContent();
			return new ParsedContent(o);
		} else {
			try {
				return new ParsedContent(new JSONObject(content));
			} catch (Exception e) {
				//couldn't parse?
				return new RawContent(content);
			}
		}
	}

	public void addDefaultInfo(String propertyName) throws Exception {
		getContent().addProperty(createDefaultData(propertyName));
	}

	private JSONObject createDefaultData(String propertyName) throws Exception {
		JSONObject obj = new JSONObject(new LinkedHashMap<String, Object>());
		obj.put("name", propertyName);
		obj.put("type", String.class.getName());
		obj.put("description", "A description for '"+propertyName+"'");
		return obj;
	}

	/**
	 * Generate the initial content (must be generated rather than being a constant to respect newline conventions
	 * on user's system.
	 */
	private JSONObject initialContent() throws Exception {
		return new JSONObject(INITIAL_CONTENT);
	}

	/**
	 * After manipulating the data, use this to persist changes back to the file.
	 */
	public void save() throws Exception {
		contentStore.setContents(getContent().toString());
	}

	/**
	 * Determines whether the 'reliable' manipulations can be used (which is the case
	 * only if the data in the file is valid json).
	 */
	public boolean isReliable() throws Exception {
		return getContent() instanceof ParsedContent;
	}

	public String getTextContent() throws Exception {
		return getContent().toString();
	}

}
