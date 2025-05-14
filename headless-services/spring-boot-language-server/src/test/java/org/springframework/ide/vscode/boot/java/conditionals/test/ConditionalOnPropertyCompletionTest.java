/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.conditionals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.bootiful.AdHocPropertyHarnessTestConf;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.editor.harness.AdHocPropertyHarness;
import org.springframework.ide.vscode.boot.editor.harness.PropertyIndexHarness;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheVoid;
import org.springframework.ide.vscode.boot.java.links.SourceLinkFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({AdHocPropertyHarnessTestConf.class, ConditionalOnPropertyCompletionTest.TestConf.class})
public class ConditionalOnPropertyCompletionTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;

	private Editor editor;

	@Autowired private PropertyIndexHarness indexHarness;
	@Autowired private AdHocPropertyHarness adHocProperties;
	private String tempJavaDocUri;

	@Configuration
	static class TestConf {

		//Somewhat strange test setup, test provides a specific test project.
		//The project finder finds this test project,
		//But it is not used in the indexProvider/harness.
		//this is a bit odd... but we preserved the strangeness how it was.

		@Bean MavenJavaProject testProject() throws Exception {
			return ProjectsHarness.INSTANCE.mavenProject("test-annotations");
		}

		@Bean PropertyIndexHarness indexHarness(ValueProviderRegistry valueProviders) {
			return new PropertyIndexHarness(valueProviders);
		}

		@Bean JavaProjectFinder projectFinder(MavenJavaProject testProject) {
			return new JavaProjectFinder() {

				@Override
				public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
					return Optional.ofNullable(testProject);
				}

				@Override
				public Collection<? extends IJavaProject> all() {
					// TODO Auto-generated method stub
					return testProject == null ? Collections.emptyList() : ImmutableList.of(testProject);
				}
			};
		}

		@Bean BootLanguageServerHarness harness(SimpleLanguageServer server, BootLanguageServerParams serverParams, PropertyIndexHarness indexHarness, JavaProjectFinder projectFinder) throws Exception {
			return new BootLanguageServerHarness(server, serverParams, indexHarness, projectFinder, LanguageId.JAVA, ".java");
		}

		@Bean BootLanguageServerParams serverParams(SimpleLanguageServer server, JavaProjectFinder projectFinder, ValueProviderRegistry valueProviders, PropertyIndexHarness indexHarness) {
			BootLanguageServerParams testDefaults = BootLanguageServerHarness.createTestDefault(server, valueProviders);
			return new BootLanguageServerParams(
					projectFinder,
					ProjectObserver.NULL,
					indexHarness.getIndexProvider(),
					testDefaults.typeUtilProvider
			);
		}

		@Bean IndexCache symbolCache() {
			return new IndexCacheVoid();
		}

		@Bean SourceLinks sourceLinks(SimpleTextDocumentService documents, CompilationUnitCache cuCache) {
			return SourceLinkFactory.NO_SOURCE_LINKS;
		}

	}

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		
        File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotations/").toURI());
        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();
		
		indexHarness.data("spring.boot.prop1", "java.lang.String", null, null);
		indexHarness.data("data.prop2", "java.lang.String", null, null);
		indexHarness.data("else.prop3", "java.lang.String", null, null);
	}

    @Test
    public void testConditionalOnPropertyCompletionWithoutPrefix() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(<*>)");
        assertEquals(3, completions.size());
        
        assertEquals("data.prop2", completions.get(0).getLabel());
        assertEquals("else.prop3", completions.get(1).getLabel());
        assertEquals("spring.boot.prop1", completions.get(2).getLabel());
    }

    @Test
    public void testConditionalOnPropertyCompletionWithAdhocProperties() throws Exception {
    	adHocProperties.add("adhoc.prop");
    	
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(<*>)");
        assertEquals(4, completions.size());
        
        assertEquals("adhoc.prop", completions.get(0).getLabel());
        assertEquals("data.prop2", completions.get(1).getLabel());
        assertEquals("else.prop3", completions.get(2).getLabel());
        assertEquals("spring.boot.prop1", completions.get(3).getLabel());
    }

    @Test
    public void testConditionalOnPropertyCompletionWithAdhocPropertiesDuplicatedKey() throws Exception {
    	adHocProperties.add("data.prop2");
    	
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(<*>)");
        assertEquals(3, completions.size());
        
        assertEquals("data.prop2", completions.get(0).getLabel());
        assertEquals("else.prop3", completions.get(1).getLabel());
        assertEquals("spring.boot.prop1", completions.get(2).getLabel());
    }

    @Test
    public void testConditionalOnPropertyCompletionWithoutPrefixAttributeWithNameAttribute() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(name=<*>)");
        assertEquals(3, completions.size());
        
        assertEquals("data.prop2", completions.get(0).getLabel());
        assertEquals("else.prop3", completions.get(1).getLabel());
        assertEquals("spring.boot.prop1", completions.get(2).getLabel());
    }

    @Test
    public void testConditionalOnPropertyCompletionWithoutPrefixAttributeWithNameAttributeAndSpaces() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(name = <*>)");
        assertEquals(3, completions.size());
        
        assertEquals("data.prop2", completions.get(0).getLabel());
        assertEquals("else.prop3", completions.get(1).getLabel());
        assertEquals("spring.boot.prop1", completions.get(2).getLabel());
    }

    @Test
    public void testConditionalOnPropertyCompletionWithoutPrefixAttributeWithNameAttributeAndSpacesInsideArray() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(name = {<*>})");
        assertEquals(3, completions.size());
        
        assertEquals("data.prop2", completions.get(0).getLabel());
        assertEquals("else.prop3", completions.get(1).getLabel());
        assertEquals("spring.boot.prop1", completions.get(2).getLabel());
    }

    @Test
    public void testConditionalOnPropertyCompletionForPrefix() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(prefix = <*>)");
        assertEquals(4, completions.size());
        
        assertEquals("data", completions.get(0).getLabel());
        assertEquals("else", completions.get(1).getLabel());
        assertEquals("spring", completions.get(2).getLabel());
        assertEquals("spring.boot", completions.get(3).getLabel());
    }

    @Test
    @Disabled // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/3260
    public void testConditionalOnPropertyCompletionWithPrefixAndAttributeWithNameAttribute() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(prefix = \"else\", name=<*>)");
        assertEquals(1, completions.size());
        
        assertEquals("else.prop3", completions.get(0).getLabel());
        assertEquals("prop3", completions.get(0).getFilterText());
        assertEquals("prop3", completions.get(0).getTextEdit().getRight().getNewText());
    }

    @Test
    @Disabled // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/3260
    public void testConditionalOnPropertyCompletionWithPrefixAndAttributeWithNameAttributeAndSpaces() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(prefix = \"else\", name= <*>)");
        assertEquals(1, completions.size());
        
        assertEquals("else.prop3", completions.get(0).getLabel());
        assertEquals("prop3", completions.get(0).getFilterText());
        assertEquals("prop3", completions.get(0).getTextEdit().getRight().getNewText());
    }

    @Test
    public void testConditionalOnPropertyCompletionWithPrefixAndAttributeWithNameAttributeAndQuotes() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(prefix = \"else\", name=\"<*>\")");
        assertEquals(1, completions.size());
        
        assertEquals("else.prop3", completions.get(0).getLabel());
        assertEquals("prop3", completions.get(0).getFilterText());
        assertEquals("prop3", completions.get(0).getTextEdit().getLeft().getNewText());
    }

    @Test
    @Disabled // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/3419
    public void testConditionalOnPropertyCompletionWithEmptyPrefixAndEmptyNameAttribute() throws Exception {
    	List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(prefix = , name = <*>)");
        assertEquals(3, completions.size());
        
        assertEquals("data.prop2", completions.get(0).getLabel());
        assertEquals("else.prop3", completions.get(1).getLabel());
        assertEquals("spring.boot.prop1", completions.get(2).getLabel());
    }

    @Test
    @Disabled // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/3419
    public void testConditionalOnPropertyCompletionWithEmptyPrefixAndEmptyNameAttributeInQuotes() throws Exception {
    	List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(prefix = , name = \"<*>\")");
        assertEquals(3, completions.size());
        
        assertEquals("data.prop2", completions.get(0).getLabel());
        assertEquals("else.prop3", completions.get(1).getLabel());
        assertEquals("spring.boot.prop1", completions.get(2).getLabel());
    }
    
    @Test
    public void testConditionalOnPropertyCompletionForPrefixWithAdditionalEmptyNameAttribute() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnProperty(prefix = <*>, name = )");
        assertEquals(4, completions.size());
        
        assertEquals("data", completions.get(0).getLabel());
        assertEquals("else", completions.get(1).getLabel());
        assertEquals("spring", completions.get(2).getLabel());
        assertEquals("spring.boot", completions.get(3).getLabel());
    }

    private void assertCompletions(String completionLine, int noOfExpectedCompletions, int selectedProposal, String expectedCompletedLine) throws Exception {
        String editorContent = """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                """ +
                completionLine + "\n" +
                """
                public class TestConditionalOnBeanCompletion {

                    @Bean
                    public void method() {
                    }
                }
                """;

        Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(noOfExpectedCompletions, completions.size());

        if (noOfExpectedCompletions > 0) {
            editor.apply(completions.get(selectedProposal));
            assertEquals("""
    				package org.test;

            		import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
                    import org.springframework.context.annotation.Bean;
                    import org.springframework.context.annotation.Configuration;

                    @Configuration
                    """ +
                    expectedCompletedLine + "\n" +
                    """
                    public class TestConditionalOnBeanCompletion {

                        @Bean
                        public void method() {
                        }
                    }
                    """, editor.getText());

        }
    }

    private List<CompletionItem> getCompletions(String completionLine) throws Exception {
        String editorContent = """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                """ +
                completionLine + "\n" +
                """
                public class TestConditionalOnBeanCompletion {

                    @Bean
                    public void method() {
                    }
                }
                """;

        Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);
        return editor.getCompletions();
    }


}
