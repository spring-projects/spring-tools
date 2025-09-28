/*******************************************************************************
 * Copyright (c) 2025 Broadcom
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
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.boot.java.value.ValuePropertyReferencesProvider;
import org.springframework.ide.vscode.boot.properties.BootPropertiesLanguageServerComponents;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.ParseResults;
import org.springframework.ide.vscode.java.properties.parser.Parser;

public class WebConfigCodeLensProvider implements CodeLensProvider {

	private static final Logger log = LoggerFactory.getLogger(WebConfigCodeLensProvider.class);

	private final SpringMetamodelIndex springIndex;
	private final BootJavaConfig config;
	private final JavaProjectFinder projectFinder;

	public WebConfigCodeLensProvider(JavaProjectFinder projectFinder, SpringMetamodelIndex springIndex, BootJavaConfig config) {
		this.projectFinder = projectFinder;
		this.springIndex = springIndex;
		this.config = config;
	}

	@Override
	public void provideCodeLenses(CancelChecker cancelToken, TextDocument document, CompilationUnit cu, List<CodeLens> codeLenses) {
		if (!config.isEnabledCodeLensForWebConfigs()) {
			return;
		}
		
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				provideCodeLens(cancelToken, node, document, codeLenses);
				return super.visit(node);
			}
		});
		
	}
	
	private void provideCodeLens(CancelChecker cancelToken, TypeDeclaration node, TextDocument doc, List<CodeLens> codeLenses) {
		cancelToken.checkCanceled();
		
		ITypeBinding binding = node.resolveBinding();
		if (binding == null) return;
		
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(node);
		if (!annotationHierarchies.isAnnotatedWith(binding, Annotations.CONTROLLER)) return;
		
		Optional<IJavaProject> optional = projectFinder.find(doc.getId());
		if (optional.isEmpty()) return;
		
		IJavaProject project = optional.get();

		List<WebConfigIndexElement> webConfigs = springIndex.getNodesOfType(project.getElementName(), WebConfigIndexElement.class);
		List<WebConfigIndexElement> webConfigFromProperties = findWebConfigFromProperties(project);
		webConfigs.addAll(webConfigFromProperties);
		
		for (WebConfigIndexElement webConfig : webConfigs) {
			CodeLens codeLens = createCodeLens(webConfig, node, doc);
			if (codeLens != null) {
				codeLenses.add(codeLens);
			}
		}
		
	}

	private CodeLens createCodeLens(WebConfigIndexElement webConfig, TypeDeclaration node, TextDocument doc) {
		Command command = new Command();
	
		// Display label
		String label = webConfig.getConfigType().getLabel();

		if (webConfig.getPathPrefix() != null && webConfig.getPathPrefix().trim().length() > 0) {
			label += " - Path Prefix: " + webConfig.getPathPrefix();
		}
		
		if (webConfig.getVersionSupportStrategies() != null && webConfig.getVersionSupportStrategies().size() > 0) {
			label += " - Versioning via " + String.join(", ", webConfig.getVersionSupportStrategies());
		}
		
		if (webConfig.getSupportedVersions() != null && webConfig.getSupportedVersions().size() > 0) {
			label += " - Supported Versions: " + String.join(", ", webConfig.getSupportedVersions());
		}
		
		Location targetLocation = webConfig.getLocation();
		Range targetRange = targetLocation.getRange();
		
		command.setTitle(label);
		command.setCommand("vscode.open");
		command.setArguments(List.of(targetLocation.getUri(),
				Map.of("selection", Map.of(
						"start", Map.of("line", targetRange.getStart().getLine(), "character", targetRange.getStart().getCharacter()),
						"end", Map.of("line", targetRange.getEnd().getLine(), "character", targetRange.getEnd().getCharacter()))
				)
			)
		);
		
		// Range
		
		SimpleName nameNode = node.getName();
		if (nameNode == null) return null;
		
		Range range;
		try {
			range = doc.toRange(node.getStartPosition(), node.getLength());
			return new CodeLens(range, command, null);

		} catch (BadLocationException e) {
			return null;
		}

	}
	
	private List<WebConfigIndexElement> findWebConfigFromProperties(IJavaProject project) {
		Map<String, BiConsumer<String, WebConfigIndexElement.Builder>> converters = new HashMap<>();
		converters.put("spring.mvc.apiversion.use.header", (value, configBuilder) -> configBuilder.versionStrategy("Request Header: " + value));
		converters.put("spring.mvc.apiversion.use.path-segment", (value, configBuilder) -> configBuilder.versionStrategy("Path Segment: " + value));
		converters.put("spring.mvc.apiversion.supported", (value, configBuilder) -> configBuilder.supportedVersion(value));
		
		return IClasspathUtil.getClasspathResourcesFullPaths(project.getClasspath())
			.filter(path -> ValuePropertyReferencesProvider.isPropertiesFile(path))
			.map(path -> {
				WebConfigIndexElement.Builder builder = new WebConfigIndexElement.Builder(ConfigType.PROPERTIES);

				getProperties(path)
					.filter(pair -> converters.containsKey(pair.key()))
					.forEach(pair -> {
						converters.get(pair.key()).accept(pair.value(), builder);
					});
				
				Location location = new Location(path.toUri().toASCIIString(), new Range(new Position(0, 0), new Position(0, 0)));
				return builder.buildFor(location);
			})
			.toList();
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
		return Stream.empty();
		
		// TODO !!!
	}

	record PropertyKeyValue (String key, String value) {}

}
