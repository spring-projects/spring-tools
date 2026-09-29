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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBindingKeys;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;

/**
 * The JAR/bytecode counterpart of {@link BeansIndexer#indexBeanMethod}, for the {@code @Bean}
 * methods of a {@code @Configuration} bean: a child {@link Bean} per bean name, labelled by the same
 * {@link BeansIndexer#beanLabel}. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>One approximation, accepted for this: the source side renders the method's other annotations
 * as their source text; here they are rebuilt from their values - identical for the common cases
 * (a marker annotation, a single string or constant), but not necessarily in how a value was
 * written (a constant's name, a static import, spacing). Also different: the annotations, and the
 * attributes of each, come in the order Jandex keeps them - sorted by name, not as written - and
 * annotations that are not in the class file (source retention, such as {@code @SuppressWarnings})
 * or that annotate the return type ({@code TYPE_USE}, such as a {@code @Nullable}) are missing.
 * Several {@code @Bean}-annotated annotations on one method are read in that order too.
 *
 * <p>Not read: injection points of the method, and a functional web router's routes - neither is
 * rendered as a member of the configuration class.
 *
 * @author Martin Lippert
 */
public class JarBeanMethodScanner {

	public static void addBeanMethods(Bean configuration, JarType type) {
		if (!configuration.isConfiguration()) {
			return;
		}

		for (MethodInfo method : JarStereotypeScanner.sourceLevelMethodsOf(type.classInfo())) {
			if (Modifier.isAbstract(method.flags())) {
				continue;
			}
			for (AnnotationInstance annotation : method.declaredAnnotations()) {
				if (JarStereotypeScanner.metaAnnotationTypesOf(annotation.name(), type.index()).contains(Annotations.BEAN)) {
					addBeanMethod(configuration, type, method, annotation);
				}
			}
		}
	}

	private static void addBeanMethod(Bean configuration, JarType type, MethodInfo method, AnnotationInstance annotation) {
		String markerString = "@Bean" + otherAnnotations(method);
		String beanType = JdtStyleTypeNames.name(method.returnType());

		for (String name : beanNames(annotation, method)) {
			Bean bean = new Bean(name, JdtStyleTypeNames.qualifiedName(method.returnType()), type.placeholderLocation(),
					DefaultValues.EMPTY_INJECTION_POINTS, supertypesOf(method.returnType(), type.index()),
					DefaultValues.EMPTY_ANNOTATIONS, false, BeansIndexer.beanLabel(name, beanType, markerString));
			type.bindingKeys().put(bean, JarBindingKeys.of(method));
			configuration.addChild(bean);
		}
	}

	/**
	 * {@code ASTUtils.findSupertypes} of the bean's type - as far as it is on the classpath index;
	 * not rendered in the tree, but part of the bean.
	 */
	private static Set<String> supertypesOf(Type type, Index index) {
		ClassInfo classInfo = index.getClassByName(type.name());
		return classInfo != null ? JarStereotypeScanner.supertypesOf(classInfo, index) : Set.of();
	}

	/**
	 * {@code BeanUtils.getBeanNamesFromBeanAnnotationWithRegions}: the {@code value} names, then the
	 * {@code name} ones - or, with neither, the method's name.
	 */
	private static List<String> beanNames(AnnotationInstance annotation, MethodInfo method) {
		List<String> names = new ArrayList<>();
		for (String attribute : List.of("value", "name")) {
			AnnotationValue value = annotation.value(attribute);
			if (value != null) {
				if (value.kind() == AnnotationValue.Kind.ARRAY) {
					value.asArrayList().forEach(element -> names.add(element.value().toString()));
				}
				else {
					names.add(value.value().toString());
				}
			}
		}
		return names.isEmpty() ? List.of(method.name()) : names;
	}

	/**
	 * {@code BeansIndexer.getAnnotations}: every annotation but {@code @Bean} itself, each preceded by
	 * a space - approximated from the annotation's values, see the class comment.
	 */
	private static String otherAnnotations(MethodInfo method) {
		StringBuilder result = new StringBuilder();
		for (AnnotationInstance annotation : method.declaredAnnotations()) {
			if (!Annotations.BEAN.equals(annotation.name().toString())) {
				result.append(' ').append(render(annotation));
			}
		}
		return result.toString();
	}

	private static String render(AnnotationInstance annotation) {
		String name = "@" + JdtStyleTypeNames.simpleName(annotation.name());
		List<AnnotationValue> values = annotation.values();
		if (values.isEmpty()) {
			return name;
		}
		if (values.size() == 1 && values.get(0).name().equals("value")) {
			return name + "(" + render(values.get(0)) + ")";
		}
		return name + "(" + values.stream().map(value -> value.name() + "=" + render(value)).collect(Collectors.joining(",")) + ")";
	}

	/**
	 * A string as a Java literal has it - the way the source text shows it in the common case.
	 */
	private static String escaped(String string) {
		return string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
	}

	private static String render(AnnotationValue value) {
		switch (value.kind()) {
		case STRING:
			return "\"" + escaped(value.asString()) + "\"";
		case ENUM:
			return JdtStyleTypeNames.simpleName(value.asEnumType()) + "." + value.asEnum();
		case CLASS:
			return JdtStyleTypeNames.name(value.asClass()) + ".class";
		case ARRAY:
			// a single element is usually written without the braces - and stored as an array either way
			List<AnnotationValue> elements = value.asArrayList();
			return elements.size() == 1 ? render(elements.get(0))
					: "{" + elements.stream().map(JarBeanMethodScanner::render).collect(Collectors.joining(",")) + "}";
		case NESTED:
			return render(value.asNested());
		default:
			return value.value().toString();
		}
	}

}
