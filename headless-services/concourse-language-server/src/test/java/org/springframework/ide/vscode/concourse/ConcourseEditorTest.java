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
package org.springframework.ide.vscode.concourse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.ide.vscode.languageserver.testharness.Editor.INDENTED_COMPLETION;
import static org.springframework.ide.vscode.languageserver.testharness.Editor.PLAIN_COMPLETION;
import static org.springframework.ide.vscode.languageserver.testharness.TestAsserts.assertContains;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.InsertTextFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.commons.util.IOUtil;
import org.springframework.ide.vscode.commons.util.Unicodes;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.concourse.bootiful.ConcourseLanguageServerTest;
import org.springframework.ide.vscode.concourse.github.GithubInfoProvider;
import org.springframework.ide.vscode.concourse.github.GithubRepoContentAssistant;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;
import org.springframework.ide.vscode.languageserver.testharness.SynchronizationPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@ExtendWith(SpringExtension.class)
@ExtendWith(LogTestStartAndEnd.class)
@ConcourseLanguageServerTest
public class ConcourseEditorTest {
	
	private static final Logger log = LoggerFactory.getLogger(ConcourseEditorTest.class);

	private static final String CURSOR = "<*>";

	@Autowired
	ConcourseLanguageServerInitializer serverInitializer;

	@Autowired
	LanguageServerHarness harness;

	@MockitoBean
	private GithubInfoProvider github;
	
	@BeforeEach public void setup() throws Exception {
		serverInitializer.setMaxCompletions(100);
	}

    @Test
    void GH_737_support_on_error_in_steps() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/737
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: myjob\n" +
                        "  serial: false\n" +
                        "  plan:\n" +
                        "    - task: build-app\n" +
                        "      params:\n" +
                        "        STAGE: dev\n" +
                        "        APP: app\n" +
                        "      file: build.xml\n" +
                        "      on_success:\n" +
                        "        put: buildstatus\n" +
                        "        params:\n" +
                        "          context: build\n" +
                        "          repo: src\n" +
                        "          state: SUCCESSFUL\n" +
                        "      on_error:\n" +
                        "        put: buildstatus\n" +
                        "        params:\n" +
                        "          context: build\n" +
                        "          repo: src\n" +
                        "          state: FAILED"
        );
        editor.assertProblems(
                "buildstatus|does not exist",
                "buildstatus|does not exist"
        );

        editor.assertHoverContains("on_error", "execute after the parent step if the parent step terminates abnormally");
    }

    @Test
    void GH_752_accross_step_modifier() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/752
        //See: https://concourse-ci.org/across-step.html#schema.across
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: job\n" +
                        "  plan:\n" +
                        "  - across:\n" +
                        "    - var: some-text\n" +
                        "      values: some_values\n" +
                        "      max_in_flight: bad_flight\n" +
                        "      fail_fast: is_fail_fast\n" +
                        "    task: running-((.:some-text))\n" +
                        "    config:\n" +
                        "      platform: linux\n" +
                        "      image_resource:\n" +
                        "        type: docker-image\n" +
                        "        source:\n" +
                        "          repository: ubuntu\n" +
                        "      run:\n" +
                        "        path: echo\n" +
                        "        args: [\"((.:some-text))\"]"
        );
        editor.assertProblems(
                "some_values|Expecting a 'Sequence'",
                "bad_flight|'all' or an integer greater than 0",
                "is_fail_fast|boolean"
        );

        editor.assertHoverContains("across", "Run a step multiple times with different combinations of variable values");
        editor.assertHoverContains("var", "The name of the variable that will be added");
        editor.assertHoverContains("values", "will iterate over");
        editor.assertHoverContains("max_in_flight", "number of substeps may run in parallel");
        editor.assertHoverContains("fail_fast", "steps will be interrupted and pending steps");
    }

    @Test
    void GH_752_repo_mirror() throws Exception {
        //See: 
        //  - https://github.com/concourse/registry-image-resource#:~:text=registry_mirror%3A%20Optional.
        //  - https://github.com/spring-projects/sts4/issues/752
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: alpine\n" +
                        "    tag: latest\n" +
                        "    registry_mirror: https://my-registry.com\n" +
                        "run:\n" +
                        "    path: sh\n" +
                        "    args:\n" +
                        "    - -exc\n" +
                        "    - sleep 60\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "image_resource:\n" +
                        "  type: registry-image\n" +
                        "  source:\n" +
                        "    repository: alpine\n" +
                        "    tag: latest\n" +
                        "    registry_mirror:\n" +
                        "      host: my-registry.com\n" +
                        "      username: myuser\n" +
                        "      password: mypass\n" +
                        "      bad: unknown\n" +
                        "run:\n" +
                        "    path: sh\n" +
                        "    args:\n" +
                        "    - -exc\n" +
                        "    - sleep 60\n"
        );
        editor.assertProblems("bad|Unknown");

    }

    @Test
    void GH_639_globStar() throws Exception {
        Editor editor = harness.newEditor(
                "groups:\n" +
                        "- name: build\n" +
                        "  jobs:\n" +
                        "    - \"ci-*\"\n" +
                        "\n" +
                        "jobs:\n" +
                        "- name: ci-project-one\n" +
                        "  plan:\n" +
                        "  - task: gradle-build\n" +
                        "    file: gradle-build-dcind.yml"
        );
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(
                "groups:\n" +
                        "- name: build\n" +
                        "  jobs:\n" +
                        "    - one-*\n" +
                        "    - two-*\n" +
                        "- name: scan\n" +
                        "  jobs:\n" +
                        "    - one-*\n" +
                        "    - three-*\n" +
                        "\n" +
                        "jobs:\n" +
                        "- name: one-project\n" +
                        "  plan: []\n" +
                        "- name: two-project\n" +
                        "  plan: []\n" +
                        "- name: three-project\n" +
                        "  plan: []\n" +
                        "- name: four-project\n" +
                        "  plan: []\n"
        );
        editor.assertProblems("four-project|no group");

        editor = harness.newEditor(
                "groups:\n" +
                        "- name: build\n" +
                        "  jobs:\n" +
                        "    - ci-*\n" +
                        "\n" +
                        "jobs:\n" +
                        "- name: xx-project-one\n" +
                        "  plan:\n" +
                        "  - task: gradle-build\n" +
                        "    file: gradle-build-dcind.yml"
        );
        editor.assertProblems(
                "ci-*|does not match any existing job",
                "xx-project-one|belongs to no group"
        );

        // No errors if patterns are too complex for our SimpleGlob to understand
        editor = harness.newEditor(
                "groups:\n" +
                        "- name: build\n" +
                        "  jobs:\n" +
                        "    - \"{one,two,three,four}-project*\"\n" +
                        "- name: scan\n" +
                        "\n" +
                        "jobs:\n" +
                        "- name: one-project\n" +
                        "  plan: []\n" +
                        "- name: two-project\n" +
                        "  plan: []\n" +
                        "- name: three-project\n" +
                        "  plan: []\n" +
                        "- name: four-project\n" +
                        "  plan: []\n"
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void addSingleRequiredPropertiesQuickfix() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  source:\n" +
                        "    username: someone\n" +
                        "# Confuse"
        );
        Diagnostic problem = editor.assertProblems(
                "-|'type' is required",
                "foo|Unused"
        ).get(0);
        CodeAction quickfix = editor.assertCodeAction(problem);
        assertEquals("Add property 'type'", quickfix.getLabel());
        quickfix.perform();

        editor.assertText(
                "resources:\n" +
                        "- name: foo\n" +
                        "  source:\n" +
                        "    username: someone\n" +
                        "  type: <*>\n" +
                        "# Confuse"
        );
    }

    @Test
    void addMultipleRequiredPropertiesQuickfix() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    username: someone\n"
        );
        Diagnostic problem = editor.assertProblems(
                "foo|Unused",
                "source|[branch, pool, uri] are required").get(1);
        CodeAction quickfix = editor.assertCodeAction(problem);
        assertEquals("Add properties: [branch, pool, uri]", quickfix.getLabel());
        quickfix.perform();

        editor.assertText(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    username: someone\n" +
                        "    uri: <*>\n" +
                        "    branch: \n" +
                        "    pool: \n"
        );
    }

    @Test
    void quickfixForOneOfMultipleMarkersOnSameRange() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: myjob\n" +
                        "  plan:\n" +
                        "  - task: foo\n" +
                        "    config:\n" +
                        "      inputs:\n" +
                        "      - name: foo"
        );
        List<Diagnostic> problems = editor.assertProblems(
                "config|[image_resource, rootfs_uri, image] is required",
                "config|[platform, run] are required"
        );

        assertTrue(editor.getCodeActions(problems.get(0)).isEmpty());

        CodeAction quickfix = editor.assertCodeAction(problems.get(1));
        assertEquals("Add properties: [platform, run]", quickfix.getLabel());
        quickfix.perform();

        editor.assertText(
                "jobs:\n" +
                        "- name: myjob\n" +
                        "  plan:\n" +
                        "  - task: foo\n" +
                        "    config:\n" +
                        "      inputs:\n" +
                        "      - name: foo\n" +
                        "      platform: <*>\n" +
                        "      run:\n" +
                        "        path: "
        );

    }

    @Test
    void reconcileResourceTypeType() throws Exception {
        Editor editor;
        editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: s3-multi\n" +
                        "  type: # <- bad\n"
        );
        editor.assertProblems(
                "# <- bad\n^^|cannot be blank"
        );

        editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: s3-multi\n" +
                        "  type: garbage\n"
        );
        editor.assertProblems(
                "garbage|Resource Type does not exist"
        );
    }

    @Test
    void reconcileDisplayType() throws Exception {
        Editor editor;
        editor = harness.newEditor(
                "display:\n" +
                        "  background_image: # <- bad\n"
        );

        editor.assertProblems(
                "# <- bad\n^^|String should not be empty"
        );
    }

    @Test
    void testReconcileCatchesParseError() throws Exception {
        Editor editor = harness.newEditor(
                "somemap: val\n" +
                        "- sequence"
        );
        editor.assertProblems(
                "-|expected <block end>"
        );
    }

    @Test
    void reconcileRunsOnDocumentOpenAndChange() throws Exception {
        Editor editor = harness.newEditor(
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
    void reconcileMisSpelledPropertyNames() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resorces:\n" +
                        "- name: git\n" +
                        "  type: git\n"
        );
        editor.assertProblems("resorces|Unknown property");
    }

    @Test
    void reconcileAcceptsSensiblePipelineFile() throws Exception {
        Editor editor;

        when(github.getReposForOwner("spring-projects")).thenReturn(ImmutableSet.of("sts4"));
        editor = harness.newEditor(
                getClasspathResourceText("workspace/pipeline.yml")
        );
        editor.assertProblems(/*NONE*/);
        ;
    }

	private String getClasspathResourceText(String resourceName) throws Exception {
		InputStream stream = ConcourseEditorTest.class.getClassLoader().getResourceAsStream(resourceName);
		return IOUtil.toString(stream);
	}

    @Test
    void outlineWithAnchors() throws Exception {
        harness.enableHierarchicalDocumentSymbols(true);
        //See: https://github.com/spring-projects/sts4/issues/483
        Editor editor = harness.newEditor(
                "def: &stuff\n" +
                        "  name: git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: \n" +
                        "resources:\n" +
                        "- <<: *stuff\n" +
                        "- name: git2\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:kdvolder/7zip.git"
        );

        editor.assertHierarchicalDocumentSymbols(
                "resources::Resources\n" +
                        "  git::Resource\n" +
                        "  git2::Resource\n"
        );
    }

    @Test
    void complexOutlineWithAnchors() throws Exception {
        harness.enableHierarchicalDocumentSymbols(true);
        //See: https://github.com/spring-projects/sts4/issues/483

        Editor editor = harness.newEditorFromClasspath("/workspace/gh_483_pipeline.yml");

        editor.assertHierarchicalDocumentSymbols(
                "resource_types::Resource Types\n" +
                        "  concourse-pipeline-resource::Resource Type\n" +
                        "  slack-notification::Resource Type\n" +
                        "resources::Resources\n" +
                        "  git-ci-pipeline::Resource\n" +
                        "  concourse::Resource\n" +
                        "  mattermost-notify::Resource\n" +
                        "jobs::Jobs\n" +
                        "  pipeline install::Job\n"
        );
    }

    @Test
    void reconcileStructuralProblems() throws Exception {
        Editor editor;

        //resources should be a sequence not a map, even if there's only one entry
        editor = harness.newEditor(
                "resources:\n" +
                        "  name: git\n" +
                        "  type: git\n"
        );
        editor.assertProblems(
                "name: git\n  type: git|Expecting a 'Sequence' but found a 'Map'"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - task: a-task\n" +
                        "    tags: a-single-string\n"
        );
        editor.assertProblems(
                "-^ task|One of [config, file] is required",
                "a-single-string|Expecting a 'Sequence'"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - try:\n" +
                        "      put: a-resource\n"
        );
        editor.assertProblems(
                "a-resource|does not exist"
        );
    }

    @Test
    void resource_tags() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: abc\n" +
                        "  type: git\n" +
                        "  tags:\n" +
                        "  - sometag\n" +
                        "  - othertag\n"
        );
        editor.assertProblems("abc|Unused");

        editor.assertHoverContains("tags", "determine which workers");
    }

    @Test
    void primaryStepCompletions() throws Exception {
        assertContextualCompletions(
                // Context:
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - <*>"
        , // ==============
                "<*>"
        , // =>
                "do:\n" +
                        "    - <*>"
        , // ==============
                "get: <*>"
        , // ==============
                "in_parallel:\n" +
                        "      <*>"
        , // ==============
                "load_var: <*>"
        , // ==============
                "put: <*>"
        , // ==============
                "set_pipeline: <*>"
        , // ==============
                "task: <*>"
        , // ==============
                "try:\n" +
                        "      <*>"
        , // ==============
                "aggregate:\n" +
                        "    - <*>"
        );
    }

    @Test
    void PT_136196057_do_step_completion_indentation() throws Exception {
        assertCompletions(
                "jobs:\n" +
                        "- name:\n" +
                        "  plan:\n" +
                        "  - do<*>"
        , // =>
                "jobs:\n" +
                        "- name:\n" +
                        "  plan:\n" +
                        "  - do:\n" +
                        "    - <*>"
        );
    }

    @Test
    void primaryStepHovers() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - get: something\n" +
                        "  - put: something\n" +
                        "  - do: []\n" +
                        "  - aggregate:\n" +
                        "    - task: perform-something\n" +
                        "  - in_parallel:\n" +
                        "    - task: perform-something\n" +
                        "  - try:\n" +
                        "      put: test-logs\n" +
                        "  - set_pipeline: configure-the-pipeline\n" +
                        "    file: my-repo/ci/pipeline.yml\n" +
                        "  - load_var: some-var\n" +
                        "    file: /path/to/var-file\n"
        );

        editor.assertHoverContains("get", "Fetches a resource");
        editor.assertHoverContains("put", "Pushes to the given [Resource]");
        editor.assertHoverContains("aggregate", "Performs the given steps in parallel");
        editor.assertHoverContains("in_parallel", "Performs the given steps in parallel");
        editor.assertHoverContains("task", "Executes a [Task]");
        editor.assertHoverContains("do", "performs the given steps serially");
        editor.assertHoverContains("try", "Performs the given step, swallowing any failure");
        editor.assertHoverContains("set_pipeline", "The identifier specifies the name of the pipeline to configure");
    }

    @Test
    void putStepHovers() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - put: something\n" +
                        "    inputs: []\n" +
                        "    resource: something\n" +
                        "    params:\n" +
                        "      some_param: some_value\n" +
                        "    get_params:\n" +
                        "      skip_download: true\n"
        );

        editor.assertHoverContains("inputs", "only the listed artifacts will be provided to the container");
        editor.assertHoverContains("resource", "The resource to update");
        editor.assertHoverContains("params", "A map of arbitrary configuration");
        editor.assertHoverContains("get_params", "A map of arbitrary configuration to forward to the resource that will be utilized during the implicit `get` step");
    }

    @Test
    void setPipelineStepHovers() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - get: my-repo\n" +
                        "  - set_pipeline: configure-the-pipeline\n" +
                        "    file: my-repo/ci/pipeline.yml\n" +
                        "    instance_vars:\n" +
                        "    var_files:\n" +
                        "    - my-repo/ci/dev.yml\n" +
                        "    vars:\n" +
                        "    team: my-team\n" +
                        "    text: \"Hello World!\"\n"
        );

        editor.assertHoverContains("file", "The path to the pipeline's configuration file.");
        editor.assertHoverContains("instance_vars", "A map of instance vars used to identify");
        editor.assertHoverContains("var_files", "files that will be passed to the pipeline config in the same manner as the --load-vars-from flag");
        editor.assertHoverContains("vars", 2, "A map of template variables to pass to the pipeline config.");
        editor.assertHoverContains("team", "By default, the `set_pipeline` step sets the pipeline for the same **team** that is running the build");
    }

    @Test
    void loadVarStepHovers() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - get: my-repo\n" +
                        "  - load_var: some-var\n" +
                        "    file: path/to/varfile.json\n" +
                        "    format: json\n" +
                        "    reveal: true\n"
        );

        editor.assertHoverContains("load_var", "Load the value for a var at runtime");
        editor.assertHoverContains("file", "file whose content shall be read");
        editor.assertHoverContains("format", "The format of the file's content");
        editor.assertHoverContains("reveal", "allow the var's content to be printed");
    }

    @Test
    void loadVarStepReconcile() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - load_var: some-var\n"
        );
        editor.assertProblems("-^ load_var|'file' is required");

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - load_var: some-var\n" +
                        "    file: path/to/varfile.json\n" +
                        "    format: a-format\n" +
                        "    reveal: show-it\n"
        );
        editor.assertProblems(
                "a-format|Valid values are: [json, raw, trim, yaml, yml]",
                "show-it|boolean"
        );
    }

    @Test
    void getStepHovers() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - get: something\n" +
                        "    resource: something\n" +
                        "    version: latest\n" +
                        "    passed: [other-job]\n" +
                        "    params:\n" +
                        "      some_param: some_value\n" +
                        "    trigger: true\n" +
                        "    attempts: 10\n" +
                        "    on_abort:\n" +
                        "    - bogus: bad\n" +
                        "    on_failure:\n" +
                        "    - bogus: bad\n" +
                        "    on_success:\n" +
                        "    - bogus: bad\n" +
                        "    ensure:\n" +
                        "      task: cleanups\n"
        );
        editor.assertHoverContains("resource", "The resource to fetch");
        editor.assertHoverContains("version", "The version of the resource to fetch");
        editor.assertHoverContains("params", "A map of arbitrary configuration");
        editor.assertHoverContains("trigger", "Set to `true` to auto-trigger");
        editor.assertHoverContains("attempts", "Any step can set the number of times it should be attempted");
        editor.assertHoverContains("on_abort", "step to execute only if the parent step aborts");
        editor.assertHoverContains("on_failure", "Any step can have `on_failure` tacked onto it");
        editor.assertHoverContains("on_success", "Any step can have `on_success` tacked onto it");
        editor.assertHoverContains("ensure", "a second step to execute regardless of the result of the parent step");
    }

    @Test
    void displayHovers() throws Exception {
        Editor editor;
        editor = harness.newEditor(
                "display:\n" +
                        "  background_image: https://google.com/myimage.png\n"
        );

        editor.assertHoverContains("background_image", "custom background image");
    }


    @Test
    void groupHovers() throws Exception {
        Editor editor = harness.newEditor(
                "groups:\n" +
                        "- name: some-group\n" +
                        "  resources: []\n" +
                        "  jobs: []\n"
        );
        editor.assertHoverContains("name", "The name of the group");
        editor.assertHoverContains("resources", "A list of resources that should appear in this group");
        editor.assertHoverContains("jobs", " A list of jobs that should appear in this group");
    }

    @Test
    void concourse_3_0_rootfs_uri_prop() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "image: blah\n" +
                        "run:\n" +
                        "  path: demo-repo/ci/tasks/run-tests.sh"
        );
        Diagnostic p = editor.assertProblems("image|renamed to 'rootfs_uri'").get(0);
        assertEquals(DiagnosticSeverity.Warning, p.getSeverity());

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "image: blah\n" +
                        "run:\n" +
                        "  path: demo-repo/ci/tasks/run-tests.sh"
        );
        editor.assertHoverContains("image", "renamed to `rootfs_uri`");
    }

    @Test
    void taskStepHovers() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - task: do-something\n" +
                        "    file: some-file.yml\n" +
                        "    privileged: true\n" +
                        "    image: some-image\n" +
                        "    params:\n" +
                        "      map: of-stuff\n" +
                        "    vars:\n" +
                        "      map: of-stuff\n" +
                        "    input_mapping:\n" +
                        "      map: of-stuff\n" +
                        "    output_mapping:\n" +
                        "      map: of-stuff\n" +
                        "    config: some-config\n" +
                        "    tags: [a, b, c]\n" +
                        "    attempts: 10\n" +
                        "    timeout: 1h30m\n" +
                        "    ensure:\n" +
                        "      bogus: bad\n" +
                        "    on_failure:\n" +
                        "      bogus: bad\n" +
                        "    on_success:\n" +
                        "      bogus: bad\n"
        );
        editor.assertHoverContains("file", "`file` points at a `.yml` file containing the task config");
        editor.assertHoverContains("privileged", "If set to `true`, the task will run with full capabilities");
        editor.assertHoverContains("image", "Names an artifact source within the plan");
        editor.assertHoverContains("params", "A map of task parameters to set, overriding those configured in `config` or `file`");
        editor.assertHoverContains("vars", "A map of template variables to pass to an external task");
        editor.assertHoverContains("input_mapping", "A map from task input names to concrete names in the build plan");
        editor.assertHoverContains("output_mapping", "A map from task output names to concrete names");
        editor.assertHoverContains("config", "Use `config` to inline the task config");
        editor.assertHoverContains("tags", "Any step can be directed at a pool of workers");
        editor.assertHoverContains("timeout", "amount of time to limit the step's execution");
    }

    @Test
    void taskVarsReconcile() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job-with-external-task\n" +
                        "  plan:\n" +
                        "  - task: do-something\n" +
                        "    file: some-file.yml\n" +
                        "    vars:\n" +
                        "      foo: bar\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job-internal-task\n" +
                        "  plan:\n" +
                        "  - task: do-something\n" +
                        "    config: some-config\n" +
                        "    vars: not-a-map"
        );
        editor.assertProblems(
                "some-config|Expecting a 'Map'",
                "vars|assumes that 'file' is also defined",
                "not-a-map|Expecting a 'Map'"
        );
    }

    @Test
    void aggregateStepHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - aggregate:\n" +
                        "    - get: some-resource\n"
        );

        editor.assertHoverContains("aggregate", "Performs the given steps in parallel");
    }

    @Test
    void inParallelStepHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "      limit: 2\n" +
                        "      fail_fast: true\n" +
                        "      steps:\n" +
                        "      - get: some-resource\n"
        );

        editor.assertHoverContains("in_parallel", "Performs the given steps in parallel");
        editor.assertHoverContains("limit", "sempahore which limits the parallelism");
        editor.assertHoverContains("fail_fast", "step will fail fast");
    }

    @Test
    void inParallelStepReconcile() throws Exception {
        Editor editor;

        //old style... just a sequence of steps
        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    - get: some-resource\n"
        );
        editor.assertProblems(
                "some-resource|does not exist"
        );

        //new style... object with a 'steps' property
        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "      limit: not-a-number\n" +
                        "      fail_fast: not-a-bool\n" +
                        "      steps:\n" +
                        "      - get: some-resource\n"
        );
        editor.assertProblems(
                "not-a-number|NumberFormatException",
                "not-a-bool|boolean",
                "some-resource|does not exist"
        );
    }

    @Test
    void inParallelStepCompletion() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - <*>"
        );
        editor.assertCompletionWithLabel("in_parallel",
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "      <*>"
        );
    }

    @Test
    void inParallelStepCompletionInList() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    - <*>"
        );
        editor.assertCompletionWithLabel("get",
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    - get: <*>"
        );
    }

    @Test
    void inParallelStepCompletionOptions() throws Exception {
        assertContextualCompletions(PLAIN_COMPLETION,
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "      <*>"
        , // ------------
                "<*>"
        , // ==>
                "fail_fast: <*>"
        ,
                "limit: <*>"
        ,
                "steps:\n" +
                        "      - <*>"
        );

        assertContextualCompletions(c -> {
                    String l = c.getLabel();
                    boolean isDedentedStep = l.startsWith(Unicodes.LEFT_ARROW + " -");
                    return isDedentedStep && (l.contains("get") || l.contains("put"));
                },
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    <*>"
        , // ------------
                "  <*>"
        , // ==>
                "- get: <*>"
        ,
                "- put: <*>"
        );

    }

    @Test
    void inParallelStepCompletionInObject() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "      steps:\n" +
                        "      - <*>"
        );
        editor.assertCompletionWithLabel("get",
                "jobs:\n" +
                        "- name: some-job\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "      steps:\n" +
                        "      - get: <*>"
        );
    }

    @Test
    void reconcileSimpleTypes() throws Exception {
        Editor editor;

        //check for 'format' errors:
        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: foo\n" +
                        "  serial: boohoo\n" +
                        "  max_in_flight: -1\n" +
                        "  plan:\n" +
                        "  - get: git\n" +
                        "    trigger: yohoho\n" +
                        "    attempts: 0\n" +
                        "    timeout: 1h:30m\n"
        );
        editor.assertProblems(
                "boohoo|boolean",
                "-1|must be at least 0",
                "git|resource does not exist",
                "yohoho|boolean",
                "0|must be at least 1",
                "1h:30m|Duration"
        );

        //check that correct values are indeed accepted
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: foo\n" +
                        "  serial: true\n" +
                        "  max_in_flight: 2\n" +
                        "  plan:\n" +
                        "  - get: git\n" +
                        "    trigger: true"
        );
        editor.assertProblems(/*none*/);

    }

    @Test
    void noListIndent() throws Exception {
        Editor editor;
        editor = harness.newEditor("jo<*>");
        editor.assertCompletions(
                "jobs:\n" +
                        "- name: $1\n" +
                        "  plan:\n" +
                        "  - $2<*>"
        );
    }

    @Test
    void toplevelCompletions() throws Exception {
        Editor editor;
        editor = harness.newEditor(CURSOR);
        editor.assertCompletions(
                "display:\n" +
                        "  background_image: <*>"
        , // ---------------
                "groups:\n" +
                        "- name: <*>"
        , // --------------
                "jobs:\n" +
                        "- name: $1\n" +
                        "  plan:\n" +
                        "  - $2<*>"
        , // ---------------
                "resource_types:\n" +
                        "- name: $1\n" +
                        "  type: $2<*>"
        , // ---------------
                "resources:\n" +
                        "- name: $1\n" +
                        "  type: $2<*>"
        , // ---------------
        		"var_sources:\n" +
        		       "- name: $1\n" +
        		       "  type: $2\n" +
        		       "  config:\n" +
        		       "    $3<*>"
        );

        editor = harness.newEditor("rety<*>");
        editor.assertCompletions(
                "resource_types:\n" +
                        "- name: $1\n" +
                        "  type: $2<*>"
        );
    }

    @Test
    void valueCompletions() throws Exception {
        String [] builtInResourceTypes = {
                "git", "hg", "time", "s3", "archive",
                "semver", "github-release",
                "docker-image", "registry-image",
                "tracker",
                "pool", "cf",
                "bosh-io-release", "bosh-io-stemcell", "bosh-deployment",
                "vagrant-cloud"
        };
        Arrays.sort(builtInResourceTypes);

        String[] expectedCompletions = new String[builtInResourceTypes.length];
        for (int i = 0; i < expectedCompletions.length; i++) {
            expectedCompletions[i] =
                    "resources:\n" +
                            "- type: " + builtInResourceTypes[i] + "<*>\n" +
                            "  source:";
        }

        assertCompletions(
                "resources:\n" +
                        "- type: <*>\n" +
                        "  source:"
        , //=>
                expectedCompletions
        );
        assertCompletions(
                "jobs:\n" +
                        "- name: foo\n" +
                        "  serial: <*>"
        , // =>
                "jobs:\n" +
                        "- name: foo\n" +
                        "  serial: false<*>"
        , // --
                "jobs:\n" +
                        "- name: foo\n" +
                        "  serial: true<*>"
        );
    }

    @Test
    void topLevelHoverInfos() throws Exception {
        Editor editor = harness.newEditor(
                "display:\n" +
                        "  background_image: https://example.com/fakeimage.png\n" +
                        "resource_types:\n" +
                        "- name: s3-multi\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/s3-resource-simple\n" +
                        "resources:\n" +
                        "- name: docker-git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:spring-projects/sts4.git\n" +
                        "    branch: {{branch}}\n" +
                        "    username: kdvolder\n" +
                        "    private_key: {{rsa_id}}\n" +
                        "    paths:\n" +
                        "    - concourse/docker\n" +
                        "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    trigger: true\n" +
                        "  - put: docker-image\n" +
                        "    params:\n" +
                        "      build: docker-git/concourse/docker\n" +
                        "    get_params: \n" +
                        "      skip_download: true\n" +
                        "groups:\n" +
                        "- name: a-groups\n"
        );

        editor.assertHoverContains("resource_types", "each pipeline can configure its own custom types by specifying `resource_types` at the top level.");
        editor.assertHoverContains("resources", "A resource is any entity that can be checked for new versions");
        editor.assertHoverContains("jobs", "At a high level, a job describes some actions to perform");
        editor.assertHoverContains("groups", "A pipeline may optionally contain a section called `groups`");
        editor.assertHoverContains("display", "set a background image on your pipeline");
    }

    @Test
    void reconcileResourceReferences() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: sts4\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://someplace.com/kdvolder/somestuff\n" +
                        "    branch: master\n" +
                        "jobs:\n" +
                        "- name: job1\n" +
                        "  plan:\n" +
                        "  - get: sts4\n" +
                        "  - get: bogus-get\n" +
                        "  - task: do-stuff\n" +
                        "    image: bogus-image\n" +
                        "    file: some-file.yml\n" +
                        "    input_mapping:\n" +
                        "      task-input: bogus-input\n" +
                        "      repo: sts4\n" +
                        "  - put: bogus-put\n"
        );
        editor.assertProblems(
                "bogus-get|resource does not exist",
                "bogus-image|resource does not exist",
//				"bogus-input|resource does not exist", //Not checked anymore. See: https://www.pivotaltracker.com/story/show/145024233
                "bogus-put|resource does not exist"
        );

        editor.assertProblems(
                "bogus-get|[sts4]",
                "bogus-image|[sts4]",
//				"bogus-input|[sts4]",  //Not checked anymore. See: https://www.pivotaltracker.com/story/show/145024233
                "bogus-put|[sts4]"
        );
    }

    @Test
    void reconcileDuplicateResourceNames() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: sts4\n" +
                        "  type: git\n" +
                        "- name: utils\n" +
                        "  type: git\n" +
                        "- name: sts4\n" +
                        "  type: git\n"
        );
        editor.assertProblems(
                "sts4|Duplicate resource name",
                "sts4|Unused",
                "utils|Unused",
                "sts4|Duplicate resource name",
                "sts4|Unused"
        );
    }

    @Test
    void reconcileDuplicateResourceTypeNames() throws Exception {
        Editor editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: slack-notification\n" +
                        "  type: docker-image\n" +
                        "- name: slack-notification\n" +
                        "  type: docker-image"
        );
        editor.assertProblems(
                "slack-notification|Duplicate resource-type name",
                "slack-notification|Duplicate resource-type name"
        );
    }


    @Test
    void reconcileDuplicateResourceTypeNames_exempt_Builtins() throws Exception {
        //See https://github.com/spring-projects/sts4/issues/196
        Editor editor;

        //Redefintion of built-in resource-type: no errors!
        editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: docker-image\n" +
                        "  privileged: true\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: concourse/docker-image-resource"
        );
        editor.assertProblems(/*None*/);

        //... unless they are redefined twice...
        editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: docker-image\n" +
                        "  privileged: true\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: concourse/docker-image-resource\n" +
                        "- name: docker-image\n" +
                        "  privileged: true\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: concourse/docker-image-resource"
        );
        editor.assertProblems(
                "docker-image|Duplicate",
                "docker-image|Duplicate"
        );
    }

    @Test
    void violatedPropertyConstraintsAreWarnings() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: blah"
        );
        Diagnostic problem = editor.assertProblems("-^ name: blah|'plan' is required").get(0);
        assertEquals(DiagnosticSeverity.Warning,  problem.getSeverity());

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - task: foo"
        );
        problem = editor.assertProblems("-^ task: foo|One of [config, file] is required").get(0);
        assertEquals(DiagnosticSeverity.Warning,  problem.getSeverity());

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - task: foo\n" +
                        "    config: {}\n" +
                        "    file: path/to/file"
        );
        {
            List<Diagnostic> problems = editor.assertProblems(
                    "config|One of [image_resource, rootfs_uri, image]",
                    "config|Only one of [config, file]",
                    "config|[platform, run] are required",
                    "file|Only one of [config, file]"
            );
            //All of the problems in this example are property contraint violations! So all should be warnings.
            for (Diagnostic diagnostic : problems) {
                assertEquals(DiagnosticSeverity.Warning, diagnostic.getSeverity());
            }
        }
    }

    @Test
    void underlineParentPropertyForMissingNode() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/140709005

        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: hello-world\n" +
                        "  plan:\n" +
                        "  - task: say-hello\n" +
                        "    config:\n" +
                        "      image_resource:\n" +
                        "        type: docker-image\n" +
                        "        source: {repository: ubuntu}\n" +
                        "      run:\n" +
                        "        path: echo\n" +
                        "        args: [\"Hello, world!\"]"
        );
        editor.assertProblems(
                "config|'platform' is required"
        );
    }

    @Test
    void reconcileDuplicateJobNames() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: job-1\n" +
                        "- name: utils\n" +
                        "- name: job-1\n"
        );
        editor.assertProblems(
                "-^ name: job-1|'plan' is required",
                "job-1|Duplicate job name",
                "-^ name: utils|'plan' is required",
                "-^ name: job-1|'plan' is required",
                "job-1|Duplicate job name"
        );
    }

    @Test
    void completionsResourceReferences() throws Exception {
        assertContextualCompletions(
                "resources:\n" +
                        "- name: sts4\n" +
                        "- name: repo-a\n" +
                        "- name: repo-b\n" +
                        "jobs:\n" +
                        "- name: job1\n" +
                        "  plan:\n" +
                        "  - get: <*>\n"
        , ////////////////////
                "<*>"
        , // =>
                "repo-a<*>", "repo-b<*>", "sts4<*>"
        );

        assertContextualCompletions(
                "resources:\n" +
                        "- name: sts4\n" +
                        "- name: repo-a\n" +
                        "- name: repo-b\n" +
                        "jobs:\n" +
                        "- name: job1\n" +
                        "  plan:\n" +
                        "  - put: <*>\n"
        , ////////////////////
                "r<*>"
        , // =>
                "repo-a<*>", "repo-b<*>"
        );

        assertContextualCompletions(
                "resources:\n" +
                        "- name: sts4\n" +
                        "- name: repo-a\n" +
                        "- name: repo-b\n" +
                        "jobs:\n" +
                        "- name: job1\n" +
                        "  plan:\n" +
                        "  - task: do-it\n" +
                        "    input_mapping:\n" +
                        "      remapped: <*>\n"
        , ////////////////////
                "<*>"
        , // =>
                "repo-a<*>", "repo-b<*>", "sts4<*>"
        );

    }

    @Test
    void reconcileDuplicateKeys() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    branch: master\n" +
                        "    uri: https://someplace.com/kdvolder/my-repo\n" +
                        "resources:\n" +
                        "- name: your-repo\n" +
                        "  type: git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    branch: master\n" +
                        "    uri: https://someplace.com/kdvolder/forked-repo\n"
        );

        editor.assertProblems(
                "resources|Duplicate key",
                "my-repo|Unused 'Resource'",
                "resources|Duplicate key",
                "your-repo|Unused 'Resource'",
                "type|Duplicate key",
                "type|Duplicate key"
        );
    }

    @Test
    void reconcileJobNames() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: git-repo\n" +
                        "  type: git\n" +
                        "- name: build-artefact\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build\n" +
                        "  plan:\n" +
                        "  - get: git-repo\n" +
                        "  - task: run-build\n" +
                        "    file: some-task.yml\n" +
                        "  - put: build-artefact\n" +
                        "- name: test\n" +
                        "  plan:\n" +
                        "  - get: git-repo\n" +
                        "    passed:\n" +
                        "    - not-a-job\n" +
                        "    - build\n"
        );

        editor.assertProblems(
                "build-artefact|should define 'branch'",
                "not-a-job|does not exist"
        );
    }

    @Test
    void reconcileGroups() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: git-repo\n" +
                        "  type: git\n" +
                        "- name: build-artefact\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build\n" +
                        "  plan:\n" +
                        "  - get: git-repo\n" +
                        "  - task: run-build\n" +
                        "    file: tasks/some-task.yml\n" +
                        "  - put: build-artefact # <- bad\n" +
                        "- name: test\n" +
                        "  plan:\n" +
                        "  - get: git-repo\n" +
                        "groups:\n" +
                        "- name: some-group\n" +
                        "  jobs: [build, test, bogus-job]\n" +
                        "  resources: [git-repo, build-artefact, not-a-resource]"
        );

        editor.assertProblems(
                "build-artefact^ # <- bad|should define 'branch'",
                "bogus-job|does not match",
                "not-a-resource|does not exist"
        );
    }

    @Test
    void timeResourceCompletions() throws Exception {
        assertContextualCompletions(
                "resources:\n" +
                        "- name: every5minutes\n" +
                        "  type: time\n" +
                        "  source:\n" +
                        "    location: <*>"
        , // ======================
                "Van<*>"
        , // =>
                "America/Vancouver<*>",
                "Asia/Vientiane<*>",
                "Europe/Vatican<*>"
        );

        assertContextualCompletions(
                "resources:\n" +
                        "- name: every5minutes\n" +
                        "  type: time\n" +
                        "  source:\n" +
                        "    <*>\n" +
                        "    blah: blah"
        , // ======================
                "<*>"
        , // =>
                "days:\n" +
                        "    - <*>",
                "interval: <*>",
                "location: <*>",
                "start: <*>",
                "stop: <*>"
        );

        assertContextualCompletions(
                "resources:\n" +
                        "- name: every5minutes\n" +
                        "  type: time\n" +
                        "  source:\n" +
                        "    days:\n" +
                        "    - <*>"
        , // ======================
                "<*>"
        , // =>
                "Friday<*>",
                "Monday<*>",
                "Saturday<*>",
                "Sunday<*>",
                "Thursday<*>",
                "Tuesday<*>",
                "Wednesday<*>"
        );

    }

    @Test
    void timeResourceSourceReconcile() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: every5minutes\n" +
                        "  type: time\n" +
                        "  source:\n" +
                        "    location: PST8PDT\n" +
                        "    start: 7AM\n" +
                        "    stop: 8AM\n" +
                        "    interval: 5m\n" +
                        "    days:\n" +
                        "    - Thursday\n"
        );
        editor.assertProblems(
                "every5minutes|Unused"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: every5minutes\n" +
                        "  type: time\n" +
                        "  source:\n" +
                        "    location: some-location\n" +
                        "    start: the-start-time\n" +
                        "    stop: the-stop-time\n" +
                        "    interval: the-interval\n" +
                        "    days:\n" +
                        "    - Monday\n" +
                        "    - Someday\n"
        );
        editor.assertProblems(
                "every5minutes|Unused",
                "some-location|Unknown 'Location'",
                "the-start-time|not a valid 'Time'",
                "the-stop-time|not a valid 'Time'",
                "the-interval|not a valid 'Duration'",
                "Someday|unknown 'Day'"
        );
    }

    @Test
    void timeResourceSourceHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: timed-trigger\n" +
                        "  type: time\n" +
                        "  source:\n" +
                        "    interval: 5m\n" +
                        "    location: UTC\n" +
                        "    start: 8:00PM\n" +
                        "    stop: 9:00PM\n" +
                        "    days: [Monday, Wednesday, Friday]"
        );
        editor.assertHoverContains("interval", "interval on which to report new versions");
        editor.assertHoverContains("location", "*Optional. Default `UTC`");
        editor.assertHoverContains("start", "The supported time formats are");
        editor.assertHoverContains("stop", "The supported time formats are");
        editor.assertHoverContains("days", "Run only on these day(s)");
    }

    @Test
    void gitResourceSourceReconcile() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: sts4-out\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@someplace.com:spring-projects/sts4.git\n" +
                        "    bogus: bad\n" +
                        "    branch: {{branch}}\n" +
                        "    private_key: {{rsa_id}}\n" +
                        "    private_key_user: pkuser\n" +
                        "    private_key_passphrase: pkpassf\n" +
                        "    forward_agent: isForwardAgent\n" +
                        "    username: jeffy\n" +
                        "    password: {{git_passwords}}\n" +
                        "    paths: not-a-list\n" +
                        "    ignore_paths: also-not-a-list\n" +
                        "    skip_ssl_verification: skip-it\n" +
                        "    tag_filter: RELEASE_*\n" +
                        "    tag_regex: aTagRegExp\n" +
                        "    fetch_tags: is-fetch-tags\n" +
                        "    submodule_credentials:\n" +
                        "    - host: host1\n" +
                        "      username: username1\n" +
                        "      password: pass1\n" +
                        "    - submodbogus: bad\n" +
                        "    - host: host2\n" +
                        "    - username: user2\n" +
                        "    git_config:\n" +
                        "    - name: good\n" +
                        "      val: bad\n" +
                        "    disable_ci_skip: no_ci_skip\n" +
                        "    commit_verification_keys: not-a-list-of-keys\n" +
                        "    commit_verification_key_ids: not-a-list-of-ids\n" +
                        "    gpg_keyserver: hkp://somekeyserver.net\n" +
                        "    git_crypt_key: YXNhc2Zm\n" +
                        "    https_tunnel:\n" +
                        "      proxy_host: proxhost\n" +
                        "      proxy_port: portnum\n" +
                        "      proxy_user: proxuser\n" +
                        "      proxy_password: proxpass\n" +
                        "    commit_filter:\n" +
                        "      exclude: exclude-commit-filters\n" +
                        "      include: include-commit-filters\n" +
                        "    version_depth: verdep\n"
        );
        editor.assertProblems(
                "sts4-out|Unused",
                "bogus|Unknown property",
                "isForwardAgent|'boolean'",
                "not-a-list|Expecting a 'Sequence'",
                "also-not-a-list|Expecting a 'Sequence'",
                "skip-it|'boolean'",
                "tag_filter|Only one of 'tag_filter' and 'tag_regex'",
                "tag_regex|Only one of 'tag_filter' and 'tag_regex'",
                "is-fetch-tags|'boolean'",
                "submodbogus|Unknown property",
                "-|[password, username] are required",
                "-|[host, password] are required",
                "val|Unknown property",
                "no_ci_skip|'boolean'",
                "not-a-list-of-keys|Expecting a 'Sequence'",
                "not-a-list-of-ids|Expecting a 'Sequence'",
                "portnum|NumberFormat",
                "exclude-commit-filters|Expecting a 'Sequence'",
                "include-commit-filters|Expecting a 'Sequence'",
                "verdep|NumberFormat"
        );
    }

    @Test
    void gitResourceSourceCompletions() throws Exception {
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: the-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    <*>"
        , //================
                "<*>"
        , // ==>
                "uri: <*>"
        );

        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: the-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri:\n" +
                        "    <*>"
        , //================
                "<*>"
        , // ==>
                "branch: <*>"
        ,
                "commit_filter:\n" +
                        "      <*>"
        ,
                "commit_verification_key_ids:\n" +
                        "    - <*>"
        ,
                "commit_verification_keys:\n" +
                        "    - <*>"
        ,
                "disable_ci_skip: <*>"
        ,
                "fetch_tags: <*>"
        ,
                "forward_agent: <*>"
        ,
                "git_config:\n" +
                        "    - <*>"
        ,
                "git_crypt_key: <*>"
        ,
                "gpg_keyserver: <*>"
        ,
                "https_tunnel:\n" +
                        "      proxy_host: $1\n" +
                        "      proxy_port: $2<*>"
        ,
                "ignore_paths:\n" +
                        "    - <*>"
        ,
                "password: <*>"
        ,
                "paths:\n" +
                        "    - <*>"
        ,
                "private_key: <*>"
        ,
                "private_key_passphrase: <*>"
        ,
                "private_key_user: <*>"
        ,
                "skip_ssl_verification: <*>"
        ,
                "submodule_credentials:\n" +
                        "    - host: $1\n" +
                        "      username: $2\n" +
                        "      password: $3<*>"
        ,
                "tag_filter: <*>"
        ,
                "tag_regex: <*>"
        ,
                "username: <*>"
        ,
                "version_depth: <*>"
        );

        assertContextualCompletions(
                "resources:\n" +
                        "- name: the-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    git_config:\n" +
                        "    - <*>"
        , // =============
                "<*>"
        , // ==>
                "name: <*>",
                "value: <*>"
        );
    }

    @Test
    void gitResourceSourceHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: sts4-out\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:spring-projects/sts4.git\n" +
                        "    bogus: bad\n" +
                        "    branch: {{branch}}\n" +
                        "    private_key: {{rsa_id}}\n" +
                        "    private_key_user: pkuser\n" +
                        "    private_key_passphrase: pkpassf\n" +
                        "    forward_agent: isForwardAgent\n" +
                        "    username: jeffy\n" +
                        "    password: {{git_passwords}}\n" +
                        "    paths: not-a-list\n" +
                        "    ignore_paths: also-not-a-list\n" +
                        "    skip_ssl_verification: skip-it\n" +
                        "    tag_filter: RELEASE_*\n" +
                        "    tag_regex: tagRegex\n" +
                        "    fetch_tags: is-fetch-tags\n" +
                        "    submodule_credentials: []\n" +
                        "    git_config:\n" +
                        "    - name: good\n" +
                        "      val: bad\n" +
                        "    disable_ci_skip: no_ci_skip\n" +
                        "    commit_verification_keys: not-a-list-of-keys\n" +
                        "    commit_verification_key_ids: not-a-list-of-ids\n" +
                        "    gpg_keyserver: hkp://somekeyserver.net\n" +
                        "    git_crypt_key: YXNhc2Zm\n" +
                        "    https_tunnel:\n" +
                        "      proxy_host: proxhost\n" +
                        "      proxy_port: portnum\n" +
                        "      proxy_user: proxuser\n" +
                        "      proxy_password: proxpass\n" +
                        "    commit_filter:\n" +
                        "      include: []\n" +
                        "      exclude: []\n" +
                        "    version_depth: 3\n"
        );
        editor.assertHoverContains("uri", "*Required.* The location of the repository.");
        editor.assertHoverContains("branch", "The branch to track");
        editor.assertHoverContains("private_key", "Private key to use when pulling/pushing");
        editor.assertHoverContains("private_key_user", "Enables setting User in the ssh config");
        editor.assertHoverContains("private_key_passphrase", "unlock `private_key` if it is protected by a passphrase");
        editor.assertHoverContains("forward_agent", "Enables ForwardAgent SSH option when set to true");
        editor.assertHoverContains("username", "Username for HTTP(S) auth");
        editor.assertHoverContains("password", "Password for HTTP(S) auth");
        editor.assertHoverContains("paths", "a list of glob patterns");
        editor.assertHoverContains("ignore_paths", "The inverse of `paths`");
        editor.assertHoverContains("skip_ssl_verification", "Skips git ssl verification");
        editor.assertHoverContains("tag_filter", "the resource will only detect commits");
        editor.assertHoverContains("tag_regex", "only detect commits that have a tag matching the expression");
        editor.assertHoverContains("fetch_tags", "fetch all tags in the repository");
        editor.assertHoverContains("submodule_credentials", "List of credentials for HTTP(s) auth");
        editor.assertHoverContains("git_config", "configure git global options");
        editor.assertHoverContains("disable_ci_skip", "Allows for commits that have been labeled with `[ci skip]`");
        editor.assertHoverContains("commit_verification_keys", "Array of GPG public keys");
        editor.assertHoverContains("gpg_keyserver", "GPG keyserver to download the public keys from");
        editor.assertHoverContains("git_crypt_key", "will unlock / decrypt the repository");
        editor.assertHoverContains("https_tunnel", "Information about an HTTPS proxy");
        editor.assertHoverContains("proxy_host", "host name or IP");
        editor.assertHoverContains("proxy_port", "listening port");
        editor.assertHoverContains("proxy_user", "use this username");
        editor.assertHoverContains("proxy_password", "use this password");
        editor.assertHoverContains("commit_filter", "Object containing commit message filters");
        editor.assertHoverContains("include", "not be skipped");
        editor.assertHoverContains("exclude", "cause a commit to be skipped");
        editor.assertHoverContains("version_depth", "number of versions to return");
    }

    @Test
    void gitResourceGetParamsCompletions() throws Exception {
        String context =
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: my-git\n" +
                        "    params:\n" +
                        "      <*>\n" +
                        "      blah: blah";

        assertContextualCompletions(context,
                "<*>"
        , // ===>
                "clean_tags: <*>"
        ,
                "depth: <*>"
        ,
                "describe_ref_options: <*>"
        ,
                "disable_git_lfs: <*>"
        ,
                "fetch_tags: <*>"
        ,
                "short_ref_format: <*>"
        ,
                "submodule_recursive: <*>"
        ,
                "submodule_remote: <*>"
        ,
                "submodules:\n" +
                        "        <*>"
        ,
                "timestamp_format: <*>"
        ,
                "fetch:\n" + // Deprecated, so suggested last
                        "      - <*>"
        );
        assertContextualCompletions(context,
                "disable_git_lfs: <*>"
        , // ===>
                "disable_git_lfs: false<*>",
                "disable_git_lfs: true<*>"
        );
        assertContextualCompletions(context,
                "submodules: <*>"
        , // ===>
                "submodules: all<*>",
                "submodules: none<*>"
        );
    }

    @Test
    void gitResourceGetParamsHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: my-git\n" +
                        "    params:\n" +
                        "      depth: -1\n" +
                        "      fetch_tags: is-fetch\n" +
                        "      submodules: none\n" +
                        "      disable_git_lfs: not-bool-a\n" +
                        "      submodule_recursive: not-bool-b\n" +
                        "      submodule_remote: not-bool-c\n" +
                        "      clean_tags: not-bool-d\n" +
                        "      short_ref_format: '%s'\n" +
                        "      timestamp_format: iso8601\n" +
                        "      describe_ref_options: '--allways --broken'"
        );

        editor.assertHoverContains("depth", "using the `--depth` option");
        editor.assertHoverContains("fetch_tags", "fetch all tags in the repository");
        editor.assertHoverContains("submodules", "If `none`, submodules will not be fetched");
        editor.assertHoverContains("submodule_recursive", "If `false`, a flat submodules checkout is performed");
        editor.assertHoverContains("submodule_remote", "If `true`, the submodules are checked out for");
        editor.assertHoverContains("disable_git_lfs", "will not fetch Git LFS files");
        editor.assertHoverContains("clean_tags", "If `true` all incoming tags will be deleted");
        editor.assertHoverContains("short_ref_format", "`.git/short_ref` use this `printf` format");
        editor.assertHoverContains("describe_ref_options", "When populating `.git/describe_ref` use these options");
    }

    @Test
    void gitResourceGetParamsReconcile() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: my-git\n" +
                        "    params:\n" +
                        "      depth: -1\n" +
                        "      fetch_tags: not-bool-a\n" +
                        "      disable_git_lfs: not-bool-b\n" +
                        "      submodule_recursive: not-bool-c\n" +
                        "      submodule_remote: not-bool-d\n" +
                        "      clean_tags: not-bool-e\n" +
                        "      short_ref_format: goo%s\n" +
                        "      timestamp_format: iso8601\n" +
                        "      describe_ref_options: whatever\n"
        );
        editor.assertProblems(
                "-1|must be at least 1",
                "not-bool-a|'boolean'",
                "not-bool-b|'boolean'",
                "not-bool-c|'boolean'",
                "not-bool-d|'boolean'",
                "not-bool-e|'boolean'"
        );
    }

    @Test
    void gitResourcePutParamsCompletions() throws Exception {
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    params:\n" +
                        "      <*>"
        ,
                "<*>"
        , // =>
                "repository: <*>"
        );

        String context =
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    params:\n" +
                        "      repository: blah\n" +
                        "      <*>";

        assertContextualCompletions(PLAIN_COMPLETION, context,
                "<*>"
        , // ===>
                "annotate: <*>"
        ,
                "branch: <*>"
        ,
                "force: <*>"
        ,
                "merge: <*>"
        ,
                "notes: <*>"
        ,
                "only_tag: <*>"
        ,
                "rebase: <*>"
        ,
                "returning: <*>"
        ,
                "tag: <*>"
        ,
                "tag_prefix: <*>"
        );
        assertContextualCompletions(context,
                "rebase: <*>"
        , // ===>
                "rebase: false<*>",
                "rebase: true<*>"
        );
        assertContextualCompletions(context,
                "only_tag: <*>"
        , // ===>
                "only_tag: false<*>",
                "only_tag: true<*>"
        );
        assertContextualCompletions(context,
                "force: <*>"
        , // ===>
                "force: false<*>",
                "force: true<*>"
        );
        assertContextualCompletions(context,
                "merge: <*>"
        , // ===>
                "merge: false<*>",
                "merge: true<*>"
        );
    }

    @Test
    void gitResourcePutParamsReconcile() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: some-uri\n" +
                        "    branch: master\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    params: {}\n"
        );
        editor.assertProblems("params|'repository' is required");

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: some-uri\n" +
                        "    branch: master\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    params:\n" +
                        "      repository: some-other-repo\n" +
                        "      rebase: do-rebase\n" +
                        "      only_tag: do-tag\n" +
                        "      force: force-it\n" +
                        "      merge: merge-it\n" +
                        "      returning: returningValue\n" +
                        "      notes: whatever\n" +
                        "      branch: main\n" +
                        "      "
        );
        editor.assertProblems(
                "rebase|Only one of [rebase, merge] should be defined",
                "do-rebase|'boolean'",
                "do-tag|'boolean'",
                "force-it|'boolean'",
                "merge|Only one of [rebase, merge] should be defined",
                "merge-it|'boolean'",
                "returningValue|Valid values are: [merged, unmerged]"
        );
    }

    @Test
    void gitResourcePutParamsHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    params:\n" +
                        "      repository: some-other-repo\n" +
                        "      rebase: do-rebase\n" +
                        "      tag: the-tag-file\n" +
                        "      only_tag: do-tag\n" +
                        "      tag_prefix: RELEASE\n" +
                        "      force: force-it\n" +
                        "      annotate: release-annotion\n" +
                        "      merge: merge-it\n" +
                        "      returning: unmerged\n" +
                        "      notes: /path/to/notes\n" +
                        "      branch: main\n"
        );

        editor.assertHoverContains("repository", "The path of the repository");
        editor.assertHoverContains("rebase", "attempt rebasing");
        editor.assertHoverContains("tag", "HEAD will be tagged");
        editor.assertHoverContains("only_tag", "push only the tags");
        editor.assertHoverContains("tag_prefix", "prepended with this string");
        editor.assertHoverContains("force", "pushed regardless of the upstream state");
        editor.assertHoverContains("annotate", "path to a file containing the annotation message");
        editor.assertHoverContains("merge", "continuously attempt to merge remote");
        editor.assertHoverContains("returning", "unmerged commit should be passed");
        editor.assertHoverContains("notes", "path to a file containing the notes");
        editor.assertHoverContains("branch", "branch to push commits");
    }

    @Test
    void gitResourcePut_get_params_Hovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    get_params:\n" +
                        "      submodules: none\n" +
                        "      depth: 1\n" +
                        "      disable_git_lfs: true\n"
        );

        editor.assertHoverContains("depth", "using the `--depth` option");
        editor.assertHoverContains("submodules", "If `none`, submodules will not be fetched");
        editor.assertHoverContains("disable_git_lfs", "will not fetch Git LFS files");
    }

    @Test
    void putStepInputsReconcile() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/341
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://example.com/my-name/my-repo.git\n" +
                        "    branch: master\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    inputs: not-a-list\n"
        );
        editor.assertProblems("not-a-list|Valid values are: [all, detect]");
    }

    @Test
    void putStepInputsReconcileAll() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/341
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://example.com/my-name/my-repo.git\n" +
                        "    branch: master\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    inputs: all\n"
        );
        editor.assertProblems();
    }
    
    void putStepInputsReconcileNoProblems() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/341
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://example.com/my-name/my-repo.git\n" +
                        "    branch: master\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - put: my-git\n" +
                        "    inputs:\n" +
                        "    - build\n" + 
                        "    - test\n"
        );
        editor.assertProblems();
    }

    @Test
    void contentAssistJobNames() throws Exception {
        assertContextualCompletions(
                "resources:\n" +
                        "- name: git-repo\n" +
                        "- name: build-artefact\n" +
                        "jobs:\n" +
                        "- name: build\n" +
                        "  plan:\n" +
                        "  - get: git-repo\n" +
                        "  - task: run-build\n" +
                        "  - put: build-artefact\n" +
                        "- name: test\n" +
                        "  plan:\n" +
                        "  - get: git-repo\n" +
                        "    passed:\n" +
                        "    - <*>\n"
        , ///////////////////////////
                "<*>"
        , // =>
                "build<*>",
                "test<*>"
        );
    }

    @Test
    void resourceTypeAttributeReconcile() throws Exception {
        //Example from https://github.com/spring-projects/sts4/issues/382
        Editor editor = harness.newEditor(
                "resource_types:\n" +
                        "\n" +
                        "- name: cogito\n" +
                        "  type: registry-image\n" +
                        "  check_every: 24h\n" +
                        "  source:\n" +
                        "    repository: ((docker-registry))/cogito"
        );
        editor.assertProblems(/*none*/);

        //More elaborate example
        editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: cogito\n" +
                        "  type: registry-image\n" +
                        "  check_every: bad-duration\n" +
                        "  privileged: is-priviliged\n" +
                        "  params:\n" +
                        "    foo_param: bar\n" +
                        "    format: rootfs\n" +
                        "    skip_download: should-skip-dl\n" +
                        "  tags: tags-list\n" +
                        "  unique_version_history: is-unique-hist\n" +
                        "  source:\n" +
                        "    repository: ((docker-registry))/cogito"
        );
        editor.assertProblems(
                "bad-duration|Duration",
                "is-priviliged|boolean",
                "foo_param|Unknown",
                "should-skip-dl|boolean",
                "tags-list|Sequence",
                "is-unique-hist|boolean"
        );
    }

    @Test
    void resourceTypeAttributeHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: s3-multi\n" +
                        "  type: docker-image\n" +
                        "  check_every: bad-duration\n" +
                        "  old_name: was-renamed\n" +
                        "  privileged: is-priviliged\n" +
                        "  tags: tags-list\n" +
                        "  unique_version_history: is-unique-hist\n" +
                        "  source:\n" +
                        "    repository: kdvolder/s3-resource-simple\n"
        );

        editor.assertHoverContains("name", "This name will be referenced by `resources` defined within the same pipeline");
        editor.assertHoverContains("type", 2, "used to provide the resource type's container image");
        editor.assertHoverContains("source", 2, "The location of the resource type's resource");
        editor.assertHoverContains("privileged", "containers will be run with full capabilities");
        editor.assertHoverContains("check_every", "interval on which to check for new versions");
        editor.assertHoverContains("tags", "list of tags to determine which workers");
        editor.assertHoverContains("unique_version_history", "resource type will have a version history that is unique to the resource");
    }

    @Test
    void resourceAttributeHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: sts4\n" +
                        "  type: git\n" +
                        "  old_name: was-renamed\n" +
                        "  icon: foo\n" +
                        "  check_every: 5m\n" +
                        "  webhook_token: bladayadayaaa\n" +
                        "  source:\n" +
                        "    repository: https://github.com/spring-projects/sts4\n"
        );

        editor.assertHoverContains("name", "The name of the resource");
        editor.assertHoverContains("type", "The type of the resource. Each worker advertises");
        editor.assertHoverContains("old_name", "history of the old resource will be inherited");
        editor.assertHoverContains("icon", "name of a [Material Design Icon]");
        editor.assertHoverContains("source", 2, "The location of the resource");
        editor.assertHoverContains("webhook_token", "web hooks can be sent to trigger an immediate *check* of the resource");
        editor.assertHoverContains("check_every", "The interval on which to check for new versions");
    }

    @Test
    void requiredPropertiesReconcile() throws Exception {
        Editor editor;

        //addProp(resource, "name", resourceNameDef).isRequired(true);
        editor = harness.newEditor(
                "resources:\n" +
                        "- type: git"
        );
        editor.assertProblems("-^ type: git|'name' is required");

        //addProp(resource, "type", t_resource_type_name).isRequired(true);
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo"
        );
        editor.assertProblems(
                "-^ name: foo|'type' is required",
                "foo|Unused"
        );

        //Both name and type missing:
        editor = harness.newEditor(
                "resources:\n" +
                        "- source: {}"
        );
        editor.assertProblems("-^ source:|[name, type] are required");

        //addProp(job, "name", jobNameDef).isRequired(true);
        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: foo"
        );
        editor.assertProblems("-^ name: foo|'plan' is required");

        //addProp(job, "plan", f.yseq(step)).isRequired(true);
        editor = harness.newEditor(
                "jobs:\n" +
                        "- plan: []"
        );
        editor.assertProblems("-^ plan: []|'name' is required");

        //addProp(resourceType, "name", t_ne_string).isRequired(true);
        editor = harness.newEditor(
                "resource_types:\n" +
                        "- type: docker-image"
        );
        editor.assertProblems("-^ type: docker-image|'name' is required");

        //addProp(resourceType, "type", t_image_type).isRequired(true);
        editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: foo"
        );
        editor.assertProblems("-^ name: foo|'type' is required");

        //addProp(gitSource, "uri", t_string).isRequired(true);
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    branch: master"
        );
        editor.assertProblems(
                "foo|Unused",
                "source|'uri' is required"
        );

        //addProp(group, "name", t_ne_string).isRequired(true);
        editor = harness.newEditor(
                "groups:\n" +
                        "- jobs: []"
        );
        editor.assertProblems("-^ jobs: []|'name' is required");
    }

    @Test
    void gitBranchRequiredInPutStep() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@someplace.com:johny-coder/test-repo.git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: repo\n" +
                        "  - put: repo # <- bad\n"
        );
        editor.assertProblems(
                "repo^ # <- bad|should define 'branch'"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@someplace.com:johny-coder/test-repo.git\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: repo\n" +
                        "  - put: blah\n" +
                        "    resource: repo # <- bad\n"
        );
        editor.assertProblems(
                "repo^ # <- bad|should define 'branch'"
        );
    }

    @Test
    void repositoryImageResourceIsKnown() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: noise-maker\n" +
                        "  plan:\n" +
                        "  - task: make-noise\n" +
                        "    config:\n" +
                        "      platform: linux\n" +
                        "      image_resource:\n" +
                        "        type: registry-image\n" +
                        "        source:\n" +
                        "          repository: ubuntu:18.04\n" +
                        "      run:\n" +
                        "        path: echo\n" +
                        "        args:\n" +
                        "        - \"Hello world!\""
        );
        editor.assertProblems(/*None*/);
    }

    @Test
    void dockerImageResourceSourceReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    tag: latest\n"
        );
        editor.assertProblems(
                "my-docker-image|Unused 'Resource'",
                "source|'repository' is required"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/sts4-build-env\n" +
                        "    tag: latest\n" +
                        "    username: kdvolder\n" +
                        "    password: {{docker_pass}}\n" +
                        "    aws_access_key_id: {{aws_access_key}}\n" +
                        "    aws_secret_access_key: {{aws_secret_key}}\n" +
                        "    aws_session_token: ((aws_token))\n" +
                        "    insecure_registries: no-list\n" +
                        "    registry_mirror: https://some.registry.host\n" +
                        "    max_concurrent_downloads: num-down\n" +
                        "    max_concurrent_uploads: num-up\n" +
                        "    ca_certs:\n" +
                        "    - domain: example.com:443\n" +
                        "      cert: |\n" +
                        "        -----BEGIN CERTIFICATE-----\n" +
                        "        ...\n" +
                        "        -----END CERTIFICATE-----\n" +
                        "      bogus_ca_certs_prop: bad\n" +
                        "    client_certs:\n" +
                        "    - domain: example.com:443\n" +
                        "      cert: |\n" +
                        "        -----BEGIN CERTIFICATE-----\n" +
                        "        ...\n" +
                        "        -----END CERTIFICATE-----\n" +
                        "      key: |\n" +
                        "        -----BEGIN RSA PRIVATE KEY-----\n" +
                        "        ...\n" +
                        "        -----END RSA PRIVATE KEY-----\n" +
                        "      bogus_client_cert_prop: bad\n"
        );
        editor.assertProblems(
                "my-docker-image|Unused 'Resource'",
                "no-list|Expecting a 'Sequence'",
                "num-down|NumberFormat",
                "num-up|NumberFormat",
                "bogus_ca_certs_prop|Unknown property", //ca_certs
                "bogus_client_cert_prop|Unknown property" //client_certs
        );

        editor.assertHoverContains("repository", "The name of the repository");
        editor.assertHoverContains("tag", "The tag to track");
        editor.assertHoverContains("username", "username to authenticate");
        editor.assertHoverContains("password", "password to use");
        editor.assertHoverContains("aws_access_key_id",  "AWS access key to use");
        editor.assertHoverContains("aws_secret_access_key", "AWS secret key to use");
        editor.assertHoverContains("aws_session_token", "AWS session token (assumed role)");
        editor.assertHoverContains("insecure_registries", "array of CIDRs");
        editor.assertHoverContains("registry_mirror", "URL pointing to a docker registry mirror service");

        editor.assertHoverContains("ca_certs", "Each entry specifies the x509 CA certificate for");
        editor.assertHoverContains("client_certs", "Each entry specifies the x509 certificate and key");
        editor.assertHoverContains("max_concurrent_downloads", "Limits the number of concurrent download threads");
        editor.assertHoverContains("max_concurrent_uploads", "Limits the number of concurrent upload threads");
    }

    @Test
    void dockerImageResourceGetParamsReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: docker-image\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - get: my-docker-image\n" +
                        "    params:\n" +
                        "      save: save-it\n" +
                        "      rootfs: tar-it\n" +
                        "      skip_download: skip-it\n"
        );

        editor.assertProblems(
                "save-it|'boolean'",
                "tar-it|'boolean'",
                "skip-it|'boolean'"
        );

        editor.assertHoverContains("save", "docker save");
        editor.assertHoverContains("rootfs", "a `.tar` file of the image");
        editor.assertHoverContains("skip_download", "Skip `docker pull`");
    }

    @Test
    void dockerImageResourcePutParamsReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: docker-image\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: my-docker-image\n" +
                        "    params:\n" +
                        "      additional_tags: path/to/tags\n" +
                        "      build: path/to/docker/dir\n" +
                        "      load: path/to/image\n" +
                        "      dockerfile: path/to/Dockerfile\n" +
                        "      cache: cache-it\n" +
                        "      cache_tag: the-cache-tag\n" +
                        "      cache_from: cache-from-value\n" +
                        "      load_base: path/to/base-image\n" +
                        "      load_bases: load-bases-value\n" +
                        "      load_file: path/to/file-to-load\n" +
                        "      load_repository: some-repo\n" +
                        "      load_tag: some-tag\n" +
                        "      import_file: path/to/file-to-import\n" +
                        "      labels: labels-map\n" +
                        "      labels_file: path/to/file-with-labels\n" +
                        "      pull_repository: path/to/repository-to-pull\n" +
                        "      pull_tag: tag-to-pull\n" +
                        "      tag: path/to/file-containing-tag\n" +
                        "      tag_file: path/to/file-containing-tag\n" +
                        "      tag_prefix: v\n" +
                        "      tag_as_latest: tag-latest\n" +
                        "      build_args: the-build-args\n" +
                        "      build_args_file: path/to/file-with-build-args.json\n" +
                        "      target_name: some-build-stage\n" +
                        "    get_params:\n" +
                        "      save: save-it\n" +
                        "      rootfs: tar-it\n" +
                        "      skip_download: skip-it\n"
        );

        editor.assertProblems(
                "cache-it|'boolean'",
                "cache-from-value|Expecting a 'Sequence'",
                "load-bases-value|Expecting a 'Sequence'",
                "labels-map|Expecting a 'Map",

                "pull_repository|Deprecated",
                "pull_tag|Deprecated",
                "tag|Deprecated",
                "tag-latest|'boolean'",
                "the-build-args|Expecting a 'Map'",

                "save-it|'boolean'",
                "tar-it|'boolean'",
                "skip-it|'boolean'"
        );
        assertEquals(DiagnosticSeverity.Warning, editor.assertProblem("pull_repository").getSeverity());
        assertEquals(DiagnosticSeverity.Warning, editor.assertProblem("pull_tag").getSeverity());
        assertEquals(DiagnosticSeverity.Warning, editor.assertProblem("tag").getSeverity());

        editor.assertHoverContains("build", "directory containing a `Dockerfile`");
        editor.assertHoverContains("load", "directory containing an image");
        editor.assertHoverContains("dockerfile", "path of the `Dockerfile` in the directory");
        editor.assertHoverContains("cache", "first pull `image:tag` from the Docker registry");
        editor.assertHoverContains("cache_tag", "specific tag to pull");
        editor.assertHoverContains("load_base", "path to a directory containing an image to `docker load`");
        editor.assertHoverContains("load_file", "path to a file to `docker load`");
        editor.assertHoverContains("load_repository", "repository of the image loaded from `load_file`");
        editor.assertHoverContains("load_tag", "tag of image loaded from `load_file`");
        editor.assertHoverContains("import_file", "file to `docker import`");
        editor.assertHoverContains("labels", "map of labels that will be added to the image");
        editor.assertHoverContains("labels_file", "Path to a JSON file containing the image labels");
        editor.assertHoverContains("pull_repository", "DEPRECATED");
        editor.assertHoverContains("pull_tag", "DEPRECATED");
        editor.assertHoverContains(" tag:", "DEPRECATED - Use `tag_file` instead"); // The word 'tag' occurs many times in editor so use " tag: " to be precise
        editor.assertHoverContains("tag_file", "path to a file containing the name");
        editor.assertHoverContains("tag_as_latest", "tagged as `latest`");
        editor.assertHoverContains("tag_prefix", "prepended with this string");
        editor.assertHoverContains("build_args", "map of Docker build-time variables");
        editor.assertHoverContains("build_args_file", "JSON file containing");
        editor.assertHoverContains("save", "docker save");
        editor.assertHoverContains("rootfs", "a `.tar` file of the image");
        editor.assertHoverContains("skip_download", "Skip `docker pull`");
        editor.assertHoverContains("additional_tags", "Path to a space separated list of tags");
        editor.assertHoverContains("cache_from", "An array of images to consider as cache");
        editor.assertHoverContains("load_bases", "Same as `load_base`, but takes an array");
        editor.assertHoverContains("target_name", "Specify the name of the target build stage");
    }

    @Test
    void registryImageResourceeSourceReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: registry-image\n" +
                        "  source:\n" +
                        "    tag: latest\n"
        );
        editor.assertProblems(
                "my-docker-image|Unused 'Resource'",
                "source|'repository' is required"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: registry-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/sts4-build-env\n" +
                        "    insecure: this-is-not-safe\n" +
                        "    tag: latest\n" +
                        "    variant: some-suffix\n" +
                        "    semver_constraint: invalid-semver-exp\n" +
                        "    username: kdvolder\n" +
                        "    password: {{docker_password}}\n" +
                        "    aws_access_key_id: the-key-to-aws\n" +
                        "    aws_secret_access_key: the-aws-secret\n" +
                        "    aws_session_token: aws-is-in-session\n" +
                        "    aws_region: bad-aws-region\n" +
                        "    aws_role_arn: some-arn\n" +
                        "    aws_role_arns: a-list-of-arns\n" +
                        "    debug: no-bool\n" +
                        "    registry_mirror: {}\n" +
                        "    content_trust: {}\n" +
                        "    ca_certs: some-certs\n"
        );

        editor.assertProblems(
                "my-docker-image|Unused 'Resource'",
                "this-is-not-safe|boolean",
                //"invalid-semver-exp|Invalid semver constraint", //TODO: parse / check this?
                "bad-aws-region|AWSRegion",
                "aws_role_arn|Only one of 'aws_role_arn' and 'aws_role_arns'",
                "aws_role_arns|Only one of 'aws_role_arn' and 'aws_role_arns'",
                "a-list-of-arns|Expecting a 'Sequence'",
                "no-bool|boolean",
                "registry_mirror|'host' is required",
                "content_trust|Properties [repository_key, repository_key_id, repository_passphrase] are required",
                "some-certs|Expecting a 'Sequence'"
        );

        editor.assertHoverContains("repository", "The name of the repository");
        editor.assertHoverContains("insecure", "Allow insecure registry");
        editor.assertHoverContains("tag", "name of the tag");
        editor.assertHoverContains("variant", "variant suffix");
        editor.assertHoverContains("semver_constraint", "Constrain the returned semver");
        editor.assertHoverContains("username", "username to use");
        editor.assertHoverContains("password", "password to use");
        editor.assertHoverContains("aws_access_key_id", "access key ID to use for authenticating with ECR");
        editor.assertHoverContains("aws_secret_access_key", "secret access key to use for authenticating with ECR");
        editor.assertHoverContains("aws_session_token", "session token to use");
        editor.assertHoverContains("aws_region", "region to use");
        editor.assertHoverContains("aws_role_arn", "this role will");
        editor.assertHoverContains("aws_role_arns", "assumed in the specified order");
        editor.assertHoverContains("debug",  "debugging output will be printed");
        editor.assertHoverContains("registry_mirror", "pointing to a docker registry mirror service");

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: registry-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/sts4-build-env\n" +
                        "    ca_certs:\n" +
                        "    - cert1\n" +
                        "    - cert2\n" +
                        "    registry_mirror:\n" +
                        "      host: mirrorhost\n" +
                        "      username: mirroruser\n" +
                        "      password: mirrorpass\n" +
                        "      not_expected_in_mirror: bad\n" +
                        "    content_trust:\n" +
                        "      server: notary.server\n" +
                        "      repository_key: repokey\n" +
                        "      repository_key_id: keyid\n" +
                        "      repository_passphrase: Secret phrase\n" +
                        "      tls_key: tlskey\n" +
                        "      tls_cert: tlscert\n" +
                        "      bogus_prop:\n"
        );
        editor.assertProblems(
                "my-docker-image|Unused 'Resource'",
                "not_expected_in_mirror|Unknown property",
                "bogus_prop|Unknown property"
        );
        editor.assertHoverContains("host", "hostname pointing to a Docker registry");
        editor.assertHoverContains("username", "username to use");
        editor.assertHoverContains("password", "password to use");
        editor.assertHoverContains("server", "URL for the notary server");
        editor.assertHoverContains("repository_key_id", "ID used to sign the trusted collection");
        editor.assertHoverContains("repository_key", "Target key used to sign");
        editor.assertHoverContains("repository_passphrase", "passphrase of the signing/target key");
        editor.assertHoverContains("tls_key", "TLS key for the notary server");
        editor.assertHoverContains("tls_cert", "TLS certificate for the notary server");
    }

    @Test
    void registryImageResourceGetParamsReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: registry-image\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - get: my-docker-image\n" +
                        "    params:\n" +
                        "      format: bad-format\n" +
                        "      skip_download: is-download\n" +
                        "      bogus: bad"
        );

        editor.assertProblems(
                "bad-format|Valid values are: [oci, rootfs]",
                "is-download|boolean",
                "bogus|Unknown property"
        );

        editor.assertHoverContains("format", "The format to fetch as");
        editor.assertHoverContains("skip_download", "Skip downloading the image");
    }

    @Test
    void registryImageResourcePutParamsReconcileAndHovers() throws Exception {
        Editor editor;

        //TODO: new properties 
        // version
        // bump_aliases
        
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: registry-image\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: my-docker-image\n" +
                        "    params:\n" +
                        "      image: path/to/image/tarball\n" +
                        "      version: some-version\n" +
                        "      bump_aliases: is-bump\n" +
                        "      additional_tags: path/to/tagsfiles\n" +
                        "      bogus: bad\n" +
                        "    get_params:\n" +
                        "      format: bad-format\n"
        );

        editor.assertProblems(
                "is-bump|boolean",
                "bogus|Unknown property",
                "bad-format|Valid values are: [oci, rootfs]"
        );

        editor.assertHoverContains("image", 4, "path to the OCI image tarball");
        editor.assertHoverContains("version", "version number to use as a tag");
        editor.assertHoverContains("bump_aliases", "automatically bump alias tags");
        editor.assertHoverContains("additional_tags", "list of tag values");
        editor.assertHoverContains("format", "The format to fetch as");
    }

    @Test
    void s3ResourceSourceInitialResourceImplications() throws Exception {
        Editor editor;

        // 'initial_path' => 'regexp' 
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: s3-snapshots\n" +
                        "  type: s3\n" +
                        "  source:\n" +
                        "    bucket: the-bucket\n" +
                        "    access_key_id: the-access-key\n" +
                        "    secret_access_key: the-secret-key\n" +
                        "    versioned_file: path/to/file.tar.gz\n" +
                        "    initial_path: whatever\n"
        );
        editor.assertProblems(
                "s3-snapshots|Unused",
                "initial_path|Property 'initial_path' assumes that 'regexp' is also defined"
        );

        // 'initial_version' => 'versioned_file' 
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: s3-snapshots\n" +
                        "  type: s3\n" +
                        "  source:\n" +
                        "    bucket: the-bucket\n" +
                        "    access_key_id: the-access-key\n" +
                        "    secret_access_key: the-secret-key\n" +
                        "    regexp: path/to/file.tar.gz\n" +
                        "    initial_version: whatever\n"
        );
        editor.assertProblems(
                "s3-snapshots|Unused",
                "initial_version|Property 'initial_version' assumes that 'versioned_file' is also defined"
        );

    }

    @Test
    void s3ResourceSourceReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: s3-snapshots\n" +
                        "  type: s3\n" +
                        "  source:\n" +
                        "    access_key_id: the-key"
        );
        editor.assertProblems(
                "s3-snapshots|Unused 'Resource'",
                "source|One of [regexp, versioned_file] is required",
                "source|'bucket' is required"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: s3-snapshots\n" +
                        "  type: s3\n" +
                        "  source:\n" +
                        "    bucket: the-bucket\n" +
                        "    access_key_id: the-access-key\n" +
                        "    secret_access_key: the-secret-key\n" +
                        "    session_token: the-session-token\n" +
                        "    region_name: bogus-region\n" +
                        "    private: is-private\n" +
                        "    cloudfront_url: https://d5yxxxxx.cloudfront.net\n" +
                        "    endpoint: https://blah.custom.com/blah/blah\n" +
                        "    disable_ssl: no_ssl_checking\n" +
                        "    skip_ssl_verification: skipping-ssl\n" +
                        "    skip_download: skipping-downloading\n" +
                        "    server_side_encryption: some-encryption-algo\n" + //TODO: validation and CA? What values are acceptable?
                        "    sse_kms_key_id: the-master-key-id\n" +
                        "    use_v2_signing: should-use-v2\n" +
                        "    regexp: path-to-file-(.*).tar.gz\n" +
                        "    versioned_file: path/to/file.tar.gz\n" +
                        "    initial_path: some/initial/path\n" +
                        "    initial_version: 0.0.0\n" +
                        "    initial_content_text: some-initial-text\n" +
                        "    initial_content_binary: some-base64-stuff\n"
        );
        editor.assertProblems(
                "s3-snapshots|Unused 'Resource'",
                "bogus-region|unknown 'AWSRegion'",
                "is-private|'boolean'",
                "no_ssl_checking|'boolean'",
                "skipping-ssl|'boolean'",
                "skipping-downloading|'boolean'",
                "should-use-v2|'boolean'",
                "regexp|Only one of [regexp, versioned_file] should be defined",
                "versioned_file|Only one of [regexp, versioned_file] should be defined",

                "initial_path|Only one of 'initial_path' and 'initial_version' should be defined",
                "initial_version|Only one of 'initial_path' and 'initial_version' should be defined",

                "initial_content_text|Only one of 'initial_content_text' and 'initial_content_binary' should be defined",
                "initial_content_binary|Only one of 'initial_content_text' and 'initial_content_binary' should be defined"
        );

        editor.assertHoverContains("bucket", "The name of the bucket");
        editor.assertHoverContains("access_key_id", "The AWS access key");
        editor.assertHoverContains("secret_access_key", "The AWS secret key");
        editor.assertHoverContains("session_token", "The AWS STS session token");
        editor.assertHoverContains("region_name", "The region the bucket is in");
        editor.assertHoverContains("private", "Indicates that the bucket is private");
        editor.assertHoverContains("cloudfront_url", "The URL (scheme and domain) of your CloudFront distribution");
        editor.assertHoverContains("endpoint", "Custom endpoint for using S3");
        editor.assertHoverContains("disable_ssl", "Disable SSL for the endpoint");
        editor.assertHoverContains("skip_ssl_verification", "Skip SSL verification for S3 endpoint");
        editor.assertHoverContains("skip_download", "Skip downloading object from S3");
        editor.assertHoverContains("server_side_encryption", "An encryption algorithm to use");
        editor.assertHoverContains("sse_kms_key_id", "The ID of the AWS KMS master encryption key");
        editor.assertHoverContains("use_v2_signing", "Use signature v2 signing");
        editor.assertHoverContains("regexp", "The pattern to match filenames against within S3");
        editor.assertHoverContains("versioned_file", "If you enable versioning for your S3 bucket");
        editor.assertHoverContains("initial_path", "the file path containing the initial version");
        editor.assertHoverContains("initial_version", "the resource version");
        editor.assertHoverContains("initial_content_text", "Initial content as a string");
        editor.assertHoverContains("initial_content_binary", "base64 encoded string");
    }

    @Test
    void s3ResourceRegionCompletions() throws Exception {
        String[] validRegions = {
                "af-south-1",
                "ap-east-1",
                "ap-southeast-3",
                "ap-south-1",
                "ap-northeast-3",
                "ap-northeast-2",
                "ap-southeast-1",
                "ap-southeast-2",
                "ap-northeast-1",
                "ca-central-1",
                "cn-north-1",
                "cn-northwest-1",
                "eu-central-1",
                "eu-west-1",
                "eu-west-2",
                "eu-south-1",
                "eu-west-3",
                "eu-north-1",
                "me-south-1",
                "us-east-1",
                "us-east-2",
                "us-west-1",
                "us-west-2",
                "sa-east-1"
        };
        Arrays.sort(validRegions);

        String[] expectedCompletions = new String[validRegions.length];
        for (int i = 0; i < expectedCompletions.length; i++) {
            expectedCompletions[i] = validRegions[i] + "<*>";
        }

        assertContextualCompletions(
                "resources:\n" +
                        "- name: s3-snapshots\n" +
                        "  type: s3\n" +
                        "  source:\n" +
                        "    region_name: <*>"
        , //===================
                "<*>"
        , // ===>
                expectedCompletions
        );
    }

    @Test
    void s3ResourceGetParamsReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-s3-bucket\n" +
                        "  type: s3\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: my-s3-bucket\n" +
                        "    params:\n" +
                        "      acl: public-read\n" +
                        "    get_params:\n" +
                        "      no-params-expected: bad"
        );
        editor.assertProblems(
                "params|'file' is required",
                "no-params-expected|Unknown property"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-s3-bucket\n" +
                        "  type: s3\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - get: my-s3-bucket\n" +
                        "    params:\n" +
                        "      skip_download: no-bool1\n" +
                        "      unpack: no-bool2\n"
        );
        editor.assertProblems(
                "no-bool1|boolean",
                "no-bool2|boolean"
        );

        editor.assertHoverContains("skip_download", "Skip downloading object from S3");
        editor.assertHoverContains("unpack", "unpack the file");
    }

    @Test
    void s3ResourcePutParamsReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-s3-bucket\n" +
                        "  type: s3\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: my-s3-bucket\n" +
                        "    params:\n" +
                        "      acl: public-read\n" +
                        "    get_params:\n" +
                        "      no-params-expected: bad"
        );
        editor.assertProblems(
                "params|'file' is required",
                "no-params-expected|Unknown property"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-s3-bucket\n" +
                        "  type: s3\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: my-s3-bucket\n" +
                        "    params:\n" +
                        "      file: path/to/file\n" +
                        "      acl: bad-acl\n" +
                        "      content_type: anything/goes\n"
        );
        editor.assertProblems(
                "bad-acl|unknown 'S3CannedAcl'"
        );

        editor.assertHoverContains("file", "Path to the file to upload");
        editor.assertHoverContains("acl", "Canned Acl");
        editor.assertHoverContains("content_type", "MIME");
    }

    @Test
    void s3ResourcePutParamsContentAssist() throws Exception {
        String[] cannedAcls = { //See https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#canned-acl
                "private",
                "public-read",
                "public-read-write",
                "aws-exec-read",
                "authenticated-read",
                "bucket-owner-read",
                "bucket-owner-full-control",
                "log-delivery-write"
        };
        Arrays.sort(cannedAcls);

        String conText =
                "resources:\n" +
                        "- name: my-s3-bucket\n" +
                        "  type: s3\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: my-s3-bucket\n" +
                        "    params:\n" +
                        "      <*>";

        assertContextualCompletions(conText,
                "content_type: json<*>"
        , //=>
                "content_type: application/geo+json<*>",
                "content_type: application/hal+json<*>",
                "content_type: application/json; charset=utf-8<*>",
                "content_type: application/manifest+json; charset=utf-8<*>",
                "content_type: application/jose+json<*>"
        );

        String[] expectedAclCompletions = new String[cannedAcls.length];
        for (int i = 0; i < expectedAclCompletions.length; i++) {
            expectedAclCompletions[i] = "acl: " + cannedAcls[i] + "<*>";
        }
        assertContextualCompletions(conText,
                "acl: <*>"
        , // ===>
                expectedAclCompletions
        );
    }

    @Test
    void poolResourceSourceReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: swimming-pool\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    private_key: stuff"
        );
        editor.assertProblems(
                "swimming-pool|Unused",
                "source|[branch, pool, uri] are required"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: the--locks\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    uri: git@github.com:concourse/locks.git\n" +
                        "    branch: master\n" +
                        "    pool: aws\n" +
                        "    private_key: |\n" +
                        "      -----BEGIN RSA PRIVATE KEY-----\n" +
                        "      MIIEowIBAAKCAQEAtCS10/f7W7lkQaSgD/mVeaSOvSF9ql4hf/zfMwfVGgHWjj+W\n" +
                        "      ...\n" +
                        "      DWiJL+OFeg9kawcUL6hQ8JeXPhlImG6RTUffma9+iGQyyBMCGd1l\n" +
                        "      -----END RSA PRIVATE KEY-----\n" +
                        "    username: jonhsmith\n" +
                        "    password: his-password\n" +
                        "    retry_delay: retry-after\n"
        );
        editor.assertProblems(
                "the--locks|Unused",
                "retry-after|'Duration'"
        );

        editor.assertHoverContains("uri", "The location of the repository.");
        editor.assertHoverContains("branch", "The branch to track");
        editor.assertHoverContains("pool", 2, "The logical name of your pool of things to lock");
        editor.assertHoverContains("private_key", "Private key to use when pulling/pushing");
        editor.assertHoverContains("username", "Username for HTTP(S) auth");
        editor.assertHoverContains("password", "Password for HTTP(S) auth ");
        editor.assertHoverContains("retry_delay", "how long to wait until retrying");
    }

    @Test
    void poolResourceGetParamsReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-locks\n" +
                        "  type: pool\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - get: my-locks\n" +
                        "    params:\n" +
                        "      no-params-expected: bad"
        );

        editor.assertProblems(
                "no-params-expected|Unknown property"
        );
    }

    @Test
    void poolResourcePutParamsReconcileAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-locks\n" +
                        "  type: pool\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: my-locks\n" +
                        "    params:\n" +
                        "      acquire: should-acquire\n" +
                        "      claim: a-specific-lock\n" +
                        "      release: path/to/lock\n" +
                        "      add: path/to/lock\n" +
                        "      add_claimed: path/to/lock\n" +
                        "      remove: path/to/lock"
        );

        editor.assertProblems(
                "acquire|Only one of",
                "should-acquire|'boolean'",
                "claim|Only one of",
                "release|Only one of",
                "add|Only one of",
                "add_claimed|Only one of",
                "remove|Only one of"
        );

        editor.assertHoverContains("acquire", "attempt to move a randomly chosen lock");
        editor.assertHoverContains("claim", "the specified lock from the pool will be acquired");
        editor.assertHoverContains("release", "release the lock");
        editor.assertHoverContains("add", "add a new lock to the pool in the unclaimed state");
        editor.assertHoverContains("add_claimed", "in the *claimed* state");
        editor.assertHoverContains("remove", "remove the given lock from the pool");
    }

    @Test
    void resourceCheckEveryValidation() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/816
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: git\n" +
                        "  type: git\n" +
                        "  check_every: bad-duration\n"
        );

        editor.assertProblems(
                "git|Unused 'Resource'",
                "bad-duration|not a valid 'Duration'"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: git\n" +
                        "  type: git\n" +
                        "  check_every:  never\n"
        );
        editor.assertProblems(
                "git|Unused 'Resource'"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: git\n" +
                        "  type: git\n" +
                        "  check_every:  1h\n"
        );
        editor.assertProblems(
                "git|Unused 'Resource'"
        );
    }

    @Test
    void resourceCheckEveryCompletion() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/816
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: git\n" +
                        "  type: git\n" +
                        "  check_every:  <*>"
        );
        editor.assertContainsCompletions("<*>", "never<*>");
    }

    @Test
    void semverResourceSourceReconcileAtomNotAllowed() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source: an-atom"
        );
        editor.assertProblems(
                "version|Unused 'Resource'",
                "an-atom|Expecting a 'Map'"
        );
    }

    @Test
    void semverResourceSourceReconcileRequiredProps() throws Exception {
        Editor editor;

        //required props for s3 driver
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: s3"
        );
        editor.assertProblems(
                "version|Unused",
                "source|[access_key_id, bucket, key, secret_access_key] are required"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source: {}"
        );
        editor.assertProblems(
                "version|Unused",
                "source|[access_key_id, bucket, key, secret_access_key] are required"
        );

        // required props for git driver
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git"
        );
        editor.assertProblems(
                "version|Unused",
                "source|[branch, file, uri] are required"
        );

        //required props for swift driver
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: swift"
        );
        editor.assertProblems(
                "version|Unused",
                "source|'openstack' is required"
        );

        //required props for gcs driver
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  check_every: 1h  \n" +
                        "  source:\n" +
                        "    driver: gcs"
        );
        editor.assertProblems(
                "version|Unused",
                "source|Properties [bucket, json_key, key] are required"
        );
    }

    @Test
    void semverResourceSourceBadDriver() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: bad-driver"
        );
        editor.assertProblems(
                "version|Unused",
                "bad-driver|'SemverDriver'"
        );
    }

    @Test
    void semverGcsResourceSourceContentAssist() throws Exception {
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: gcs\n" +
                        "    <*>"
        , // ===========
                "<*>"
        , // ==>
                "bucket: $1\n" +
                        "    key: $2\n" +
                        "    json_key: $3<*>"
        , // --
                "bucket: <*>",
                "json_key: <*>",
                "key: <*>"
        );
    }

    @Test
    void semverGcsResourceReconcileAndHover() throws Exception {
        Editor editor;

        // required props for git driver
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: gcs\n" +
                        "    bucket: some-bucket\n" +
                        "    key: some-key\n" +
                        "    json_key: some-json-key-string\n" +
                        "    bogus: bad"
        );
        editor.assertProblems(
                "version|Unused",
                "bogus|Unknown property"
        );

        editor.assertHoverContains("driver", "The driver to use");
        editor.assertHoverContains("bucket", "The name of the bucket");
        editor.assertHoverContains("key", "key to use for the object in the bucket tracking the version");
        editor.assertHoverContains("json_key", "contents of your GCP Account JSON Key");
    }

    @Test
    void semverGitResourceSourceContentAssist() throws Exception {
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    <*>"
        , // ===========
                "<*>"
        , // ==>
                "uri: $1\n" +
                        "    branch: $2\n" +
                        "    file: $3<*>"
        , //---
                "uri: <*>"
        );
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    uri: something\n" +
                        "<*>"
        , // =============
                "    <*>"
        , // ==>
                "    branch: <*>"
        ,
                "    file: <*>"
        );
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    uri: something\n" +
                        "    branch: master\n" +
                        "    file: somefile\n" +
                        "<*>"
        , // =============
                "    <*>"
        , // ==>
                "    commit_message: <*>"
        ,
                "    depth: <*>"
        ,
                "    git_user: <*>"
        ,
                "    initial_version: <*>"
        ,
                "    password: <*>"
        ,
                "    private_key: <*>"
        ,
                "    skip_ssl_verification: <*>"
        ,
                "    username: <*>"
        );

    }

    @Test
    void semverGitResourceSourceReconcileAndHovers() throws Exception {
        Editor editor;

        // required props for git driver
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    initial_version: not-a-version\n" + //TODO: should be marked as a error but isn't yet.
                        "    driver: git\n" +
                        "    uri: git@github.com:concourse/concourse.git\n" +
                        "    branch: version\n" +
                        "    file: version\n" +
                        "    private_key: {{concourse-repo-private-key}}\n" +
                        "    commit_message: '[skip ci] my commit message'\n" +
                        "    skip_ssl_verification: skip-ssl\n" +
                        "    username: jsmith\n" +
                        "    password: s3cre$t\n" +
                        "    git_user: jsmith@mailhost.com\n" +
                        "    depth: 0\n" +
                        "    bogus: bad"
        );
        editor.assertProblems(
                "version|Unused",
                "skip-ssl|'boolean'",
                "0|must be at least 1",
                "bogus|Unknown property"
        );
        editor.assertHoverContains("initial_version", "version number to use when bootstrapping");
        editor.assertHoverContains("driver", "The driver to use");
        editor.assertHoverContains("uri", "The repository URL");
        editor.assertHoverContains("branch", "The branch the file lives on");
        editor.assertHoverContains("file", "The name of the file");
        editor.assertHoverContains("private_key", "The SSH private key");
        editor.assertHoverContains("username", "Username for HTTP(S) auth");
        editor.assertHoverContains("password", "Password for HTTP(S) auth");
        editor.assertHoverContains("git_user", "The git identity to use");
        editor.assertHoverContains("commit_message", "overides the default commit message");
        editor.assertHoverContains("skip_ssl_verification", "Skip SSL verification for git endpoint");
        editor.assertHoverContains("depth", "shallow clone the repository using the `--depth` option");
    }

    @Test
    void semverResourceSourcePrimaryContentAssist() throws Exception {

        //S3 completions
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: fff\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    <*>"
        , /////////////
                "<*>"
        , // ==>
                //snippet:
                "bucket: $1\n" +
                        "    key: $2\n" +
                        "    access_key_id: $3\n" +
                        "    secret_access_key: $4<*>",
                //non-snippet:
                "bucket: <*>",
                "driver: <*>"
        );

        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: fff\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: s3\n" +
                        "    <*>"
        , /////////////
                "<*>"
        , // ==>
                //snippet:
                "bucket: $1\n" +
                        "    key: $2\n" +
                        "    access_key_id: $3\n" +
                        "    secret_access_key: $4<*>",
                //non-snippet:
                "bucket: <*>"
        );

        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: fff\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: s3\n" +
                        "    bucket: some-bucket\n" +
                        "    <*>"
        , /////////////
                "<*>"
        ,  // ==>
                "access_key_id: <*>",
                "key: <*>",
                "secret_access_key: <*>"
        );

        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: fff\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    bucket: some-bucket\n" +
                        "    <*>"
        , /////////////
                "<*>"
        ,  // ==>
                "access_key_id: <*>",
                "driver: <*>",
                "key: <*>",
                "secret_access_key: <*>"
        );

        //git completions
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: fff\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    <*>"
        , /////////////
                "<*>"
        , // ==>
                "uri: $1\n" +
                        "    branch: $2\n" +
                        "    file: $3<*>"
        , //===
                "uri: <*>"
        );
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: fff\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    uri: blah\n" +
                        "    <*>"
        , /////////////
                "<*>"
        , // ==>
                "branch: <*>",
                "file: <*>"
        );
        assertContextualCompletions(PLAIN_COMPLETION,
                "resources:\n" +
                        "- name: fff\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    uri: blah\n" +
                        "    branch: master\n" +
                        "    file: version-file\n" +
                        "    <*>"
        , /////////////
                "<*>"
        , // ==>
                "commit_message: <*>",
                "depth: <*>",
                "git_user: <*>",
                "initial_version: <*>",
                "password: <*>",
                "private_key: <*>",
                "skip_ssl_verification: <*>",
                "username: <*>"
        );
    }

    @Test
    void semverS3ResourceSourceReconcileAndHovers() throws Exception {
        Editor editor;

        //without explicit 'driver'... should assume s3 by default
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    initial_version: 1.2.3\n" +
                        "    bucket: the-bucket\n" +
                        "    key: object-key\n" +
                        "    access_key_id: aws-access-key\n" +
                        "    secret_access_key: aws-access-key\n" +
                        "    region_name: bogus-region\n" +
                        "    endpoint: https://blah.com/blah\n" +
                        "    disable_ssl: no-use-ssl\n" +
                        "    bogus-prop: bad"
        );
        editor.assertProblems(
                "version|Unused 'Resource'",
                "bogus-region|'AWSRegion'",
                "no-use-ssl|'boolean'",
                "bogus-prop|Unknown property"
        );


        //with explicit 'driver: s3'
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    initial_version: 1.2.3\n" +
                        "    driver: s3\n" +
                        "    bucket: the-bucket\n" +
                        "    key: object-key\n" +
                        "    access_key_id: aws-access-key\n" +
                        "    secret_access_key: aws-access-key\n" +
                        "    region_name: bogus-region\n" +
                        "    endpoint: https://blah.com/blah\n" +
                        "    disable_ssl: no-use-ssl\n" +
                        "    bogus-prop: bad"
        );
        editor.assertProblems(
                "version|Unused 'Resource'",
                "bogus-region|'AWSRegion'",
                "no-use-ssl|'boolean'",
                "bogus-prop|Unknown property"
        );

        editor.assertHoverContains("initial_version", "version number to use when bootstrapping");
        editor.assertHoverContains("driver", "The driver to use");
        editor.assertHoverContains("bucket", "The name of the bucket");
        editor.assertHoverContains("key", "The key to use for the object");
        editor.assertHoverContains("access_key_id", "The AWS access key to");
        editor.assertHoverContains("secret_access_key", "The AWS secret key to");
        editor.assertHoverContains("region_name", "The region the bucket is in");
        editor.assertHoverContains("endpoint", "Custom endpoint for using S3");
        editor.assertHoverContains("disable_ssl", "Disable SSL for the endpoint");
    }

    @Test
    void semverSwiftResourceSourceReconcileAndHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    initial_version: 1.2.3\n" +
                        "    driver: swift\n" +
                        "    openstack:\n" +
                        "       container: nice-container\n" +
                        "       item_name: flubber-blub\n" +
                        "       region_name: us-west-1\n"
        );
        editor.assertProblems("version|Unused 'Resource'");
        editor.assertHoverContains("openstack", "All openstack configuration");
    }

    @Test
    void GH_849_pre_without_version() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: test\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    uri: some-git-url\n" +
                        "    branch: main\n" +
                        "    file: VERSION\n" +
                        "jobs:\n" +
                        "- name: test\n" +
                        "  plan:\n" +
                        "  - get: test\n" +
                        "    params:\n" +
                        "      pre_without_version: not-a-bool\n" +
                        "  - put: test\n" +
                        "    params:\n" +
                        "      pre_without_version: not-b-bool\n"
        );
        editor.assertProblems(
                "not-a-bool|boolean",
                "not-b-bool|boolean"
        );

        editor.assertHoverContains("pre_without_version", 1, "PreRelease will be bumped");
        editor.assertHoverContains("pre_without_version", 2, "PreRelease will be bumped");
    }


    @Test
    void GH_849_build_without_version() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: test\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    driver: git\n" +
                        "    uri: some-git-url\n" +
                        "    branch: main\n" +
                        "    file: VERSION\n" +
                        "jobs:\n" +
                        "- name: test\n" +
                        "  plan:\n" +
                        "  - get: test\n" +
                        "    params:\n" +
                        "      build_without_version: not-a-bool\n" +
                        "  - put: test\n" +
                        "    params:\n" +
                        "      build_without_version: not-b-bool\n"
        );
        editor.assertProblems(
                "not-a-bool|boolean",
                "not-b-bool|boolean"
        );

        editor.assertHoverContains("build_without_version", 1, "Same as pre_without_version but for build labels");
        editor.assertHoverContains("build_without_version", 2, "Same as pre_without_version but for build labels");

    }

    @Test
    void semverResourceGetParamsReconcileAndHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    initial_version: 1.2.3\n" +
                        "    driver: swift\n" +
                        "    openstack: whatever\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - get: version\n" +
                        "    params:\n" +
                        "      bump: what-to-bump\n" +
                        "      pre: beta\n" +
                        "      bogus: bad\n"
        );
        editor.assertProblems(
                "what-to-bump|[final, major, minor, patch]",
                "bogus|Unknown property"
        );

        editor.assertHoverContains("bump", "Bump the version number");
        editor.assertHoverContains("pre", "bump to a prerelease");
    }

    @Test
    void semverPutParamsReconcileAndHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "  source:\n" +
                        "    initial_version: 1.2.3\n" +
                        "    driver: swift\n" +
                        "    openstack: whatever\n" +
                        "jobs:\n" +
                        "- name: a-job\n" +
                        "  plan:\n" +
                        "  - put: version\n" +
                        "    params:\n" +
                        "      file: version-file\n" +
                        "      bump: what-to-bump\n" +
                        "      pre: alpha\n" +
                        "      bogus-one: bad\n" +
                        "    get_params:\n" +
                        "      file: not-expected-here\n" +
                        "      bump: what-to-get-bump\n" +
                        "      pre: beta\n" +
                        "      bogus-two: bad\n"
        );
        editor.assertProblems(
                "what-to-bump|[final, major, minor, patch]",
                "bogus-one|Unknown property",
                "file|Unknown property",
                "what-to-get-bump|[final, major, minor, patch]",
                "bogus-two|Unknown property"
        );

        editor.assertHoverContains("file", 1, "Path to a file containing the version number");
        editor.assertHoverContains("bump", 1, "Bump the version number");
        editor.assertHoverContains("bump", 2, "Bump the version number");
        editor.assertHoverContains("pre", 1, "bump to a prerelease");
        editor.assertHoverContains("pre", 2, "bump to a prerelease");
    }

    @Test
    void reconcileExplicitResourceAttributeInPutStep() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/138568839
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: aws-environments\n" +
                        "  type: pool\n" +
                        "jobs:\n" +
                        "- name: test-multi-aws\n" +
                        "  plan:\n" +
                        "    - put: environment-1\n" +
                        "      resource: aws-environments\n" +
                        "      params:\n" +
                        "        acquire: true\n" +
                        "        bogus_param: bad\n" +
                        "      get_params:\n" +
                        "        bogus_get_param: bad"
        );
        editor.assertProblems(
                "bogus_param|Unknown property",
                "bogus_get_param|Unknown property"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: aws-environments\n" +
                        "  type: pool\n" +
                        "jobs:\n" +
                        "- name: test-multi-aws\n" +
                        "  plan:\n" +
                        "    - get: environment-1\n" +
                        "      resource: aws-environments\n" +
                        "      params:\n" +
                        "        bogus_param: bad\n"
        );
        editor.assertProblems(
                "bogus_param|Unknown property"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: aws-environments\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    uri: git@github.com:concourse/locks.git\n" +
                        "    branch: master\n" +
                        "    pool: aws\n" +
                        "    private_key: |\n" +
                        "      -----BEGIN RSA PRIVATE KEY-----\n" +
                        "      MIIEowIBAAKCAQEAtCS10/f7W7lkQaSgD/mVeaSOvSF9ql4hf/zfMwfVGgHWjj+W\n" +
                        "      ...\n" +
                        "      DWiJL+OFeg9kawcUL6hQ8JeXPhlImG6RTUffma9+iGQyyBMCGd1l\n" +
                        "      -----END RSA PRIVATE KEY-----\n" +
                        "    username: jonhsmith\n" +
                        "    password: his-password\n" +
                        "    retry_delay: 10s\n" +
                        "jobs:\n" +
                        "- name: test-multi-aws\n" +
                        "  plan:\n" +
                        "    - get: aws-environments\n" +
                        "      params: {}     \n" +
                        "    - put: environment-1\n" +
                        "      resource: aws-environments\n" +
                        "      params: {acquire: true}\n" +
                        "    - put: environment-2\n" +
                        "      resource: aws-environments\n" +
                        "      params: \n" +
                        "        acquire: true\n" +
                        "        bogus_param: blah\n" +
                        "    - task: test-multi-aws\n" +
                        "      file: my-scripts/test-multi-aws.yml\n" +
                        "    - put: aws-environments\n" +
                        "      params: {release: environment-1}\n" +
                        "    - put: aws-environments\n" +
                        "      params: {release: environment-2}"
        );
        editor.assertProblems(
                "bogus_param|Unknown property"
        );
    }

    @Test
    void resourceNameContentAssist() throws Exception {
        String conText;

        conText =
                "resources:\n" +
                        "- name: foo-resource\n" +
                        "- name: bar-resource\n" +
                        "- name: other-resource\n" +
                        "jobs:\n" +
                        "- name: test-multi-aws\n" +
                        "  plan:\n" +
                        "  - <*>";
        assertContextualCompletions(conText
        , // ==============
                "get: <*>"
        ,
                "get: bar-resource<*>",
                "get: foo-resource<*>",
                "get: other-resource<*>"
        );
        assertContextualCompletions(conText
        , // ==============
                "put: <*>"
        , // ==>
                "put: bar-resource<*>",
                "put: foo-resource<*>",
                "put: other-resource<*>"
        );


        conText =
                "resources:\n" +
                        "- name: foo-resource\n" +
                        "- name: bar-resource\n" +
                        "- name: other-resource\n" +
                        "jobs:\n" +
                        "- name: test-multi-aws\n" +
                        "  plan:\n" +
                        "  - put: something\n" +
                        "    <*>";
        assertContextualCompletions(conText
        , // ==============
                "resource: <*>"
        , // ==>
                "resource: bar-resource<*>",
                "resource: foo-resource<*>",
                "resource: other-resource<*>"
        );

        conText =
                "resources:\n" +
                        "- name: foo-resource\n" +
                        "- name: bar-resource\n" +
                        "- name: other-resource\n" +
                        "jobs:\n" +
                        "- name: test-multi-aws\n" +
                        "  plan:\n" +
                        "  - <*>\n" +
                        "    resource: foo-resource\n"; // presence of explicit 'resource' attribute should disable treating the name in put/get as a resource-name

        assertContextualCompletions(conText,
                "put: <*>"
        // ==> NONE
        );
        assertContextualCompletions(conText,
                "get: <*>"
        // ==> NONE
        );
    }

    @Test
    void gotoResourceDefinition() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "- name: build-env\n" +
                        "  type: docker-image\n" +
                        "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: my-git\n" +
                        "    params:\n" +
                        "      rootfs: true\n" +
                        "      save: true\n" +
                        "  - put: build-env\n" +
                        "    build: my-git/docker\n"
        );

        editor.assertGotoDefinition(editor.positionOf("get: my-git", "my-git"),
                editor.rangeOf("- name: my-git", "my-git"),
                editor.rangeOf("get: my-git", "my-git")
        );
    }

    @Test
    void gotoJobDefinition() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-git\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: prepare-stuff\n" +
                        "  plan:\n" +
                        "  - get: my-git\n" +
                        "  - task: preparations\n" +
                        "    file: my-git/ci/tasks/preparations.yml\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: my-git\n" +
                        "    passed:\n" +
                        "    - prepare-stuff\n"
        );
        editor.assertGotoDefinition(editor.positionOf("- prepare-stuff", "prepare-stuff"),
                editor.rangeOf("- name: prepare-stuff", "prepare-stuff"),
                editor.rangeOf("- prepare-stuff", "prepare-stuff")
        );
    }

    @Test
    void gotoResourceTypeDefinition() throws Exception {
        Editor editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: slack-notification\n" +
                        "  type: docker-image\n" +
                        "resources:\n" +
                        "- name: zazazee\n" +
                        "  type: slack-notification\n"
        );
        editor.assertGotoDefinition(editor.positionOf("type: slack-notification", "slack-notification"),
                editor.rangeOf("- name: slack-notification", "slack-notification"),
                editor.rangeOf("type: slack-notification", "slack-notification")
        );
    }

    @Test
    void reconcileResourceTypeNames() throws Exception {
        String userDefinedResourceTypesSnippet =
                "resource_types:\n" +
                        "- name: s3-multi\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/s3-resource-simple\n" +
                        "- name: slack-notification\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: cfcommunity/slack-notification-resource\n" +
                        "    tag: latest\n";
        String[] goodNames = {
                //user-defined:
                "s3-multi", "slack-notification",
                //built-in:
                "git", "hg", "time", "s3",
                "archive", "semver", "github-release",
                "docker-image", "tracker", "pool", "cf", "bosh-io-release",
                "bosh-io-stemcell", "bosh-deployment", "vagrant-cloud"
        };
        String[] badNames = {
                "bogus", "wrong", "not-defined-resource-type"
        };

        //All the bad names are detected and flagged:
        for (String badName : badNames) {
            Editor editor = harness.newEditor(
                    userDefinedResourceTypesSnippet +
                            "resources:\n" +
                            "- name: the-resource\n" +
                            "  type: " + badName
            );
            editor.assertProblems(
                    "the-resource|Unused 'Resource'",
                    badName + "|Resource Type does not exist"
            );
        }

        //All the good names are accepted:
        for (String goodName : goodNames) {
            Editor editor = harness.newEditor(
                    userDefinedResourceTypesSnippet +
                            "resources:\n" +
                            "- name: the-resource\n" +
                            "  type: " + goodName
            );
            editor.assertProblems(/*None*/
                    "the-resource|Unused 'Resource'"
            );
        }
    }

    @Test
    void contentAssistResourceTypeNames() throws Exception {
        String[] goodNames = {
                //user-defined:
                "s3-multi", "slack-notification",
                //built-in:
                "git", "hg", "time", "s3",
                "archive", "semver", "github-release",
                "docker-image", "registry-image", "tracker", "pool", "cf", "bosh-io-release",
                "bosh-io-stemcell", "bosh-deployment", "vagrant-cloud"
        };
        Arrays.sort(goodNames);

        String context =
                "resource_types:\n" +
                        "- name: s3-multi\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/s3-resource-simple\n" +
                        "- name: slack-notification\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: cfcommunity/slack-notification-resource\n" +
                        "    tag: latest\n" +
                        "resources:\n" +
                        "- type: <*>\n" +
                        "  source:\n";

        //All the good names are accepted:
        String[] expectedCompletions = new String[goodNames.length];
        for (int i = 0; i < expectedCompletions.length; i++) {
            expectedCompletions[i] = goodNames[i] + "<*>";
        }

        assertContextualCompletions(context,
                "<*>"
        , // ===>
                expectedCompletions
        );
    }

    @Test
    void taskWithYamlParams() throws Exception {
        Editor editor;

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "---\n" +
                        "platform: linux\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: czero/platform-automation\n" +
                        "params:\n" +
                        "  DEBUG: false\n" +
                        "  VCENTER_URL: \n" +
                        "  VCENTER_INSECURE: true\n" +
                        "  NODE_COUNT: 4\n" +
                        "  IDRAC_IPS:\n" +
                        "    - 1.1.1.1\n" +
                        "    - 2.2.2.2\n" +
                        "inputs:\n" +
                        "  - name: pipeline\n" +
                        "run:\n" +
                        "  path: pipeline/tasks/re-image-hosts/task.sh\n"

        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void reconcileTaskFileToplevelProperties() throws Exception {
        Editor editor;

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "rootfs_uri: some-image"
        );
        editor.assertProblems("rootfs_uri: some-imag^e^|[platform, run] are required");

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: a-platform\n" +
                        "image_resource:\n" +
                        "  name: should-not-be-here\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    bogus-source-prop: bad\n" +
                        "    repository: ruby\n" +
                        "    tag: '2.1'\n" +
                        "rootfs_uri: some-image\n" +
                        "inputs:\n" +
                        "- path: path/to/input\n" +
                        "outputs:\n" +
                        "- path: path/to/output\n" +
                        "run:\n" +
                        "  path: my-app/scripts/test\n" +
                        "params: the-params\n"
        );
        editor.assertProblems(
                "image_resource|Only one of [image_resource, rootfs_uri, image] should be defined",
                "name|Unknown property",
                "bogus-source-prop|Unknown property",
                "rootfs_uri|Only one of [image_resource, rootfs_uri, image] should be defined",
                "-^ path: path/to/input|'name' is required",
                "-^ path: path/to/output|'name' is required",
                "the-params|Expecting a 'Map'"
        );
    }

    @Test
    void taskFileMissingToplevelPropertiesUnderlinesLastNonWhitespaceChar() throws Exception {
        Editor editor;

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "rootfs_uri: some-image"
        );
        editor.assertProblems("rootfs_uri: some-imag^e^|[platform, run] are required");

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "rootfs_uri: some-image\n" +
                        "   \n"
        );
        editor.assertProblems("rootfs_uri: some-imag^e^|[platform, run] are required");

    }

    @Test
    void contentAssistTaskFileToplevelProperties() throws Exception {
        assertTaskCompletions(
                "<*>"
        , // ==>
                "platform: $1\n" +
                        "run:\n" +
                        "  path: $2<*>"
        ,
                "platform: <*>"
        ,
                "run:\n" +
                        "  path: <*>"
        );

        assertContextualTaskCompletions(
                "run: {}\n" +
                        "platform: linux\n" +
                        "<*>"
        ,
                "<*>"
        , // ==>
                "caches:\n" +
                        "- path: <*>"
        ,
                "image_resource:\n" +
                        "  type: <*>"
        ,
                "inputs:\n" +
                        "- name: <*>"
        ,
                "outputs:\n" +
                        "- name: <*>"
        ,
                "params:\n" +
                        "  <*>"
        ,
                "rootfs_uri: <*>"
        ,
                "image: <*>"
        );

        assertTaskCompletions(
                "platform: <*>"
        , //=>
                "platform: darwin<*>",
                "platform: linux<*>",
                "platform: windows<*>"
        );
    }

    @Test
    void hoversForTaskFileToplevelProperties() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "image: some-image\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/sts4-build-env\n" +
                        "inputs: []\n" +
                        "outputs: []\n" +
                        "params: {}\n" +
                        "platform: linux\n" +
                        "run:\n" +
                        "  path: sts4/concourse/tasks/build-vscode-extensions.sh"
        );

        editor.assertHoverContains("platform", "The platform the task should run on");
        editor.assertHoverContains("image_resource", "The base image of the container");
        editor.assertHoverContains("image", "A string specifying the rootfs of the container");
        editor.assertHoverContains("inputs", "The expected set of inputs for the task");
        editor.assertHoverContains("outputs", "The artifacts produced by the task");
        editor.assertHoverContains("run", "Note that this is *not* provided as a script blob");
        editor.assertHoverContains("params", "A key-value mapping of values that are exposed to the task via environment variables");
        editor.assertHoverContains("repository", "The name of the repository");

        editor.assertHoverContains("platform", "The platform the task should run on");
    }

    @Test
    void reconcileEmbeddedTaskConfig() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: foo\n" +
                        "  plan:\n" +
                        "  - task: the-task\n" +
                        "    config:\n" +
                        "      platform: a-platform\n" +
                        "      image_resource:\n" +
                        "        name: should-not-be-here\n" +
                        "        type: docker-image\n" +
                        "        source:\n" +
                        "          bogus-source-prop: bad\n" +
                        "          repository: ruby\n" +
                        "          tag: '2.1'\n" +
                        "      rootfs_uri: some-image\n" +
                        "      inputs:\n" +
                        "      - path: path/to/input\n" +
                        "      outputs:\n" +
                        "      - path: path/to/output\n" +
                        "      run:\n" +
                        "        path: my-app/scripts/test\n" +
                        "      params: the-params"
        );
        editor.assertProblems(
                "image_resource|Only one of [image_resource, rootfs_uri, image] should be defined",
                "name|Unknown property",
                "bogus-source-prop|Unknown property",
                "rootfs_uri|Only one of [image_resource, rootfs_uri, image] should be defined",
                "-^ path: path/to/input|'name' is required",
                "-^ path: path/to/output|'name' is required",
                "the-params|Expecting a 'Map'"
        );
    }

    @Test
    void taskRunPropertiesValidationAndHovers() throws Exception {
        Editor editor;

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "inputs:\n" +
                        "- name: sts4\n" +
                        "outputs:\n" +
                        "- name: vsix-files\n" +
                        "platform: linux\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/sts4-build-env\n" +
                        "run:\n" +
                        "  path: sts4/concourse/tasks/build-vscode-extensions.sh\n" +
                        "  args: the-args\n" +
                        "  user: admin\n" +
                        "  dir: the-dir\n" +
                        "  bogus: bad\n"
        );
        editor.assertProblems(
                "the-args|Expecting a 'Sequence'",
                "bogus|Unknown property"
        );

        editor.assertHoverContains("path", "The command to execute, relative to the task's working directory");
        editor.assertHoverContains("args", "Arguments to pass to the command");
        editor.assertHoverContains("dir", "A directory, relative to the initial working directory, to set as the working directory");
        editor.assertHoverContains("user", "Explicitly set the user to run as");

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "inputs:\n" +
                        "- name: sts4\n" +
                        "outputs:\n" +
                        "- name: vsix-files\n" +
                        "platform: linux\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kdvolder/sts4-build-env\n" +
                        "run:\n" +
                        "  user: admin\n"
        );
        editor.assertProblems("run|'path' is required");
    }

    @Test
    void nameAndPathHoversInTaskInputsAndOutputs() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "inputs:\n" +
                        "- name: sts4\n" +
                        "  path: botk\n" +
                        "outputs:\n" +
                        "- name: vsix-files\n" +
                        "  path: zaza"
        );
        editor.assertHoverContains("name", 1, "The logical name of the input");
        editor.assertHoverContains("name", 2, "The logical name of the output");
        editor.assertHoverContains("path", 1, "The path where the input will be placed");
        editor.assertHoverContains("path", 2, "The path to a directory where the output will be taken from");
    }

    @Test
    void PT_140711495_triple_dash_at_start_of_file_disrupts_content_assist() throws Exception {
        assertContextualCompletions(
                "#leading comment\n" +
                        "---\n" +
                        "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com/spring-projects/sts4.git\n" +
                        "    <*>"
        , // ==================
                "bra<*>"
        , // ==>
                "branch: <*>"
        ,
                "submodule_credentials:\n" +
                        "    - host: $1\n" +
                        "      username: $2\n" +
                        "      password: $3<*>"
        );

        assertContextualCompletions(
                "---\n" +
                        "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com/spring-projects/sts4.git\n" +
                        "    <*>"
        , // ==================
                "bra<*>"
        , // ==>
                "branch: <*>"
        ,
                "submodule_credentials:\n" +
                        "    - host: $1\n" +
                        "      username: $2\n" +
                        "      password: $3<*>"
        );

        assertContextualCompletions(
//				"---\n" +
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com/spring-projects/sts4.git\n" +
                        "    <*>"
        , // ==================
                "bra<*>"
        , // ==>
                "branch: <*>"
        ,
                "submodule_credentials:\n" +
                        "    - host: $1\n" +
                        "      username: $2\n" +
                        "      password: $3<*>"
        );
    }

    @Test
    void PT_141050891_language_server_crashes_on_CA_before_first_document_marker() throws Exception {
        Editor editor = harness.newEditor(
                "%Y<*>\n" +
                        "#leading comment\n" +
                        "---\n" +
                        "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com/spring-projects/sts4.git\n" +
                        "    <*>"
        );
        //We don't expect completions, but this should at least not crash!
        editor.assertCompletions(/*NONE*/);
    }

    @Test
    void resourceInEmbeddedTaskConfigNotRequiredIfSpecifiedInTask() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: docker-image\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    username: {{docker_hub_username}}\n" +
                        "    password: {{docker_hub_password}}\n" +
                        "    repository: kdvolder/sts3-build-env\n" +
                        "jobs:\n" +
                        "- name: build-commons-update-site\n" +
                        "  plan:\n" +
                        "  - task: hello-world\n" +
                        "    image: docker-image\n" + //Given here! So not required in config!
                        "    config:\n" +
                        "      inputs:\n" +
                        "      - name: commons-git\n" +
                        "      platform: linux\n" +
                        "      run:\n" +
                        "        path: which\n" +
                        "        args:\n" +
                        "        - mvn"
        );
        editor.assertProblems(/*none*/);

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: docker-image\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    username: {{docker_hub_username}}\n" +
                        "    password: {{docker_hub_password}}\n" +
                        "    repository: kdvolder/sts3-build-env\n" +
                        "jobs:\n" +
                        "- name: build-commons-update-site\n" +
                        "  plan:\n" +
                        "  - task: hello-world\n" +
                        "    config:\n" +
                        "      inputs:\n" +
                        "      - name: commons-git\n" +
                        "      platform: linux\n" +
                        "      run:\n" +
                        "        path: which\n" +
                        "        args:\n" +
                        "        - mvn"
        );

        editor.assertProblems(
                "docker-image|Unused",
                "config|One of [image_resource, rootfs_uri, image] is required"
        );
    }

    @Test
    void resourceInTaskConfigFileNotRequired() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "inputs:\n" +
                        "- name: commons-git\n" +
                        "platform: linux\n" +
                        "run:\n" +
                        "  path: which\n" +
                        "  args:\n" +
                        "  - mvn"
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void resourceInEmbeddedTaskConfigDeprecated() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-docker-image\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    username: {{docker_hub_username}}\n" +
                        "    password: {{docker_hub_password}}\n" +
                        "    repository: kdvolder/sts3-build-env\n" +
                        "jobs:\n" +
                        "- name: build-commons-update-site\n" +
                        "  plan:\n" +
                        "  - task: hello-world\n" +
                        "    image: my-docker-image\n" +
                        "    config:\n" +
                        "      rootfs_uri: blah\n" +
                        "      image_resource:\n" +
                        "        type: docker-image\n" +
                        "      inputs:\n" +
                        "      - name: commons-git\n" +
                        "      platform: linux\n" +
                        "      run:\n" +
                        "        path: which\n" +
                        "        args:\n" +
                        "        - mvn"
        );
        List<Diagnostic> problems = editor.assertProblems(
                "rootfs_uri|Deprecated",
                "image_resource|Deprecated"
        );
        for (Diagnostic d : problems) {
            assertEquals(DiagnosticSeverity.Warning, d.getSeverity());
        }
    }

    @Test
    void relaxedIndentContextMoreSpaces() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "  <*>"
        );
        editor.assertCompletionLabels(
                //For the 'exact' context:
                "check_every",
                "icon",
                "old_name",
                "tags",
                "webhook_token",
                //"name", exists
                //"source", exists
                //"type", exists
                //For the nested context:
                "→ uri",
                // For the top-level context:
                "← display",
                "← groups",
                "← jobs",
                "← resource_types",
                "← var_sources",
                "← - Resource Snippet",
                // For the 'next job' context
                "← - name"
        );

        editor.assertCompletionWithLabel("check_every",
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "  check_every: <*>"
        );

        editor.assertCompletionWithLabel("→ uri",
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: <*>"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: blah\n" +
                        "  <*>"
        );
        editor.assertCompletionWithLabel("→ commit_verification_key_ids",
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: blah\n" +
                        "    commit_verification_key_ids:\n" +
                        "    - <*>"
        );
    }

    @Test
    void relaxedIndentContextMoreSpaces2() throws Exception {
        assertContextualCompletions(INDENTED_COMPLETION,
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "  <*>"
        , // =========
                "ur"
        , //=>
                "  uri: <*>"
        );

        assertContextualCompletions(INDENTED_COMPLETION,
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: blah\n" +
                        "  <*>"
        , // =========
                "bran"
        , //=>
                "  branch: <*>"
        );

        assertContextualCompletions(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: blah\n" +
                        "  <*>"
        , // =========
                "comverids<*>"
        , //=>
                "  commit_verification_key_ids:\n" +
                        "    - <*>"
        );
    }

    @Test
    void jobPropertyHovers() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "  - name: job\n" +
                        "    old_name: formerly-known-as\n" +
                        "    serial: true\n" +
                        "    build_logs_to_retain: 10\n" +
                        "    build_log_retention:\n" +
                        "      days: 1\n" +
                        "      builds: 2\n" +
                        "      minimum_succeeded_builds: 1\n" +
                        "    serial_groups: []\n" +
                        "    max_in_flight: 3\n" +
                        "    public: false\n" +
                        "    disable_manual_trigger: true\n" +
                        "    interruptible: true\n" +
                        "    plan:\n" +
                        "      - get: code\n" +
                        "    on_failure:\n" +
                        "      put: code\n" +
                        "    on_error:\n" +
                        "      put: code\n" +
                        "    on_success:\n" +
                        "      put: code\n" +
                        "    ensure:\n" +
                        "      put: code\n" +
                        "    on_abort:\n" +
                        "      put: code\n" +
                        "resources:\n" +
                        "- name: code\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: blah\n" +
                        "    branch: master\n"
        );
        editor.assertHoverContains("name", "The name of the job");
        editor.assertHoverContains("old_name", "history of old job will be inherited to the new one");
        editor.assertHoverContains("serial", "execute one-by-one");
        editor.assertHoverContains("build_logs_to_retain", "Deprecated");
        editor.assertHoverContains("build_log_retention", "Configures the retention policy for build logs");
        editor.assertHoverContains("days", "Keep logs for builds which have finished within the specified number of days");
        editor.assertHoverContains("builds", "Keep logs for the last specified number of builds");
        editor.assertHoverContains("minimum_succeeded_builds", "Keep logs for at least N successful builds");
        editor.assertHoverContains("serial_groups", "referencing the same tags will be serialized");
        editor.assertHoverContains("max_in_flight", "maximum number of builds to run at a time");
        editor.assertHoverContains("public", "build log of this job will be viewable");
        editor.assertHoverContains("disable_manual_trigger", "manual triggering of the job");
        editor.assertHoverContains("interruptible", "worker will not wait on the builds");
        editor.assertHoverContains("on_success", "Step to execute when the job succeeds");
        editor.assertHoverContains("on_failure", "Step to execute when the job fails");
        editor.assertHoverContains("on_error", "Step to execute when the job errors");
        editor.assertHoverContains("on_abort", "Step to execute when the job aborts");
        editor.assertHoverContains("ensure", "Step to execute regardless");
    }

    @Test
    void jobPropertyReconcile() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "  - name: job\n" +
                        "    old_name: formerly-known-as\n" +
                        "    serial: isSerial\n" +
                        "    build_logs_to_retain: retainers\n" +
                        "    build_log_retention:\n" +
                        "      days: retain-days\n" +
                        "      builds: retain-builds\n" +
                        "      minimum_succeeded_builds: succ-builds\n" +
                        "    serial_groups: no-list\n" +
                        "    max_in_flight: flying-number\n" +
                        "    public: publicize\n" +
                        "    disable_manual_trigger: nomanual\n" +
                        "    interruptible: nointerrupt\n" +
                        "    plan:\n" +
                        "      - get: a-resource\n" +
                        "    on_failure:\n" +
                        "      put: b-resource\n" +
                        "    on_error:\n" +
                        "      put: c-resource\n" +
                        "    on_success:\n" +
                        "      put: d-resource\n" +
                        "    ensure:\n" +
                        "      put: e-resource\n" +
                        "resources:\n" +
                        "- name: b-resource\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: blah\n" +
                        "- name: code\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: blah\n" +
                        "    branch: master\n"
        );
        editor.assertProblems(
                "isSerial|boolean",
                "build_logs_to_retain|Deprecated",
                "retainers|Number",
                "retain-days|Number",
                "retain-builds|Number",
                "succ-builds|Number",
                "no-list|Expecting a 'Sequence'",
                "flying-number|Number",
                "publicize|boolean",
                "nomanual|boolean",
                "nointerrupt|boolean",
                "a-resource|resource does not exist",
                "b-resource|should define 'branch'",
                "c-resource|resource does not exist",
                "d-resource|resource does not exist",
                "e-resource|resource does not exist",
                "code|Unused"
        );
    }

    @Test
    void relaxedIndentContextMoreSpaces3() throws Exception {
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: job-hello-world\n" +
                        "  public: true\n" +
                        "  plan:\n" +
                        "  - get: resource-tutorial\n" +
                        "  - task: hello-world\n" +
                        "  <*>"
        );

        editor.assertCompletionLabels(
                //completions for current (i.e Job) context:
                "build_log_retention",
                "disable_manual_trigger",
                "ensure",
                "interruptible",
                "max_in_flight",
                "old_name",
                "on_abort",
                "on_error",
                "on_failure",
                "on_success",
                "serial",
                "serial_groups",
                "build_logs_to_retain",
                //"name", exists
                //"plan", exists
                //"public", exists
                //Completions for nested context (i.e. task step)
                "→ across",
                "→ attempts",
                "→ config",
                "→ ensure",
                "→ file",
                "→ image",
                "→ input_mapping",
                "→ on_abort",
                "→ on_error",
                "→ on_failure",
                "→ on_success",
                "→ output_mapping",
                "→ params",
                "→ privileged",
                "→ tags",
                "→ timeout",
                "→ vars",
                //Completions with '-'
                "- do",
                "- get",
                "- in_parallel",
                "- load_var\n" +
                        "- put",
                "- set_pipeline",
                "- task",
                "- try",
                "- aggregate",
                //Dedented completions
                "← display",
                "← groups",
                "← resource_types",
                "← resources",
                "← var_sources",
                "← - Job Snippet",
                "← - name"
        );
    }

    @Test
    void gotoSymbolInPipeline() throws Exception {
        harness.enableHierarchicalDocumentSymbols(false);
        Editor editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: some-resource-type\n" +
                        "resources:\n" +
                        "- name: foo-resource\n" +
                        "- name: bar-resource\n" +
                        "jobs:\n" +
                        "- name: do-some-stuff\n" +
                        "- name: do-more-stuff\n" +
                        "groups:\n" +
                        "- name: group-one\n" +
                        "- name: group-two\n"
        );

        editor.assertDocumentSymbols(
                "some-resource-type",
                "foo-resource",
                "bar-resource",
                "do-some-stuff",
                "do-more-stuff",
                "group-one",
                "group-two"
        );
    }

    @Test
    void hiearhicalDocumentSymbolsInPipeline() throws Exception {
        harness.enableHierarchicalDocumentSymbols(true);
        Editor editor = harness.newEditor(
                "resource_types:\n" +
                        "- name: some-resource-type\n" +
                        "resources:\n" +
                        "- name: foo-resource\n" +
                        "- name: bar-resource\n" +
                        "jobs:\n" +
                        "- name: do-some-stuff\n" +
                        "- name: do-more-stuff\n" +
                        "groups:\n" +
                        "- name: group-one\n" +
                        "- name: group-two\n"
        );

        editor.assertHierarchicalDocumentSymbols(
                "resource_types::Resource Types\n" +
                        "  some-resource-type::Resource Type\n" +
                        "resources::Resources\n" +
                        "  foo-resource::Resource\n" +
                        "  bar-resource::Resource\n" +
                        "jobs::Jobs\n" +
                        "  do-some-stuff::Job\n" +
                        "  do-more-stuff::Job\n" +
                        "groups::Groups\n" +
                        "  group-one::Groups\n" +
                        "  group-two::Groups\n"
        );
    }
	
	private static StringBuffer getStackDumps() {
		StringBuffer sb = new StringBuffer();
		Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
		for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
			sb.append(entry.getKey().toString());
			sb.append("\n");
			for (StackTraceElement element : entry.getValue()) {
				sb.append("  ");
				sb.append(element.toString());
				sb.append("\n");
			}
			sb.append("\n");
		}
		return sb;
	}


    @Test
    void reconcilerRaceCondition() throws Exception {
        Disposable future = Mono.fromRunnable(() -> {
            log.info(getStackDumps().toString());
        }).delaySubscription(Duration.ofSeconds(60)).subscribe();

        Editor editor = harness.newEditor("garbage");
        log.info("Editor created");
        SynchronizationPoint reconcilerThreadStart = harness.reconcilerThreadStart();
        log.info("Reconcile thread started");
        try {
            String editorContents = editor.getRawText();
            for (int i = 0; i < 4; i++) {
                log.info("Perfroming change: " + i);
                editorContents = "\n" + editorContents;
                editor.setText(editorContents);
                log.info("Text changed: " + i);
            }
        } finally {
            reconcilerThreadStart.unblock();
        }

        editor.assertRawText(
                "\n" +
                        "\n" +
                        "\n" +
                        "\n" +
                        "garbage"
        );
        editor.assertProblems("garbage|Expecting a 'Map'");

        future.dispose();
    }

    @Test
    void noAutoInsertRequiredSourcePropertiesIfPresent() throws Exception {
        Editor editor;

        //Most common case
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: source-repo\n" +
                        "  type: <*>\n" +
                        "  source:"
        );
        editor.assertCompletionWithLabel((l) -> l.startsWith("pool"),
                "resources:\n" +
                        "- name: source-repo\n" +
                        "  type: pool<*>\n" +
                        "  source:"
        );

    }

    @Test
    void autoInsertRequiredSourceProperties() throws Exception {
        Editor editor;

        //Most common case
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: source-repo\n" +
                        "  type: <*>"
        );
        editor.assertCompletionWithLabel((l) -> l.startsWith("pool"),
                "resources:\n" +
                        "- name: source-repo\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    uri: $1\n" +
                        "    branch: $2\n" +
                        "    pool: $3<*>"
        );

        // What if we use somewhat different indentation style?
        editor = harness.newEditor(
                "resources:\n" +
                        "  - name: source-repo\n" +
                        "    type: <*>"
        );
        editor.assertCompletionWithLabel((l) -> l.startsWith("pool"),
                "resources:\n" +
                        "  - name: source-repo\n" +
                        "    type: pool\n" +
                        "    source:\n" +
                        "      uri: $1\n" +
                        "      branch: $2\n" +
                        "      pool: $3<*>"
        );
    }

    @Disabled
    @Test
    void autoInsertRequiredSourceProperties3() throws Exception {
        //This case can not be implemented correctly because of the magic indentations that vscode
        // automatically applies. The magic indents will allways indent the extra lines we insert after
        // the value to be indented to the level of that value. So it is impossible to create an edit
        // where the text on the lines following it is indented *less* than that value, which is what
        // is required to implement this case correctly.

        //What if the type was on a new line (this is odd, but anyhow)
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: source-repo\n" +
                        "  type: \n" +
                        "    <*>"
        );
        editor.assertCompletionWithLabel((l) -> l.startsWith("pool"),
                "resources:\n" +
                        "- name: source-repo\n" +
                        "  type: \n" +
                        "    pool\n" +
                        "  source:\n" +
                        "    uri: $1\n" +
                        "    branch: $2\n" +
                        "    pool: $3<*>"
        );
    }


    @Test
    void reconcilerJobFromPassedAttributeMustInteractWithResource() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "- name: source-repo\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    # - put: version\n" +
                        "    - get: source-repo\n" +
                        "- name: test-it\n" +
                        "  plan:\n" +
                        "  - get: source-repo\n" +
                        "    passed:\n" +
                        "    - build-it # <- good\n" +
                        "  - get: version\n" +
                        "    passed:\n" +
                        "    - build-it # <- bad\n"
        );
        editor.assertProblems(
                "build-it^ # <- bad|Job 'build-it' does not interact with resource 'version'"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "- name: source-repo\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    - put: version\n" +
                        "    # - get: source-repo\n" +
                        "- name: test-it\n" +
                        "  plan:\n" +
                        "  - get: source-repo\n" +
                        "    passed:\n" +
                        "    - build-it # <- bad\n" +
                        "  - get: version\n" +
                        "    passed:\n" +
                        "    - build-it # <- good\n"
        );
        editor.assertProblems(
                "build-it^ # <- bad|Job 'build-it' does not interact with resource 'source-repo'"
        );

        //Check that we find interactions in steps that are at the top-level of the plan:
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "- name: source-repo\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    - put: version\n" +
                        "    - get: source-repo\n" +
                        "- name: test-it\n" +
                        "  plan:\n" +
                        "  - get: source-repo\n" +
                        "    passed:\n" +
                        "    - build-it\n" +
                        "  - get: version\n" +
                        "    passed:\n" +
                        "    - build-it\n"
        );
        editor.assertProblems(/*NONE*/);

        //Check that we find interactions in steps that are nested in other steps
        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "- name: source-repo\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - put: version\n" +
                        "  - get: source-repo\n" +
                        "- name: test-it\n" +
                        "  plan:\n" +
                        "  - get: source-repo\n" +
                        "    passed:\n" +
                        "    - build-it\n" +
                        "  - get: version\n" +
                        "    passed:\n" +
                        "    - build-it\n"
        );
        editor.assertProblems(/*NONE*/);

    }

    @Test
    void reconcilerSkipInteractsWithChecckedForNonExistantResource() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/144217965
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "- name: source-repo\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - aggregate:\n" +
                        "    - put: version\n" +
                        "    - get: source-repo\n" +
                        "- name: test-it\n" +
                        "  plan:\n" +
                        "  - get: source-repo\n" +
                        "    passed:\n" +
                        "    - build-it\n" +
                        "  - get: versi\n" +
                        "    passed:\n" +
                        "    - build-it"
        );

        editor.assertProblems(
                "aggregate|Deprecated",
                "get: ^versi^|resource does not exist"
        );
    }

    @Test
    void relaxedContentAssistContextForListItem_sameLine() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  plan: <*>\n"
        );
        editor.assertCompletionWithLabel("- put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  plan: \n" +
                        "  - put: <*>\n"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  plan: pu<*>\n"
        );
        editor.assertCompletionWithLabel("- put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  plan: \n" +
                        "  - put: <*>\n"
        );
    }

    @Test
    void relaxedContentAssistContextForListItem_indented() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "    - get: docker-git\n" +
                        "      trigger: true\n" +
                        "    <*>"
        );
        editor.assertCompletionWithLabel("- put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "    - get: docker-git\n" +
                        "      trigger: true\n" +
                        "    - put: <*>"
        );
    }

    @Test
    void relaxedContentAssistContextForListItem_not_indented() throws Exception {
        Editor editor;

        //Note: this is a tricky case because when <*> lines up with 'plan' it will not be considered
        // to be a child of 'plan' but a child of the 'job' instead.
        // However, for hypothetical '- ' completions we'd have to treat as a child of plan instead!

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    trigger: true\n" +
                        "  <*>"
        );
        editor.assertCompletionWithLabel("- put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    trigger: true\n" +
                        "  - put: <*>"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  <*>"
        );
        editor.assertCompletionWithLabel("- put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - put: <*>"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  pu<*>"
        );
        editor.assertCompletionWithLabel("- put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - put: <*>"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  ag"
        );
        editor.assertCompletionWithLabel("- aggregate",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - aggregate:\n" +
                        "    - <*>"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  in_"
        );
        editor.assertCompletionWithLabel("- in_parallel",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "      <*>"
        );
    }

    @Test
    void relaxedContentAssist_primary_properties() throws Exception {
        //See https://www.pivotaltracker.com/story/show/144584163
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: docker-git\n" +
                        "<*>"
        );
        editor.assertCompletionLabels(
                "display",
                "groups",
                "jobs",
                "resource_types",
                "var_sources",
                "→ type",
                "- Resource Snippet",
                "- name"
        );
    }

    @Test
    void relaxedContentAssistLessSpaces() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    trigger: true\n" +
                        "    <*>"
        );
        editor.assertCompletionWithLabel("← - put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    trigger: true\n" +
                        "  - put: <*>"
        );

        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    trigger: true\n" +
                        "    pu<*>"
        );
        editor.assertCompletionWithLabel("← - put",
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    trigger: true\n" +
                        "  - put: <*>"
        );

        // De-indentation relaxation should not be allowed if they cause the context node to be split.
        // So in this example de-indented completions shouldn't be suggested.
        editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-docker-image\n" +
                        "  serial: true\n" +
                        "  plan:\n" +
                        "  - get: docker-git\n" +
                        "    <*>\n" +
                        "    trigger: true\n"
        );
        editor.assertNoCompletionsWithLabel(label -> label.startsWith(Unicodes.LEFT_ARROW + " "));
        ;
    }

    @Test
    void reconcileUnusedResources() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "- name: source-repo\n" +
                        "  type: git\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - get: version\n"
        );
        Diagnostic p = editor.assertProblems("source-repo|Unused 'Resource'").get(0);
        assertEquals(DiagnosticSeverity.Error, p.getSeverity());

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: not-used\n" +
                        "  type: pool\n" +
                        "- name: version\n" +
                        "  type: semver\n" +
                        "- name: source-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    branch: master\n" +
                        "    uri: git@someplace.com:blah/blah.git\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - in_parallel:\n" +
                        "    - get: not-used\n" +  // <-- This isn't a real use but looks like one!
                        "      resource: version\n" +
                        "    - put: source-repo\n"
        );
        editor.assertProblems(
                "not-used|Unused 'Resource'"
        );
    }

    @Test
    void gitResourceFetchParameter() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "  - name: cf-networking-dev\n" +
                        "    type: git\n" +
                        "    source:\n" +
                        "      uri: git@someplace.com:cloudfoundry-incubator/cf-networking-release.git\n" +
                        "      branch: develop\n" +
                        "      ignore_paths:\n" +
                        "        - docs\n" +
                        "      private_key: {{cf-networking-deploy-key}}\n" +
                        "jobs:\n" +
                        "- name: foo\n" +
                        "  plan:\n" +
                        "  - get: cf-networking-dev\n" +
                        "    params:\n" +
                        "      fetch: [master]\n" +
                        "      submodules: none\n"
        );
        Diagnostic p = editor.assertProblems("fetch|Deprecated").get(0);
        assertEquals(DiagnosticSeverity.Warning, p.getSeverity());
    }

    @Test
    void bug_150337510() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/150337510
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: test\n" +
                        "  type: s3\n" +
                        "  source:\n" +
                        "    bucket: blah\n" +
                        "    regexp: blah/blah*.tar.gz\n" +
                        "jobs:\n" +
                        "- name: build-it\n" +
                        "  plan:\n" +
                        "  - task: build-it\n" +
                        "    file: tasks/build-it.yml\n" +
                        "  on_success:\n" +
                        "    put: test\n" +
                        "- name: create-website\n" +
                        "  plan:\n" +
                        "  - get: test\n" +
                        "    passed:\n" +
                        "    - build-it"
        );
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void cfResourceTypeCompletion() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: <*>"
        );
        CompletionItem item = editor.assertCompletionWithLabel(label -> label.startsWith("cf"),
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: $1\n" +
                        "    organization: $2\n" +
                        "    space: $3<*>"
        );
        assertEquals(InsertTextFormat.Snippet, item.getInsertTextFormat());
    }

    @Test
    void cfResourceSourceCompletions() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>",
                //Snippet:
                "api: $1\n" +
                        "    organization: $2\n" +
                        "    space: $3<*>"
        , // non-snippet:
                "api: <*>",
                "organization: <*>",
                "space: <*>"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: {{cf_api}}\n" +
                        "    organization: {{cf_org}}\n" +
                        "    space: {{cf_space}}\n" +
                        "    <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>",
                "client_id: <*>",
                "client_secret: <*>",
                "password: <*>",
                "skip_cert_check: <*>",
                "username: <*>",
                "verbose: <*>"
        );
    }

    @Test
    void cfResourceSourceValidations() throws Exception {
        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n"
        );
        editor.assertProblems(
                "pws|Unused",
                "source|One of [username, password, client_id, client_secret] is required"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n" +
                        "    username: myself"
        );
        editor.assertProblems(
                "pws|Unused",
                "username|assumes that 'password' is also defined"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n" +
                        "    password: ((secret))"
        );
        editor.assertProblems(
                "pws|Unused",
                "password|assumes that 'username' is also defined"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n" +
                        "    client_id: ((secret))"
        );
        editor.assertProblems(
                "pws|Unused",
                "client_id|assumes that 'client_secret' is also defined"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n" +
                        "    client_secret: ((secret))"
        );
        editor.assertProblems(
                "pws|Unused",
                "client_secret|assumes that 'client_id' is also defined"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n" +
                        "    client_id: ((secret))\n" +
                        "    client_secret: ((secret))"
        );
        editor.assertProblems(
                "pws|Unused"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n" +
                        "    username: ((secret))\n" +
                        "    password: ((secret))\n" +
                        "    skip_cert_check: not-bool-1\n" +
                        "    verbose: not-bool-2"
        );
        editor.assertProblems(
                "pws|Unused",
                "not-bool-1|boolean",
                "not-bool-2|boolean"
        );

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: https://api.run.pivotal.io\n" +
                        "    organization: my-org\n" +
                        "    space: my-space\n" +
                        "    username: ((secret))\n" +
                        "    password: ((secret))\n" +
                        "    client_id: ((id)\n" +
                        "    client_secret: ((secret)\n"
        );
        editor.assertProblems(
                "pws|Unused",
                "username|Properties [username, password] should not be used together with [client_id, client_secret]",
                "password|Properties [username, password] should not be used together with [client_id, client_secret]",
                "client_id|Properties [username, password] should not be used together with [client_id, client_secret]",
                "client_secret|Properties [username, password] should not be used together with [client_id, client_secret]"
        );
    }

    @Test
    void cfResourceSourceHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "  source:\n" +
                        "    api: {{cf_api}}\n" +
                        "    username: {{cf_user}}\n" +
                        "    password: {{cf_password}}\n" +
                        "    client_id: ((cf_client_id))\n" +
                        "    client_secret: ((cf_client_secret))\n" +
                        "    organization: {{cf_org}}\n" +
                        "    space: {{cf_space}}\n" +
                        "    skip_cert_check: true<*>\n" +
                        "    verbose: true"
        );
        editor.assertHoverContains("api", "address of the Cloud Controller");
        editor.assertHoverContains("username", "username used to authenticate");
        editor.assertHoverContains("password", "password used to authenticate");
        editor.assertHoverContains("client_id", "client id used to authenticate");
        editor.assertHoverContains("client_secret", "client secret used to authenticate");
        editor.assertHoverContains("organization", "organization to push");
        editor.assertHoverContains("space", "space to push");
        editor.assertHoverContains("skip_cert_check", "Check the validity of the CF SSL cert");
        editor.assertHoverContains("verbose", "Invoke `cf` cli using `CF_TRACE=true`");
    }

    @Test
    void cfPutParamsReconcileAndHovers() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: pws\n" +
                        "  type: cf\n" +
                        "jobs:\n" +
                        "- name: deploy-stuff\n" +
                        "  plan:\n" +
                        "  - put: pws\n" +
                        "    params:\n" +
                        "      manifest: repo/manifest.yml\n" +
                        "      path: out/*.jar\n" +
                        "      current_app_name: the-name\n" +
                        "      environment_variables:\n" +
                        "        key: value\n" +
                        "        key2: value2\n" +
                        "      vars: {}\n" +
                        "      vars_files: []\n" +
                        "      docker_username: mike\n" +
                        "      docker_password: ((secret))\n" +
                        "      show_app_log: true\n" +
                        "      no_start: false\n"
        );

        editor.assertProblems(
                "current_app_name|Only one of 'no_start' and 'current_app_name' should be defined",
                "no_start|Only one of 'no_start' and 'current_app_name' should be defined"
        );

        editor.assertHoverContains("manifest", "Path to a application manifest file");
        editor.assertHoverContains("path", "Path to the application to push");
        editor.assertHoverContains("current_app_name", "zero-downtime deploy");
        editor.assertHoverContains("environment_variables", "Environment variables");
        editor.assertHoverContains("vars", "variables to pass");
        editor.assertHoverContains("vars_files", "variables files to pass");
        editor.assertHoverContains("docker_username", "username to authenticate");
        editor.assertHoverContains("docker_password", "password when authenticating");
        editor.assertHoverContains("show_app_log", "Tails the app log");
        editor.assertHoverContains("no_start", "does not start it");
    }

    @Test
    void bug_152918825_no_reconciling_for_double_parens_placeholders() throws Exception {
        //https://www.pivotaltracker.com/story/show/152918825
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: image-XXX\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: ((DOCKER_IMAGE))\n" +
                        "    insecure_registries: ((DOCKER_INSECURE_REGISTRIES))\n" +
                        "    tag: latest"
        );
        editor.assertProblems(
                "image-XXX|Unused 'Resource'"
        );
    }

    @Test
    void getStepVersionShouldAcceptLatestAndEvery() throws Exception {
        //See https://github.com/spring-projects/sts4/pull/24
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: cf-deployment-git\n" +
                        "    version: latest\n" +
                        "  - get: some-resource\n" +
                        "    version: every\n" +
                        "  - get: other-resource\n" +
                        "    version: bogus"
        );
        editor.assertProblems(
                "cf-deployment-git|resource does not exist",
                "some-resource|resource does not exist",
                "other-resource|resource does not exist",
                "bogus|Valid values are: [every, latest]"
        );
    }

    @Test
    void getStepVersionShouldAcceptMap() throws Exception {
        //See https://github.com/spring-projects/sts4/pull/24
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: cf-deployment-git\n" +
                        "    version: { ref: ((cf_deployment_commit_ref)) }"
        );
        editor.assertProblems("cf-deployment-git|resource does not exist");
    }

    @Test
    void getStepVersionCompletionsSuggestLatestAndEvery() throws Exception {
        //See https://github.com/spring-projects/sts4/pull/24
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: cf-deployment-git\n" +
                        "    version: <*>"
        );
        editor.assertCompletionLabels("every", "latest");
    }

    @Test
    void getStepVersionMapStringStringValidation() throws Exception {
        //See https://github.com/spring-projects/sts4/pull/24
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: do-stuff\n" +
                        "  plan:\n" +
                        "  - get: cf-deployment-git\n" +
                        "    version: { ref: good}\n" +
                        "  - get: other-rsrc\n" +
                        "    version: { ref: [bad]}\n"
        );
        editor.assertProblems(
                "cf-deployment-git|resource does not exist",
                "other-rsrc|resource does not exist",
                "[bad]|Expecting a 'String' but found a 'Sequence'"
        );
    }

    @Test
    void taskCachesReconcile() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/153861788
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "\n" +
                        "inputs:\n" +
                        "- name: project-src\n" +
                        "\n" +
                        "caches:\n" +
                        "- path: project-src/node_modules\n" +
                        "  junk: bad\n" +
                        "\n" +
                        "run:\n" +
                        "  path: project-src/ci/build"
        );
        editor.assertProblems("junk|Unknown property");
    }

    @Test
    void taskCachesCompletions() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/153861788
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "\n" +
                        "inputs:\n" +
                        "- name: project-src\n" +
                        "\n" +
                        "caches:\n" +
                        "- <*>\n" +
                        "run:\n" +
                        "  path: project-src/ci/build"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>", "path: <*>");
    }

    @Test
    void taskCachesHovers() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "\n" +
                        "inputs:\n" +
                        "- name: project-src\n" +
                        "\n" +
                        "caches:\n" +
                        "- path: project-src/node_modules\n" +
                        "  junk: bad\n" +
                        "\n" +
                        "run:\n" +
                        "  path: project-src/ci/build"
        );
        editor.assertHoverContains("caches", "Caches are scoped to the worker the task is run on");
        editor.assertHoverContains("path", "The path to a directory to be cached");
    }

    @Test
    void image_resource_completions() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "<*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION,
                "imgrs<*>"
        , // ==>
                "image_resource:\n" +
                        "  type: <*>"
        );

        editor.setText(
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "image_resource:\n" +
                        "  type: <*>"
        );
        editor.assertContextualCompletions("dckr<*>",
                "docker-image\n" +
                        "  source:\n" +
                        "    repository: <*>"
        );

        editor.setText(
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>"
        , // =>
                "params:\n" +
                        "    <*>"
        , // ----
                "source:\n" +
                        "    <*>" //Actually... would expect 'repository' to be filled in by snippet here!
        , // ----
                "version:\n" +
                        "    <*>"
        );
    }

    @Test
    void image_resource_subHovers() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  params: {}\n" +
                        "  source:\n" +
                        "    repository: some-docker-image\n" +
                        "  version: latest"
        );
        editor.assertHoverContains("type", "type of the resource. Usually `docker-image`.");
        editor.assertHoverContains(" source", "The location of the resource");
        editor.assertHoverContains("params", "A map of arbitrary configuration to forward to the resource");
        editor.assertHoverContains("version", "A specific version of the resource to fetch");
    }

    @Test
    void image_resource_version_reconcile() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: some-docker-image\n" +
                        "  version: latest"
        );
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: some-docker-image\n" +
                        "  version: every"
        );
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: some-docker-image\n" +
                        "  version:\n" +
                        "    anystring: goes\n"
        );
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "image_resource:\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: some-docker-image\n" +
                        "  version: not-a-valid-version"
        );
        editor.assertProblems("not-a-valid-version|Valid values are: [every, latest]");
    }

    @Test
    void jobGroupsCompletenessReconcile() throws Exception {
        //1) if all jobs are assigned to a group... don't complain:
        Editor editor = harness.newEditor(
                "jobs:\n" +
                        "- name: build-snapshot\n" +
                        "- name: build-release\n" +
                        "- name: test-snapshot\n" +
                        "- name: test-release\n" +
                        "- name: publish-snapshot\n" +
                        "- name: publish-release\n" +
                        "groups:\n" +
                        "- name: snapshot\n" +
                        "  jobs:\n" +
                        "  - build-snapshot\n" +
                        "  - test-snapshot\n" +
                        "  - publish-snapshot\n" +
                        "- name: release\n" +
                        "  jobs:\n" +
                        "  - build-release\n" +
                        "  - test-release\n" +
                        "  - publish-release\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(/*NONE*/);

        //If there are no groups then, don't complain about inconmplete jobs partitioning
        editor.setText(
                "jobs:\n" +
                        "- name: build-snapshot\n" +
                        "- name: build-release\n" +
                        "- name: test-snapshot\n" +
                        "- name: test-release\n" +
                        "- name: publish-snapshot\n" +
                        "- name: publish-release\n"
        );

        //If at least one job is in a group, check that all jobs are in a group
        editor.setText(
                "jobs:\n" +
                        "- name: build-snapshot\n" +
                        "- name: build-release\n" +
                        "- name: test-snapshot\n" +
                        "- name: test-release\n" +
                        "- name: publish-snapshot\n" +
                        "- name: publish-release\n" +
                        "groups:\n" +
                        "- name: snapshot\n" +
                        "  jobs:\n" +
                        "  - build-snapshot\n" +
                        "- name: release\n" +
                        "  jobs:\n" +
                        "  - build-release"
        );
        editor.assertProblems(
                "test-snapshot|'test-snapshot' belongs to no group",
                "test-release|'test-release' belongs to no group",
                "publish-snapshot|'publish-snapshot' belongs to no group",
                "publish-release|'publish-release' belongs to no group"
        );
    }

    @Test
    void githubCompletionsUriTypes() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "git@github.com:<*>",
                "https://github.com/<*>"
        );

        editor.assertContextualCompletions("@<*>",
                "git@github.com:<*>"
        );
    }

    @Test
    void githubCompletionsOwners() throws Exception {
        when(github.getOwners()).thenReturn(ImmutableList.of(
                "kdvolder", "spring-projects", "spring-guides"
        ));
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: <*>"
        );

        editor.assertContextualCompletions("git@github.com:<*>",
                "git@github.com:kdvolder/<*>",
                "git@github.com:spring-guides/<*>",
                "git@github.com:spring-projects/<*>"
        );

        editor.assertContextualCompletions("git@github.com:vol<*>",
                "git@github.com:kdvolder/<*>"
        );
        editor.assertContextualCompletions("https://github.com/<*>",
                "https://github.com/kdvolder/<*>",
                "https://github.com/spring-guides/<*>",
                "https://github.com/spring-projects/<*>"
        );

    }

    @Test
    void githubCompletionsRepos() throws Exception {
        when(github.getReposForOwner("the-owner")).thenReturn(ImmutableList.of(
                "nice-repo", "cool-project", "good-stuff"
        ));
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: <*>"
        );
        editor.assertContextualCompletions("https://github.com/the-owner/<*>",
                "https://github.com/the-owner/cool-project.git<*>",
                "https://github.com/the-owner/good-stuff.git<*>",
                "https://github.com/the-owner/nice-repo.git<*>"
        );

        editor.assertContextualCompletions("https://github.com/the-owner/proj<*>",
                "https://github.com/the-owner/cool-project.git<*>"
        );

        editor.assertContextualCompletions("git@github.com:the-owner/<*>",
                "git@github.com:the-owner/cool-project.git<*>",
                "git@github.com:the-owner/good-stuff.git<*>",
                "git@github.com:the-owner/nice-repo.git<*>"
        );

        editor.assertContextualCompletions("git@github.com:the-owner/proj<*>",
                "git@github.com:the-owner/cool-project.git<*>"
        );
    }

    @Test
    void githubCompletionErrors() throws Exception {
        when(github.getReposForOwner("the-owner")).thenThrow(new IOException("Explain some stuff"));
        when(github.getOwners()).thenThrow(new IOException("Explain some stuff"));
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:the-owner/<*>"
        );
        editor.assertCompletionLabels("Explain some stuff");

        editor.setText(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:<*>"
        );
        editor.assertCompletionLabels("Explain some stuff");
    }


    @Test
    void noNetworkGithubUriReconciling() throws Exception {
        //When not plugged into the network (i.e github api returns errors)
        // we consider the repos unknowable and will not warn about anything.
        when(github.getReposForOwner("the-owner")).thenThrow(new Exception("Some problem talking to github"));
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:the-owner/bad-project.git\n" +
                        "- name: other-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com/the-owner/wrong-project.git\n"
        );
        editor.assertProblems(
                "my-repo|Unused",
                "other-repo|Unused"
        );
    }

    @Test
    void nonGithubUriReconciling() throws Exception {
        //We only care about gihub uris. So, ignore anything else for reconciler.
        when(github.getReposForOwner("the-owner")).thenReturn(ImmutableList.of(
                "nice-repo", "cool-project", "good-stuff"
        ));
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@somehost.somewhere:the-owner/bad-project\n" +
                        "- name: other-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://somehost.somewhere/the-owner/wrong-project\n"
        );
        editor.assertProblems(
                "my-repo|Unused",
                "other-repo|Unused"
        );
    }

    @Test
    void githubWrongFormatReconciling() throws Exception {
        when(github.getReposForOwner("the-owner")).thenReturn(ImmutableList.of(
                "nice-repo", "cool-project", "good-stuff"
        ));
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:ssh-just-owner\n" +
                        "- name: my-repo2\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com:ssh-just-owner.git\n" +
                        "- name: other-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com/https-just-owner\n" +
                        "- name: other-repo2\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com/https-just-owner.git\n" +
                        "- name: different-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.com/ssh/wrong-separator.git\n" +
                        "- name: one-more-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.com:https/wrong-separator.git\n"
        );
        editor.ignoreProblem(PipelineYmlSchemaProblems.UNUSED_RESOURCE);
        editor.assertProblems(
                "uri: git@github.com:ssh-just-owne^r^\n|should end with '.git'",
                "ssh-just-owner|Expecting something of the form '${owner}/${repo}'",
                "uri: https://github.com/https-just-owne^r^\n|should end with '.git'",
                "https-just-owner|Expecting something of the form '${owner}/${repo}'",
                "git@github.com^/^ssh/wrong-separator|Expecting a ':'",
                "https://github.com^:^https/wrong-separator|Expecting a '/'"
        );
    }

    @Test
    void githubUriReconciling_bug_194() throws Exception {
        //See: https://github.com/spring-projects/sts4/issues/194

        Editor editor;

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: git@github.computer.com:me/repo.git\n"
        );
        editor.assertProblems("my-repo|Unused");

        editor = harness.newEditor(
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: https://github.computer.com/me/repo.git\n"
        );
        editor.assertProblems("my-repo|Unused");

    }

    @Test
    void githubUriReconciling() throws Exception {
        when(github.getReposForOwner("the-owner")).thenReturn(ImmutableList.of(
                "nice-repo", "cool-project", "good-stuff"
        ));
        when(github.getReposForOwner("owner-no-exist")).thenReturn(null);

        String editorText =
                "resources:\n" +
                        "- name: my-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: $$github$$the-owner/cool-project.git\n" +
                        "- name: other-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: $$github$$the-owner/repo-no-exist.git\n" +
                        "- name: different-repo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    uri: $$github$$owner-no-exist/who-cares.git\n"
        ;

        for (String githubUriPrefix : GithubRepoContentAssistant.URI_PREFIXES) {
            Editor editor = harness.newEditor(editorText.replace("$$github$$", githubUriPrefix));
            editor.ignoreProblem(PipelineYmlSchemaProblems.UNUSED_RESOURCE);
            List<Diagnostic> problems = editor.assertProblems(
                    "repo-no-exist|Repo not found: 'repo-no-exist'",
                    "owner-no-exist|User or Organization not found: 'owner-no-exist'"
            );
            for (Diagnostic d : problems) {
                assertEquals(DiagnosticSeverity.Warning, d.getSeverity());
            }
        }
    }

    @Test
    void emptyInputPathWarning() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "inputs:\n" +
                        "- name: foo\n" +
                        "  path: \"\"\n" +
                        "outputs:\n" +
                        "- name: bar\n" +
                        "  path: \"\"\n"
        );
        List<Diagnostic> problems = editor.assertProblems(
                "\"\"|Empty optional String attribute is useless and can be omitted",
                "\"\"|Empty optional String attribute is useless and can be omitted"
        );
        for (Diagnostic problem : problems) {
            assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
        }
    }

    @Test
    void taskInputOptionalAttribute() throws Exception {
        Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK,
                "platform: linux\n" +
                        "run:\n" +
                        "  path: blah\n" +
                        "inputs:\n" +
                        "- name: foo\n" +
                        "  optional: non-bool\n"
        );
        editor.assertProblems("non-bool|boolean");
        editor.assertHoverContains("optional", "If `true`, then the input is not required by the task");
    }

    @Test
    void anchorNodeSuppressesUnknownPropertyError() throws Exception {
        Editor editor = harness.newEditor(
                "pool-template: &pool-template\n" +
                        "  uri: ((pool-git-backing-store-uri))\n" +
                        "  branch: master\n" +
                        "  pool: OVERRIDEME\n" +
                        "  private_key: ((pool-git-backing-store-private-key))\n"
        );
        editor.assertProblems(/*none*/);
    }

    @Test
    void referencedAnchorNodesReconciled() throws Exception {
        Editor editor = harness.newEditor(
                "repo-dflts: &repo-dflts\n" +
                        "  bogus: bar\n" +
                        "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source: *repo-dflts\n"
        );
        editor.assertProblems(
                "bogus|Unknown",
                "foo|Unused"
        );
    }

    @Test
    void mergedAnchorNodesReconciled() throws Exception {
        Editor editor = harness.newEditor(
                "repo-dflts: &repo-dflts\n" +
                        "  bogus: bar\n" +
                        "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    <<: *repo-dflts\n"
        );
        editor.assertProblems(
                "bogus|Unknown",
                "foo|Unused"
        );
    }

    @Test
    void referenceToMissingAnchor() throws Exception {
        Editor editor = harness.newEditor(
                "repo-dflts: &repo-dflts\n" +
                        "  bogus: bar\n" +
                        "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    <<: *TYPO\n"
        );
        editor.assertProblems(
                "*|undefined alias TYPO"
        );
    }

    @Test
    void reconcileMalformedMergeNode() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    <<: scalar\n"
        );
        editor.assertProblems(
                "foo|Unused",
                "source|'uri' is required",
                "scalar|Expected a mapping or list of mappings"
        );
    }

    @Test
    void reconcileMalformedMergeNodeList() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "- name: foo\n" +
                        "  type: git\n" +
                        "  source:\n" +
                        "    <<:\n" +
                        "    - scalar\n"
        );
        editor.assertProblems(
                "foo|Unused",
                "source|'uri' is required",
                "scalar|Expected a mapping for merging"
        );
    }


    @Test
    void anchorsAndReferenceSample_1() throws Exception {
        Editor editor = harness.newEditor(
                "pool-template: &pool-template\n" +
                        "  uri: ((pool-git-backing-store-uri))\n" +
                        "  branch: master\n" +
                        "  pool: OVERRIDEME\n" +
                        "  private_key: ((pool-git-backing-store-private-key))\n" +
                        "\n" +
                        "sleep: &sleep\n" +
                        "  config:\n" +
                        "    platform: linux\n" +
                        "    image_resource:\n" +
                        "      type: docker-image\n" +
                        "      source:\n" +
                        "        repository: alpine\n" +
                        "        tag: latest\n" +
                        "    run:\n" +
                        "      path: sh\n" +
                        "      args:\n" +
                        "      - -exc\n" +
                        "      - sleep 60\n" +
                        "\n" +
                        "##########\n" +
                        "\n" +
                        "resource_types:\n" +
                        "\n" +
                        "- name: pool\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: ((pool-resource-docker-repo))\n" +
                        "    tag: ((pool-resource-tag))\n" +
                        "\n" +
                        "##########\n" +
                        "\n" +
                        "resources:\n" +
                        "\n" +
                        "- name: acquire-pool\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    <<: *pool-template\n" +
                        "    pool: acquire-pool\n" +
                        "\n" +
                        "- name: claim-pool\n" +
                        "  type: pool\n" +
                        "  source:\n" +
                        "    <<: *pool-template\n" +
                        "    pool: claim-pool\n" +
                        "\n" +
                        "##########\n" +
                        "\n" +
                        "jobs:\n" +
                        "\n" +
                        "- name: acquire-1\n" +
                        "  plan:\n" +
                        "    - put: acquire-pool\n" +
                        "      params: {acquire: true}\n" +
                        "    - task: sleep\n" +
                        "      <<: *sleep\n" +
                        "  ensure:\n" +
                        "    put: acquire-pool\n" +
                        "    params: {release: acquire-pool}\n" +
                        "\n" +
                        "- name: claim-1\n" +
                        "  plan:\n" +
                        "    - put: claim-pool\n" +
                        "      params: {claim: slot-1}\n" +
                        "    - task: sleep\n" +
                        "      <<: *sleep\n" +
                        "  ensure:\n" +
                        "    put: claim-pool\n" +
                        "    params: {release: claim-pool}\n"
        );

        log.info("============================");
        log.info(editor.getRawText());
        log.info("============================");
        editor.assertProblems(/*none*/);
    }

    @Test
    void anchorsAndReferenceSample_1_simple() throws Exception {
        Editor editor = harness.newEditor(
                "sleep: &sleep\n" +
                        "  config:\n" +
                        "    platform: linux\n" +
                        "    image_resource:\n" +
                        "      type: docker-image\n" +
                        "      source:\n" +
                        "        repository: alpine\n" +
                        "        tag: latest\n" +
                        "    run:\n" +
                        "      path: sh\n" +
                        "      args:\n" +
                        "      - -exc\n" +
                        "      - sleep 60\n" +
                        "\n" +
                        "##########\n" +
                        "\n" +
                        "resource_types:\n" +
                        "- name: pool\n" +
                        "  type: docker-image\n" +
                        "\n" +
                        "jobs:\n" +
                        "- name: acquire-1\n" +
                        "  plan:\n" +
                        "    - task: sleep\n" +
                        "      <<: *sleep\n"
        );

        log.info("============================");
        log.info(editor.getRawText());
        log.info("============================");
        editor.assertProblems(/*none*/);
    }

    @Test
    void anchorsAndReferenceSample_2() throws Exception {
        Editor editor = harness.newEditor(
                "dcind: &dcind\n" +
                        "  type: docker-image\n" +
                        "  source:\n" +
                        "    repository: kiwiops/stuff-mem-dcind\n" +
                        "    tag: latest\n" +
                        "jobs:\n" +
                        "- name: job-well-done\n" +
                        "  plan:\n" +
                        "  - task: deploy-ssp-devint\n" +
                        "    privileged: true\n" +
                        "    config:\n" +
                        "      platform: linux\n" +
                        "      image_resource: \n" +
                        "        <<: *dcind\n" +
                        "      inputs:\n" +
                        "      - name: kms\n" +
                        "      run:\n" +
                        "        path: ls\n" +
                        "        args:\n" +
                        "        - '-la'"
        );
        editor.assertProblems(/*None*/);
    }

    @Test
    void anchorsAndReferenceSample_3() throws Exception {
        Editor editor = harness.newEditor(
                "resources:\n" +
                        "  - name: hello_hapi\n" +
                        "    type: git\n" +
                        "    source: &repo-source\n" +
                        "      uri: https://somewhere.com/your_github_user/hello_hapi.git\n" +
                        "      branch: master\n" +
                        "  - name: dependency-cache\n" +
                        "    type: npm-cache\n" +
                        "    source:\n" +
                        "      <<: *repo-source\n" +
                        "      paths:\n" +
                        "        - package.json\n"
        );
        editor.assertProblems(
                "hello_hapi|Unused",
                "dependency-cache|Unused",
                "npm-cache|not exist"
        );
    }

    @Test
    void anchorsAndReferenceSample_4() throws Exception {
        Editor editor = harness.newEditor(
                "resource_types:\n" +
                        "  - name: npm-cache\n" +
                        "    type: docker-image\n" +
                        "    source:\n" +
                        "      repository: ymedlop/npm-cache-resource\n" +
                        "      tag: latest\n" +
                        "\n" +
                        "resources:\n" +
                        "  - name: hello_hapi\n" +
                        "    type: git\n" +
                        "    source: &repo-source\n" +
                        "      uri: https://somehost.com/your_github_user/hello_hapi.git\n" +
                        "      branch: master\n" +
                        "  - name: dependency-cache\n" +
                        "    type: npm-cache\n" +
                        "    source:\n" +
                        "      <<: *repo-source\n" +
                        "      paths:\n" +
                        "        - package.json\n" +
                        "\n" +
                        "jobs:\n" +
                        "  - name: Install dependencies\n" +
                        "    plan:\n" +
                        "      - get: hello_hapi\n" +
                        "        trigger: true\n" +
                        "      - get: dependency-cache\n" +
                        "  - name: Run tests\n" +
                        "    plan:\n" +
                        "      - get: hello_hapi\n" +
                        "        trigger: true\n" +
                        "        passed: [Install dependencies]\n" +
                        "      - get: dependency-cache\n" +
                        "        passed: [Install dependencies]\n" +
                        "      - task: run the test suite\n" +
                        "        file: hello_hapi/ci/tasks/run_tests.yml\n"
        );
        editor.assertProblems(/*None*/);
    }

    @Test
    void PT_163752179_completions_confused_by_empty_lines() throws Exception {
        //See: https://www.pivotaltracker.com/story/show/163752179
        String[] insertion = {
                "",
                "\n"
        };

        for (String maybeEmptyLine : insertion) {
            Editor editor = harness.newEditor(
                    "resources:\n" +
                            "\n" +
                            "- name: banana-img.git\n" +
                            "  type: docker-image\n" +
                            "  source:\n" +
                            "    repository: repo/banana-scratch-build\n" +
                            "\n" +
                            "- name: repo.git\n" +
                            "  type: git\n" +
                            "  source:\n" +
                            "    uri: https://example.com/repo.git\n" +
                            "\n" +
                            "jobs:\n" +
                            "- name: work-img\n" +
                            "  plan:\n" +
                            maybeEmptyLine +
                            "  - get: repo.git\n" +
                            "  - put: banana-img.git\n" +
                            "    params:\n" +
                            "      <*>"
            );
            editor.assertCompletionLabels(c -> c.getLabel().startsWith("cache"), "cache", "cache_from", "cache_tag");
        }
    }
    
    @Test
    void var_source_AttributeHovers() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: sts4\n" +
                        "  type: ssm\n" +
                        "  config:\n" +
                        "    region: east\n"
        );
        editor.assertProblems();

        editor.assertHoverContains("name", "The name of the **((var))** source");
        editor.assertHoverContains("type", "Expected one of:");
        editor.assertHoverContains("config", "Depending on the chosen `type` corresponding config:");
    }
    
    @Test
    void var_source_AttributeReconcile() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: "
        );
        editor.assertProblems(
                "-|[config, type] are required",
                "|String should not be empty"
        );

        editor = harness.newEditor(
                "var_sources:\n" +
                        "- type: blah"
        );
        editor.assertProblems(
                "-|[config, name] are required",
                "blah|Valid values are: [dummy, secretmanager, ssm, vault]"
        );
    }

    @Test
    void var_source_SSMConfig_Attrs_Hovers() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: sts4\n" +
                        "  type: ssm\n" +
                        "  config:\n" +
                        "    region: east\n"
        );
        editor.assertProblems();

        editor.assertHoverContains("region", "The AWS region to read secrets from.");
    }

    @Test
    void var_source_DummyConfig_Attrs_Hovers() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: sts4\n" +
                        "  type: dummy\n" +
                        "  config:\n" +
                        "    vars:\n" +
                        "      k1: v1\n"
        );
        
        editor.assertProblems();

        editor.assertHoverContains("vars", "A mapping of **var** name to **var** value.");
    }

    @Test
    void var_source_SecretManagerConfig_Attrs_Hovers() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: sts4\n" +
                        "  type: secretmanager\n" +
                        "  config:\n" +
                        "    aws-secretsmanager-access-key: access-key\n" +
                        "    aws-secretsmanager-secret-key: secret-key\n" +
                        "    aws-secretsmanager-session-token: token\n" +
                        "    aws-secretsmanager-region: region\n" +
                        "    aws-secretsmanager-pipeline-secret-template: pipeline\n" +
                        "    aws-secretsmanager-team-secret-template: team\n"
        );
        
        editor.assertProblems();

        editor.assertHoverContains("aws-secretsmanager-access-key", "A valid AWS access key.");
        editor.assertHoverContains("aws-secretsmanager-secret-key", "The secret key that corresponds to the access key defined above.");
        editor.assertHoverContains("aws-secretsmanager-session-token", "A valid AWS session token.");
        editor.assertHoverContains("aws-secretsmanager-region", "The AWS region that requests to Secrets Manager will be sent to.");
        editor.assertHoverContains("aws-secretsmanager-pipeline-secret-template", "The base path used when attempting to locate a pipeline-level secret.");
        editor.assertHoverContains("aws-secretsmanager-team-secret-template", "The base path used when attempting to locate a team-level secret.");
    }

    @Test
    void var_source_SecretManagerConfig_Attrs_Reconcile() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: sts4\n" +
                        "  type: secretmanager\n" +
                        "  config:\n" +
                        "    aws-secretsmanager-access-key: access-key\n"
        );
        
        editor.assertProblems(
                "config|'aws-secretsmanager-region' is required"
        );
    }
    
    @Test
    void var_source_VaultConfig_Attrs_Hovers() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: sts4\n" +
                        "  type: vault\n" +
                        "  config:\n" +
                        "    url: some_url\n" +
                        "    auth_backend: backend\n" +
                        "    auth_max_ttl: 10s\n" +
                        "    auth_params: \n" +
                        "      p1: v1\n" +
                        "      p2: v2\n" +
                        "    auth_retry_initial: 1s\n" +
                        "    auth_retry_max: 60s\n" +
                        "    ca_cert: ca\n" +
                        "    client_cert: client\n" +
                        "    client_key: client-key\n" +
                        "    client_name: client-name\n" +
                        "    client_token: client-token\n" +
                        "    insecure_skip_verify: true\n" +
                        "    lookup_templates:\n" +
                        "      - t1\n" +
                        "      - t2\n" +
                        "    namespace: awesomes\n" +
                        "    path_prefix: prefix\n" +
                        "    shared_path: shared\n"
        );
        
        editor.assertProblems();

        editor.assertHoverContains("url", "The URL of the Vault API.");
        editor.assertHoverContains("auth_backend", "Authenticate using an auth backend, e.g. cert or approle.");
        editor.assertHoverContains("auth_max_ttl", "Maximum duration to elapse before forcing the client to log in again.");
        editor.assertHoverContains("auth_params", "A key-value map of parameters to pass during authentication.");
        editor.assertHoverContains("auth_retry_initial", "When retrying during authentication, start with this retry interval.");
        editor.assertHoverContains("auth_retry_max", "When failing to authenticate, give up after this amount of time.");

        editor.assertHoverContains("ca_cert", "The PEM encoded contents of a CA certificate to use when connecting to the API.");
        editor.assertHoverContains("client_cert", "A PEM encoded client certificate, for use with TLS based auth.");
        editor.assertHoverContains("client_key", "A PEM encoded client key, for use with TLS based auth.");
        editor.assertHoverContains("client_name", "The expected name of the server when connecting through TLS.");
        editor.assertHoverContains("client_token", "Authenticate via a periodic client token.");
        editor.assertHoverContains("insecure_skip_verify", "Skip TLS validation. Not recommended. Don't do it. No really, don't.");
        editor.assertHoverContains("lookup_templates", "A list of path templates to be expanded in a team and pipeline context subject to the `path_prefix` and `namespace`.");
        editor.assertHoverContains("namespace", "Vault namespace");
        editor.assertHoverContains("path_prefix", "A prefix under which to look for all credential values.");
        editor.assertHoverContains("shared_path", "An additional path under which credentials will be looked up.");
    }

    @Test
    void var_source_VaultConfig_Attrs_Reconcile() throws Exception {
        Editor editor = harness.newEditor(
                "var_sources:\n" +
                        "- name: sts4\n" +
                        "  type: vault\n" +
                        "  config:\n" +
                        "    namespace: awesome\n"
        );
        
        editor.assertProblems(
                "config|'url' is required"
        );
    }
    
	
	//////////////////////////////////////////////////////////////////////////////

	private void assertContextualCompletions(String conText, String textBefore, String... textAfter) throws Exception {
		assertContextualCompletions((c) -> true, conText, textBefore, textAfter);
	}

	private void assertContextualCompletions(Predicate<CompletionItem> isInteresting, String conText, String textBefore, String... textAfter) throws Exception {
		assertContextualCompletions(LanguageId.CONCOURSE_PIPELINE, isInteresting, conText, textBefore, textAfter);
	}

	private void assertContextualCompletions(LanguageId language, Predicate<CompletionItem> isInteresting, String conText, String textBefore, String... textAfter) throws Exception {
		Editor editor = harness.newEditor(language, conText);
		editor.reconcile(); //this ensures the conText is parsed and its AST is cached (will be used for
		                    //dynamic CA when the conText + textBefore is not parsable.
		assertContains(CURSOR, conText);
		textBefore = conText.replace(CURSOR, textBefore);
		textAfter = Arrays.stream(textAfter)
				.map((String t) -> conText.replace(CURSOR, t))
				.collect(Collectors.toList()).toArray(new String[0]);
		editor.setText(textBefore);
		editor.assertCompletions(isInteresting, textAfter);
	}

	private void assertCompletions(String textBefore, String... textAfter) throws Exception {
		Editor editor = harness.newEditor(textBefore);
		editor.assertCompletions(textAfter);
	}

	private void assertTaskCompletions(String textBefore, String... textAfter) throws Exception  {
		Editor editor = harness.newEditor(LanguageId.CONCOURSE_TASK, textBefore);
		editor.assertCompletions(textAfter);
	}

	private void assertContextualTaskCompletions(String conText, String textBefore, String... textAfter) throws Exception {
		assertContextualCompletions(LanguageId.CONCOURSE_TASK, c -> true, conText, textBefore, textAfter);
	}

}
