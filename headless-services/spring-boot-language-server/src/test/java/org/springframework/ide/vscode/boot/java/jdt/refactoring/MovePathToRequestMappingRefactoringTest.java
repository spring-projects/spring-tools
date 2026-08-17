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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MovePathToRequestMappingRefactoring}.
 */
class MovePathToRequestMappingRefactoringTest {

	private static Map<String, String> defaultFormatterOptions() {
		Map<String, String> options = JavaCore.getOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		return options;
	}

	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS25);
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		parser.setUnitName("Test.java");
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}

	private static String applyRefactoring(String source) throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new MovePathToRequestMappingRefactoring().apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	@Test
	void movePathFromControllerToRequestMapping() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;

				@Controller("/api/users")
				public class UserController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.RequestMapping;

				@Controller
				@RequestMapping("/api/users")
				public class UserController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

	@Test
	void movePathFromRestControllerToRequestMapping() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				@RestController("/api/products")
				public class ProductController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping("/api/products")
				public class ProductController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

	@Test
	void updateExistingRequestMapping() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.RequestMapping;

				@Controller("/api/orders")
				@RequestMapping("/orders")
				public class OrderController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.RequestMapping;

				@Controller
				@RequestMapping("/api/orders")
				public class OrderController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

	@Test
	void updateExistingRequestMappingWithPathParameter() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.RequestMapping;

				@Controller("/api/customers")
				@RequestMapping(path = "/customers")
				public class CustomerController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.RequestMapping;

				@Controller
				@RequestMapping(path = "/api/customers")
				public class CustomerController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

	@Test
	void updateExistingRequestMappingWithValueParameter() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController("/api/inventory")
				@RequestMapping(value = "/inventory")
				public class InventoryController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping(value = "/api/inventory")
				public class InventoryController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

	@Test
	void doNotModifyControllerWithoutPath() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;

				@Controller
				public class SimpleController {
					// controller methods
				}
				""";

		assertEquals(source, applyRefactoring(source));
	}

	@Test
	void doNotModifyControllerWithNonPathValue() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;

				@Controller("someValue")
				public class SimpleController {
					// controller methods
				}
				""";

		assertEquals(source, applyRefactoring(source));
	}

	@Test
	void handleControllerWithMultipleAnnotations() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Component;
				import org.springframework.stereotype.Controller;

				@Component
				@Controller("/api/reports")
				public class ReportController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.stereotype.Component;
				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.RequestMapping;

				@Component
				@Controller
				@RequestMapping("/api/reports")
				public class ReportController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

	@Test
	void handleControllerWithValueParameter() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;

				@Controller(value = "/api/dashboard")
				public class DashboardController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.RequestMapping;

				@Controller
				@RequestMapping("/api/dashboard")
				public class DashboardController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

	@Test
	void handleRestControllerWithValueParameter() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				@RestController(value = "/api/notifications")
				public class NotificationController {
					// controller methods
				}
				""";

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping("/api/notifications")
				public class NotificationController {
					// controller methods
				}
				""", applyRefactoring(source));
	}

}
