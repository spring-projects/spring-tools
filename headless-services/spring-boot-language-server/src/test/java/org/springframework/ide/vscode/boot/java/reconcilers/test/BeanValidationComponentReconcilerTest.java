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
import org.springframework.ide.vscode.boot.java.reconcilers.BeanValidationComponentReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link BeanValidationComponentReconciler}
 * 
 * @author Martin Lippert
 */
public class BeanValidationComponentReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "beanvalidation";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-indexing";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new BeanValidationComponentReconciler(new QuickfixRegistry());
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
	void componentWithValidParamWithoutValidated() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				import jakarta.validation.Valid;
				
				@Component
				public class MyService {
					public void process(@Valid String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyService.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_VALIDATED_ANNOTATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("MyService", markedStr);
	}

	@Test
	void componentWithValidParamWithValidated() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				import org.springframework.validation.annotation.Validated;
				import jakarta.validation.Valid;
				
				@Component
				@Validated
				public class MyService {
					public void process(@Valid String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyService.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void componentWithoutValidationAnnotations() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				
				@Component
				public class MyService {
					public void process(String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyService.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void controllerWithValidParamNotFlagged() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Controller;
				import jakarta.validation.Valid;
				
				@Controller
				public class MyController {
					public void process(@Valid String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyController.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void restControllerWithValidParamNotFlagged() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.bind.annotation.RestController;
				import jakarta.validation.Valid;
				
				@RestController
				public class MyRestController {
					public void process(@Valid String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyRestController.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void nonComponentClassNotFlagged() throws Exception {
		String source = """
				package example;
				
				import jakarta.validation.Valid;
				
				public class PlainClass {
					public void process(@Valid String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("PlainClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void serviceWithNotNullConstraint() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Service;
				import jakarta.validation.constraints.NotNull;
				
				@Service
				public class MyService {
					public void process(@NotNull String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyService.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_VALIDATED_ANNOTATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("MyService", markedStr);
	}

	@Test
	void serviceWithValidOnReturnValue() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Service;
				import jakarta.validation.Valid;
				
				@Service
				public class MyService {
					@Valid
					public String process() {
						return "result";
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyService.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_VALIDATED_ANNOTATION, problem.getType());
	}

	@Test
	void abstractClassNotFlagged() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				import jakarta.validation.Valid;
				
				@Component
				public abstract class AbstractService {
					public abstract void process(@Valid String input);
				}
				""";

		List<ReconcileProblem> problems = reconcile("AbstractService.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void interfaceNotFlagged() throws Exception {
		String source = """
				package example;
				
				import jakarta.validation.Valid;
				
				public interface MyInterface {
					void process(@Valid String input);
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyInterface.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void serviceWithSizeConstraintOnParam() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Service;
				import jakarta.validation.constraints.Size;
				
				@Service
				public class MyService {
					public void process(@Size(min = 1, max = 10) String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyService.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_VALIDATED_ANNOTATION, problem.getType());
	}

	@Test
	void serviceAlreadyValidatedNotFlagged() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Service;
				import org.springframework.validation.annotation.Validated;
				import jakarta.validation.constraints.NotNull;
				
				@Service
				@Validated
				public class MyService {
					public void process(@NotNull String input) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyService.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void innerNonStaticClassNotFlagged() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				import jakarta.validation.Valid;
				
				@Component
				public class OuterClass {
					public class InnerService {
						public void process(@Valid String input) {
						}
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("OuterClass.java", source, false);
		// Inner non-static classes are not applicable
		// Outer class doesn't have validation annotations on its own methods
		assertEquals(0, problems.size());
	}

	@Test
	void staticInnerClassWithValidationAnnotation() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				import jakarta.validation.Valid;
				
				public class OuterClass {
					@Component
					public static class InnerService {
						public void process(@Valid String input) {
						}
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("OuterClass.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_VALIDATED_ANNOTATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("InnerService", markedStr);
	}

}
