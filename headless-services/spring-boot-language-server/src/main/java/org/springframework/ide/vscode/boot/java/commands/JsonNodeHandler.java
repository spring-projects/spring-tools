/*
 * Copyright 2025 - 2026 the original author or authors.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeDefinitionLocator;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Oliver Drotbohm
 * @author Martin Lippert
 */
public class JsonNodeHandler<A, C> implements NodeHandler<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> {
	
	private static final Logger log = LoggerFactory.getLogger(JsonNodeHandler.class);

	public static final String PROJECT_ID = "projectId";

	public static final String LOCATION = "location";
	public static final String REFERENCE = "reference";

	/**
	 * A {@link JavaElementReference} to the element a node stands for, set instead of a
	 * {@link #LOCATION} for one read from a JAR: clients resolve it into a location when the node is
	 * opened, via {@code sts/spring-boot/structure/resolveLocation}.
	 */
	public static final String JAVA_ELEMENT = "javaElement";

	public static final String ICON = "icon";
	public static final String TEXT = "text";
	public static final String HOVER = "hover";

	public static final String NODE_ID = "nodeId";

	/**
	 * A hash of the source code behind this node, computed while indexing. Lets the diff detect
	 * changes that leave the node itself looking identical - an edited method body, for instance -
	 * which are invisible to a comparison of labels, icons and children alone. Absent for nodes
	 * that don't stand for a piece of source code (packages and stereotype groups).
	 */
	public static final String CONTENT_HASH = "contentHash";

	/**
	 * How this node changed compared to the captured baseline of its project, attached only when a
	 * baseline was captured for it: "added", "removed" or "modified" for the node a change actually
	 * happened to, and "containsChanges" for the packages and groups on the way down to one. Clients
	 * highlight the first three and use the last only to keep the path to a change visible.
	 */
	public static final String CHANGE = "change";

	/**
	 * Whether a baseline has been captured for this project, set on the root node only.
	 * Independent of whether anything actually changed since that baseline - clients need this to
	 * tell "no baseline captured" apart from "baseline captured, nothing changed (yet)", which both
	 * look the same from the absence of {@link #CHANGE} attributes alone.
	 */
	public static final String HAS_BASELINE = "hasBaseline";

	/**
	 * The git commit sha and short message of the baseline this project's tree was actually compared
	 * against, set on the root node only alongside {@link #HAS_BASELINE}. Lets a client show the
	 * user which snapshot the highlighted changes are relative to - the most recent one by default,
	 * or an older one the user explicitly picked to compare against instead.
	 */
	public static final String COMPARED_AGAINST_SHA = "comparedAgainstSha";
	public static final String COMPARED_AGAINST_MESSAGE = "comparedAgainstMessage";

	/**
	 * When that baseline was captured, always set alongside the two attributes above. This is the
	 * only thing there is to show for a manually captured snapshot, which carries no commit at all.
	 */
	public static final String COMPARED_AGAINST_CAPTURED_AT = "comparedAgainstCapturedAt";

	/**
	 * Discriminates the kind of element a node represents, independent of its label or icon
	 * (several kinds of node can share the same icon). Used to identify nodes across two
	 * structure trees when diffing them.
	 */
	public static final String KIND = "kind";
	public static final String KIND_APPLICATION = "application";
	public static final String KIND_PACKAGE = "package";
	public static final String KIND_STEREOTYPE = "stereotype";
	public static final String KIND_TYPE = "type";
	public static final String KIND_MEMBER = "member";
	public static final String KIND_METHOD = "method";
	public static final String KIND_CUSTOM = "custom";

	private final Node root;
	private final LabelProvider<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> labels;
	private final BiConsumer<Node, C> customHandler;
	private final StructureElements elements;
	private final SourceLinks sourceLinks;
	private final StereotypeDefinitionLocator definitionLocator;
	private final IJavaProject project;

	private Node current;
	private StereotypeCatalog catalog;

	public JsonNodeHandler(LabelProvider<A, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, C> labels, BiConsumer<Node, C> customHandler,
			StructureElements elements, SourceLinks sourceLinks, StereotypeDefinitionLocator definitionLocator,
			StereotypeCatalog catalog, IJavaProject project) {
		this.labels = labels;
		this.elements = elements;
		this.customHandler = customHandler;
		this.sourceLinks = sourceLinks;
		this.definitionLocator = definitionLocator;
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
					.withAttribute(KIND, KIND_STEREOTYPE)
				);
		} else {
			var definition = catalog.getDefinition(stereotype);
			var sources = definition.getSources();

			final Location referenceLocation = referenceFor(stereotype, sources);
			addChild(node -> node
				.withAttribute(TEXT, labels.getStereotypeLabel(stereotype))
				.withAttribute(ICON, StereotypeIcons.getIcon(stereotype))
				.withAttribute(HOVER, "defined in: " + sources.toString())
				.withAttribute(REFERENCE, referenceLocation)
				.withAttribute(KIND, KIND_STEREOTYPE)
			);
		}
	}

	/**
	 * Identifies where the given stereotype is defined. Stereotypes that are defined in the source code
	 * of the project come with their exact location attached already, for stereotypes that are defined in
	 * a catalog file the exact position of the definition within that catalog file is looked up.
	 */
	private Location referenceFor(Stereotype stereotype, Set<Object> sources) {
		for (Object source : sources) {
			if (source instanceof Location location) {
				return location;
			}
		}

		Location catalogFileWithoutDefinitionRange = null;

		for (Object source : sources) {
			if (source instanceof URL url) {
				Optional<Range> definitionRange = definitionLocator.findDefinition(url, stereotype.getIdentifier());
				Location location = locationFor(url, definitionRange.orElseGet(JsonNodeHandler::startOfFile));

				if (location == null) {
					continue;
				}

				// a catalog file that we could pinpoint the definition in wins over one that we could not
				if (definitionRange.isPresent()) {
					return location;
				}
				else if (catalogFileWithoutDefinitionRange == null) {
					catalogFileWithoutDefinitionRange = location;
				}
			}
		}

		return catalogFileWithoutDefinitionRange;
	}

	private static Range startOfFile() {
		return new Range(new Position(0, 0), new Position(0, 0));
	}

	private Location locationFor(URL catalogFile, Range definitionRange) {
		try {
			URI uri = catalogFile.toURI();
			Location location = new Location(uri.toASCIIString(), definitionRange);

			if (Misc.JAR.equals(uri.getScheme())) {
				sourceLinks.sourceLinkForJarEntry(project, uri, definitionRange).map(u -> u.toASCIIString()).ifPresent(location::setUri);
			}

			return location;
		} catch (URISyntaxException e) {
			log.error("", e);
			return null;
		}
	}

	@Override
	public void handleApplication(A application) {
		this.root
			.withAttribute(TEXT, labels.getApplicationLabel(application))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.APPLICATION_KEY))
			.withAttribute(PROJECT_ID, project.getElementName())
			.withAttribute(KIND, KIND_APPLICATION)
		;
		assignNodeId(root, null);
	}

	@Override
	public void handlePackage(StereotypePackageElement pkg, NodeContext context) {

		addChild(node -> node
			.withAttribute(TEXT, labels.getPackageLabel(pkg))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.MODULE_KEY))
			.withAttribute(KIND, KIND_PACKAGE)
		);
	}

	@Override
	public void handleType(StereotypeClassElement type, NodeContext context) {
		addChild(node -> node
			.withAttribute(TEXT, labels.getTypeLabel(type))
			.withAttribute(LOCATION, type.getLocation())
			.withAttribute(JAVA_ELEMENT, javaElementOf(type.getLocation(), elements.bindingKeyOf(type)))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.TYPE_KEY))
			.withAttribute(KIND, KIND_TYPE)
			.withAttribute(CONTENT_HASH, type.getContentHash())
			.withChildren(createTypeSubnotes(node, type))
		);
	}

	private List<Node> createTypeSubnotes(Node parent, StereotypeClassElement type) {
		if (System.getProperty("disable-structure-view-details") != null) {
			return Collections.emptyList();
		}

		ArrayList<Node> result = new ArrayList<Node>();

		elements.membersOf(type).forEach(member -> {
			Node childNode = new Node(parent)
					.withAttribute(TEXT, member.label())
					.withAttribute(LOCATION, member.location())
					.withAttribute(JAVA_ELEMENT, javaElementOf(member.location(), member.bindingKey()))
					.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.METHOD_KEY))
					.withAttribute(KIND, KIND_MEMBER)
					.withAttribute(CONTENT_HASH, member.contentHash());

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
			.withAttribute(JAVA_ELEMENT, javaElementOf(method.getLocation(), elements.bindingKeyOf(method)))
			.withAttribute(ICON, StereotypeIcons.getIcon(StereotypeIcons.METHOD_KEY))
			.withAttribute(KIND, KIND_METHOD)
			.withAttribute(CONTENT_HASH, method.getContentHash())
		);
	}

	/**
	 * A reference by binding key for an element without a location - on the classpath of this tree's
	 * project, which is the one including the JAR it was read from.
	 */
	private JavaElementReference javaElementOf(Location location, String bindingKey) {
		if (location != null || bindingKey == null || project.getLocationUri() == null) {
			return null;
		}
		return new JavaElementReference(project.getLocationUri().toASCIIString(), bindingKey);
	}

	@Override
	public void handleCustom(C custom, NodeContext context) {
		addChild(node -> {
			node.withAttribute(KIND, KIND_CUSTOM);
			customHandler.accept(node, custom);
		});
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
		String textId = n.attributes.containsKey(PROJECT_ID) ? (String) n.attributes.get(PROJECT_ID)
				: n.attributes.containsKey(TEXT) ? (String) n.attributes.get(TEXT) : "";
		
		Location location = (Location) n.attributes.get(LOCATION);
		JavaElementReference javaElement = (JavaElementReference) n.attributes.get(JAVA_ELEMENT);

		// an element read from a JAR has no location to tell it apart from a same-labelled sibling,
		// but its binding key does just as well
		String locationId = location != null
				? location.getUri() + ":" + location.getRange().getStart().getLine() + ":" + location.getRange().getStart().getCharacter()
				: javaElement != null ? javaElement.bindingKey() : "";
		
		Location reference = (Location) n.attributes.get(REFERENCE);
		String referenceId = reference == null ? "" : reference.getUri();
		
		// assigned to every node of a tree, so plain concatenation rather than formatting and a regex
		String nodeSpecificId = withoutTrailingSeparators(textId + "|" + locationId + "|" + referenceId);
		
		n.attributes.put(NODE_ID, p != null && p.attributes.containsKey(NODE_ID) ? p.attributes.get(NODE_ID) + "/" + nodeSpecificId : nodeSpecificId);
	}

	private static String withoutTrailingSeparators(String id) {
		int end = id.length();
		while (end > 0 && id.charAt(end - 1) == '|') {
			end--;
		}
		return id.substring(0, end);
	}
	
	private Node addChildFoo(Consumer<Node> consumer) {

		var node = new Node(this.current);
		consumer.accept(node);
		assignNodeId(node, current);
		
		// check whether a node with the same ID already exists
		// (this can happen if there are methods as well as types found for the same stereotype, for example)
		// in this case, do not add the new node, but use the existing one
		Node alreadyExistingNode = this.current.getChildById(node.attributes.get(NODE_ID));

		if (alreadyExistingNode != null) {
			return alreadyExistingNode;
		}
		else {
			this.current.addChild(node);
			return node;
		}

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

		/**
		 * The children by their {@link JsonNodeHandler#NODE_ID} - the first one, should several have
		 * the same - so that adding a child doesn't take a look at every sibling it already has.
		 * Created along with the first child, never serialized.
		 */
		private transient Map<Object, Node> childrenById;

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
			children.forEach(this::addChild);
			return this;
		}

		private void addChild(Node child) {
			this.children.add(child);

			Object id = child.attributes.get(NODE_ID);
			if (id != null) {
				if (childrenById == null) {
					childrenById = new HashMap<>();
				}
				childrenById.putIfAbsent(id, child);
			}
		}

		private Node getChildById(Object id) {
			return id == null || childrenById == null ? null : childrenById.get(id);
		}

		/**
		 * The attributes of this node, keyed by the constants of {@link JsonNodeHandler}.
		 */
		public Map<String, Object> getAttributes() {
			return Collections.unmodifiableMap(attributes);
		}

		public Object getAttribute(String key) {
			return attributes.get(key);
		}

		public List<Node> getChildren() {
			return Collections.unmodifiableList(children);
		}

	}


}
