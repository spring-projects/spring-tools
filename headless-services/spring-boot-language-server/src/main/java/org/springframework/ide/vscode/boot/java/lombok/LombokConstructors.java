/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.lombok;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;

/**
 * The constructors Lombok generates for a class, derived from the class's source as it is - so
 * that injection points of a {@code @RequiredArgsConstructor} component, which only exist in the
 * compiled class, are known when indexing the source.
 *
 * <p>Follows Lombok's rules for:
 * <ul>
 * <li>{@code @NoArgsConstructor}, {@code @RequiredArgsConstructor}, {@code @AllArgsConstructor} -
 * always generated, whatever else the class has</li>
 * <li>{@code @Data} (required args) and {@code @Value} (all args) - only if the class has neither an
 * explicit constructor nor one of the three annotations above, nor {@code @Builder}</li>
 * <li>{@code @Builder} on the class (all args) - only if the class has neither an explicit
 * constructor nor one of the three annotations above</li>
 * <li>which fields are final: declared so, made so by {@code @Value} or by
 * {@code @FieldDefaults(makeFinal = true)} or by {@link LombokSettings#fieldDefaultsFinal()} - unless
 * marked {@code @NonFinal}</li>
 * <li>{@code onConstructor} / {@code onConstructor_}, e.g. {@code @__(@Autowired)} - the annotations
 * the generated constructor carries</li>
 * <li>which field annotations are copied onto the constructor parameter: the nullability
 * annotations Lombok copies anyway, and Spring's {@code @Qualifier} and {@code @Value} with
 * {@link LombokSettings#copySpringAnnotations()}</li>
 * </ul>
 *
 * <p>Lombok annotations are recognized by their resolved type and, should Lombok not be on the
 * project's classpath, by their name and the imports of the compilation unit.
 *
 * @author Martin Lippert
 */
public class LombokConstructors {

	public static final String NO_ARGS_CONSTRUCTOR = "lombok.NoArgsConstructor";
	public static final String REQUIRED_ARGS_CONSTRUCTOR = "lombok.RequiredArgsConstructor";
	public static final String ALL_ARGS_CONSTRUCTOR = "lombok.AllArgsConstructor";
	public static final String DATA = "lombok.Data";
	public static final String VALUE = "lombok.Value";
	public static final String BUILDER = "lombok.Builder";
	public static final String FIELD_DEFAULTS = "lombok.experimental.FieldDefaults";
	public static final String NON_FINAL = "lombok.experimental.NonFinal";

	private static final Set<String> SPRING_COPYABLE_ANNOTATIONS = Set.of(
			"org.springframework.beans.factory.annotation.Qualifier",
			"org.springframework.beans.factory.annotation.Value");

	/**
	 * Lombok annotations by their simple name, for resolving them through imports when their type
	 * cannot be resolved.
	 */
	private static final Map<String, String> BY_SIMPLE_NAME = Map.of(
			"NoArgsConstructor", NO_ARGS_CONSTRUCTOR,
			"RequiredArgsConstructor", REQUIRED_ARGS_CONSTRUCTOR,
			"AllArgsConstructor", ALL_ARGS_CONSTRUCTOR,
			"Data", DATA,
			"Value", VALUE,
			"Builder", BUILDER,
			"FieldDefaults", FIELD_DEFAULTS,
			"NonFinal", NON_FINAL);

	/**
	 * A constructor Lombok generates.
	 *
	 * @param origin the fully qualified name of the Lombok annotation that generates it
	 * @param declaration that annotation in source - the closest there is to a declaration of the
	 *        constructor
	 * @param parameters the constructor's parameters, in the order Lombok generates them
	 * @param annotations the annotations the constructor carries, from {@code onConstructor}
	 */
	public record GeneratedConstructor(String origin, Annotation declaration, List<GeneratedParameter> parameters, List<Annotation> annotations) {
	}

	/**
	 * A parameter of a generated constructor, for one of the fields of the class.
	 *
	 * @param field the field the parameter is generated for
	 * @param annotations the annotations of the field that Lombok copies onto the parameter
	 */
	public record GeneratedParameter(VariableDeclarationFragment field, List<Annotation> annotations) {

		public String getName() {
			return field.getName().getIdentifier();
		}
	}

	/**
	 * @return the constructors Lombok generates for the class, empty if it generates none
	 */
	public static List<GeneratedConstructor> of(TypeDeclaration type, LombokSettings settings) {
		if (type.isInterface()) {
			return List.of();
		}

		Collection<Annotation> typeAnnotations = ASTUtils.getAnnotations(type);

		Annotation noArgs = null, requiredArgs = null, allArgs = null, data = null, value = null, builder = null, fieldDefaults = null;
		for (Annotation annotation : typeAnnotations) {
			String name = lombokAnnotationName(annotation);
			if (name == null) {
				continue;
			}
			switch (name) {
			case NO_ARGS_CONSTRUCTOR -> noArgs = annotation;
			case REQUIRED_ARGS_CONSTRUCTOR -> requiredArgs = annotation;
			case ALL_ARGS_CONSTRUCTOR -> allArgs = annotation;
			case DATA -> data = annotation;
			case VALUE -> value = annotation;
			case BUILDER -> builder = annotation;
			case FIELD_DEFAULTS -> fieldDefaults = annotation;
			default -> {}
			}
		}

		if (noArgs == null && requiredArgs == null && allArgs == null && data == null && value == null && builder == null) {
			return List.of();
		}

		boolean makeFinal = value != null || isMakeFinal(fieldDefaults) || settings.fieldDefaultsFinal();

		List<Field> fields = fieldsOf(type, makeFinal);

		boolean explicitConstructorAnnotation = noArgs != null || requiredArgs != null || allArgs != null;
		boolean explicitConstructor = false;
		for (MethodDeclaration method : type.getMethods()) {
			if (method.isConstructor()) {
				explicitConstructor = true;
			}
		}

		List<GeneratedConstructor> result = new ArrayList<>();

		if (noArgs != null) {
			result.add(new GeneratedConstructor(NO_ARGS_CONSTRUCTOR, noArgs, List.of(), onConstructor(noArgs)));
		}
		if (requiredArgs != null) {
			result.add(new GeneratedConstructor(REQUIRED_ARGS_CONSTRUCTOR, requiredArgs, parameters(requiredFields(fields), settings), onConstructor(requiredArgs)));
		}
		if (allArgs != null) {
			result.add(new GeneratedConstructor(ALL_ARGS_CONSTRUCTOR, allArgs, parameters(allArgsFields(fields), settings), onConstructor(allArgs)));
		}

		if (!explicitConstructorAnnotation && !explicitConstructor) {
			if (builder != null) {
				result.add(new GeneratedConstructor(BUILDER, builder, parameters(allArgsFields(fields), settings), List.of()));
			}
			else if (value != null) {
				result.add(new GeneratedConstructor(VALUE, value, parameters(allArgsFields(fields), settings), List.of()));
			}
			else if (data != null) {
				result.add(new GeneratedConstructor(DATA, data, parameters(requiredFields(fields), settings), List.of()));
			}
		}

		return result;
	}

	private record Field(FieldDeclaration declaration, VariableDeclarationFragment fragment, boolean isFinal, boolean isNonNull) {

		boolean isInitialized() {
			return fragment.getInitializer() != null;
		}
	}

	private static List<Field> fieldsOf(TypeDeclaration type, boolean makeFinal) {
		List<Field> result = new ArrayList<>();

		for (FieldDeclaration field : type.getFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers)) {
				continue;
			}

			Collection<Annotation> annotations = ASTUtils.getAnnotations(field);
			boolean nonFinal = annotations.stream().anyMatch(annotation -> NON_FINAL.equals(lombokAnnotationName(annotation)));
			boolean isFinal = Modifier.isFinal(modifiers) || (makeFinal && !nonFinal);
			boolean isNonNull = annotations.stream().anyMatch(annotation -> "nonnull".equalsIgnoreCase(simpleName(annotation)));

			for (Object fragment : field.fragments()) {
				if (fragment instanceof VariableDeclarationFragment variable && !variable.getName().getIdentifier().startsWith("$")) {
					result.add(new Field(field, variable, isFinal, isNonNull));
				}
			}
		}

		return result;
	}

	/**
	 * {@code @RequiredArgsConstructor}: the final fields, and those marked non-null - unless they
	 * are initialized where they are declared.
	 */
	private static List<Field> requiredFields(List<Field> fields) {
		return fields.stream()
				.filter(field -> (field.isFinal() || field.isNonNull()) && !field.isInitialized())
				.toList();
	}

	/**
	 * {@code @AllArgsConstructor}: every field, except for final fields that are initialized where
	 * they are declared.
	 */
	private static List<Field> allArgsFields(List<Field> fields) {
		return fields.stream()
				.filter(field -> !(field.isFinal() && field.isInitialized()))
				.toList();
	}

	private static List<GeneratedParameter> parameters(List<Field> fields, LombokSettings settings) {
		List<GeneratedParameter> result = new ArrayList<>(fields.size());
		for (Field field : fields) {
			List<Annotation> copied = ASTUtils.getAnnotations(field.declaration()).stream()
					.filter(annotation -> isCopied(annotation, settings))
					.toList();
			result.add(new GeneratedParameter(field.fragment(), copied));
		}
		return result;
	}

	/**
	 * Lombok copies a long list of nullability annotations onto generated parameters by default -
	 * approximated here by their simple name - and on top of those Spring's {@code @Qualifier} and
	 * {@code @Value}, if configured to.
	 */
	private static boolean isCopied(Annotation annotation, LombokSettings settings) {
		String simpleName = simpleName(annotation);
		if ("nonnull".equalsIgnoreCase(simpleName) || "nullable".equalsIgnoreCase(simpleName)) {
			return true;
		}

		if (!settings.copySpringAnnotations()) {
			return false;
		}

		ITypeBinding binding = annotation.resolveTypeBinding();
		String qualifiedName = binding != null ? binding.getQualifiedName() : annotation.getTypeName().getFullyQualifiedName();
		return SPRING_COPYABLE_ANNOTATIONS.contains(qualifiedName);
	}

	private static boolean isMakeFinal(Annotation fieldDefaults) {
		if (fieldDefaults instanceof NormalAnnotation normal) {
			for (Object value : normal.values()) {
				if (value instanceof MemberValuePair pair && "makeFinal".equals(pair.getName().getIdentifier())) {
					return "true".equals(pair.getValue().toString());
				}
			}
		}
		return false;
	}

	/**
	 * The annotations of {@code onConstructor = @__({@Autowired})}, or of the
	 * {@code onConstructor_ = @Autowired} form - without the {@code @__} wrapper.
	 */
	private static List<Annotation> onConstructor(Annotation constructorAnnotation) {
		if (!(constructorAnnotation instanceof NormalAnnotation normal)) {
			return List.of();
		}

		List<Annotation> result = new ArrayList<>();

		for (Object value : normal.values()) {
			if (value instanceof MemberValuePair pair) {
				String name = pair.getName().getIdentifier();
				if ("onConstructor".equals(name) || "onConstructor_".equals(name)) {
					collectAnnotations(pair.getValue(), result);
				}
			}
		}

		return result;
	}

	private static void collectAnnotations(Expression expression, List<Annotation> result) {
		expression.accept(new ASTVisitor() {

			@Override
			public boolean visit(MarkerAnnotation node) {
				return visitAnnotation(node);
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				return visitAnnotation(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				return visitAnnotation(node);
			}

			private boolean visitAnnotation(Annotation annotation) {
				if ("__".equals(simpleName(annotation))) {
					return true; // the wrapper of the older syntax, the annotations are inside of it
				}
				result.add(annotation);
				return false;
			}
		});
	}

	private static String simpleName(Annotation annotation) {
		String name = annotation.getTypeName().getFullyQualifiedName();
		return name.substring(name.lastIndexOf('.') + 1);
	}

	/**
	 * The fully qualified name of the given annotation if it is one of the Lombok annotations
	 * handled here, null otherwise.
	 */
	static String lombokAnnotationName(Annotation annotation) {
		ITypeBinding binding = annotation.resolveTypeBinding();
		if (binding != null && !binding.isRecovered()) {
			String qualifiedName = binding.getQualifiedName();
			return qualifiedName.startsWith("lombok.") ? qualifiedName : null;
		}

		// Lombok is not on the classpath - recognize the annotation by its name and the imports
		String name = annotation.getTypeName().getFullyQualifiedName();
		if (name.startsWith("lombok.")) {
			return BY_SIMPLE_NAME.containsValue(name) ? name : null;
		}

		String candidate = BY_SIMPLE_NAME.get(name);
		if (candidate == null) {
			return null;
		}

		ASTNode root = annotation.getRoot();
		if (root instanceof CompilationUnit cu) {
			String candidatePackage = candidate.substring(0, candidate.lastIndexOf('.'));
			for (Object element : cu.imports()) {
				if (element instanceof ImportDeclaration importDeclaration && !importDeclaration.isStatic()) {
					String imported = importDeclaration.getName().getFullyQualifiedName();
					if (importDeclaration.isOnDemand() ? imported.equals(candidatePackage) : imported.equals(candidate)) {
						return candidate;
					}
				}
			}
		}

		return null;
	}

}
