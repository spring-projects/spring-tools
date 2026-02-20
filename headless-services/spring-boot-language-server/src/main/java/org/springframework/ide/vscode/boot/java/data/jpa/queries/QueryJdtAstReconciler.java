/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryVisitorUtils.EmbeddedQueryExpression;
import org.springframework.ide.vscode.boot.java.embedded.lang.AntlrReconcilerWithSpel;
import org.springframework.ide.vscode.boot.java.handlers.Reconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingContext;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.spel.SpelReconciler;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.parser.mysql.MySqlLexer;
import org.springframework.ide.vscode.parser.mysql.MySqlParser;
import org.springframework.ide.vscode.parser.postgresql.PostgreSqlLexer;
import org.springframework.ide.vscode.parser.postgresql.PostgreSqlParser;

public class QueryJdtAstReconciler implements JdtAstReconciler {
	
	private final Reconciler hqlReconciler;
	private final Reconciler jpqlReconciler;
	private final Map<SqlType, Reconciler> sqlReconcilers;

	
	public QueryJdtAstReconciler(Reconciler hqlReconciler, Reconciler jpqlReconciler,
			Optional<SpelReconciler> spelReconciler) {
		this.hqlReconciler = hqlReconciler;
		this.jpqlReconciler = jpqlReconciler;
		
		this.sqlReconcilers = new LinkedHashMap<>();
		this.sqlReconcilers.put(SqlType.MYSQL, new AntlrReconcilerWithSpel("MySQL", MySqlParser.class, MySqlLexer.class, "sqlStatements", QueryProblemType.SQL_SYNTAX, spelReconciler, MySqlLexer.SPEL));
		this.sqlReconcilers.put(SqlType.POSTGRESQL, new AntlrReconcilerWithSpel("PostgreSQL", PostgreSqlParser.class, PostgreSqlLexer.class, "root", QueryProblemType.SQL_SYNTAX, spelReconciler, PostgreSqlLexer.SPEL));
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) throws RequiredCompleteAstException {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
		return new ASTVisitor() {

			@Override
			public boolean visit(NormalAnnotation node) {
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, node);
				if (q != null) {
					Optional<Reconciler> reconcilerOpt = q.isNative() ? getSqlReconciler(project) : Optional.of(getQueryReconciler(project));
					reconcilerOpt.ifPresent(r -> r.reconcile(q.query().getText(), q.query()::toSingleJavaRange, context.getProblemCollector()));
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, node);
				if (q != null) {
					Optional<Reconciler> reconcilerOpt = q.isNative() ? getSqlReconciler(project) : Optional.of(getQueryReconciler(project));
					reconcilerOpt.ifPresent(r -> r.reconcile(q.query().getText(), q.query()::toSingleJavaRange, context.getProblemCollector()));
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(MethodInvocation node) {
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(node);
				if (q != null) {
					getQueryReconciler(project).reconcile(q.query().getText(), q.query()::toSingleJavaRange, context.getProblemCollector());
				}
				return super.visit(node);
			}
			
		};
	}
	
	/*
	 * Gets either HQL or JPQL reconciler
	 */
	private Reconciler getQueryReconciler(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "hibernate-core", null) ? hqlReconciler : jpqlReconciler;
	}
	
//	public static void reconcileExpression(Reconciler reconciler, Expression valueExp, IProblemCollector problemCollector) {
//		String query = null;
//		int offset = 0;
//		if (valueExp instanceof StringLiteral sl) {
//			query = sl.getEscapedValue();
//			query = query.substring(1, query.length() - 1);
//			offset = sl.getStartPosition() + 1; // +1 to skip over opening "
//		} else if (valueExp instanceof TextBlock tb) {
//			query = tb.getEscapedValue();
//			query = query.substring(3, query.length() - 3);
//			offset = tb.getStartPosition() + 3; // +3 to skip over opening """ 
//		}
//		
//		if (query != null) {
//			reconciler.reconcile(query, offset, problemCollector);
//		}
//	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "spring-data-jpa", null)
				|| SpringProjectUtil.hasDependencyStartingWith(project, "spring-data-jdbc", null);
	}

	@Override
	public ProblemType getProblemType() {
		return QueryProblemType.JPQL_SYNTAX;
	}
	
	private Optional<Reconciler> getSqlReconciler(IJavaProject project) {
		if (SpringProjectUtil.hasDependencyStartingWith(project, "mysql-connector", null)) {
			return Optional.of(sqlReconcilers.get(SqlType.MYSQL));
		} else if (SpringProjectUtil.hasDependencyStartingWith(project, "postgresql", null)) {
			return Optional.of(sqlReconcilers.get(SqlType.POSTGRESQL));
		}
		return Optional.empty();
	}

}
