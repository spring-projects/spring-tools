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

import org.eclipse.lsp4j.Location;

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
 *
 * @author Martin Lippert
 */
public record StructureMember(String label, Location location, String contentHash) {
}
