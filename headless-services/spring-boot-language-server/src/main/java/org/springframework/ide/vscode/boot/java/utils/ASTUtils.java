/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.jdt.imports.ImportRewrite;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

public class ASTUtils {

	private static final Logger log = LoggerFactory.getLogger(ASTUtils.class);
	
	public static DocumentRegion nameRegion(TextDocument doc, Annotation annotation) {
		int start = annotation.getTypeName().getStartPosition();
		int end = start + annotation.getTypeName().getLength();
		if (doc.getSafeChar(start - 1) == '@') {
			start--;
		}
		return new DocumentRegion(doc, start, end);
	}

	public static Optional<Range> nameRange(TextDocument doc, Annotation annotation) {
		try {
			return Optional.of(nameRegion(doc, annotation).asRange());
		} catch (Exception e) {
			log.error("", e);
			return Optional.empty();
		}
	}

	public static DocumentRegion stringRegion(TextDocument doc, StringLiteral node) {
		DocumentRegion nodeRegion = nodeRegion(doc, node);
		if (nodeRegion.startsWith("\"")) {
			nodeRegion = nodeRegion.subSequence(1);
		}
		if (nodeRegion.endsWith("\"")) {
			nodeRegion = nodeRegion.subSequence(0, nodeRegion.getLength()-1);
		}
		return nodeRegion;
	}


	public static DocumentRegion nodeRegion(TextDocument doc, ASTNode node) {
		int start = node.getStartPosition();
		int end = start + node.getLength();
		return new DocumentRegion(doc, start, end);
	}

	public static Optional<Expression> getAttribute(Annotation annotation, String name) {
		if (annotation == null) {
			return Optional.empty();
		}
		
		try {

			if (annotation.isSingleMemberAnnotation() && name.equals("value")) {
				SingleMemberAnnotation sma = (SingleMemberAnnotation) annotation;
				return Optional.ofNullable(sma.getValue());
				
			} else if (annotation.isNormalAnnotation()) {
				NormalAnnotation na = (NormalAnnotation) annotation;
				Object attributeObjs = na.getStructuralProperty(NormalAnnotation.VALUES_PROPERTY);
				if (attributeObjs instanceof List) {
					for (Object atrObj : (List<?>)attributeObjs) {
						if (atrObj instanceof MemberValuePair) {
							MemberValuePair mvPair = (MemberValuePair) atrObj;
							if (name.equals(mvPair.getName().getIdentifier())) {
								return Optional.ofNullable(mvPair.getValue());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}

		return Optional.empty();
	}

	/**
	 * For case where a expression can be either a String or a array of Strings and
	 * we are interested in the first element of the array. (I.e. typical case
	 * when annotation attribute is of type String[] (because Java allows using a single
	 * value as a convenient syntax for writing an array of length 1 in that case.
	 */
	public static Optional<String> getFirstString(Expression exp) {
		if (exp instanceof StringLiteral) {
			return Optional.ofNullable(getLiteralValue((StringLiteral) exp));
		} else if (exp instanceof ArrayInitializer) {
			ArrayInitializer array = (ArrayInitializer) exp;
			Object objs = array.getStructuralProperty(ArrayInitializer.EXPRESSIONS_PROPERTY);
			if (objs instanceof List) {
				List<?> list = (List<?>) objs;
				if (!list.isEmpty()) {
					Object firstObj = list.get(0);
					if (firstObj instanceof Expression) {
						return getFirstString((Expression) firstObj);
					}
				}
			}
		}
		return Optional.empty();
	}

	public static TypeDeclaration findDeclaringType(ASTNode node) {
		while (node != null && !(node instanceof TypeDeclaration)) {
			node = node.getParent();
		}

		return node != null ? (TypeDeclaration) node : null;
	}

	public static boolean hasExactlyOneConstructor(TypeDeclaration typeDecl) {
		boolean oneFound = false;
		MethodDeclaration[] methods = typeDecl.getMethods();
		for (MethodDeclaration methodDeclaration : methods) {
			if (methodDeclaration.isConstructor()) {
				if (oneFound) {
					return false;
				} else {
					oneFound = true;
				}
			}
		}
		return oneFound;
	}

	public static MethodDeclaration getAnnotatedMethod(Annotation annotation) {
		ASTNode parent = annotation.getParent();
		if (parent instanceof MethodDeclaration) {
			return (MethodDeclaration)parent;
		}
		return null;
	}

	public static TypeDeclaration getAnnotatedType(Annotation annotation) {
		ASTNode parent = annotation.getParent();
		if (parent instanceof TypeDeclaration) {
			return (TypeDeclaration)parent;
		}
		return null;
	}

	public static String getLiteralValue(StringLiteral node) {
		synchronized (node.getAST()) {
			return node.getLiteralValue();
		}
	}

	public static String getExpressionValueAsString(Expression exp, Consumer<ITypeBinding> dependencies) {
		if (exp instanceof StringLiteral) {
			return getLiteralValue((StringLiteral) exp);
		} else if (exp instanceof Name) {
			
			IBinding binding = ((Name) exp).resolveBinding();
			if (binding != null && binding.getKind() == IBinding.VARIABLE) {

				IVariableBinding varBinding = (IVariableBinding) binding;
				
				ITypeBinding klass = varBinding.getDeclaringClass();
				if (klass != null) {
					dependencies.accept(klass);
				}

				Object constValue = varBinding.getConstantValue();
				if (constValue != null) {
					return constValue.toString();
				}
			}
			if (exp instanceof QualifiedName) {
				return getExpressionValueAsString(((QualifiedName) exp).getName(), dependencies);
			}
			else if (exp instanceof SimpleName) {
				return ((SimpleName) exp).getIdentifier();
			}
		} else {
			Object constValue = exp.resolveConstantExpressionValue();
			if (constValue != null) {
				return constValue.toString();
			}
		}
		
		return null;
	}

	@SuppressWarnings("unchecked")
	public static String[] getExpressionValueAsArray(Expression exp, Consumer<ITypeBinding> dependencies) {
		if (exp instanceof ArrayInitializer) {
			ArrayInitializer array = (ArrayInitializer) exp;
			return ((List<Expression>) array.expressions()).stream().map(e -> getExpressionValueAsString(e, dependencies))
					.filter(Objects::nonNull).toArray(String[]::new);
		} else {
			String rm = getExpressionValueAsString(exp, dependencies);
			if (rm != null) {
				return new String[] { rm };
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static List<StringLiteral> getExpressionValueAsListOfLiterals(Expression exp) {
		if (exp instanceof ArrayInitializer) {
			ArrayInitializer array = (ArrayInitializer) exp;
			return ((List<Expression>) array.expressions()).stream()
					.flatMap(e -> e instanceof StringLiteral
							? Stream.of((StringLiteral)e)
							: Stream.empty()
					)
					.collect(CollectorUtil.toImmutableList());
		} else if (exp instanceof StringLiteral){
			return ImmutableList.of((StringLiteral)exp);
		}
		return ImmutableList.of();
	}

	@SuppressWarnings("unchecked")
	public static List<Expression> expandExpressionsFromPotentialArray(Expression exp) {
		if (exp instanceof ArrayInitializer array) {
			return ((List<Expression>)array.expressions());
		}
		else {
			return List.of(exp);
		}
	}

	public static Collection<Annotation> getAnnotations(AbstractTypeDeclaration abstractTypeDeclaration) {
		if (abstractTypeDeclaration instanceof TypeDeclaration typeDeclaration) {
			return getAnnotations(typeDeclaration);
		}
		else if (abstractTypeDeclaration instanceof RecordDeclaration recordDeclaration) {
			return getAnnotations(recordDeclaration);
		}
		else {
			return null;
		}
	}
	
	public static Collection<Annotation> getAnnotations(TypeDeclaration typeDeclaration) {
		return getAnnotationsFromModifiers(typeDeclaration.getStructuralProperty(TypeDeclaration.MODIFIERS2_PROPERTY));
	}
	
	public static Collection<Annotation> getAnnotations(RecordDeclaration recordDeclaration) {
		return getAnnotationsFromModifiers(recordDeclaration.getStructuralProperty(RecordDeclaration.MODIFIERS2_PROPERTY));
	}
	
	public static Collection<Annotation> getAnnotations(MethodDeclaration methodDeclaration) {
		return getAnnotationsFromModifiers(methodDeclaration.getStructuralProperty(MethodDeclaration.MODIFIERS2_PROPERTY));
	}
	
	public static Collection<Annotation> getAnnotations(FieldDeclaration fieldDeclaration) {
		return getAnnotationsFromModifiers(fieldDeclaration.getStructuralProperty(FieldDeclaration.MODIFIERS2_PROPERTY));
	}
	
	public static Collection<Annotation> getAnnotations(SingleVariableDeclaration variableDeclaration) {
		return getAnnotationsFromModifiers(variableDeclaration.getStructuralProperty(SingleVariableDeclaration.MODIFIERS2_PROPERTY));
	}
	
	private static Collection<Annotation> getAnnotationsFromModifiers(Object modifiersObj) {
		if (modifiersObj instanceof List) {
			ImmutableList.Builder<Annotation> annotations = ImmutableList.builder();
			for (Object node : (List<?>)modifiersObj) {
				if (node instanceof Annotation) {
					annotations.add((Annotation) node);
				}
			}
			return annotations.build();
		}
		return ImmutableList.of();
	}


	public static String getAnnotationType(Annotation annotation) {
		ITypeBinding binding = annotation.resolveTypeBinding();
		if (binding != null) {
			return binding.getQualifiedName();
		}
		return null;
	}

	public static Optional<String> beanId(List<Object> modifiers) {
		return modifiers.stream()
				.filter(m -> m instanceof SingleMemberAnnotation)
				.map(m -> (SingleMemberAnnotation) m)
				.filter(m -> {
					ITypeBinding typeBinding = m.resolveTypeBinding();
					if (typeBinding != null) {
						return Annotations.QUALIFIER.equals(typeBinding.getQualifiedName());
					}
					return false;
				})
				.findFirst()
				.map(a -> a.getValue())
				.filter(e -> e != null)
				.map(e -> e.resolveConstantExpressionValue())
				.filter(o -> o instanceof String)
				.map(o -> (String) o);
	}

	public static Annotation getBeanAnnotation(MethodDeclaration method) {
		return getAnnotation(method, Annotations.BEAN);
	}
	
	public static Annotation getAnnotation(BodyDeclaration method, String annotationType) {
		List<?> modifiers = method.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation = (Annotation) modifier;
				ITypeBinding typeBinding = annotation.resolveTypeBinding();
				if (typeBinding != null) {
					String fqName = typeBinding.getQualifiedName();
					if (annotationType.equals(fqName)) {
						return annotation;
					}
				}
			}
		}
		return null;
	}
	
	public static boolean isAbstractClass(TypeDeclaration typeDeclaration) {
		List<?> modifiers = typeDeclaration.modifiers();
		for (Object object : modifiers) {
			if (object instanceof Modifier) {
				if (((Modifier) object).isAbstract()) {
					return true;
				}
			}
		}

		return false;
	}
	
	public static ITypeBinding findInTypeHierarchy(ITypeBinding resolvedType, Set<String> typesToCheck) {
		for (Iterator<ITypeBinding> itr = getHierarchyTypesBreadthFirstIterator(resolvedType); itr.hasNext();) {
			ITypeBinding b = itr.next();
			String fqn = b.isParameterizedType() ? b.getBinaryName() : b.getQualifiedName();
			if (typesToCheck.contains(fqn)) {
				return b;
			}
		}
		return null;
	}
	
	public static Optional<DocumentEdits> getImportsEdit(CompilationUnit cu, Collection<String> imprts, IDocument doc) {
		ImportRewrite rewrite =  ImportRewrite.create(cu, true);

		for (String imprt : imprts) {
			rewrite.addImport(imprt);
		}

		DocumentEdits edit = rewrite.createEdit(doc);

		return edit != null ?  Optional.of(edit) : Optional.empty();
	}

//	public static Collection<InjectionPoint> getInjectionPointsFromMethodParams(MethodDeclaration method, TextDocument doc) throws BadLocationException {
//		List<InjectionPoint> result = new ArrayList<>();
//		
//		List<?> parameters = method.parameters();
//		for (Object object : parameters) {
//			if (object instanceof VariableDeclaration) {
//				VariableDeclaration variable = (VariableDeclaration) object;
//				String name = variable.getName().toString();
//				
//				IVariableBinding variableBinding = variable.resolveBinding();
//				String type = variableBinding.getType().getQualifiedName();
//				
//				DocumentRegion region = ASTUtils.nodeRegion(doc, variable.getName());
//				Range range = doc.toRange(region);
//				
//				Location location = new Location(doc.getUri(), range);
//				AnnotationMetadata[] annotations = ASTUtils.getAnnotationsMetadata(variableBinding.getAnnotations());
//				
//				result.add(new InjectionPoint(name, type, location, annotations));
//			}
//		}
//		return result;
//	}
//
	/**
	 * Returns only the super types without the type itself.
	 * @param binding
	 * @return
	 */
	public static Set<String> findSupertypes(ITypeBinding binding) {
		Set<String> supertypesCollector = new HashSet<>();
		for (Iterator<String> itr = getHierarchyTypesFqNamesBreadthFirstIterator(binding); itr.hasNext();) {
			supertypesCollector.add(itr.next());
		}
		supertypesCollector.remove(binding.isParameterizedType() ? binding.getBinaryName() : binding.getQualifiedName());
		return supertypesCollector;
	}
	
	public static boolean isAnyTypeInHierarchy(ITypeBinding binding, Collection<String> typeFqns) {
		for (Iterator<String> itr = getHierarchyTypesFqNamesBreadthFirstIterator(binding); itr.hasNext();) {
			String fqn = itr.next();
			if (typeFqns.contains(fqn)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean areAllTypesInHierarchy(ITypeBinding binding, Collection<String> typeFqns) {
		HashSet<String> notFound = new HashSet<>(typeFqns);
		for (Iterator<String> itr = getHierarchyTypesFqNamesBreadthFirstIterator(binding); itr.hasNext() && !notFound.isEmpty();) {
			notFound.remove(itr.next());
		}
		return notFound.isEmpty();
	}
	
	private static void enqueueSuperTypes(Queue<ITypeBinding> q, ITypeBinding t) {
		for (ITypeBinding b : t.getInterfaces()) {
			if (b != null) {
				q.add(b);
			}
		}
		if (t.getSuperclass() != null) {
			q.add(t.getSuperclass());
		}
	}
	
	public static Iterator<ITypeBinding> getHierarchyTypesBreadthFirstIterator(ITypeBinding binding) {
		final Queue<ITypeBinding> q = new ArrayDeque<>(10);
		q.add(binding);
		return new Iterator<ITypeBinding>() {
			
			@Override
			public boolean hasNext() {
				return !q.isEmpty();
			}

			@Override
			public ITypeBinding next() {
				ITypeBinding t = q.poll();
				if (t == null) {
					throw new NoSuchElementException();
				}
				enqueueSuperTypes(q, t);
				return t;
			}
			
		};
	}
	
	public static Iterator<String> getHierarchyTypesFqNamesBreadthFirstIterator(ITypeBinding binding) {
		Iterator<ITypeBinding> itr = getHierarchyTypesBreadthFirstIterator(binding);
		return new Iterator<String>() {

			@Override
			public boolean hasNext() {
				return itr.hasNext();
			}

			@Override
			public String next() {
				ITypeBinding b = itr.next();
				return b.isParameterizedType() ? b.getBinaryName() : b.getQualifiedName();
			}
			
		};
	}
	
	public static InjectionPoint[] findInjectionPoints(MethodDeclaration method, TextDocument doc) throws BadLocationException {
		List<InjectionPoint> result = new ArrayList<>();
		findInjectionPoints(method, doc, result, false);
		
		return result.size() > 0 ? result.toArray(new InjectionPoint[result.size()]) : DefaultValues.EMPTY_INJECTION_POINTS;
	}
	
	public static InjectionPoint[] findInjectionPoints(AbstractTypeDeclaration abstractType, TextDocument doc) throws BadLocationException {
		if (abstractType instanceof TypeDeclaration type) {
			return findInjectionPointsForType(type, doc);
		}
		else {
			return DefaultValues.EMPTY_INJECTION_POINTS;
		}
	}
	
	public static InjectionPoint[] findInjectionPointsForType(TypeDeclaration type, TextDocument doc) throws BadLocationException {
		List<InjectionPoint> result = new ArrayList<>();

		findInjectionPoints(type.getMethods(), doc, result);
		findInjectionPoints(type.getFields(), doc, result);
		
		return result.size() > 0 ? result.toArray(new InjectionPoint[result.size()]) : DefaultValues.EMPTY_INJECTION_POINTS;
	}

	private static void findInjectionPoints(MethodDeclaration[] methods, TextDocument doc, List<InjectionPoint> result) throws BadLocationException {
		int constructorCount = 0;

		// special rule that if there is a single constructor, it doesn't have to have an autowired or inject annotation on it
		MethodDeclaration singleConstructor = null;

		for (MethodDeclaration method : methods) {
			if (method.isConstructor()) {
				constructorCount++;
				singleConstructor = method;
			}
		}
		
		if (constructorCount == 1) {
			findInjectionPoints(singleConstructor, doc, result, false);
		}

		// look for all methods with annotations (whether constructors or regular methods)
		for (MethodDeclaration method : methods) {
			findInjectionPoints(method, doc, result, true);
		}
	}
	
	public static void findInjectionPoints(MethodDeclaration method, TextDocument doc, List<InjectionPoint> result, boolean checkForAnnotation) throws BadLocationException {

		Collection<Annotation> annotationsOnMethod = getAnnotations(method);
		
		if (checkForAnnotation) {
			boolean isAutowired = false;
			
			for (Annotation annotation : annotationsOnMethod) {
				String qualifiedName = annotation.resolveTypeBinding().getQualifiedName();
				if (Annotations.AUTOWIRED.equals(qualifiedName)
						|| Annotations.INJECT_JAVAX.equals(qualifiedName)
						|| Annotations.INJECT_JAKARTA.equals(qualifiedName)) {
					isAutowired = true;
				}
			}

			if (!isAutowired) {
				return;
			}
		}
		
		List<?> parameters = method.parameters();
		for (Object object : parameters) {
			if (object instanceof SingleVariableDeclaration) {
				SingleVariableDeclaration variable = (SingleVariableDeclaration) object;
				String name = variable.getName().toString();

				IVariableBinding variableBinding = variable.resolveBinding();
				String type = variableBinding.getType().getQualifiedName();
				
				DocumentRegion region = ASTUtils.nodeRegion(doc, variable.getName());
				Range range = doc.toRange(region);
				
				Location location = new Location(doc.getUri(), range);
				
				List<Annotation> allAnnotations = new ArrayList<>();
				
				// add method level annotations to each injection point only for autowired setter injection
				// (not for bean methods)
				if (checkForAnnotation) {
					allAnnotations.addAll(annotationsOnMethod);
				}
				
				allAnnotations.addAll(getAnnotations(variable));
				
				AnnotationMetadata[] annotations = getAnnotationsMetadata(allAnnotations, doc);
				
				result.add(new InjectionPoint(name, type, location, annotations));
			}
		}
	}

	private static void findInjectionPoints(FieldDeclaration[] fields, TextDocument doc, List<InjectionPoint> result) throws BadLocationException {
		for (FieldDeclaration field : fields) {
			findInjectionPoints(field, doc, result);
		}
	}

	private static void findInjectionPoints(FieldDeclaration field, TextDocument doc, List<InjectionPoint> result) throws BadLocationException {
		boolean autowiredField = false;
		
		List<Annotation> fieldAnnotations = new ArrayList<>();

		List<?> modifiers = field.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation = (Annotation) modifier;
				fieldAnnotations.add(annotation);

				String qualifiedName = annotation.resolveTypeBinding().getQualifiedName();
				if (Annotations.AUTOWIRED.equals(qualifiedName)
						|| Annotations.INJECT_JAVAX.equals(qualifiedName)
						|| Annotations.INJECT_JAKARTA.equals(qualifiedName)
						|| Annotations.VALUE.equals(qualifiedName)) {
					autowiredField = true;
				}
			}
		}

		if (!autowiredField) {
			return;
		}
		
		List<?> fragments = field.fragments();
		for (Object fragment : fragments) {
			if (fragment instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment varFragment = (VariableDeclarationFragment) fragment;
				String fieldName = varFragment.getName().toString();

				DocumentRegion region = ASTUtils.nodeRegion(doc, varFragment.getName());
				Range range = doc.toRange(region);
				Location fieldLocation = new Location(doc.getUri(), range);

				String fieldType = field.getType().resolveBinding().getQualifiedName();

				AnnotationMetadata[] annotationsMetadata = getAnnotationsMetadata(fieldAnnotations, doc);

				result.add(new InjectionPoint(fieldName, fieldType, fieldLocation, annotationsMetadata));
			}
		}
	}
	
	public static AnnotationMetadata[] getAnnotationsMetadata(Collection<Annotation> annotations, TextDocument doc) {
		return annotations.stream()
				.map(annotation -> createAnnotationMetadataFrom(annotation, doc))
				.toArray(AnnotationMetadata[]::new);
	}
	
	private static AnnotationMetadata createAnnotationMetadataFrom(Annotation annotation, TextDocument doc) {
		try {
			DocumentRegion region = ASTUtils.nodeRegion(doc, annotation);
			Range range = doc.toRange(region);
			Location location = new Location(doc.getUri(), range);

			IAnnotationBinding binding = annotation.resolveAnnotationBinding();
			return new AnnotationMetadata(binding.getAnnotationType().getQualifiedName(), false, location, getAttributes(annotation, doc));
		}
		catch (BadLocationException e) {
			log.error("problem when identifying location of annotation", e);
		}
		
		return null;
	}
	
//	public static Map<String, String[]> getAttributes(IAnnotationBinding t) {
//		Map<String, String[]> result = new LinkedHashMap<>();
//
//		IMemberValuePairBinding[] pairs = t.getDeclaredMemberValuePairs();
//		for (IMemberValuePairBinding pair : pairs) {
//			MemberValuePairAndType values = ASTUtils.getValuesFromValuePair(pair);
//			if (values != null) {
//				result.put(pair.getName(), values.values);
//			}
//		}
//		
//		return result;
//	}
	
	public static Map<String, AnnotationAttributeValue[]> getAttributes(Annotation annotation, TextDocument doc) throws BadLocationException {
		Map<String, AnnotationAttributeValue[]> result = new LinkedHashMap<>();
/*
		IAnnotationBinding t = annotation.resolveAnnotationBinding();
		IMemberValuePairBinding[] pairs = t.getDeclaredMemberValuePairs();
		for (IMemberValuePairBinding pair : pairs) {
			MemberValuePairAndType values = ASTUtils.getValuesFromValuePair(pair);
			if (values != null) {
				AnnotationAttributeValue[] attributeValue = Arrays.stream(values.values)
					.map(value -> new AnnotationAttributeValue(value, null))
					.toArray(AnnotationAttributeValue[]::new);
				
				result.put(pair.getName(), attributeValue);
			}
		}
*/
		
		if (annotation.isSingleMemberAnnotation()) {
			Expression value = ((SingleMemberAnnotation) annotation).getValue();
			AnnotationAttributeValue[] attributeValues = getAnnotationAttributeValues(value, doc);
			result.put("value", attributeValues);

		} else if (annotation.isNormalAnnotation()) {
			List<?> attributes = ((NormalAnnotation) annotation).values();
			for (Object attribute : attributes) {
				MemberValuePair pair = (MemberValuePair) attribute;
				
				SimpleName attributeName = pair.getName();
				Expression attributeValue = pair.getValue();
				
				AnnotationAttributeValue[] attributeValues = getAnnotationAttributeValues(attributeValue, doc);
				result.put(attributeName.toString(), attributeValues);
			}
		}

		return result;
	}
	
	private static AnnotationAttributeValue[] getAnnotationAttributeValues(Expression expression, TextDocument doc) throws BadLocationException {
		if (expression instanceof ArrayInitializer) {
			ArrayInitializer arrayInitializer = (ArrayInitializer) expression;
			List<?> expressions = arrayInitializer.expressions();
			
			AnnotationAttributeValue[] result = new AnnotationAttributeValue[expressions.size()];
			for (int i = 0; i < result.length; i++) {
				Expression ex = (Expression) expressions.get(i);
				result[i] = convertExpressionInto(ex, doc);
			}
			
			return result;
		}
		else {
			return new AnnotationAttributeValue[] {convertExpressionInto(expression, doc)};
		}
		
//		Object constantExpressionValue = attributeValue.resolveConstantExpressionValue();
	}
	
	private static AnnotationAttributeValue convertExpressionInto(Expression expression, TextDocument doc) throws BadLocationException {
		if (expression instanceof StringLiteral) {
			StringLiteral stringLiteral = (StringLiteral) expression;
			String literalValue = ASTUtils.getLiteralValue(stringLiteral);
			
			DocumentRegion region = ASTUtils.nodeRegion(doc, stringLiteral);
			Range range = doc.toRange(region);
			Location location = new Location(doc.getUri(), range);
			
			return new AnnotationAttributeValue(literalValue, location);
		}
		else if (expression instanceof TypeLiteral) {
			TypeLiteral type = (TypeLiteral) expression;
			
			DocumentRegion region = ASTUtils.nodeRegion(doc, type);
			Range range = doc.toRange(region);
			Location location = new Location(doc.getUri(), range);

			return new AnnotationAttributeValue(type.getType().resolveBinding().getQualifiedName(), location);
		}
		else {
			DocumentRegion region = ASTUtils.nodeRegion(doc, expression);
			Range range = doc.toRange(region);
			Location location = new Location(doc.getUri(), range);
			
			Object constantExpressionValue = expression.resolveConstantExpressionValue();
			
			return new AnnotationAttributeValue(constantExpressionValue != null ? constantExpressionValue.toString() : expression.toString(), location);
		}
	}
	
	public static ASTNode getNearestAnnotationParent(ASTNode node) {
		while (node != null && !(node instanceof Annotation)) {
			node = node.getParent();
		}
		return node;
	}
	
	public static MemberValuePairAndType getValuesFromValuePair(IMemberValuePairBinding valuePair) {
		if (valuePair != null) {
			Object value = valuePair.getValue();
			
			MemberValuePairAndType result = new MemberValuePairAndType();

			if (value instanceof Object[]) {
				Object[] values = (Object[]) value;
				result.values = new String[values.length];
				for (int k = 0; k < values.length; k++) {
					
					Object v = values[k];
					if (v instanceof IVariableBinding) {
						IVariableBinding varBinding = (IVariableBinding) v;
						result.values[k] = varBinding.getName();
						
						ITypeBinding klass = varBinding.getDeclaringClass();
						if (klass != null) {
							result.dereferencedType = klass;
						}

					}
					else if (v instanceof String) {
						result.values[k] = (String) v;
					}
					else if (v instanceof ITypeBinding) {
						result.values[k] = ((ITypeBinding) v).getQualifiedName();
					}
					else if (v != null) {
						result.values[k] = v.toString();
					}
				}
				return result;
			}
			else if (value instanceof String[]) {
				result.values = (String[]) value;
				return result;
			}
			else if (value != null) {
				result.values = new String[] {value.toString()};
				return result;
			}
		}
		return null;
	}
	
	public static class MemberValuePairAndType {
		public String[] values;
		public ITypeBinding dereferencedType;
	}

}
