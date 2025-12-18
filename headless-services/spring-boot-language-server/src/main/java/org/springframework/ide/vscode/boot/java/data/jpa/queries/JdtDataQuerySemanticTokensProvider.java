/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.springframework.ide.vscode.boot.java.JdtSemanticTokensProvider;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryVisitorUtils.EmbeddedQueryExpression;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokensDataProvider;
import org.springframework.ide.vscode.commons.util.Collector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class JdtDataQuerySemanticTokensProvider implements JdtSemanticTokensProvider {
	
	private final JpqlSemanticTokens jpqlProvider;
	private final HqlSemanticTokens hqlProvider;
	private final JpqlSupportState supportState;
	private final Map<SqlType, SemanticTokensDataProvider> sqlTokenProviders;

	
	public JdtDataQuerySemanticTokensProvider(JpqlSemanticTokens jpqlProvider, HqlSemanticTokens hqlProvider, JpqlSupportState supportState, Optional<SpelSemanticTokens> spelSemanticTokens) {
		this.jpqlProvider = jpqlProvider;
		this.hqlProvider = hqlProvider;
		this.supportState = supportState;
		
		this.sqlTokenProviders = new LinkedHashMap<>();
		this.sqlTokenProviders.put(SqlType.MYSQL, new MySqlSemanticTokens(spelSemanticTokens));
		this.sqlTokenProviders.put(SqlType.POSTGRESQL, new PostgreSqlSemanticTokens(spelSemanticTokens));
	}

	@Override
	public List<String> getTokenTypes() {
		return Stream.concat(jpqlProvider.getTokenTypes().stream(), hqlProvider.getTokenTypes().stream()).distinct().collect(Collectors.toList());
	}

	@Override
	public List<String> getTokenModifiers() {
		return Stream.concat(jpqlProvider.getTypeModifiers().stream(), hqlProvider.getTypeModifiers().stream()).distinct().collect(Collectors.toList());
	}

	@Override
	public ASTVisitor getTokensComputer(IJavaProject jp, TextDocument doc, CompilationUnit cu, Collector<SemanticTokenData> tokensData) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
		return new ASTVisitor() {
			@Override
			public boolean visit(NormalAnnotation a) {
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, a);
				if (q != null) {
					computeSemanticTokens(jp, q.query(), q.isNative()).forEach(tokensData::accept);
				}
				return super.visit(a);
			}

			@Override
			public boolean visit(SingleMemberAnnotation a) {
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, a);
				if (q != null) {
					computeSemanticTokens(jp, q.query(), q.isNative()).forEach(tokensData::accept);
				}
				return super.visit(a);
			}

			@Override
			public boolean visit(MethodInvocation node) {
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(node);
				if (q != null) {
					computeSemanticTokens(jp, q.query(), q.isNative()).forEach(tokensData::accept);
				}
				return super.visit(node);
			}
		};
	}
	
	public List<SemanticTokenData> computeSemanticTokens(IJavaProject jp, EmbeddedLanguageSnippet s, boolean isNative) {
		SemanticTokensDataProvider provider = isNative ? getSqlSemanticTokensProvider(jp)
				: (SpringProjectUtil.hasDependencyStartingWith(jp, "hibernate-core", null) ? hqlProvider
						: jpqlProvider);
		if (provider != null) {
			return provider.computeTokens(s.getText()).stream()
					.flatMap(td -> s.toJavaRanges(td.range()).stream().map(r -> new SemanticTokenData(r,
							td.type(), td.modifiers())))
					.toList();
		}
		return Collections.emptyList();
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return supportState.isEnabled() && (SpringProjectUtil.hasDependencyStartingWith(project, "spring-data-jpa", null)
				|| SpringProjectUtil.hasDependencyStartingWith(project, "spring-data-jdbc", null)
				|| SpringProjectUtil.hasDependencyStartingWith(project, "jakarta.persistence-api", null)
				|| SpringProjectUtil.hasDependencyStartingWith(project, "javax.persistence-api", null));
	}
	
	private SemanticTokensDataProvider getSqlSemanticTokensProvider(IJavaProject project) {
		if (SpringProjectUtil.hasDependencyStartingWith(project, "mysql-connector", null)) {
			return sqlTokenProviders.get(SqlType.MYSQL);
		} else if (SpringProjectUtil.hasDependencyStartingWith(project, "postgresql", null)) {
			return sqlTokenProviders.get(SqlType.POSTGRESQL);
		}
		return sqlTokenProviders.get(SqlType.MYSQL);
	}

}
