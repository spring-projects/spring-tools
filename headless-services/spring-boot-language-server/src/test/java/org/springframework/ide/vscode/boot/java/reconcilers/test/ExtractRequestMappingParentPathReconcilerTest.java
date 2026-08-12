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
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.ExtractRequestMappingParentPathReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link ExtractRequestMappingParentPathReconciler}
 *
 * @author Martin Lippert
 */
public class ExtractRequestMappingParentPathReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "extractrequestmappingparentpath";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new ExtractRequestMappingParentPathReconciler(new QuickfixRegistry());
	}

	@BeforeEach
	void setup() throws Exception {
		super.setup();
	}

	@AfterEach
	void tearDown() throws Exception {
		super.tearDown();
	}

	@Test
	void commonParentPathIsFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping("/foo/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.EXTRACT_REQUEST_MAPPING_PARENT_PATH, problem.getType());

		int expectedStart = source.indexOf("@RestController");
		assertEquals(expectedStart, problem.getOffset());
		assertEquals("@RestController".length(), problem.getLength());

		assertEquals(1, problem.getQuickfixes().size());
		assertEquals("Extract `/foo` into class-level `@RequestMapping`", problem.getQuickfixes().get(0).title);
	}

	@Test
	void anchorsOnCustomComposedControllerAnnotation() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import java.lang.annotation.ElementType;
				import java.lang.annotation.Retention;
				import java.lang.annotation.RetentionPolicy;
				import java.lang.annotation.Target;
				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Retention(RetentionPolicy.RUNTIME)
				@Target(ElementType.TYPE)
				@Controller
				@ResponseBody
				@interface ApiController {
				}

				@ApiController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping("/foo/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		int expectedStart = source.lastIndexOf("@ApiController");
		assertEquals(expectedStart, problem.getOffset());
		assertEquals("@ApiController".length(), problem.getLength());
	}

	@Test
	void deeperCommonPrefixIsFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping("/foo/b/c")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(1, problems.size());
		assertEquals("Extract `/foo` into class-level `@RequestMapping`", problems.get(0).getQuickfixes().get(0).title);
	}

	@Test
	void existingClassLevelRequestMappingIsNotFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping("/foo")
				class FooController {

					@GetMapping("/a")
					String a() {
						return "a";
					}

					@GetMapping("/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void existingClassLevelRequestMappingViaMetaAnnotationIsNotFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import java.lang.annotation.ElementType;
				import java.lang.annotation.Retention;
				import java.lang.annotation.RetentionPolicy;
				import java.lang.annotation.Target;
				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@Retention(RetentionPolicy.RUNTIME)
				@Target(ElementType.TYPE)
				@RequestMapping("/foo")
				@interface FooApi {
				}

				@RestController
				@FooApi
				class FooController {

					@GetMapping("/a")
					String a() {
						return "a";
					}

					@GetMapping("/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void additionalCommonPathIsMergedIntoExistingClassMapping() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping("/foo")
				class FooController {

					@GetMapping("/bar/a")
					String a() {
						return "a";
					}

					@GetMapping("/bar/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.EXTRACT_REQUEST_MAPPING_PARENT_PATH, problem.getType());
		assertEquals(1, problem.getQuickfixes().size());
		assertEquals("Merge `/bar` into existing class-level `@RequestMapping` (`/foo` → `/foo/bar`)",
				problem.getQuickfixes().get(0).title);
	}

	@Test
	void existingClassMappingWithNoResolvablePathBlocksMerge() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RequestMethod;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping(method = RequestMethod.GET)
				class FooController {

					@GetMapping("/bar/a")
					String a() {
						return "a";
					}

					@GetMapping("/bar/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void existingClassMappingWithMultiValuePathBlocksMerge() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping({ "/foo", "/foo2" })
				class FooController {

					@GetMapping("/bar/a")
					String a() {
						return "a";
					}

					@GetMapping("/bar/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void nonControllerIsNotFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;

				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping("/foo/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void singleMappingMethodIsNotFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void noCommonPrefixIsNotFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping("/bar/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void bareMappingAnnotationBlocksExtraction() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void multiValuePathBlocksExtraction() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping({ "/foo/a", "/foo/aa" })
					String a() {
						return "a";
					}

					@GetMapping("/foo/b")
					String b() {
						return "b";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void nonMappedMethodsAreIgnored() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping("/foo/b")
					String b() {
						return "b";
					}

					private String helper() {
						return "helper";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(1, problems.size());
	}

	@Test
	void identicalPathsAreFlagged() throws Exception {
		String source = """
				package extractrequestmappingparentpath;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.PostMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo")
					String get() {
						return "get";
					}

					@PostMapping("/foo")
					String post() {
						return "post";
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("FooController.java", source, false);

		assertEquals(1, problems.size());
		assertEquals("Extract `/foo` into class-level `@RequestMapping`", problems.get(0).getQuickfixes().get(0).title);
	}

}
