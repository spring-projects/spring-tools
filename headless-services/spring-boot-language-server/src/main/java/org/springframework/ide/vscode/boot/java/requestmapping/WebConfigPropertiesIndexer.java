/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.boot.java.value.ValuePropertyReferencesProvider;
import org.springframework.ide.vscode.boot.properties.BootPropertiesLanguageServerComponents;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.ParseResults;
import org.springframework.ide.vscode.java.properties.parser.Parser;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public class WebConfigPropertiesIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(WebConfigPropertiesIndexer.class);

	private final Map<String, BiConsumer<String, WebConfigIndexElement.Builder>> converters;
	
	public WebConfigPropertiesIndexer() {
		converters = new HashMap<>();
		
		converters.put("spring.mvc.apiversion.use.header", (value, configBuilder) -> configBuilder.versionStrategy("Request Header: " + value, null));
		converters.put("spring.mvc.apiversion.use.path-segment", (value, configBuilder) -> configBuilder.versionStrategy("Path Segment: " + value, null));
		converters.put("spring.mvc.apiversion.use.query-parameter", (value, configBuilder) -> configBuilder.versionStrategy("Query Param: " + value, null));
		converters.put("spring.mvc.apiversion.use.media-type-parameter", (value, configBuilder) -> configBuilder.versionStrategy("Media Type Parameter: " + value, null));
		converters.put("spring.mvc.apiversion.supported", (value, configBuilder) -> configBuilder.supportedVersion(value));
		
		converters.put("spring.webflux.apiversion.use.header", (value, configBuilder) -> configBuilder.versionStrategy("Request Header: " + value, null));
		converters.put("spring.webflux.apiversion.use.path-segment", (value, configBuilder) -> configBuilder.versionStrategy("Path Segment: " + value, null));
		converters.put("spring.webflux.apiversion.use.query-parameter", (value, configBuilder) -> configBuilder.versionStrategy("Query Param: " + value, null));
		converters.put("spring.webflux.apiversion.use.media-type-parameter", (value, configBuilder) -> configBuilder.versionStrategy("Media Type Parameter: " + value, null));
		converters.put("spring.webflux.apiversion.supported", (value, configBuilder) -> configBuilder.supportedVersion(value));
	}

	public List<WebConfigIndexElement> findWebConfigFromProperties(IJavaProject project) {

		return IClasspathUtil.getClasspathResourcesFullPaths(project.getClasspath())
			.filter(path -> ValuePropertyReferencesProvider.isPropertiesFile(path))
			.map(path -> {
				return createFromFile(path);
			})
			.filter(configElement -> !configElement.isEmpty())
			.toList();
	}

	public WebConfigIndexElement createFromFile(Path path) {
		WebConfigIndexElement.Builder builder = new WebConfigIndexElement.Builder(ConfigType.PROPERTIES);

		getProperties(path)
			.filter(pair -> converters.containsKey(pair.key()))
			.forEach(pair -> {
				converters.get(pair.key()).accept(pair.value(), builder);
			});
		
		Location location = new Location(path.toUri().toASCIIString(), new Range(new Position(0, 0), new Position(0, 0)));
		return builder.buildFor(location);
	}
	
	private Stream<PropertyKeyValue> getProperties(Path path) {
		String fileName = path.getFileName().toString();

		if (fileName.endsWith(BootPropertiesLanguageServerComponents.PROPERTIES)) {
			return getPropertiesFromPropertiesFile(path.toFile());
		}
		else {
			for (String ymlExtension : BootPropertiesLanguageServerComponents.YML) {
				if (fileName.endsWith(ymlExtension)) {
					return getPropertiesFromYamlFile(path.toFile());
				}
			}
		}
		
		return Stream.empty();
	}
	
	private Stream<PropertyKeyValue> getPropertiesFromPropertiesFile(File file) {
		try {
			String fileContent = FileUtils.readFileToString(file, Charset.defaultCharset());
	
			Parser parser = new AntlrParser();
			ParseResults parseResults = parser.parse(fileContent);
	
			if (parseResults != null && parseResults.ast != null) {
				return parseResults.ast.getPropertyValuePairs().stream()
					.map(pair -> new PropertyKeyValue(pair.getKey().decode(), pair.getValue().decode()));
			}
		} catch (IOException e) {
			log.error("", e);
		}

		return Stream.empty();
	}
	
	private Stream<PropertyKeyValue> getPropertiesFromYamlFile(File file) {
		if (file.isFile()) {
			Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

			try (Reader reader = new InputStreamReader(new FileInputStream(file), "UTF8")) {

				List<PropertyKeyValue> result = new ArrayList<>();
				for (Node node : yaml.composeAll(reader)) {
					flattenProperties("", node, result);
				}

				return result.stream();
			} catch (Exception e ) {
				//ignore failed attempt to read bad file
			}
		}
		return Stream.empty();
	}

	private void flattenProperties(String prefix, Node node, List<PropertyKeyValue> props) {
		switch (node.getNodeId()) {
		
		case mapping:
			if (!prefix.isEmpty()) {
				prefix = prefix + ".";
			}
			MappingNode mapping = (MappingNode)node;
			for (NodeTuple tup : mapping.getValue()) {
				String key = NodeUtil.asScalar(tup.getKeyNode());
				if (key != null) {
					flattenProperties(prefix + key, tup.getValueNode(), props);
				}
			}
			break;
			
		case scalar:
			//End of the line.
			props.add(new PropertyKeyValue(prefix, NodeUtil.asScalar(node)));
			break;
		
		case sequence:
			SequenceNode sequenceNode = (SequenceNode) node;
			List<String> values = sequenceNode.getValue().stream()
					.filter(valueNode -> valueNode instanceof ScalarNode)
					.map(scalarNode -> NodeUtil.asScalar(scalarNode))
					.toList();
			
			props.add(new PropertyKeyValue(prefix, String.join(", ", values)));
			
			break;
			
		default:
			if (!prefix.isEmpty()) {
				props.add(new PropertyKeyValue(prefix, "<object>"));
			}

			break;
		}
	}

	record PropertyKeyValue (String key, String value) {}

}
