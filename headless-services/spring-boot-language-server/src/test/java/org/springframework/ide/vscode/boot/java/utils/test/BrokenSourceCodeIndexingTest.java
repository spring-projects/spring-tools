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
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Indexing must survive broken source code. JDT hands out a null binding for the second of two
 * duplicated declarations and for method invocations it cannot resolve; dereferencing those
 * bindings used to abort indexing of the whole AST node, which silently dropped everything the
 * indexers had collected for the surrounding type (GH-1980).
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class BrokenSourceCodeIndexingTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());
		projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	/**
	 * Indexes the given broken source file and hands the resulting beans to the assertions. The
	 * file and its index entries are removed afterwards, so the shared index is left untouched.
	 */
	private void indexBrokenSource(String fileName, String content, BeanAssertions assertions) throws Exception {
		Path file = directory.toPath().resolve("src/main/java/org/test/" + fileName);
		String docUri = file.toUri().toString();

		Files.writeString(file, content);
		try {
			indexer.createDocument(docUri).get(10, TimeUnit.SECONDS);
			assertions.check(springIndex.getBeansOfDocument(docUri));
		}
		finally {
			Files.deleteIfExists(file);
			indexer.deleteDocument(docUri).get(10, TimeUnit.SECONDS);
		}
	}

	@FunctionalInterface
	private interface BeanAssertions {
		void check(Bean[] beans) throws Exception;
	}

	private static Bean beanNamed(Bean[] beans, String name) {
		return Arrays.stream(beans)
				.filter(bean -> name.equals(bean.getName()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no bean '" + name + "' in " + Arrays.stream(beans)
						.map(Bean::getName).toList()));
	}

	@Test
	void duplicatedQueryMethodKeepsRepositoryBeanInIndex() throws Exception {
		// DataRepositoryIndexer.identifyQueryString: methodBinding.getDeclaringClass().getBinaryName()
		indexBrokenSource("BrokenDuplicatedRepo.java", """
				package org.test;

				import org.springframework.data.repository.CrudRepository;

				public interface BrokenDuplicatedRepo extends CrudRepository<String, Long> {

					String findByName(String name);

					String findByName(String name);

					String findByOtherName(String name);

				}
				""", beans -> {
			Bean repository = beanNamed(beans, "brokenDuplicatedRepo");
			assertEquals("org.test.BrokenDuplicatedRepo", repository.getType());
		});
	}

	@Test
	void unresolvablePublishEventInvocationKeepsComponentBeanInIndex() throws Exception {
		// ComponentIndexer.scanEventPublisherInvocations: methodBinding.getDeclaringClass()
		indexBrokenSource("BrokenPublisherComponent.java", """
				package org.test;

				import org.springframework.context.ApplicationEventPublisher;
				import org.springframework.stereotype.Component;

				@Component
				public class BrokenPublisherComponent {

					private final ApplicationEventPublisher publisher;
					private final com.nope.NotOnClasspath other;

					public BrokenPublisherComponent(ApplicationEventPublisher publisher, com.nope.NotOnClasspath other) {
						this.publisher = publisher;
						this.other = other;
					}

					public void go() {
						// the receiver type cannot be resolved, so this invocation has no method binding
						other.publishEvent("hello");
					}

				}
				""", beans -> {
			Bean component = beanNamed(beans, "brokenPublisherComponent");
			assertEquals("org.test.BrokenPublisherComponent", component.getType());
		});
	}

	@Test
	void duplicatedMethodBeforeOnApplicationEventKeepsComponentBeanInIndex() throws Exception {
		// ComponentIndexer.findHandleEventMethod: binding.getName()
		// the duplicated method has to come first - otherwise the loop returns before reaching it
		indexBrokenSource("BrokenListenerComponent.java", """
				package org.test;

				import org.springframework.context.ApplicationListener;
				import org.springframework.context.event.ContextRefreshedEvent;
				import org.springframework.stereotype.Component;

				@Component
				public class BrokenListenerComponent implements ApplicationListener<ContextRefreshedEvent> {

					public void duplicated(int number) {
					}

					public void duplicated(int number) {
					}

					@Override
					public void onApplicationEvent(ContextRefreshedEvent event) {
					}

				}
				""", beans -> {
			Bean component = beanNamed(beans, "brokenListenerComponent");
			assertEquals("org.test.BrokenListenerComponent", component.getType());
		});
	}

	@Test
	void duplicatedMethodInWebMvcConfigurerKeepsConfigurationBeanInIndex() throws Exception {
		// WebConfigJavaIndexer.findMethod: binding.overrides(...)
		indexBrokenSource("BrokenWebConfig.java", """
				package org.test;

				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

				@Configuration
				public class BrokenWebConfig implements WebMvcConfigurer {

					public void duplicated(int number) {
					}

					public void duplicated(int number) {
					}

				}
				""", beans -> {
			Bean config = beanNamed(beans, "brokenWebConfig");
			assertEquals("org.test.BrokenWebConfig", config.getType());
			assertTrue(config.isConfiguration());
		});
	}

	@Test
	void duplicatedTypeDeclarationKeepsFirstConfigurationPropertiesBeanInIndex() throws Exception {
		// ComponentIndexer.indexBeanMethods: type.resolveBinding().getQualifiedName()
		indexBrokenSource("BrokenDuplicatedProps.java", """
				package org.test;

				import org.springframework.boot.context.properties.ConfigurationProperties;

				@ConfigurationProperties(prefix = "broken")
				class BrokenDuplicatedProps {

					private String first;

				}

				@ConfigurationProperties(prefix = "broken")
				class BrokenDuplicatedProps {

					private String second;

				}
				""", beans -> {
			// the first of the two declarations still resolves and must stay in the index
			Bean props = beanNamed(beans, "brokenDuplicatedProps");
			assertEquals("org.test.BrokenDuplicatedProps", props.getType());
		});
	}

}
