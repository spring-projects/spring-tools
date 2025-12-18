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

public class JpqlSemanticTokensTest {
	
	private JpqlSemanticTokens provider;
	
	@BeforeEach
	void setup() {
		provider = new JpqlSemanticTokens(Optional.empty(), Optional.of(Assertions::fail));
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
		String query = "SELECT g FROM G g GROUP BY g.name";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("g", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("G", "class"),
			new ExpectedSemanticToken("g", "variable"),
			new ExpectedSemanticToken("GROUP", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("g", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("name", "method")
		);
	}
	
	@Test
	void query_with_parameter() {
		String query = "SELECT f from Student f LEFT JOIN f.classTbls s WHERE s.ClassName = :className";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("f", "variable"),
			new ExpectedSemanticToken("from", "keyword"),
			new ExpectedSemanticToken("Student", "class"),
			new ExpectedSemanticToken("f", "variable"),
			new ExpectedSemanticToken("LEFT", "keyword"),
			new ExpectedSemanticToken("JOIN", "keyword"),
			new ExpectedSemanticToken("f", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("classTbls", "method"),
			new ExpectedSemanticToken("s", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("s", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("ClassName", "method"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("className", "parameter")
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
			new ExpectedSemanticToken("pets", "method"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("id", "method"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("id", "string"),
			new ExpectedSemanticToken("}", "operator")
		);
	}
	
	@Test
	void query_with_SPEL_Tokens() {
		provider = new JpqlSemanticTokens(Optional.of(new SpelSemanticTokens()));
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
			new ExpectedSemanticToken("pets", "method"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("owner", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("id", "method"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("id", "variable"),
			new ExpectedSemanticToken("}", "operator")
		);
	}

}
