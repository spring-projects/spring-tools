/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.protocol.spring.AotProcessorElement;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.DefineMethod;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.util.UriUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;

public class NotRegisteredBeansReconciler implements JdtAstReconciler {

	public static final List<String> AOT_BEANS = List.of(
			"org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor",
			"org.springframework.beans.factory.aot.BeanRegistrationAotProcessor"
	);

	private QuickfixRegistry registry;
	private SpringMetamodelIndex springIndex;
	
	public NotRegisteredBeansReconciler(QuickfixRegistry registry, SpringMetamodelIndex springIndex) {
		this.registry = registry;
		this.springIndex = springIndex;		
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(3, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return SpringAotJavaProblemType.JAVA_BEAN_NOT_REGISTERED_IN_AOT;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration node) {
				if (!node.isInterface() && !Modifier.isAbstract(node.getModifiers())) {

					ITypeBinding type = node.resolveBinding();
					if (type != null && ReconcileUtils.implementsAnyType(AOT_BEANS, type)) {
						
						// // reconcile AOT Proceesor itself
						
						if (!context.isIndexComplete()) {
							throw new RequiredCompleteIndexException();
						}
						
						String beanClassName = type.getQualifiedName();
						Bean[] registeredBeans = springIndex.getBeansWithType(project.getElementName(), beanClassName);
						
						if (registeredBeans == null || registeredBeans.length == 0) {
							createProblemAndQuickFixes(project, context.getProblemCollector(), node, type);
						}
						else {
							// record dependency, if bean is not coming from the current doc (defined somewhere else)
							String uri = docUri.toASCIIString();
							for (Bean bean : registeredBeans) {
								String beanDocUri = bean.getLocation().getUri();
								if (!beanDocUri.equals(uri)) {
									Bean parentBean = springIndex.getParentBean(bean);
									if (parentBean != null) {
										context.addDependency(parentBean.getType());
									}
								}
							}
						}
					}
					else {
						
						//
						// check if new beans have been defined that refer to any AOP processor element
						//
						
						List<AotProcessorElement> aotProcessors = springIndex.getNodesOfType(AotProcessorElement.class);
						if (aotProcessors != null && aotProcessors.size() > 0) {

							List<SpringIndexElement> createdIndexElements = context.getCreatedIndexElements();
							List<Bean> createdBeanElements = SpringMetamodelIndex.getNodesOfType(Bean.class, createdIndexElements);
							Set<String> beanTypes = createdBeanElements.stream()
									.filter(bean -> context.getDocURI().equals(bean.getLocation().getUri()))
									.map(bean -> bean.getType())
									.collect(Collectors.toSet());

							aotProcessors.stream()
									.filter(aotProcessor -> beanTypes.contains(aotProcessor.getType()))
									.map(aotProcessor -> UriUtil.toFileString(aotProcessor.getDocUri()))
									.forEach(file -> context.markForAffetcedFilesIndexing(file));
						}
					}
				}
				return super.visit(node);
			}
		};
	}

	protected void createProblemAndQuickFixes(IJavaProject project, IProblemCollector problemCollector, TypeDeclaration node, ITypeBinding type) {
		Builder<FixDescriptor> fixListBuilder = ImmutableList.builder();
		
		Bean[] configBeans = getConfigurationBeans(project);

		for (Bean configBean : configBeans) {
			for (IMethodBinding constructor : type.getDeclaredMethods()) {
				if (constructor.isConstructor()) {
					String constructorParamsSignature = "(" + Arrays.stream(constructor.getParameterTypes()).map(pt -> typePattern(pt)).collect(Collectors.joining(",")) + ")";
					String beanMethodName = "get" + type.getName();
					String pattern = beanMethodName + constructorParamsSignature;
					String contructorParamsLabel = "(" + Arrays.stream(constructor.getParameterTypes()).map(NotRegisteredBeansReconciler::typeStr).collect(Collectors.joining(", ")) + ")";

					Builder<String> paramBuilder = ImmutableList.builder();
					for (int i = 0; i < constructor.getParameterNames().length && i < constructor.getParameterTypes().length; i++) {
						ITypeBinding paramType = constructor.getParameterTypes()[i];
						String paramName = constructor.getParameterNames()[i]; 
						paramBuilder.add(typeStr(paramType) + ' ' + paramName);
					}
					String paramsStr = String.join(", ", paramBuilder.build().toArray(String[]::new));

					final Set<String> allFqTypes = new HashSet<>();
					allFqTypes.add(Annotations.BEAN);
					allFqTypes.addAll(allFQTypes(constructor));
					fixListBuilder.add(new FixDescriptor(DefineMethod.class.getName(), List.of(configBean.getLocation().getUri()), "Define bean in config '" + configBean.getName() + "' with constructor " + contructorParamsLabel)
							.withRecipeScope(RecipeScope.FILE)
							.withParameters(Map.of(
									"targetFqName", configBean.getType(),
									"signature", pattern,
									"template", "@Bean\n"
											+ type.getName() + " " + beanMethodName + "(" + paramsStr + ") {\n"
											+ "return new " +  type.getName() + "(" + Arrays.stream(constructor.getParameterNames()).collect(Collectors.joining(", ")) + ");\n"
											+ "}\n",
											"imports", allFqTypes.toArray(String[]::new),
											"typeStubs", new String[0]/*new String[] { source.printAll() }*/,
											"classpath", IClasspathUtil.getAllBinaryRoots(project.getClasspath()).stream().map(f -> f.toPath().toString()).toArray(String[]::new)

									))
							);
				}
			}
		}

		ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), getProblemType().getLabel(), node.getName().getStartPosition(), node.getName().getLength());
		ReconcileUtils.setRewriteFixes(registry, problem, fixListBuilder.build());
		problemCollector.accept(problem);
	}

	private Bean[] getConfigurationBeans(IJavaProject project) {
		Bean[] beans = springIndex.getBeansOfProject(project.getElementName());
		if (beans != null) {
			return Arrays.stream(beans).filter(bean -> bean.isConfiguration()).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}

	private static Set<String> allFQTypes(IBinding binding) {
		ImmutableSet.Builder<String> b = ImmutableSet.builder();
		if (binding instanceof IMethodBinding) {
			IMethodBinding methodBinding = (IMethodBinding) binding;
			b.addAll(allFQTypes(methodBinding.getDeclaringClass()));
			for (ITypeBinding paramType : methodBinding.getParameterTypes()) {
				b.addAll(allFQTypes(paramType));
			}
		} else if (binding instanceof ITypeBinding) {
			ITypeBinding typeBinding = (ITypeBinding) binding;
			if (!typeBinding.isPrimitive()) {
				for (ITypeBinding paramType : typeBinding.getTypeParameters()) {
					b.addAll(allFQTypes(paramType));
				}
				for (ITypeBinding argType : typeBinding.getTypeArguments()) {
					b.addAll(allFQTypes(argType));
					
				}
				b.addAll(allFQTypes(typeBinding.getBound()));
				ITypeBinding erasure = typeBinding.getErasure();
				if (erasure == typeBinding) {
					b.add(typeBinding.getQualifiedName());
				} else {
					b.addAll(allFQTypes(erasure));
				}
			}
		}
		return b.build();
	}


    private static String typePattern(ITypeBinding type) {
    	if (type.isArray()) {
    		return typePattern(type.getErasure()) + "[]";
    	} else {
    		return ReconcileUtils.getDeepErasureType(type).getQualifiedName();
    	}
    }
    
    private static String typeStr(ITypeBinding type) {
    	return type.getName();
    }

}
