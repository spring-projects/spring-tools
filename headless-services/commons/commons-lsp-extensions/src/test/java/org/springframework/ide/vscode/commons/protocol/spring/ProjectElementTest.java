/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectElementTest {

	@Test
	void testAddDocumentChildren() {
		ProjectElement project = new ProjectElement("projectName");
		
		List<SpringIndexElement> children = project.getChildren();
		assertEquals(0, children.size());
		
		DocumentElement doc1 = new DocumentElement("docUri1");
		DocumentElement doc2 = new DocumentElement("docUri2");
		
		project.addChild(doc1);
		project.addChild(doc2);
		
		children = project.getChildren();
		assertEquals(2, children.size());
		
		assertTrue(children.contains(doc1));
		assertTrue(children.contains(doc2));
	}

	@Test
	void testAddNonDocumentChildren() {
		ProjectElement project = new ProjectElement("projectName");
		
		var child1 = new AbstractSpringIndexElement() {};
		var child2 = new AbstractSpringIndexElement() {};
		
		project.addChild(child1);
		project.addChild(child2);
		
		List<SpringIndexElement> children = project.getChildren();
		assertEquals(2, children.size());
		
		assertTrue(children.contains(child1));
		assertTrue(children.contains(child2));
	}

	@Test
	void testMixedProjectChildren() {
		ProjectElement project = new ProjectElement("projectName");
		
		var doc1 = new DocumentElement("docUri1");
		var doc2 = new DocumentElement("docUri2");
		
		project.addChild(doc1);
		project.addChild(doc2);
		
		var child1 = new AbstractSpringIndexElement() {};
		var child2 = new AbstractSpringIndexElement() {};
		
		project.addChild(child1);
		project.addChild(child2);
		
		List<SpringIndexElement> children = project.getChildren();
		assertEquals(4, children.size());
		
		assertTrue(children.contains(child1));
		assertTrue(children.contains(child2));
		assertTrue(children.contains(doc1));
		assertTrue(children.contains(doc2));
	}
	
	@Test
	void testRemoveProjectChildren() {
		ProjectElement project = new ProjectElement("projectName");
		
		var doc1 = new DocumentElement("docUri1");
		var doc2 = new DocumentElement("docUri2");
		
		project.addChild(doc1);
		project.addChild(doc2);
		
		var child1 = new AbstractSpringIndexElement() {};
		var child2 = new AbstractSpringIndexElement() {};
		
		project.addChild(child1);
		project.addChild(child2);
		
		project.removeDocument("docUri1");
		List<SpringIndexElement> children = project.getChildren();
		assertEquals(3, children.size());
		
		assertTrue(children.contains(child1));
		assertTrue(children.contains(child2));
		assertFalse(children.contains(doc1));
		assertTrue(children.contains(doc2));
		
		project.removeDocument("docDoesNotExist");
		children = project.getChildren();
		assertEquals(3, children.size());
		
		assertTrue(children.contains(child1));
		assertTrue(children.contains(child2));
		assertFalse(children.contains(doc1));
		assertTrue(children.contains(doc2));

		project.removeChild(child2);
		children = project.getChildren();
		assertEquals(2, children.size());
		
		assertTrue(children.contains(child1));
		assertFalse(children.contains(child2));
		assertFalse(children.contains(doc1));
		assertTrue(children.contains(doc2));
	}
	
}
