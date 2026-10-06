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
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;

/**
 * Everything the JAR-side scanners know about one scanned class, handed to each of them the way
 * the AST-side indexers are all handed the same {@code TypeDeclaration} and context.
 *
 * @param classInfo the class, as Jandex read it
 * @param element the stereotype element built for it ({@link JarStereotypeScanner#ownClassesOf})
 * @param ownAnnotationTypes its own annotations, meta-expanded, without those of its supertypes
 *        ({@link JarStereotypeScanner#ownAnnotationTypesOf}) - what every "is this a component /
 *        a configuration class / ..." decision is made from, as on the AST side
 * @param index the combined index over the including project's classpath, to resolve supertypes
 *        and annotation types against
 * @param placeholderLocation the class entry inside the JAR, with an empty range - index elements
 *        need a non-null location; it is never sent to a client
 * @param jarFile the JAR the class was read from - for the few scanners that read method bodies
 *        ({@link JarBytecode})
 * @param bindingKeys where a scanner records the JDT binding key ({@link JarBindingKeys}) of each
 *        member element it adds, by the element's identity - what a tree node opens it by
 *
 * @author Martin Lippert
 */
public record JarType(ClassInfo classInfo, StereotypeClassElement element, Set<String> ownAnnotationTypes, IndexView index,
		Location placeholderLocation, File jarFile, Map<Object, String> bindingKeys) {
}
