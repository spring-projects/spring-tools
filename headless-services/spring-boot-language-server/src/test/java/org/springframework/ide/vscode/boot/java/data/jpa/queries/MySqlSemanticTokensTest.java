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

public class MySqlSemanticTokensTest {
	
	private MySqlSemanticTokens provider;
	
	@BeforeEach
	void setup() {
		provider = new MySqlSemanticTokens(Optional.of(new SpelSemanticTokens()), Optional.of(Assertions::fail));
	}
	
	@Test
	void simple() {
		String query = "SELECT * from Document document WHERE document.id=fn_module_candidates()";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("from", "keyword"),
			new ExpectedSemanticToken("Document", "variable"),
			new ExpectedSemanticToken("document", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("document", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("id", "property"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken("fn_module_candidates", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken(")", "operator")
		);
	}
	
	@Test
	void parametersInQuery() {
		String query = "DELETE FROM component_document WHERE item_document_id = :itemDocumentId";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("DELETE", "keyword"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("component_document", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("item_document_id", "variable"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("itemDocumentId", "parameter")
		);
	}
	
	@Test
	void spelInQuery() {
		String query = "DELETE FROM component_document WHERE item_document_id = :#{someBean}";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("DELETE", "keyword"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("component_document", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("item_document_id", "variable"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("someBean", "variable"),
			new ExpectedSemanticToken("}", "operator")
		);
	}
	
	@Test
	void complexSpelInQuery() {
		String query = "DELETE FROM component_document WHERE item_document_id = :#{someBean.someProperty != null ? someBean.someProperty : 'default'}";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("DELETE", "keyword"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("component_document", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("item_document_id", "variable"),
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
	void fromIsIdentifierInQuery() {
		String query = "SELECT inv FROM service WHERE inv.issued_date >= :from AND inv.issued_date <= :to GROUP BY inv.id";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("inv", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("service", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("inv", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("issued_date", "property"),
			new ExpectedSemanticToken(">", "operator"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("from", "parameter"),
			new ExpectedSemanticToken("AND", "keyword"),
			new ExpectedSemanticToken("inv", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("issued_date", "property"),
			new ExpectedSemanticToken("<", "operator"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("to", "parameter"),
			new ExpectedSemanticToken("GROUP", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("inv", "variable"),
			new ExpectedSemanticToken(".", "operator"),
			new ExpectedSemanticToken("id", "property")
		);
	}

	@Test
	void parameterIn() {
		String query = "DELETE FROM t WHERE ID IN :ids";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("DELETE", "keyword"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("t", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("ID", "variable"),
			new ExpectedSemanticToken("IN", "keyword"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("ids", "parameter")
		);
	}

}
