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
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBindingKeys;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

/**
 * The JAR/bytecode counterpart of {@link RequestMappingIndexer}: adds a
 * {@link RequestMappingIndexElement} per mapped path of every request-mapping method of a
 * controller, a {@code @BasePathAwareController} or a Feign client - the same elements, labelled by
 * the same {@link RouteUtils#createRouteLabel}, combined by the same
 * {@link WebEndpointIndexer#combinePath}. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>What is written again, against Jandex: reading the attribute values, with
 * {@link WebEndpointIndexer}'s fallbacks - an attribute the method's annotation does not declare
 * comes from the class's own {@code @RequestMapping}, or if it has none, from the first one found on
 * its interfaces (depth first) and then its superclasses. A constant used as a value needs no
 * resolving here: the compiler has inlined it.
 *
 * <p>One difference: a method with several request-mapping annotations has them read in the order
 * Jandex keeps them (sorted by name) rather than in source order - so its members, and which of them
 * labels its method node, can come in a different order.
 *
 * @author Martin Lippert
 */
public class JarRequestMappingScanner {

	private static final DotName REQUEST_MAPPING = DotName.createSimple(Annotations.SPRING_REQUEST_MAPPING);

	// "value" first: of two aliases only one may be set, so which one is read first is moot
	private static final List<String> VALUE_PATH = List.of("value", "path");

	/**
	 * {@code RequestMappingIndexer.indexRequestMappings}'s gate: the class's own annotations,
	 * meta-expanded.
	 */
	public static boolean hasRequestMappings(JarType type) {
		Set<String> own = type.ownAnnotationTypes();
		return own.contains(Annotations.CONTROLLER) || own.contains(Annotations.DATA_REST_BASE_PATH_AWARE_CONTROLLER)
				|| own.contains(Annotations.FEIGN_CLIENT);
	}

	public static void addRequestMappings(Bean bean, JarType type) {
		if (!hasRequestMappings(type)) {
			return;
		}

		for (MethodInfo method : JarStereotypeScanner.sourceLevelMethodsOf(type.classInfo())) {
			for (AnnotationInstance annotation : method.declaredAnnotations()) {
				if (JarStereotypeScanner.metaAnnotationTypesOf(annotation.name(), type.index()).contains(Annotations.SPRING_REQUEST_MAPPING)) {
					addRequestMapping(bean, type, method, annotation);
				}
			}
		}
	}

	/**
	 * {@code RequestMappingIndexer.indexRequestMapping}: one element per combination of class-level
	 * and method-level path.
	 */
	private static void addRequestMapping(Bean bean, JarType type, MethodInfo method, AnnotationInstance annotation) {
		String methodSignature = JarStereotypeScanner.methodSignatureOf(method);

		String[] path = path(annotation);
		String[] parentPath = parentPath(type);
		String[] methods = RequestMappingIndexer.METHOD_MAPPING.get(annotation.name().toString());
		if (methods == null) {
			methods = attributeValues(annotation, List.of("method"), type);
		}
		String[] contentTypes = attributeValues(annotation, List.of("produces"), type);
		String[] acceptTypes = attributeValues(annotation, List.of("consumes"), type);
		String[] versions = attributeValues(annotation, List.of("version"), type);
		String version = versions != null && versions.length == 1 ? versions[0] : null;

		String[] httpMethods = methods;
		Stream<String> parents = parentPath == null ? Stream.of("") : Arrays.stream(parentPath);
		parents.filter(Objects::nonNull)
				.flatMap(parent -> Arrays.stream(path).filter(Objects::nonNull).map(p -> WebEndpointIndexer.combinePath(parent, p)))
				.forEach(p -> {
					String label = RouteUtils.createRouteLabel(type.placeholderLocation(), p, httpMethods, contentTypes, acceptTypes, version);
					RequestMappingIndexElement element = new RequestMappingIndexElement(p, httpMethods, contentTypes, acceptTypes, version,
							type.placeholderLocation().getRange(), label, methodSignature, null);
					type.bindingKeys().put(element, JarBindingKeys.of(method));
					bean.addChild(element);
				});
	}

	/**
	 * {@code WebEndpointIndexer.getPath}: the annotation's own path, or the empty path.
	 */
	private static String[] path(AnnotationInstance annotation) {
		String[] result = ownValues(annotation, VALUE_PATH);
		return result != null ? result : new String[] { "" };
	}

	/**
	 * {@code WebEndpointIndexer.getParentPath}: the class's own {@code @RequestMapping}'s path, else
	 * the path declared by the first one on a supertype - {@code null} if there is none at all.
	 */
	private static String[] parentPath(JarType type) {
		AnnotationInstance classLevel = type.classInfo().declaredAnnotation(REQUEST_MAPPING);
		if (classLevel != null) {
			return path(classLevel);
		}
		return declaredValues(firstOnSupertypes(type.classInfo(), type.index()), VALUE_PATH);
	}

	/**
	 * {@code WebEndpointIndexer.getAttributeValues}: declared by the annotation itself, else by the
	 * class's own {@code @RequestMapping}, else - only if the class has none - by the first one on a
	 * supertype.
	 */
	private static String[] attributeValues(AnnotationInstance annotation, List<String> names, JarType type) {
		String[] result = ownValues(annotation, names);
		if (result == null) {
			AnnotationInstance classLevel = type.classInfo().declaredAnnotation(REQUEST_MAPPING);
			result = classLevel != null ? ownValues(classLevel, names) : declaredValues(firstOnSupertypes(type.classInfo(), type.index()), names);
		}
		return result;
	}

	/**
	 * {@code WebEndpointIndexer.findFirstRequestMappingAnnotation}, below the class itself (which
	 * has none when this is asked): interfaces first, depth first, then the superclass. A supertype
	 * that is not on the classpath index is skipped.
	 */
	private static AnnotationInstance firstOnSupertypes(ClassInfo classInfo, IndexView index) {
		for (Type supertype : supertypesToSearch(classInfo)) {
			AnnotationInstance found = firstOn(index.getClassByName(supertype.name()), index);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static AnnotationInstance firstOn(ClassInfo classInfo, IndexView index) {
		if (classInfo == null) {
			return null;
		}
		AnnotationInstance own = classInfo.declaredAnnotation(REQUEST_MAPPING);
		return own != null ? own : firstOnSupertypes(classInfo, index);
	}

	private static List<Type> supertypesToSearch(ClassInfo classInfo) {
		List<Type> result = new ArrayList<>(classInfo.interfaceTypes());
		if (classInfo.superClassType() != null && !classInfo.isInterface()) {
			result.add(classInfo.superClassType());
		}
		return result;
	}

	/**
	 * As {@link #declaredValues}, for an annotation of the method or class itself - read from source
	 * by {@code ASTUtils.getExpressionValueAsArray}, which has no string for a class literal or a
	 * nested annotation: such an element is left out, and a single one of them counts as not
	 * declared.
	 */
	private static String[] ownValues(AnnotationInstance annotation, List<String> names) {
		if (annotation == null) {
			return null;
		}
		for (String name : names) {
			AnnotationValue value = annotation.value(name);
			if (value != null) {
				if (value.kind() == AnnotationValue.Kind.ARRAY) {
					return value.asArrayList().stream().filter(JarRequestMappingScanner::hasSourceString)
							.map(JarRequestMappingScanner::stringOf).toArray(String[]::new);
				}
				return hasSourceString(value) ? new String[] { stringOf(value) } : null;
			}
		}
		return null;
	}

	private static boolean hasSourceString(AnnotationValue value) {
		return value.kind() != AnnotationValue.Kind.CLASS && value.kind() != AnnotationValue.Kind.NESTED;
	}

	/**
	 * The value of the first of the given attributes a supertype's annotation declares, as strings -
	 * {@code ASTUtils.getValuesFromValuePair}: an enum constant by its name, a class by its qualified
	 * name. {@code null} if it declares none of them.
	 */
	private static String[] declaredValues(AnnotationInstance annotation, List<String> names) {
		if (annotation == null) {
			return null;
		}
		for (String name : names) {
			AnnotationValue value = annotation.value(name);
			if (value != null) {
				return value.kind() == AnnotationValue.Kind.ARRAY
						? value.asArrayList().stream().map(JarRequestMappingScanner::stringOf).toArray(String[]::new)
						: new String[] { stringOf(value) };
			}
		}
		return null;
	}

	private static String stringOf(AnnotationValue value) {
		switch (value.kind()) {
		case ENUM:
			return value.asEnum();
		case CLASS:
			return JdtStyleTypeNames.qualifiedName(value.asClass());
		default:
			return value.value().toString();
		}
	}

}
