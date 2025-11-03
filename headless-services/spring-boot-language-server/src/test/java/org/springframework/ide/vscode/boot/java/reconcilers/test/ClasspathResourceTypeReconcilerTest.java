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
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.ClasspathResourceTypeReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link ClasspathResourceTypeReconciler}
 * 
 * @author Martin Lippert
 */
public class ClasspathResourceTypeReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "classpathresource";
	}

	@Override
	protected String getProjectName() {
		return "empty-boot-2.4.4-app";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new ClasspathResourceTypeReconciler();
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
	void validResourceType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.core.io.Resource;
				
				public class TestClass {
					@Value("classpath:application.properties")
					private Resource validResource;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validResourceArrayType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.core.io.Resource;
				
				public class TestClass {
					@Value("classpath*:*.properties")
					private Resource[] validResources;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validInputStreamType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.io.InputStream;
				
				public class TestClass {
					@Value("classpath:data.txt")
					private InputStream validInputStream;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validFileType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.io.File;
				
				public class TestClass {
					@Value("classpath:data.txt")
					private File validFile;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validURLType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.net.URL;
				
				public class TestClass {
					@Value("classpath:data.txt")
					private URL validUrl;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validPathType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.nio.file.Path;
				
				public class TestClass {
					@Value("classpath:data.txt")
					private Path validPath;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validInputStreamArrayType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.io.InputStream;
				
				public class TestClass {
					@Value("classpath*:*.txt")
					private InputStream[] validInputStreamArray;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validFileArrayType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.io.File;
				
				public class TestClass {
					@Value("classpath*:*.txt")
					private File[] validFileArray;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validURLArrayType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.net.URL;
				
				public class TestClass {
					@Value("classpath*:*.txt")
					private URL[] validUrlArray;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void validPathArrayType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import java.nio.file.Path;
				
				public class TestClass {
					@Value("classpath*:*.txt")
					private Path[] validPathArray;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void invalidStringType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				
				public class TestClass {
					@Value("classpath:application.properties")
					private String invalidType;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.VALUE_CLASSPATH_RESOURCE_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Value(\"classpath:application.properties\")", markedStr);
	}

	@Test
	void invalidIntegerType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				
				public class TestClass {
					@Value("classpath:config.properties")
					private Integer invalidType;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.VALUE_CLASSPATH_RESOURCE_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Value(\"classpath:config.properties\")", markedStr);
	}

	@Test
	void validWithPlaceholder() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.core.io.Resource;
				
				public class TestClass {
					@Value("${classpath:application.properties}")
					private Resource validWithPlaceholder;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void nonClasspathValueIgnored() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				
				public class TestClass {
					@Value("${some.property}")
					private String validPropertyInjection;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void invalidTypeWithClasspathStar() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				
				public class TestClass {
					@Value("classpath*:*.properties")
					private String invalidType;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.VALUE_CLASSPATH_RESOURCE_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Value(\"classpath*:*.properties\")", markedStr);
	}

	@Test
	void invalidStringArrayType() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				
				public class TestClass {
					@Value("classpath*:*.properties")
					private String[] invalidStringArray;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.VALUE_CLASSPATH_RESOURCE_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Value(\"classpath*:*.properties\")", markedStr);
	}

	@Test
	void invalidTypeInConstructorParameter() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.stereotype.Component;
				
				@Component
				public class TestClass {
					public TestClass(@Value("classpath:data.txt") String invalidParam) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.VALUE_CLASSPATH_RESOURCE_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Value(\"classpath:data.txt\")", markedStr);
	}

	@Test
	void validTypeInConstructorParameter() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.core.io.Resource;
				import org.springframework.stereotype.Component;
				
				@Component
				public class TestClass {
					public TestClass(@Value("classpath:data.txt") Resource validParam) {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void fileProtocolNotAffected() throws Exception {
		String source = """
				package example;
				
				import org.springframework.beans.factory.annotation.Value;
				
				public class TestClass {
					@Value("file:/etc/config.properties")
					private String fileProtocol;
				}
				""";

		List<ReconcileProblem> problems = reconcile("TestClass.java", source, false);
		assertEquals(0, problems.size());
	}

}
