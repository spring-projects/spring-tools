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

import static org.springframework.ide.vscode.languageserver.testharness.SemanticTokensAssert.assertTokens;
import static org.springframework.ide.vscode.languageserver.testharness.SemanticTokensAssert.ExpectedSemanticToken;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;

public class HqlSemanticTokensTest {
	
	private HqlSemanticTokens provider;
	
	@BeforeEach
	void setup() {
		provider = new HqlSemanticTokens(Optional.empty(), Optional.of(Assertions::fail));
	}
	
	@Test
	void simpleQuery_1() {
		String query = "SELECT owner FROM Owner owner";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Owner", "class"),
			new ExpectedSemanticToken("owner", "variable")
		);
	}

	@Test
	void query_with_conflicting_groupby() {
		String query = "SELECT g FROM Group g GROUP BY g.name";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("g", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Group", "class"),
			new ExpectedSemanticToken("g", "variable"),
			new ExpectedSemanticToken("GROUP", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("g", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("name", "property")
		);
	}
	
	@Test
	void query_with_parameter() {
		String query = "SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :lastName%";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("DISTINCT", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Owner", "class"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("left", "keyword"),
			new ExpectedSemanticToken("join", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("pets", "property"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("lastName", "property"),
			new ExpectedSemanticToken("LIKE", "keyword"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("lastName", "parameter"),
			new ExpectedSemanticToken("%", "operator")
		);
	}

	@Test
	void query_with_SPEL() {
		String query = "SELECT owner FROM Owner owner left join fetch owner.pets WHERE owner.id =:#{id}";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Owner", "class"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("left", "keyword"),
			new ExpectedSemanticToken("join", "keyword"),
			new ExpectedSemanticToken("fetch", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("pets", "property"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("id", "property"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("id", "string"),
			new ExpectedSemanticToken("}", "operator")
		);
	}

	@Test
	void query_with_SPEL_Tokens() {
		provider = new HqlSemanticTokens(Optional.of(new SpelSemanticTokens()));
		String query = "SELECT owner FROM Owner owner left join fetch owner.pets WHERE owner.id =:#{id}";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Owner", "class"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("left", "keyword"),
			new ExpectedSemanticToken("join", "keyword"),
			new ExpectedSemanticToken("fetch", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("pets", "property"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("id", "property"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("id", "variable"),
			new ExpectedSemanticToken("}", "operator")
		);
	}

	@Test
	void query_with_complex_SPEL_Tokens() {
		provider = new HqlSemanticTokens(Optional.of(new SpelSemanticTokens()));
		String query = "SELECT owner FROM Owner owner left join fetch owner.pets WHERE owner.id =:#{someBean.someProperty != null ? someBean.someProperty : 'default'}";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Owner", "class"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken("left", "keyword"),
			new ExpectedSemanticToken("join", "keyword"),
			new ExpectedSemanticToken("fetch", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("pets", "property"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("id", "property"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("someBean", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("someProperty", "property"),
			new ExpectedSemanticToken("!=", "operator"),
			new ExpectedSemanticToken("null", "keyword"),
			new ExpectedSemanticToken("?", "operator"),
			new ExpectedSemanticToken("someBean", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("someProperty", "property"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("'default'", "string"),
			new ExpectedSemanticToken("}", "operator")
		);
	}
	
	@Test
	void instatiotation_1() {
		provider = new HqlSemanticTokens(Optional.of(new SpelSemanticTokens()));
		String query = "SELECT new com.example.ls.issue.SampleTableSizePojo(t.schemaName, sum(t.tableSize) ) FROM MTables t GROUP BY t.schemaName ORDER BY sum(t.tableSize) DESC";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("new", "keyword"),
			new ExpectedSemanticToken("com.example.ls.issue.SampleTableSizePojo", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken("t", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("schemaName", "property"),
			new ExpectedSemanticToken(",", "operator"),
			new ExpectedSemanticToken("sum", "keyword"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken("t", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("tableSize", "property"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("MTables", "class"),
			new ExpectedSemanticToken("t", "variable"),
			new ExpectedSemanticToken("GROUP", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("t", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("schemaName", "property"),
			new ExpectedSemanticToken("ORDER", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("sum", "keyword"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken("t", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("tableSize", "property"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("DESC", "keyword")
		);
	}
	
	@Test
	void spelForEntityName() {
		String query = "from #{#entityName} e where g.order= (select min(sub.order) from #{#entityName} sub)";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("from", "keyword"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("#entityName", "string"),
			new ExpectedSemanticToken("}", "operator"),
			new ExpectedSemanticToken("e", "variable"),
			new ExpectedSemanticToken("where", "keyword"),
			new ExpectedSemanticToken("g", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("order", "property"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken("select", "keyword"),
			new ExpectedSemanticToken("min", "keyword"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken("sub", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("order", "property"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("from", "keyword"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("#entityName", "string"),
			new ExpectedSemanticToken("}", "operator"),
			new ExpectedSemanticToken("sub", "variable"),
			new ExpectedSemanticToken(")", "operator")
		);
	}
}
