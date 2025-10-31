/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.Assertions;
import org.openrewrite.test.RewriteTest;

public class AddAnnotationOverMethodTest implements RewriteTest {
	
	@Test
	void addAnnotationToMethod() {
		rewriteRun(
				spec -> spec.recipe(new AddAnnotationOverMethod("demo.A foo()", "java.lang.Deprecated", List.of(new AddAnnotationOverMethod.Attribute("value", "\"Expected-Text\"")))),
				Assertions.java(
					"""
					package demo;
					interface A {
						void foo(); 
					}
					""",
					"""
					package demo;
					interface A {
					    @Deprecated("Expected-Text")
					    void foo(); 
					}
					"""
				)
		);
	}

	@Test
	void addAnnotationToMethodWithAnnotation() {
		rewriteRun(
				spec -> spec.recipe(new AddAnnotationOverMethod("demo.A foo()", "java.lang.Deprecated", List.of(new AddAnnotationOverMethod.Attribute("value", "\"Expected-Text\"")))),
				Assertions.java(
					"""
					package demo;
					class A {
						@SuppressWarnings("null")
						void foo() {
							System.out.println("foo");
						} 
					}
					""",
					"""
					package demo;
					class A {
					    @SuppressWarnings("null")
					    @Deprecated("Expected-Text")
					    void foo() {
							System.out.println("foo");
						} 
					}
					"""
				)
		);
	}

}
