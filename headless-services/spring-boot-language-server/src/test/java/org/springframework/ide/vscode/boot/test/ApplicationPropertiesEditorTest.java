/*******************************************************************************
 * Copyright (c) 2016, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.ide.vscode.boot.properties.reconcile.ApplicationPropertiesProblemType.PROP_DUPLICATE_KEY;
import static org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts.field;
import static org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts.method;
import static org.springframework.ide.vscode.languageserver.testharness.ClasspathTestUtil.getOutputFolder;
import static org.springframework.ide.vscode.languageserver.testharness.TestAsserts.assertContains;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.PropertyEditorTestConf;
import org.springframework.ide.vscode.boot.editor.harness.AbstractPropsEditorTest;
import org.springframework.ide.vscode.boot.editor.harness.AdHocPropertyHarness;
import org.springframework.ide.vscode.boot.metadata.CachingValueProvider;
import org.springframework.ide.vscode.boot.metadata.PropertiesLoader;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.ProjectsHarness.ProjectCustomizer;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * Boot App Properties Editor tests
 *
 * @author Alex Boyko
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({PropertyEditorTestConf.class,ApplicationPropertiesEditorTest.TestConf.class})
public class ApplicationPropertiesEditorTest extends AbstractPropsEditorTest {

	@Autowired DefinitionLinkAsserts definitionLinkAsserts;
	@Autowired AdHocPropertyHarness adHocProperties;


	@Configuration static class TestConf {
		@Bean LanguageId defaultLanguageId() {
			return LanguageId.BOOT_PROPERTIES;
		}
		@Bean String defaultFileExtension() {
			return ".properties";
		}
	}

	private static final ProjectCustomizer WITH_EMPTY_APPLICATION_YML = projectContents -> {
		projectContents.createFile("src/main/resources/application.yml", "");
	};

    @Test
    void configImportClasspathCompletions() throws Exception {
        MavenJavaProject p = createPredefinedMavenProject("demo-conf-import");
        useProject(p);

//		assertCompletions("spring.config.import=<*>", 
//				// ==>
//				"spring.config.import=classpath:<*>",
//				"spring.config.import=configtree:<*>",
//				"spring.config.import=file:<*>"
//		);
        
        assertCompletions("spring.config.import=classpath:ex<*>",
                // ==>
                "spring.config.import=classpath:extra.properties<*>"
        );
    }

    @Test
    void reconcilesWithMultiDocuments_Hash() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/533
        
        defaultTestData();

        //lowest bar: just disable the warning
        Editor editor = newEditor(
                "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "#---\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n"
        );
        editor.assertProblems(/*NONE*/);

        //better: still detect duplicates within same section
        editor = newEditor(
                "spring.application.name=frodo\n" +
                        "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "spring.application.name=frodo\n" +
                        "#---\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n" +
                        "server.port=9090\n"
        );
        editor.assertProblems(
                "spring.application.name|Duplicate",
                "spring.application.name|Duplicate",
                "server.port|Duplicate",
                "server.port|Duplicate"
        );

        //nitpick 1: leading spaces before the marker means... it is not a marker
        editor = newEditor(
                "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        " #---\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n"
        );
        editor.assertProblems(
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate",
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate"
        );

        //nitpick 2: trailing spaces after the marker are ignored
        editor = newEditor(
                "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "#---   \t\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n"
        );
        editor.assertProblems(/*NONE*/);
        
        editor = newEditor(
                "spring.application.name=frodo\n" +
                        "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "#---\n" +
                        "# Something off\n" +
                        "spring.application.name=sam\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=9090\n"
        );
        editor.assertProblems(
                "spring.application.name|Duplicate",
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate",
                "spring.application.name|Duplicate",
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate"
        );

        editor = newEditor(
                "spring.application.name=frodo\n" +
                        "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "#---\n" +
                        "! Something off\n" +
                        "spring.application.name=sam\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=9090\n"
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void reconcilesWithMultiDocuments_Exclamation() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/1129
        
        defaultTestData();

        //lowest bar: just disable the warning
        Editor editor = newEditor(
                "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "!---\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n"
        );
        editor.assertProblems(/*NONE*/);

        //better: still detect duplicates within same section
        editor = newEditor(
                "spring.application.name=frodo\n" +
                        "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "spring.application.name=frodo\n" +
                        "!---\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n" +
                        "server.port=9090\n"
        );
        editor.assertProblems(
                "spring.application.name|Duplicate",
                "spring.application.name|Duplicate",
                "server.port|Duplicate",
                "server.port|Duplicate"
        );

        //nitpick 1: leading spaces before the marker means... it is not a marker
        editor = newEditor(
                "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        " !---\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n"
        );
        editor.assertProblems(
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate",
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate"
        );

        //nitpick 2: trailing spaces after the marker are ignored
        editor = newEditor(
                "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "!---   \t\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=8080\n"
        );
        editor.assertProblems(/*NONE*/);
        
        editor = newEditor(
                "spring.application.name=frodo\n" +
                        "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "!---\n" +
                        "! Something off\n" +
                        "spring.application.name=sam\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=9090\n"
        );
        editor.assertProblems(
                "spring.application.name|Duplicate",
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate",
                "spring.application.name|Duplicate",
                "spring.config.activate.on-profile|Duplicate",
                "server.port|Duplicate"
        );

        editor = newEditor(
                "spring.application.name=frodo\n" +
                        "spring.config.activate.on-profile=foo\n" +
                        "server.port=8888\n" +
                        "!---\n" +
                        "# Something off\n" +
                        "spring.application.name=sam\n" +
                        "spring.config.activate.on-profile=bar\n" +
                        "server.port=9090\n"
        );
        editor.assertProblems(/*NONE*/);
    }
    
    @Test
    void inheritedPojoProperties() throws Exception {
        //See https://github.com/spring-projects/sts4/issues/116
        useProject(createPredefinedMavenProject("cloud-rabbit-project"));

        Editor editor = newEditor(
                "spring.cloud.stream.rabbit.bindings.input.consumer.auto-bind-dlq: no-bool"
        );
        editor.assertProblems("no-bool|boolean");
    }

    @Test
    void abbreviateLongPrefixCompletions() throws Exception {
    	
        String changedSettings = "{\"boot-java\": {\"properties\": {\"completions\": {\"elide-prefix\": true}}}}";
        JsonElement settingsAsJson = new Gson().fromJson(changedSettings, JsonElement.class);
        Settings settings = new Settings(settingsAsJson);
        
        harness.changeConfiguration(settings);
    	
        //See: https://github.com/spring-projects/sts4/issues/361
        Editor editor;

        data("spring.data.jpa.very.long.foobar", "java.lang.String", null, null);
        data("spring.data.jpa.very.long.barbar", "java.lang.String", null, null);
        data("spring.data.jpa.very.long.foofoo", "java.lang.String", null, null);
        data("spring.data.jpa.very.long.barfoo", "java.lang.String", null, null);
        data("spring.data.jpa.very.long.foobar.more", "java.lang.String", null, null);
        data("spring.data.jpa.very.long.barbar.more", "java.lang.String", null, null);
        data("spring.data.jpa.very.long.foofoo.more", "java.lang.String", null, null);
        data("spring.data.jpa.very.long.barfoo.more", "java.lang.String", null, null);


        editor = newEditor(
                "spring.data.jpa.very.bar<*>"
        );
        editor.assertCompletions(
                "spring.data.jpa.very.long.barbar=<*>",
                "spring.data.jpa.very.long.barfoo=<*>",
                "spring.data.jpa.very.long.foobar=<*>",
                "spring.data.jpa.very.long.barbar.more=<*>",
                "spring.data.jpa.very.long.barfoo.more=<*>",
                "spring.data.jpa.very.long.foobar.more=<*>"
        );

        List<CompletionItem> completions = editor.assertCompletionLabels(
                "long.barbar",
                "long.barfoo",
                "long.foobar",
                "long.barbar.more",
                "long.barfoo.more",
                "long.foobar.more"
        );
        for (CompletionItem c : completions) {
            TextEdit edit = c.getTextEdit().getLeft();
            assertEquals("bar", editor.getText(edit.getRange()));
        }

        editor = newEditor(
                "spring.data.jpa.vr<*>"
        );
        completions = editor.assertCompletionLabels(
                "very.long.barbar",
                "very.long.barfoo",
                "very.long.foobar",
                "very.long.foofoo",
                "very.long.barbar.more",
                "very.long.barfoo.more",
                "very.long.foobar.more",
                "very.long.foofoo.more"
        );
        for (CompletionItem c : completions) {
            TextEdit edit = c.getTextEdit().getLeft();
            assertEquals("vr", editor.getText(edit.getRange()));
        }

        editor = newEditor(
                "spring.data.jpa.very.<*>"
        );
        editor.assertCompletions(
                "spring.data.jpa.very.long.barbar=<*>",
                "spring.data.jpa.very.long.barbar.more=<*>",
                "spring.data.jpa.very.long.barfoo=<*>",
                "spring.data.jpa.very.long.barfoo.more=<*>",
                "spring.data.jpa.very.long.foobar=<*>",
                "spring.data.jpa.very.long.foobar.more=<*>",
                "spring.data.jpa.very.long.foofoo=<*>",
                "spring.data.jpa.very.long.foofoo.more=<*>"
        );
        completions = editor.assertCompletionLabels(
                "long.barbar",
                "long.barbar.more",
                "long.barfoo",
                "long.barfoo.more",
                "long.foobar",
                "long.foobar.more",
                "long.foofoo",
                "long.foofoo.more"
        );
        for (CompletionItem c : completions) {
            TextEdit edit = c.getTextEdit().getLeft();
            assertEquals("", editor.getText(edit.getRange()));
        }

    }

    @Test
    void testReconcileCatchesParseError() throws Exception {
        Editor editor = newEditor("key\n");
        editor.assertProblems("key|{':', '=', Space}");
    }

    @Test
    void bug_GH_STS4_314() throws Exception {
        ///See: https://github.com/spring-projects/sts4/issues/314
        data("my.text", "java.lang.String", null, null);
        Editor editor;

        editor = newEditor(
                "my.text=something \\n escapy"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "my.text=something simple"
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void bug_158348104() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/158348104
        data("spring.activemq.close-timeout", "java.time.Duration", null, null);

        Editor editor;

        editor = newEditor("");
        editor.assertContextualCompletions("springactcloti",
                "spring.activemq.close-timeout=<*>"
        );

        editor = newEditor(
                "spring.activemq.close-timeout=garbage"
        );
        editor.assertProblems("garbage|not a valid duration");

        editor = newEditor(
                "spring.activemq.close-timeout: 15s"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "spring.activemq.close-timeout: PT10S" //ISO duration format
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void testServerPortCompletion() throws Exception {
        data("server.port", INTEGER, 8080, "Port where server listens for http.");
        assertCompletion("ser<*>", "server.port=<*>");
        assertCompletionDetails("ser<*>", "server.port", "int", null);
    }

    @Test
    void testLoggingLevelCompletion() throws Exception {
        data("logging.level", "java.util.Map<java.lang.String,java.lang.Object>", null, "Logging level per package.");
        assertCompletion("lolev<*>", "logging.level.<*>");
    }

    @Test
    void testListCompletion() throws Exception {
        data("foo.bars", "java.util.List<java.lang.String>", null, "List of bars in foo.");
        assertCompletion("foba<*>", "foo.bars=<*>");
    }

    @Test
    void testInetAddresCompletion() throws Exception {
        defaultTestData();
        assertCompletion("server.add<*>", "server.address=<*>");
    }

    @Test
    void testStringArrayCompletion() throws Exception {
        data("spring.freemarker.view-names", "java.lang.String[]", null, "White list of view names that can be resolved.");
        data("some.defaulted.array", "java.lang.String[]", new String[]{"a", "b", "c"}, "Stuff.");

        assertCompletion("spring.freemarker.vn<*>", "spring.freemarker.view-names=<*>");
        assertCompletion("some.d.a<*>", "some.defaulted.array=<*>");
    }

    @Test
    void testEmptyPrefixProposalsSortedAlpabetically() throws Exception {
        defaultTestData();
        Editor editor = newEditor("");
        List<CompletionItem> completions = editor.getCompletions();
        assertTrue(completions.size() > 100); //should be many proposals
        String previous = null;
        for (CompletionItem c : completions) {
            String current = c.getLabel();
            if (previous != null) {
                assertTrue(previous.compareTo(current) <= 0, "Incorrect order: \n   " + previous + "\n   " + current);
            }
            previous = current;
        }
    }

    @Test
    void testValueCompletion() throws Exception {
        defaultTestData();
        assertCompletionsVariations("liquibase.enabled=<*>",
                "liquibase.enabled=false<*>",
                "liquibase.enabled=true<*>"
        );

        assertCompletionsVariations("liquibase.enabled:<*>",
                "liquibase.enabled:false<*>",
                "liquibase.enabled:true<*>"
        );

        assertCompletionsVariations("liquibase.enabled = <*>",
                "liquibase.enabled = false<*>",
                "liquibase.enabled = true<*>"
        );

        assertCompletionsVariations("liquibase.enabled   <*>",
                "liquibase.enabled   false<*>",
                "liquibase.enabled   true<*>"
        );

        assertCompletionsVariations("liquibase.enabled=f<*>",
                "liquibase.enabled=false<*>"
        );

        assertCompletionsVariations("liquibase.enabled=t<*>",
                "liquibase.enabled=true<*>"
        );

        assertCompletionsVariations("liquibase.enabled:f<*>",
                "liquibase.enabled:false<*>"
        );

        assertCompletionsVariations("liquibase.enabled:t<*>",
                "liquibase.enabled:true<*>"
        );

        assertCompletionsVariations("liquibase.enabled = f<*>",
                "liquibase.enabled = false<*>"
        );

        assertCompletionsVariations("liquibase.enabled = t<*>",
                "liquibase.enabled = true<*>"
        );

        assertCompletionsVariations("liquibase.enabled   t<*>",
                "liquibase.enabled   true<*>"
        );

        //one more... for special char like '-' in the name

        assertCompletionsVariations("liquibase.check-change-log-location=t<*>",
                "liquibase.check-change-log-location=true<*>"
        );
    }


    @Test
    void testHoverInfos() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "#foo\n" +
                        "# bar\n" +
                        "server.port=8080\n" +
                        "logging.level.com.acme=INFO\n"
        );
        //Case 1: an 'exact' match of the property is in the hover region
        editor.assertHoverText("server.",
                "**server.port**"
        );
        //Case 2: an object/map property has extra text after the property name
        editor.assertHoverText("logging.", "**logging.level**");
    }

    @Test
    void testHoverInfosWithSpaces() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "#foo\n" +
                        "# bar\n" +
                        "\n" +
                        "  server.port = 8080\n" +
                        "  logging.level.com.acme = INFO\n"
        );
        //Case 1: an 'exact' match of the property is in the hover region
        editor.assertHoverText("server.",
                "**server.port**"
        );
        //Case 2: an object/map property has extra text after the property name
        editor.assertHoverText("logging.", "**logging.level**");
    }

    @Test
    void testHoverLongAndShort() throws Exception {
        data("server.port", INTEGER, 8080, "Port where server listens for http.");
        data("server.port.fancy", BOOLEAN, 8080, "Whether the port is fancy.");
        Editor editor = newEditor(
                "server.port=8080\n" +
                        "server.port.fancy=true\n"
        );
        editor.assertHoverText("server.", "**server.port**");
        editor.assertHoverText("port.fa", "**server.port.fancy**");
    }


    @Test
    void testPredefinedProject() throws Exception {
        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        IType type = p.getIndex().findType("demo.DemoApplication");
        assertNotNull(type);
    }

    @Test
    void testEnableApt() throws Throwable {
        MavenJavaProject p = createPredefinedMavenProject("boot-1.2.0-properties-live-metadta");

        Path metadataFile = getOutputFolder(p).resolve(PropertiesLoader.PROJECT_META_DATA_LOCATIONS[0]);
        assertTrue(metadataFile.toFile().isFile());
        assertContains("\"name\": \"foo.counter\"", Files.asCharSource(metadataFile.toFile(), StandardCharsets.UTF_8).read());
    }

    @Test
    void testHyperlinkTargets() throws Exception {
        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(p);

        Editor editor = newEditor(
                "server.port=888\n" +
                        "flyway.init-sqls=a,b,c\n"
        );

        definitionLinkAsserts.assertLinkTargets(editor, "server", p, editor.rangeOf("server.port", "server.port"),
                method("org.springframework.boot.autoconfigure.web.ServerProperties", "setPort", "java.lang.Integer"));

        definitionLinkAsserts.assertLinkTargets(editor, "flyway", p, editor.rangeOf("flyway.init-sqls", "flyway.init-sqls"),
                method("org.springframework.boot.autoconfigure.flyway.FlywayProperties", "setInitSqls", "java.util.List"));
    }


    @Test
    void testHyperlinkTargetsLoggingLevel() throws Exception {
        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");

        useProject(p);

        Editor editor = newEditor(
                "logging.level.com.acme=INFO\n"
        );

        definitionLinkAsserts.assertLinkTargets(editor, "level", p, editor.rangeOf("logging.level", "logging.level"),
                "org.springframework.boot.logging.LoggingApplicationListener");
    }

    @Test
    void testReconcile() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server.port=8080\n" +
                        "server.port.extracrap=8080\n" +
                        "logging.level.com.acme=INFO\n" +
                        "logging.snuggem=what?\n" +
                        "bogus.no.good=true\n"
        );
        editor.assertProblems(
                ".extracrap|Can't use '.' navigation",
                "snuggem|unknown property",
                "ogus.no.good|unknown property"
        );

    }

    @Test
    void testReconcilePojoArray() throws Exception {
        IJavaProject p = createPredefinedMavenProject("boot-1.2.1-app-properties-list-of-pojo");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Foo"));

        Editor editor = newEditor(
                "token.bad.guy=problem\n" +
                        "volder.foo.list[0].name=Kris\n" +
                        "volder.foo.list[0].description=Kris\n" +
                        "volder.foo.list[0].roles[0]=Developer\n" +
                        "volder.foo.list[0]garbage=Grable\n" +
                        "volder.foo.list[0].bogus=Bad\n"
        );

        //This is the more ambitious requirement but it is not implemented yet.
        editor.assertProblems(
                "token.bad.guy|unknown property",
                //'name' is ok
                //'description' is ok
                "garbage|'.' or '['",
                "bogus|has no property"
        );
    }

    @Test
    void testPojoArrayCompletions() throws Exception {
        IJavaProject p = createPredefinedMavenProject("boot-1.2.1-app-properties-list-of-pojo");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Foo"));

        assertCompletionsVariations("volder.foo.l<*>", "volder.foo.list[<*>");
        assertCompletionsDisplayStringAndDetail("volder.foo.list[0].<*>",
                new String[]{"description", "String"},
                new String[]{"name", "String"},
                new String[]{"roles", "List<String>"}
        );

        assertCompletionsVariations("volder.foo.list[0].na<*>",
                "volder.foo.list[0].name=<*>"
        );
        assertCompletionsVariations("volder.foo.list[0].d<*>",
                "volder.foo.list[0].description=<*>"
        );
        assertCompletionsVariations("volder.foo.list[0].rl<*>",
                "volder.foo.list[0].roles=<*>"
        );
    }

    @Test
    void testReconcileArrayNotation() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "borked=bad+\n" + //token problem, to make sure reconciler is working
                        "security.user.role[0]=foo\n" +
                        "security.user.role[${one}]=foo"
        );
        editor.assertProblems(
                "orked|unknown property"
        //no other problems
        );
    }

    @Test
    void testReconcileArrayNotationError() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "security.user.role[bork]=foo\n" +
                        "security.user.role[1=foo\n" +
                        "security.user.role[1]crap=foo\n" +
                        "server.port[0]=8888\n" +
                        "spring.thymeleaf.view-names[1]=hello" //This is okay now. Boot handles this notation for arrays
        );
        editor.assertProblems(
                "bork|'int'",
                "[|matching ']'",
                "crap|'.' or '['",
                "[0]|Can't use '[..]'"
        //no other problems
        );
    }

    @Test
    void test_PT_175147973_tolerate_extra_dot_before_lbrack() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/175147973
        IJavaProject p = createPredefinedMavenProject("map-of-pojo");
        useProject(p);
        Editor editor = newEditor(
                "my.map.[foo.bar].age=foo-age\n" +
                        "my.map[foo.bar].age=bar-age\n"
        );
        editor.assertProblems(
                "foo-age|'int'",
                "bar-age|'int'"
        );
    }

    @Test
    void test_GH_534() throws Exception {
        IJavaProject p = createPredefinedMavenProject("map-of-pojo");
        useProject(p);
        data("my.props", "java.util.Properties", null, "Properties in java.util.Properties");
        data("my.arr", "java.lang.String[]", null, "Array");

        Editor editor = newEditor(
                "my.arr[index]=something\n" +
                        "my.props[foo.bar]=something\n" +
                        "my.map[foo.bar].name=Freddy\n" +
                        "my.map[foo.bar].age=the-age\n"
        );
        editor.assertProblems(
                "index|'int'",
                "the-age|'int'"
        );

        //Also check if completion engine understands the convention
        editor = newEditor(
                "my.map[foo.bar].<*>"
        );
        editor.assertContextualCompletions("<*>",
                // ==>
                "age=<*>",
                "name=<*>"
        );
    }
    
    @Test
    void testRecordReconcile() throws Exception {
        MavenJavaProject p = createPredefinedMavenProject("record-props");
		useProject(p);

        Editor editor = newEditor(
                "application.temp.user.name=something"
        );
        editor.assertProblems();
        
        definitionLinkAsserts.assertLinkTargets(editor, "name", p, editor.rangeOf("application.temp.user.name"),
                field("com.record.props.UserProperties", "name")
        );


    }

    @Test
    void testRecordReconcileExtra() throws Exception {
        useProject(createPredefinedMavenProject("record-props"));

        Editor editor = newEditor(
                "application.security.users[0].name=something"
        );
        editor.assertProblems();

    }
    
    @Test
    void testRecordCompletions() throws Exception {
        useProject(createPredefinedMavenProject("record-props"));

        assertCompletionsDisplayString(
        		"application.temp.user.<*>"
        , // =>
                "application.temp.user.name",
                "application.temp.user.password",
                "application.temp.user.roles"
        );

        assertCompletionsDisplayString(
        		"application.security.users[0].<*>"
        , // =>
                "name",
                "password",
                "roles"
        );
    }

    @Test
    void testRelaxedNameReconciling() throws Exception {
        data("connection.remote-host", "java.lang.String", "service.net", null);
        data("foo-bar.name", "java.lang.String", null, null);
        Editor editor = newEditor(
                "bork=foo\n" +
                        "connection.remote-host=alternate.net\n" +
                        "connection.remoteHost=alternate.net\n" +
                        "foo-bar.name=Charlie\n" +
                        "fooBar.name=Charlie\n"
        );
        editor.assertProblems(
                "bork|unknown property"
        //no other problems
        );
    }

    @Test
    void testRelaxedNameReconcilingErrors() throws Exception {
        //Tricky with relaxec names: the error positions have to be moved
        // around because the relaxed names aren't same length as the
        // canonical ids.
        data("foo-bar-zor.enabled", "java.lang.Boolean", null, null);
        Editor editor = newEditor(
                "fooBarZor.enabled=notBoolean\n" +
                        "fooBarZor.enabled.subprop=true\n"
        );
        editor.assertProblems(
                "notBoolean|boolean",
                ".subprop|Can't use '.' navigation"
        );
    }

    @Test
    void testRelaxedNameContentAssist() throws Exception {
        data("foo-bar-zor.enabled", "java.lang.Boolean", null, null);
        assertCompletion("fooBar<*>", "foo-bar-zor.enabled=<*>");
    }

    @Test
    void testReconcileValues() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server.port=badPort\n" +
                        "liquibase.enabled=nuggels"
        );
        editor.assertProblems(
                "badPort|'int'",
                "nuggels|'boolean'"
        );
    }

    @Test
    void testNoReconcileInterpolatedValues() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server.port=${port}\n" +
                        "liquibase.enabled=nuggels"
        );
        editor.assertProblems(
                //no problem should be reported for ${port}
                "nuggels|'boolean'"
        );
    }

    @Test
    void testReconcileValuesWithSpaces() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server.port  =   badPort\n" +
                        "liquibase.enabled   nuggels  \n" +
                        "liquibase.enabled   : snikkers"
        );
        editor.ignoreProblem(PROP_DUPLICATE_KEY); //ignore deliberate abuse of dups
        editor.assertProblems(
                "badPort|'int'",
                "nuggels|'boolean'",
                "snikkers|'boolean'"
        );
    }


    @Test
    void testReconcileWithExtraSpaces() throws Exception {
        defaultTestData();
        //Same test as previous but with extra spaces to make things more confusing
        Editor editor = newEditor(
                "   server.port   =  8080  \n" +
                        "\n" +
                        "  server.port.extracrap = 8080\n" +
                        " logging.level.com.acme  : INFO\n" +
                        "logging.snuggem = what?\n" +
                        "bogus.no.good=  true\n"
        );
        editor.assertProblems(
                ".extracrap|Can't use '.' navigation",
                "snuggem|unknown property",
                "ogus.no.good|unknown property"
        );
    }

    @Test
    void testEnumPropertyCompletionInsideCommaSeparateList() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));

        data("foo.colors", "java.util.List<demo.Color>", null, "A foonky list");

        //Completion requested right after '=' sign:
        assertCompletionsDisplayString("foo.colors=<*>", "red", "green", "blue");
        assertCompletionWithLabel("foo.colors=<*>", "red", "foo.colors=red<*>");
        assertCompletion("foo.colors=R<*>", "foo.colors=RED<*>");
        assertCompletion("foo.colors=g<*>", "foo.colors=green<*>");
        assertCompletion("foo.colors=B<*>", "foo.colors=BLUE<*>");

        //Completion requested after ','
        assertCompletionsDisplayString("foo.colors=red,<*>", "red", "green", "blue");
        assertCompletionWithLabel("foo.colors=red,<*>", "green", "foo.colors=red,green<*>");
        assertCompletion("foo.colors=RED,R<*>", "foo.colors=RED,RED<*>");
        assertCompletion("foo.colors=RED,G<*>", "foo.colors=RED,GREEN<*>");
        assertCompletion("foo.colors=RED,B<*>", "foo.colors=RED,BLUE<*>");
    }

    @Test
    void testEnumPropertyCompletion() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));

        data("foo.color", "demo.Color", null, "A foonky colour");

        assertCompletion("foo.c<*>", "foo.color=<*>"); //Should add the '=' because enums are 'simple' values.

        assertCompletion("foo.color=R<*>", "foo.color=RED<*>");
        assertCompletion("foo.color=G<*>", "foo.color=GREEN<*>");
        assertCompletion("foo.color=B<*>", "foo.color=BLUE<*>");
        assertCompletionsDisplayString("foo.color=<*>",
                "red", "green", "blue"
        );
    }

    @Test
    void testEnumPropertyReconciling() throws Exception {

        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));

        data("foo.color", "demo.Color", null, "A foonky colour");
        Editor editor = newEditor(
                "foo.color=BLUE\n" +
                        "foo.color=RED\n" +
                        "foo.color=GREEN\n" +
                        "foo.color.bad=BLUE\n" +
                        "foo.color=Bogus\n"
        );

        editor.ignoreProblem(PROP_DUPLICATE_KEY); //ignore deliberate abuse of dups

        editor.assertProblems(
                ".bad|Can't use '.' navigation",
                "Bogus|Color"
        );
    }
    
    @Test
    void testNestedEnumPropertyListCompletion() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.C"));
        assertNotNull(p.getIndex().findType("demo.C$E"));

        assertCompletionsVariations("nested-enum.list-properties[0].<*>",
        		"nested-enum.list-properties[0].enum-value=<*>",
                "nested-enum.list-properties[0].list-of-enums=<*>"
        );

        assertCompletionsDisplayString("nested-enum.list-properties[0].list-of-enums=<*>",
                "no", "yes"
        );
        assertCompletionsDisplayString("nested-enum.list-properties[0].list-of-enums=yes,<*>",
                "no", "yes"
        );
        assertCompletionsDisplayString("nested-enum.list-properties[0].enum-value=<*>",
                "no", "yes"
        );
        assertCompletionsVariations("nested-enum.list-properties[0].list-of-enums=yes,n<*>", "nested-enum.list-properties[0].list-of-enums=yes,no<*>");
    }

    @Test
    void testEnumMapValueCompletion() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));

        assertCompletionsVariations("foo.nam<*>",
                "foo.name-colors.<*>",
                "foo.color-names.<*>"
        );

        assertCompletionsDisplayString("foo.name-colors.something=<*>",
                "red", "green", "blue"
        );
        assertCompletionsVariations("foo.name-colors.something=G<*>", "foo.name-colors.something=GREEN<*>");
    }

    @Test
    void testEnumMapValueReconciling() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        data("foo.name-colors", "java.util.Map<java.lang.String,demo.Color>", null, "Map with colors in its values");

        assertNotNull(p.getIndex().findType("demo.Color"));

        Editor editor = newEditor(
                "foo.name-colors.jacket=BLUE\n" +
                        "foo.name-colors.hat=RED\n" +
                        "foo.name-colors.pants=GREEN\n" +
                        "foo.name-colors.wrong=NOT_A_COLOR\n"
        );
        editor.assertProblems(
                "NOT_A_COLOR|Color"
        );
    }

    @Test
    void testEnumMapKeyCompletion() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        data("foo.color-names", "java.util.Map<demo.Color,java.lang.String>", null, "Map with colors in its keys");
        data("foo.color-data", "java.util.Map<demo.Color,demo.ColorData>", null, "Map with colors in its keys, and pojo in values");
        assertNotNull(p.getIndex().findType("demo.Color"));
        assertNotNull(p.getIndex().findType("demo.ColorData"));

        //Map Enum -> String:
        assertCompletionsVariations("foo.colnam<*>", "foo.color-names.<*>");
        assertCompletionsVariations("foo.color-names.<*>",
                "foo.color-names.blue=<*>",
                "foo.color-names.green=<*>",
                "foo.color-names.red=<*>"
        );
        assertCompletionsDisplayStringAndDetail("foo.color-names.<*>",
                new String[]{"blue",  "String"},
                new String[]{"green", "String"},
                new String[]{"red", "String"}
        );
        assertCompletionsVariations("foo.color-names.B<*>",
                "foo.color-names.BLUE=<*>"
        );

        //Map Enum -> Pojo:
        assertCompletionsVariations("foo.coldat<*>", "foo.color-data.<*>");
        assertCompletionsVariations("foo.color-data.<*>",
                "foo.color-data.blue.<*>",
                "foo.color-data.green.<*>",
                "foo.color-data.red.<*>"
        );
        assertCompletionsVariations("foo.color-data.B<*>",
                "foo.color-data.BLUE.<*>"
        );
        assertCompletionsDisplayStringAndDetail("foo.color-data.<*>",
                new String[]{"blue",  "demo.ColorData"},
                new String[]{"green", "demo.ColorData"},
                new String[]{"red", "demo.ColorData"}
        );
    }

    @Test
    void testEnumMapKeyReconciling() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));
        assertNotNull(p.getIndex().findType("demo.ColorData"));

        Editor editor = newEditor(
                "foo.color-names.RED=Rood\n" +
                        "foo.color-names.GREEN=Groen\n" +
                        "foo.color-names.BLUE=Blauw\n" +
                        "foo.color-names.NOT_A_COLOR=Wrong\n" +
                        "foo.color-names.BLUE.bad=Blauw\n"
        );
        editor.assertProblems(
                "NOT_A_COLOR|Color",
                "BLUE.bad|Color" //because value type is not dotable the dots will be taken to be part of map key
        );
    }

    @Test
    void testPojoCompletions() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));
        assertNotNull(p.getIndex().findType("demo.ColorData"));

        assertCompletion("foo.dat<*>", "foo.data.<*>");

        assertCompletionsDisplayStringAndDetail("foo.data.",
                new String[]{"children", "List<demo.ColorData>"},
                new String[]{"color-children", "Map<demo.Color[BLUE, GREEN, RED], demo.ColorData>"},
                new String[]{"funky", "boolean"},
                new String[]{"mapped-children", "Map<String, demo.ColorData>"},
                new String[]{"name", "String"},
                new String[]{"nested", "demo.ColorData"},
                new String[]{"next", "demo.Color[BLUE, GREEN, RED]"},
                new String[]{"tags", "List<String>"},
                new String[]{"wavelen", "double"}
        );

        assertCompletionsVariations("foo.data.wav<*>", "foo.data.wavelen=<*>");
        assertCompletionsVariations("foo.data.nam<*>", "foo.data.name=<*>");
        assertCompletionsVariations("foo.data.nex<*>", "foo.data.next=<*>");
        assertCompletionsVariations("foo.data.nes<*>", "foo.data.nested.<*>");
        assertCompletionsVariations("foo.data.chi<*>",
                "foo.data.children[<*>",
                "foo.data.color-children.<*>", //fuzzy
                "foo.data.mapped-children.<*>" //fuzzy
        );
        assertCompletionsVariations("foo.data.tag<*>", "foo.data.tags=<*>");
        assertCompletionsVariations("foo.data.map<*>", "foo.data.mapped-children.<*>");
        assertCompletionsVariations("foo.data.col<*>", "foo.data.color-children.<*>");
    }

    @Test
    void testPojoReconciling() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));
        assertNotNull(p.getIndex().findType("demo.ColorData"));

        Editor editor = newEditor(
                "foo.data.bogus=Something\n" +
                        "foo.data.wavelen=3.0\n" +
                        "foo.data.wavelen=not a double\n" +
                        "foo.data.wavelen.more=3.0\n" +
                        "foo.data.wavelen[0]=3.0\n"
        );
        editor.ignoreProblem(PROP_DUPLICATE_KEY); //ignore deliberate abuse of dups
        editor.assertProblems(
                "bogus|no property",
                "not a double|'double'",
                ".more|Can't use '.' navigation",
                "[0]|Can't use '[..]' navigation"
        );
    }

    @Test
    void testListOfAtomicCompletions() throws Exception {
        data("foo.slist", "java.util.List<java.lang.String>", null, "list of strings");
        data("foo.ulist", "java.util.List<Unknown>", null, "list of strings");
        data("foo.dlist", "java.util.List<java.lang.Double>", null, "list of doubles");
        assertCompletionsVariations("foo.u<*>", "foo.ulist[<*>");
        assertCompletionsVariations("foo.d<*>", "foo.dlist=<*>");
        assertCompletionsVariations("foo.sl<*>", "foo.slist=<*>");
    }

    @Test
    void testMapKeyDotInterpretation() throws Exception {
        //Interpretation of '.' changes depending on the domain type (i.e. when domain type is
        //is a simple type got which '.' navigation is invalid then the '.' is 'eaten' by the key.

        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));
        assertNotNull(p.getIndex().findType("demo.ColorData"));

        data("atommap", "java.util.Map<java.lang.String,java.lang.Integer>", null, "map of atomic data");
        data("objectmap", "java.util.Map<java.lang.String,java.lang.Object>", null, "map of atomic object (recursive map)");
        data("enummap", "java.util.Map<java.lang.String,demo.Color>", null, "map of enums");
        data("pojomap", "java.util.Map<java.lang.String,demo.ColorData>", null, "map of pojos");

        Editor editor = newEditor(
                "atommap.something.with.dots=Vaporize\n" +
                        "atommap.something.with.bracket[0]=Brackelate\n" +
                        "objectmap.other.with.dots=Objectify\n" +
                        "enummap.more.dots=Enumerate\n" +
                        "pojomap.do.some.dots=Pojodot\n" +
                        "pojomap.bracket.and.dots[1]=lala\n" +
                        "pojomap.zozo[2]=lala\n"
        );
        editor.assertProblems(
                "Vaporize|'int'",
                "[0]|Can't use '[..]'",
                //objectmap okay
                "Enumerate|Color",
                "some|no property",
                "and|no property",
                "[2]|Can't use '[..]'"
        );

        assertCompletionsVariations("enummap.more.dots=R<*>",
                "enummap.more.dots=RED<*>",
                "enummap.more.dots=GREEN<*>" //fuzzy match: G(R)EEN
        );
    }

    @Test
    void testMapKeyDotInterpretationInPojo() throws Exception {
        //Similar to testMapKeyDotInterpretation but this time maps are not attached to property
        // directly but via a pojo property

        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));
        assertNotNull(p.getIndex().findType("demo.ColorData"));

        Editor editor = newEditor(
                "foo.color-names.BLUE.dot=Blauw\n" +
                        "foo.color-data.RED.name=Good\n" +
                        "foo.color-data.GREEN.bad=Bad\n" +
                        "foo.color-data.GREEN.wrong[1]=Wrong\n"
        );
        editor.assertProblems(
                "BLUE.dot|Color", //dot is eaten so this is an error
                "bad|no property", //dot not eaten so '.bad' is accessing a property
                "wrong|no property"
        );

        assertCompletionsVariations("foo.color-data.RED.ch<*>",
                "foo.color-data.RED.children[<*>",
                "foo.color-data.RED.color-children.<*>",
                "foo.color-data.RED.mapped-children.<*>"
        );
    }

    @Test
    void testEnumsInLowerCaseReconciling() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.ClothingSize"));

        data("simple.pants.size", "demo.ClothingSize", null, "The simple pant's size");

        Editor editor = newEditor(
                "simple.pants.size=NOT_A_SIZE\n" +
                        "simple.pants.size=EXTRA_SMALL\n" +
                        "simple.pants.size=extra-small\n" +
                        "simple.pants.size=small\n" +
                        "simple.pants.size=SMALL\n"
        );
        editor.ignoreProblem(PROP_DUPLICATE_KEY); //ignore deliberate abuse of dups
        editor.assertProblems(
                "NOT_A_SIZE|ClothingSize"
        );

        editor = newEditor(
                "foo.color-names.red=Rood\n" +
                        "foo.color-names.green=Groen\n" +
                        "foo.color-names.blue=Blauw\n" +
                        "foo.color-names.not-a-color=Wrong\n" +
                        "foo.color-names.blue.bad=Blauw\n"
        );
        editor.assertProblems(
                "not-a-color|Color",
                "blue.bad|Color" //because value type is not dotable the dots will be taken to be part of map key
        );

        editor = newEditor(
                "foo.color-data.red.next=green\n" +
                        "foo.color-data.green.next=not a color\n" +
                        "foo.color-data.red.bogus=green\n" +
                        "foo.color-data.red.name=Rood\n"
        );
        editor.assertProblems(
                "not a color|Color",
                "bogus|no property"
        );
    }

    @Test
    void testEnumsInLowerCaseContentAssist() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);
        assertNotNull(p.getIndex().findType("demo.ClothingSize"));

        data("simple.pants.size", "demo.ClothingSize", null, "The simple pant's size");

        assertCompletionsVariations("simple.pants.size=S<*>",
                "simple.pants.size=SMALL<*>",
                "simple.pants.size=EXTRA_SMALL<*>"
        );
        assertCompletionsVariations("simple.pants.size=s<*>",
                "simple.pants.size=small<*>",
                "simple.pants.size=extra-small<*>"
        );
        assertCompletionsVariations("simple.pants.size=ex<*>",
                "simple.pants.size=extra-large<*>",
                "simple.pants.size=extra-small<*>"
        );
        assertCompletionsVariations("simple.pants.size=EX<*>",
                "simple.pants.size=EXTRA_LARGE<*>",
                "simple.pants.size=EXTRA_SMALL<*>"
        );
        assertCompletionsDisplayString("foo.color=<*>", "red", "green", "blue");

        assertCompletionsVariations("foo.color-data.R<*>",
                "foo.color-data.RED.<*>",
                "foo.color-data.GREEN.<*>"
        );
        assertCompletionsVariations("foo.color-data.r<*>",
                "foo.color-data.red.<*>",
                "foo.color-data.green.<*>"
        );
        assertCompletionsVariations("foo.color-data.<*>",
                "foo.color-data.blue.<*>",
                "foo.color-data.green.<*>",
                "foo.color-data.red.<*>"
        );

        assertCompletionsVariations("foo.color-data.red.na<*>", "foo.color-data.red.name=<*>");
    }

    @Test
    void testNavigationProposalAfterRelaxedPropertyName() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);

        assertCompletionsVariations("foo.colorData.b<*>", "foo.colorData.blue.<*>");
        assertCompletionsVariations("foo.colorData.red.na<*>", "foo.colorData.red.name=<*>");
    }

    @Test
    void testValueProposalAssignedToRelaxedPropertyName() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

        useProject(p);

        data("relaxed-color", "demo.Color", null, "A soothing color");

        assertCompletion("relaxed-color=b<*>", "relaxed-color=blue<*>");
        assertCompletion("relaxedColor=b<*>", "relaxedColor=blue<*>");
    }

    /*
     * TODO: Remove editor.setText(contents) after the call to deprecate(...) once property index listener mechanism is in place
     */
    @Test
    void testReconcileDeprecatedProperty() throws Exception {
        data("error.path", "java.lang.String", null, "Path of the error controller.");
        String contents = "# a comment\n"
                + "error.path=foo\n";
        Editor editor = newEditor(contents);

        deprecate("error.path", "server.error.path", null);
        editor.setText(contents);
        editor.assertProblems(
                "error.path|Deprecated: Use 'server.error.path'"
        //no other problems
        );

        deprecate("error.path", "server.error.path", "This is old.");
        editor.setText(contents);
        editor.assertProblems(
                "error.path|Deprecated: Use 'server.error.path' instead. Reason: This is old."
        //no other problems
        );

        deprecate("error.path", null, "This is old.");
        editor.setText(contents);
        editor.assertProblems(
                "error.path|Deprecated: This is old."
        //no other problems
        );

        deprecate("error.path", null, null);
        editor.setText(contents);
        editor.assertProblems(
                "error.path|Deprecated!"
        //no other problems
        );

    }

    @Test
    void testDeprecatedPropertyCompletion() throws Exception {
        data("error.path", "java.lang.String", null, "Path of the error controller.");
        data("server.error.path", "java.lang.String", null, "Path of the error controller.");
        deprecate("error.path", "server.error.path", "This is old.");
        assertCompletionsDisplayString("error.pa<*>",
                true,
                "server.error.path : String", // should be first because it is not deprecated, even though it is not as good a pattern match
                "error.path : String"
        );
        //TODO: could we check that 'deprecated' completions are formatted with 'strikethrough font?
        assertCompletionDetailsWithDeprecation("error.pa<*>", "server.error.path", "String", null, false);
        assertCompletionDetailsWithDeprecation("error.pa<*>", "error.path", "String",
                "~~error.path~~ \u2192 server.error.path  \n" +
                        "\n" +
                        "Path of the error controller.\n" +
                        "\n" +
                        "**Deprecated:** This is old",
                true);
    }

    @Test
    void testDeprecatedPropertyHoverInfo() throws Exception {
        data("error.path", "java.lang.String", null, "Path of the error controller.");
        Editor editor = newEditor(
                "# a comment\n" +
                        "error.path=foo\n"
        );

        deprecate("error.path", "server.error.path", null);
        editor.assertHoverText("path", "~~error.path~~ \u2192 server.error.path");
        editor.assertHoverText("path", "**Deprecated!**");

        deprecate("error.path", "server.error.path", "This is old.");
        editor.assertHoverText("path", "~~error.path~~ \u2192 server.error.path");
        editor.assertHoverText("path", "**Deprecated:** This is old");

        deprecate("error.path", null, "This is old.");
        editor.assertHoverText("path", "**Deprecated:** This is old");

        deprecate("error.path", null, null);
        editor.assertHoverText("path", "**Deprecated!**");
    }

    @Test
    void testDeprecatedPropertyQuickfix() throws Exception {
        data("error.path", "java.lang.String", null, "Path of the error controller.");
        deprecate("error.path", "server.error.path", null);

        Editor editor = newEditor(
                "# a comment\n" +
                        "error.path=foo\n"
        );

        Diagnostic problem = editor.assertProblem("error.path");
        CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `server.error.path`");
        fix.perform();
        editor.assertText(
                "# a comment\n" +
                        "server.error.path<*>=foo\n"
        );
    }

    @Test
    void testUnknownPropertyCreateMetadataQuickfix() throws Exception {
        // Need something to be in the index otherwise reconciler doesn't looks at the
        // AST
        data("error.title", "java.lang.String", null, "Error's title");

        Editor editor = harness.newEditor("# a comment\n" + "error.something=foo\n");

        Diagnostic problem = editor.assertProblem("something");
        editor.assertFirstQuickfix(problem, "Create metadata for `error.something`");
    }

    @Test
    void testDeprecatedBeanPropertyReconcile() throws Exception {
        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(p);
        data("foo", "demo.Deprecater", null, "A Bean with deprecated properties");

        Editor editor = newEditor(
                "# comment\n" +
                        "foo.name=Old faithfull\n" +
                        "foo.new-name=New and fancy\n" +
                        "foo.alt-name=alternate\n"
        );
        editor.assertProblems(
                "name|Deprecated",
                "alt-name|Property 'alt-name' of type 'demo.Deprecater' is Deprecated: Use 'something.else' instead. Reason: No good anymore"
        );
    }

    @Test
    void testDeprecatedBeanPropertyCompletions() throws Exception {
        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(p);
        data("foo", "demo.Deprecater", null, "A Bean with deprecated properties");

        assertCompletionDetailsWithDeprecation("foo.nam<*>", "new-name", "String", null, false);
        assertCompletionDetailsWithDeprecation("foo.nam<*>", "name", "String", null, true);
        assertCompletionDetailsWithDeprecation("foo.nam<*>", "alt-name", "String", null, true);
    }

    @Test
    void testCharsetCompletions() throws Exception {
        data("foobar.encoding", "java.nio.charset.Charset", null, "The charset-encoding to use for foobars");

        assertCompletions(
                "foobar.enco<*>"
        , // ==>
                "foobar.encoding=<*>"
        );

        assertCompletionWithLabel(
                "foobar.encoding=UT<*>"
        ,
                "UTF-8"
        ,
                "foobar.encoding=UTF-8<*>"
        );
    }

    @Test
    void testLocaleCompletions() throws Exception {
        data("foobar.locale", "java.util.Locale", null, "Yada yada");

        assertCompletions(
                "foobar.loca<*>"
        , // ==>
                "foobar.locale=<*>"
        );

        assertCompletionWithLabel(
                "foobar.locale=en<*>"
        ,
                "en_CA"
        ,
                "foobar.locale=en_CA<*>"
        );
    }

    @Test
    void testPropertyValueHintCompletions() throws Exception {
        //Test that 'value hints' work when property name is associated with 'value' hints.
        // via boot metadata.

        //TODO: this should also work when hints associated with a
        //  map property key
        //  map property value
        //  list property value

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));
        assertCompletionsDisplayString(
                "spring.http.converters.preferred-json-mapper=<*>\n"
        , //=>
                "gson",
                "jackson"
        );
    }

    @Test
    void testPropertyListHintCompletions() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        assertCompletion(
                "management.health.status.ord<*>"
        , //=>
                "management.health.status.order=<*>"
        );

        assertCompletionsDisplayString(
                "management.health.status.order=<*>"
        , //=>
                "DOWN",
                "OUT_OF_SERVICE",
                "UNKNOWN",
                "UP"
        );

        assertCompletionsDisplayString(
                "management.health.status.order=DOWN,<*>"
        , //=>
                "DOWN",
                "OUT_OF_SERVICE",
                "UNKNOWN",
                "UP"
        );
    }

    @Test
    void testPropertyMapValueCompletions() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        assertCompletionsDisplayString(
                "logging.level.some: <*>"
        , // =>
                "trace",
                "debug",
                "info",
                "warn",
                "error",
                "fatal",
                "off"
        );

        assertCompletionsDisplayString(
                "logging.level.some.package: <*>"
        , // =>
                "trace",
                "debug",
                "info",
                "warn",
                "error",
                "fatal",
                "off"
        );
    }

    @Test
    void userDefinedLoggingGroups() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-2.1.0-app"));

        adHocProperties.add("logging.group.foobar");
        adHocProperties.add("logging.group.user-defined");
        adHocProperties.add("logging.group.indexed[0]");
        adHocProperties.add("logging.group.indexed[0]");

        assertCompletionWithLabel(
                "logging.level.<*>"
        , //==============
                "user-defined",
                //=>
                "logging.level.user-defined=<*>"
        );

        assertCompletionWithLabel(
                "logging.level.<*>"
        , //==============
                "foobar",
                //=>
                "logging.level.foobar=<*>"
        );

        assertCompletionWithLabel(
                "logging.level.<*>"
        , //==============
                "indexed",
                //=>
                "logging.level.indexed=<*>"
        );

        //Check whether the added parameter to disable group name hints is obeyed:
        assertNoCompletionWithLabel("logging.group.whatever=<*>", "foobar");
        assertNoCompletionWithLabel("logging.group.whatever=<*>", "user-defined");
        assertNoCompletionWithLabel("logging.group.whatever=<*>", "indexed");
    }

    @Test
    void userDefinedLoggingGroupsValueCompletions() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-2.1.0-app"));

        assertCompletionWithLabel(
                "logging.group.whatever=demo<*>"
        , //==============
                "com.example.demo",
                //=>
                "logging.group.whatever=com.example.demo<*>"
        );

        assertCompletionWithLabel(
                "logging.group.whatever=stuff,demo<*>"
        , //==============
                "com.example.demo",
                //=>
                "logging.group.whatever=stuff,com.example.demo<*>"
        );

        assertCompletionWithLabel(
                "logging.group.whatever[0]=demo<*>"
        , //==============
                "com.example.demo",
                //=>
                "logging.group.whatever[0]=com.example.demo<*>"
        );
    }

    @Test
    void testPropertyMapKeyCompletions() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-2.1.0-app"));
        assertCompletionWithLabel(
                "logging.level.<*>"
        , //==============
                "root",
                //=>
                "logging.level.root=<*>"
        );
        assertCompletionDetails("logging.level.<*>", "root", "String", "Root logger");

        assertCompletionWithLabel(
                "logging.level.r<*>"
        , //==============
                "root",
                //=>
                "logging.level.root=<*>"
        );
        assertCompletionDetails("logging.level.r<*>", "root", "String", "Root logger");

        assertCompletionWithLabel(
                "logging.level.ot<*>"
        , //==============
                "root",
                //=>
                "logging.level.root=<*>"
        );
        assertCompletionDetails("logging.level.ot<*>", "root", "String", "Root logger");

        // 'web' and 'sql' are built-in logger groups as of Spring Boot 2.1
        // See: https://docs.spring.io/spring-boot/docs/2.1.0.RELEASE/reference/htmlsingle/#_logger_name
        assertCompletionWithLabel(
                "logging.level.<*>"
        , //==============
                "web",
                //=>
                "logging.level.web=<*>"
        );
        assertCompletionDetails("logging.level.<*>", "web", "String", "Web logging group");

        assertCompletionWithLabel(
                "logging.level.<*>"
        , //==============
                "sql",
                //=>
                "logging.level.sql=<*>"
        );
        assertCompletionDetails("logging.level.<*>", "sql", "String", "SQL logging group");
    }

    @Test
    void testHandleAsResourceContentAssist() throws Exception {
        //"name": "my.terms-and-conditions",
        //        "providers": [
        //                      {
        //                          "name": "handle-as",
        //                          "parameters": {
        //                              "target": "org.springframework.core.io.Resource"
        //                          }
        //                      }
        //                  ]
        data("my.terms-and-conditions", "java.lang.String", null, "Terms and Conditions text file")
                .provider("handle-as", "target", "org.springframework.core.io.Resource");

        assertCompletionsDisplayString(
                "my.terms-and-conditions=<*>"
        , // =>
                "classpath:",
                "classpath*:",
                "file:",
                "http://",
                "https://",
                "optional:"
        );
    }

    @Test
    void testHandleAsListContentAssist() throws Exception {
        data("my.tosses", "String[]", null, "A sequence of coin tosses")
                .provider("handle-as", "target", "java.lang.Boolean[]");

        assertCompletionsDisplayString(
                "my.tosses[0]=<*>"
        , // =>
                "true",
                "false"
        );
    }


    @Test
    void test_STS_3335_reconcile_list_nested_in_Map_of_String() throws Exception {
        Editor editor;
        useProject(createPredefinedMavenProject("boot-1.3.3-sts-4335"));

        editor = newEditor(
                "test-map.test-list-object.color-list[0]=not-a-color\n" +
                        "test-map.test-list-object.color-list[1]=RED\n" +
                        "test-map.test-list-object.color-list[2]=GREEN\n"
        );
        editor.assertProblems(
                "not-a-color|com.wellsfargo.lendingplatform.web.config.Color"
        );

        editor = newEditor(
                "test-map.test-list-object.string-list[0]=not-a-color\n" +
                        "test-map.test-list-object.string-list[1]=RED\n" +
                        "test-map.test-list-object.string-list[2]=GREEN\n"
        );
        editor.assertProblems(/*NONE*/);
    }


    @Test
    void test_STS_3335_completions_list_nested_in_Map_of_String() throws Exception {
        useProject(createPredefinedMavenProject("boot-1.3.3-sts-4335"));

        assertCompletions(
                "test-map.some-string-key.col<*>"
        , // =>
                "test-map.some-string-key.color-list=<*>"
        );

        assertCompletionsDisplayString(
                "test-map.some-string-key.color-list[0]=<*>\n"
        , // =>
                "red", "green", "blue"
        );

        assertCompletionsDisplayString(
                "test-map.some-string-key.color-list[0]=<*>\n"
        , // =>
                "red", "green", "blue"
        );
    }

    @Test
    void testSimpleResourceCompletion() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        data("my.nice.resource", "org.springframework.core.io.Resource", null, "A very nice resource.");

        assertCompletion(
                "nicer<*>\n"
        , // =>
                "my.nice.resource=<*>\n"
        );

        assertCompletionsDisplayString(
                "my.nice.resource=<*>\n"
        , // =>
                "classpath:",
                "classpath*:",
                "file:",
                "http://",
                "https://",
                "optional:"
        );
    }

    @Test
    void testClasspathResourceCompletion() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(projects.mavenProject("empty-boot-1.3.0-app", WITH_EMPTY_APPLICATION_YML));

        data("my.nice.resource", "org.springframework.core.io.Resource", null, "A very nice resource.");
        data("my.nice.list", "java.util.List<org.springframework.core.io.Resource>", null, "A nice list of resources.");

        assertCompletionsDisplayString(
                "my.nice.resource=classpath:app<*>\n"
        , // =>
                "application.properties",
                "application.yml"
        );

        //Test 'list item' context:

        assertCompletionsDisplayString(
                "my.nice.list[0]=<*>"
        , // =>
                "classpath:",
                "classpath*:",
                "file:",
                "http://",
                "https://",
                "optional:"
        );

        assertCompletionsDisplayString(
                "my.nice.list[0]=classpath:app<*>\n"
        , // =>
                "application.properties",
                "application.yml"
        );

        assertCompletionWithLabel(
                "my.nice.list[0]=classpath:app<*>\n"
        , // ==========
                "application.yml"
        , // =>
                "my.nice.list[0]=classpath:application.yml<*>\n"
        );

        assertCompletionWithLabel(
                "my.nice.list[0]=  classpath:app<*>\n"
        , // ==========
                "application.yml"
        , // =>
                "my.nice.list[0]=  classpath:application.yml<*>\n"
        );

        assertCompletionWithLabel(
                "my.nice.list[0]=classpath:<*>\n"
        , // ==========
                "application.yml"
        , // =>
                "my.nice.list[0]=classpath:application.yml<*>\n"
        );

        //Test 'raw node' context

        // do we find resources in sub-folders too?
        assertCompletionWithLabel(
                "my.nice.resource=classpath:word<*>\n"
        , //===============
                "stuff/wordlist.txt"
        , // =>
                "my.nice.resource=classpath:stuff/wordlist.txt<*>\n"
        );
    }

    @Test
    void testClasspathResourceCompletionInCommaList() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));
        data("my.nice.list", "java.util.List<org.springframework.core.io.Resource>", null, "A nice list of resources.");
        data("my.nice.array", "org.springframework.core.io.Resource[]", null, "A nice array of resources.");

        for (String kind : ImmutableList.of("list", "array")) {
            assertCompletionWithLabel(
                    "my.nice." + kind + "=classpath:<*>"
            , //===========
                    "stuff/wordlist.txt"
            , //=>
                    "my.nice." + kind + "=classpath:stuff/wordlist.txt<*>"
            );

            assertCompletionsDisplayString(
                    "my.nice." + kind + "=<*>"
            , // =>
                    "classpath:",
                    "classpath*:",
                    "file:",
                    "http://",
                    "https://",
                    "optional:"
            );

            assertCompletionWithLabel(
                    "my.nice." + kind + "=classpath:stuff/wordlist.txt,classpath:app<*>"
            , //===========
                    "application.yml"
            , //=>
                    "my.nice." + kind + "=classpath:stuff/wordlist.txt,classpath:application.yml<*>"
            );
        }
    }

    @Test
    void testClassReferenceCompletion() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-with-mongo"));

        assertCompletion(
                "spring.data.mongodb.field-na<*>"
        , // =>
                "spring.data.mongodb.field-naming-strategy=<*>"
        );

        assertCompletionsDisplayString(
                "spring.data.mongodb.field-naming-strategy=<*>"
        , // =>
                "org.springframework.data.mapping.model.CamelCaseAbbreviatingFieldNamingStrategy",
                "org.springframework.data.mapping.model.CamelCaseSplittingFieldNamingStrategy",
                "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy",
                "org.springframework.data.mapping.model.SnakeCaseFieldNamingStrategy"
        );

        assertCompletionWithLabel(
                "spring.data.mongodb.field-naming-strategy=<*>"
        , //=====
                "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy"
        , //=>
                "spring.data.mongodb.field-naming-strategy=org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy<*>"
        );

        //Test what happens when 'target' type isn't on the classpath:
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));
        assertCompletionsDisplayString(
                "spring.data.mongodb.field-naming-strategy=<*>"
        // =>
        /*NONE*/
        );
    }

    @Test
    void testClassReferenceInValueLink() throws Exception {
        Editor editor;
        MavenJavaProject project = createPredefinedMavenProject("empty-boot-1.3.0-with-mongo");
        useProject(project);

        editor = newEditor(
                "#stuff\n" +
                        "spring.data.mongodb.field-naming-strategy=org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy\n" +
                        "#more stuff"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy", project,
                editor.rangeOf("org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy", "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy"),
                "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy");

        //Linking should also work for types that aren't valid based on the constraints

        // java.lang.String commented out - CI build node open JDK comes with no src.zip in the JDK
        // Well... src.zip in the open JDK in the CI is a symlink pointing non-existant file
        editor = newEditor(
                "#stuff\n" +
                        "spring.data.mongodb.field-naming-strategy=java.lang.String\n" +
                        "#more stuff"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "java.lang.String", project,
                editor.rangeOf("java.lang.String", "java.lang.String"),
                "java.lang.String");

        // Instead of java.lang.String
        editor = newEditor(
                "#stuff\n" +
                        "spring.data.mongodb.field-naming-strategy=org.springframework.core.io.Resource\n" +
                        "#more stuff"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "org.springframework.core.io.Resource", project,
                editor.rangeOf("org.springframework.core.io.Resource", "org.springframework.core.io.Resource"),
                "org.springframework.core.io.Resource");

    }

    @Test
    void testCommaListReconcile() throws Exception {
        doCommaCollectionReconcileTest("java.util.List");
    }

    @Test
    void testCommaSetReconcile() throws Exception {
        doCommaCollectionReconcileTest("java.util.Set");
    }

	private void doCommaCollectionReconcileTest(String collectionType) throws Exception {
		Editor editor;
		IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");

		useProject(p);
		assertNotNull(p.getIndex().findType("demo.Color"));

		data("my.colors", collectionType+"<demo.Color>", null, "Ooh! nice colors!");

		editor = newEditor(
				"#comment\n" +
				"my.colors=RED, green, not-a-color , BLUE"
		);
		editor.assertProblems(
				"not-a-color|demo.Color"
		);

		editor = newEditor(
				"my.colors=\\\n" +
				"	red , \\\n" +
				"	green,\\\n" +
				"	bad\n"
		);
		editor.assertProblems( "bad|demo.Color");

		editor = newEditor(
				"my.colors=\\\n" +
				"	red,\\\n" +
				"	green,\\\n" +
				"	blue\n"
		);
		editor.assertProblems(/*no problems*/);

		editor = newEditor(
				"my.colors=\\\n" +
				"	bad,\\\n" +
				"	green,\\\n" +
				"	blue\n"
		);
		editor.assertProblems( "bad|demo.Color");

		editor = newEditor(
				"my.colors=\\\n" +
				"	bad , \\\n" +
				"	green,\\\n" +
				"	blue\n"
		);
		editor.assertProblems( "bad|demo.Color");

		editor = newEditor(
				"my.colors=\\\n" +
				"	red , \\\n" +
				"	green,\\\n" +
				"	bad   \n"
		);
		editor.assertProblems( "bad|demo.Color");

		editor = newEditor(
				"my.colors=red,\n"
		);
		editor.assertProblems( ",|demo.Color");


		editor = newEditor(
				"my.colors=red, \n"
		);
		editor.assertProblems( " |demo.Color");
	}

    @Test
    void testReconcileDuplicateKey() throws Exception {
        Editor editor;
        data("some.property", "java.lang.String", null, "yada");
        data("some.other.property", "java.lang.String", null, "yada");

        editor = newEditor(
                "#comment\n" +
                        "some.property=stuff\n" +
                        "some.other.property=stuff\n" +
                        "some.property=different stuff\n"
        );
        editor.assertProblems(
                "some.property|Duplicate",
                "some.property|Duplicate"
        );

        editor = newEditor(
                "#comment\n" +
                        "some.property = stuff\n" +
                        "some.other.property=stuff\n" +
                        "some.property: different stuff\n" +
                        "some.other.property=stuff\n" +
                        "some.property: different stuff\n"
        );
        editor.assertProblems(
                "some.property|Duplicate",
                "some.other.property|Duplicate",
                "some.property|Duplicate",
                "some.other.property|Duplicate",
                "some.property|Duplicate"
        );
    }

    @Test
    void test_PT_119352965() throws Exception {
        data("some.property", "java.lang.String", null, "Some property to test stuff")
                .valueHint("SOMETHING", "A value for something")
                .valueHint("ALTERNATE", "An alternative value");
        data("some.other.property", "java.lang.String", null, "Another property to test stuff");

        assertCompletionWithLabel(
                "some.property=SOMETHING\n" +
                        "<*>"
        , // ===============
                "some.other.property"
        , // =>
                "some.property=SOMETHING\n" +
                        "some.other.property=<*>"
        );
    }

    @Test
    @Disabled
    void testEnumJavaDocShownInValueContentAssist() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("my.background", "demo.Color", null, "Color to use as default background.");

        assertCompletionDetails(
                "my.background=<*>"
        , // ==========
                "red"
        , // ==>
                "demo.Color[BLUE, GREEN, RED]",
                "Hot and delicious"
        );
    }

    @Test
    @Disabled
    // TODO: Test disabled. Java 17 generates javadoc in a lightly different format. Either support it or perhaps try mockito
    void testEnumJavaDocShownInValueHover() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("my.background", "demo.Color", null, "Color to use as default background.");

        Editor editor;

        editor = newEditor(
                "my.background: RED"
        );
        editor.assertIsHoverRegion("RED");
        editor.assertHoverContains("RED", "Hot and delicious");

        editor = newEditor(
                "my.background=red"
        );
        editor.assertHoverContains("red", "Hot and delicious");
    }

    @Test
    void testEnumInValueLink() throws Exception {
        MavenJavaProject project = createPredefinedMavenProject("enums-boot-1.3.2-app");
        useProject(project);
        data("my.background", "demo.Color", null, "Color to use as default background.");

        Editor editor;

        editor = newEditor(
                "my.background: RED"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "RED", project, editor.rangeOf("RED",  "RED"), field("demo.Color", "RED"));

        editor = newEditor(
                "my.background=red"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "red", project, editor.rangeOf("red",  "red"), field("demo.Color", "RED"));
    }

    @Test
    void testEnumInPojoField() throws Exception {
        MavenJavaProject project = createPredefinedMavenProject("enum-def-nav");
        useProject(project);

        Editor editor;

        editor = newEditor(
                "my.screen.background=green"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "background", project, editor.rangeOf("my.screen.background"),
                method("com.example.demo.MyProperties$Screen", "setBackground", "com.example.demo.Color")
        );
        definitionLinkAsserts.assertLinkTargets(editor, "green", project, editor.rangeOf("green"), field("com.example.demo.Color", "GREEN"));
    }

    @Test
    void testNoHoverForUnrecognizedProperty() throws Exception {
        // Thus property with letter 'g' exists. Important.
        data("debug", "java.lang.String", null, "Some debug property.");
        Editor editor;

        editor = newEditor(
                "ggggg.kkkk: 6754"
        );
        editor.assertNoHover("ggggg.kkkk");
    }

    @Test
    void testEmptyDescriptionHover() throws Exception {
        data("debug", "java.lang.String", null, null);
        Editor editor = newEditor(
                "debug=something\n"
        );
        editor.assertHoverExactText("debug", "**debug**  \n[java.lang.String](null)");
    }

    @Test
    void testSetOfEnumsCompletions() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("my.color-set", "java.util.Set<demo.Color>", null, "Set of colors that can be used.");

        assertCompletions("my.colos<*>",
                "my.color-set=<*>"
        );
        assertCompletions("my.color-set=<*>",
                "my.color-set=blue<*>",
                "my.color-set=green<*>",
                "my.color-set=red<*>"
        );
        assertCompletions("my.color-set=B<*>",
                "my.color-set=BLUE<*>"
        );
        assertCompletions("my.color-set=red,B<*>",
                "my.color-set=red,BLUE<*>"
        );
    }
    
    @Test
    void sequenceOrObject() throws Exception {
        data("my.any", "java.util.Map<java.lang.String,java.lang.Object>", null, "Some map");

        Editor editor = newEditor(
        		"""
        		my.any.entry.value.name=Freddy
        		my.any.entry.value.age=the-age
        		my.any.entry.value.bad=123
        		"""
        );
        editor.assertProblems();

        editor = newEditor(
        		"""
        		my.any.entry[0].value.name=Freddy
        		my.any.entry[0].value.age=the-age
        		my.any.entry[0].value.bad=123
        		"""
        );
        editor.assertProblems();
    }


	////////////// harness code below /////////////////////////

	/**
	 * Like 'assertCompletionsBasic' but places the 'textBefore' in a context
	 * with other text around it... trying several different variations of
	 * text before and after the 'interesting' line.
	 */
	public void assertCompletionsVariations(String textBefore, String... expectTextAfter) throws Exception {
		//Variation 1: by itself
		assertCompletions(textBefore, expectTextAfter);
		//Variation 2: comment text before and after
		assertCompletions("#comment\n"+textBefore+"\n#comment", wrap("#comment\n", expectTextAfter, "\n#comment"));
		//Variation 3: empty lines of text before and after
		assertCompletions("\n"+textBefore+"\n\n", wrap("\n", expectTextAfter, "\n\n"));
		//Variation 3.b: empty lines of text before and single newline after
		assertCompletions("\n"+textBefore+"\n", wrap("\n", expectTextAfter, "\n"));
		//Variation 4: property assignment before and after
		assertCompletions("foo=bar\n"+textBefore+"\nnol=brol", wrap("foo=bar\n", expectTextAfter, "\nnol=brol"));
	}

	private String[] wrap(String before, String[] middle, String after) {
		//"\n"+expectTextAfter+"\n\n"
		String[] result = new String[middle.length];
		for (int i = 0; i < result.length; i++) {
			result[i] =  before+middle[i]+after;
		}
		return result;
	}

	@Override
	protected String getFileExtension() {
		return ".properties";
	}

	@Override
	protected LanguageId getLanguageId() {
		return LanguageId.BOOT_PROPERTIES;
	}

	@Override
	public Editor newEditor(String contents) throws Exception {
		return harness.newEditor(LanguageId.BOOT_PROPERTIES, contents);
	}

}
