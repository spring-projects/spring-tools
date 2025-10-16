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
package org.springframework.ide.vscode.boot.java.beans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.events.EventListenerIndexElement;
import org.springframework.ide.vscode.boot.java.events.EventListenerIndexer;
import org.springframework.ide.vscode.boot.java.events.EventPublisherIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.reconcilers.NotRegisteredBeansReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexer;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigJavaIndexer;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.DefaultSymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.AotProcessorElement;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeanMethodContainerElement;
import org.springframework.ide.vscode.commons.protocol.spring.BeanRegistrarElement;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.protocol.spring.SimpleSymbolElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 * @author Kris De Volder
 */
public class ComponentSymbolProvider implements SymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(ComponentSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			if (node != null && node.getParent() != null && node.getParent() instanceof TypeDeclaration type) {
				createSymbol(type, node, annotationType, metaAnnotations, context, doc);
			}
			else if (node != null && node.getParent() != null && node.getParent() instanceof RecordDeclaration record) {
				createSymbol(record, node, annotationType, metaAnnotations, context, doc);
			}
			else if (node != null && node.getParent() != null && node.getParent() instanceof AnnotationTypeDeclaration annotationDeclaration) {
				createSymbol(annotationDeclaration, node, annotationType, metaAnnotations, context, doc);
			}
			else if (Annotations.NAMED_ANNOTATIONS.contains(annotationType.getQualifiedName())) {
				WorkspaceSymbol symbol = DefaultSymbolProvider.provideDefaultSymbol(node, doc);
				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
				context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), new SimpleSymbolElement(symbol)));
			}
		}
		catch (BadLocationException e) {
			log.error("", e);
		}
	}

	private void createSymbol(TypeDeclaration type, Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
		String annotationTypeName = annotationType.getName();
		
		Collection<String> metaAnnotationNames = metaAnnotations.stream()
				.map(ITypeBinding::getName)
				.collect(Collectors.toList());
		
		String beanName = BeanUtils.getBeanNameFromComponentAnnotation(node, type);
		ITypeBinding beanType = type.resolveBinding();

		Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
		
		WorkspaceSymbol symbol = new WorkspaceSymbol(
				beanLabel(annotationTypeName, metaAnnotationNames, beanName, beanType.getName()), SymbolKind.Interface,
				Either.forLeft(location));
		
		boolean isConfiguration = Annotations.CONFIGURATION.equals(annotationType.getQualifiedName())
				|| metaAnnotations.stream().anyMatch(t -> Annotations.CONFIGURATION.equals(t.getQualifiedName()));

		InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(type, doc);
		
		Set<String> supertypes = ASTUtils.findSupertypes(beanType);
		
		Collection<Annotation> annotationsOnType = ASTUtils.getAnnotations(type);
		
		AnnotationMetadata[] annotations = Stream.concat(
				Arrays.stream(ASTUtils.getAnnotationsMetadata(annotationsOnType, doc))
				,
				metaAnnotations.stream()
				.map(an -> new AnnotationMetadata(an.getQualifiedName(), true, null, null)))
				.toArray(AnnotationMetadata[]::new);
		
		Bean beanDefinition = new Bean(beanName, beanType.getQualifiedName(), location, injectionPoints, supertypes, annotations, isConfiguration, symbol.getName());
		
		// event publisher checks
		boolean usesEventPublisher = false;
		for (InjectionPoint injectionPoint : injectionPoints) {
			if (Annotations.EVENT_PUBLISHER.equals(injectionPoint.getType())) {
				usesEventPublisher = true;
			}
		}
		
		if (usesEventPublisher) {
			if (context.isFullAst()) {
				scanEventPublisherInvocations(beanDefinition, node, annotationType, metaAnnotations, context, doc);
			}
			else {
				throw new RequiredCompleteAstException();
			}
		}
		
		indexBeanMethods(beanDefinition, type, annotationType, metaAnnotations, context, doc);
		indexEventListeners(beanDefinition, type, annotationType, metaAnnotations, context, doc);
		indexEventListenerInterfaceImplementation(beanDefinition, type, context, doc);
		indexRequestMappings(beanDefinition, type, annotationType, context, doc);
		indexConfigurationProperties(beanDefinition, type, context, doc);
		indexBeanRegistrarImplementation(beanDefinition, type, context, doc);
		indexWebConfig(beanDefinition, type, context, doc);
		
		SpringBootApplicationIndexer.createIndexElement(type, annotationType, metaAnnotations, context);

		context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
		context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), beanDefinition));
	}
	
	private void createSymbol(RecordDeclaration record, Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
		String annotationTypeName = annotationType.getName();
		
		Collection<String> metaAnnotationNames = metaAnnotations.stream()
				.map(ITypeBinding::getName)
				.collect(Collectors.toList());
		
		String beanName = BeanUtils.getBeanNameFromComponentAnnotation(node, record);
		ITypeBinding beanType = record.resolveBinding();

		Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
		
		WorkspaceSymbol symbol = new WorkspaceSymbol(
				beanLabel(annotationTypeName, metaAnnotationNames, beanName, beanType.getName()), SymbolKind.Interface,
				Either.forLeft(location));
		
		boolean isConfiguration = Annotations.CONFIGURATION.equals(annotationType.getQualifiedName())
				|| metaAnnotations.stream().anyMatch(t -> Annotations.CONFIGURATION.equals(t.getQualifiedName()));

		InjectionPoint[] injectionPoints = DefaultValues.EMPTY_INJECTION_POINTS;
		
		Set<String> supertypes = ASTUtils.findSupertypes(beanType);
		
		Collection<Annotation> annotationsOnType = ASTUtils.getAnnotations(record);
		
		AnnotationMetadata[] annotations = Stream.concat(
				Arrays.stream(ASTUtils.getAnnotationsMetadata(annotationsOnType, doc))
				,
				metaAnnotations.stream()
				.map(an -> new AnnotationMetadata(an.getQualifiedName(), true, null, null)))
				.toArray(AnnotationMetadata[]::new);
		
		Bean beanDefinition = new Bean(beanName, beanType.getQualifiedName(), location, injectionPoints, supertypes, annotations, isConfiguration, symbol.getName());
		
		indexConfigurationProperties(beanDefinition, record, context, doc);

		context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
		context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), beanDefinition));
	}
	
	private void createSymbol(AnnotationTypeDeclaration annotationDeclaration, Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations,
			SpringIndexerJavaContext context, TextDocument doc) {
		
		SpringBootApplicationIndexer.createIndexElement(annotationDeclaration, annotationType, metaAnnotations, context);
	}
	
	private void indexConfigurationProperties(Bean beanDefinition, AbstractTypeDeclaration type, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);

		if (annotationHierarchies.isAnnotatedWith(type.resolveBinding(), Annotations.CONFIGURATION_PROPERTIES)) {
			ConfigurationPropertiesSymbolProvider.indexConfigurationProperties(beanDefinition, type, context, doc);
		}
	}

	private void indexBeanMethods(final Bean bean, TypeDeclaration type, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);
		
		SpringIndexElement parent = bean;

		if (bean == null) {
			try {

				Location location = new Location(doc.getUri(), doc.toRange(type.getName().getStartPosition(), type.getName().getLength()));
				String typeName = type.resolveBinding().getQualifiedName();
				parent = new BeanMethodContainerElement(location, typeName);

			} catch (BadLocationException e) {
				log.error("error while looking up position for type: " + type.toString(), e);
				return;
			}
		}
		else if (!bean.isConfiguration()) {
			return;
		}
		
		MethodDeclaration[] methods = type.getMethods();
		if (methods == null) {
			return;
		}

		for (int i = 0; i < methods.length; i++) {
			MethodDeclaration methodDecl = methods[i];
			Collection<Annotation> annotations = ASTUtils.getAnnotations(methodDecl);

			for (Annotation annotation : annotations) {
				ITypeBinding typeBinding = annotation.resolveTypeBinding();

				boolean isBeanMethod = annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.BEAN);
				if (isBeanMethod) {
					BeansIndexer.indexBeanMethod(parent, annotation, context, doc);
				}
			}
		}
		
		if (bean == null && parent.getChildren().size() > 0) {
			context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), parent));
		}
	}

	private void indexEventListeners(Bean bean, TypeDeclaration type, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);		
		
		MethodDeclaration[] methods = type.getMethods();
		if (methods == null) {
			return;
		}

		for (int i = 0; i < methods.length; i++) {
			MethodDeclaration methodDecl = methods[i];
			Collection<Annotation> annotations = ASTUtils.getAnnotations(methodDecl);

			for (Annotation annotation : annotations) {
				ITypeBinding typeBinding = annotation.resolveTypeBinding();

				boolean isEventListenerAnnotation = annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.EVENT_LISTENER);
				if (isEventListenerAnnotation) {
					EventListenerIndexer.indexEventListener(bean, annotation, context, doc);
				}
			}
		}
	}

	private void indexRequestMappings(Bean controller, TypeDeclaration type, ITypeBinding annotationType, SpringIndexerJavaContext context, TextDocument doc) {
		RequestMappingIndexer.indexRequestMappings(controller, type, annotationType, context, doc);
	}

	private void scanEventPublisherInvocations(Bean component, Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		TypeDeclaration type = (TypeDeclaration) node.getParent();
		type.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				try {
					String methodName = methodInvocation.getName().toString();
					if ("publishEvent".equals(methodName)) {

						IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
						boolean doesInvokeEventPublisher = Annotations.EVENT_PUBLISHER.equals(methodBinding.getDeclaringClass().getQualifiedName());
						if (doesInvokeEventPublisher) {
							List<?> arguments = methodInvocation.arguments();
							if (arguments != null && arguments.size() == 1) {

								ITypeBinding eventTypeBinding = ((Expression) arguments.get(0)).resolveTypeBinding();
								if (eventTypeBinding != null) {

									DocumentRegion nodeRegion = ASTUtils.nodeRegion(doc, methodInvocation);
									Location location;
									location = new Location(doc.getUri(), nodeRegion.asRange());

									Set<String> typesFromhierarchy = ASTUtils.findSupertypes(eventTypeBinding);

									EventPublisherIndexElement eventPublisherIndexElement = new EventPublisherIndexElement(eventTypeBinding.getQualifiedName(), location, typesFromhierarchy);
									component.addChild(eventPublisherIndexElement);

									// symbol
									String symbolLabel = "@EventPublisher (" + eventTypeBinding.getName() + ")";
									WorkspaceSymbol symbol = new WorkspaceSymbol(symbolLabel, SymbolKind.Interface, Either.forLeft(location));
									context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
								}
							}
						}
					}
				
				} catch (BadLocationException e) {
					log.error("", e);
				}
				return super.visit(methodInvocation);
			}
		});
	}
	
	@Override
	public void addSymbols(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);
		boolean isComponment = annotationHierarchies.isAnnotatedWith(typeDeclaration.resolveBinding(), Annotations.COMPONENT);
		
		// check for event listener implementations on classes that are not annotated with component, but created via bean methods (for example)
		if (!isComponment) {
			indexEventListenerInterfaceImplementation(null, typeDeclaration, context, doc);
			indexBeanRegistrarImplementation(null, typeDeclaration, context, doc);
			indexBeanMethods(null, typeDeclaration, null, null, context, doc);
			indexAotProcessors(typeDeclaration, context);
		}
		
	}
	
	private void indexAotProcessors(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context) {
		ITypeBinding typeBinding = typeDeclaration.resolveBinding();
		if (typeBinding == null) return;
		
		if (ASTUtils.isAnyTypeInHierarchy(typeBinding, NotRegisteredBeansReconciler.AOT_BEANS)) {
			String type = typeBinding.getQualifiedName();
			String docUri = context.getDocURI();
			
			AotProcessorElement aotProcessorElement = new AotProcessorElement(type, docUri);
			context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), aotProcessorElement));
		}
	}

	private void indexEventListenerInterfaceImplementation(Bean bean, TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			ITypeBinding typeBinding = typeDeclaration.resolveBinding();
			if (typeBinding == null) return;
			
			ITypeBinding inTypeHierarchy = ASTUtils.findInTypeHierarchy(typeBinding, Set.of(Annotations.APPLICATION_LISTENER));
			if (inTypeHierarchy == null) return;
	
			MethodDeclaration handleEventMethod = findHandleEventMethod(typeDeclaration);
			if (handleEventMethod == null) return;
	
			IMethodBinding methodBinding = handleEventMethod.resolveBinding();
			ITypeBinding[] parameterTypes = methodBinding.getParameterTypes();
			if (parameterTypes != null && parameterTypes.length == 1) {
	
				ITypeBinding eventType = parameterTypes[0];
				String eventTypeFq = eventType.getQualifiedName();
	
				DocumentRegion nodeRegion = ASTUtils.nodeRegion(doc, handleEventMethod.getName());
				Location handleMethodLocation = new Location(doc.getUri(), nodeRegion.asRange());
	
				Collection<Annotation> annotationsOnHandleEventMethod = ASTUtils.getAnnotations(handleEventMethod);
				AnnotationMetadata[] handleEventMethodAnnotations = ASTUtils.getAnnotationsMetadata(annotationsOnHandleEventMethod, doc);
	
				EventListenerIndexElement eventElement = new EventListenerIndexElement(eventTypeFq, handleMethodLocation, typeBinding.getQualifiedName(), handleEventMethodAnnotations);
				
				if (bean != null) {
					bean.addChild(eventElement);
				}
				else {
					context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), eventElement));
				}
			}
		} catch (BadLocationException e) {
			log.error("", e);
		}
	}

	private MethodDeclaration findHandleEventMethod(TypeDeclaration type) {
		MethodDeclaration[] methods = type.getMethods();
		
		for (MethodDeclaration method : methods) {
			IMethodBinding binding = method.resolveBinding();
			String name = binding.getName();
			
			if (name != null && name.equals("onApplicationEvent")) {
				return method;
			}
		}
		return null;
	}
	
	private MethodDeclaration findRegisterMethod(TypeDeclaration type, ITypeBinding beanRegistrarType) {
		IMethodBinding[] beanRegistrarMethods = beanRegistrarType.getDeclaredMethods();
		if (beanRegistrarMethods == null || beanRegistrarMethods.length != 1 || !"register".equals(beanRegistrarMethods[0].getName())) {
			return null;
		}
		
		MethodDeclaration[] methods = type.getMethods();
		
		for (MethodDeclaration method : methods) {
			IMethodBinding binding = method.resolveBinding();
			boolean overrides = binding.overrides(beanRegistrarMethods[0]);
			if (overrides) {
				return method;
			}
		}

		return null;
	}
	
	private void indexBeanRegistrarImplementation(SpringIndexElement parentNode, TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			ITypeBinding typeBinding = typeDeclaration.resolveBinding();
			if (typeBinding == null) return;
			
			ITypeBinding inTypeHierarchy = ASTUtils.findInTypeHierarchy(typeBinding, Set.of(Annotations.BEAN_REGISTRAR_INTERFACE));
			if (inTypeHierarchy == null) return;
	
			MethodDeclaration registerMethod = findRegisterMethod(typeDeclaration, inTypeHierarchy);
			if (registerMethod == null) return;
			
			if (!context.isFullAst()) { // needs full method bodies to continue
				throw new RequiredCompleteAstException();
			}
			
			if (parentNode == null) { // need to create and register bean element
				String name = typeBinding.getName();
				String type = typeBinding.getQualifiedName();
				
				SimpleName typeNameNode = typeDeclaration.getName();
				Location location = new Location(doc.getUri(), doc.toRange(typeNameNode.getStartPosition(), typeNameNode.getLength()));
				
				WorkspaceSymbol symbol = new WorkspaceSymbol(
						name + " (Bean Registrar)",
						SymbolKind.Interface,
						Either.forLeft(location));
				
				parentNode = new BeanRegistrarElement(name, type, location);

				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
				context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), parentNode));
			}
			
			scanBeanRegistryInvocations(parentNode, registerMethod.getBody(), context, doc);
			
		} catch (BadLocationException e) {
			log.error("", e);
		}
	}
	
	private void indexWebConfig(Bean beanDefinition, TypeDeclaration type, SpringIndexerJavaContext context, TextDocument doc) {
		WebConfigJavaIndexer.indexWebConfig(beanDefinition, type, context, doc);
	}

	private void scanBeanRegistryInvocations(SpringIndexElement parent, Block body, SpringIndexerJavaContext context, TextDocument doc) {
		if (body == null) {
			return;
		}

		body.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				try {
					String methodName = methodInvocation.getName().toString();
					if ("registerBean".equals(methodName)) {

						IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
						ITypeBinding declaringClass = methodBinding.getDeclaringClass();
						
						if (declaringClass != null && Annotations.BEAN_REGISTRY_INTERFACE.equals(declaringClass.getQualifiedName())) {
							
							@SuppressWarnings("unchecked")
							List<Expression> arguments = methodInvocation.arguments();
							List<ITypeBinding> types = new ArrayList<>();
							
							for (Expression argument : arguments) {
								ITypeBinding typeBinding = argument.resolveTypeBinding();
								if (typeBinding != null) {
									types.add(typeBinding);
								}
								else {
									return true;
								}
							}
							
							if (arguments.size() == 1 && "java.lang.Class".equals(types.get(0).getBinaryName())) {
								// <T> String registerBean(Class<T> beanClass);
 
								ITypeBinding typeBinding = types.get(0);
								ITypeBinding[] typeParameters = typeBinding.getTypeArguments();
								if (typeParameters != null && typeParameters.length == 1) {
									String typeParamName = typeParameters[0].getBinaryName();
									
									String beanName = BeanUtils.getBeanNameFromType(typeParameters[0].getName());
									String beanType = typeParamName;
									
									createBean(parent, beanName, beanType, typeParameters[0], methodInvocation, context, doc);
								}
							}
							else if (arguments.size() == 2 && "java.lang.String".equals(types.get(0).getQualifiedName()) && "java.lang.Class".equals(types.get(1).getBinaryName())) {
								// <T> void registerBean(String name, Class<T> beanClass);
								
								String beanName = ASTUtils.getExpressionValueAsString(arguments.get(0), (dep) -> {});
								
								ITypeBinding typeBinding = types.get(1);
								ITypeBinding[] typeParameters = typeBinding.getTypeArguments();
								if (typeParameters != null && typeParameters.length == 1) {
									String typeParamName = typeParameters[0].getBinaryName();
									String beanType = typeParamName;
									
									createBean(parent, beanName, beanType, typeParameters[0], methodInvocation, context, doc);
								}
							}
							else if (arguments.size() == 2 && "java.lang.Class".equals(types.get(0).getBinaryName()) && "java.util.function.Consumer".equals(types.get(1).getBinaryName())) {
								// <T> String registerBean(Class<T> beanClass, Consumer<Spec<T>> customizer);
								
								ITypeBinding typeBinding = types.get(0);
								ITypeBinding[] typeParameters = typeBinding.getTypeArguments();
								if (typeParameters != null && typeParameters.length == 1) {
									String typeParamName = typeParameters[0].getBinaryName();
									
									String beanName = BeanUtils.getBeanNameFromType(typeParameters[0].getName());
									String beanType = typeParamName;
									
									createBean(parent, beanName, beanType, typeParameters[0], methodInvocation, context, doc);
								}
							}
							else if (arguments.size() == 3 && "java.lang.String".equals(types.get(0).getQualifiedName())
									&& "java.lang.Class".equals(types.get(1).getBinaryName()) && "java.util.function.Consumer".equals(types.get(2).getBinaryName())) {
								// <T> void registerBean(String name, Class<T> beanClass, Consumer<Spec<T>> customizer);
								
								String beanName = ASTUtils.getExpressionValueAsString(arguments.get(0), (dep) -> {});
								
								ITypeBinding typeBinding = types.get(1);
								ITypeBinding[] typeParameters = typeBinding.getTypeArguments();
								if (typeParameters != null && typeParameters.length == 1) {
									String typeParamName = typeParameters[0].getBinaryName();
									String beanType = typeParamName;
									
									createBean(parent, beanName, beanType, typeParameters[0], methodInvocation, context, doc);
								}
							}
						}
					}
				
				} catch (BadLocationException e) {
					log.error("", e);
				}
				return super.visit(methodInvocation);
			}
		});
	}
	
	public void createBean(SpringIndexElement parentNode, String beanName, String beanType, ITypeBinding beanTypeBinding, ASTNode node, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
		Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
		
		WorkspaceSymbol symbol = new WorkspaceSymbol(
				beanLabel(null, null, beanName, beanType),
				SymbolKind.Class,
				Either.forLeft(location));
		context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
		
		InjectionPoint[] injectionPoints = DefaultValues.EMPTY_INJECTION_POINTS;
		Set<String> supertypes = ASTUtils.findSupertypes(beanTypeBinding);
		
		AnnotationMetadata[] annotations = DefaultValues.EMPTY_ANNOTATIONS;
		
		Bean bean = new Bean(beanName, beanType, location, injectionPoints, supertypes, annotations, false, symbol.getName());
		parentNode.addChild(bean);
	}
	
	public static String beanLabel(String annotationTypeName, Collection<String> metaAnnotationNames, String beanName, String beanType) {
		StringBuilder symbolLabel = new StringBuilder();
		symbolLabel.append('@');
		symbolLabel.append('+');
		symbolLabel.append(' ');
		symbolLabel.append('\'');
		symbolLabel.append(beanName);
		symbolLabel.append('\'');
		
		if (annotationTypeName != null) {
			symbolLabel.append(" (@");
			symbolLabel.append(annotationTypeName);
			if (!metaAnnotationNames.isEmpty()) {
				symbolLabel.append(" <: ");
				boolean first = true;
				for (String ma : metaAnnotationNames) {
					if (!first) {
						symbolLabel.append(", ");
					}
					symbolLabel.append("@");
					symbolLabel.append(ma);
					first = false;
				}
			}
			symbolLabel.append(")");
		}
		symbolLabel.append(" ");
		symbolLabel.append(beanType);
		return symbolLabel.toString();
	}
	
}
