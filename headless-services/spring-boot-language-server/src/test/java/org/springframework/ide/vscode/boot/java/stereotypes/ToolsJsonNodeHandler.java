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

package org.springframework.ide.vscode.boot.java.stereotypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.tooling.LabelProvider;
import org.jmolecules.stereotype.tooling.MethodNodeContext;
import org.jmolecules.stereotype.tooling.NodeContext;
import org.jmolecules.stereotype.tooling.NodeHandler;

import net.minidev.json.JSONObject;

/**
 * @author Oliver Drotbohm
 * @author Martin Lippert
 */
public class ToolsJsonNodeHandler implements NodeHandler<StereotypePackageElement, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, Object> {

	public static final String ICON = "icon";
	public static final String TEXT = "text";

	private final Node root;
	private final LabelProvider<StereotypePackageElement, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, Object> labels;
	private final BiConsumer<Node, Object> customHandler;
	private Node current;

	public ToolsJsonNodeHandler(LabelProvider<StereotypePackageElement, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, Object> labels, BiConsumer<Node, Object> customHandler) {
		this.labels = labels;
		this.root = new Node(null);
		this.customHandler = customHandler;
		this.current = root;
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.stereotype.tooling.NodeHandler#handleApplication(java.lang.Object)
	 */
	@Override
	public void handleApplication(StereotypePackageElement application) {
		this.root.withAttribute(TEXT, labels.getApplicationLabel(application));
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.stereotype.tooling.NodeHandler#handlePackage(java.lang.Object, org.jmolecules.stereotype.tooling.NodeContext)
	 */
	@Override
	public void handlePackage(StereotypePackageElement pkg, NodeContext context) {

		addChild(node -> node
				.withAttribute(ICON, "fa-package")
				.withAttribute(TEXT, labels.getPackageLabel(pkg)));
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.stereotype.tooling.NodeHandler#handleStereotype(org.jmolecules.stereotype.api.Stereotype, org.jmolecules.stereotype.tooling.NestingLevel)
	 */
	@Override
	public void handleStereotype(Stereotype stereotype, NodeContext context) {

		addChild(node -> node.withAttribute(ICON, "fa-stereotype")
				.withAttribute(TEXT, labels.getSterotypeLabel(stereotype)));
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.stereotype.tooling.NodeHandler#handleType(java.lang.Object, org.jmolecules.stereotype.tooling.NestingLevel)
	 */
	@Override
	public void handleType(StereotypeClassElement type, NodeContext context) {
		addChild(node -> node
			.withAttribute(TEXT, labels.getTypeLabel(type))
			.withAttribute("location", type.getLocation())
		);
	}

	public Node createNested() {
		return new Node(this.current);
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.stereotype.tooling.NodeHandler#handleMethod(java.lang.Object, org.jmolecules.stereotype.tooling.NestingLevel, boolean)
	 */
	@Override
	public void handleMethod(StereotypeMethodElement method, MethodNodeContext<StereotypeClassElement> context) {
		addChildFoo(node -> node.withAttribute("title", labels.getMethodLabel(method, context.getContextualType())));
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.stereotype.tooling.NodeHandler#handleCustom(java.lang.Object, org.jmolecules.stereotype.tooling.NodeContext)
	 */
	@Override
	public void handleCustom(Object custom, NodeContext context) {
		addChild(node -> customHandler.accept(node, custom));
	}

	/*
	 * (non-Javadoc)
	 * @see org.jmolecules.stereotype.tooling.NodeHandler#postGroup()
	 */
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

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return render(root).toJSONString();
	}

	private JSONObject render(Node node) {

		var object = new JSONObject();

		object.putAll(node.attributes);

		if (!node.children.isEmpty()) {
			object.put("children", node.children.stream().map(this::render).toList());
		}

		return object;
	}

	public static class Node {

		private final Node parent;
		private final Map<String, Object> attributes;
		private final List<Node> children;

		Node(Node parent) {
			this.parent = parent;
			this.attributes = new LinkedHashMap<>();
			this.children = new ArrayList<>();
		}

		public Node withAttribute(String key, Object value) {
			this.attributes.put(key, value);
			return this;
		}
	}
}
