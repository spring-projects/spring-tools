/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.cron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.ParseResults;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.KeyValuePair;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Value;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

/**
 * Spring Boot declares the properties below as plain <code>java.lang.String</code>, with no hint
 * or other metadata marking them as CRON expressions. Hence they are listed explicitly here.
 *
 * @author Alex Boyko
 */
public final class CronProperties {

	private static final Set<String> NAMES = Set.of(
			"spring.integration.poller.cron",
			"spring.session.jdbc.cleanup-cron",
			"spring.session.redis.cleanup-cron", // Boot 3.x name, deprecated in Boot 4
			"spring.session.data.redis.cleanup-cron"
	).stream().map(CronProperties::normalize).collect(Collectors.toSet());

	/**
	 * A CRON expression in a document, <code>end</code> is after the closing quote of a quoted YAML value
	 */
	record CronValue(int offset, String text, int end) {
	}

	private CronProperties() {
	}

	/**
	 * Finds CRON expressions in a properties or YAML document. Values with placeholders, escapes or
	 * multiple lines are not included, they cannot be mapped back to the document text.
	 */
	static List<CronValue> findValues(TextDocument doc, YamlASTProvider yamlParser) throws Exception {
		List<CronValue> values = new ArrayList<>();
		LanguageId language = doc.getLanguageId();
		if (LanguageId.BOOT_PROPERTIES.equals(language)) {
			collectPropertiesValues(doc.get(), values);
		} else if (LanguageId.BOOT_PROPERTIES_YAML.equals(language)) {
			YamlFileAST ast = yamlParser.getAST(doc);
			if (ast != null && ast.getNodes() != null) {
				String text = doc.get();
				Set<Node> onPath = NodeUtil.newIdentitySet();
				for (Node node : ast.getNodes()) {
					collectYamlValues(text, node, "", onPath, values);
				}
			}
		}
		return values;
	}

	private static void collectPropertiesValues(String text, List<CronValue> values) {
		ParseResults result = new AntlrParser().parse(text);
		for (KeyValuePair pair : result.ast.getPropertyValuePairs()) {
			Value value = pair.getValue();
			if (value != null && pair.getKey() != null && isCronProperty(pair.getKey().decode())) {
				int offset = findValueOffset(text, value);
				if (offset >= 0) {
					String cron = value.decode().stripTrailing();
					addValue(values, offset, cron, offset + cron.length());
				}
			}
		}
	}

	private static void collectYamlValues(String text, Node node, String prefix, Set<Node> onPath, List<CronValue> values) {
		// Anchors and aliases may make a node its own descendant
		if (node instanceof MappingNode map && onPath.add(node)) {
			try {
				for (NodeTuple entry : map.getValue()) {
					String key = NodeUtil.asScalar(entry.getKeyNode());
					if (key != null) {
						if (key.startsWith("[") && key.endsWith("]")) {
							key = key.substring(1, key.length() - 1);
						}
						String name = prefix.isEmpty() ? key : prefix + "." + key;
						Node value = entry.getValueNode();
						if (value instanceof ScalarNode scalar) {
							if (isCronProperty(name)) {
								int offset = findValueOffset(text, scalar);
								if (offset >= 0) {
									addValue(values, offset, scalar.getValue(), scalar.getEndMark().getIndex());
								}
							}
						} else {
							collectYamlValues(text, value, name, onPath, values);
						}
					}
				}
			} finally {
				onPath.remove(node);
			}
		}
	}

	private static void addValue(List<CronValue> values, int offset, String text, int end) {
		if (!text.isBlank() && !text.contains("${")) {
			values.add(new CronValue(offset, text, end));
		}
	}

	/**
	 * @param propertyName property name, canonical or in any relaxed binding form (camelCase, snake_case etc.)
	 * @return <code>true</code> if the value of the property is a CRON expression
	 */
	public static boolean isCronProperty(String propertyName) {
		return propertyName != null && NAMES.contains(normalize(propertyName));
	}

	private static String normalize(String name) {
		return name.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * @return offset of the value text in a <code>.properties</code> document or <code>-1</code> if it isn't found verbatim
	 */
	private static int findValueOffset(String docText, Value value) {
		// Value node offset points at the whitespace following the separator
		int offset = Math.max(0, value.getOffset());
		int end = offset;
		while (end < docText.length() && docText.charAt(end) != '\n' && docText.charAt(end) != '\r') {
			end++;
		}
		while (offset < end && Character.isWhitespace(docText.charAt(offset))) {
			offset++;
		}
		String raw = docText.substring(offset, end).stripTrailing();
		return raw.equals(value.decode().stripTrailing()) ? offset : -1;
	}

	/**
	 * @return offset of the scalar's value text in a YAML document or <code>-1</code> if it isn't found verbatim
	 */
	private static int findValueOffset(String docText, ScalarNode scalar) {
		String value = scalar.getValue();
		int start = scalar.getStartMark().getIndex();
		int end = scalar.getEndMark().getIndex();
		switch (scalar.getScalarStyle()) {
		case PLAIN:
			break;
		case SINGLE_QUOTED:
		case DOUBLE_QUOTED:
			start++;
			end--;
			break;
		default:
			return -1;
		}
		if (start >= 0 && end <= docText.length() && end - start == value.length()
				&& docText.startsWith(value, start)) {
			return start;
		}
		return -1;
	}

}
