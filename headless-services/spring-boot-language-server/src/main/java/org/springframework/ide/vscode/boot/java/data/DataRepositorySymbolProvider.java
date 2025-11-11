/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
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
import java.util.Set;

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
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.BeanUtils;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryVisitorUtils;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryVisitorUtils.EmbeddedQueryExpression;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

/**
 * @author Martin Lippert
 */
public class DataRepositorySymbolProvider implements SymbolProvider {

	private static final Logger log = LoggerFactory.getLogger(DataRepositorySymbolProvider.class);

	private final DataRepositoryAotMetadataService repositoryMetadataService;
	
	public DataRepositorySymbolProvider(DataRepositoryAotMetadataService repositoryMetadataService) {
		this.repositoryMetadataService = repositoryMetadataService;
	}

	@Override
	public void addSymbols(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		// this checks spring data repository beans that are defined as extensions of the repository interface
		Tuple4<String, ITypeBinding, String, DocumentRegion> repositoryBean = getRepositoryBean(typeDeclaration, doc);

		if (repositoryBean != null) {
			try {
				String beanName = repositoryBean.getT1();
				ITypeBinding beanType = repositoryBean.getT2();
				Location location = new Location(doc.getUri(), doc.toRange(repositoryBean.getT4()));
				
				WorkspaceSymbol symbol = new WorkspaceSymbol(
						beanLabel(true, beanName, beanType.getName(), repositoryBean.getT3()),
						SymbolKind.Interface,
						Either.forLeft(location));

				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));

				// index elements
				InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(typeDeclaration, doc);
				
				ITypeBinding concreteBeanTypeBindung = typeDeclaration.resolveBinding();

				Set<String> supertypes = ASTUtils.findSupertypes(concreteBeanTypeBindung);

				String concreteRepoType = concreteBeanTypeBindung.getQualifiedName();
				
				Collection<Annotation> annotationsOnMethod = ASTUtils.getAnnotations(typeDeclaration);
				AnnotationMetadata[] annotations = ASTUtils.getAnnotationsMetadata(annotationsOnMethod, doc);
				
				Bean beanDefinition = new Bean(beanName, concreteRepoType, location, injectionPoints, supertypes, annotations, false, symbol.getName());
				indexQueryMethods(beanDefinition, typeDeclaration, context, doc);
				
				context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), beanDefinition));

			} catch (BadLocationException e) {
				log.error("error creating data repository symbol for a specific range", e);
			}
		}
	}

	private void indexQueryMethods(Bean beanDefinition, TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);
		
		List<MethodDeclaration> methods = identifyQueryMethods(typeDeclaration, annotationHierarchies);
		
		for (MethodDeclaration method : methods) {
			SimpleName nameNode = method.getName();
			
			if (nameNode != null) {
				String methodName = nameNode.getFullyQualifiedName();
				DocumentRegion nodeRegion = ASTUtils.nodeRegion(doc, nameNode);

				try {
					Range range = doc.toRange(nodeRegion);
				
					if (methodName != null) {
						String queryString = identifyQueryString(method, annotationHierarchies, context);
						String methodSignature = identifyMethodSignature(method);
						beanDefinition.addChild(new QueryMethodIndexElement(methodSignature, queryString, range));
					}
	
				} catch (BadLocationException e) {
					log.error("query method range computation failed", e);
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
			return queryExpression.query().getText();
		}
		
		// second option: lookup repository metadata service to see if there is a matching enty
		IMethodBinding methodBinding = method.resolveBinding();
		final String repositoryClass = methodBinding.getDeclaringClass().getBinaryName().trim();

		DataRepositoryAotMetadata repositoryMetadata = this.repositoryMetadataService.getRepositoryMetadata(context.getProject(), repositoryClass);
		if (repositoryMetadata == null) {
			return null;
		}
		
		IDataRepositoryAotMethodMetadata aotMethod = repositoryMetadata.findMethod(methodBinding);
		return aotMethod == null ? null :  aotMethod.getQueryStatement();
	}
	
	protected String beanLabel(boolean isFunctionBean, String beanName, String beanType, String markerString) {
		StringBuilder symbolLabel = new StringBuilder();
		symbolLabel.append("@+");
		symbolLabel.append(' ');
		symbolLabel.append('\'');
		symbolLabel.append(beanName);
		symbolLabel.append('\'');

		markerString = markerString != null && markerString.length() > 0 ? " Repository(" + markerString + ")" : "";
		symbolLabel.append(markerString);

		return symbolLabel.toString();
	}

	private static Tuple4<String, ITypeBinding, String, DocumentRegion> getRepositoryBean(TypeDeclaration typeDeclaration, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);

		ITypeBinding resolvedType = typeDeclaration.resolveBinding();
		if (resolvedType != null && !annotationHierarchies.isAnnotatedWith(resolvedType, Annotations.NO_REPO_BEAN)) {
			return getRepositoryBean(typeDeclaration, doc, resolvedType);
		}
		else {
			return null;
		}
	}

	private static Tuple4<String, ITypeBinding, String, DocumentRegion> getRepositoryBean(TypeDeclaration typeDeclaration, TextDocument doc, ITypeBinding resolvedType) {

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
				String beanName = BeanUtils.getBeanName(typeDeclaration);

				String domainType = null;
				if (resolvedInterface.isParameterizedType()) {
					ITypeBinding[] typeParameters = resolvedInterface.getTypeArguments();
					if (typeParameters != null && typeParameters.length > 0) {
						domainType = typeParameters[0].getName();
					}
				}
				DocumentRegion region = ASTUtils.nodeRegion(doc, typeDeclaration.getName());

				return Tuples.of(beanName, resolvedInterface, domainType, region);
			}
			else {
				Tuple4<String, ITypeBinding, String, DocumentRegion> result = getRepositoryBean(typeDeclaration, doc, resolvedInterface);
				if (result != null) {
					return result;
				}
			}
		}

		ITypeBinding superclass = resolvedType.getSuperclass();
		if (superclass != null) {
			return getRepositoryBean(typeDeclaration, doc, superclass);
		}
		else {
			return null;
		}
	}

}
