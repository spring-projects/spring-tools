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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.openrewrite.java.spring.boot2.UnnecessarySpringExtension;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class UnnecessarySpringExtensionReconciler implements JdtAstReconciler {

	private static final String LABEL = "Remove unnecessary @SpringExtension";
    private static final List<String> SPRING_BOOT_TEST_ANNOTATIONS = Arrays.asList(
            "org.springframework.boot.test.context.SpringBootTest",
            "org.springframework.boot.test.autoconfigure.jdbc.JdbcTest",
            "org.springframework.boot.test.autoconfigure.web.client.RestClientTest",
            "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest",
            "org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest",
            "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
            "org.springframework.boot.test.autoconfigure.webservices.client.WebServiceClientTest",
            "org.springframework.boot.test.autoconfigure.jooq.JooqTest",
            "org.springframework.boot.test.autoconfigure.json.JsonTest",
            "org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest",
            "org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest",
            "org.springframework.boot.test.autoconfigure.data.ldap.DataLdapTest",
            "org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest",
            "org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest",
            "org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest",
            "org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest"
    );
    
    private static final String FQN_EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String FQN_SPRING_EXT = "org.springframework.test.context.junit.jupiter.SpringExtension";
    
	private QuickfixRegistry registry;
    
    public UnnecessarySpringExtensionReconciler(QuickfixRegistry registry) {
		this.registry = registry;
    }

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 1, 0).test(project);
	}

	@Override
	public Boot2JavaProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_TEST_SPRING_EXTENSION;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration typeDecl) {
				Annotation testAnnotation = null;
				Annotation extendWithAnnotation = null;
				for (Object o : typeDecl.modifiers()) {
					if (o instanceof Annotation) {
						Annotation a = (Annotation) o;
						if (testAnnotation == null && isApplicableTestAnnotation(a)) {
							testAnnotation = a;
						}
						if (extendWithAnnotation == null && isApplicableExtendsWith(a)) {
							extendWithAnnotation = a;
						}
						if (testAnnotation != null && extendWithAnnotation != null) {
							ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), LABEL, extendWithAnnotation.getStartPosition(), extendWithAnnotation.getLength());
							ReconcileUtils.setRewriteFixes(registry, problem, List.of(
									new FixDescriptor(UnnecessarySpringExtension.class.getName(), List.of(docUri.toASCIIString()), ReconcileUtils.buildLabel(LABEL, RecipeScope.PROJECT))
							));
							context.getProblemCollector().accept(problem);
							break;
						}
					}
				}
				return super.visit(typeDecl);
			}
		});
	}
	
	private static boolean isApplicableExtendsWith(Annotation a) {
		if (FQN_EXTEND_WITH.endsWith(a.getTypeName().getFullyQualifiedName())) {
			IAnnotationBinding annotationBinding = a.resolveAnnotationBinding();
			if (annotationBinding != null && FQN_EXTEND_WITH.equals(annotationBinding.getAnnotationType().getQualifiedName()) && annotationBinding.getDeclaredMemberValuePairs().length == 1) {
				IMemberValuePairBinding pair = annotationBinding.getDeclaredMemberValuePairs()[0];
				if ("value".equals(pair.getName())) {
					ITypeBinding typeBinding = null;
					if (pair.getValue() instanceof ITypeBinding) {
						typeBinding = (ITypeBinding) pair.getValue();
					} else if (pair.getValue() instanceof Object[]) {
						Object[] arr = (Object[]) pair.getValue();
						if (arr.length > 0 && arr[0] instanceof ITypeBinding) {
							typeBinding = (ITypeBinding) arr[0];
						}
					}
					return typeBinding != null && FQN_SPRING_EXT.equals(typeBinding.getQualifiedName());
				}
			}
		}
		return false;
	}
	
	private static boolean isApplicableTestAnnotation(Annotation a) {
		String annotationTypeFqn = a.getTypeName().getFullyQualifiedName();
		if (SPRING_BOOT_TEST_ANNOTATIONS.stream().anyMatch(fqn -> fqn.endsWith(annotationTypeFqn))) {
			IAnnotationBinding annotationBinding = a.resolveAnnotationBinding();
			if (annotationBinding != null && SPRING_BOOT_TEST_ANNOTATIONS.contains(annotationBinding.getAnnotationType().getQualifiedName())) {
				return true;
			}
		}
		return false;
	}

}
