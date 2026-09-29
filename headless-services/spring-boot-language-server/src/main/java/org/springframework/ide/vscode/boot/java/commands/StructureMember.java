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
package org.springframework.ide.vscode.boot.java.commands;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

/**
 * One member a type contributes to the structure view beyond its own {@code StereotypeMethodElement}s
 * - e.g. an event listener implementation or a plain event-publishing method, neither of which is a
 * stereotype-annotated method of its own. See {@link StructureViewUtil#membersOf}.
 *
 * <p>Carries everything {@link JsonNodeHandler} needs to render a member node, independent of where
 * it came from - the live index today ({@link IndexStructureElements}), or a captured baseline
 * snapshot once {@code docs/structure-diff-elements.md} lands its snapshot format.
 *
 * @param label the label to render for the member
 * @param location where the member is defined in source, or {@code null} when it isn't (a member
 *        reconstructed from a snapshot has no source location)
 * @param contentHash a hash of the member's source, used to detect edits that leave its label
 *        unchanged (may be {@code null})
 * @param bindingKey the JDT binding key the IDE can open the member by when it has no location -
 *        one read from a JAR (see {@code JarBindingKeys}); {@code null} otherwise
 *
 * @author Martin Lippert
 */
public record StructureMember(String label, Location location, String contentHash, String bindingKey) {

	/**
	 * A member with a location of its own - one of a project's, not of a JAR.
	 */
	public StructureMember(String label, Location location, String contentHash) {
		this(label, location, contentHash, null);
	}

	/**
	 * This member, identified by the given JDT binding key for the IDE to open it by - for a member
	 * read from a JAR, which has no location.
	 */
	public StructureMember withBindingKey(String bindingKey) {
		return new StructureMember(label, location, contentHash, bindingKey);
	}

	/**
	 * The member an index element stands for - label and content hash from the element itself, so
	 * a member reads the same whether the element came from the live index or from a JAR scan.
	 *
	 * @param documentUri the document the element is in, or {@code null} for one without a real
	 *        location (a JAR-scanned element, whose range is only a placeholder)
	 */
	public static StructureMember of(SymbolElement element, String documentUri) {
		DocumentSymbol symbol = element.getDocumentSymbol();
		return new StructureMember(symbol.getName(), documentUri == null ? null : new Location(documentUri, symbol.getRange()),
				element.getContentHash());
	}

}
