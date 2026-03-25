/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Range;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.BeanUtils;
import org.springframework.ide.vscode.boot.java.beans.BeanUtils.BeanLabelStrategy;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryVisitorUtils;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryVisitorUtils.EmbeddedQueryExpression;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class DataRepositoryIndexer {

	private final DataRepositoryAotMetadataService repositoryMetadataService;
	
	public DataRepositoryIndexer(DataRepositoryAotMetadataService repositoryMetadataService) {
		this.repositoryMetadataService = repositoryMetadataService;
	}

	public BeanLabelStrategy createRepositoryLabelStrategy(String domainTypeMarker) {
		return (beanName, annotations, beanTypeName) -> {
			StringBuilder symbolLabel = new StringBuilder();
			symbolLabel.append("@+");
			symbolLabel.append(' ');
			symbolLabel.append('\'');
			symbolLabel.append(beanName);
			symbolLabel.append('\'');
			
			String marker = domainTypeMarker != null && domainTypeMarker.length() > 0
					? " Repository(" + domainTypeMarker + ")"
					: "";
			symbolLabel.append(marker);
			
			return symbolLabel.toString();
		};
	}

	public Bean createReppsitoryBean(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context) throws Exception {
		// this checks spring data repository beans that are defined as extensions of the repository interface
		String domainTypeMarker = findRepositoryDomainType(typeDeclaration);

		if (domainTypeMarker != null) {
			Bean beanDefinition = BeanUtils.createBean(typeDeclaration, context.getDoc(), createRepositoryLabelStrategy(domainTypeMarker));
			indexQueryMethods(beanDefinition, typeDeclaration, context, context.getDoc());

			return beanDefinition;
		}

		return null;
	}

	private void indexQueryMethods(Bean beanDefinition, TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);

		List<MethodDeclaration> methods = identifyQueryMethods(typeDeclaration, annotationHierarchies);

		for (MethodDeclaration method : methods) {
			SimpleName nameNode = method.getName();

			if (nameNode != null) {
				String methodName = nameNode.getFullyQualifiedName();
				DocumentRegion nodeRegion = ASTUtils.nodeRegion(doc, nameNode);

				Range range = doc.toRange(nodeRegion);

				if (methodName != null) {
					String queryString = identifyQueryString(method, annotationHierarchies, context);
					String methodSignature = identifyMethodSignature(method);
					beanDefinition.addChild(new QueryMethodIndexElement(methodSignature, queryString, range));
				}
			}
		}
	}

	private String identifyMethodSignature(MethodDeclaration method) {
		StringBuilder result = new StringBuilder();

		// method name
		String name = method.getName().getFullyQualifiedName();
		result.append(name);

		// params
		result.append("(");
		
		@SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> parameters = method.parameters();
		String[] paramNames = new String[parameters.size()];

		for (int i = 0; i < parameters.size(); i++) {
			ITypeBinding type = parameters.get(i).getType().resolveBinding();
			paramNames[i] = type.getName();
		}
		result.append(String.join(", ", paramNames));

		result.append(") : ");
		
		// return type
		ITypeBinding returnType = method.getReturnType2().resolveBinding();
		String returnTypeName = returnType.getName();
		result.append(returnTypeName);
		
		return result.toString();
	}

	private List<MethodDeclaration> identifyQueryMethods(TypeDeclaration type, AnnotationHierarchies annotationHierarchies) {
		List<MethodDeclaration> result = new ArrayList<>();
		
		MethodDeclaration[] methods = type.getMethods();
		if (methods == null) return result;
		
		for (MethodDeclaration method : methods) {
			int modifiers = method.getModifiers();
			
			if ((modifiers & Modifier.DEFAULT) == 0) {
				result.add(method);
			}
		}
		
		return result;
	}

	private String identifyQueryString(MethodDeclaration method, AnnotationHierarchies annotationHierarchies, SpringIndexerJavaContext context) {
		
		// lookup query annotation on the method first
		EmbeddedQueryExpression queryExpression = null;

		Collection<Annotation> annotations = ASTUtils.getAnnotations(method);
		for (Annotation annotation : annotations) {
			ITypeBinding typeBinding = annotation.resolveTypeBinding();
			
			if (typeBinding != null && annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.DATA_QUERY_META_ANNOTATION)) {
				if (annotation instanceof SingleMemberAnnotation) {
					queryExpression = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, (SingleMemberAnnotation)annotation);
				}
				else if (annotation instanceof NormalAnnotation) {
					queryExpression = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, (NormalAnnotation)annotation);
				}
			}
		}

		if (queryExpression != null) {
			return normalizeQueryStringForRepositoryIndex(queryExpression.query().getText());
		}
		
		// second option: lookup repository metadata service to see if there is a matching entry
		IMethodBinding methodBinding = method.resolveBinding();
		final String repositoryClass = methodBinding.getDeclaringClass().getBinaryName().trim();

		DataRepositoryAotMetadata repositoryMetadata = this.repositoryMetadataService.getRepositoryMetadata(context.getProject(), repositoryClass).orElse(null);
		if (repositoryMetadata == null) {
			return null;
		}
		
		return repositoryMetadata.findMethod(methodBinding)
				.map(aotMethod -> normalizeQueryStringForRepositoryIndex(aotMethod.getQueryStatement()))
				.orElse(null);
	}

	private String normalizeQueryStringForRepositoryIndex(String sql) {
		if (sql == null) {
			return null;
		}
		return sql.replaceAll("\\s+", " ").trim();
	}

	private String findRepositoryDomainType(TypeDeclaration typeDeclaration) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);

		ITypeBinding resolvedType = typeDeclaration.resolveBinding();
		if (resolvedType != null && !annotationHierarchies.isAnnotatedWith(resolvedType, Annotations.NO_REPO_BEAN)) {
			return findRepositoryDomainType(resolvedType);
		}
		else {
			return null;
		}
	}

	private String findRepositoryDomainType(ITypeBinding resolvedType) {

		ITypeBinding[] interfaces = resolvedType.getInterfaces();
		for (ITypeBinding resolvedInterface : interfaces) {
			String simplifiedType = null;
			if (resolvedInterface.isParameterizedType()) {
				simplifiedType = resolvedInterface.getBinaryName();
			}
			else {
				simplifiedType = resolvedType.getQualifiedName();
			}

			if (Constants.REPOSITORY_TYPE.equals(simplifiedType)) {
				if (resolvedInterface.isParameterizedType()) {
					ITypeBinding[] typeParameters = resolvedInterface.getTypeArguments();
					if (typeParameters != null && typeParameters.length > 0) {
						return typeParameters[0].getName();
					}
				}
				return null;
			}
			else {
				String result = findRepositoryDomainType(resolvedInterface);
				if (result != null) {
					return result;
				}
			}
		}

		ITypeBinding superclass = resolvedType.getSuperclass();
		if (superclass != null) {
			return findRepositoryDomainType(superclass);
		}
		else {
			return null;
		}
	}

}
