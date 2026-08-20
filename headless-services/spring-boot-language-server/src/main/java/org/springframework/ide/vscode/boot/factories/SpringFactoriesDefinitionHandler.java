/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.factories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.links.JavaElementLocationProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.DefinitionHandler;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageSpecific;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Key;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Node;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Value;

/**
 * Go-to-definition support for the fully-qualified type names in spring.factories files.
 * Both the key (the factory type) and the individual comma-separated type names of the
 * value navigate to the respective Java type.
 *
 * @author Martin Lippert
 */
public class SpringFactoriesDefinitionHandler implements DefinitionHandler, LanguageSpecific {

	private static final Logger log = LoggerFactory.getLogger(SpringFactoriesDefinitionHandler.class);

	private final SimpleTextDocumentService documents;
	private final JavaProjectFinder projectFinder;
	private final JavaElementLocationProvider locationProvider;
	private final AntlrParser parser = new AntlrParser();

	public SpringFactoriesDefinitionHandler(SimpleTextDocumentService documents, JavaProjectFinder projectFinder,
			JavaElementLocationProvider locationProvider) {
		this.documents = documents;
		this.projectFinder = projectFinder;
		this.locationProvider = locationProvider;
	}

	@Override
	public Collection<LanguageId> supportedLanguages() {
		return List.of(LanguageId.SPRING_FACTORIES);
	}

	@Override
	public List<LocationLink> handle(CancelChecker cancelToken, DefinitionParams definitionParams) {
		try {
			TextDocument doc = documents.getLatestSnapshot(definitionParams);
			if (doc == null) {
				return Collections.emptyList();
			}

			IJavaProject project = projectFinder.find(doc.getId()).orElse(null);
			if (project == null) {
				return Collections.emptyList();
			}

			cancelToken.checkCanceled();

			PropertiesAst ast = parser.parse(doc.get()).ast;
			if (ast == null) {
				return Collections.emptyList();
			}

			int offset = doc.toOffset(definitionParams.getPosition());
			TypeReference typeReference = findTypeReference(doc, ast.findNode(offset), offset);
			if (typeReference == null) {
				return Collections.emptyList();
			}

			cancelToken.checkCanceled();

			return toLocationLinks(project, typeReference);
		} catch (CancellationException e) {
			throw e;
		} catch (Exception e) {
			log.error("", e);
			return Collections.emptyList();
		}
	}

	private TypeReference findTypeReference(TextDocument doc, Node node, int offset) throws Exception {
		if (node instanceof Key key) {
			return trim(doc, key.getOffset(), key.getLength());
		}
		else if (node instanceof Value value) {
			for (TypeReference reference : splitValue(doc, value)) {
				if (reference.contains(offset)) {
					return reference;
				}
			}
		}
		return null;
	}

	/**
	 * Splits the value of a key-value pair into its comma-separated type names. The
	 * offsets refer to the raw document text, so line continuations are skipped instead
	 * of being resolved.
	 */
	private List<TypeReference> splitValue(TextDocument doc, Value value) throws Exception {
		List<TypeReference> references = new ArrayList<>();

		int start = value.getOffset();
		int end = Math.min(start + value.getLength(), doc.getLength());
		String text = doc.get(start, end - start);

		int segmentStart = 0;
		for (int i = 0; i <= text.length(); i++) {
			if (i == text.length() || text.charAt(i) == ',') {
				TypeReference reference = trim(doc, start + segmentStart, i - segmentStart);
				if (reference != null) {
					references.add(reference);
				}
				segmentStart = i + 1;
			}
		}

		return references;
	}

	/**
	 * Strips whitespace and line continuation backslashes from both ends of the given
	 * region and returns the remaining type name together with its range.
	 */
	private TypeReference trim(TextDocument doc, int offset, int length) throws Exception {
		String text = doc.get(offset, length);

		int start = 0;
		int end = text.length();
		while (start < end && isIgnorable(text.charAt(start))) {
			start++;
		}
		while (end > start && isIgnorable(text.charAt(end - 1))) {
			end--;
		}

		if (start >= end) {
			return null;
		}

		return new TypeReference(text.substring(start, end), offset + start, end - start,
				doc.toRange(offset + start, end - start));
	}

	private static boolean isIgnorable(char c) {
		return c == '\\' || Character.isWhitespace(c);
	}

	private List<LocationLink> toLocationLinks(IJavaProject project, TypeReference typeReference) {
		IType type = project.getIndex().findType(typeReference.fqName());
		if (type == null) {
			return Collections.emptyList();
		}

		Location location = locationProvider.findLocation(project, type);
		if (location == null || location.getUri() == null || location.getRange() == null) {
			return Collections.emptyList();
		}

		return List.of(new LocationLink(location.getUri(), location.getRange(), location.getRange(),
				typeReference.range()));
	}

	private static record TypeReference(String fqName, int offset, int length, Range range) {

		boolean contains(int position) {
			return offset <= position && position <= offset + length;
		}

	}

}
