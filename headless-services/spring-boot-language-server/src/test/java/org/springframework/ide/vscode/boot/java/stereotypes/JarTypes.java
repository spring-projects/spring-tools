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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.springframework.ide.vscode.boot.java.beans.JarBeanIndexer;
import org.springframework.ide.vscode.boot.java.commands.StructureMember;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

/**
 * Scans a fixture JAR the way {@code JarDependencySource} does - minus the including project's
 * classpath - for tests of the individual JAR-side scanners.
 *
 * @author Martin Lippert
 */
public class JarTypes {

	/**
	 * @return the scanned source-level types, by fully qualified (binary) name
	 */
	public static Map<String, JarType> scan(File jar) {
		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		Map<String, JarType> result = new LinkedHashMap<>();
		for (StereotypeClassElement element : JarStereotypeScanner.ownClassesOf(ownClasses, index)) {
			var classInfo = index.getClassByName(DotName.createSimple(element.getType()));
			Location placeholder = new Location("jar:" + jar.toURI() + "!/" + element.getType().replace('.', '/') + ".class",
					new Range(new Position(0, 0), new Position(0, 0)));
			result.put(element.getType(), new JarType(classInfo, element, JarStereotypeScanner.ownAnnotationTypesOf(classInfo, index), index,
					placeholder, new IdentityHashMap<>()));
		}
		return result;
	}

	/**
	 * The binding keys the type's members were recorded with, in member order - what their nodes
	 * open them by.
	 */
	public static List<String> memberKeys(JarType type) {
		return JarBeanIndexer.beansOf(type).stream()
				.flatMap(bean -> bean.getChildren().stream())
				.filter(SymbolElement.class::isInstance)
				.map(child -> type.bindingKeys().get(child))
				.toList();
	}

	/**
	 * The member labels the type's beans contribute, in order - what the structure tree shows below
	 * the type.
	 */
	public static List<String> memberLabels(JarType type) {
		return JarBeanIndexer.beansOf(type).stream()
				.flatMap(bean -> bean.getChildren().stream())
				.filter(SymbolElement.class::isInstance)
				.map(child -> StructureMember.of((SymbolElement) child, null).label())
				.toList();
	}

}
