/*******************************************************************************
 * Copyright (c) 2017, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.CompletionProvider;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Udayani V
 */
public class BeanCompletionProvider implements CompletionProvider {

	private static final Logger log = LoggerFactory.getLogger(BeanCompletionProvider.class);

	private final JavaProjectFinder javaProjectFinder;
	private final SpringMetamodelIndex springIndex;
	private final RewriteRefactorings rewriteRefactorings;

	private final BootJavaConfig config;

	public BeanCompletionProvider(JavaProjectFinder javaProjectFinder, SpringMetamodelIndex springIndex,
			RewriteRefactorings rewriteRefactorings, BootJavaConfig config) {
		this.javaProjectFinder = javaProjectFinder;
		this.springIndex = springIndex;
		this.rewriteRefactorings = rewriteRefactorings;
		this.config = config;
	}

	@Override
	public void provideCompletions(ASTNode node, int offset, TextDocument doc, Collection<ICompletionProposal> completions) {
		if (config.isBeanInjectionCompletionEnabled() 
				&& (node instanceof SimpleName || node instanceof Block || node instanceof FieldAccess || node instanceof ThisExpression)) {
			try {
				
				if (node instanceof SimpleName) {
					if (node.getParent() instanceof FieldAccess fa && !(fa.getExpression() instanceof ThisExpression)) {
						return;
					}
				}
				
				if (node instanceof FieldAccess fa && !(fa.getExpression() instanceof ThisExpression)) {
					return;
				}
				
				// Don't look at anything inside Annotation or VariableDelcaration node
				for (ASTNode n = node; n != null; n = n.getParent()) {
					if (n instanceof Annotation
							|| n instanceof VariableDeclaration
							|| n instanceof QualifiedName /* Ignores statements such as 's.' or 's.foo' */) {
						return;
					}
				}
				
				Optional<IJavaProject> optionalProject = this.javaProjectFinder.find(doc.getId());
				if (optionalProject.isEmpty()) {
					return;
				}
	
				IJavaProject project = optionalProject.get();
				TypeDeclaration topLevelClass = ASTUtils.findDeclaringType(node);
		        if (topLevelClass == null) {
		            return;
		        }
		        
				if (AnnotationHierarchies.get(node).isAnnotatedWith(topLevelClass.resolveBinding(), Annotations.COMPONENT)) {
		            String className = getFullyQualifiedName(topLevelClass);
					Bean[] beans = this.springIndex.getBeansOfProject(project.getElementName());
					ITypeBinding topLevelBeanType = topLevelClass.resolveBinding();
					Set<String> fieldTypes = new HashSet<>();
					Set<String> fieldNames = new HashSet<>();
					for (IVariableBinding vd : topLevelBeanType.getDeclaredFields()) {
						fieldNames.add(vd.getName());
						fieldTypes.add(vd.getType().getQualifiedName());
					}
					// Collect bean completions in a set to exclude possible bean symbol duplications
					Set<BeanCompletionProposal> beanCompletions = new HashSet<>();
					for (Bean bean : beans) {
						// If current class is a bean - ignore it
						if (className.equals(bean.getType())) {
							continue;
						}
						// Filter out beans already injected into this class
						if (fieldTypes.contains(bean.getType())) {
							continue;
						}
						
						String fieldName = bean.getName();
						for (int i = 0; i < Integer.MAX_VALUE && fieldNames.contains(fieldName); i++, fieldName = "%s_%d".formatted(bean.getName(), i)) {
							// nothing
						}
						BeanCompletionProposal proposal = new BeanCompletionProposal(node, offset, doc, bean.getName(),
								bean.getType(), fieldName, className, rewriteRefactorings);

						if (proposal.getScore() > 0) {
							beanCompletions.add(proposal);
						}
					}
					completions.addAll(beanCompletions);
				}
			} catch (Exception e) {
				log.error("problem while looking for bean completions", e);
			}
		}
	}
	
	private static String getFullyQualifiedName(TypeDeclaration typeDecl) {
		ITypeBinding binding = typeDecl.resolveBinding();
		if (binding != null) {
			String qualifiedName = binding.getQualifiedName();
	        return qualifiedName/*.replaceAll("\\.(?=[^\\.]+$)", "\\$")*/;
	    }
	    CompilationUnit cu = (CompilationUnit) typeDecl.getRoot();
	    String packageName = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "";
	    String typeName = typeDecl.getName().getFullyQualifiedName();
	    return packageName.isEmpty() ? typeName : packageName + "." + typeName;
	}

}
