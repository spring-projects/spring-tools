/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ide.vscode.boot.java.commands;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeCatalog;
import org.jmolecules.stereotype.tooling.LabelProvider;
import org.jmolecules.stereotype.tooling.MethodNodeContext;
import org.jmolecules.stereotype.tooling.NodeContext;
import org.jmolecules.stereotype.tooling.NodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Oliver Drotbohm
 * @author Martin Lippert
 */
public class JsonNodeHandler<A, C> implements NodeHandler<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> {
	
	private static final Logger log = LoggerFactory.getLogger(JsonNodeHandler.class);

	private static final String PROJECT_ID = "projectId";
	
	private static final String LOCATION = "location";
	private static final String REFERENCE = "reference";
	
	public static final String ICON = "icon";
	public static final String TEXT = "text";
	public static final String HOVER = "hover";
	
	private static final String NODE_ID = "nodeId";

	private final Node root;
	private final LabelProvider<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> labels;
	private final BiConsumer<Node, C> customHandler;
	private final CachedSpringMetamodelIndex springIndex;
	private final SourceLinks sourceLinks;
	private final IJavaProject project;

	private Node current;
	private StereotypeCatalog catalog;

	public JsonNodeHandler(LabelProvider<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> labels, BiConsumer<Node, C> customHandler,
			CachedSpringMetamodelIndex springIndex, SourceLinks sourceLinks, StereotypeCatalog catalog, IJavaProject project) {
		this.labels = labels;
		this.springIndex = springIndex;
		this.customHandler = customHandler;
		this.sourceLinks = sourceLinks;
		this.project = project;

		this.root = new Node(null);
		this.current = root;
		this.catalog = catalog;
	}
	
	@Override
	public void handleStereotype(Stereotype stereotype, NodeContext context) {

		if (stereotype.getIdentifier().equals("org.jmolecules.misc.Other")) {
			addChild(node -> node
					.withAttribute(TEXT, labels.getStereotypeLabel(stereotype))
					.withAttribute(ICON, StereotypeIcons.getIcon(stereotype))
				);
		} else {
			var definition = catalog.getDefinition(stereotype);
			var sources = definition.getSources();
			
			Location reference = null;
			for (Object source : sources) {
				if (source instanceof URL) {
					URL url = (URL) source;
					
					try {
						URI uri = url.toURI();
						reference = new Location(uri.toASCIIString(), new Range(new Position(0,0), new Position(0,0)));
						if (Misc.JAR.equals(uri.getScheme())) {
							sourceLinks.sourceLinkForJarEntry(project, uri).map(u -> u.toASCIIString()).ifPresent(reference::setUri);
						}
					} catch (URISyntaxException e) {
						log.error("", e);
					}
				}
				else if (source instanceof Location) {
					reference = (Location) source;
				}
			}
			
			final Location referenceLocation = reference;
			addChild(node -> node
				.withAttribute(TEXT, labels.getStereotypeLabel(stereotype))
				.withAttribute(ICON, StereotypeIcons.getIcon(stereotype))
				.withAttribute(HOVER, "defined in: " + sources.toString())
				.withAttribute(REFERENCE, referenceLocation)
			);
		}
	}
	
	@Override
	public void handleApplication(A application) {
		this.root
			.withAttribute(TEXT, labels.getApplicationLabel(application))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.APPLICATION_KEY))
			.withAttribute(PROJECT_ID, project.getElementName())
		;
		assignNodeId(root, null);
	}

	@Override
	public void handlePackage(StereotypePackageElement pkg, NodeContext context) {

		addChild(node -> node
			.withAttribute(TEXT, labels.getPackageLabel(pkg))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.MODULE_KEY))
		);
	}

	@Override
	public void handleType(StereotypeClassElement type, NodeContext context) {
		addChild(node -> node
			.withAttribute(TEXT, labels.getTypeLabel(type))
			.withAttribute(LOCATION, type.getLocation())
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.TYPE_KEY))
			.withChildren(createTypeSubnotes(node, type))
		);
	}

	private List<Node> createTypeSubnotes(Node parent, StereotypeClassElement type) {
		if (System.getProperty("enable-structure-view-details") == null) {
			return Collections.emptyList();
		}
		
		if (type.getLocation() == null) {
			return Collections.emptyList();
		}
		
		String docUri = type.getLocation().getUri();
		ArrayList<Node> result = new ArrayList<Node>();

		Arrays.stream(springIndex.getBeansOfDocument(docUri))
				.filter(bean -> bean.getType().equals(type.getType()))
				.flatMap(bean -> bean.getChildren().stream())
				.filter(child -> child instanceof SymbolElement)
				.map(child -> ((SymbolElement) child))
				.forEach(symbolElement -> {
					DocumentSymbol symbol = symbolElement.getDocumentSymbol();
					
					Node childNode = new Node(parent)
							.withAttribute(TEXT, symbol.getName())
							.withAttribute(LOCATION, new Location(docUri, symbol.getRange()));

					assignNodeId(childNode, parent);

					result.add(childNode);
				});
						
		return result;
	}

	@Override
	public void handleMethod(StereotypeMethodElement method, MethodNodeContext<StereotypeClassElement> context) {
		addChildFoo(node -> node
			.withAttribute(TEXT, labels.getMethodLabel(method, context.getContextualType()))
			.withAttribute(LOCATION, method.getLocation())
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.METHOD_KEY))
		);
	}

	@Override
	public void handleCustom(C custom, NodeContext context) {
		addChild(node -> customHandler.accept(node, custom));
	}

	public Node createNested() {
		return new Node(this.current);
	}

	@Override
	public void postGroup() {
		this.current = this.current.parent;
	}

	private void addChild(Consumer<Node> consumer) {
		this.current = addChildFoo(consumer);
	}
	
	private static void assignNodeId(Node n, Node p) {
		String textId = n.attributes.containsKey(TEXT) ? (String) n.attributes.get(TEXT) : "";
		
		Location location = (Location) n.attributes.get(LOCATION);
		String locationId = location == null ? "" : "%s:%d:%d".formatted(location.getUri(), location.getRange().getStart().getLine(), location.getRange().getStart().getCharacter());
		
		String referenceId = n.attributes.containsKey(REFERENCE) ? ((Location) n.attributes.get(REFERENCE)).getUri() : "";
		
		String nodeSpecificId = "%s|%s|%s".formatted(textId, locationId, referenceId).replaceAll("\\|+$", "");
		
		n.attributes.put(NODE_ID, p != null && p.attributes.containsKey(NODE_ID) ? "%s/%s".formatted(p.attributes.get(NODE_ID), nodeSpecificId) : nodeSpecificId);
	}
	
	private Node addChildFoo(Consumer<Node> consumer) {

		var node = new Node(this.current);
		consumer.accept(node);
		
		assignNodeId(node, current);

		this.current.children.add(node);

		return node;
	}

	@Override
	public String toString() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(root);
	}
	
	Node getRoot() {
		return root;
	}
	
	public static class Node {

		transient final Node parent;
		final Map<String, Object> attributes;
		final List<Node> children;

		Node(Node parent) {
			this.parent = parent;
			this.attributes = new LinkedHashMap<>();
			this.children = new ArrayList<>();
		}

		public Node withAttribute(String key, Object value) {
			this.attributes.put(key, value);
			return this;
		}
		
		public Node withChildren(List<Node> children) {
			this.children.addAll(children);
			return this;
		}
		
	}


}
