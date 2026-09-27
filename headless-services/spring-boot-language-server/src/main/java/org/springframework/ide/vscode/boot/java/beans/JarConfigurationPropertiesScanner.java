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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.RecordComponentInfo;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.commands.StructureMember;

/**
 * The JAR/bytecode counterpart of {@link ConfigurationPropertiesIndexer}: reads the "properties" a
 * {@code @ConfigurationProperties} class contributes - its fields, or its record components if it
 * is a record - directly from a Jandex {@link ClassInfo}, into the same
 * {@link StructureMember} shape {@code IndexStructureElements.membersOf} builds from the live
 * index. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Only the field/record-component enumeration is duplicated here - the one piece of real
 * decision logic, resolving the {@code prefix}/{@code value} attribute into the prefix each
 * property name is joined to, is shared with {@link ConfigurationPropertiesIndexer} via
 * {@link ConfigurationPropertiesIndexer#resolvePrefix}, so that rule cannot drift between the two.
 *
 * @author Martin Lippert
 */
public class JarConfigurationPropertiesScanner {

	private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple(Annotations.CONFIGURATION_PROPERTIES);

	/**
	 * @param annotationTypes the class's already-computed, meta-annotation-expanded annotation
	 *        types ({@code JarStereotypeScanner.annotationTypesOf}) - consulted first, so a class
	 *        that carries {@code @ConfigurationProperties} only via a meta-annotation is still
	 *        recognized, consistent with how the class itself gets grouped as a stereotype
	 * @return empty when the class is not a {@code @ConfigurationProperties} class
	 */
	public static List<StructureMember> membersOf(ClassInfo classInfo, Set<String> annotationTypes) {
		if (!annotationTypes.contains(Annotations.CONFIGURATION_PROPERTIES)) {
			return List.of();
		}

		// the prefix/value attribute values are only available from a *direct* annotation instance -
		// a class that only ever picks up @ConfigurationProperties via a meta-annotation (unusual)
		// degrades to an empty prefix rather than failing outright
		AnnotationInstance annotation = classInfo.annotation(CONFIGURATION_PROPERTIES);
		String prefix = ConfigurationPropertiesIndexer.resolvePrefix(stringAttribute(annotation, "prefix"), stringAttribute(annotation, "value"));

		List<StructureMember> result = new ArrayList<>();

		if (classInfo.isRecord()) {
			for (RecordComponentInfo component : classInfo.recordComponentsInDeclarationOrder()) {
				result.add(member(prefix + component.name(), component.type().name()));
			}
		} else {
			for (FieldInfo field : classInfo.fieldsInDeclarationOrder()) {
				if (!field.isSynthetic() && !field.isEnumConstant()) {
					result.add(member(prefix + field.name(), field.type().name()));
				}
			}
		}

		return result;
	}

	private static StructureMember member(String name, DotName type) {
		return new StructureMember(name + " (" + type.local() + ")", null, null);
	}

	private static String stringAttribute(AnnotationInstance annotation, String attributeName) {
		if (annotation == null) {
			return null;
		}
		AnnotationValue value = annotation.value(attributeName);
		return value == null ? null : value.asString();
	}

}
