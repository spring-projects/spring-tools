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
package org.springframework.ide.vscode.parser.jpql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JpqlQueryFormatterTest {

	private JpqlQueryFormatter formatter;

	@BeforeEach
	void setup() {
		formatter = new JpqlQueryFormatter();
	}

	@Test
	void simpleSelectFrom() {
		assertEquals("""
				SELECT e
				FROM Employee e""",
				formatter.format("SELECT e FROM Employee e"));
	}

	@Test
	void selectFromWhere() {
		assertEquals("""
				SELECT e
				FROM Employee e
				WHERE e.active = true""",
				formatter.format("SELECT e FROM Employee e WHERE e.active = true"));
	}

	@Test
	void selectFromWhereWithAndOr() {
		assertEquals("""
				SELECT e
				FROM Employee e
				WHERE e.department = :dept
				  AND e.salary > :minSalary
				  OR e.role = 'ADMIN'""",
				formatter.format("SELECT e FROM Employee e WHERE e.department = :dept AND e.salary > :minSalary OR e.role = 'ADMIN'"));
	}

	@Test
	void selectWithJoin() {
		assertEquals("""
				SELECT e
				FROM Employee e
				  JOIN e.department d
				WHERE d.name = :deptName""",
				formatter.format("SELECT e FROM Employee e JOIN e.department d WHERE d.name = :deptName"));
	}

	@Test
	void selectWithMultipleJoins() {
		assertEquals("""
				SELECT o
				FROM Order o
				  JOIN o.customer c
				  LEFT JOIN o.items i
				WHERE c.status = 'ACTIVE'
				  AND i.quantity > :minQty""",
				formatter.format("SELECT o FROM Order o JOIN o.customer c LEFT JOIN o.items i WHERE c.status = 'ACTIVE' AND i.quantity > :minQty"));
	}

	@Test
	void selectWithFetchJoin() {
		assertEquals("""
				SELECT owner
				FROM Owner owner
				  LEFT JOIN FETCH owner.pets
				WHERE owner.id = :id""",
				formatter.format("SELECT owner FROM Owner owner LEFT JOIN FETCH owner.pets WHERE owner.id = :id"));
	}

	@Test
	void selectWithGroupByHavingOrderBy() {
		assertEquals("""
				SELECT d.name, COUNT(e)
				FROM Employee e
				  JOIN e.department d
				GROUP BY d.name
				HAVING COUNT(e) > 5
				ORDER BY d.name ASC""",
				formatter.format("SELECT d.name, COUNT(e) FROM Employee e JOIN e.department d GROUP BY d.name HAVING COUNT(e) > 5 ORDER BY d.name ASC"));
	}

	@Test
	void selectDistinct() {
		assertEquals("""
				SELECT DISTINCT e.department
				FROM Employee e""",
				formatter.format("SELECT DISTINCT e.department FROM Employee e"));
	}

	@Test
	void deleteStatement() {
		assertEquals("""
				DELETE FROM Employee e
				WHERE e.active = false""",
				formatter.format("DELETE FROM Employee e WHERE e.active = false"));
	}

	@Test
	void updateStatement() {
		assertEquals("""
				UPDATE Employee e SET e.salary = e.salary * 1.1
				WHERE e.department = :dept""",
				formatter.format("UPDATE Employee e SET e.salary = e.salary * 1.1 WHERE e.department = :dept"));
	}

	@Test
	void queryWithNamedParameter() {
		assertEquals("""
				SELECT f
				from Student f
				  LEFT JOIN f.classTbls s
				WHERE s.ClassName = :className""",
				formatter.format("SELECT f from Student f LEFT JOIN f.classTbls s WHERE s.ClassName = :className"));
	}

	@Test
	void queryWithPositionalParameter() {
		assertEquals("""
				SELECT e
				FROM Employee e
				WHERE e.id = ?1
				  AND e.name = ?2""",
				formatter.format("SELECT e FROM Employee e WHERE e.id = ?1 AND e.name = ?2"));
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
	void invalidQueryReturnsOriginal() {
		String invalid = "NOT A VALID JPQL QUERY !!!";
		assertEquals(invalid, formatter.format(invalid));
	}

	@Test
	void queryWithSubquery() {
		assertEquals("""
				SELECT e
				FROM Employee e
				WHERE e.salary > (
				  SELECT AVG(e2.salary)
				  FROM Employee e2)""",
				formatter.format("SELECT e FROM Employee e WHERE e.salary > (SELECT AVG(e2.salary) FROM Employee e2)"));
	}

	@Test
	void queryWithInExpression() {
		assertEquals("""
				SELECT e
				FROM Employee e
				WHERE e.department IN ('IT', 'HR', 'SALES')""",
				formatter.format("SELECT e FROM Employee e WHERE e.department IN ('IT', 'HR', 'SALES')"));
	}

	@Test
	void queryWithBetween() {
		assertEquals("""
				SELECT e
				FROM Employee e
				WHERE e.salary BETWEEN 50000 AND 100000""",
				formatter.format("SELECT e FROM Employee e WHERE e.salary BETWEEN 50000 AND 100000"));
	}

	@Test
	void queryWithIsNull() {
		assertEquals("""
				SELECT e
				FROM Employee e
				WHERE e.manager IS NULL
				  AND e.active = true""",
				formatter.format("SELECT e FROM Employee e WHERE e.manager IS NULL AND e.active = true"));
	}

	@Test
	void queryWithConstructorExpression() {
		assertEquals("""
				SELECT NEW com.example.EmployeeDTO(e.name, e.salary)
				FROM Employee e
				WHERE e.active = true""",
				formatter.format("SELECT NEW com.example.EmployeeDTO(e.name, e.salary) FROM Employee e WHERE e.active = true"));
	}

	@Test
	void queryPreservesKeywordCase() {
		assertEquals("""
				select e
				from Employee e
				where e.active = true""",
				formatter.format("select e from Employee e where e.active = true"));
	}

}
