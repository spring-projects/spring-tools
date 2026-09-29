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
package org.springframework.ide.vscode.boot.java.beans;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.RecordComponentInfo;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBindingKeys;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

/**
 * The JAR/bytecode counterpart of {@link ConfigurationPropertiesIndexer}: adds a
 * {@code @ConfigurationProperties} class's properties - its fields, or its record components if it
 * is a record - to its bean, as the same {@link ConfigPropertyIndexElement}s the AST side adds, so
 * their label comes from the same code. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>What is shared with {@link ConfigurationPropertiesIndexer} rather than rewritten: the element
 * (and with it the label), and {@link ConfigurationPropertiesIndexer#resolvePrefix}. What is written
 * again, against Jandex instead of JDT: reading the fields and the annotation's attribute values.
 *
 * @author Martin Lippert
 */
public class JarConfigurationPropertiesScanner {

	private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple(Annotations.CONFIGURATION_PROPERTIES);

	/**
	 * Whether the class is a {@code @ConfigurationProperties} class - decided from its own
	 * (meta-expanded) annotations, as {@code ComponentIndexer.indexConfigurationProperties} does.
	 */
	public static boolean isConfigurationProperties(JarType type) {
		return type.ownAnnotationTypes().contains(Annotations.CONFIGURATION_PROPERTIES);
	}

	/**
	 * Adds one {@link ConfigPropertyIndexElement} per field (or record component) to the bean - in
	 * declaration order, as the AST side walks them.
	 */
	public static void addConfigurationProperties(Bean bean, JarType type) {
		// the prefix/value attribute values are only available from a *direct* annotation instance -
		// a class that only ever picks up @ConfigurationProperties via a meta-annotation (unusual)
		// degrades to an empty prefix, as it does on the AST side, which reads the direct one too
		AnnotationInstance annotation = type.classInfo().declaredAnnotation(CONFIGURATION_PROPERTIES);
		String prefix = ConfigurationPropertiesIndexer.resolvePrefix(stringAttribute(annotation, "prefix"), stringAttribute(annotation, "value"));

		if (type.classInfo().isRecord()) {
			for (RecordComponentInfo component : type.classInfo().recordComponentsInDeclarationOrder()) {
				ConfigPropertyIndexElement property = new ConfigPropertyIndexElement(prefix + component.name(),
						JdtStyleTypeNames.qualifiedName(component.type()), type.placeholderLocation().getRange(), null);
				// the component's backing field - there in every record's class file
				FieldInfo field = type.classInfo().field(component.name());
				if (field != null) {
					type.bindingKeys().put(property, JarBindingKeys.of(field));
				}
				bean.addChild(property);
			}
		}
		else {
			for (FieldInfo field : type.classInfo().fieldsInDeclarationOrder()) {
				if (!field.isSynthetic()) {
					ConfigPropertyIndexElement property = new ConfigPropertyIndexElement(prefix + field.name(),
							JdtStyleTypeNames.qualifiedName(field.type()), type.placeholderLocation().getRange(), null);
					type.bindingKeys().put(property, JarBindingKeys.of(field));
					bean.addChild(property);
				}
			}
		}
	}

	private static String stringAttribute(AnnotationInstance annotation, String attributeName) {
		if (annotation == null) {
			return null;
		}
		AnnotationValue value = annotation.value(attributeName);
		return value == null ? null : value.asString();
	}

}
