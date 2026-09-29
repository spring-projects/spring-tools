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

/**
 * A Java element the IDE's Java tooling can find by its JDT binding key - what a structure tree
 * node for an element read from a JAR carries instead of a location. Resolved into one only when
 * the node is actually opened ({@code sts/spring-boot/structure/resolveLocation}): asking the IDE
 * for the location of every such node while building the tree would be a round trip per node.
 *
 * @param projectUri the project whose classpath the element is on - the one including the JAR
 * @param bindingKey the element's JDT binding key
 *
 * @author Martin Lippert
 */
public record JavaElementReference(String projectUri, String bindingKey) {
}
