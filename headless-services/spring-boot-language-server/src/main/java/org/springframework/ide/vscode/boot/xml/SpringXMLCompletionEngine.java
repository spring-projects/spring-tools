/*******************************************************************************
 * Copyright (c) 2019, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.xml;

import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.ALIAS_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.ARG_TYPE_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.BASE_PACKAGE_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.BEANS_NAMESPACE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.BEAN_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.BEAN_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.CLASS_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.COMPONENT_SCAN_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.CONSTRUCTOR_ARG_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.CONTEXT_NAMESPACE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.DEPENDS_ON_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.ENTRY_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.FACTORY_BEAN_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.IDREF_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.KEY_REF_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.KEY_TYPE_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.LOOKUP_METHOD_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.MATCH_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.NAME_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.NAME_GENERATOR_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.PARENT_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.PROPERTY_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.REF_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.REF_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.REPLACED_METHOD_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.REPLACER_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.SCOPE_RESOLVER_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.TYPE_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.UTIL_NAMESPACE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.VALUE_ELEMENT;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.VALUE_REF_ATTRIBUTE;
import static org.springframework.ide.vscode.boot.xml.XmlConfigConstants.VALUE_TYPE_ATTRIBUTE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.lemminx.dom.DOMAttr;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lemminx.dom.parser.Scanner;
import org.eclipse.lemminx.dom.parser.TokenType;
import org.eclipse.lemminx.dom.parser.XMLScanner;
import org.eclipse.lsp4j.CompletionItemKind;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.xml.completions.BeanRefCompletionProposalProvider;
import org.springframework.ide.vscode.boot.xml.completions.ConstructorArgNameCompletionProposalProvider;
import org.springframework.ide.vscode.boot.xml.completions.GenericXMLCompletionProposal;
import org.springframework.ide.vscode.boot.xml.completions.NamespaceCompletionProvider;
import org.springframework.ide.vscode.boot.xml.completions.PropertyNameCompletionProposalProvider;
import org.springframework.ide.vscode.boot.xml.completions.TypeCompletionProposalProvider;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.InternalCompletionList;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageSpecific;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

/**
 * @author Martin Lippert
 * @author Kris De Volder
 */
public class SpringXMLCompletionEngine implements ICompletionEngine, LanguageSpecific {

	private final Map<XMLElementKey, XMLCompletionProvider> completionProviders;
	private final BootJavaConfig config;
	
	public SpringXMLCompletionEngine(
			SimpleLanguageServer server, 
			JavaProjectFinder projectFinder, 
			SpringSymbolIndex symbolIndex, 
			BootJavaConfig config
	) {

		this.config = config;
		this.completionProviders = new HashMap<>();

		TypeCompletionProposalProvider classesOnlyProvider = new TypeCompletionProposalProvider(server, projectFinder, true, true, false, false);
		TypeCompletionProposalProvider classesAndInterfacesProvider = new TypeCompletionProposalProvider(server, projectFinder, true, true, true, false);
		TypeCompletionProposalProvider packagesProvider = new TypeCompletionProposalProvider(server, projectFinder, true, false, false, false);

		BeanRefCompletionProposalProvider beanRefProvider = new BeanRefCompletionProposalProvider(projectFinder, symbolIndex);
		PropertyNameCompletionProposalProvider propertyNameProvider = new PropertyNameCompletionProposalProvider(projectFinder);
		ConstructorArgNameCompletionProposalProvider constructorArgNameProvider = new ConstructorArgNameCompletionProposalProvider(projectFinder);
		
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, BEAN_ELEMENT, CLASS_ATTRIBUTE), classesOnlyProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, CONSTRUCTOR_ARG_ELEMENT, TYPE_ATTRIBUTE), classesAndInterfacesProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, ARG_TYPE_ELEMENT, MATCH_ATTRIBUTE), classesAndInterfacesProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, VALUE_ELEMENT, TYPE_ATTRIBUTE), classesAndInterfacesProvider);
		
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, BEAN_ELEMENT, PARENT_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, BEAN_ELEMENT, DEPENDS_ON_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, BEAN_ELEMENT, FACTORY_BEAN_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, REF_ELEMENT, BEAN_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, IDREF_ELEMENT, BEAN_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, CONSTRUCTOR_ARG_ELEMENT, REF_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, ALIAS_ELEMENT, NAME_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, REPLACED_METHOD_ELEMENT, REPLACER_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, ENTRY_ELEMENT, VALUE_REF_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, ENTRY_ELEMENT, KEY_REF_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, LOOKUP_METHOD_ELEMENT, BEAN_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, BEAN_ELEMENT, PROPERTY_ELEMENT, REF_ATTRIBUTE), beanRefProvider);

		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, BEAN_ELEMENT, PROPERTY_ELEMENT, NAME_ATTRIBUTE), propertyNameProvider);
		this.completionProviders.put(new XMLElementKey(BEANS_NAMESPACE, null, CONSTRUCTOR_ARG_ELEMENT, NAME_ATTRIBUTE), constructorArgNameProvider);
		
		this.completionProviders.put(new XMLElementKey(UTIL_NAMESPACE, null, null, VALUE_TYPE_ATTRIBUTE), classesAndInterfacesProvider);
		this.completionProviders.put(new XMLElementKey(UTIL_NAMESPACE, null, null, KEY_TYPE_ATTRIBUTE), classesAndInterfacesProvider);

		this.completionProviders.put(new XMLElementKey(CONTEXT_NAMESPACE, null, COMPONENT_SCAN_ELEMENT, BASE_PACKAGE_ATTRIBUTE), packagesProvider);
		this.completionProviders.put(new XMLElementKey(CONTEXT_NAMESPACE, null, COMPONENT_SCAN_ELEMENT, NAME_GENERATOR_ATTRIBUTE), beanRefProvider);
		this.completionProviders.put(new XMLElementKey(CONTEXT_NAMESPACE, null, COMPONENT_SCAN_ELEMENT, SCOPE_RESOLVER_ATTRIBUTE), beanRefProvider);
	}

	@Override
	public InternalCompletionList getCompletions(TextDocument doc, int offset) throws Exception {
		if (!config.isSpringXMLSupportEnabled() || !config.isXmlContentAssistEnabled()) {
			return new InternalCompletionList(Collections.emptyList(), false);
		}
		
		String content = doc.get();
		
		// if doc is empty, create simple skeleton snippet
		if (content != null && content.length() == 0) {
			return emptySpringXMLConfigSnippet(doc);
		}

		// if doc is not empty, dive into the details and provide more sophisticated content assist proposals
		DOMParser parser = DOMParser.getInstance();
		DOMDocument dom = parser.parse(content, "", null);

		DOMNode node = dom.findNodeBefore(offset);

		if (node != null) {
			String namespace = node.getNamespaceURI();

			Scanner scanner = XMLScanner.createScanner(content, node.getStart(), false);
			TokenType token = scanner.scan();
			while (token != TokenType.EOS && scanner.getTokenOffset() <= offset) {
				switch (token) {
				case AttributeValue:
					if (scanner.getTokenOffset() <= offset && offset <= scanner.getTokenEnd()) {
						DOMAttr attributeAt = dom.findAttrAt(offset);

						if (attributeAt != null) {
							XMLElementKey key = new XMLElementKey(namespace, null, node.getLocalName(), attributeAt.getNodeName());

							if (!this.completionProviders.containsKey(key)) {
								DOMNode parentNode = node.getParentNode();
								String parentNodeName = parentNode != null ? parentNode.getLocalName() : null;
								key = new XMLElementKey(namespace, parentNodeName, node.getLocalName(), attributeAt.getNodeName());
							}

							XMLCompletionProvider completionProvider = this.completionProviders.get(key);
							if (completionProvider != null) {
								InternalCompletionList completions = completionProvider.getCompletions(doc, namespace, node, attributeAt, scanner, offset);
								return completions;
							}
						}
					}
					break;
				case AttributeName:
					if (scanner.getTokenOffset() <= offset && offset < scanner.getTokenEnd()) {
						if (node.getParentNode() != null && node.getParentNode() instanceof DOMDocument
								&& namespace.equals("http://www.springframework.org/schema/beans") && node.getLocalName().equals("beans")) {
							return new InternalCompletionList(NamespaceCompletionProvider.createNamespaceCompletionProposals(doc, offset, token, node), false);
						}
					}
					break;
				case Whitespace:
					if (scanner.getTokenOffset() <= offset && offset < scanner.getTokenEnd()) {
						if (node.getParentNode() != null && node.getParentNode() instanceof DOMDocument
								&& namespace.equals("http://www.springframework.org/schema/beans") && node.getLocalName().equals("beans")) {
							return new InternalCompletionList(NamespaceCompletionProvider.createNamespaceCompletionProposals(doc, offset, token, node), false);
						}
					}
					break;
				default:
					break;
				}
				token = scanner.scan();
			}
		}
		return new InternalCompletionList(Collections.emptyList(), false);
	}

	private InternalCompletionList emptySpringXMLConfigSnippet(TextDocument doc) {

		CompletionItemKind kind = CompletionItemKind.Snippet;
		
		String label = "Spring XML config file skeleton";

		String snippetText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n"
				+ "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
				+ "    xsi:schemaLocation=\"\n"
				+ "        http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n"
				+ "\n"
				+ "    <!-- bean definitions here -->\n"
				+ "    $0\n"
				+ "\n"
				+ "</beans>";
		
		String descriptionDetails = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n"
				+ "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
				+ "    xsi:schemaLocation=\"\n"
				+ "        http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n"
				+ "\n"
				+ "    <!-- bean definitions here -->\n"
				+ "    \n"
				+ "</beans>";
		
		String details = "Insert Spring XML config file skeleton";
		
		DocumentEdits edits = new DocumentEdits(doc, true);
		edits.insertSnippet(0, snippetText);
		
		Renderable documentation = Renderables.inlineMultiLineSnippet(Renderables.text(descriptionDetails));
		
		ICompletionProposal proposal = new GenericXMLCompletionProposal(label, kind, edits, details, documentation, 1.0d);
		Collection<ICompletionProposal> completions = new ArrayList<>(1);
		completions.add(proposal);
		
		return new InternalCompletionList(completions, false);
	}
	
	@Override
	public Collection<LanguageId> supportedLanguages() {
		return ImmutableList.of(LanguageId.XML);
	}
}
