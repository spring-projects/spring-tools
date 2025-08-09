/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
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
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT * from fn_module_candidates");
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0]));
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 8, "operator", new String[0]));
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(9, 13, "keyword", new String[0]));
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 34, "variable", new String[0]));
		
		assertThat(tokens.size()).isEqualTo(4);
	}
	
	@Test
	void adv_1() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
				DELETE FROM Represenation representation
				WHERE representation.project_id=?1 
				AND NOT EXISTS (
					SELECT * FROM Document document 
					WHERE document.project_id=?1 
					AND json_path_exists(document.content::jsonb, ('strict $.content.**.id ? (@ == "\\' || representation.targetobjectid || \\'")')::jsonpath)
				)
				""");
		assertThat(tokens.size()).isEqualTo(43);
		
		assertThat(tokens.get(10)).isEqualTo(new SemanticTokenData(74, 75, "parameter", new String[0])); // 1 from ?1
		assertThat(tokens.get(26)).isEqualTo(new SemanticTokenData(154, 155, "parameter", new String[0])); // 1 from ?1
		assertThat(tokens.get(28)).isEqualTo(new SemanticTokenData(161, 177, "method", new String[0])); // json_path_exists
		assertThat(tokens.get(33)).isEqualTo(new SemanticTokenData(194, 196, "operator", new String[0])); // ::
		assertThat(tokens.get(34)).isEqualTo(new SemanticTokenData(196, 201, "type", new String[0])); // jsonb
		assertThat(tokens.get(37)).isEqualTo(new SemanticTokenData(204, 281, "string", new String[0])); // 'strict $.content.**.id ? (@ == "\\' || representation.targetobjectid || \\'")'
		assertThat(tokens.get(39)).isEqualTo(new SemanticTokenData(282, 284, "operator", new String[0])); // ::
		assertThat(tokens.get(40)).isEqualTo(new SemanticTokenData(284, 292, "type", new String[0])); // jsonpath
	}
	
	@Test
	void spelInQuery() {
		List<SemanticTokenData> tokens = provider.computeTokens("DELETE FROM component_document WHERE item_document_id = :#{someBean}");
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0]));
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 11, "keyword", new String[0]));
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(12, 30, "variable", new String[0]));
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(31, 36, "keyword", new String[0]));
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(37, 53, "variable", new String[0]));
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(54, 55, "operator", new String[0]));
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(56, 57, "operator", new String[0]));
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(57, 59, "operator", new String[0]));
		assertThat(tokens.get(8)).isEqualTo(new SemanticTokenData(59, 67, "variable", new String[0]));
		assertThat(tokens.get(9)).isEqualTo(new SemanticTokenData(67, 68, "operator", new String[0]));
		
		assertThat(tokens.size()).isEqualTo(10);
	}
	
	@Test
	void semiColonAtEnd() {
		List<SemanticTokenData> tokens = provider.computeTokens(" select count(*) from anecdote where anecdote_id=:anecdote ; ");
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(1, 7, "keyword", new String[0])); // select
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(8, 13, "method", new String[0])); // count
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(13, 14, "operator", new String[0])); // (
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 15, "operator", new String[0])); // *
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(15, 16, "operator", new String[0])); // )
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(17, 21, "keyword", new String[0])); // from 
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(22, 30, "variable", new String[0])); // anecdote
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(31, 36, "keyword", new String[0])); // where
		assertThat(tokens.get(8)).isEqualTo(new SemanticTokenData(37, 48, "variable", new String[0])); // anecdote_id
		assertThat(tokens.get(9)).isEqualTo(new SemanticTokenData(48, 49, "operator", new String[0])); // =
		assertThat(tokens.get(10)).isEqualTo(new SemanticTokenData(49, 50, "operator", new String[0])); // :
		assertThat(tokens.get(11)).isEqualTo(new SemanticTokenData(50, 58, "parameter", new String[0])); // anecdote
		assertThat(tokens.get(12)).isEqualTo(new SemanticTokenData(59, 60, "operator", new String[0])); // ;
		
		assertThat(tokens.size()).isEqualTo(13);
	}
	
	@Test
	void parameterInLimitClause_1() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT * FROM cards ORDER BY random() LIMIT ?2");
		assertThat(tokens.size()).isEqualTo(12);
		
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0]));
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 8, "operator", new String[0]));
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(9, 13, "keyword", new String[0]));
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 19, "variable", new String[0]));
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(20, 25, "keyword", new String[0]));
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(26, 28, "keyword", new String[0]));
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(29, 35, "method", new String[0]));
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(35, 36, "operator", new String[0]));
		assertThat(tokens.get(8)).isEqualTo(new SemanticTokenData(36, 37, "operator", new String[0]));
		assertThat(tokens.get(9)).isEqualTo(new SemanticTokenData(38, 43, "keyword", new String[0]));
		assertThat(tokens.get(10)).isEqualTo(new SemanticTokenData(44, 45, "operator", new String[0]));
		assertThat(tokens.get(11)).isEqualTo(new SemanticTokenData(45, 46, "parameter", new String[0]));
	}

	@Test
	void parameterInLimitClause_2() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT * FROM cards ORDER BY random() LIMIT ?2");
		assertThat(tokens.size()).isEqualTo(12);
		
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0]));
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 8, "operator", new String[0]));
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(9, 13, "keyword", new String[0]));
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 19, "variable", new String[0]));
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(20, 25, "keyword", new String[0]));
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(26, 28, "keyword", new String[0]));
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(29, 35, "method", new String[0]));
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(35, 36, "operator", new String[0]));
		assertThat(tokens.get(8)).isEqualTo(new SemanticTokenData(36, 37, "operator", new String[0]));
		assertThat(tokens.get(9)).isEqualTo(new SemanticTokenData(38, 43, "keyword", new String[0]));
		assertThat(tokens.get(10)).isEqualTo(new SemanticTokenData(44, 45, "operator", new String[0]));
		assertThat(tokens.get(11)).isEqualTo(new SemanticTokenData(45, 46, "parameter", new String[0]));
	}
	
	@Test
	void parameterInLimitClause_3() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT * FROM cards ORDER BY random() LIMIT :#{qq}");
		assertThat(tokens.size()).isEqualTo(14);
		
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0]));
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 8, "operator", new String[0]));
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(9, 13, "keyword", new String[0]));
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 19, "variable", new String[0]));
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(20, 25, "keyword", new String[0]));
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(26, 28, "keyword", new String[0]));
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(29, 35, "method", new String[0]));
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(35, 36, "operator", new String[0]));
		assertThat(tokens.get(8)).isEqualTo(new SemanticTokenData(36, 37, "operator", new String[0]));
		assertThat(tokens.get(9)).isEqualTo(new SemanticTokenData(38, 43, "keyword", new String[0]));
		assertThat(tokens.get(10)).isEqualTo(new SemanticTokenData(44, 45, "operator", new String[0]));
		assertThat(tokens.get(11)).isEqualTo(new SemanticTokenData(45, 47, "operator", new String[0]));
		assertThat(tokens.get(12)).isEqualTo(new SemanticTokenData(47, 49, "variable", new String[0]));
		assertThat(tokens.get(13)).isEqualTo(new SemanticTokenData(49, 50, "operator", new String[0]));
	}

	@Test
	void parameterInLimitClause_4() {
		List<SemanticTokenData> tokens = provider.computeTokens("SELECT * FROM cards ORDER BY random() LIMIT :limit");
		assertThat(tokens.size()).isEqualTo(12);
		
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0]));
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 8, "operator", new String[0]));
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(9, 13, "keyword", new String[0]));
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 19, "variable", new String[0]));
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(20, 25, "keyword", new String[0]));
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(26, 28, "keyword", new String[0]));
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(29, 35, "method", new String[0]));
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(35, 36, "operator", new String[0]));
		assertThat(tokens.get(8)).isEqualTo(new SemanticTokenData(36, 37, "operator", new String[0]));
		assertThat(tokens.get(9)).isEqualTo(new SemanticTokenData(38, 43, "keyword", new String[0]));
		assertThat(tokens.get(10)).isEqualTo(new SemanticTokenData(44, 45, "operator", new String[0]));
		assertThat(tokens.get(11)).isEqualTo(new SemanticTokenData(45, 50, "parameter", new String[0]));
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
		List<SemanticTokenData> tokens = provider.computeTokens("select top 1 * from SAMPLE_TABLE where SCHEMA_NAME = ?1");
		assertThat(tokens.size()).isEqualTo(11);
		
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0]));
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 10, "keyword", new String[0]));
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(11, 12, "number", new String[0]));
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(13, 14, "operator", new String[0]));
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(15, 19, "keyword", new String[0]));
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(20, 32, "variable", new String[0]));
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(33, 38, "keyword", new String[0]));
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(39, 50, "variable", new String[0]));
		assertThat(tokens.get(8)).isEqualTo(new SemanticTokenData(51, 52, "operator", new String[0]));
		assertThat(tokens.get(9)).isEqualTo(new SemanticTokenData(53, 54, "operator", new String[0]));
		assertThat(tokens.get(10)).isEqualTo(new SemanticTokenData(54, 55, "parameter", new String[0]));
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
		List<SemanticTokenData> tokens = provider.computeTokens("""
				SELECT DISTINCT test COLLATE "numeric" FROM Test
				""");
		assertThat(tokens.size()).isEqualTo(7);
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0])); // SELECT
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 15, "keyword", new String[0])); // DISTICT
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(16, 20, "variable", new String[0])); // test
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(21, 28, "keyword", new String[0])); // COLLATE
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(29, 38, "variable", new String[0])); // "numeric"
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(39, 43, "keyword", new String[0])); // FROM
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(44, 48, "variable", new String[0])); // Test
	}

	@Test
	void collate_2() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
				SELECT DISTINCT test COLLATE \"numeric\" FROM Test
				""");
		assertThat(tokens.size()).isEqualTo(7);
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0])); // SELECT
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 15, "keyword", new String[0])); // DISTICT
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(16, 20, "variable", new String[0])); // test
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(21, 28, "keyword", new String[0])); // COLLATE
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(29, 38, "variable", new String[0])); // \"numeric\"
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(39, 43, "keyword", new String[0])); // FROM
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(44, 48, "variable", new String[0])); // Test
	}
	
	@Test
	void collate_3() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
				SELECT a COLLATE "de_DE" < b FROM test1
				""");
		assertThat(tokens.size()).isEqualTo(8);
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0])); // SELECT
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 8, "variable", new String[0])); // a
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(9, 16, "keyword", new String[0])); // COLLATE
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(17, 24, "variable", new String[0])); // "de_DE"
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(25, 26, "operator", new String[0])); // <
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(27, 28, "variable", new String[0])); // b
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(29, 33, "keyword", new String[0])); // FROM
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(34, 39, "variable", new String[0])); // test1
	}
	
	@Test
	void placeholder() {
		List<SemanticTokenData> tokens = provider.computeTokens("CALL {h-schema}calcRequest( :i_session_id );");
		assertThat(tokens.size()).isEqualTo(8);
		
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 4, "keyword", new String[0])); // CALL
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(5, 15, "parameter", new String[0])); // {h-schema} 
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(15, 26, "method", new String[0])); // calcRequest
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(26, 27, "operator", new String[0])); // (
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(28, 29, "operator", new String[0])); // :
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(29, 41, "parameter", new String[0])); // i_session_id
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(42, 43, "operator", new String[0])); // )
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(43, 44, "operator", new String[0])); // ;
	}
	
	@Test
	void parameterIn() {
		List<SemanticTokenData> tokens = provider.computeTokens("DELETE FROM t WHERE ID IN :ids");
		assertThat(tokens.size()).isEqualTo(8);
		
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0])); // DELETE
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 11, "keyword", new String[0])); // FROM 
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(12, 13, "variable", new String[0])); // t
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 19, "keyword", new String[0])); // WHERE
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(20, 22, "variable", new String[0])); // ID
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(23, 25, "keyword", new String[0])); // IN
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(26, 27, "operator", new String[0])); // :
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(27, 30, "parameter", new String[0])); // ids
	}
	
	@Test
	void jsob_exists() {
		List<SemanticTokenData> tokens = provider.computeTokens("""
				SELECT * FROM some_table WHERE jsonb_field ? 'some_key'
				""");
		assertThat(tokens.size()).isEqualTo(8);
		assertThat(tokens.get(0)).isEqualTo(new SemanticTokenData(0, 6, "keyword", new String[0])); // SELECT
		assertThat(tokens.get(1)).isEqualTo(new SemanticTokenData(7, 8, "operator", new String[0])); // *
		assertThat(tokens.get(2)).isEqualTo(new SemanticTokenData(9, 13, "keyword", new String[0])); // FROM
		assertThat(tokens.get(3)).isEqualTo(new SemanticTokenData(14, 24, "variable", new String[0])); // some_table
		assertThat(tokens.get(4)).isEqualTo(new SemanticTokenData(25, 30, "keyword", new String[0])); // WHERE
		assertThat(tokens.get(5)).isEqualTo(new SemanticTokenData(31, 42, "variable", new String[0])); // jsonb_field
		assertThat(tokens.get(6)).isEqualTo(new SemanticTokenData(43, 44, "operator", new String[0])); // ?
		assertThat(tokens.get(7)).isEqualTo(new SemanticTokenData(45, 55, "string", new String[0])); // 'some_key'
	}

	
}
