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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.tooling.LabelProvider;
import org.jmolecules.stereotype.tooling.MethodNodeContext;
import org.jmolecules.stereotype.tooling.NodeContext;
import org.jmolecules.stereotype.tooling.NodeHandler;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Oliver Drotbohm
 * @author Martin Lippert
 */
public class ApplicationModulesJsonNodeHandler implements NodeHandler<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, NamedInterfaceNode> {

	private static final String LOCATION = "location";
	static final String ICON = "icon";
	static final String TEXT = "text";

	private final Node root;
	private final LabelProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, NamedInterfaceNode> labels;
	private final BiConsumer<Node, NamedInterfaceNode> customHandler;
	private Node current;

	public ApplicationModulesJsonNodeHandler(LabelProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, NamedInterfaceNode> labels, BiConsumer<Node, NamedInterfaceNode> customHandler) {
		this.labels = labels;
		this.root = new Node(null);
		this.customHandler = customHandler;
		this.current = root;
	}
	
	@Override
	public void handleStereotype(Stereotype stereotype, NodeContext context) {
		addChild(node -> node
			.withAttribute(TEXT, labels.getSterotypeLabel(stereotype))
			.withAttribute(ICON, StereotypeIcons.getIcon(stereotype))
		);
	}

	@Override
	public void handleApplication(ApplicationModules application) {
		this.root
			.withAttribute(TEXT, labels.getApplicationLabel(application))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.APPLICATION_KEY))
		;
	}

	@Override
	public void handlePackage(StereotypePackageElement pkg, NodeContext context) {

		addChild(node -> node
			.withAttribute(TEXT, labels.getPackageLabel(pkg))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.PACKAGES_KEY))
		);
	}

	@Override
	public void handleType(StereotypeClassElement type, NodeContext context) {
		addChild(node -> node
			.withAttribute(TEXT, labels.getTypeLabel(type))
			.withAttribute(LOCATION, type.getLocation())
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.TYPE_KEY))
		);
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
	public void handleCustom(NamedInterfaceNode custom, NodeContext context) {
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

}
