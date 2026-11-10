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
package org.springframework.ide.vscode.boot.kotlin.bindings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.AnnotationUse;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassKind;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.springframework.ide.vscode.commons.java.IAnnotation;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.java.JavaUtils;

/**
 * The implementations of {@link ResolvedType} and friends that the Kotlin type resolution
 * produces. They answer the same questions the JDT bindings do for Java: names, kinds, supertypes
 * and annotations, in the way the JVM sees the types.
 *
 * @author Martin Lippert
 */
final class KotlinTypes {

	private KotlinTypes() {
	}

	/** equality by what the type stands for, so types that were resolved twice are the same */
	abstract static class BaseType implements ResolvedType {

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BaseType other && getKey().equals(other.getKey());
		}

		@Override
		public int hashCode() {
			return getKey().hashCode();
		}

		@Override
		public String toString() {
			return getQualifiedName();
		}

		protected static String simpleName(String name) {
			int index = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
			return index >= 0 ? name.substring(index + 1) : name;
		}

		@Override
		public ResolvedType getErasure() {
			return this;
		}

		@Override
		public boolean isParameterizedType() {
			return false;
		}

		@Override
		public boolean isAnnotation() {
			return false;
		}

		@Override
		public boolean isInterface() {
			return false;
		}

		@Override
		public ResolvedType getSuperclass() {
			return null;
		}

		@Override
		public List<ResolvedType> getInterfaces() {
			return List.of();
		}

		@Override
		public List<ResolvedAnnotation> getAnnotations() {
			return List.of();
		}
	}

	/** int, boolean, void, ... */
	static final class PrimitiveType extends BaseType {

		private final String name;

		PrimitiveType(String name) {
			this.name = name;
		}

		/** the letter the JVM uses for the primitive in descriptors, which is also what JDT answers as binary name and key */
		String descriptor() {
			return switch (name) {
				case "int" -> "I";
				case "long" -> "J";
				case "short" -> "S";
				case "byte" -> "B";
				case "char" -> "C";
				case "boolean" -> "Z";
				case "float" -> "F";
				case "double" -> "D";
				default -> "V";
			};
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getQualifiedName() {
			return name;
		}

		@Override
		public String getBinaryName() {
			return descriptor();
		}

		@Override
		public String getKey() {
			return descriptor();
		}
	}

	static final class ArrayType extends BaseType {

		private final ResolvedType component;

		ArrayType(ResolvedType component) {
			this.component = component;
		}

		@Override
		public String getName() {
			return component.getName() + "[]";
		}

		@Override
		public String getQualifiedName() {
			return component.getQualifiedName() + "[]";
		}

		@Override
		public String getBinaryName() {
			String componentName = component.getBinaryName();
			if (component instanceof PrimitiveType || componentName.startsWith("[")) {
				return "[" + componentName;
			}
			return "[L" + componentName + ";";
		}

		@Override
		public String getKey() {
			return "[" + component.getKey();
		}
	}

	/** a name that could not be resolved, which is still kept as it was written */
	static final class UnresolvedType extends BaseType {

		private final String written;

		UnresolvedType(String written) {
			this.written = written;
		}

		@Override
		public String getName() {
			return simpleName(written);
		}

		@Override
		public String getQualifiedName() {
			return written;
		}

		@Override
		public String getBinaryName() {
			return written;
		}

		@Override
		public String getKey() {
			return JavaUtils.typeFqNameToBindingKey(written);
		}
	}

	/** a generic type together with its type arguments */
	static final class ParameterizedType extends BaseType {

		private final ResolvedType type;
		private final List<ResolvedType> arguments;

		ParameterizedType(ResolvedType type, List<ResolvedType> arguments) {
			this.type = type;
			this.arguments = arguments;
		}

		@Override
		public String getName() {
			return type.getName() + arguments.stream().map(ResolvedType::getName).collect(Collectors.joining(",", "<", ">"));
		}

		@Override
		public String getQualifiedName() {
			return type.getQualifiedName() + arguments.stream().map(ResolvedType::getQualifiedName).collect(Collectors.joining(",", "<", ">"));
		}

		@Override
		public String getBinaryName() {
			return type.getBinaryName();
		}

		@Override
		public String getKey() {
			String key = type.getKey();
			return key.substring(0, key.length() - 1) + arguments.stream().map(ResolvedType::getKey).collect(Collectors.joining("", "<", ">;"));
		}

		@Override
		public ResolvedType getErasure() {
			return type;
		}

		@Override
		public boolean isParameterizedType() {
			return true;
		}

		@Override
		public boolean isAnnotation() {
			return type.isAnnotation();
		}

		@Override
		public boolean isInterface() {
			return type.isInterface();
		}

		@Override
		public ResolvedType getSuperclass() {
			return type.getSuperclass();
		}

		@Override
		public List<ResolvedType> getInterfaces() {
			return type.getInterfaces();
		}

		@Override
		public List<ResolvedAnnotation> getAnnotations() {
			return type.getAnnotations();
		}
	}

	/** a class from the classpath: a library, or the compiled output of the project */
	static final class ClasspathType extends BaseType {

		private final KotlinTypeResolver resolver;
		private final IType type;
		private List<ResolvedType> interfaces;
		private List<ResolvedAnnotation> annotations;

		ClasspathType(KotlinTypeResolver resolver, IType type) {
			this.resolver = resolver;
			this.type = type;
		}

		@Override
		public String getName() {
			return simpleName(type.getFullyQualifiedName());
		}

		@Override
		public String getQualifiedName() {
			return type.getFullyQualifiedName().replace('$', '.');
		}

		@Override
		public String getBinaryName() {
			return type.getFullyQualifiedName();
		}

		@Override
		public String getKey() {
			return JavaUtils.typeFqNameToBindingKey(type.getFullyQualifiedName());
		}

		@Override
		public boolean isAnnotation() {
			return type.isAnnotation();
		}

		@Override
		public boolean isInterface() {
			return type.isInterface();
		}

		@Override
		public ResolvedType getSuperclass() {
			if (type.isInterface()) {
				return null; // interfaces have Object as the super class in class files, JDT bindings say they have none
			}
			String name = type.getSuperclassName();
			return name != null ? resolver.resolveBinaryName(name) : null;
		}

		@Override
		public synchronized List<ResolvedType> getInterfaces() {
			if (interfaces == null) {
				List<ResolvedType> result = new ArrayList<>();
				String[] names = type.getSuperInterfaceNames();
				if (names != null) {
					for (String name : names) {
						ResolvedType resolved = resolver.resolveBinaryName(name);
						if (resolved != null) {
							result.add(resolved);
						}
					}
				}
				interfaces = result;
			}
			return interfaces;
		}

		@Override
		public synchronized List<ResolvedAnnotation> getAnnotations() {
			if (annotations == null) {
				List<ResolvedAnnotation> result = new ArrayList<>();
				type.getDeclaredAnnotations().forEach(annotation -> addAnnotation(result, annotation));
				annotations = result;
			}
			return annotations;
		}

		private void addAnnotation(List<ResolvedAnnotation> result, IAnnotation annotation) {
			ResolvedType annotationType = resolver.resolveBinaryName(annotation.fqName());
			if (annotationType != null) {
				result.add(new ClasspathAnnotation(resolver, annotationType, annotation));
			}
		}
	}

	/** a class that is declared in a Kotlin source file of the project */
	static final class SourceType extends BaseType {

		private final KotlinTypeResolver resolver;
		private final KotlinSourceClass source;
		private volatile List<ResolvedType> declaredSupertypes;
		private volatile List<ResolvedType> supertypes;
		private List<ResolvedAnnotation> annotations;

		SourceType(KotlinTypeResolver resolver, KotlinSourceClass source) {
			this.resolver = resolver;
			this.source = source;
		}

		KotlinSourceClass source() {
			return source;
		}

		@Override
		public String getName() {
			return source.declaration().name();
		}

		@Override
		public String getQualifiedName() {
			return source.binaryName().replace('$', '.');
		}

		@Override
		public String getBinaryName() {
			return source.binaryName();
		}

		@Override
		public String getKey() {
			return JavaUtils.typeFqNameToBindingKey(source.binaryName());
		}

		@Override
		public boolean isAnnotation() {
			return source.declaration().kind() == ClassKind.ANNOTATION;
		}

		@Override
		public boolean isInterface() {
			return source.declaration().kind() == ClassKind.INTERFACE || isAnnotation();
		}

		/** the supertypes as written, cycles included */
		private List<ResolvedType> declaredSupertypes() {
			List<ResolvedType> result = declaredSupertypes;
			if (result == null) {
				result = new ArrayList<>();
				for (var supertype : source.declaration().supertypes()) {
					ResolvedType resolved = resolver.resolveTypeReference(supertype, source.file(), source.outer());
					if (resolved != null && !resolved.equals(this) && !(resolved instanceof UnresolvedType)) {
						result.add(resolved.getErasure());
					}
				}
				declaredSupertypes = result;
			}
			return result;
		}

		/**
		 * The supertypes without the ones whose hierarchy leads back to this type. Cyclic code does
		 * not compile, and JDT cuts such cycles as well: the indexers walk the hierarchy without
		 * guarding against cycles.
		 */
		private List<ResolvedType> supertypes() {
			List<ResolvedType> result = supertypes;
			if (result == null) {
				result = declaredSupertypes().stream().filter(supertype -> !leadsTo(supertype, this)).toList();
				supertypes = result;
			}
			return result;
		}

		/** whether the hierarchy of the given type contains the target type */
		private static boolean leadsTo(ResolvedType from, ResolvedType target) {
			Set<String> seen = new HashSet<>();
			Deque<ResolvedType> todo = new ArrayDeque<>();
			todo.add(from);

			while (!todo.isEmpty()) {
				ResolvedType current = todo.poll();
				if (!seen.add(current.getKey())) {
					continue;
				}
				if (current.equals(target)) {
					return true;
				}
				if (current instanceof SourceType sourceType) {
					todo.addAll(sourceType.declaredSupertypes());
				}
				else {
					if (current.getSuperclass() != null) {
						todo.add(current.getSuperclass());
					}
					todo.addAll(current.getInterfaces());
				}
			}
			return false;
		}

		@Override
		public ResolvedType getSuperclass() {
			if (isInterface()) {
				return null;
			}
			for (ResolvedType supertype : supertypes()) {
				if (!supertype.isInterface()) {
					return supertype;
				}
			}
			return resolver.resolveBinaryName("java.lang.Object");
		}

		@Override
		public List<ResolvedType> getInterfaces() {
			List<ResolvedType> result = new ArrayList<>();
			for (ResolvedType supertype : supertypes()) {
				if (supertype.isInterface()) {
					result.add(supertype);
				}
			}
			if (isAnnotation()) {
				ResolvedType annotation = resolver.resolveBinaryName("java.lang.annotation.Annotation");
				if (annotation != null) {
					result.add(annotation);
				}
			}
			return result;
		}

		@Override
		public synchronized List<ResolvedAnnotation> getAnnotations() {
			if (annotations == null) {
				List<ResolvedAnnotation> result = new ArrayList<>();
				for (var use : source.declaration().annotations()) {
					ResolvedAnnotation annotation = resolver.resolveAnnotation(use, source.file(), source.outer());
					if (annotation != null) {
						result.add(annotation);
					}
				}
				annotations = result;
			}
			return annotations;
		}
	}

	/** an annotation, which is identified by its type */
	abstract static class BaseAnnotation implements ResolvedAnnotation {

		protected final ResolvedType type;

		BaseAnnotation(ResolvedType type) {
			this.type = type;
		}

		@Override
		public String getName() {
			return type.getName();
		}

		@Override
		public ResolvedType getAnnotationType() {
			return type;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BaseAnnotation other && type.equals(other.type);
		}

		@Override
		public int hashCode() {
			return Objects.hash(type);
		}

		@Override
		public String toString() {
			return "@" + type.getQualifiedName();
		}
	}

	/** an annotation that is written in a Kotlin source file, with the values of its arguments */
	static final class SourceAnnotation extends BaseAnnotation {

		private final KotlinTypeResolver resolver;
		private final AnnotationUse use;
		private final KotlinSyntax.File file;
		private final KotlinSourceClass scope;
		private List<ResolvedMemberValuePair> pairs;

		SourceAnnotation(KotlinTypeResolver resolver, ResolvedType type, AnnotationUse use, KotlinSyntax.File file, KotlinSourceClass scope) {
			super(type);
			this.resolver = resolver;
			this.use = use;
			this.file = file;
			this.scope = scope;
		}

		@Override
		public synchronized List<ResolvedMemberValuePair> getDeclaredMemberValuePairs() {
			if (pairs == null) {
				List<ResolvedMemberValuePair> result = new ArrayList<>();
				boolean first = true;
				for (KotlinSyntax.Argument argument : use.arguments()) {
					// the argument that is passed by position is the one that is called value
					String name = argument.name() != null ? argument.name() : (first ? "value" : null);
					first = false;
					if (name != null) {
						result.add(new KotlinMemberValuePair(name, resolver.bindingValue(argument.value(), file, scope)));
					}
				}
				pairs = result;
			}
			return pairs;
		}
	}

	/** an annotation of a class from the classpath */
	static final class ClasspathAnnotation extends BaseAnnotation {

		private final KotlinTypeResolver resolver;
		private final IAnnotation annotation;

		ClasspathAnnotation(KotlinTypeResolver resolver, ResolvedType type, IAnnotation annotation) {
			super(type);
			this.resolver = resolver;
			this.annotation = annotation;
		}

		@Override
		public List<ResolvedMemberValuePair> getDeclaredMemberValuePairs() {
			List<ResolvedMemberValuePair> result = new ArrayList<>();
			annotation.getMemberValuePairs().forEach(pair -> result.add(new KotlinMemberValuePair(pair.getMemberName(), convert(pair.getValue()))));
			return result;
		}

		private Object convert(Object value) {
			if (value instanceof AnnotationValue annotationValue) {
				return convert(annotationValue.value());
			}
			else if (value instanceof org.jboss.jandex.Type classType) {
				ResolvedType resolved = resolver.resolveBinaryName(classType.name().toString());
				return resolved != null ? resolved : classType.name().toString();
			}
			else if (value instanceof Object[] values) {
				Object[] result = new Object[values.length];
				for (int i = 0; i < values.length; i++) {
					result[i] = convert(values[i]);
				}
				return result;
			}
			else if (value instanceof AnnotationInstance) {
				return null; // nested annotations are not needed
			}
			return value;
		}
	}

	static final class KotlinMemberValuePair implements ResolvedMemberValuePair {

		private final String name;
		private final Object value;

		KotlinMemberValuePair(String name, Object value) {
			this.name = name;
			this.value = value;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Object getValue() {
			return value;
		}

		@Override
		public String toString() {
			return name + "=" + value;
		}
	}

	/** an enum constant, or a constant that is not evaluated */
	static final class KotlinVariable implements ResolvedVariable {

		private final String name;
		private final ResolvedType declaringClass;

		KotlinVariable(String name, ResolvedType declaringClass) {
			this.name = name;
			this.declaringClass = declaringClass;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public ResolvedType getDeclaringClass() {
			return declaringClass;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	static final class KotlinMethod implements ResolvedMethod {

		private final String name;
		private final ResolvedType declaringClass;
		private final List<ResolvedType> parameterTypes;
		private final ResolvedType returnType;

		KotlinMethod(String name, ResolvedType declaringClass, List<ResolvedType> parameterTypes, ResolvedType returnType) {
			this.name = name;
			this.declaringClass = declaringClass;
			this.parameterTypes = parameterTypes;
			this.returnType = returnType;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public ResolvedType getDeclaringClass() {
			return declaringClass;
		}

		@Override
		public List<ResolvedType> getParameterTypes() {
			return parameterTypes;
		}

		@Override
		public ResolvedType getReturnType() {
			return returnType;
		}

		@Override
		public String toString() {
			return declaringClass.getQualifiedName() + "." + name;
		}
	}

	static final class KotlinPackage implements ResolvedPackage {

		private final String name;

		KotlinPackage(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof KotlinPackage other && name.equals(other.name);
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public String toString() {
			return name;
		}
	}

}
