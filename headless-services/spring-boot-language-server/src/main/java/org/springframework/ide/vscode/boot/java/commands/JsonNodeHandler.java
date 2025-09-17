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
import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeCatalog;
import org.jmolecules.stereotype.tooling.LabelProvider;
import org.jmolecules.stereotype.tooling.MethodNodeContext;
import org.jmolecules.stereotype.tooling.NodeContext;
import org.jmolecules.stereotype.tooling.NodeHandler;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Oliver Drotbohm
 * @author Martin Lippert
 */
public class JsonNodeHandler<A, C> implements NodeHandler<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> {

	private static final String LOCATION = "location";
	
	public static final String ICON = "icon";
	public static final String TEXT = "text";
	public static final String HOVER = "hover";

	private final Node root;
	private final LabelProvider<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> labels;
	private final BiConsumer<Node, C> customHandler;
	private final CachedSpringMetamodelIndex springIndex;

	private Node current;
	private StereotypeCatalog catalog;

	public JsonNodeHandler(LabelProvider<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> labels, BiConsumer<Node, C> customHandler,
			CachedSpringMetamodelIndex springIndex, StereotypeCatalog catalog) {
		this.labels = labels;
		this.springIndex = springIndex;
		this.customHandler = customHandler;

		this.root = new Node(null);
		this.current = root;
		this.catalog = catalog;
	}
	
	@Override
	public void handleStereotype(Stereotype stereotype, NodeContext context) {

		// var definition = catalog.getDefinition(stereotype);
		// var sources = definition.getSources();

		addChild(node -> node
			.withAttribute(TEXT, labels.getStereotypeLabel(stereotype))
			.withAttribute(ICON, StereotypeIcons.getIcon(stereotype))
			.withAttribute(HOVER, "<stereotype catalog source coming soon...>")
		);
	}

	@Override
	public void handleApplication(A application) {
		this.root
			.withAttribute(TEXT, labels.getApplicationLabel(application))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.APPLICATION_KEY))
		;
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
					result.add(new Node(parent)
							.withAttribute(TEXT, symbol.getName())
							.withAttribute(LOCATION, new Location(docUri, symbol.getRange())));
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

	private Node addChildFoo(Consumer<Node> consumer) {

		var node = new Node(this.current);
		consumer.accept(node);

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
