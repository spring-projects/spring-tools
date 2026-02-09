/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.parser.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PostgreSqlQueryFormatterTest {

	private PostgreSqlQueryFormatter formatter;

	@BeforeEach
	void setup() {
		formatter = new PostgreSqlQueryFormatter();
	}

	@Test
	void simpleSelectFrom() {
		assertEquals("""
				SELECT *
				FROM employees""",
				formatter.format("SELECT * FROM employees"));
	}

	@Test
	void selectFromWhere() {
		assertEquals("""
				SELECT e.name,
				  e.salary
				FROM employees e
				WHERE e.active = true""",
				formatter.format("SELECT e.name, e.salary FROM employees e WHERE e.active = true"));
	}

	@Test
	void selectFromWhereWithAndOr() {
		assertEquals("""
				SELECT *
				FROM employees e
				WHERE e.department = 'IT'
				  AND e.salary > 50000
				  OR e.role = 'ADMIN'""",
				formatter.format("SELECT * FROM employees e WHERE e.department = 'IT' AND e.salary > 50000 OR e.role = 'ADMIN'"));
	}

	@Test
	void selectWithJoin() {
		assertEquals("""
				SELECT e.name,
				  d.name
				FROM employees e
				  JOIN departments d ON e.dept_id = d.id
				WHERE d.active = true""",
				formatter.format("SELECT e.name, d.name FROM employees e JOIN departments d ON e.dept_id = d.id WHERE d.active = true"));
	}

	@Test
	void selectWithMultipleJoins() {
		assertEquals("""
				SELECT o.id,
				  c.name,
				  i.product
				FROM orders o
				  JOIN customers c ON o.customer_id = c.id
				  LEFT JOIN order_items i ON o.id = i.order_id
				WHERE c.status = 'ACTIVE'
				  AND i.quantity > 0""",
				formatter.format("SELECT o.id, c.name, i.product FROM orders o JOIN customers c ON o.customer_id = c.id LEFT JOIN order_items i ON o.id = i.order_id WHERE c.status = 'ACTIVE' AND i.quantity > 0"));
	}

	@Test
	void selectWithGroupByHavingOrderBy() {
		assertEquals("""
				SELECT d.name,
				  COUNT(*)
				FROM employees e
				  JOIN departments d ON e.dept_id = d.id
				GROUP BY d.name
				HAVING COUNT(*) > 5
				ORDER BY d.name ASC""",
				formatter.format("SELECT d.name, COUNT(*) FROM employees e JOIN departments d ON e.dept_id = d.id GROUP BY d.name HAVING COUNT(*) > 5 ORDER BY d.name ASC"));
	}

	@Test
	void selectDistinct() {
		assertEquals("""
				SELECT DISTINCT department
				FROM employees""",
				formatter.format("SELECT DISTINCT department FROM employees"));
	}

	@Test
	void selectWithLimitOffset() {
		assertEquals("""
				SELECT *
				FROM employees
				ORDER BY name
				LIMIT 10
				OFFSET 20""",
				formatter.format("SELECT * FROM employees ORDER BY name LIMIT 10 OFFSET 20"));
	}

	@Test
	void deleteStatement() {
		assertEquals("""
				DELETE FROM employees e
				WHERE e.active = false""",
				formatter.format("DELETE FROM employees e WHERE e.active = false"));
	}

	@Test
	void updateStatement() {
		assertEquals("""
				UPDATE employees e SET e.salary = e.salary * 1.1
				WHERE e.department = 'IT'""",
				formatter.format("UPDATE employees e SET e.salary = e.salary * 1.1 WHERE e.department = 'IT'"));
	}

	@Test
	void insertStatement() {
		assertEquals("""
				INSERT INTO employees(name, salary) VALUES('John', 50000)""",
				formatter.format("INSERT INTO employees(name, salary) VALUES('John', 50000)"));
	}

	@Test
	void queryWithNamedParameter() {
		assertEquals("""
				SELECT *
				FROM employees e
				WHERE e.department = :dept""",
				formatter.format("SELECT * FROM employees e WHERE e.department = :dept"));
	}

	@Test
	void queryWithPositionalParameter() {
		assertEquals("""
				SELECT *
				FROM employees e
				WHERE e.id = ?1
				  AND e.name = ?2""",
				formatter.format("SELECT * FROM employees e WHERE e.id = ?1 AND e.name = ?2"));
	}

	@Test
	void nullInputReturnsNull() {
		assertEquals(null, formatter.format(null));
	}

	@Test
	void emptyInputReturnsEmpty() {
		assertEquals("", formatter.format(""));
	}

	@Test
	void blankInputReturnsBlank() {
		assertEquals("   ", formatter.format("   "));
	}

	@Test
	void queryWithSubquery() {
		assertEquals("""
				SELECT *
				FROM employees e
				WHERE e.salary > (
				  SELECT AVG(e2.salary)
				  FROM employees e2)""",
				formatter.format("SELECT * FROM employees e WHERE e.salary > (SELECT AVG(e2.salary) FROM employees e2)"));
	}

	@Test
	void queryWithInExpression() {
		assertEquals("""
				SELECT *
				FROM employees e
				WHERE e.department IN ('IT', 'HR', 'SALES')""",
				formatter.format("SELECT * FROM employees e WHERE e.department IN ('IT', 'HR', 'SALES')"));
	}

	@Test
	void queryWithBetween() {
		assertEquals("""
				SELECT *
				FROM employees e
				WHERE e.salary BETWEEN 50000 AND 100000""",
				formatter.format("SELECT * FROM employees e WHERE e.salary BETWEEN 50000 AND 100000"));
	}

	@Test
	void queryWithIsNull() {
		assertEquals("""
				SELECT *
				FROM employees e
				WHERE e.manager IS NULL
				  AND e.active = true""",
				formatter.format("SELECT * FROM employees e WHERE e.manager IS NULL AND e.active = true"));
	}

	@Test
	void queryPreservesKeywordCase() {
		assertEquals("""
				select *
				from employees
				where active = true""",
				formatter.format("select * from employees where active = true"));
	}

	@Test
	void queryWithCrossJoin() {
		assertEquals("""
				SELECT *
				FROM employees e
				  CROSS JOIN departments d""",
				formatter.format("SELECT * FROM employees e CROSS JOIN departments d"));
	}

	@Test
	void queryWithReturning() {
		assertEquals("""
				DELETE FROM employees e
				WHERE e.active = false
				RETURNING *""",
				formatter.format("DELETE FROM employees e WHERE e.active = false RETURNING *"));
	}

}
