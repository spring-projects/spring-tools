/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.JavaUtils;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class EntityIdForRepoReconciler implements JdtAstReconciler {
	
	private static final List<String> NUMBER_CLASS_NAMES = List.of(
			Integer.class.getName(),
			Long.class.getName(),
			Short.class.getName(),
			Float.class.getName(),
			Double.class.getName(),
			Byte.class.getName()
	);
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.DOMAIN_ID_FOR_REPOSITORY;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return new ASTVisitor() {

			final boolean considerIdField = project.getClasspath().findBinaryLibraryByPrefix("spring-data-mongodb-").isPresent();

			@Override
			public boolean visit(TypeDeclaration typeDecl) {
				IAnnotationBinding repoDefAnnotationType = null;
				Annotation repoDefAnnotation = null;
				for (Annotation a : ASTUtils.getAnnotations(typeDecl)) {
					if (annotationHierarchies.isAnnotatedWith(a.resolveAnnotationBinding(), Annotations.NO_REPO_BEAN)) {
						return true;
					}
					IAnnotationBinding annotationBinding = a.resolveAnnotationBinding();
					if (repoDefAnnotationType == null && annotationHierarchies.isAnnotatedWith(annotationBinding, Annotations.REPOSITORY_DEFINITION)) {
						String bindingKey = JavaUtils.typeFqNameToBindingKey(Annotations.REPOSITORY_DEFINITION);
						for (Iterator<IAnnotationBinding> itr = annotationHierarchies.iterator(annotationBinding); itr.hasNext() && repoDefAnnotationType == null;) {
							IAnnotationBinding ab = itr.next();
							if (bindingKey.equals(ab.getAnnotationType().getKey())) {
								repoDefAnnotationType = ab;
							}
						}
						if (repoDefAnnotationType != null) {
							repoDefAnnotation = a;
						}
					}

				}
				if (repoDefAnnotationType != null) {
					// NOTE: repoDefAnnotation binding is NOT NECCESSARILY repoDefAnnotationType as
					// repoDefAnnotationType may come from the exact RepositoryDefinition annotation
					// in the meta inheritance
					handleRepoDef(typeDecl, repoDefAnnotation, repoDefAnnotationType);
				} else {
					handleRepoType(typeDecl);
				}
				return super.visit(typeDecl);
			}

			private void handleRepoDef(TypeDeclaration typeDecl, Annotation annotationOverClass,
					IAnnotationBinding repoAnnotationType) {
				ITypeBinding domainClass = getTypeFromAnnotationParameter(repoAnnotationType, "domainClass");
				if (domainClass != null) {
					List <ITypeBinding> idTypes = findIdType(domainClass);
					// Only support single id type. If more than one is present that is a composite key for which we don't mark anything yet
					if (idTypes.size() == 1) {
						ITypeBinding idType = idTypes.get(0);
						ITypeBinding repoIdType = getTypeFromAnnotationParameter(repoAnnotationType, "idClass");
						if (repoIdType != null && !isValidRepoIdType(repoIdType, idType)) {
							ASTUtils.getAttribute(annotationOverClass, "idClass").ifPresentOrElse(
									n -> markProblem(idType, n), () -> markProblem(repoIdType, annotationOverClass));
						}
					}
				}
			}

			private void markProblem(ITypeBinding idType, ASTNode node) {
				context.getProblemCollector().accept(new ReconcileProblemImpl(getProblemType(),
						"Expected Domain ID type is '" + idType.getQualifiedName() + "'", node.getStartPosition(),
						node.getLength()));
			}

			private void handleRepoType(TypeDeclaration typeDecl) {
				ITypeBinding type = typeDecl.resolveBinding();
				if (type == null) {
					return;
				}
				List<ITypeBinding> repoTypeChain = findRepoTypeChain(type, new ArrayList<>());
				// Check if inherits from Spring Data Repository
				if (repoTypeChain != null && !repoTypeChain.isEmpty()) {
					ITypeBinding domainType = null;
					ITypeBinding idType = null;
					int domainTypeIndex = 0;
					int idTypeIndex = 1;
					for (int i = repoTypeChain.size() - 1; i >= 0 && (idType == null || idType.isTypeVariable()
							|| domainType == null || domainType.isTypeVariable()); i--) {
						ITypeBinding repoType = repoTypeChain.get(i);
						ITypeBinding[] typeParams = repoType.isParameterizedType() ? repoType.getTypeArguments()
								: repoType.getTypeParameters();
						if (repoType.isGenericType() || repoType.isParameterizedType()) {
							if (domainType == null || domainType.isTypeVariable()) {
								int idx = domainType == null ? -1
										: findTypeVarIndex(repoType.getTypeParameters(), domainType.getName());
								if (idx < 0) {
									domainType = typeParams[domainTypeIndex];
								} else {
									domainTypeIndex = idx;
									domainType = typeParams[domainTypeIndex];
								}
							}
							if (idType == null || idType.isTypeVariable()) {
								int idx = idType == null ? -1
										: findTypeVarIndex(repoType.getTypeParameters(), idType.getName());
								if (idx < 0) {
									idType = typeParams[idTypeIndex];
								} else {
									idTypeIndex = idx;
									idType = typeParams[idTypeIndex];
								}
							}
						} else {
							if (idType == null || idType.isTypeVariable()) {
								idType = typeParams[idTypeIndex];
							}
							if (domainType == null || domainType.isTypeVariable()) {
								domainType = typeParams[domainTypeIndex];
							}
						}

					}

					ITypeBinding domainClassType = domainType;
					if (domainType.isWildcardType()) {
						domainClassType = domainType.getBound();
					} else if (domainType.isTypeVariable() || domainType.isCapture()) {
						ITypeBinding[] bounds = domainType.getTypeBounds();
						if (bounds.length > 0) {
							domainClassType = bounds[0];
						}
					}
					if (domainClassType != null) {
						List<ITypeBinding> domainIdTypes = findIdType(domainClassType);
						// Only support single id type. If more than one is present that is a composite key for which we don't mark anything yet
						if (domainIdTypes.size() == 1 && !isValidRepoIdType(idType, domainIdTypes.get(0))) {
							ITypeBinding domainIdType = domainIdTypes.get(0);
							if (isNoNewTypeParamsAdded(repoTypeChain)) {
								List<ASTNode> matchedParams = findParamTypes(typeDecl.typeParameters(), idType);
								if (matchedParams.size() > 1) {
									markProblem(domainIdType, typeDecl.getName());
								} else if (matchedParams.size() == 1) {
									markProblem(domainIdType, matchedParams.get(0));
								} else {
									for (Object o : typeDecl.superInterfaceTypes()) {
										if (o instanceof Type) {
											Type interfaceTypeAst = (Type) o;
											ITypeBinding interfaceType = interfaceTypeAst.resolveBinding();
											if (repoTypeChain.get(1).isEqualTo(interfaceType)) {
												if (interfaceTypeAst.isParameterizedType()) {
													matchedParams = findParamTypes(
															((ParameterizedType) interfaceTypeAst).typeArguments(),
															idType);
													if (matchedParams.size() == 1) {
														markProblem(domainIdType, matchedParams.get(0));
													} else {
														markProblem(domainIdType, interfaceTypeAst);
													}
												} else {
													markProblem(domainIdType, interfaceTypeAst);
												}
												return;
											}
										}
									}
								}
							} else {
								markProblem(domainIdType, typeDecl.getName());
							}
						}
					}
				}
			}

			private List<ITypeBinding> findIdType(ITypeBinding type) {
				List<ITypeBinding> idTypes = findAnnotatedIdTypes(annotationHierarchies, type, new HashSet<>());
				if (idTypes.isEmpty() && considerIdField) {
					ITypeBinding idType = findIdFieldType(type);
					if (idType != null) {
						idTypes.add(idType);
					}
				}
				return idTypes;
			}

		};
		
	}

	private static boolean isValidRepoIdType(ITypeBinding repoIdType, ITypeBinding idType) {
		if (NUMBER_CLASS_NAMES.contains(repoIdType.getQualifiedName()) && NUMBER_CLASS_NAMES.contains(idType.getQualifiedName())) {
			return true;
		}
		return repoIdType.isCastCompatible(idType) || idType.isCastCompatible(repoIdType);
	}

	private static ITypeBinding getTypeFromAnnotationParameter(IAnnotationBinding a, String param) {
		for (IMemberValuePairBinding pair : a.getDeclaredMemberValuePairs()) {
			if (pair.getName().equals(param)) {
				if (pair.getValue() instanceof ITypeBinding) {
					return (ITypeBinding) pair.getValue();
				} else if (pair.getValue() instanceof Object[]) {
					Object[] arr = (Object[]) pair.getValue();
					if (arr.length > 0 && arr[0] instanceof ITypeBinding) {
						return (ITypeBinding) arr[0];
					}
				}
			}
		}
		return null;
	}

//	public static void findSuperTypeBindings(ITypeBinding binding, Set<ITypeBinding> superTypes) {
//		// superclasses
//		ITypeBinding superclass = binding.getSuperclass();
//		if (superclass != null) {
//			superTypes.add(superclass);
//			findSuperTypeBindings(superclass, superTypes);
//		}
//		// interfaces
//		for (ITypeBinding i : binding.getInterfaces()) {
//			superTypes.add(i);
//			findSuperTypeBindings(i, superTypes);
//		}
//	}
//
	private static @Nullable List<ITypeBinding> findRepoTypeChain(ITypeBinding type, List<ITypeBinding> visited) {
		if (visited.stream().anyMatch(b -> b.isEqualTo(type))) {
			return null;
		}
		ArrayList<ITypeBinding> ls = new ArrayList<>(visited.size() + 1);
		ls.addAll(visited);
		ls.add(type);
		if (type.isParameterizedType()
				&& "org.springframework.data.repository.Repository".equals(type.getErasure().getQualifiedName())) {
			return ls;
		}

		if (type.getSuperclass() != null) {
			List<ITypeBinding> superChain = findRepoTypeChain(type.getSuperclass(), ls);
			if (superChain != null) {
				return superChain;
			}
		}

		for (ITypeBinding it : type.getInterfaces()) {
			List<ITypeBinding> superChain = findRepoTypeChain(it, ls);
			if (superChain != null) {
				return superChain;
			}
		}

		return null;
	}

	private static ITypeBinding findIdFieldType(ITypeBinding type) {
		for (IVariableBinding f : type.getDeclaredFields()) {
			if ("id".equals(f.getName())) {
				return f.getType();
			}
		}
		for (IMethodBinding m : type.getDeclaredMethods()) {
			if ("id".equals(m.getName()) && m.getTypeArguments().length == 0
					&& !(m.getReturnType().isPrimitive() && "void".equals(m.getReturnType().getQualifiedName()))) {
				return m.getReturnType();
			}
		}
		if (type.getSuperclass() != null) {
			return findIdFieldType(type.getSuperclass());
		}
		return null;
	}

	private static List<ITypeBinding> findAnnotatedIdTypes(AnnotationHierarchies annotationHierarchies, ITypeBinding type, Set<String> visited) {
		List<ITypeBinding> idTypes = new ArrayList<>();
		for (IAnnotationBinding a : type.getAnnotations()) {
			switch (a.getAnnotationType().getQualifiedName()) {
			case Annotations.JPA_JAKARTA_ID_CLASS:
			case Annotations.JPA_JAVAX_ID_CLASS:
				Optional<Object> opt = Arrays.stream(a.getAllMemberValuePairs()).filter(p -> "value".equals(p.getName())).map(p -> p.getValue()).findFirst();
				if (opt.isPresent() && opt.get() instanceof ITypeBinding) {
					return List.of((ITypeBinding) opt.get());
				}
			}
		}
		List<String> idAnnotations = List.of(Annotations.SPRING_ENTITY_ID, Annotations.JPA_JAKARTA_ENTITY_ID, Annotations.JPA_JAVAX_ENTITY_ID, Annotations.JPA_JAKARTA_EMBEDDED_ID, Annotations.JPA_JAVAX_EMBEDDED_ID);
		for (IVariableBinding m : type.getDeclaredFields()) {
			String s = fieldSignature(m);
			if (!visited.contains(s) && isAnnotationCompatible(annotationHierarchies, m, idAnnotations)) {
				idTypes.add(m.getType());
			}
			visited.add(s);
		}
		if (!type.isRecord()) {
			for (IMethodBinding m : type.getDeclaredMethods()) {
				String s = methodSignature(m);
				if (!visited.contains(s) && isAnnotationCompatible(annotationHierarchies, m, idAnnotations)) {
					idTypes.add(m.getReturnType());
				}
				visited.add(s);
			}
			if (type.getSuperclass() != null) {
				idTypes.addAll(findAnnotatedIdTypes(annotationHierarchies, type.getSuperclass(), visited));
			}
		}
		return idTypes;
	}

	private static String fieldSignature(IVariableBinding f) {
		return f.getName();
	}
	
	private static boolean isAnnotationCompatible(AnnotationHierarchies annotationHierarchies, IBinding binding, Collection<String> fqNames) {
		for (String fqName : fqNames) {
			if (annotationHierarchies.isAnnotatedWith(binding, fqName)) {
				return true;
			}
		}
		return false;
	}
	
	private static String methodSignature(IMethodBinding m) {
		StringBuilder sb = new StringBuilder();
		sb.append(m.getName());
		sb.append('(');
		sb.append(
				Arrays.stream(m.getParameterTypes()).map(pt -> pt.getQualifiedName()).collect(Collectors.joining(",")));
		sb.append(')');
		return sb.toString();
	}

	private static int findTypeVarIndex(ITypeBinding[] typeParams, String genericVarName) {
		for (int i = 0; i < typeParams.length; i++) {
			ITypeBinding t = typeParams[i];
			if (t.isTypeVariable()) {
				if (genericVarName.equals(t.getName())) {
					return i;
				}
			}
		}
		return -1;
	}

	private static boolean isNoNewTypeParamsAdded(List<ITypeBinding> types) {
		for (int i = types.size() - 1; i > 0; i--) {
			if (types.get(i).getTypeArguments().length < types.get(i - 1).getTypeArguments().length) {
				return false;
			}
		}
		return true;
	}

	private static List<ASTNode> findParamTypes(List<?> typeParams, ITypeBinding idType) {
		List<ASTNode> matchedParams = new ArrayList<>();
		for (Object o : typeParams) {
			if (o instanceof TypeParameter) {
				TypeParameter tp = (TypeParameter) o;
				ITypeBinding b = tp.resolveBinding();
				if (idType.isEqualTo(b)) {
					matchedParams.add(tp);
				}
			} else if (o instanceof Type) {
				Type type = (Type) o;
				ITypeBinding b = ((Type) o).resolveBinding();
				if (idType.isEqualTo(b)) {
					matchedParams.add(type);
				}
			}
		}
		return matchedParams;
	}


}
