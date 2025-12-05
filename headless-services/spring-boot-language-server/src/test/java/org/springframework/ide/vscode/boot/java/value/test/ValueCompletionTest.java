/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.value.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
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
import org.springframework.ide.vscode.boot.java.value.ValueCompletionProcessor;
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
@Import({AdHocPropertyHarnessTestConf.class, ValueCompletionTest.TestConf.class})
public class ValueCompletionTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;

	private Editor editor;

	@Autowired private PropertyIndexHarness indexHarness;
	@Autowired private AdHocPropertyHarness adHocProperties;

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
		
        indexHarness.data("spring.prop1", "java.lang.String", null, null);
		indexHarness.data("data.prop2", "java.lang.String", null, null);
		indexHarness.data("else.prop3", "java.lang.String", null, null);
	}
	
	@Test
    void testPrefixIdentification() {
        ValueCompletionProcessor processor = new ValueCompletionProcessor(projectFinder, null, null);

        assertEquals("pre", processor.identifyPropertyPrefix("pre", 3));
        assertEquals("pre", processor.identifyPropertyPrefix("prefix", 3));
        assertEquals("", processor.identifyPropertyPrefix("", 0));
        assertEquals("pre", processor.identifyPropertyPrefix("$pre", 4));

        assertEquals("", processor.identifyPropertyPrefix("${pre", 0));
        assertEquals("", processor.identifyPropertyPrefix("${pre", 1));
        assertEquals("", processor.identifyPropertyPrefix("${pre", 2));
        assertEquals("p", processor.identifyPropertyPrefix("${pre", 3));
        assertEquals("pr", processor.identifyPropertyPrefix("${pre", 4));
    }

    @Test
    void testEmptyBracketsCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(<*>)");

        assertPropertyCompletions(
                "@Value(\"${data.prop2}\"<*>)",
                "@Value(\"${else.prop3}\"<*>)",
                "@Value(\"${spring.prop1}\"<*>)");
        
        assertClasspathCompletions(
        		"@Value(\"classpath:a-random-resource-root.md\"<*>)",
        		"@Value(\"classpath:org/random-resource-org.md\"<*>)",
        		"@Value(\"classpath:org/test/random-resource-org-test.txt\"<*>)");
    }

    @Test
    void testEmptyBracketsCompletionWithParamName() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=<*>)");

        assertPropertyCompletions(
                "@Value(value=\"${data.prop2}\"<*>)",
                "@Value(value=\"${else.prop3}\"<*>)",
                "@Value(value=\"${spring.prop1}\"<*>)");
        
        assertClasspathCompletions(
        		"@Value(value=\"classpath:a-random-resource-root.md\"<*>)",
        		"@Value(value=\"classpath:org/random-resource-org.md\"<*>)",
        		"@Value(value=\"classpath:org/test/random-resource-org-test.txt\"<*>)");
    }

    @Test
    void testCursorBehindStringLiteralEmptyCompletions() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"something\"<*>)");
        assertPropertyCompletions();
        assertClasspathCompletions();
    }

    @Test
    void testEmptyBracketsCompletionWithWrongParamName() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(another=<*>)");
        assertPropertyCompletions();
        assertClasspathCompletions();
    }

    @Test
    void testOnlyDollarNoQoutesCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value($<*>)");

        assertPropertyCompletions(
                "@Value(\"${data.prop2}\"<*>)",
                "@Value(\"${else.prop3}\"<*>)",
                "@Value(\"${spring.prop1}\"<*>)");

        assertClasspathCompletions();
    }

    @Test
    void testOnlyDollarNoQoutesWithParamCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=$<*>)");

        assertPropertyCompletions(
                "@Value(value=\"${data.prop2}\"<*>)",
                "@Value(value=\"${else.prop3}\"<*>)",
                "@Value(value=\"${spring.prop1}\"<*>)");

        assertClasspathCompletions();
    }

    @Test
    void testOnlyDollarCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"$<*>\")");

        assertPropertyCompletions(
                "@Value(\"${data.prop2}<*>\")",
                "@Value(\"${else.prop3}<*>\")",
                "@Value(\"${spring.prop1}<*>\")");

        assertClasspathCompletions();
    }

    @Test
    void testOnlyDollarWithParamCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=\"$<*>\")");

        assertPropertyCompletions(
                "@Value(value=\"${data.prop2}<*>\")",
                "@Value(value=\"${else.prop3}<*>\")",
                "@Value(value=\"${spring.prop1}<*>\")");

        assertClasspathCompletions();
    }

    @Test
    void testDollarWithBracketsCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"${<*>}\")");

        assertPropertyCompletions(
                "@Value(\"${data.prop2<*>}\")",
                "@Value(\"${else.prop3<*>}\")",
                "@Value(\"${spring.prop1<*>}\")");

        assertClasspathCompletions();
    }

    @Test
    void testDollarWithBracketsWithParamCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=\"${<*>}\")");

        assertPropertyCompletions(
                "@Value(value=\"${data.prop2<*>}\")",
                "@Value(value=\"${else.prop3<*>}\")",
                "@Value(value=\"${spring.prop1<*>}\")");

        assertClasspathCompletions();
    }

    @Test
    void testEmptyStringLiteralCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"<*>\")");

        assertPropertyCompletions(
                "@Value(\"${data.prop2}<*>\")",
                "@Value(\"${else.prop3}<*>\")",
                "@Value(\"${spring.prop1}<*>\")");

        assertClasspathCompletions(
        		"@Value(\"classpath:a-random-resource-root.md<*>\")",
        		"@Value(\"classpath:org/random-resource-org.md<*>\")",
        		"@Value(\"classpath:org/test/random-resource-org-test.txt<*>\")");
    }

    @Test
    void testPlainPrefixCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(spri<*>)");

        assertPropertyCompletions(
                "@Value(\"${spring.prop1}\"<*>)");

        assertClasspathCompletions();
    }

    @Test
    @Disabled // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/3420
    void testComplexPrefixCompletionParamNameCursorRightAfterDot() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(spring.<*>)");

        assertPropertyCompletions(
                "@Value(\"${spring.prop1}\"<*>)");

        assertClasspathCompletions();
    }

    @Test
    void testComplexPrefixCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(spring.pr<*>)");

        assertPropertyCompletions(
                "@Value(\"${spring.prop1}\"<*>)");

        assertClasspathCompletions();
    }

    @Test
    void testPrefixCompletionWithParamName() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=sprin<*>)");

        assertPropertyCompletions(
                "@Value(value=\"${spring.prop1}\"<*>)");
        
        assertClasspathCompletions();
    }

    @Test
    @Disabled // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/3421
    void testPrefixCompletionWithParamNameCursorRightAfterDot() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=spring.<*>)");

        assertPropertyCompletions(
                "@Value(value=\"${spring.prop1}\"<*>)");
        
        assertClasspathCompletions();
    }

    @Test
    void testComplexPrefixCompletionWithParamName() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=spring.pr<*>)");

        assertPropertyCompletions(
                "@Value(value=\"${spring.prop1}\"<*>)");

        assertClasspathCompletions();
    }

    @Test
    void testClasspathPrefixCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(cla<*>)");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(\"classpath:a-random-resource-root.md\"<*>)",
        		"@Value(\"classpath:org/random-resource-org.md\"<*>)",
        		"@Value(\"classpath:org/test/random-resource-org-test.txt\"<*>)");
    }

    @Test
    void testResourceNameInPrefixCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(root<*>)");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(\"classpath:a-random-resource-root.md\"<*>)");
    }

    @Test
    void testComplexResourceNameInPrefixCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(root.md<*>)");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(\"classpath:a-random-resource-root.md\"<*>)");
    }

    @Test
    void testComplexResourceNameInPrefixWithinQoutesCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"root.md<*>\")");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(\"classpath:a-random-resource-root.md<*>\")");
    }

    @Test
    void testComplexResourceNameInPrefixWithParamNameCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=root.md<*>)");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(value=\"classpath:a-random-resource-root.md\"<*>)");
    }

    @Test
    void testComplexResourceNameInPrefixWithinQoutesAndParamNameCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=\"root.md<*>\")");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(value=\"classpath:a-random-resource-root.md<*>\")");
    }

    @Test
    void testClasspathPrefixCompletionWithParamName() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(value=cla<*>)");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(value=\"classpath:a-random-resource-root.md\"<*>)",
        		"@Value(value=\"classpath:org/random-resource-org.md\"<*>)",
        		"@Value(value=\"classpath:org/test/random-resource-org-test.txt\"<*>)");
    }

    @Test
    void testQoutedPrefixCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"spri<*>\")");

        assertPropertyCompletions(
                "@Value(\"${spring.prop1}<*>\")");

        assertClasspathCompletions();
    }

    @Test
    void testComplexPrefixCompletionWithQuotesAndDotRightAfterPrefix() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"spring.<*>\")");

        assertPropertyCompletions(
                "@Value(\"${spring.prop1}<*>\")");

        assertClasspathCompletions();
    }

    @Test
    void testComplexPrefixCompletionWithQuotes() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"spring.pr<*>\")");

        assertPropertyCompletions(
                "@Value(\"${spring.prop1}<*>\")");

        assertClasspathCompletions();
    }

    @Test
    void testQuotedClasspathPrefixCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"cla<*>\")");

        assertPropertyCompletions();

        assertClasspathCompletions(
        		"@Value(\"classpath:a-random-resource-root.md<*>\")",
        		"@Value(\"classpath:org/random-resource-org.md<*>\")",
        		"@Value(\"classpath:org/test/random-resource-org-test.txt<*>\")");
    }

    @Test
    void testRandomSpelExpressionNoCompletion() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"#{<*>}\")");

        assertPropertyCompletions(
                "@Value(\"#{${data.prop2}<*>}\")",
                "@Value(\"#{${else.prop3}<*>}\")",
                "@Value(\"#{${spring.prop1}<*>}\")");

        assertClasspathCompletions();
    }

    @Test
    void testRandomSpelExpressionWithPropertyDollar() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"#{345$<*>}\")");

        assertPropertyCompletions(
                "@Value(\"#{345${data.prop2}<*>}\")",
                "@Value(\"#{345${else.prop3}<*>}\")",
                "@Value(\"#{345${spring.prop1}<*>}\")");

        assertClasspathCompletions();
    }

    @Test
    void testRandomSpelExpressionWithPropertyDollerWithoutClosindBracket() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"#{345${<*>}\")");

        assertPropertyCompletions(
                "@Value(\"#{345${data.prop2}<*>}\")",
                "@Value(\"#{345${else.prop3}<*>}\")",
                "@Value(\"#{345${spring.prop1}<*>}\")");

        assertClasspathCompletions();
    }

    @Test
    void testRandomSpelExpressionWithPropertyDollerWithClosingBracket() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"#{345${<*>}}\")");

        assertPropertyCompletions(
                "@Value(\"#{345${data.prop2<*>}}\")",
                "@Value(\"#{345${else.prop3<*>}}\")",
                "@Value(\"#{345${spring.prop1<*>}}\")");

        assertClasspathCompletions();
    }

    @Test
    void testRandomSpelExpressionWithPropertyPrefixWithoutClosingBracket() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"#{345${spri<*>}\")");

        assertPropertyCompletions(
                "@Value(\"#{345${spring.prop1}<*>}\")");

        assertClasspathCompletions();
    }

    @Test
    void testRandomSpelExpressionWithPropertyPrefixWithClosingBracket() throws Exception {
        prepareCase("@Value(\"onField\")", "@Value(\"#{345${spri<*>}}\")");

        assertPropertyCompletions(
                "@Value(\"#{345${spring.prop1<*>}}\")");

        assertClasspathCompletions();
    }

    @Test
    void adHoc() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA,
                "package org.test;\n" +
                        "\n" +
                        "import org.springframework.beans.factory.annotation.Value;\n" +
                        "\n" +
                        "public class TestValueCompletion {\n" +
                        "	\n" +
                        "	@Value(\"<*>\")\n" +
                        "	private String value1;\n" +
                        "}"
        );

        //There are no 'ad-hoc' properties yet. So should only suggest the default ones.
        editor.assertContextualCompletions(
                "<*>"
        , //==>
				"classpath:a-random-resource-root.md<*>",
				"classpath:org/random-resource-org.md<*>",
        		"classpath:org/test/random-resource-org-test.txt<*>",

                "${data.prop2}<*>",
                "${else.prop3}<*>",
                "${spring.prop1}<*>"
        );

        adHocProperties.add("spring.ad-hoc.thingy");
        adHocProperties.add("spring.ad-hoc.other-thingy");
        adHocProperties.add("spring.prop1"); //should not suggest this twice!
        editor.assertContextualCompletions(
                "<*>"
        , //==>
				"classpath:a-random-resource-root.md<*>",
				"classpath:org/random-resource-org.md<*>",
				"classpath:org/test/random-resource-org-test.txt<*>",

				"${data.prop2}<*>",
                "${else.prop3}<*>",
                "${spring.ad-hoc.other-thingy}<*>",
                "${spring.ad-hoc.thingy}<*>",
                "${spring.prop1}<*>"
        );

        editor.assertContextualCompletions(
                "adhoc<*>"
        , //==>
                "${spring.ad-hoc.thingy}<*>",
                "${spring.ad-hoc.other-thingy}<*>"
        );
    }


	private void prepareCase(String selectedAnnotation, String annotationStatementBeforeTest) throws Exception {
		InputStream resource = this.getClass().getResourceAsStream("/test-projects/test-annotations/src/main/java/org/test/TestValueCompletion.java");
		String content = IOUtils.toString(resource, Charset.defaultCharset());

		content = content.replace(selectedAnnotation, annotationStatementBeforeTest);
		editor = new Editor(harness, content, LanguageId.JAVA);
	}

	private void assertPropertyCompletions(String... completedAnnotations) throws Exception {
		List<CompletionItem> completions = editor.getCompletions();
		
		List<CompletionItem> filteredCompletions = completions.stream()
			.filter(item -> !item.getTextEdit().getLeft().getNewText().contains("classpath"))
			.toList();
		
		assertEquals(completedAnnotations.length, filteredCompletions.size());
		
		for (int i = 0; i < completedAnnotations.length; i++) {
			CompletionItem completion = filteredCompletions.get(i);
			
			Editor clonedEditor = editor.clone();
			clonedEditor.apply(completion);

			String expected = completedAnnotations[i];
			if (!clonedEditor.getText().contains(expected)) {
				fail("Not found '" + expected +"' in \n" + clonedEditor.getText());
			}
		}
	}

	private void assertClasspathCompletions(String... completedAnnotations) throws Exception {
		List<CompletionItem> completions = editor.getCompletions();
		
		List<CompletionItem> filteredCompletions = completions.stream()
				.filter(item -> item.getTextEdit().getLeft().getNewText().contains("classpath"))
				.sorted(new Comparator<CompletionItem>() {
					@Override
					public int compare(CompletionItem o1, CompletionItem o2) {
						return o1.getLabel().compareTo(o2.getLabel());
					}
				})
				.toList();
		
		assertEquals(completedAnnotations.length, filteredCompletions.size());
		
		for (int i = 0; i < completedAnnotations.length; i++) {
			CompletionItem completion = filteredCompletions.get(i);
			
			Editor clonedEditor = editor.clone();
			clonedEditor.apply(completion);

			String expected = completedAnnotations[i];
			if (!clonedEditor.getText().contains(expected)) {
				fail("Not found '" + expected +"' in \n" + clonedEditor.getText());
			}
		}
	}
}
