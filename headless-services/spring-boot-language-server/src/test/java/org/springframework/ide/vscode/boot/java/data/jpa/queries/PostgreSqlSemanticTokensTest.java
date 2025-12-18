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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ide.vscode.languageserver.testharness.SemanticTokensAssert.assertTokens;
import static org.springframework.ide.vscode.languageserver.testharness.SemanticTokensAssert.ExpectedSemanticToken;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;

public class PostgreSqlSemanticTokensTest {

	private PostgreSqlSemanticTokens provider;
	
	@BeforeEach
	void setup() {
		provider = new PostgreSqlSemanticTokens(Optional.of(new SpelSemanticTokens()), Optional.of(Assertions::fail));
	}
	
	@Test
	void simple() {
		String query = "SELECT * from fn_module_candidates";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("from", "keyword"),
			new ExpectedSemanticToken("fn_module_candidates", "variable")
		);
	}
	
	@Test
	void adv_1() {
		String query = """
				DELETE FROM Represenation representation
				WHERE representation.project_id=?1 
				AND NOT EXISTS (
					SELECT * FROM Document document 
					WHERE document.project_id=?1 
					AND json_path_exists(document.content::jsonb, ('strict $.content.**.id ? (@ == "\\' || representation.targetobjectid || \\'")')::jsonpath)
				)
				""";
		List<SemanticTokenData> tokens = provider.computeTokens(query);
		// Spot check key tokens instead of all 43
		assertThat(tokens.get(10).type()).isEqualTo("parameter"); // 1 from ?1
		assertThat(tokens.get(26).type()).isEqualTo("parameter"); // 1 from ?1
		assertThat(tokens.get(28).type()).isEqualTo("method"); // json_path_exists
		assertThat(tokens.get(33).type()).isEqualTo("operator"); // ::
		assertThat(tokens.get(34).type()).isEqualTo("type"); // jsonb
		assertThat(tokens.get(37).type()).isEqualTo("string"); // 'strict $.content.**.id ? (@ == "\\' || representation.targetobjectid || \\'")'
		assertThat(tokens.get(39).type()).isEqualTo("operator"); // ::
		assertThat(tokens.get(40).type()).isEqualTo("type"); // jsonpath
		assertThat(tokens.size()).isEqualTo(43);
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
	void semiColonAtEnd() {
		String query = " select count(*) from anecdote where anecdote_id=:anecdote ; ";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("select", "keyword"),
			new ExpectedSemanticToken("count", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("from", "keyword"),
			new ExpectedSemanticToken("anecdote", "variable"),
			new ExpectedSemanticToken("where", "keyword"),
			new ExpectedSemanticToken("anecdote_id", "variable"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("anecdote", "parameter"),
			new ExpectedSemanticToken(";", "operator")
		);
	}
	
	@Test
	void parameterInLimitClause_1() {
		String query = "SELECT * FROM cards ORDER BY random() LIMIT ?2";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("cards", "variable"),
			new ExpectedSemanticToken("ORDER", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("random", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("LIMIT", "keyword"),
			new ExpectedSemanticToken("?", "operator"),
			new ExpectedSemanticToken("2", "parameter")
		);
	}

	@Test
	void parameterInLimitClause_2() {
		String query = "SELECT * FROM cards ORDER BY random() LIMIT ?2";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("cards", "variable"),
			new ExpectedSemanticToken("ORDER", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("random", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("LIMIT", "keyword"),
			new ExpectedSemanticToken("?", "operator"),
			new ExpectedSemanticToken("2", "parameter")
		);
	}
	
	@Test
	void parameterInLimitClause_3() {
		String query = "SELECT * FROM cards ORDER BY random() LIMIT :#{qq}";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("cards", "variable"),
			new ExpectedSemanticToken("ORDER", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("random", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("LIMIT", "keyword"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("#{", "operator"),
			new ExpectedSemanticToken("qq", "variable"),
			new ExpectedSemanticToken("}", "operator")
		);
	}

	@Test
	void parameterInLimitClause_4() {
		String query = "SELECT * FROM cards ORDER BY random() LIMIT :limit";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("cards", "variable"),
			new ExpectedSemanticToken("ORDER", "keyword"),
			new ExpectedSemanticToken("BY", "keyword"),
			new ExpectedSemanticToken("random", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken("LIMIT", "keyword"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("limit", "parameter")
		);
	}
	
	@Test
	void notInInsideWhereClausePredicate() {
		List<SemanticTokenData> tokens = provider.computeTokens("delete from SAMPLE_TABLE where id not in (select top 1 id from SAMPLE_TABLE order by TABLE_NAME desc)");
		assertThat(tokens.size()).isEqualTo(19);
	}

	@Test
	void keywordAsIdentifier() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT SCHEMA_NAME, TABLE_NAME, VERSION from SAMPLE_TABLE");
		assertThat(tokens.size()).isEqualTo(8);
	}
	
	@Test
	void topInSelectClause() {
		String query = "select top 1 * from SAMPLE_TABLE where SCHEMA_NAME = ?1";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("select", "keyword"),
			new ExpectedSemanticToken("top", "keyword"),
			new ExpectedSemanticToken("1", "number"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("from", "keyword"),
			new ExpectedSemanticToken("SAMPLE_TABLE", "variable"),
			new ExpectedSemanticToken("where", "keyword"),
			new ExpectedSemanticToken("SCHEMA_NAME", "variable"),
			new ExpectedSemanticToken("=", "operator"),
			new ExpectedSemanticToken("?", "operator"),
			new ExpectedSemanticToken("1", "parameter")
		);
	}
	
	@Test
	void over_clause_1() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT depname, empno, salary, avg(salary) OVER (PARTITION BY depname) FROM empsalary;\n"
				+ "");
		assertThat(tokens.size()).isEqualTo(20);
	}

	@Test
	void over_clause_2() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
				SELECT depname, empno, salary,
				       rank() OVER (PARTITION BY depname ORDER BY salary DESC)
				FROM empsalary;
				""");
		assertThat(tokens.size()).isEqualTo(23);
	}
	
	@Test
	void over_clause_3() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT salary, sum(salary) OVER () FROM empsalary;"
				+ "");
		assertThat(tokens.size()).isEqualTo(13);
	}

	@Test
	void over_clause_4() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT salary, sum(salary) OVER (ORDER BY salary) FROM empsalary;"
				+ "");
		assertThat(tokens.size()).isEqualTo(16);
	}

	@Test
	void over_clause_5() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
				SELECT depname, empno, salary, enroll_date
				FROM
				  (SELECT depname, empno, salary, enroll_date,
				          rank() OVER (PARTITION BY depname ORDER BY salary DESC, empno) AS pos
				     FROM empsalary
				  ) AS ss
				WHERE pos < 3;
				""");
		assertThat(tokens.size()).isEqualTo(46);
	}

	@Test
	void over_clause_6() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
				SELECT sum(salary) OVER w, avg(salary) OVER w
				  FROM empsalary
				  WINDOW w AS (PARTITION BY depname ORDER BY salary DESC);
  				""");
		assertThat(tokens.size()).isEqualTo(29);
	}

	@Test
	void over_clause_7() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
            WITH cte AS (
                SELECT
                    q.*,
                    ROW_NUMBER() OVER (PARTITION BY q.database_id ORDER BY q.order_id) AS rn
                FROM
                    SAMPLE_TABLE AS q
                WHERE
                    q.status IN (0, 1, 5, 10)
            )
            SELECT *
            FROM cte
            WHERE
                (rn = 1 OR status = 10)
                AND (scenario = 11 OR scenario = 8)
            ORDER BY status DESC
            """);
		assertThat(tokens.size()).isEqualTo(74);
	}
	
	@Test
	void collate_1() {
		String query = """
				SELECT DISTINCT test COLLATE "numeric" FROM Test
				""";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("DISTINCT", "keyword"),
			new ExpectedSemanticToken("test", "variable"),
			new ExpectedSemanticToken("COLLATE", "keyword"),
			new ExpectedSemanticToken("\"numeric\"", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Test", "variable")
		);
	}

	@Test
	void collate_2() {
		String query = """
				SELECT DISTINCT test COLLATE \"numeric\" FROM Test
				""";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("DISTINCT", "keyword"),
			new ExpectedSemanticToken("test", "variable"),
			new ExpectedSemanticToken("COLLATE", "keyword"),
			new ExpectedSemanticToken("\"numeric\"", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("Test", "variable")
		);
	}
	
	@Test
	void collate_3() {
		String query = """
				SELECT a COLLATE "de_DE" < b FROM test1
				""";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("a", "variable"),
			new ExpectedSemanticToken("COLLATE", "keyword"),
			new ExpectedSemanticToken("\"de_DE\"", "variable"),
			new ExpectedSemanticToken("<", "operator"),
			new ExpectedSemanticToken("b", "variable"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("test1", "variable")
		);
	}
	
	@Test
	void placeholder() {
		String query = "CALL {h-schema}calcRequest( :i_session_id );";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("CALL", "keyword"),
			new ExpectedSemanticToken("{h-schema}", "parameter"),
			new ExpectedSemanticToken("calcRequest", "method"),
			new ExpectedSemanticToken("(", "operator"),
			new ExpectedSemanticToken(":", "operator"),
			new ExpectedSemanticToken("i_session_id", "parameter"),
			new ExpectedSemanticToken(")", "operator"),
			new ExpectedSemanticToken(";", "operator")
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
	
	@Test
	void jsob_exists() {
		String query = """
				SELECT * FROM some_table WHERE jsonb_field ? 'some_key'
				""";
		assertTokens(query, provider.computeTokens(query),
			new ExpectedSemanticToken("SELECT", "keyword"),
			new ExpectedSemanticToken("*", "operator"),
			new ExpectedSemanticToken("FROM", "keyword"),
			new ExpectedSemanticToken("some_table", "variable"),
			new ExpectedSemanticToken("WHERE", "keyword"),
			new ExpectedSemanticToken("jsonb_field", "variable"),
			new ExpectedSemanticToken("?", "operator"),
			new ExpectedSemanticToken("'some_key'", "string")
		);
	}

	
}
