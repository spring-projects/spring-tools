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
package org.springframework.ide.vscode.boot.java.springai;

import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.MethodInfo;
import org.springframework.ide.vscode.boot.java.springai.SpringAiAnnotationIndexElement.AnnotationType;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBindingKeys;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;

/**
 * The JAR/bytecode counterpart of {@link SpringAiIndexer#indexSpringAiMethods}: a
 * {@link SpringAiAnnotationIndexElement} per Spring AI / MCP annotation of a bean's methods - by
 * the exact annotation type, not meta-annotations, as on the source side, and from the same table
 * ({@link SpringAiIndexer#ANNOTATION_TYPES}). See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Not read: the method's parameters, which the element carries but the tree does not render.
 * Several of these annotations on one method are read in the order Jandex keeps them (sorted by
 * name), not as written.
 *
 * @author Martin Lippert
 */
public class JarSpringAiScanner {

	public static void addSpringAiMethods(Bean bean, JarType type) {
		String containerBeanType = JdtStyleTypeNames.qualifiedName(type.classInfo().name());

		for (MethodInfo method : JarStereotypeScanner.sourceLevelMethodsOf(type.classInfo())) {
			for (AnnotationInstance annotation : method.declaredAnnotations()) {
				AnnotationType annotationType = SpringAiIndexer.ANNOTATION_TYPES.get(JdtStyleTypeNames.qualifiedName(annotation.name()));
				if (annotationType == null) {
					continue;
				}

				String name = stringAttribute(annotation, "name");
				if (name == null || name.isEmpty()) {
					name = method.name();
				}
				String description = stringAttribute(annotation, "description");

				SpringAiAnnotationIndexElement element = new SpringAiAnnotationIndexElement(annotationType, name,
						description == null ? "" : description, JarStereotypeScanner.methodSignatureOf(method), type.placeholderLocation(),
						containerBeanType, DefaultValues.EMPTY_ANNOTATIONS, List.of(), null);
				type.bindingKeys().put(element, JarBindingKeys.of(method));
				bean.addChild(element);
			}
		}
	}

	private static String stringAttribute(AnnotationInstance annotation, String name) {
		AnnotationValue value = annotation.value(name);
		return value != null && value.kind() == AnnotationValue.Kind.STRING ? value.asString() : null;
	}

}
