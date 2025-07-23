/*******************************************************************************
 * Copyright (c) 2016, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts.field;
import static org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts.method;
import static org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts.type;
import static org.springframework.ide.vscode.languageserver.testharness.Editor.INDENTED_COMPLETION;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.PropertyEditorTestConf;
import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation.Level;
import org.springframework.ide.vscode.boot.editor.harness.AbstractPropsEditorTest;
import org.springframework.ide.vscode.boot.metadata.CachingValueProvider;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.properties.quickfix.MissingPropertyData;
import org.springframework.ide.vscode.commons.jandex.MethodImpl;
import org.springframework.ide.vscode.commons.jandex.TestDataProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.RunnableWithException;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.test.context.junit.jupiter.SpringExtension;


/**
 * This class is a placeholder where we will attempt to copy and port
 * as many tests a possible from
 * org.springframework.ide.eclipse.boot.properties.editor.test.YamlEditorTests
 *
 * @author Kris De Volder
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({PropertyEditorTestConf.class, ApplicationYamlEditorTest.TestConf.class})
public class ApplicationYamlEditorTest extends AbstractPropsEditorTest {

	@Autowired
	private DefinitionLinkAsserts definitionLinkAsserts;

	@Configuration public static class TestConf {
		@Bean LanguageId defaultLanguageId() {
			return LanguageId.BOOT_PROPERTIES_YAML;
		}
		@Bean String defaultFileExtension() {
			return ".yml";
		}
	}

	@BeforeEach
	public void setup() {
		MethodImpl.testDataProvider = new TestDataProvider();
	}

	@AfterEach
	public void cleanups() {
		MethodImpl.testDataProvider = null;
	}

    ////////////////////////////////////////////////////////////////////////////////////////
    
    @Test
    void test_GH_711_error_on_extra_empty_yaml_document() throws Exception {
        //https://github.com/spring-projects/sts4/issues/711
        defaultTestData();
        Editor editor = newEditor(
                "spring:\n" +
                        "  application:\n" +
                        "    name: demo\n" +
                        "---\n" +
                        "spring:\n" +
                        "  profiles:\n" +
                        "    active: dev\n" +
                        "services:\n" +
                        "  common:\n" +
                        "    url: https://getguid.azurewebsites.net\n" +
                        "---\n"
        );
        editor.assertProblems(
                "services|Unknown"
        );
    }

    @Test
    void test_GH_615_deprecated_spring_profiles() throws Exception {
        IJavaProject p = createPredefinedMavenProject("empty-boot-2.4.4-app");
        useProject(p);
        Editor editor = newEditor(
                "logging:\n" +
                        "  file: aaa\n" +
                        "spring:\n" +
                        "  profiles: frodod,aaacc\n" +
                        "  config:\n" +
                        "    activate:\n" +
                        "      on-profile:\n" +
                        "      - frodood"
        );
        editor.assertProblems(
                "file|Deprecated",
                "profiles|Deprecated"
        );
    }


    @Test
    void test_GH_534() throws Exception {
        //See: https://www.pivotaltracker.com/n/projects/1346850/stories/174872660
        IJavaProject p = createPredefinedMavenProject("map-of-pojo");
        useProject(p);
        data("my.props", "java.util.Properties", null, "Properties in java.util.Properties");
        data("my.arr", "java.lang.String[]", null, "Array");

        Editor editor = newEditor(
                "my:\n" +
                        "  arr:\n" +
                        "    index: something\n" +
                        "  props:\n" +
                        "    '[foo.bar]': something\n" +
                        "  map:\n" +
                        "    '[foo.bar]':\n" +
                        "        name: Freddy\n" +
                        "        age: the-age\n" +
                        "        bad: 123"
        );
        editor.assertProblems(
                "index|'int'",
                "the-age|'int'",
                "bad|Person"
        );

        //Also check if completion engine understands the convention
        editor = newEditor(
                "my:\n" +
                        "  map:\n" +
                        "    '[foo.bar]':\n" +
                        "      <*>"
        );
        editor.assertContextualCompletions("<*>",
                // ==>
                "age: <*>",
                "name: <*>"
        );
    }

    @Test
    void test_GH_534_warning() throws Exception {
        //See: https://www.pivotaltracker.com/n/projects/1346850/stories/174872660
        IJavaProject p = createPredefinedMavenProject("map-of-pojo");
        useProject(p);

        //detect values in map keys that are recommended to be escpaped.
        Editor editor = newEditor(
                "my:\n" +
                        "  map:\n" +
                        "    foo.bar:\n" +
                        "      name: jeff\n" +
                        "    '[rab.dab]':\n" + //no warn for this, already escaped! 
                        "      name: jeff\n" +
                        "    yaza:\n" +  //nothing special, so no warning either.
                        "      name: good"
        );
        Diagnostic problem = editor.assertProblems("foo.bar|escape it by surrounding it with '[]'").get(0);
        assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
    }

    @Test
    void escapeValuesWithDots() {
        //now that there is a warning for values with '.' in them we should probably automatically escape them when
        // inserted by a completion.
        
    }


    @Test
    void handleAsKey() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/174954118
        useProject(createPredefinedMavenProject("justauth-example"));

        Editor editor = harness.newEditor(
                "justauth:\n" +
                        "  type:\n" +
                        "    <*>"
        );
        editor.assertContextualCompletions("<*>", "alipay:\n      <*>");

        editor = harness.newEditor(
                "justauth:\n" +
                        "  name: ALIPAY\n" +
                        "  type:\n" +
                        "    alipay:\n" +
                        "      client-id: aaa\n" +
                        "      client-secret: bbb \n" +
                        "      bad: xxxx\n"
        );
        editor.assertProblems("bad|Unknown property");
    }

    @Test
    void GH_190_tolerate_placeholders_without_quotes_integer() throws Exception {
        data("server.port", "java.lang.Integer", null, null);
        Editor editor = harness.newEditor(
                "server:\n" +
                        "  port: @port@\n" +
                        "bad: problem"
        );
        editor.assertProblems(
                "bad|Unknown"
        );
    }

    @Test
    void GH_190_tolerate_placeholders_without_quotes() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/190
        data("info.build", "java.util.Map<String,String>", null, null);
        Editor editor = harness.newEditor(
                "info:\n" +
                        "  build:\n" +
                        "    artifact: @project.artifactId@\n" +
                        "    name: @project.name@\n" +
                        "    description: @project.description@\n" +
                        "    version: @project.version@\n" +
                        "bad: problem"
        );
        editor.assertProblems(
                "bad|Unknown"
        );
    }

    @Test
    void GH_449_inheritedPropertiesInListValues() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/449
        MavenJavaProject p = createPredefinedMavenProject("gh_449");
        useProject(p);

        // Validation
        Editor editor = harness.newEditor(
                "myprops:\n" +
                        "  nested:\n" +
                        "  - foo: fv\n" +
                        "    bar: bv\n" +
                        "    bad: bad"
        );
        editor.assertProblems(
                "bad|Unknown"
        );

        // Completion
        editor = harness.newEditor(
                "myprops:\n" +
                        "  nested:\n" +
                        "  - <*>"
        );
        editor.assertContextualCompletions("<*>"
        , // ==>
                "bar: <*>",
                "foo: <*>"
        );
    }

    @Test
    void GH_427_completionInDollarReference() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server:\n" +
                        "  port: 8006\n" +
                        "\n" +
                        "spring:\n" +
                        "  application:\n" +
                        "    name: dadada\n" +
                        "  cloud:\n" +
                        "    consul:\n" +
                        "      host: localhost\n" +
                        "      port: 8500\n" +
                        "      discovery:\n" +
                        "        service-name: ${<*>}"
        );

        editor.assertContextualCompletions("spring.appnam<*>",
                "spring.application.name<*>",
                "spring.data.rest.page-param-name<*>",
                "spring.jackson.property-naming-strategy<*>"
        );

        editor.assertContextualCompletions("appnam<*>",
                "spring.application.name<*>",
                "spring.data.rest.page-param-name<*>",
                "spring.jackson.property-naming-strategy<*>"
        );

        editor = newEditor(
                "spring:\n" +
                        "  application:\n" +
                        "    name: ${<*>\n"
        );
        editor.assertContextualCompletions("servport<*>",
                "server.port}<*>",
                "server.tomcat.port-header}<*>"
        );
    }

    @Test
    void GH_420_anchorReference() throws Exception {
        Editor editor;
        data("config.bob", "java.lang.String", null, null);
        data("config.dude", "java.lang.String", null, null);

        editor = newEditor(
                "configref: &config\n" +
                        "  bob: bob\n" +
                        "  asdf: dude\n" +
                        "config:\n" +
                        "  <<: *config"
        );
        editor.assertProblems("asdf|Unknown");

        editor = newEditor(
                "configref: &config\n" +
                        "  bob: bob\n" +
                        "  asdf: dude\n" +
                        "config: *config"
        );
        editor.assertProblems(
                "asdf|Unknown"
        );
    }

    @Test
    void GH_404_ConstructorBinding_support() throws Exception {
        useProject(createPredefinedMavenProject("gh_404"));
        Editor editor;

        MethodImpl.testDataProvider.methodParams("Ltest/NestedConfigProperties;.<init>(Ljava/lang/String;)V", "dude");

        editor = newEditor(
                "someConfig:\n" +
                        "  nested:\n" +
                        "    <*>"
        );
        editor.assertCompletionLabels("dude"); //Only the property from constructor binding, not the one from setter!

        editor = newEditor(
                "some-config:\n" +
                        "  nested:\n" +
                        "    <*>"
        );
        editor.assertCompletionLabels("dude"); //Only the property from constructor binding, not the one from setter!

        editor = newEditor(
                "someConfig:\n" +
                        "  nested:\n" +
                        "    bob: bob\n" +
                        "    dude: dude\n" +
                        "    bogus: bad\n"
        );
        editor.assertProblems(
                "bob|Unknown",
                "bogus|Unknown"
        );
    }

    @Test
    void abbreviateLongPrefixCompletions() throws Exception {
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
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      very:\n" +
                        "        bar<*>"
        );
        editor.assertCompletions(
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      very:\n" +
                        "        long:\n" +
                        "          barbar: <*>",
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      very:\n" +
                        "        long:\n" +
                        "          barfoo: <*>",
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      very:\n" +
                        "        long:\n" +
                        "          foobar: <*>",
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      very:\n" +
                        "        long:\n" +
                        "          barbar:\n" +
                        "            more: <*>",
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      very:\n" +
                        "        long:\n" +
                        "          barfoo:\n" +
                        "            more: <*>",
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      very:\n" +
                        "        long:\n" +
                        "          foobar:\n" +
                        "            more: <*>"
        );

        editor.assertCompletionLabels(
                "long.barbar",
                "long.barfoo",
                "long.foobar",
                "long.barbar.more",
                "long.barfoo.more",
                "long.foobar.more"
        );

        editor = newEditor(
                "spring:\n" +
                        "  data:\n" +
                        "    jpa:\n" +
                        "      vr<*>"
        );
        editor.assertCompletionLabels(
                "very.long.barbar",
                "very.long.barfoo",
                "very.long.foobar",
                "very.long.foofoo",
                "very.long.barbar.more",
                "very.long.barfoo.more",
                "very.long.foobar.more",
                "very.long.foofoo.more"
        );

    }


    @Test
    void bug_GH_327() throws Exception {
        //See https://github.com/spring-projects/sts4/issues/327
        data("spring.resources.static-locations", "java.lang.Boolean", null, "Blah");
        data("spring.devtools.restart.additional-paths", "java.lang.Boolean", null, "Blah blah");

        Editor editor;

        // basic check
        editor = harness.newEditor(
                "spring:\n" +
                        "  resources:\n" +
                        "    static_locations: true\n" +
                        "  devtools:\n" +
                        "    restart:\n" +
                        "      additional_paths: false\n"
        );
        editor.assertProblems(/*NONE*/);

        //Also check whether reconciler understands the structure when inside of a 'relaxed' name key 
        editor = harness.newEditor(
                "spring:\n" +
                        "  resources:\n" +
                        "    static_locations: bad\n" +
                        "  devtools:\n" +
                        "    restart:\n" +
                        "      additional_paths: wrong\n"
        );
        editor.assertProblems(
                "bad|boolean",
                "wrong|boolean"
        );
    }

    @Test
    void bug_165724475() throws Exception {
        //See: 
        // https://www.pivotaltracker.com/story/show/165724475
        // https://github.com/spring-projects/spring-ide/issues/376
        
        defaultTestData();

        Editor editor = harness.newEditor(
                "server:\n" +
                        "  port: bork\n" +
                        "logging.level.org.springframework.kafka.listener.[KafkaMessageListenerContainer$ListenerConsumer]: INFO\n" +
                        "bogus: bad"
        );
        editor.assertProblems(
                "bork|Expecting a 'int'",
                "logging.level.org.springframework.kafka.listener.[KafkaMessageListenerContainer$ListenerConsumer]|Unknown property",
                "bogus|Unknown property"
        );
    }

    @Test
    void inheritedPojoProperties() throws Exception {
        //See https://github.com/spring-projects/sts4/issues/116
        useProject(createPredefinedMavenProject("cloud-rabbit-project"));

        Editor editor = newEditor(
                "spring:\n" +
                        "  cloud:\n" +
                        "    stream:\n" +
                        "      rabbit:\n" +
                        "        bindings:\n" +
                        "          input:\n" +
                        "            consumer:\n" +
                        "              auto-bind-dlq: true"
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void bug_158348104() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/158348104
        data("spring.activemq.close-timeout", "java.time.Duration", null, null);

        Editor editor;

        editor = newEditor("<*>\n");
        editor.assertContextualCompletions(ci -> ci.getLabel().contains("spring.activemq.close-timeout"),
                "springactcloti<*>",
                "spring:\n" +
                        "  activemq:\n" +
                        "    close-timeout: <*>"
        );

        editor = newEditor(
                "spring:\n" +
                        "  activemq:\n" +
                        "    close-timeout: garbage"
        );
        editor.assertProblems("garbage|not a valid duration");

        editor = newEditor(
                "spring:\n" +
                        "  activemq:\n" +
                        "    close-timeout: 15s"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "spring:\n" +
                        "  activemq:\n" +
                        "    close-timeout: PT10S" //ISO duration format
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void bug_153144391() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/153144391
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        Editor editor = newEditor(
                "spring:\n" +
                        "  application:\n" +
                        "    name: chatter-web-ui\n" +
                        "  cloud:\n" +
                        "    stream:\n" +
                        "      bindings:\n" +
                        "        output:\n" +
                        "          destination: chat\n" +
                        "        input:\n" +
                        "          destination: chat\n" +
                        "jackloca<*>"
        );
        editor.assertCompletions(INDENTED_COMPLETION,
                "spring:\n" +
                        "  application:\n" +
                        "    name: chatter-web-ui\n" +
                        "  cloud:\n" +
                        "    stream:\n" +
                        "      bindings:\n" +
                        "        output:\n" +
                        "          destination: chat\n" +
                        "        input:\n" +
                        "          destination: chat\n" +
                        "  jackson:\n" +
                        "    locale: <*>"
        );
    }

    @Test
    void linterRunsOnDocumentOpenAndChange() throws Exception {
        Editor editor = newEditor(
                "somemap: val\n" +
                        "- sequence"
        );

        editor.assertProblems(
                "-|expected <block end>"
        );

        editor.setText(
                "- sequence\n" +
                        "zomemap: val"
        );

        editor.assertProblems(
                "z|expected <block end>"
        );
    }

    @Test
    void testJumpyInsertion() throws Exception {
        //This test not working in vscode. It is only meant to work in environment that
        // a) don't apply magic indents
        // b) allow completions with no restrictions on the main edit.
    	String[] names = {"foo", "nested", "bar"};
    	int levels = 4;
    	generateNestedProperties(levels, names, "");

    	//Note: jumpy completions use the snippet '$0' placeholder to move the cursor
    	//Our harness actually does not understand / interpret snippet placeholders.
    	//Thus the examples below expected outcome will have both a '$0' and '<*>'
    	//showing the cursor position. You can think of the second '<*>' as showing
    	//where the cursor will end up when the client doesn't have snippet support capability.

    	assertCompletion(
    			"foo:\n" +
    					"  nested:\n" +
    					"    bar:\n" +
    					"      foo:\n" +
    					"other:\n" +
    					"foo.nested.bar.b<*>"
    					,
    					"foo:\n" +
    							"  nested:\n" +
    							"    bar:\n" +
    							"      foo:\n" +
    							"      bar: $0\n" +
    							"other:<*>"
    			);

    	assertCompletion(
    			"foo:\n" +
    					"  nested:\n" +
    					"    bar:\n" +
    					"      foo:\n" +
    					"other:\n" +
    					"foo.nested.nested.b<*>"
    					,
    					"foo:\n" +
    							"  nested:\n" +
    							"    bar:\n" +
    							"      foo:\n" +
    							"    nested:\n" +
    							"      bar: $0\n" +
    							"other:<*>"
    			);

    	assertCompletion(
    			"foo.nested.nested.b<*>\n" +
    					"foo:\n" +
    					"  nested:\n" +
    					"    bar:\n" +
    					"      foo:\n" +
    					"other:"
    					,
    					"foo:\n" +
    							"  nested:\n" +
    							"    bar:\n" +
    							"      foo:\n" +
    							"    nested:\n" +
    							"      bar: <*>\n" +
    							"other:"
    			);
    }

    @Test
    void almostJumpyCompletion() throws Exception {
        defaultTestData();

        assertCompletion(
                "server:\n" +
                        "  address: bark\n" +
                        "port<*>\n"
        , // ==>
                "server:\n" +
                        "  address: bark\n" +
                        "  port: <*>\n"
        );
    }

    @Test
    void userDefinedLoggingGroupsValueCompletions() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-2.1.0-app"));

        assertCompletionWithLabel(
                "logging:\n" +
                        "  group:\n" +
                        "    whatever:\n" +
                        "    - demo<*>"
        , //==============
                "com.example.demo",
                //=>
                "logging:\n" +
                        "  group:\n" +
                        "    whatever:\n" +
                        "    - com.example.demo<*>"
        );

        assertCompletionWithLabel(
                "logging:\n" +
                        "  group:\n" +
                        "    whatever: demo<*>"
        , //==============
                "com.example.demo",
                //=>
                "logging:\n" +
                        "  group:\n" +
                        "    whatever: com.example.demo<*>"
        );

        assertCompletionWithLabel(
                "logging:\n" +
                        "  group:\n" +
                        "    whatever: stuff,demo<*>"
        , //==============
                "com.example.demo",
                //=>
                "logging:\n" +
                        "  group:\n" +
                        "    whatever: stuff,com.example.demo<*>"
        );

        //Check whether the added parameter to disable group name hints is obeyed:
        assertNoCompletionWithLabel(
                "logging:\n" +
                        "  group:\n" +
                        "    whatever: <*>"
        ,
                "foobar"
        );

    }

    ///////////////////// ported tests from old STS code base ////////////////////////////////////////////////

    @Test
    void testHovers() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "spring:\n" +
                        "  application:\n" +
                        "    name: foofoo\n" +
                        "  beyond:\n" +
                        "    the-valid: range\n" +
                        "    \n" +
                        "server:\n" +
                        "  port: 8888"
        );

        editor.assertIsHoverRegion("name");
        editor.assertIsHoverRegion("port");

        editor.assertHoverContains("name", "**spring.application.name**");
        editor.assertHoverContains("port", "**server.port**");
        editor.assertHoverContains("8888", "**server.port**"); // hover over value show info about corresponding key. Is this logical?

        editor.assertNoHover("beyond");
        editor.assertNoHover("the-valid");
        editor.assertNoHover("range");

        //Note currently, these provide no hovers, but maybe (some of them) should if we index proprty sources and not just the
        // properties themselves.
        editor.assertNoHover("spring");
        editor.assertNoHover("application");
        editor.assertNoHover("server");


        //Test for the case where we can't produce an AST for editor text
        editor = newEditor(
                "- syntax\n" +
                        "error:\n"
        );
        editor.assertNoHover("syntax");
        editor.assertNoHover("error");
    }

    @Test
    void testHoverInfoForEnumValueInMapKey() throws Exception {
        Editor editor;
        IJavaProject project = createPredefinedMavenProject("empty-boot-1.3.0-app");
        useProject(project);

        //This test will fail if source jars haven't been downloaded.
        //Probably that is why it fails in CI build?
        //Let's check:
//		IType type = project.findType("com.fasterxml.jackson.databind.SerializationFeature");

        editor = newEditor(
                "spring:\n" +
                        "  jackson:\n" +
                        "    serialization:\n" +
                        "      indent-output: true"
        );
        editor.assertIsHoverRegion("indent-output");
        editor.assertHoverContains("indent-output", "allows enabling (or disabling) indentation");

        //Also try that it works when spelled in all upper-case
        editor = newEditor(
                "spring:\n" +
                        "  jackson:\n" +
                        "    serialization:\n" +
                        "      INDENT_OUTPUT: true"
        );
        editor.assertIsHoverRegion("INDENT_OUTPUT");
        editor.assertHoverContains("INDENT_OUTPUT", "allows enabling (or disabling) indentation");
    }

    @Test
    void testHoverInfoForEnumValueInMapKeyCompletion() throws Exception {
        IJavaProject project = createPredefinedMavenProject("empty-boot-1.3.0-app");
        useProject(project);

        //This test will fail if source jars haven't been downloaded.
        //Probably that is why it fails in CI build?
        //Let's check:
//		IType type = project.findType("com.fasterxml.jackson.databind.SerializationFeature");

        assertCompletionDetails(
                "spring:\n" +
                        "  jackson:\n" +
                        "    serialization:\n" +
                        "      ind<*>"
        , // ==========
                "indent-output"
        , // ==>
                "boolean",
                "allows enabling (or disabling) indentation"
        );
    }

    @Test
    void testHoverInfoForValueHintCompletion() throws Exception {
        data("my.bonus", "java.lang.String", null, "Bonus type")
                .valueHint("small", "A small bonus. For a little extra incentive.")
                .valueHint("large", "An large bonus. For the ones who deserve it.")
                .valueHint("exorbitant", "Truly outrageous. Who deserves a bonus like that?");

        assertCompletionDetails(
                "my:\n" +
                        "  bonus: l<*>"
        , // ==========
                "large"
        , // ==>
                "String",
                "For the ones who deserve it"
        );
    }

    @Test
    void testHoverInfoForValueHint() throws Exception {
        data("my.bonus", "java.lang.String", null, "Bonus type")
                .valueHint("small", "A small bonus. For a little extra incentive.")
                .valueHint("large", "An large bonus. For the ones who deserve it.")
                .valueHint("exorbitant", "Truly outrageous. Who deserves a bonus like that?");

        Editor editor = newEditor(
                "#comment here\n" +
                        "my:\n" +
                        "  bonus: large\n"
        );

        editor.assertIsHoverRegion("large");
        editor.assertHoverContains("large", "For the ones who deserve it");
    }


    @Disabled
    @Test
    void testUserDefinedHovers() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("foo.link-tester", "demo.LinkTestSubject", null, "for testing different Pojo link cases");
        Editor editor = newEditor(
                "#A comment at the start\n" +
                        "foo:\n" +
                        "  data:\n" +
                        "    wavelen: 666\n" +
                        "    name: foo\n" +
                        "    next: green\n" +
                        "  link-tester:\n" +
                        "    has-it-all: nice\n" +
                        "    strange: weird\n" +
                        "    getter-only: getme\n"
        );

        editor.assertHoverContains("data", "Pojo"); // description from json metadata

        // NOTE: This may be failing because javadoc may not be obtained from a private member
        editor.assertHoverContains("wavelen", "JavaDoc from field"); // javadoc from field
        editor.assertHoverContains("name", "Set the name"); // javadoc from setter
        editor.assertHoverContains("next", "Get the next"); // javadoc from getter

    }

    @Test
    void testUserDefinedLinkTargets() throws Exception {
        MavenJavaProject project = createPredefinedMavenProject("enums-boot-1.3.2-app");
        useProject(project);
        data("foo.link-tester", "demo.LinkTestSubject", null, "for testing different Pojo link cases");
        Editor editor = newEditor(
                "#A comment at the start\n" +
                        "foo:\n" +
                        "  data:\n" +
                        "    wavelen: 666\n" +
                        "    name: foo\n" +
                        "    next: green\n" +
                        "  link-tester:\n" +
                        "    has-it-all: nice\n" +
                        "    strange: weird\n" +
                        "    getter-only: getme\n"
        );

        definitionLinkAsserts.assertLinkTargets(editor, "data", project, editor.rangeOf("data:", "data"), method("demo.FooProperties", "setData", "demo.ColorData"));
        definitionLinkAsserts.assertLinkTargets(editor, "wavelen", project, editor.rangeOf("wavelen:", "wavelen"), method("demo.ColorData", "setWavelen", "double"));
    }

    @Test
    void testInheritedPropertyLinkTarget() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/326
        MavenJavaProject project = createPredefinedMavenProject("super-property-nav-sample");
        useProject(project);

        Editor editor = harness.newEditor(
                "initializr:\n" +
                        "  languages:\n" +
                        "  - name: foo\n" +
                        "    id: yada\n" +
                        "    default: false\n" +
                        "    bogus: whatever\n"
        );

        //io.spring.initializr.metadata.DefaultMetadataElement.setDefault(boolean)
        //io.spring.initializr.metadata.MetadataElement.setId(String)
        //io.spring.initializr.metadata.MetadataElement.setName(String)
        definitionLinkAsserts.assertLinkTargets(editor, "name", project, editor.rangeOf("name:", "name"), method("io.spring.initializr.metadata.MetadataElement", "setName", "java.lang.String"));
        definitionLinkAsserts.assertLinkTargets(editor, "id", project, editor.rangeOf("id:", "id"), method("io.spring.initializr.metadata.MetadataElement", "setId", "java.lang.String"));
        definitionLinkAsserts.assertLinkTargets(editor, "default", project, editor.rangeOf("default:", "default"), method("io.spring.initializr.metadata.DefaultMetadataElement", "setDefault", "boolean"));

        definitionLinkAsserts.assertLinkTargets(editor, "bogus", project, editor.rangeOf("bogus", "bogus") /*NONE*/);
    }

    @Test
    void testHyperlinkTargets() throws Exception {
        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(p);

        Editor editor = newEditor(
                "server:\n" +
                        "  port: 888\n" +
                        "spring:\n" +
                        "  datasource:\n" +
                        "    login-timeout: 1000\n" +
                        "flyway:\n" +
                        "  init-sqls: a,b,c\n"
        );

        definitionLinkAsserts.assertLinkTargets(editor, "port", p,
                editor.rangeOf("port", "port"),
                method("org.springframework.boot.autoconfigure.web.ServerProperties", "setPort", "java.lang.Integer")
        );
        definitionLinkAsserts.assertLinkTargets(editor, "datasource", p,
                editor.rangeOf("datasource"),
                method("org.springframework.boot.autoconfigure.jdbc.DataSourceConfigMetadata", "hikariDataSource"),
                method("org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration", "dataSource"),
                type("org.springframework.boot.autoconfigure.jdbc.DataSourceProperties"),
                method("org.springframework.boot.autoconfigure.jdbc.DataSourceConfigMetadata", "tomcatDataSource"),
                method("org.springframework.boot.autoconfigure.jdbc.DataSourceConfigMetadata", "dbcpDataSource")
        );
        definitionLinkAsserts.assertLinkTargets(editor, "init-sql", p,
                editor.rangeOf("init-sqls", "init-sqls"),
                method("org.springframework.boot.autoconfigure.flyway.FlywayProperties", "setInitSqls", "java.util.List"));
    }
    
    @Test
    void hyperLinksForGroupsWithPrimitiveTypes() throws Exception {
        IJavaProject p = createPredefinedMavenProject("gh-sts4-sample");
        useProject(p);
        
        Editor editor = newEditor("""
				app2:
				  value: true
				  service1:
				    value: false
				    service:
				      host: 1.2.3.4
				      port: 5000
        		"""
        );
        
        definitionLinkAsserts.assertLinkTargets(editor, "app2", p, editor.rangeOf("app2"), type("com.example.demo.Settings2"));
        definitionLinkAsserts.assertLinkTargets(editor, "value", p, editor.rangeOf("value: true", "value"), method("com.example.demo.Settings2", "setValue", "boolean"));
        definitionLinkAsserts.assertLinkTargets(editor, "service1", p, editor.rangeOf("service1"), method("com.example.demo.Settings2", "getService1"));
        definitionLinkAsserts.assertLinkTargets(editor, editor.rangeOf("value: false", "value").getStart(), p, editor.rangeOf("value: false", "value"), method("com.example.demo.Settings2$Service", "setValue", "boolean"));
        definitionLinkAsserts.assertLinkTargets(editor, "service:", p, editor.rangeOf("service:", "service"), method("com.example.demo.Settings2$Service", "getService"));
        definitionLinkAsserts.assertLinkTargets(editor, "host", p, editor.rangeOf("host"), method("com.example.demo.Settings2$Endpoint", "setHost", "java.lang.String"));
        definitionLinkAsserts.assertLinkTargets(editor, "port", p, editor.rangeOf("port"), method("com.example.demo.Settings2$Endpoint", "setPort", "int"));
    }
    
    @Test
    void hyperLinksForGroupsWithoutPrimitiveTypes_1() throws Exception {
        IJavaProject p = createPredefinedMavenProject("gh-sts4-sample");
        useProject(p);
        
        Editor editor = newEditor("""
				app1:
				  service1:
				    service:
				      host: 1.2.3.4
				      port: 5000
        		"""
        );
        
        definitionLinkAsserts.assertLinkTargets(editor, "app1", p, editor.rangeOf("app1"), type("com.example.demo.Settings1"));
        definitionLinkAsserts.assertLinkTargets(editor, "service1", p, editor.rangeOf("service1"), method("com.example.demo.Settings1", "getService1"));
        definitionLinkAsserts.assertLinkTargets(editor, "service:", p, editor.rangeOf("service:", "service"), method("com.example.demo.Settings1$Service", "getService"));
        definitionLinkAsserts.assertLinkTargets(editor, "host", p, editor.rangeOf("host"), method("com.example.demo.Settings1$Endpoint", "setHost", "java.lang.String"));
        definitionLinkAsserts.assertLinkTargets(editor, "port", p, editor.rangeOf("port"), method("com.example.demo.Settings1$Endpoint", "setPort", "int"));
    }

    @Test
    void hyperLinksForGroupsWithoutPrimitiveTypes_2() throws Exception {
        IJavaProject p = createPredefinedMavenProject("gh-sts4-sample");
        useProject(p);
        
        Editor editor = newEditor("""
				app3:
				  value: true
				  service1:
				    host: 1.2.3.4
				    port: 5000
				  service2:
				    host: 5.6.7.8
				    port: 6000
        		"""
        );
        
        definitionLinkAsserts.assertLinkTargets(editor, "app3", p, editor.rangeOf("app3"), type("com.example.demo.Settings3"));
        definitionLinkAsserts.assertLinkTargets(editor, "value", p, editor.rangeOf("value"), method("com.example.demo.Settings3", "setValue", "boolean"));
        definitionLinkAsserts.assertLinkTargets(editor, "service1", p, editor.rangeOf("service1"), method("com.example.demo.Settings3", "getService1"));
        definitionLinkAsserts.assertLinkTargets(editor, editor.rangeOf("host: 1.2.3.4", "host").getStart(), p, editor.rangeOf("host: 1.2.3.4", "host"), method("com.example.demo.Settings3$Endpoint", "setHost", "java.lang.String"));
        definitionLinkAsserts.assertLinkTargets(editor, editor.rangeOf("port: 5000", "port").getStart(), p, editor.rangeOf("port: 5000", "port"), method("com.example.demo.Settings3$Endpoint", "setPort", "int"));
        definitionLinkAsserts.assertLinkTargets(editor, "service2", p, editor.rangeOf("service2"), method("com.example.demo.Settings3", "getService2"));
        definitionLinkAsserts.assertLinkTargets(editor, editor.rangeOf("host: 5.6.7.8", "host").getStart(), p, editor.rangeOf("host: 5.6.7.8", "host"), method("com.example.demo.Settings3$Endpoint", "setHost", "java.lang.String"));
        definitionLinkAsserts.assertLinkTargets(editor, editor.rangeOf("port: 6000", "port").getStart(), p, editor.rangeOf("port: 6000", "port"), method("com.example.demo.Settings3$Endpoint", "setPort", "int"));
    }

    @Test
    void hyperLinksForGroupsWithRecords() throws Exception {
        IJavaProject p = createPredefinedMavenProject("gh-sts4-sample");
        useProject(p);
        
        Editor editor = newEditor("""
				app:
				  service1:
				    service:
				      host: fvdfv
				      port: 76 
        		"""
        );
        
        definitionLinkAsserts.assertLinkTargets(editor, "app", p, editor.rangeOf("app"), type("com.example.demo.Settings"));
        definitionLinkAsserts.assertLinkTargets(editor, "service1", p, editor.rangeOf("service1"), method("com.example.demo.Settings", "getService1"));
        definitionLinkAsserts.assertLinkTargets(editor, "service:", p, editor.rangeOf("service:", "service"), field("com.example.demo.Settings$Service", "service"));
        definitionLinkAsserts.assertLinkTargets(editor, "host", p, editor.rangeOf("host"), field("com.example.demo.Settings$Endpoint", "host"));
        definitionLinkAsserts.assertLinkTargets(editor, "port", p, editor.rangeOf("port"), field("com.example.demo.Settings$Endpoint", "port"));
    }
    
    @Test
    void testReconcile() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server:\n" +
                        "  port: \n" +
                        "    extracrap: 8080\n" +
                        "logging:\n" +
                        "  level:\n" +
                        "    '[com.acme]': INFO\n" +
                        "  snuggem: what?\n" +
                        "bogus:\n" +
                        "  no: \n" +
                        "    good: true\n"
        );
        editor.assertProblems(
                "extracrap: 8080|Expecting a 'int' but got a 'Mapping' node",
                "snuggem|Unknown property",
                "bogus|Unknown property"
        );
    }

    @Test
    void test_STS_4140_StringArrayReconciling() throws Exception {
        defaultTestData();

        Editor editor;

        //Case 0: very focussed test for easy debugging
        editor = newEditor(
                "flyway:\n" +
                        "  schemas: [MEMBER_VERSION, MEMBER]"
        );
        editor.assertProblems(/*NONE*/);

        //Case 1: String[] as a flow sequence
        editor = newEditor(
                "something-bad: wrong\n" +
                        "flyway:\n" +
                        "  url: jdbc:h2:file:~/localdb;IGNORECASE=TRUE;mv_store=false\n" +
                        "  user: admin\n" +
                        "  password: admin\n" +
                        "  schemas: [MEMBER_VERSION, MEMBER]"
        );
        editor.assertProblems(
                "something-bad|Unknown property"
        //No other problems should be reported
        );

        //Case 1: String[] as a block sequence
        editor = newEditor(
                "something-bad: wrong\n" +
                        "flyway:\n" +
                        "  url: jdbc:h2:file:~/localdb;IGNORECASE=TRUE;mv_store=false\n" +
                        "  user: admin\n" +
                        "  password: admin\n" +
                        "  schemas:\n" +
                        "    - MEMBER_VERSION\n" +
                        "    - MEMBER"
        );
        editor.assertProblems(
                "something-bad|Unknown property"
        //No other problems should be reported
        );
    }

    @Test
    void test_STS_4140_StringArrayCompletion() throws Exception {
        defaultTestData();

        assertCompletion(
                "#some comment\n" +
                        "flyway:\n" +
                        "  sch<*>\n"
        , //=>
                "#some comment\n" +
                        "flyway:\n" +
                        "  schemas:\n" +
                        "  - <*>\n"
        );
    }

    @Test
    void testReconcileIntegerScalar() throws Exception {
        data("server.port", "java.lang.Integer", null, "Port of server");
        data("server.threads", "java.lang.Integer", null, "Number of threads for server threadpool");
        Editor editor = newEditor(
                "server:\n" +
                        "  port: \n" +
                        "    8888\n" +
                        "  threads:\n" +
                        "    not-a-number\n"
        );
        editor.assertProblems(
                "not-a-number|Expecting a 'int'"
        );
    }

    @Test
    void testReconcileExpectMapping() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server:\n" +
                        "  - a\n" +
                        "  - b\n"
        );
        editor.assertProblems(
                "- a\n  - b|Expecting a 'Mapping' node but got a 'Sequence' node"
        );
    }

    @Test
    void testReconcileExpectScalar() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server:\n" +
                        "  ? - a\n" +
                        "    - b\n" +
                        "  : c"
        );
        editor.assertProblems(
                "- a\n    - b|Expecting a 'Scalar' node but got a 'Sequence' node"
        );
    }

    @Test
    void testReconcileCamelCaseBasic() throws Exception {
        Editor editor;
        data("something.with-many-parts", "java.lang.Integer", "For testing tolerance of camelCase", null);
        data("something.with-parts.and-more", "java.lang.Integer", "For testing tolerance of camelCase", null);

        //Nothing special... not using camel case
        editor = newEditor(
                "#Comment for good measure\n" +
                        "something:\n" +
                        "  with-many-parts: not-a-number"
        );
        editor.assertProblems(
                "not-a-number|Expecting a 'int'"
        );

        //Now check that reconcile also tolerates camel case and reports no error for it
        editor = newEditor(
                "#Comment for good measure\n" +
                        "something:\n" +
                        "  withManyParts: 123"
        );
        editor.assertProblems(/*NONE*/);

        //Now check that reconciler traverses camelCase and reports errors assigning to camelCase names
        editor = newEditor(
                "#Comment for good measure\n" +
                        "something:\n" +
                        "  withManyParts: not-a-number"
        );
        editor.assertProblems(
                "not-a-number|Expecting a 'int'"
        );

        //Now check also that camelcase tolerance works if its not in the end of the path
        editor = newEditor(
                "#Comment for good measure\n" +
                        "something:\n" +
                        "  withParts:\n" +
                        "    andMore: not-a-number\n" +
                        "    bad: wrong\n" +
                        "    something-bad: also wrong\n"
        );
        editor.assertProblems(
                "not-a-number|Expecting a 'int'",
                "bad|Unknown property",
                "something-bad|Unknown property"
        );
    }

    @Test
    void testContentAssistCamelCaseBasic() throws Exception {
        data("something.with-many-parts", "java.lang.Integer", "For testing tolerance of camelCase", null);
        data("something.with-parts.and-more", "java.lang.Integer", "For testing tolerance of camelCase", null);

        assertCompletion(
                "something:\n" +
                        "  withParts:\n" +
                        "    <*>",
                // =>
                "something:\n" +
                        "  withParts:\n" +
                        "    and-more: <*>"
        );
    }

    @Test
    void testReconcileCamelCaseBeanProp() throws Exception {
        Editor editor;

        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(p);

        data("demo.bean", "demo.CamelCaser", "For testing tolerance of camelCase", null);

        //Nothing special... not using camel case
        editor = newEditor(
                "#Comment for good measure\n" +
                        "demo:\n" +
                        "  bean:\n" +
                        "    the-answer-to-everything: not-a-number\n"
        );
        editor.assertProblems(
                "not-a-number|Expecting a 'int'"
        );

        //Now check that reconcile also tolerates camel case and reports no error for it
        editor = newEditor(
                "#Comment for good measure\n" +
                        "demo:\n" +
                        "  bean:\n" +
                        "    theAnswerToEverything: 42\n"
        );
        editor.assertProblems(/*NONE*/);

        //Now check that reconciler traverses camelCase and reports errors assigning to camelCase names
        editor = newEditor(
                "#Comment for good measure\n" +
                        "demo:\n" +
                        "  bean:\n" +
                        "    theAnswerToEverything: not-a-number\n"
        );
        editor.assertProblems(
                "not-a-number|Expecting a 'int'"
        );

        //Now check also that camelcase tolerance works if its not in the end of the path
        editor = newEditor(
                "#Comment for good measure\n" +
                        "demo:\n" +
                        "  bean:\n" +
                        "    theAnswerToEverything: not-a-number\n" +
                        "    theLeft:\n" +
                        "      bad: wrong\n" +
                        "      theAnswerToEverything: not-this\n"
        );
        editor.assertProblems(
                "not-a-number|Expecting a 'int'",
                "bad|Unknown property",
                "not-this|Expecting a 'int'"
        );
    }

    @Test
    void testContentAssistCamelCaseBeanProp() throws Exception {
        IJavaProject p = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(p);

        data("demo.bean", "demo.CamelCaser", "For testing tolerance of camelCase", null);

        assertCompletion(
                "demo:\n" +
                        "  bean:\n" +
                        "    theLeft:\n" +
                        "      answer<*>\n",
                // =>
                "demo:\n" +
                        "  bean:\n" +
                        "    theLeft:\n" +
                        "      the-answer-to-everything: <*>\n"
        );
    }

    @Disabled
    @Test
    void testContentAssistCamelCaseInsertInExistingContext() throws Exception {
        data("demo-bean.first", "java.lang.String", "For testing tolerance of camelCase", null);
        data("demo-bean.second", "java.lang.String", "For testing tolerance of camelCase", null);

        //Confirm first it works correctly without camelizing complications
//		assertCompletion(
//				"demo-bean:\n" +
//				"  first: numero uno\n" +
//				"something-else: separator\n"+
//				"sec<*>",
//				// =>
//				"demo-bean:\n" +
//				"  first: numero uno\n" +
//				"  second: <*>\n" +
//				"something-else: separator\n"
//		);

        //Now with camels
        assertCompletion(
                "demoBean:\n" +
                        "  first: numero uno\n" +
                        "something-else: separator\n" +
                        "sec<*>",
                // =>
                "demoBean:\n" +
                        "  first: numero uno\n" +
                        "  second: <*>\n" +
                        "something-else: separator\n"
        );
    }


    @Test
    void testReconcileBeanPropName() throws Exception {
        IJavaProject p = createPredefinedMavenProject("boot-1.2.1-app-properties-list-of-pojo");
        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Foo"));
        data("some-foo", "demo.Foo", null, "some Foo pojo property");
        Editor editor = newEditor(
                "some-foo:\n" +
                        "  name: Good\n" +
                        "  bogus: Bad\n" +
                        "  ? - a\n" +
                        "    - b\n" +
                        "  : Weird\n"
        );
        editor.assertProblems(
                "bogus|Unknown property 'bogus' for type 'demo.Foo'",
                "- a\n    - b|Expecting a bean-property name for object of type 'demo.Foo' "
                        + "but got a 'Sequence' node"
        );
    }

    @Test
    void testIgnoreSpringExpression() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "server:\n" +
                        "  port: ${random.int}\n" + //should not be an error
                        "  bad: wrong\n"
        );
        editor.assertProblems(
                "bad|Unknown property"
        );
    }

    @Test
    void testReconcilePojoArray() throws Exception {
        IJavaProject p = createPredefinedMavenProject("boot-1.2.1-app-properties-list-of-pojo");
        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Foo"));

        {
            Editor editor = newEditor(
                    "token-bad-guy: problem\n" +
                            "volder:\n" +
                            "  foo:\n" +
                            "    list:\n" +
                            "      - name: Kris\n" +
                            "        description: Kris\n" +
                            "        roles:\n" +
                            "          - Developer\n" +
                            "          - Admin\n" +
                            "        bogus: Bad\n"
            );

            editor.assertProblems(
                    "token-bad-guy|Unknown property",
                    //'name' is ok
                    //'description' is ok
                    "bogus|Unknown property 'bogus' for type 'demo.Foo'"
            );
        }

        { //Pojo array can also be entered as a map with integer keys

            Editor editor = newEditor(
                    "token-bad-guy: problem\n" +
                            "volder:\n" +
                            "  foo:\n" +
                            "    list:\n" +
                            "      0:\n" +
                            "        name: Kris\n" +
                            "        description: Kris\n" +
                            "        roles:\n" +
                            "          0: Developer\n" +
                            "          one: Admin\n" +
                            "        bogus: Bad\n"
            );

            editor.assertProblems(
                    "token-bad-guy|Unknown property",
                    "one|Expecting a 'int' but got 'one'",
                    "bogus|Unknown property 'bogus' for type 'demo.Foo'"
            );

        }

    }

    @Test
    void testReconcileSequenceGotAtomicType() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    - element\n"
        );
        editor.assertProblems(
                "- element|Expecting a 'boolean' but got a 'Sequence' node"
        );
    }

    @Test
    void testReconcileSequenceGotMapType() throws Exception {
        data("the-map", "java.util.Map<java.lang.String,java.lang.String>", null, "Nice mappy");
        Editor editor = newEditor(
                "the-map:\n" +
                        "  - a\n" +
                        "  - b\n"
        );
        editor.assertProblems(
                "- a\n  - b|Expecting a 'Map<String, String>' but got a 'Sequence' node"
        );
    }

    @Test
    void testEnumPropertyReconciling() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");
        useProject(p);
        assertNotNull(p.getIndex().findType("demo.Color"));

        data("foo.color", "demo.Color", null, "A foonky colour");
        Editor editor = newEditor(
                "foo:\n" +
                        "  color: BLUE\n" +
                        "---\n" +
                        "foo:\n" +
                        "  color: RED\n" +
                        "---\n" +
                        "foo:\n" +
                        "  color: GREEN\n" +
                        "---\n" +
                        "foo:\n" +
                        "  color:\n" +
                        "    bad: BLUE\n" +
                        "---\n" +
                        "foo:\n" +
                        "  color: Bogus\n"
        );

        editor.assertProblems(
                "bad: BLUE|demo.Color",
                "Bogus|demo.Color"
        );
    }

    @Test
    void testReconcileSkipIfNoMetadata() throws Exception {
        Editor editor = newEditor(
                "foo:\n" +
                        "  color: BLUE\n" +
                        "  color: RED\n" + //technically not allowed to bind same key twice but we don' check this
                        "  color: GREEN\n" +
                        "  color:\n" +
                        "    bad: BLUE\n" +
                        "  color: Bogus\n"
        );
        assertTrue(isEmptyMetadata());
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void testReconcileCatchesParseError() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "somemap: val\n" +
                        "- sequence"
        );
        editor.assertProblems(
                "-|expected <block end>"
        );
    }

    @Test
    void testReconcileCatchesScannerError() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "somemap: \"quotes not closed\n"
        );
        editor.assertProblems(
                "|unexpected end of stream"
        );
    }

    @Test
    void testContentAssistSimple() throws Exception {
        defaultTestData();
        Editor editor = newEditor("<*>\n");
        editor.assertContextualCompletions(ci -> ci.getLabel().contains("server.port"),
                "port<*>",
                "server:\n" +
                        "  port: <*>");
        assertCompletion(
                "#A comment\n" +
                        "port<*>",
                "#A comment\n" +
                        "server:\n" +
                        "  port: <*>");
        assertCompletions(
                "server:\n" +
                        "  nonsense<*>\n"
        //=> nothing
        );
    }

    @Test
    void testContentAssistNullContext() throws Exception {
        defaultTestData();
        assertCompletions(
                "#A comment\n" +
                        "foo:\n" +
                        "  data:\n" +
                        "    bogus:\n" +
                        "      <*>"
        // => nothing
        );
    }

    /**
     * https://github.com/spring-projects/sts4/issues/709 
     */
    @Test
    void testGH709() throws Exception {
        data("server.port", "java.lang.Integer", null, "Server http port");

        Editor editor = newEditor("logging:\r\n"
                + "  enabled: true\r\n"
                + "ser<*>");
        editor.assertCompletions("logging:\r\n"
                + "  enabled: true\r\n"
                + "server:\r\n"
                + "  port: <*>");
    }

    @Test
    void testContentAssistNested() throws Exception {
        data("server.port", "java.lang.Integer", null, "Server http port");
        data("server.address", "java.lang.String", "localhost", "Server host address");

        assertCompletion(
                "server:\n" +
                        "  port: 8888\n" +
                        "  <*>"
        ,
                "server:\n" +
                        "  port: 8888\n" +
                        "  address: <*>"
        );

        assertCompletion(
                "server:\n" +
                        "  <*>"
        ,
                "server:\n" +
                        "  address: <*>"
        );

        assertCompletion(
                "server:\n" +
                        "  a<*>"
        ,
                "server:\n" +
                        "  address: <*>"
        );

        assertCompletion(
                "server:\n" +
                        "  <*>\n" +
                        "  port: 8888"
        ,
                "server:\n" +
                        "  address: <*>\n" +
                        "  port: 8888"
        );

        assertCompletion(
                "server:\n" +
                        "  a<*>\n" +
                        "  port: 8888"
        ,
                "server:\n" +
                        "  address: <*>\n" +
                        "  port: 8888"
        );

    }

    @Disabled
    @Test
    void testContentAssistNestedSameLine() throws Exception {
        data("server.port", "java.lang.Integer", null, "Server http port");

        assertCompletion(
                "server: <*>"
        ,
                "server: \n" +
                        "  port: <*>"
        );

        assertCompletion(
                "#something before this stuff\n" +
                        "server: <*>"
        ,
                "#something before this stuff\n" +
                        "server: \n" +
                        "  port: <*>"
        );
    }

    @Disabled
    @Test
    void testContentAssistInsertCompletionElsewhere() throws Exception {
        defaultTestData();

        assertCompletion(
                "server:\n" +
                        "  port: 8888\n" +
                        "  address: \n" +
                        "  servlet-path: \n" +
                        "spring:\n" +
                        "  activemq:\n" +
                        "something-else: great\n" +
                        "aopauto<*>"
        ,
                "server:\n" +
                        "  port: 8888\n" +
                        "  address: \n" +
                        "  servlet-path: \n" +
                        "spring:\n" +
                        "  activemq:\n" +
                        "  aop:\n" +
                        "    auto: <*>\n" +
                        "something-else: great\n"
        );

        assertCompletion(
                "server:\n" +
                        "  address: localhost\n" +
                        "something: nice\n" +
                        "po<*>"
        ,
                "server:\n" +
                        "  address: localhost\n" +
                        "  port: <*>\n" +
                        "something: nice\n"
        );
    }

    @Disabled
    @Test
    void testContentAssistInsertCompletionElsewhereInEmptyParent() throws Exception {
        data("server.port", "java.lang.Integer", null, "Server http port");
        data("server.address", "String", "localhost", "Server host address");

        assertCompletion(
                "#comment\n" +
                        "server:\n" +
                        "something:\n" +
                        "  more\n" +
                        "po<*>"
        ,
                "#comment\n" +
                        "server:\n" +
                        "  port: <*>\n" +
                        "something:\n" +
                        "  more\n"
        );
    }

    @Disabled
    @Test
    void testContentAssistInetAddress() throws Exception {
        //Test that InetAddress is treated as atomic w.r.t indentation

        defaultTestData();
        assertCompletion(
                "#set address of server\n" +
                        "servadd<*>"
        , //=>
                "#set address of server\n" +
                        "server:\n" +
                        "  address: <*>"
        );

        assertCompletion(
                "#set address of server\n" +
                        "server:\n" +
                        "  port: 888\n" +
                        "more: stuff\n" +
                        "servadd<*>"
        , //=>
                "#set address of server\n" +
                        "server:\n" +
                        "  port: 888\n" +
                        "  address: <*>\n" +
                        "more: stuff\n"
        );

    }

    @Disabled
    @Test
    void testContentAssistInsertCompletionElsewhereThatAlreadyExists() throws Exception {
        data("server.port", "java.lang.Integer", null, "Server http port");
        data("server.address", "String", "localhost", "Server host address");

        //inserting something that already exists should just move the cursor to existing node

        assertCompletion(
                "server:\n" +
                        "  port:\n" +
                        "    8888\n" +
                        "  address: localhost\n" +
                        "something: nice\n" +
                        "po<*>"
        ,
                "server:\n" +
                        "  port:\n" +
                        "    <*>8888\n" +
                        "  address: localhost\n" +
                        "something: nice\n"
        );

        assertCompletion(
                "server:\n" +
                        "  port: 8888\n" +
                        "  address: localhost\n" +
                        "something: nice\n" +
                        "po<*>"
        ,
                "server:\n" +
                        "  port: <*>8888\n" +
                        "  address: localhost\n" +
                        "something: nice\n"
        );

        assertCompletion(
                "server:\n" +
                        "  port:\n" +
                        "  address: localhost\n" +
                        "something: nice\n" +
                        "po<*>"
        ,
                "server:\n" +
                        "  port:<*>\n" +
                        "  address: localhost\n" +
                        "something: nice\n"
        );
    }


    @Disabled
    @Test
    void testContentAssistPropertyWithMapType() throws Exception {
        data("foo.mapping", "java.util.Map<java.lang.String,java.lang.String>", null, "Nice little map");

        //Try in-place completion
        assertCompletion(
                "map<*>"
        ,
                "foo:\n" +
                        "  mapping:\n" +
                        "    <*>"
        );

        //Try 'elswhere' completion
        assertCompletion(
                "foo:\n" +
                        "  something:\n" +
                        "more: stuff\n" +
                        "map<*>"
        ,
                "foo:\n" +
                        "  something:\n" +
                        "  mapping:\n" +
                        "    <*>\n" +
                        "more: stuff\n"
        );
    }

    @Disabled
    @Test
    void testContentAssistPropertyWithArrayType() throws Exception {
        data("foo.list", "java.util.List<java.lang.String>", null, "Nice little list");

        //Try in-place completion
        assertCompletion(
                "lis<*>"
        ,
                "foo:\n" +
                        "  list:\n" +
                        "  - <*>"
        );

        //Try 'elsewhere' completion
        assertCompletion(
                "foo:\n" +
                        "  something:\n" +
                        "more: stuff\n" +
                        "lis<*>"
        ,
                "foo:\n" +
                        "  something:\n" +
                        "  list:\n" +
                        "  - <*>\n" +
                        "more: stuff\n"
        );
    }

    @Disabled
    @Test
    void testContentAssistPropertyWithPojoType() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        //Try in-place completion
        assertCompletion(
                "foo.d<*>"
        ,
                "foo:\n" +
                        "  data:\n" +
                        "    <*>"
        );

        //Try 'elsewhere' completion
        assertCompletion(
                "foo:\n" +
                        "  something:\n" +
                        "more: stuff\n" +
                        "foo.d<*>"
        ,
                "foo:\n" +
                        "  something:\n" +
                        "  data:\n" +
                        "    <*>\n" +
                        "more: stuff\n"
        );
    }

    @Disabled
    @Test
    void testContentAssistPropertyWithEnumType() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        //Try in-place completion
        assertCompletion(
                "foo.co<*>"
        ,
                "foo:\n" +
                        "  color: <*>"
        );

        //Try 'elsewhere' completion
        assertCompletion(
                "foo:\n" +
                        "  something:\n" +
                        "more: stuff\n" +
                        "foo.co<*>"
        ,
                "foo:\n" +
                        "  something:\n" +
                        "  color: <*>\n" +
                        "more: stuff\n"
        );
    }

    @Disabled
    @Test
    void testCompletionForExistingGlobalPropertiesAreDemoted() throws Exception {
        data("foo.bar", "java.lang.String", null, null);
        data("foo.buttar", "java.lang.String", null, null);
        data("foo.baracks", "java.lang.String", null, null);
        data("foo.zamfir", "java.lang.String", null, null);
        assertCompletions(
                "foo:\n" +
                        "  bar: nice\n" +
                        "  baracks: full\n" +
                        "something:\n" +
                        "  in: between\n" +
                        "bar<*>",
                //=>
                // buttar
                "foo:\n" +
                        "  bar: nice\n" +
                        "  baracks: full\n" +
                        "  buttar: <*>\n" +
                        "something:\n" +
                        "  in: between",
                // bar (already existed so becomes navigation)
                "foo:\n" +
                        "  bar: <*>nice\n" +
                        "  baracks: full\n" +
                        "something:\n" +
                        "  in: between",
                // baracks (already existed so becomes navigation)
                "foo:\n" +
                        "  bar: nice\n" +
                        "  baracks: <*>full\n" +
                        "something:\n" +
                        "  in: between"
        );
    }

    @Disabled
    @Test
    void testCompletionForExistingBeanPropertiesAreDemoted() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        assertCompletions(
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    <*>",
                ///// non-existing ///
                //color-children
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    color-children:\n" +
                        "      <*>",
                //funky
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    funky: <*>",
                //mapped-children
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    mapped-children:\n" +
                        "      <*>",
                //nested
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    nested:\n" +
                        "      <*>",
                //next
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    next: <*>",
                //tags
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    tags:\n" +
                        "    - <*>",
                //wavelen
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: foo\n" +
                        "    wavelen: <*>",
                ///// existing ////
                //children
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      <*>-\n" +
                        "    name: foo",
                //name
                "foo:\n" +
                        "  data:\n" +
                        "    children:\n" +
                        "      -\n" +
                        "    name: <*>foo"
        );
    }

    @Test
    void testNoCompletionsInsideComments() throws Exception {
        defaultTestData();

        //Ensure this test is not trivially passing because of missing test data
        Editor editor = newEditor("<*>\n");
        editor.assertContextualCompletions(ci -> ci.getLabel().contains("server.port"),
                "po<*>"
        ,
                "server:\n" +
                        "  port: <*>"
        );

        assertNoCompletions(
                "#po<*>"
        );
    }

    @Disabled
    @Test
    void testCompletionsFromDeeplyNestedNode() throws Exception {
        String[] names = {"foo", "nested", "bar"};
        int levels = 4;
        generateNestedProperties(levels, names, "");

        assertCompletionCount(81, // 3^4
                "<*>"
        );

        assertCompletionCount(27, // 3^3
                "#comment\n" +
                        "foo:\n" +
                        "  <*>"
        );

        assertCompletionCount(9, // 3^2
                "#comment\n" +
                        "foo:\n" +
                        "  bar: <*>"
        );

        assertCompletionCount(3,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "      <*>"
        );

        assertCompletionCount(9,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "    <*>"
        );

        assertCompletionCount(27,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "  <*>"
        );

        assertCompletionCount(81,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "<*>"
        );


        assertCompletion(
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "      <*>"
        ,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "      bar: <*>"
        );

        assertCompletion(
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "    <*>"
        ,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "    bar:\n" +
                        "      bar: <*>"
        );

        assertCompletion(
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "  <*>"
        ,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "    bar:\n" +
                        "      bar: <*>\n" +
                        "  "
        );

        assertCompletion(
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "<*>"
        ,
                "#comment\n" +
                        "foo:\n" +
                        "  bar:\n" +
                        "    nested:\n" +
                        "bar:\n" +
                        "  bar:\n" +
                        "    bar:\n" +
                        "      bar: <*>"
        );
    }

    @Disabled
    @Test
    void testInsertCompletionIntoDeeplyNestedNode() throws Exception {
        String[] names = {"foo", "nested", "bar"};
        int levels = 4;
        generateNestedProperties(levels, names, "");

        assertCompletion(
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "bar.foo.nested.b<*>"
        ,
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "bar:\n" +
                        "  foo:\n" +
                        "    nested:\n" +
                        "      bar: <*>"
        );

        assertCompletion(
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "other:\n" +
                        "foo.foo.nested.b<*>"
        ,
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "  foo:\n" +
                        "    nested:\n" +
                        "      bar: <*>\n" +
                        "other:\n"
        );

        assertCompletion(
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "foo.foo.nested.b<*>"
        ,
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "  foo:\n" +
                        "    nested:\n" +
                        "      bar: <*>"
        );

        assertCompletion(
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "other:\n" +
                        "foo.nested.nested.b<*>"
        ,
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "    nested:\n" +
                        "      bar: <*>\n" +
                        "other:\n"
        );

        assertCompletion(
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "foo.nested.nested.b<*>"
        ,
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "    nested:\n" +
                        "      bar: <*>\n"
        );

        assertCompletion(
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "other:\n" +
                        "foo.nested.bar.b<*>"
        ,
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "      bar: <*>\n" +
                        "other:\n"
        );

        assertCompletion(
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "foo.nested.bar.b<*>"
        ,
                "foo:\n" +
                        "  nested:\n" +
                        "    bar:\n" +
                        "      foo:\n" +
                        "      bar: <*>\n"
        );

    }

    @Test
    void testBooleanValueCompletion() throws Exception {
        defaultTestData();
        assertCompletions(
                "liquibase:\n" +
                        "  enabled: <*>",
                "liquibase:\n" +
                        "  enabled: false<*>",
                "liquibase:\n" +
                        "  enabled: true<*>"
        );

        assertCompletions(
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    <*>",
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    false<*>",
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    true<*>"
        );

        assertCompletions(
                "liquibase:\n" +
                        "  enabled: f<*>\n",
                "liquibase:\n" +
                        "  enabled: false<*>\n"
        );

        assertCompletions(
                "liquibase:\n" +
                        "  enabled: t<*>\n",
                "liquibase:\n" +
                        "  enabled: true<*>\n"
        );

        assertCompletions(
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    f<*>\n",
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    false<*>\n"
        );

        assertCompletions(
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    t<*>\n",
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    true<*>\n"
        );

        // booleans can also be completed in upper case?
        assertCompletions(
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    T<*>\n",
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    TRUE<*>\n"
        );

        assertCompletions(
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    F<*>\n",
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    FALSE<*>\n"
        );

        //Dont suggest completion for something that's already complete. Causes odd
        // and annoying behavior, like a completion popup after you stopped typing 'true'
        assertNoCompletions(
                "liquibase:\n" +
                        "  enabled:\n" +
                        "    true<*>"
        );

        //one more... for special char like '-' in the name

        assertCompletions(
                "liquibase:\n" +
                        "  check-change-log-location: t<*>",
                "liquibase:\n" +
                        "  check-change-log-location: true<*>"
        );

        assertCompletions(
                "liquibase:\n" +
                        "  enabled:<*>",
                "liquibase:\n" +
                        "  enabled: false<*>",
                "liquibase:\n" +
                        "  enabled: true<*>"
        );


    }

    @Test
    void testEnumValueCompletion() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("foo.color", "demo.Color", null, "A foonky colour");

        Editor editor = newEditor("<*>\n");
        editor.assertContextualCompletions(ci -> ci.getLabel().equals("foo.color"),
                "foo.c<*>",
                "foo:\n" +
                        "  color: <*>" //Should complete on same line because enums are 'simple' values.
        );

        assertCompletion("foo:\n  color: R<*>", "foo:\n  color: RED<*>");
        assertCompletion("foo:\n  color: G<*>", "foo:\n  color: GREEN<*>");
        assertCompletion("foo:\n  color: B<*>", "foo:\n  color: BLUE<*>");

        assertCompletion("foo:\n  color: r<*>", "foo:\n  color: red<*>");
        assertCompletion("foo:\n  color: g<*>", "foo:\n  color: green<*>");
        assertCompletion("foo:\n  color: b<*>", "foo:\n  color: blue<*>");

        assertCompletionsDisplayString("foo:\n  color: <*>",
                "red", "green", "blue"
        );
    }
    
    @Test
    void testNestedEnumValueCompletion() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        assertCompletion("nested-enum:\n  list-properties:\n    - e<*>", "nested-enum:\n  list-properties:\n    - enum-value: <*>");
        assertCompletion("nested-enum:\n  list-properties:\n    - li<*>", "nested-enum:\n  list-properties:\n    - list-of-enums:\n      - <*>");

        assertCompletion("nested-enum:\n  list-properties:\n    - enum-value: y<*>", "nested-enum:\n  list-properties:\n    - enum-value: yes<*>");
        assertCompletion("nested-enum:\n  list-properties:\n    - list-of-enums:\n      - n<*>", "nested-enum:\n  list-properties:\n    - list-of-enums:\n      - no<*>");

        assertCompletionsDisplayString("nested-enum:\n  list-properties:\n    - list-of-enums:\n      - <*>",
                "no", "yes"
        );
    }

    @Test
    void testEnumMapValueCompletion() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        assertCompletions(
                "foo:\n" +
                        "  nam<*>",
                //==>
                "foo:\n" +
                        "  name-colors:\n" +
                        "    <*>",
                // or
                "foo:\n" +
                        "  color-names:\n" +
                        "    <*>"
        );
        assertCompletionsDisplayString(
                "foo:\n" +
                        "  name-colors:\n" +
                        "    something: <*>",
                //=>
                "red", "green", "blue"
        );
        assertCompletions(
                "foo:\n" +
                        "  name-colors:\n" +
                        "    something: G<*>",
                // =>
                "foo:\n" +
                        "  name-colors:\n" +
                        "    something: GREEN<*>"
        );
    }

    @Test
    void testEnumMapValueReconciling() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("foo.name-colors", "java.util.Map<java.lang.String,demo.Color>", null, "Map with colors in its values");

        Editor editor;

        editor = newEditor(
                "foo:\n" +
                        "  name-colors:\n" +
                        "    jacket: BLUE\n" +
                        "    hat: RED\n" +
                        "    pants: GREEN\n" +
                        "    wrong: NOT_A_COLOR\n"
        );
        editor.assertProblems(
                "NOT_A_COLOR|Color"
        );

        //lowercase enums should work too
        editor = newEditor(
                "foo:\n" +
                        "  name-colors:\n" +
                        "    jacket: blue\n" +
                        "    hat: red\n" +
                        "    pants: green\n" +
                        "    wrong: NOT_A_COLOR\n"
        );
        editor.assertProblems(
                "NOT_A_COLOR|Color"
        );
    }

    @Test
    void testInnerTypeEnumMapKeyCompletion() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        data("foo.notes", "java.util.Map<demo.FooProperties.Weekdays,java.lang.String>", null, "Map weekdays to notes");

        assertCompletionsDisplayString(
                "foo:\n" +
                        "  notes:\n" +
                        "    <*>",
                true,
                //=>
                "monday : String", "tuesday : String", "wednesday : String", "thursday : String", "friday : String", "saturday : String", "sunday : String"
        );
    }
    
    @Test
    void testValueCompletionsOnEscapedKey() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        data("foo.demo", "java.util.Map<java.lang.String,demo.Color>", null, "Map of any string to colors");

        assertCompletions(
                "foo:\n" +
                        "  demo:\n" +
                        "    '[./**/]': <*>",
                //=>
                        "foo:\n" +
                        "  demo:\n" +
                        "    '[./**/]': blue<*>",
                        "foo:\n" +
                        "  demo:\n" +
                        "    '[./**/]': green<*>",
                        "foo:\n" +
                        "  demo:\n" +
                        "    '[./**/]': red<*>"
        );
    }

    @Test
    void testEnumMapKeyCompletion() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        data("foo.color-names", "java.util.Map<demo.Color,java.lang.String>", null, "Map with colors in its keys");
        data("foo.color-data", "java.util.Map<demo.Color,demo.ColorData>", null, "Map with colors in its keys, and pojo in values");

        //Map Enum -> String:
        assertCompletions("foo:\n  colnam<*>",
                "foo:\n" +
                        "  color-names:\n" +
                        "    <*>");
        assertCompletions(
                "foo:\n" +
                        "  color-names:\n" +
                        "    <*>",
                //=>
                "foo:\n" +
                        "  color-names:\n" +
                        "    blue: <*>",
                "foo:\n" +
                        "  color-names:\n" +
                        "    green: <*>",
                "foo:\n" +
                        "  color-names:\n" +
                        "    red: <*>"
        );

        assertCompletionsDisplayString(
                "foo:\n" +
                        "  color-names:\n" +
                        "    <*>",
                true,
                //=>
                "blue : String", "green : String", "red : String"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-names:\n" +
                        "    B<*>",
                "foo:\n" +
                        "  color-names:\n" +
                        "    BLUE: <*>"
        );

        //Map Enum -> Pojo:
        Editor editor = newEditor("<*>\n");
        editor.assertContextualCompletions("foo.coldat<*>",
                "foo:\n" +
                        "  color-data:\n" +
                        "    <*>");
        editor.assertContextualCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    <*>",
                // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    blue:\n" +
                        "      <*>",
                "foo:\n" +
                        "  color-data:\n" +
                        "    green:\n" +
                        "      <*>",
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      <*>"
        );
        editor.assertContextualCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    B<*>",
                //=>
                "foo:\n" +
                        "  color-data:\n" +
                        "    BLUE:\n" +
                        "      <*>"
        );

        editor.assertContextualCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    b<*>",
                //=>
                "foo:\n" +
                        "  color-data:\n" +
                        "    blue:\n" +
                        "      <*>"
        );

        editor.assertContextualCompletions(
                "foo:\n" +
                        "  color-data: b<*>",
                //=>
                "foo:\n" +
                        "  color-data: \n" +
                        "    blue:\n" +
                        "      <*>"
        );

        assertCompletionsDisplayString(
                "foo:\n" +
                        "  color-data:\n" +
                        "    <*>",
                true,
                "red : demo.ColorData", "green : demo.ColorData", "blue : demo.ColorData"
        );

        assertCompletionsDisplayString(
                "foo:\n" +
                        "  color-data: <*>\n",
                true,
                "red : demo.ColorData", "green : demo.ColorData", "blue : demo.ColorData"
        );

    }

    @Test
    void testPojoReconciling() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        Editor editor = newEditor(
                "foo:\n" +
                        "  data:\n" +
                        "    bogus: Something\n" +
                        "    wavelen: 3.0\n" +
                        "    wavelen: not a double\n" +
                        "    wavelen:\n" +
                        "      more: 3.0\n" +
                        "    wavelen:\n" +
                        "      - 3.0\n"
        );
        editor.assertProblems(
                "bogus|Unknown property",
                "wavelen|Duplicate",
                "wavelen|Duplicate",
                "not a double|'double'",
                "wavelen|Duplicate",
                "more: 3.0|Expecting a 'double' but got a 'Mapping' node",
                "wavelen|Duplicate",
                "- 3.0|Expecting a 'double' but got a 'Sequence' node"
        );
    }


    @Test
    void testListOfAtomicCompletions() throws Exception {
        data("foo.slist", "java.util.List<java.lang.String>", null, "list of strings");
        data("foo.ulist", "java.util.List<Unknown>", null, "list of strings");
        data("foo.dlist", "java.util.List<java.lang.Double>", null, "list of doubles");
        assertCompletions("foo:\n  u<*>",
                "foo:\n" +
                        "  ulist:\n" +
                        "  - <*>");
        assertCompletions("foo:\n  d<*>",
                "foo:\n" +
                        "  dlist:\n" +
                        "  - <*>");
        assertCompletions("foo:\n  sl<*>",
                "foo:\n" +
                        "  slist:\n" +
                        "  - <*>");
    }

    @Test
    void testEnumsInLowerCaseReconciling() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        data("simple.pants.size", "demo.ClothingSize", null, "The simple pant's size");

        Editor editor = newEditor(
                "simple:\n" +
                        "  pants:\n" +
                        "    size: NOT_A_SIZE\n" +
                        "    size: EXTRA_SMALL\n" +
                        "    size: extra-small\n" +
                        "    size: small\n" +
                        "    size: SMALL\n"
        );
        editor.assertProblems(
                "size|Duplicate",
                "NOT_A_SIZE|ClothingSize",
                "size|Duplicate",
                "size|Duplicate",
                "size|Duplicate",
                "size|Duplicate"
        );

        editor = newEditor(
                "foo:\n" +
                        "  color-names:\n" +
                        "    red: Rood\n" +
                        "    green: Groen\n" +
                        "    blue: Blauw\n" +
                        "    not-a-color: Wrong\n" +
                        "    blue-bad: Blauw\n" +
                        "    blue:\n" +
                        "      bad: Blauw"
        );
        editor.assertProblems(
                "blue|Duplicate",
                "not-a-color|Color",
                "blue-bad|Color",
                "blue|Duplicate",
                "bad: Blauw|Expecting a 'String' but got a 'Mapping'"
        );

        editor = newEditor(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      next: green\n" +
                        "      next: not a color\n" +
                        "      bogus: green\n" +
                        "      name: Rood\n"
        );
        editor.assertProblems(
                "next|Duplicate",
                "next|Duplicate",
                "not a color|Color",
                "bogus|Unknown property"
        );
    }

    @Disabled
    @Test
    void testEnumsInLowerCaseContentAssist() throws Exception {
        IJavaProject p = createPredefinedMavenProject("enums-boot-1.3.2-app");
        useProject(p);
        assertNotNull(p.getIndex().findType("demo.ClothingSize"));

        data("simple.pants.size", "demo.ClothingSize", null, "The simple pant's size");

        assertCompletions(
                "simple:\n" +
                        "  pants:\n" +
                        "    size: S<*>",
                "simple:\n" +
                        "  pants:\n" +
                        "    size: SMALL<*>",
                "simple:\n" +
                        "  pants:\n" +
                        "    size: EXTRA_SMALL<*>"
        );

        assertCompletions(
                "simple:\n" +
                        "  pants:\n" +
                        "    size: s<*>",
                "simple:\n" +
                        "  pants:\n" +
                        "    size: small<*>",
                "simple:\n" +
                        "  pants:\n" +
                        "    size: extra-small<*>"
        );

        assertCompletions(
                "simple:\n" +
                        "  pants:\n" +
                        "    size: ex<*>",
                // =>
                "simple:\n" +
                        "  pants:\n" +
                        "    size: extra-large<*>",
                // or
                "simple:\n" +
                        "  pants:\n" +
                        "    size: extra-small<*>"
        );

        assertCompletions(
                "simple:\n" +
                        "  pants:\n" +
                        "    size: EX<*>",
                // =>
                "simple:\n" +
                        "  pants:\n" +
                        "    size: EXTRA_LARGE<*>",
                // or
                "simple:\n" +
                        "  pants:\n" +
                        "    size: EXTRA_SMALL<*>"
        );

        assertCompletionsDisplayString("foo:\n  color: <*>", "red", "green", "blue");

        assertCompletions("foo:\n  color-data: B<*>", "foo:\n  color-data: \n    BLUE:\n      <*>");
        assertCompletions("foo:\n  color-data: b<*>", "foo:\n  color-data: \n    blue:\n      <*>");
        assertCompletions("foo:\n  color-data: <*>",
                "foo:\n  color-data: \n    blue:\n      <*>",
                "foo:\n  color-data: \n    green:\n      <*>",
                "foo:\n  color-data: \n    red:\n      <*>"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red: na<*>",
                "foo:\n" +
                        "  color-data:\n" +
                        "    red: \n" +
                        "      name: <*>"
        );
    }

    @Disabled
    @Test
    void testPojoInListCompletion() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));

        assertCompletion(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red: chi<*>"
        , // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    red: \n" +
                        "      children:\n" +
                        "      - <*>"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "        - nex<*>",
                // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "        - next: <*>"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "        - nes<*>",
                // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "        - nested:\n" +
                        "            <*>"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "      - nex<*>",
                // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "      - next: <*>"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "      - nes<*>",
                // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "      - nested:\n" +
                        "          <*>"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "        - next: RED\n" +
                        "          wav<*>",
                // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "        - next: RED\n" +
                        "          wavelen: <*>"
        );

        assertCompletions(
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "      - next: RED\n" +
                        "        wav<*>",
                // =>
                "foo:\n" +
                        "  color-data:\n" +
                        "    red:\n" +
                        "      children:\n" +
                        "      - next: RED\n" +
                        "        wavelen: <*>"
        );
    }

    @Disabled
    @Test
    void test_STS4111_NoEmptyLinesGapBeforeInsertedCompletion() throws Exception {
        data("spring.application.name", "java.lang.String", null, "The name of the application");
        data("spring.application.index", "java.lang.Integer", true, "App instance index");
        data("spring.considerable.fun", "java.lang.Boolean", true, "Whether the spring fun is considerable");

        assertCompletions(
                "spring:\n" +
                        "  application:\n" +
                        "    index: 12\n" +
                        "\n" +
                        "server:\n" +
                        "  port: 8888\n" +
                        "appname<*>"
        , //=>
                "spring:\n" +
                        "  application:\n" +
                        "    index: 12\n" +
                        "    name: <*>\n" +
                        "\n" +
                        "server:\n" +
                        "  port: 8888"

        );

        //Also test that:
        // - Fully commented lines also count as gaps
        // - Gaps of more than one line are also handled correctly
        assertCompletions(
                "# spring stuff\n" +
                        "spring:\n" +
                        "  application:\n" +
                        "    index: 12\n" +
                        "\n" +
                        "#server stuff\n" +
                        "server:\n" +
                        "  port: 8888\n" +
                        "cfun<*>"
        , //=>
                "# spring stuff\n" +
                        "spring:\n" +
                        "  application:\n" +
                        "    index: 12\n" +
                        "  considerable:\n" +
                        "    fun: <*>\n" +
                        "\n" +
                        "#server stuff\n" +
                        "server:\n" +
                        "  port: 8888"

        );

    }

    @Test
    void testDocumentSeparator() throws Exception {
        defaultTestData();

        assertCompletion(
                "flyway:\n" +
                        "  encoding: utf8\n" +
                        "---\n" +
                        "flyena<*>",
                // =>
                "flyway:\n" +
                        "  encoding: utf8\n" +
                        "---\n" +
                        "flyway:\n" +
                        "  enabled: <*>"
        );
    }

    @Test
    void testMultiProfileYamlReconcile() throws Exception {
        Editor editor;
        //See https://issuetracker.springsource.com/browse/STS-4144
        defaultTestData();

        //Narrowly focussed test-case for easier debugging
        editor = newEditor(
                "spring:\n" +
                        "  profiles: seven\n"
        );
        editor.assertProblems(/*NONE*/);

        //More complete test
        editor = newEditor(
                "spring:\n" +
                        "  profiles: seven\n" +
                        "server:\n" +
                        "  port: 7777\n" +
                        "---\n" +
                        "spring:\n" +
                        "  profiles: eight\n" +
                        "server:\n" +
                        "  port: 8888\n" +
                        "bogus: bad"
        );
        editor.assertProblems(
                "bogus|Unknown property"
        );
    }

    @Test
    void testReconcileArrayOfPrimitiveType() throws Exception {
        data("my.array", "int[]", null, "A primitive array");
        data("my.boxarray", "int[]", null, "An array of boxed primitives");
        data("my.list", "java.util.List<java.lang.Integer>", null, "A list of boxed types");

        Editor editor = newEditor(
                "my:\n" +
                        "  array:\n" +
                        "    - 777\n" +
                        "    - bad\n" +
                        "    - 888"
        );
        editor.assertProblems(
                "bad|Expecting a 'int'"
        );

        editor = newEditor(
                "my:\n" +
                        "  boxarray:\n" +
                        "    - 777\n" +
                        "    - bad\n" +
                        "    - 888"
        );
        editor.assertProblems(
                "bad|Expecting a 'int'"
        );

        editor = newEditor(
                "my:\n" +
                        "  list:\n" +
                        "    - 777\n" +
                        "    - bad\n" +
                        "    - 888"
        );
        editor.assertProblems(
                "bad|Expecting a 'int'"
        );
    }

    @Disabled
    @Test
    void test_STS4231() throws Exception {
        //Should the 'predefined' project need to be recreated... use the commented code below:
//		BootProjectTestHarness projectHarness = new BootProjectTestHarness(ResourcesPlugin.getWorkspace());
//		IJavaProject project = projectHarness.createBootProject("boot-1.3.0-app-sts-4231",
//				bootVersionAtLeast("1.3.0"),
//				withStarters("web", "cloud-config-server")
//		);

        //For more robust test use predefined project which is not so much a moving target:
        IJavaProject project = createPredefinedMavenProject("boot-1.3.0-app-sts-4231");
        useProject(project);

        assertCompletionsDisplayString(
                "info:\n" +
                        "  component: Config Server\n" +
                        "spring:\n" +
                        "  application:\n" +
                        "    name: configserver\n" +
                        "  jmx:\n" +
                        "    default-domain: cloud.config.server\n" +
                        "  cloud:\n" +
                        "    config:\n" +
                        "      server:\n" +
                        "        git:\n" +
                        "          uri: https://github.com/spring-cloud-samples/config-repo\n" +
                        "          repos:\n" +
                        "            my-repo:\n" +
                        "              <*>\n",
                // ==>
                "name : String",
                "pattern : String[]"
        );

        assertCompletion(
                "info:\n" +
                        "  component: Config Server\n" +
                        "spring:\n" +
                        "  application:\n" +
                        "    name: configserver\n" +
                        "  jmx:\n" +
                        "    default-domain: cloud.config.server\n" +
                        "  cloud:\n" +
                        "    config:\n" +
                        "      server:\n" +
                        "        git:\n" +
                        "          uri: https://github.com/spring-cloud-samples/config-repo\n" +
                        "          repos:\n" +
                        "            my-repo:\n" +
                        "              p<*>\n",
                // ==>
                "info:\n" +
                        "  component: Config Server\n" +
                        "spring:\n" +
                        "  application:\n" +
                        "    name: configserver\n" +
                        "  jmx:\n" +
                        "    default-domain: cloud.config.server\n" +
                        "  cloud:\n" +
                        "    config:\n" +
                        "      server:\n" +
                        "        git:\n" +
                        "          uri: https://github.com/spring-cloud-samples/config-repo\n" +
                        "          repos:\n" +
                        "            my-repo:\n" +
                        "              pattern:\n" +
                        "              - <*>\n"
        );

    }

    @Test
    void test_STS_4254_MapStringObjectReconciling() throws Exception {
        Editor editor;
        data("info", "java.util.Map<java.lang.String,java.lang.Object>", null, "Info for the actuator's info endpoint.");

        editor = newEditor(
                "info: not-a-map\n"
        );
        editor.assertProblems(
                "not-a-map|Expecting a 'Map<String, Object>'"
        );

        editor = newEditor(
                "info:\n" +
                        "  build: \n" +
                        "    artifact: foo-bar\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "info:\n" +
                        "  more: \n" +
                        "    deeply:\n" +
                        "      nested: foo-bar\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "booger: Bad\n" +
                        "info:\n" +
                        "  akey: avalue\n"
        );
        editor.assertProblems(
                "booger|Unknown property"
        );
    }

    @Test
    void test_STS_4254_MapStringStringReconciling() throws Exception {
        Editor editor;
        data("info", "java.util.Map<java.lang.String,java.lang.String>", null, "Info for the actuator's info endpoint.");

        editor = newEditor(
                "info:\n" +
                        "  build: \n" +
                        "    artifact: foo-bar\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "info:\n" +
                        "  more: \n" +
                        "    deeply:\n" +
                        "      nested: foo-bar\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "booger: Bad\n" +
                        "info:\n" +
                        "  akey: avalue\n"
        );
        editor.assertProblems(
                "booger|Unknown property"
        );

    }

    @Test
    void test_STS_4254_MapStringIntegerReconciling() throws Exception {
        Editor editor;
        data("info", "java.util.Map<java.lang.String,java.lang.Integer>", null, "Info for the actuator's info endpoint.");

        editor = newEditor(
                "info:\n" +
                        "  build: \n" +
                        "    foo-bar\n"
        );
        editor.assertProblems(
                "foo-bar|Expecting a 'int'"
        );

        editor = newEditor(
                "info:\n" +
                        "  build: 123\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "info:\n" +
                        "  build: \n" +
                        "    number: 123\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = newEditor(
                "info:\n" +
                        "  build: \n" +
                        "    artifact: abc\n"
        );
        editor.assertProblems(
                "abc|Expecting a 'int'"
        );

        //A more complex example for good measure
        editor = newEditor(
                "info:\n" +
                        "  some: \n" +
                        "    nested: foo\n" +
                        "    and: bar\n" +
                        "  or: 444\n" +
                        "  also: bad\n"
        );
        editor.assertProblems(
                "foo|Expecting a 'int'",
                "bar|Expecting a 'int'",
                "bad|Expecting a 'int'"
        );
    }

    /*
     * TODO: Remove editor.setText(contents) after the call to deprecate(...) once property index listener mechanism is in place
     */
    @Test
    void testDeprecatedReconcileProperty() throws Exception {
        data("error.path", "java.lang.String", null, "Path of the error controller.");

        String contents = "# a comment\n" +
                "error:\n" +
                "  path: foo\n";

        Editor editor = newEditor(
                contents
        );

        deprecate("error.path", "server.error.path", null);
        editor.setText(contents);
        editor.assertProblems(
                "path|'error.path' is Deprecated: Use 'server.error.path'"
        //no other problems
        );

        deprecate("error.path", "server.error.path", "This is old.");
        editor.setText(contents);
        editor.assertProblems(
                "path|'error.path' is Deprecated: Use 'server.error.path' instead. Reason: This is old."
        //no other problems
        );

        deprecate("error.path", null, "This is old.");
        editor.setText(contents);
        editor.assertProblems(
                "path|'error.path' is Deprecated: This is old."
        //no other problems
        );

        deprecate("error.path", null, null);
        editor.setText(contents);
        editor.assertProblems(
                "path|'error.path' is Deprecated!"
        //no other problems
        );
    }

    @Test
    void testDeprecatedPropertyCompletion() throws Exception {
        data("error.path", "java.lang.String", null, "Path of the error controller.");
        data("server.error.path", "java.lang.String", null, "Path of the error controller.");
        deprecate("error.path", "server.error.path", "This is old.");
        assertCompletionsDisplayString(
                "errorpa<*>",
                true, // =>
                "server.error.path : String", // should be first because it is not deprecated, even though it is not as good a pattern match
                "error.path : String"
        );
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
                        "error:\n" +
                        "  path: foo\n"
        );

        deprecate("error.path", "server.error.path", null);
        editor.assertHoverContains("path", "~~error.path~~ \u2192 server.error.path");
        editor.assertHoverContains("path", "**Deprecated!**");

        deprecate("error.path", "server.error.path", "This is old.");
        editor.assertHoverContains("path", "~~error.path~~ \u2192 server.error.path");
        editor.assertHoverContains("path", "**Deprecated:** This is old");

        deprecate("error.path", null, "This is old.");
        editor.assertHoverContains("path", "**Deprecated:** This is old");

        deprecate("error.path", null, null);
        editor.assertHoverContains("path", "**Deprecated!**");
    }

    @Test
    void testDeprecatedBeanPropertyHoverInfo() throws Exception {
        IJavaProject jp = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(jp);
        data("foo", "demo.Deprecater", null, "A bean with deprecated property.");
        Editor editor = newEditor(
                "# a comment\n" +
                        "foo:\n" +
                        "  name: foo\n"
        );

        editor.assertHoverContains("name", "**Deprecated!**");
    }

    @Test
    void testDeprecatedBeanPropertyReconcile() throws Exception {
        IJavaProject jp = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(jp);
        data("foo", "demo.Deprecater", null, "A Bean with deprecated properties");

        Editor editor = newEditor(
                "# comment\n" +
                        "foo:\n" +
                        "  name: Old faithfull\n" +
                        "  new-name: New and fancy\n" +
                        "  alt-name: alternate\n"
        );
        editor.assertProblems(
                "name|Property 'name' of type 'demo.Deprecater' is Deprecated!",
                "alt-name|Deprecated"
        );

        editor = newEditor(
                "# comment\n" +
                        "foo:\n" +
                        "  alt-name: alternate\n"
        );
        //check that message also contains reason and replacement infos.
        editor.assertProblems(
                "alt-name|Use 'something.else' instead"
        );
        editor.assertProblems(
                "alt-name|No good anymore"
        );


    }

    @Test
    void testDeprecatedBeanPropertyCompletions() throws Exception {
        IJavaProject jp = createPredefinedMavenProject("tricky-getters-boot-1.3.1-app");
        useProject(jp);
        data("foo", "demo.Deprecater", null, "A Bean with deprecated properties");

        assertCompletionDetailsWithDeprecation("foo:\n  nam<*>", "new-name", "String", null, false);
        assertCompletionDetailsWithDeprecation("foo:\n  nam<*>", "name", "String", null, true);
        assertCompletionDetailsWithDeprecation("foo:\n  nam<*>", "alt-name", "String", null, true);
    }

    @Test
    void missingPropertyQuickfix_1() throws Exception {
        IJavaProject p = createPredefinedMavenProject("empty-boot-2.1.0-app");
        useProject(p);
        File location = new File(p.getLocationUri());

        File metadata_rsrc = new File(location, "src/main/resources/META-INF/additional-spring-configuration-metadata.json");
        File metadata_java = new File(location, "src/main/java/META-INF/additional-spring-configuration-metadata.json");

        assertFalse(
                metadata_rsrc.exists()
        );
        assertFalse(
                metadata_java.exists()
        );
        try {
            Editor editor = newEditor(
                    "myapp:\n" +
                            "  orders:\n" +
                            "    pages: 10"
            );
            Diagnostic problem = editor.assertProblems("myapp|Unknown").get(0);
            CodeAction fix = editor.assertQuickfixes(problem, "Create metadata for `myapp.orders.pages`").get(0);
            assertEquals("myapp.orders.pages", getMissingPropertyName(fix));

            fix.perform();
            assertTrue(metadata_rsrc.exists());
            assertEquals(
                    "{\"properties\": [{\n" +
                            "  \"name\": \"myapp.orders.pages\",\n" +
                            "  \"type\": \"java.lang.String\",\n" +
                            "  \"description\": \"A description for 'myapp.orders.pages'\"\n" +
                            "}]}",
                    FileUtils.readFileToString(metadata_rsrc, "utf8")
            );

        } finally {
            FileUtils.deleteQuietly(new File(location, "src/main/resources/META-INF"));
            FileUtils.deleteQuietly(new File(location, "src/main/java/META-INF"));
        }
    }
	
	private String getMissingPropertyName(CodeAction fix) {
		Command command = fix.getCommand();
		String cmd = command.getCommand();
		assertEquals("sts.vscode-spring-boot.codeAction", cmd);
		List<Object> args = command.getArguments();
		assertEquals("MISSING_PROPERTY_APP", args.get(0));
		MissingPropertyData data = (MissingPropertyData) args.get(1);
		return data.getProperty();
	}

    @Test
    void missingPropertyQuickfix_2() throws Exception {
        IJavaProject p = createPredefinedMavenProject("empty-boot-2.1.0-app");
        useProject(p);
        File location = new File(p.getLocationUri());

        File metadata_rsrc = new File(location, "src/main/resources/META-INF/additional-spring-configuration-metadata.json");
        File metadata_java = new File(location, "src/main/java/META-INF/additional-spring-configuration-metadata.json");

        assertFalse(
                metadata_rsrc.exists()
        );
        assertFalse(
                metadata_java.exists()
        );
        try {
            Editor editor = newEditor(
                    "myapp:\n" +
                            "  orders:\n" +
                            "    pageSize: 10"
            );
            Diagnostic problem = editor.assertProblems("myapp|Unknown").get(0);
            CodeAction fix = editor.assertQuickfixes(problem, "Create metadata for `myapp.orders.page-size`").get(0);
            assertEquals("myapp.orders.page-size", getMissingPropertyName(fix));
        } finally {
            FileUtils.deleteQuietly(new File(location, "src/main/resources/META-INF"));
            FileUtils.deleteQuietly(new File(location, "src/main/java/META-INF"));
        }
    }

    @Test
    void missingPropertyQuickfix_3() throws Exception {
        IJavaProject p = createPredefinedMavenProject("empty-boot-2.1.0-app");
        useProject(p);
        File location = new File(p.getLocationUri());

        File metadata_rsrc = new File(location, "src/main/resources/META-INF/additional-spring-configuration-metadata.json");
        File metadata_java = new File(location, "src/main/java/META-INF/additional-spring-configuration-metadata.json");

        assertFalse(
                metadata_rsrc.exists()
        );
        assertFalse(
                metadata_java.exists()
        );
        try {

            Editor editor = newEditor(
                    "myapp:\n" +
                            "  orders:\n" +
                            "    pageSize: 10\n" +
                            "    start: 0\n"
            );
            Diagnostic problem = editor.assertProblems("myapp|Unknown").get(0);
            editor.assertQuickfixes(problem,
                    "Create metadata for `myapp.orders.page-size`",
                    "Create metadata for `myapp.orders.start`"
            );

        } finally {
            FileUtils.deleteQuietly(new File(location, "src/main/resources/META-INF"));
            FileUtils.deleteQuietly(new File(location, "src/main/java/META-INF"));
        }
    }


    @Test
    void testDeprecatedPropertyQuickfixSimple() throws Exception {
        //A simple case for starters. The path edits aren't too complicated since there's
        //just the one property in the file and only the last part of the 'path' changes.
        //So this is a simple 'in-place' edit.

        data("my.old-name", "java.lang.String", null, "Old and deprecated name");
        deprecate("my.old-name", "my.new-name", null);

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  old-name: foo\n"
            );

            Diagnostic problem = editor.assertProblem("old-name");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `my.new-name`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "my:\n" +
                            "  new-name: foo<*>\n"
            );
        }

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  old-name: foo\n" +
                            "your: stuff"
            );

            Diagnostic problem = editor.assertProblem("old-name");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `my.new-name`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "my:\n" +
                            "  new-name: foo<*>\n" +
                            "your: stuff"
            );
        }

        {	// Don't move prop if it doesn't need to be moved
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  old-name: foo\n" +
                            "  other: bar\n"
            );

            Diagnostic problem = editor.assertProblem("old-name");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `my.new-name`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "my:\n" +
                            "  new-name: foo<*>\n" +
                            "  other: bar\n"
            );
        }
    }

    @Test
    void testDeprecatedPropertyQuickfixMovingValue() throws Exception {
        data("my.old-name", "java.lang.String", null, "Old and deprecated name");
        deprecate("my.old-name", "your.new-name", null);

        data("my.stuff", "java.lang.String", null, "Old and deprecated name");
        deprecate("my.stuff", "my.for-sale.stuff", null);

        data("my.long.path.with.many.pieces", "java.lang.String", null, "Old");
        deprecate("my.long.path.with.many.pieces", "my.path.with.many.pieces", null);

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  long:\n" +
                            "    path:\n" +
                            "      with:\n" +
                            "        many:\n" +
                            "          pieces: foo\n"
            );

            Diagnostic problem = editor.assertProblem("pieces");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `my.path.with.many.pieces`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "my:\n" +
                            "  path:\n" +
                            "    with:\n" +
                            "      many:\n" +
                            "        pieces: foo<*>"
            );
        }

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  long:\n" +
                            "    path:\n" +
                            "      with:\n" +
                            "        many:\n" +
                            "          cannot: remove this\n" +
                            "          pieces: foo\n"
            );

            Diagnostic problem = editor.assertProblem("pieces");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `my.path.with.many.pieces`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "my:\n" +
                            "  long:\n" +
                            "    path:\n" +
                            "      with:\n" +
                            "        many:\n" +
                            "          cannot: remove this\n" +
                            "  path:\n" +
                            "    with:\n" +
                            "      many:\n" +
                            "        pieces: foo<*>"
            );
        }

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  stuff: foo\n"
            );

            Diagnostic problem = editor.assertProblem("stuff");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `my.for-sale.stuff`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "my:\n" +
                            "  for-sale:\n" +
                            "    stuff: foo<*>"
            );
        }

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  old-name: foo\n"
            );

            Diagnostic problem = editor.assertProblem("old-name");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `your.new-name`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "your:\n" +
                            "  new-name: foo<*>"
            );
        }

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  old-name: foo\n" +
                            "your:\n" +
                            "  goodies: nice"
            );

            Diagnostic problem = editor.assertProblem("old-name");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `your.new-name`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "your:\n" +
                            "  goodies: nice\n" +
                            "  new-name: foo<*>"
            );
        }

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "your:\n" +
                            "  goodies: nice\n" +
                            "my:\n" +
                            "  other: stuff\n" +
                            "  old-name: foo\n"
            );

            Diagnostic problem = editor.assertProblem("old-name");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `your.new-name`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "your:\n" +
                            "  goodies: nice\n" +
                            "  new-name: foo<*>\n" +
                            "my:\n" +
                            "  other: stuff"
            );
        }
    }

    @Test
    void testDeprecatedPropertyQuickfixMovingIndentedValue() throws Exception {
        data("my.old-name", "java.lang.String", null, "Old and deprecated name");
        deprecate("my.old-name", "your.new-name", null); //same indent level

        data("my.stuff", "java.lang.String", null, "Old and deprecated name");
        deprecate("my.stuff", "my.long.path.with.many.pieces", null); // deeper indent level

        data("my.long.path.with.many.pieces", "java.lang.String", null, "Old");
        deprecate("my.long.path.with.many.pieces", "short.path", null); //shallower level

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  long:\n" +
                            "    path:\n" +
                            "      with:\n" +
                            "        many:\n" +
                            "          pieces: >\n" +
                            "            foo spread over\n" +
                            "            several lines\n" +
                            "            of text\n" +
                            "short:\n" +
                            "  stuff: goes here\n"
            );
            Diagnostic problem = editor.assertProblem("pieces");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `short.path`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "short:\n" +
                            "  stuff: goes here\n" +
                            "  path: >\n" +
                            "    foo spread over\n" +
                            "    several lines\n" +
                            "    of text<*>\n"
            );
        }

        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  long:\n" +
                            "    bits: go here\n" +
                            "  stuff: >\n" +
                            "    foo spread over\n" +
                            "      several lines\n" +
                            "     of text\n"
            );
            Diagnostic problem = editor.assertProblem("stuff");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `my.long.path.with.many.pieces`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "my:\n" +
                            "  long:\n" +
                            "    bits: go here\n" +
                            "    path:\n" +
                            "      with:\n" +
                            "        many:\n" +
                            "          pieces: >\n" +
                            "            foo spread over\n" +
                            "              several lines\n" +
                            "             of text<*>"
            );
        }
        {
            Editor editor = newEditor(
                    "# a comment\n" +
                            "my:\n" +
                            "  long:\n" +
                            "    path:\n" +
                            "      with:\n" +
                            "        many:\n" +
                            "          pieces:\n" +
                            "# some\n" +
                            "          - foo spread over\n" +
                            "              #confusing\n" +
                            "          - several lines\n" +
                            "            #comments\n" +
                            "          - of text\n" +
                            "short:\n" +
                            "  stuff: goes here\n"
            );
            Diagnostic problem = editor.assertProblem("pieces");
            CodeAction fix = editor.assertFirstQuickfix(problem, "Replace with `short.path`");
            fix.perform();
            editor.assertText(
                    "# a comment\n" +
                            "short:\n" +
                            "  stuff: goes here\n" +
                            "  path:\n" +
                            "  # some\n" +
                            "  - foo spread over\n" +
                            "      #confusing\n" +
                            "  - several lines\n" +
                            "    #comments\n" +
                            "  - of text<*>\n"
            );
        }
    }

    @Test
    void testReconcileDuplicateProperties() throws Exception {
        defaultTestData();
        Editor editor = newEditor(
                "spring:\n" +
                        "  profiles: cloudfoundry\n" +
                        "spring:  \n" +
                        "  application:\n" +
                        "    name: eureka"
        );
        editor.assertProblems(
                "spring|Duplicate",
                "spring|Duplicate"
        );
    }

    @Test
    void testReconcileDuplicatePropertiesNested() throws Exception {
        data("foo.person.name", "String", null, "Name of person");
        data("foo.person.family", "String", null, "Family name of person");
        Editor editor = newEditor(
                "foo:\n" +
                        "  person:\n" +
                        "    name: Hohohoh\n" +
                        "  person:\n" +
                        "    family:\n"
        );
        editor.assertProblems(
                "person|Duplicate",
                "person|Duplicate"
        );
    }

    @Test
    void testReconcileDuplicatePropertiesInBean() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("some.color", "demo.ColorData", null, "Some info about a color.");
        Editor editor = newEditor(
                "some:\n" +
                        "  color:\n" +
                        "    name: RED\n" +
                        "    name: GREEN\n"
        );
        editor.assertProblems(
                "name|Duplicate",
                "name|Duplicate"
        );
    }

    @Test
    void testCharSetCompletions() throws Exception {
        data("foobar.encoding", "java.nio.charset.Charset", null, "The charset-encoding to use for foobars");

        assertCompletions(
                "foobar:\n" +
                        "  enco<*>"
        , // ==>
                "foobar:\n" +
                        "  encoding: <*>"
        );

        assertCompletionWithLabel(
                "foobar:\n" +
                        "  encoding: UT<*>"
        ,
                "UTF-8"
        ,
                "foobar:\n" +
                        "  encoding: UTF-8<*>"
        );
    }

    @Test
    void testLocaleCompletions() throws Exception {
        data("foobar.locale", "java.util.Locale", null, "The locale for foobars");

        assertCompletions(
                "foobar:\n" +
                        "  loca<*>"
        , // ==>
                "foobar:\n" +
                        "  locale: <*>"
        );

        assertCompletionWithLabel(
                "foobar:\n" +
                        "  locale: en<*>"
        ,
                "en_CA"
        ,
                "foobar:\n" +
                        "  locale: en_CA<*>"
        );
    }

    @Test
    void testMimeTypeCompletions() throws Exception {
        data("foobar.mime", "org.springframework.util.MimeType", null, "The mimetype for foobars");

        assertCompletions(
                "foobar:\n" +
                        "  mi<*>"
        , // ==>
                "foobar:\n" +
                        "  mime: <*>"
        );

        assertCompletionWithLabel(
                "foobar:\n" +
                        "  mime: json<*>"
        ,
                "application/json; charset=utf-8"
        ,
                "foobar:\n" +
                        "  mime: application/json; charset=utf-8<*>"
        );
    }

    @Test
    void testPropertyValueHintCompletions() throws Exception {
        //Test that 'value hints' work when property name is associated with 'value' hints.
        // via boot metadata.
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        assertCompletionsDisplayString(
                "spring:\n" +
                        "  http:\n" +
                        "    converters:\n" +
                        "      preferred-json-mapper: <*>\n"
        , //=>
                "gson",
                "jackson"
        );
    }

    @Test
    void testPropertyListHintCompletions() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        assertCompletion(
                "management:\n" +
                        "  health:\n" +
                        "    status:\n" +
                        "      ord<*>"
        , //=>
                "management:\n" +
                        "  health:\n" +
                        "    status:\n" +
                        "      order:\n" +
                        "      - <*>"
        );

        assertCompletionsDisplayString(
                "management:\n" +
                        "  health:\n" +
                        "    status:\n" +
                        "      order:\n" +
                        "      - <*>"
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
                "logging:\n" +
                        "  level:\n" +
                        "    some.package: <*>"
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
    void testYamlExtensionAccepted() throws Exception {
        data("server.port", "java.lang.Integer", null, "Port of server");
        Editor editor;

        // Hovers
        editor = harness.newEditorWithExt(LanguageId.BOOT_PROPERTIES_YAML, ".yaml",
                "server:\n" +
                        "  port: blah"
        );
        editor.assertHoverContains("port", "Port of server");

        //Reconcile
        editor = harness.newEditorWithExt(LanguageId.BOOT_PROPERTIES_YAML, ".yaml",
                "server:\n" +
                        "  porter: blah"
        );
        editor.assertProblems("porter|Unknown");

        //Completions
        editor = harness.newEditorWithExt(LanguageId.BOOT_PROPERTIES_YAML, ".yaml",
                "server:\n" +
                        "  p<*>"
        );

        editor.assertCompletionLabels("port");


    }

    @Test
    void testPropertyMapKeyCompletions() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));
        assertCompletionWithLabel(
                "logging:\n" +
                        "  level:\n" +
                        "    roo<*>"
        , // =>
                "root"
        ,
                "logging:\n" +
                        "  level:\n" +
                        "    root: <*>"
        );
        assertCompletionDetails(
                "logging:\n" +
                        "  level:\n" +
                        "    roo<*>"
        , // =>
                "root",
                "String",
                null
        );
    }
    
    @Test
    void testRecordReconcile() throws Exception {
        MavenJavaProject p = createPredefinedMavenProject("record-props");
		useProject(p);

        Editor editor = newEditor(
                "application:\n" +
                "  temp:\n" + 
                "    user:\n" +
                "      name: something\n"
        );
        editor.assertProblems();
        
        definitionLinkAsserts.assertLinkTargets(editor, "name", p, editor.rangeOf("name"),
                field("com.record.props.UserProperties", "name")
        );

    }

    @Test
    void testRecordReconcileExtra() throws Exception {
        MavenJavaProject p = createPredefinedMavenProject("record-props");
		useProject(p);

        Editor editor = newEditor(
                "application:\n" + 
                "  security:\n" +
                "    users:\n" +
                "    - name: something\n"
        );
        editor.assertProblems();

        definitionLinkAsserts.assertLinkTargets(editor, "name", p, editor.rangeOf("name"),
                field("com.record.props.UserProperties", "name")
        );
    }
    
    @Test
    void testRecordCompletions() throws Exception {
        useProject(createPredefinedMavenProject("record-props"));

        assertCompletionsDisplayString(
        		"application:\n" +
        		"  temp:\n" +
        		"    user:\n" +
        		"      <*>"
        , // =>
                "name",
                "password",
                "roles"
        );

        assertCompletionsDisplayString(
        		"application:\n" +
        		"  security:\n" +
        		"    users:\n" +
        		"    - <*>"
        , // =>
                "name",
                "password",
                "roles"
        );
    }


    @Test
    void testEscapeStringValueStartingWithStar() throws Exception {
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));
        assertCompletions(
                "endpoints:\n" +
                        "  cors:\n" +
                        "    allowed-headers: \n" +
                        "      - <*>"
        , // =>
                "endpoints:\n" +
                        "  cors:\n" +
                        "    allowed-headers: \n" +
                        "      - '*'<*>"
        );
    }

    @Test
    void testEscapeStringValueWithAQuote() throws Exception {
        data("foo.quote", "java.lang.String", null, "Character to used to surround quotes");
        valueHints("foo.quote", "\"", "'", "`");

        assertCompletions(
                "foo:\n" +
                        "  quote: <*>"
        , // =>
                "foo:\n" +
                        "  quote: '\"'<*>"
        ,
                "foo:\n" +
                        "  quote: ''''<*>"
        ,
                "foo:\n" +
                        "  quote: '`'<*>"
        );
    }

    @Disabled
    @Test
    void testEscapeStringKeyWithAQuote() throws Exception {
        data("foo.quote", "java.util.Map<java.lang.String,java.lang.String>", null, "Name of quote characters");
        keyHints("foo.quote", "\"", "'", "`");

        assertCompletions(
                "foo:\n" +
                        "  quote: <*>"
        , // =>
                "foo:\n" +
                        "  quote: \n" +
                        "    '\"': <*>"
        ,
                "foo:\n" +
                        "  quote: \n" +
                        "    '''': <*>"
        ,
                "foo:\n" +
                        "  quote: \n" +
                        "    '`': <*>"
        );
    }

    @Test
    void testLoggerNameCompletion() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20); // the provider can't be reliably tested if its not allowed to
        // fetch all its values (even though in 'production' you
        // wouldn't want it to block the UI thread for this long.
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));
        //Finds a package:
        assertCompletionWithLabel(
                "logging:\n" +
                        "  level:\n" +
                        "    boot.auto<*>"
        , //-----------------
                "org.springframework.boot.autoconfigure"
        , // =>
                "logging:\n" +
                        "  level:\n" +
                        "    '[org.springframework.boot.autoconfigure]': <*>"
        );
        assertCompletionDetails(
                "logging:\n" +
                        "  level:\n" +
                        "    boot.auto<*>"
        , //-----------------
                "org.springframework.boot.autoconfigure", "String", null);

        //Finds a type:
        assertCompletionWithLabel(
                "logging:\n" +
                        "  level:\n" +
                        "    MesgSource<*>"
        , //-----------------
                "org.springframework.boot.autoconfigure.MessageSourceAutoConfiguration"
        , // =>
                "logging:\n" +
                        "  level:\n" +
                        "    '[org.springframework.boot.autoconfigure.MessageSourceAutoConfiguration]': <*>"
        );
        assertCompletionDetails(
                "logging:\n" +
                        "  level:\n" +
                        "    MesgSource<*>"
        , //-----------------
                "org.springframework.boot.autoconfigure.MessageSourceAutoConfiguration",
                "String",
                null
        );
    }

    @Test
    void testSimpleResourceCompletion() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        data("my.nice.resource", "org.springframework.core.io.Resource", null, "A very nice resource.");

        assertCompletion(
                "my:\n" +
                        "  nice:\n" +
                        "    <*>\n"
        , // =>
                "my:\n" +
                        "  nice:\n" +
                        "    resource: <*>\n"
        );

        assertCompletionsDisplayString(
                "my:\n" +
                        "  nice:\n" +
                        "    resource: <*>\n"
        , // =>
                "classpath:",
                "classpath*:",
                "file:",
                "http://",
                "https://",
                "optional:"
        );

        assertCompletionsDisplayString(
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      <*>\n"
        , // =>
                "classpath:",
                "classpath*:",
                "file:",
                "http://",
                "https://",
                "optional:"
        );

    }

    /**
     * TODO: Delete this tempotary test.
     * This test is just one piece copied from another test to do more focussed debugging. If you find this test
     * in the git repo, then it was committed by accident. So feel free to delete it.
     */
    @Test
    void testClasspathResourceCompletionTemp() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        data("my.nice.resource", "org.springframework.core.io.Resource", null, "A very nice resource.");
        data("my.nice.list", "java.util.List<org.springframework.core.io.Resource>", null, "A nice list of resources.");

        assertCompletionWithLabel(
                "my:\n" +
                        "  nice:\n" +
                        "    list:\n" +
                        "    - classpath:<*>\n"
        , // ==========
                "application.yml"
        , // =>
                "my:\n" +
                        "  nice:\n" +
                        "    list:\n" +
                        "    - classpath:application.yml<*>\n"
        );

    }

    @Test
    void testClasspathResourceCompletion() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));

        data("my.nice.resource", "org.springframework.core.io.Resource", null, "A very nice resource.");
        data("my.nice.list", "java.util.List<org.springframework.core.io.Resource>", null, "A nice list of resources.");

        //Test 'simple key context'

        assertCompletionsDisplayString(
                "my:\n" +
                        "  nice:\n" +
                        "    resource: classpath:app<*>\n"
        , // =>
                "application.properties",
                "application.yml"
        );

        //Test 'list item' context:

        assertCompletionsDisplayString(
                "my:\n" +
                        "  nice:\n" +
                        "    list:\n" +
                        "    - classpath:app<*>\n"
        , // =>
                "application.properties",
                "application.yml"
        );

        assertCompletionWithLabel(
                "my:\n" +
                        "  nice:\n" +
                        "    list:\n" +
                        "    - classpath:app<*>\n"
        , // ==========
                "application.yml"
        , // =>
                "my:\n" +
                        "  nice:\n" +
                        "    list:\n" +
                        "    - classpath:application.yml<*>\n"
        );

        assertCompletionWithLabel(
                "my:\n" +
                        "  nice:\n" +
                        "    list:\n" +
                        "    - classpath:<*>\n"
        , // ==========
                "application.yml"
        , // =>
                "my:\n" +
                        "  nice:\n" +
                        "    list:\n" +
                        "    - classpath:application.yml<*>\n"
        );

        //Test 'raw node' context

        assertCompletionsDisplayString(
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      classpath:app<*>\n"
        , // =>
                "application.properties",
                "application.yml"
        );

        assertCompletionWithLabel(
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      classpath:app<*>\n"
        , //===============
                "application.properties"
        , // =>
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      classpath:application.properties<*>\n"
        );

        assertCompletionWithLabel(
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      classpath:<*>\n"
        , //===============
                "application.properties"
        , // =>
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      classpath:application.properties<*>\n"
        );

        // do we find resources in sub-folders too?
        assertCompletionWithLabel(
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      classpath:word<*>\n"
        , //===============
                "stuff/wordlist.txt"
        , // =>
                "my:\n" +
                        "  nice:\n" +
                        "    resource:\n" +
                        "      classpath:stuff/wordlist.txt<*>\n"
        );
    }

    @Test
    void testCompletionsInContextWithDuplicateKey() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/135708013
        defaultTestData();

        assertCompletions(
                "spring:\n" +
                        "  application:\n" +
                        "    name: my-app\n" +
                        "spring:\n" +
                        "  activemq:\n" +
                        "    broker-u<*>"
        , // ==>
                "spring:\n" +
                        "  application:\n" +
                        "    name: my-app\n" +
                        "spring:\n" +
                        "  activemq:\n" +
                        "    broker-url: <*>"
        );
    }

    @Test
    void testClassReferenceCompletion() throws Exception {
        CachingValueProvider.TIMEOUT = Duration.ofSeconds(20);

        useProject(createPredefinedMavenProject("empty-boot-1.3.0-with-mongo"));

        assertCompletion(
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-na<*>"
        , // =>
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy: <*>"
        );

        assertCompletionsDisplayString(
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy: <*>"
        , // =>
                "org.springframework.data.mapping.model.CamelCaseAbbreviatingFieldNamingStrategy",
                "org.springframework.data.mapping.model.CamelCaseSplittingFieldNamingStrategy",
                "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy",
                "org.springframework.data.mapping.model.SnakeCaseFieldNamingStrategy"
        );

        assertCompletionWithLabel(
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy: <*>"
        , //=====
                "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy"
        , //=>
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy: org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy<*>"
        );

        //Test what happens when 'target' type isn't on the classpath:
        useProject(createPredefinedMavenProject("empty-boot-1.3.0-app"));
        assertCompletionsDisplayString(
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy: <*>"
        // =>
        /*NONE*/
        );
    }

    @Test
    void definitionLinks_bug_169240253_nested() throws Exception {
        MavenJavaProject p = createPredefinedMavenProject("boot-web-actuator-2.2.0");
        //See: https://www.pivotaltracker.com/story/show/169240253
        useProject(p);
        Editor editor = newEditor(
                "management:\n" +
                        "  endpoints:\n" +
                        "    web:\n" +
                        "      exposure:\n" +
                        "        exclude: '*'\n" +
                        "spring:\n" +
                        "  messages:\n" +
                        "    basename: messages/messages\n"
        );
        //org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties.Exposure.setExclude(Set<String>)
        definitionLinkAsserts.assertLinkTargets(editor, "exclude", p, editor.rangeOf("exclude"),
                method("org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties$Exposure", "setExclude", "java.util.Set")
        );

        //org.springframework.boot.autoconfigure.context.MessageSourceProperties.setBasename(String)
        definitionLinkAsserts.assertLinkTargets(editor, "basename", p, editor.rangeOf("basename"),
                method("org.springframework.boot.autoconfigure.context.MessageSourceProperties", "setBasename", "java.lang.String")
        );
    }

    @Test
    void definitionLinks_bug_169240253_parent() throws Exception {
        MavenJavaProject p = createPredefinedMavenProject("boot-web-actuator-2.2.0");
        //See: https://www.pivotaltracker.com/story/show/169240253
        useProject(p);
        Editor editor = newEditor(
                "management:\n" +
                        "  endpoints:\n" +
                        "    web:\n" +
                        "      exposure:\n" +
                        "        exclude: '*'\n" +
                        "spring:\n" +
                        "  messages:\n" +
                        "    basename: messages/messages\n"
        );
        //org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties.getExposure()
        definitionLinkAsserts.assertLinkTargets(editor, "exposure", p, editor.rangeOf("exposure"),
                method("org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties", "getExposure")
        );
    }

    @Test
    void testClassReferenceInValueLink() throws Exception {
        Editor editor;
        MavenJavaProject project = createPredefinedMavenProject("empty-boot-1.3.0-with-mongo");
        useProject(project);

        editor = newEditor(
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy: org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy\n"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy", project,
                editor.rangeOf("org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy", "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy"),
                "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy");

        editor = newEditor(
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy:\n" +
                        "        org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy\n"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy", project,
                editor.rangeOf("org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy", "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy"),
                "org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy");

        //Linking should also work for types that aren't valid based on the constraints
        editor = newEditor(
                "spring:\n" +
                        "  data:\n" +
                        "    mongodb:\n" +
                        "      field-naming-strategy: java.lang.String\n" +
                        "#more stuff"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "java.lang.String", project, editor.rangeOf("java.lang.String", "java.lang.String"), "java.lang.String");
    }

    @Test
    void test_STS_3335_reconcile_list_nested_in_Map_of_String() throws Exception {
        Editor editor;
        useProject(createPredefinedMavenProject("boot-1.3.3-sts-4335"));

        editor = newEditor(
                "test-map:\n" +
                        "  test-list-object:\n" +
                        "    color-list:\n" +
                        "      - not-a-color\n" +
                        "      - RED\n" +
                        "      - GREEN\n"
        );
        editor.assertProblems(
                "not-a-color|com.wellsfargo.lendingplatform.web.config.Color"
        );

        editor = newEditor(
                "test-map:\n" +
                        "  test-list-object:\n" +
                        "    string-list:\n" +
                        "      - abc\n" +
                        "      - def\n"
        );
        editor.assertProblems(/*NONE*/);

    }


    @Test
    void test_STS_3335_completions_list_nested_in_Map_of_String() throws Exception {
        useProject(createPredefinedMavenProject("boot-1.3.3-sts-4335"));

        assertCompletions(
                "test-map:\n" +
                        "  some-string-key:\n" +
                        "    col<*>"
        , // =>
                "test-map:\n" +
                        "  some-string-key:\n" +
                        "    color-list:\n" +
                        "    - <*>"
        );

        assertCompletionsDisplayString(
                "test-map:\n" +
                        "  some-string-key:\n" +
                        "    color-list:\n" +
                        "    - <*>"
        , // =>
                "red", "green", "blue"
        );
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
                "my:\n" +
                        "  terms-and-conditions: <*>"
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
    void testBootBug5905() throws Exception {
        useProject(createPredefinedMavenProject("boot-1.3.3-app-with-resource-prop"));

        //Check the metadata reflects the 'handle-as':
        PropertyInfo metadata = getIndexProvider().getIndex((IDocument)null).getProperties().get("my.welcome.path");
        assertEquals("org.springframework.core.io.Resource", metadata.getType());

        //Check the content assist based on it works too:
        assertCompletionsDisplayString(
                "my:\n" +
                        "  welcome:\n" +
                        "    path: <*>"
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
    @Disabled
    // TODO: Test disabled. Java 17 generates javadoc in a lightly different format. Either support it or perhaps try mockito
    void testEnumJavaDocShownInValueContentAssist() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("my.background", "demo.Color", null, "Color to use as default background.");

        assertCompletionDetails(
                "my:\n" +
                        "  background: <*>"
        , // ==========
                "red"
        , // ==>
                "demo.Color[BLUE, GREEN, RED]",
                "Hot and delicious"
        );
    }

    @Disabled
    @Test
    void testEnumJavaDocShownInValueHover() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("my.background", "demo.Color", null, "Color to use as default background.");

        Editor editor;

        editor = newEditor(
                "my:\n" +
                        "  background: red"
        );
        editor.assertHoverContains("red", "Hot and delicious");

        editor = newEditor(
                "my:\n" +
                        "  background: RED"
        );
        editor.assertHoverContains("RED", "Hot and delicious");
    }

    @Test
    void testHyperLinkEnumValue() throws Exception {
        Editor editor;
        MavenJavaProject project = createPredefinedMavenProject("enums-boot-1.3.2-app");
        useProject(project);
        data("my.background", "demo.Color", null, "Color to use as default background.");

        editor = newEditor(
                "my:\n" +
                        "  background: RED"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "RED", project, editor.rangeOf("RED", "RED"), field("demo.Color", "RED"));

        editor = newEditor(
                "my:\n" +
                        "  background: red"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "red", project, editor.rangeOf("red", "red"), field("demo.Color", "RED"));
    }

    @Test
    void testHyperLinkEnumValueInMapKey() throws Exception {
        Editor editor;
        MavenJavaProject project = createPredefinedMavenProject("enums-boot-1.3.2-app");
        useProject(project);
        data("my.color.map", "java.util.Map<demo.Color,java.lang.String>", null, "Pretty names for the colors.");

        editor = newEditor(
                "my:\n" +
                        "  color:\n" +
                        "    map:\n" +
                        "      RED: Rood\n" +
                        "      green: Groen\n"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "RED", project, editor.rangeOf("RED", "RED"), field("demo.Color", "RED"));
        definitionLinkAsserts.assertLinkTargets(editor, "green", project, editor.rangeOf("green", "green"), field("demo.Color", "GREEN"));

        editor = newEditor(
                "spring:\n" +
                        "  jackson:\n" +
                        "    serialization:\n" +
                        "      INDENT_OUTPUT: true"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "INDENT_OUTPUT", project,
                editor.rangeOf("INDENT_OUTPUT", "INDENT_OUTPUT"),
                field("com.fasterxml.jackson.databind.SerializationFeature", "INDENT_OUTPUT")
        );

        editor = newEditor(
                "spring:\n" +
                        "  jackson:\n" +
                        "    serialization:\n" +
                        "      indent-output: true"
        );
        definitionLinkAsserts.assertLinkTargets(editor, "indent-output", project,
                editor.rangeOf("indent-output", "indent-output"),
                field("com.fasterxml.jackson.databind.SerializationFeature", "INDENT_OUTPUT")
        );
    }

    @Test
    void PT_137299017_extra_space_wth_bean_property_value_completion() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("color", "demo.ColorData", null, "colorful stuff");

        assertCompletions(
                "color:\n" +
                        "  next:<*>"
        ,  //==>
                "color:\n" +
                        "  next: blue<*>"
        , // ==
                "color:\n" +
                        "  next: green<*>"
        , // ==
                "color:\n" +
                        "  next: red<*>"
        );
    }

    @Test
    void PT_137299017_extra_space_wth_index_property_value_completion() throws Exception {
        defaultTestData();
        assertCompletions(
                "flyway:\n" +
                        "  enabled:<*>"
        , // ==>
                "flyway:\n" +
                        "  enabled: false<*>"
        , // ==
                "flyway:\n" +
                        "  enabled: true<*>"
        );
    }

    @Test
    void testIgnoreTypeErrorsForValuesContainingMavenResourcesPlaceholders_workaround() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/150005676
        defaultTestData();
        Editor editor = newEditor(
                "server:\n" +
                        "  port: \"@application-port@\"\n" +
                        "bogus: bad" //token error to ensure reconciler is really working
        );
        editor.assertProblems("bogus|Unknown property");
    }

    @Test
    @Disabled
    void IGNORED_testIgnoreTypeErrorsForValuesContainingMavenResourcesPlaceholders_direct() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/150005676
        //Not implemented, this test fails. The choice not to implement this was deliberate!
        defaultTestData();
        Editor editor = newEditor(
                "server:\n" +
                        "  port: @application-port@\n" +
                        "bogus: bad" //token error to ensure reconciler is really working
        );
        editor.assertProblems("bogus|Unknown property");
    }

    @Test
    void testSetOfEnumReconcile() throws Exception {
        String collectionType = "java.util.Set";
        doCollectionOfEnumReconcileTest(collectionType);
    }

    @Test
    void testListOfEnumReconcile() throws Exception {
        String collectionType = "java.util.List";
        doCollectionOfEnumReconcileTest(collectionType);
    }

	private void doCollectionOfEnumReconcileTest(String collectionType) throws Exception {
		useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
		data("my.colors", collectionType + "<demo.Color>", null, "Ooh! nice colors!");
		Editor editor;

		//comma-separated string
		editor = newEditor(
				"my:\n" +
				"  colors: red,green,BLUE,not-a-color\n"
		);
		editor.assertProblems("not-a-color|demo.Color");

		//comma-separated string
		editor = newEditor(
				"my:\n" +
				"  colors: red, green,  not-a-color, BLUE\n"
		);
		editor.assertProblems("not-a-color|demo.Color");

		//flow list
		editor = newEditor(
				"my:\n" +
				"  colors: [red, green, BLUE, not-a-color]"
		);
		editor.assertProblems("not-a-color|demo.Color");

		//block list
		editor = newEditor(
				"my:\n" +
				"  colors:\n" +
				"  - red\n" +
				"  - r-e-d\n" + // Canonical name is "red" from "r-e-d" hence it is okay in Boot as well 
				"  - green\n" +
				"  - BLUE\n" +
				"  - not-a-color\n"
		);
		editor.assertProblems("not-a-color|demo.Color");
	}

    @Test
    void testSetOfEnumCompletions() throws Exception {
        useProject(createPredefinedMavenProject("enums-boot-1.3.2-app"));
        data("my.colors", "java.util.Set<demo.Color>", null, "Ooh! nice colors!");

        Editor editor = newEditor(
                "my:\n" +
                        "  colors:\n" +
                        "  - <*>"
        );
        editor.assertContextualCompletions("<*>", "blue<*>", "green<*>", "red<*>");
        editor.assertContextualCompletions("b<*>", "blue<*>");
        editor.assertContextualCompletions("B<*>", "BLUE<*>");
    }

    @Test
    void test_NoQuickfixForDeprecatedProperty() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/163720976
        //Summary: if a deprecated property metadata does *not*
        // provide a 'replace with' hint then it should not create
        // a 'Replace with' quickfix.

        data("spring.devtools.remote.debug.local-port", "java.lang.Integer",
                8000,  "Local remote debug server port."
        );
        deprecate("spring.devtools.remote.debug.local-port", null, "No longer supported", Level.ERROR);

        Editor editor = harness.newEditor(
                "spring:\n" +
                        "  devtools:\n" +
                        "    remote:\n" +
                        "      debug:\n" +
                        "        local-port: 8888"
        );
        Diagnostic problem = editor.assertProblems("local-port|Deprecated").get(0);
        editor.assertNoCodeAction(problem);
        assertEquals(DiagnosticSeverity.Error, problem.getSeverity());
    }

	///////////////// cruft ////////////////////////////////////////////////////////

	private void withSystemProperty(String prop, String value, RunnableWithException doit) throws Exception {
		Optional<String> oldValue = System.getProperties().containsKey(prop) ? Optional.of(System.getProperty(prop)) : Optional.empty();
		try {
			System.setProperty(prop, value);
			doit.run();
		} finally {
			if (oldValue.isPresent()) {
				System.setProperty(prop, oldValue.get());
			} else {
				System.getProperties().remove(prop);
			}
		}
	}

	private void generateNestedProperties(int levels, String[] names, String prefix) {
		if (levels==0) {
			data(prefix, "java.lang.String", null, "Property "+prefix);
		} else if (levels > 0) {
			for (int i = 0; i < names.length; i++) {
				generateNestedProperties(levels-1, names, join(prefix, names[i]));
			}
		}
	}

	private String join(String prefix, String string) {
		if (StringUtil.hasText(prefix)) {
			return prefix +"." + string;
		}
		return string;
	}

	@Override
	protected String getFileExtension() {
		return ".yml";
	}

	@Override
	protected LanguageId getLanguageId() {
		return LanguageId.BOOT_PROPERTIES_YAML;
	}

	@Override
	public Editor newEditor(String contents) throws Exception {
		return harness.newEditor(LanguageId.BOOT_PROPERTIES_YAML, contents);
	}

}
