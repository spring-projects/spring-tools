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
package org.springframework.ide.vscode.bosh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.ide.vscode.languageserver.testharness.Editor.DEDENTED_COMPLETION;
import static org.springframework.ide.vscode.languageserver.testharness.Editor.PLAIN_COMPLETION;
import static org.springframework.ide.vscode.languageserver.testharness.Editor.SNIPPET_COMPLETION;
import static org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness.getDocString;
import static org.springframework.ide.vscode.languageserver.testharness.TestAsserts.assertContains;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.bosh.bootiful.BoshLanguageServerTest;
import org.springframework.ide.vscode.bosh.mocks.MockCloudConfigProvider;
import org.springframework.ide.vscode.bosh.models.BoshCommandReleasesProvider;
import org.springframework.ide.vscode.bosh.models.BoshCommandStemcellsProvider;
import org.springframework.ide.vscode.bosh.models.DynamicModelProvider;
import org.springframework.ide.vscode.bosh.models.ReleaseData;
import org.springframework.ide.vscode.bosh.models.ReleasesModel;
import org.springframework.ide.vscode.bosh.models.StemcellData;
import org.springframework.ide.vscode.bosh.models.StemcellsModel;
import org.springframework.ide.vscode.commons.util.ExternalCommand;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;

@ExtendWith(SpringExtension.class)
@BoshLanguageServerTest
public class BoshEditorTest {

	@Autowired LanguageServerHarness harness;

	@Autowired BoshLanguageServerInitializer serverInitializer;
	@Autowired BoshCliConfig cliConfig;
	@Autowired MockCloudConfigProvider cloudConfigProvider;

	@MockitoBean DynamicModelProvider<StemcellsModel> stemcellsProvider;
	@MockitoBean DynamicModelProvider<ReleasesModel> releasesProvider;

	@BeforeEach public void setup() throws Exception {
		harness.intialize(null);
		System.setProperty("lsp.yaml.completions.errors.disable", "false");
	}

    @Test
    void toplevelV2PropertyNamesKnown() throws Exception {
        cloudConfigProvider.readWith(() -> {
            throw new IOException("Can't read cloud config");
        });
        Editor editor = harness.newEditor(
                "name: some-name\n" +
                        "director_uuid: cf8dc1fc-9c42-4ffc-96f1-fbad983a6ce6\n" +
                        "releases:\n" +
                        "- name: redis\n" +
                        "  version: 12\n" +
                        "stemcells:\n" +
                        "- alias: default\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: 3421.11\n" +
                        "update:\n" +
                        "  canaries: 1\n" +
                        "  max_in_flight: 10\n" +
                        "  canary_watch_time: 1000-30000\n" +
                        "  update_watch_time: 1000-30000\n" +
                        "instance_groups:\n" +
                        "- name: redis-master\n" +
                        "  instances: 1\n" +
                        "  azs: [z1, z2]\n" +
                        "  jobs:\n" +
                        "  - name: redis-server\n" +
                        "    release: redis\n" +
                        "    properties:\n" +
                        "      port: 3606\n" +
                        "  vm_type: large\n" +
                        "  vm_extensions: [public-lbs]\n" +
                        "  stemcell: default\n" +
                        "  persistent_disk_type: large\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "- name: redis-slave\n" +
                        "  instances: 2\n" +
                        "  azs: [z1, z2]\n" +
                        "  jobs:\n" +
                        "  - name: redis-server\n" +
                        "    release: redis\n" +
                        "    properties: {}\n" +
                        "  vm_type: large\n" +
                        "  stemcell: default\n" +
                        "  persistent_disk_type: large\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "variables:\n" +
                        "- name: admin_password\n" +
                        "  type: password\n" +
                        "- name: default_ca\n" +
                        "  type: certificate\n" +
                        "  options:\n" +
                        "    is_ca: true\n" +
                        "    common_name: some-ca\n" +
                        "- name: director_ssl\n" +
                        "  type: certificate\n" +
                        "  options:\n" +
                        "    ca: default_ca\n" +
                        "    common_name: cc.cf.internal\n" +
                        "    alternative_names: [cc.cf.internal]\n" +
                        "properties:\n" +
                        "  a-property: the-value\n" +
                        "tags:\n" +
                        "  project: cf\n" +
                        "blah: hoooo\n"
        );
        editor.assertProblems(
                "director_uuid|bosh v2 CLI no longer checks or requires",
                "properties|Deprecated in favor of job level properties and links",
                "blah|Unknown property"
        );

        editor.assertHoverContains("name", "The name of the deployment");
        editor.assertHoverContains("director_uuid", "This string must match the UUID of the currently targeted Director");
        editor.assertHoverContains("releases", "The name and version of each release in the deployment");
        editor.assertHoverContains("stemcells", "The name and version of each stemcell");
        editor.assertHoverContains("update", "This specifies instance update properties");
        editor.assertHoverContains("instance_groups", "Specifies the mapping between release [jobs](https://bosh.io/docs/terminology.html#job) and instance groups.");
        editor.assertHoverContains("properties", 3, "Describes global properties. Deprecated");
        editor.assertHoverContains("variables", "Describes variables");
        editor.assertHoverContains("tags", "Specifies key value pairs to be sent to the CPI for VM tagging");
    }

    @Test
    void reconcileCfManifest() throws Exception {
        cloudConfigProvider.executeCommandWith(() -> {
            throw new IOException("Couldn't contact the director");
        });
        Editor editor = harness.newEditorFromClasspath("/workspace/cf-deployment-manifest.yml");
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void toplevelPropertyCompletions() throws Exception {
        Editor editor = harness.newEditor(
                "<*>"
        );
        editor.assertCompletions(SNIPPET_COMPLETION.negate(),
                "name: <*>"
        );

        editor = harness.newEditor(
                "name: blah\n" +
                        "<*>"
        );

        editor.assertCompletions(
                "name: blah\n" +
                        "instance_groups:\n" +
                        "- name: $1\n" +
                        "  azs:\n" +
                        "  - $2\n" +
                        "  instances: $3\n" +
                        "  jobs:\n" +
                        "  - name: $4\n" +
                        "    release: $5\n" +
                        "  vm_type: $6\n" +
                        "  stemcell: $7\n" +
                        "  networks:\n" +
                        "  - name: $8<*>"
        , // ============
                "name: blah\n" +
                        "releases:\n" +
                        "- name: $1\n" +
                        "  version: $2<*>"
        , // ============
                "name: blah\n" +
                        "stemcells:\n" +
                        "- alias: $1\n" +
                        "  version: $2<*>"
        , // ============
                "name: blah\n" +
                        "tags:\n  <*>"
        , // ============
                "name: blah\n" +
                        "update:\n" +
                        "  canaries: $1\n" +
                        "  max_in_flight: $2\n" +
                        "  canary_watch_time: $3\n" +
                        "  update_watch_time: $4<*>"
        , // ============
                "name: blah\n" +
                        "variables:\n" +
                        "- name: $1\n" +
                        "  type: $2<*>"
// Below completions are suppressed because they are deprecated
//				, // ============
//				"name: blah\n" +
//				"director_uuid: <*>"
//				, // ============
//				"name: blah\n" +
//				"properties:\n  <*>"
        );
    }

    @Test
    void stemcellCompletions() throws Exception {
        Editor editor = harness.newEditor(
                "stemcells:\n" +
                        "- <*>"
        );
        editor.assertCompletions(PLAIN_COMPLETION,
                "stemcells:\n" +
                        "- alias: $1\n" +
                        "  version: $2<*>"
        , // ==========
                "stemcells:\n" +
                        "- alias: <*>"
        );

        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: blah\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION,
                "<*>"
        , // =>
                "name: <*>",
                "os: <*>",
                "version: <*>"
        );

    }

    @Test
    void stemcellReconciling() throws Exception {
        Editor editor = harness.newEditor(
                "stemcells:\n" +
                        "- {}"
        );
        editor.assertProblems(
                "-|One of [name, os] is required",
                "-|[alias, version] are required",
                "}|[instance_groups, name, releases, update] are required"
        );
    }

    @Test
    void stemcellHovers() throws Exception {
        Editor editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: default\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: 3147\n" +
                        "  name: bosh-aws-xen-hvm-ubuntu-trusty-go_agent"
        );
        editor.assertHoverContains("alias", "Name of a stemcell used in the deployment");
        editor.assertHoverContains("os", "Operating system of a matching stemcell");
        editor.assertHoverContains("version", "The version of a matching stemcell");
        editor.assertHoverContains("name", "Full name of a matching stemcell. Either `name` or `os` keys can be specified.");
    }

    @Test
    void releasesBlockCompletions() throws Exception {
        serverInitializer.enableSnippets(false);
        Editor editor = harness.newEditor(
                "releases:\n" +
                        "- <*>"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertCompletions(
                "releases:\n" +
                        "- name: <*>"
        );

        editor = harness.newEditor(
                "releases:\n" +
                        "- name: foo\n" +
                        "  <*>"
        );
        editor.assertCompletions(PLAIN_COMPLETION,
                "releases:\n" +
                        "- name: foo\n" +
                        "  sha1: <*>"
        , // ============
                "releases:\n" +
                        "- name: foo\n" +
                        "  url: <*>"
        , // ============
                "releases:\n" +
                        "- name: foo\n" +
                        "  version: <*>"
        );
    }

    @Test
    void releasesAdvancedValidations() throws Exception {
        Editor editor = harness.newEditor(
                "releases:\n" +
                        "- name: some-release\n" +
                        "  url: https://my.releases.com/funky.tar.gz\n" +
                        "- name: other-relase\n" +
                        "  version: other-version\n" +
                        "  url: file:///root/releases/a-nice-file.tar.gz\n" +
                        "- name: bad-url\n" +
                        "  version: more-version\n" +
                        "  url: proto://something.com\n" +
                        "#x"
        );
        editor.assertProblems(
                "^-^ name: some-release|'version' is required",
                "url|'sha1' is recommended when the 'url' is http(s)",
                "proto|Url scheme must be one of [http, https, file]",
                "x|are required"
        );
        Diagnostic missingSha1Problem = editor.assertProblem("url");
        assertContains("'sha1' is recommended", missingSha1Problem.getMessage());
        assertEquals(DiagnosticSeverity.Warning, missingSha1Problem.getSeverity());
    }

    @Test
    void releasesBlockPropertyReconcileAndHovers() throws Exception {
        Editor editor = harness.newEditor(
                "releases:\n" +
                        "- name: some-release\n" +
                        "  version: some-version\n" +
                        "  url: https://my.releases.com/funky.tar.gz\n" +
                        "  sha1: 440248a31253296b1626ad52886e58900730f32e\n" +
                        "  woot: dunno\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "woot|Unknown property"
        );

        editor.assertHoverContains("name", "Name of a release used in the deployment");
        editor.assertHoverContains("version", "The version of the release to use");
        editor.assertHoverContains("url", "URL of the release to use");
        editor.assertHoverContains("sha1", "The SHA1 of the release tarball");

        editor = harness.newEditor(
                "releases:\n" +
                        "- name: some-release\n" +
                        "  version: <*>\n"
        );
        editor.assertCompletionLabels("latest");
    }

    @Test
    void instanceGroupsCompletions() throws Exception {
        serverInitializer.enableSnippets(false);
        Editor editor = harness.newEditor(
                "instance_groups:\n" +
                        "- <*>"
        );
        editor.assertCompletions(
                "instance_groups:\n" +
                        "- name: <*>"
        );

        editor = harness.newEditor(
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  <*>"
        );
        editor.assertCompletions(PLAIN_COMPLETION,
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  azs:\n" +
                        "  - <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  env:\n" +
                        "    <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  instances: <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  jobs:\n" +
                        "  - <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  lifecycle: <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  migrated_from:\n" +
                        "  - <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  networks:\n" +
                        "  - name: <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  persistent_disk_type: <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  stemcell: <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  update:\n" +
                        "    <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  vm_extensions:\n" +
                        "  - <*>"
        , // =============
                "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  vm_type: <*>"

//				, // =============
// Not suggested because its deprecated:
//				"instance_groups:\n" +
//				"- name: foo-group\n" +
//				"  properties:\n" +
//				"    <*>"
        );
    }

    @Test
    void instanceGroupsHovers() throws Exception {
        Editor editor = harness.newEditor(
                "instance_groups:\n" +
                        "- name: redis-master\n" +
                        "  properties: {}\n" +
                        "  instances: 1\n" +
                        "  azs: [z1, z2]\n" +
                        "  jobs:\n" +
                        "  - name: redis-server\n" +
                        "    release: redis\n" +
                        "    properties:\n" +
                        "      port: 3606\n" +
                        "  vm_type: medium\n" +
                        "  vm_extensions: [public-lbs]\n" +
                        "  stemcell: default\n" +
                        "  persistent_disk_type: medium\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "\n" +
                        "- name: redis-slave\n" +
                        "  instances: 2\n" +
                        "  azs: [z1, z2]\n" +
                        "  jobs:\n" +
                        "  - name: redis-server\n" +
                        "    release: redis\n" +
                        "    properties: {}\n" +
                        "  update:\n" +
                        "    canaries: 2\n" +
                        "  lifecycle: errand\n" +
                        "  migrated_from: []\n" +
                        "  env: {}\n" +
                        "  vm_type: medium\n" +
                        "  stemcell: default\n" +
                        "  persistent_disk_type: medium\n" +
                        "  networks:\n" +
                        "  - name: default\n"
        );
        editor.assertHoverContains("name", "A unique name used to identify and reference the instance group.");
        editor.assertHoverContains("azs", "List of AZs associated with this instance group");
        editor.assertHoverContains("instances", "The number of instances in this group");
        editor.assertHoverContains("jobs", "Specifies the name and release of jobs that will be installed on each instance.");
        editor.assertHoverContains("vm_type", "A valid VM type name from the cloud config");
        editor.assertHoverContains("vm_extensions", "A valid list of VM extension names from the cloud config");
        editor.assertHoverContains("stemcell", "A valid stemcell alias from the Stemcells Block");
        editor.assertHoverContains("persistent_disk_type", "A valid disk type name from the cloud config.");
        editor.assertHoverContains("networks", "Specifies the networks this instance requires");
        editor.assertHoverContains("update", "Specific update settings for this instance group");
        editor.assertHoverContains("migrated_from", "Specific migration settings for this instance group.");
        editor.assertHoverContains("lifecycle", "Specifies the kind of workload");
        editor.assertHoverContains("properties", "Specifies instance group properties");
        editor.assertHoverContains("env", "Specifies advanced BOSH Agent configuration");
    }

    @Test
    void instanceGroups_job_hovers() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups:\n" +
                        "- name: foo\n" +
                        "  jobs:\n" +
                        "  - name: the-job\n" +
                        "    release: the-jobs-release\n" +
                        "    properties:\n" +
                        "      blah: blah\n" +
                        "    consumes:\n" +
                        "      blah: blah \n" +
                        "    provides:\n" +
                        "      blah: blah\n"
        );
        editor.assertHoverContains("name", 3, "The job name");
        editor.assertHoverContains("release", "The release where the job exists");
        editor.assertHoverContains("consumes", "Links consumed by the job");
        editor.assertHoverContains("provides", "Links provided by the job");
        editor.assertHoverContains("properties", "Specifies job properties");
    }

    @Test
    void instanceGroups_network_hovers() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups:\n" +
                        "- name: foo\n" +
                        "  networks:\n" +
                        "  - name: the-network\n" +
                        "    static_ips: []\n" +
                        "    default: []\n"
        );
        editor.assertHoverContains("name", 3, "A valid network name from the cloud config");
        editor.assertHoverContains("static_ips", "Array of IP addresses");
        editor.assertHoverContains("default", "Specifies which network components");
    }

    @Test
    void instanceGroups_env_hovers() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups:\n" +
                        "- name: foo\n" +
                        "  env:\n" +
                        "    bosh: {}\n" +
                        "    password: []\n"
        );
        editor.assertHoverContains("bosh", "no description");
        editor.assertHoverContains("password", "Crypted password");
    }

    @Test
    void updateBlockCompletions() throws Exception {
        serverInitializer.enableSnippets(false);
        Editor editor = harness.newEditor(
                "update:\n" +
                        "  <*>"
        );
        editor.assertCompletions(PLAIN_COMPLETION,
                "update:\n" +
                        "  canaries: <*>"
        , // =====
                "update:\n" +
                        "  canary_watch_time: <*>"
        , // =====
                "update:\n" +
                        "  max_in_flight: <*>"
        , // =====
                "update:\n" +
                        "  serial: <*>"
        , // =====
                "update:\n" +
                        "  update_watch_time: <*>"
        );
    }

    @Test
    void updateBlockHovers() throws Exception {
        Editor editor = harness.newEditor(
                "update:\n" +
                        "  canaries: 1\n" +
                        "  max_in_flight: 10\n" +
                        "  canary_watch_time: 1000-30000\n" +
                        "  update_watch_time: 1000-30000\n" +
                        "  serial: false"
        );
        editor.assertHoverContains("canaries", "The number of [canary]");
        editor.assertHoverContains("max_in_flight", "maximum number of non-canary instances");
        editor.assertHoverContains("canary_watch_time", "checks whether the canary instances");
        editor.assertHoverContains("update_watch_time", "checks whether the instances");
        editor.assertHoverContains("serial", "deployed in parallel");
    }

    @Test
    void variablesBlockCompletions() throws Exception {
        serverInitializer.enableSnippets(false);
        Editor editor = harness.newEditor(
                "variables:\n" +
                        "- <*>"
        );
        editor.assertCompletions(
                "variables:\n" +
                        "- name: <*>"
        );

        editor = harness.newEditor(
                "variables:\n" +
                        "- name: foo\n" +
                        "  <*>"
        );
        editor.assertCompletions(PLAIN_COMPLETION,
                "variables:\n" +
                        "- name: foo\n" +
                        "  options:\n" +
                        "    <*>"
        , // ===============
                "variables:\n" +
                        "- name: foo\n" +
                        "  type: <*>"
        );

        editor = harness.newEditor(
                "variables:\n" +
                        "- name: foo\n" +
                        "  type: <*>"
        );
        editor.assertCompletionLabels("certificate", "password", "rsa", "ssh");
    }

    @Test
    void variablesBlockReconciling() throws Exception {
        Editor editor = harness.newEditor(
                "variables:\n" +
                        "- name: admin-passcode\n" +
                        "  type: something-that-might-work-in-theory\n" + //shouldn't be a warning/error
                        "  bogus-propt: bah\n" +
                        "  options: {}"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "bogus-propt|Unknown property"
        );
    }

    @Test
    void variablesBlockHovers() throws Exception {
        Editor editor = harness.newEditor(
                "variables:\n" +
                        "- name: admin_password\n" +
                        "  type: password\n" +
                        "- name: default_ca\n" +
                        "  type: certificate\n" +
                        "  options:\n" +
                        "    is_ca: true\n" +
                        "    common_name: some-ca\n" +
                        "- name: director_ssl\n" +
                        "  type: certificate\n" +
                        "  options:\n" +
                        "    ca: default_ca\n" +
                        "    common_name: cc.cf.internal\n" +
                        "    alternative_names: [cc.cf.internal]"
        );
        editor.assertHoverContains("name", "Unique name used to identify a variable");
        editor.assertHoverContains("type", "Type of a variable");
        editor.assertHoverContains("options", "Specifies generation options");
    }

    @Test
    void tolerateV1Manifests() throws Exception {
        Editor editor = harness.newEditor(
                "---\n" +
                        "name: my-redis-deployment\n" +
                        "director_uuid: 1234abcd-5678-efab-9012-3456cdef7890\n" +
                        "\n" +
                        "releases:\n" +
                        "- {name: redis, version: 12}\n" +
                        "\n" +
                        "resource_pools:\n" +
                        "- name: redis-servers\n" +
                        "  network: default\n" +
                        "  stemcell:\n" +
                        "    name: bosh-aws-xen-ubuntu-trusty-go_agent\n" +
                        "    version: 2708\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: m1.small\n" +
                        "    availability_zone: us-east-1c\n" +
                        "\n" +
                        "disk_pools: []\n" +
                        "\n" +
                        "networks:\n" +
                        "- name: default\n" +
                        "  type: manual\n" +
                        "  subnets:\n" +
                        "  - range: 10.10.0.0/24\n" +
                        "    gateway: 10.10.0.1\n" +
                        "    static:\n" +
                        "    - 10.10.0.16 - 10.10.0.18\n" +
                        "    reserved:\n" +
                        "    - 10.10.0.2 - 10.10.0.15\n" +
                        "    dns: [10.10.0.6]\n" +
                        "    cloud_properties:\n" +
                        "      subnet: subnet-d597b993\n" +
                        "\n" +
                        "compilation:\n" +
                        "  workers: 2\n" +
                        "  network: default\n" +
                        "  reuse_compilation_vms: true\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: c1.medium\n" +
                        "    availability_zone: us-east-1c\n" +
                        "\n" +
                        "update:\n" +
                        "  canaries: 1\n" +
                        "  max_in_flight: 3\n" +
                        "  canary_watch_time: 15000-30000\n" +
                        "  update_watch_time: 15000-300000\n" +
                        "\n" +
                        "jobs:\n" +
                        "- name: redis-master\n" +
                        "  instances: 1\n" +
                        "  templates:\n" +
                        "  - {name: redis-server, release: redis}\n" +
                        "  persistent_disk: 10_240\n" +
                        "  resource_pool: redis-servers\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "\n" +
                        "- name: redis-slave\n" +
                        "  instances: 2\n" +
                        "  templates:\n" +
                        "  - {name: redis-server, release: redis}\n" +
                        "  persistent_disk: 10_240\n" +
                        "  resource_pool: redis-servers\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "\n" +
                        "properties:\n" +
                        "  redis:\n" +
                        "    max_connections: 10\n" +
                        "\n" +
                        "cloud_provider: {}"
        );
        editor.ignoreProblem(YamlSchemaProblems.DEPRECATED_PROPERTY);
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(
                "name: foo\n" +
                        "director_uuid: dca5480a-6b0e-11e7-907b-a6006ad3dba0\n" +
                        "networks: {}" //This makes it a V1 schema
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "director_uuid|bosh v2 CLI no longer checks or requires",
                "networks|Deprecated: 'networks' is a V1 schema property"
        );
    }

    @Test
    void documentSymbols() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "variables:\n" +
                        "- name: blobstore_admin_users_password\n" +
                        "  type: password\n" +
                        "- name: blobstore_secure_link_secret\n" +
                        "  type: password\n" +
                        "stemcells:\n" +
                        "- alias: default\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: '3421.11'\n" +
                        "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  networks:\n" +
                        "  - name: the-network\n" +
                        "    static_ips: []\n" +
                        "    default: []\n" +
                        "- name: bar-group\n" +
                        "  networks:\n" +
                        "  - name: the-network\n" +
                        "    static_ips: []\n" +
                        "    default: []\n" +
                        "releases:\n" +
                        "- name: one-release\n" +
                        "- name: other-release\n"
        );
        editor.assertDocumentSymbols(
                "default",
                "foo-group",
                "bar-group",
                "one-release",
                "other-release",
                "blobstore_admin_users_password",
                "blobstore_secure_link_secret"
        );
    }

    @Test
    void duplicateSymbolChecking() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "variables:\n" +
                        "- name: dup_var\n" +
                        "  type: password\n" +
                        "- name: blobstore_admin_users_password\n" +
                        "  type: password\n" +
                        "- name: blobstore_secure_link_secret\n" +
                        "  type: password\n" +
                        "- name: dup_var\n" +
                        "  type: ssh\n" +
                        "stemcells:\n" +
                        "- alias: default\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: '3421.11'\n" +
                        "- alias: dup_cell\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: '3421.11'\n" +
                        "- alias: dup_cell\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: '3421.11'\n" +
                        "instance_groups:\n" +
                        "- name: foo-group\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "    static_ips: []\n" +
                        "    default: []\n" +
                        "- name: bar-group\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "    static_ips: []\n" +
                        "    default: []\n" +
                        "- name: bar-group\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "    static_ips: []\n" +
                        "    default: []\n" +
                        "releases:\n" +
                        "- name: one-release\n" +
                        "- name: other-release\n" +
                        "- name: one-release\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);

        editor.assertProblems(
                "dup_var|Duplicate 'VariableName'",
                "dup_var|Duplicate 'VariableName'",
                "dup_cell|Duplicate 'StemcellAlias'",
                "dup_cell|Duplicate 'StemcellAlias'",
                "bar-group|Duplicate 'InstanceGroupName'",
                "bar-group|Duplicate 'InstanceGroupName'",
                "one-release|Duplicate 'ReleaseName'",
                "one-release|Duplicate 'ReleaseName'"
        );
    }

    @Test
    void contentAssistReleaseReference() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  jobs:\n" +
                        "  - release: <*>\n" +
                        "releases: \n" +
                        "- name: some-release\n" +
                        "  url: https://release-hub.info/some-release.tar.gz?version=99.3.2\n" +
                        "  sha1: asddsfsd\n" +
                        "- name: other-release\n" +
                        "  url: https://release-hub.info/other-release.tar.gz?version=99.3.2\n" +
                        "  sha1: asddsfsd\n"
        );
        editor.assertContextualCompletions("<*>"
        , // ==>
                "other-release<*>", "some-release<*>"
        );
    }

    @Test
    void reconcileReleaseReference() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  jobs:\n" +
                        "  - release: some-release\n" +
                        "- name: some-other-server\n" +
                        "  jobs:\n" +
                        "  - release: bogus-release\n" +
                        "releases: \n" +
                        "- name: some-release\n" +
                        "  url: https://release-hub.info/some-release.tar.gz?version=99.3.2\n" +
                        "  sha1: asddsfsd\n" +
                        "- name: other-release\n" +
                        "  url: https://release-hub.info/other-release.tar.gz?version=99.3.2\n" +
                        "  sha1: asddsfsd\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "bogus-release|unknown 'ReleaseName'. Valid values are: [other-release, some-release]"
        );
    }

    @Test
    void contentAssistStemcellReference() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  stemcell: <*>\n" +
                        "stemcells:\n" +
                        "- alias: default\n" +
                        "  os: ubuntu\n" +
                        "  version: 1346.77.1\n" +
                        "- alias: windoze\n" +
                        "  os: windows\n" +
                        "  version: 678.9.1\n"
        );
        editor.assertContextualCompletions("<*>"
        , // ==>
                "default<*>", "windoze<*>"
        );
    }

    @Test
    void reconcileStemcellReference() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  stemcell: default\n" +
                        "- name: windoze-server\n" +
                        "  stemcell: windoze\n" +
                        "- name: bad-server\n" +
                        "  stemcell: bogus-stemcell\n" +
                        "stemcells:\n" +
                        "- alias: default\n" +
                        "  os: ubuntu\n" +
                        "  version: 1346.77.1\n" +
                        "- alias: windoze\n" +
                        "  os: windows\n" +
                        "  version: 678.9.1\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "bogus-stemcell|unknown 'StemcellAlias'. Valid values are: [default, windoze]"
        );
    }

    @Test
    void dynamicStemcellNamesFromDirector() throws Exception {
        StemcellsModel stemcellsModel = mock(StemcellsModel.class);
        when(stemcellsProvider.getModel(any())).thenReturn(stemcellsModel);
        when(stemcellsModel.getStemcellNames()).thenReturn(ImmutableMultiset.of(
                "bosh-vsphere-esxi-centos-7-go_agent",
                "bosh-vsphere-esxi-ubuntu-trusty-go_agent"
        ));

        //content assist
        Editor editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: blah\n" +
                        "  name: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "bosh-vsphere-esxi-centos-7-go_agent<*>",
                "bosh-vsphere-esxi-ubuntu-trusty-go_agent<*>"
        );

        //reconcile
        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  name: bosh-vsphere-esxi-centos-7-go_agent\n" +
                        "- alias: not-so-good\n" +
                        "  name: bogus<*>"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "bogus|unknown 'StemcellName'. Valid values are: [bosh-vsphere-esxi-centos-7-go_agent, bosh-vsphere-esxi-ubuntu-trusty-go_agent]"
        );
    }

    @Test
    void dynamicStemcellOssFromDirector() throws Exception {
        StemcellsModel stemcellsModel = mock(StemcellsModel.class);
        when(stemcellsProvider.getModel(any())).thenReturn(stemcellsModel);
        when(stemcellsModel.getStemcellOss()).thenReturn(ImmutableSet.of("ubuntu", "centos"));

        //content assist
        Editor editor = harness.newEditor(
                "stemcells:\n" +
                        "- os: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "centos<*>",
                "ubuntu<*>"
        );

        //reconcile
        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  os: ubuntu\n" +
                        "- alias: not-so-good\n" +
                        "  os: bogus<*>"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "bogus|unknown 'StemcellOs'. Valid values are: [ubuntu, centos]"
        );
    }

    @Test
    void contentAssistStemcellNameNoDirector() throws Exception {
        Editor editor;
        when(stemcellsProvider.getModel(any())).thenThrow(new IOException("Couldn't connect to bosh"));
        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  name: <*>\n"
        );
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(1, completions.size());
        CompletionItem c = completions.get(0);
        c = harness.resolveCompletionItem(c);
        assertContains("Couldn't connect to bosh", getDocString(c));
    }

    @Test
    void contentAssistStemcellVersionNoDirector() throws Exception {
        Editor editor;
        when(stemcellsProvider.getModel(any())).thenThrow(new IOException("Couldn't connect to bosh"));
        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  name: centos-agent\n" +
                        "  version: <*>\n"
        );
        editor.assertContextualCompletions("<*>",
                "latest<*>"
        );
    }

    @Test
    void contentAssistStemcellVersionFromDirector() throws Exception {
        Editor editor;
        provideStemcellsFrom(
                new StemcellData("ubuntu-agent", "123.4", "ubuntu"),
                new StemcellData("ubuntu-agent", "222.2", "ubuntu"),
                new StemcellData("centos-agent", "222.2", "centos"),
                new StemcellData("centos-agent", "333.3", "centos")
        );

        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  name: centos-agent\n" +
                        "  version: <*>\n"
        );
        editor.assertContextualCompletions("<*>",
                "222.2<*>", "333.3<*>", "latest<*>"
        );

        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  os: ubuntu\n" +
                        "  version: <*>\n"
        );
        editor.assertContextualCompletions("<*>",
                "123.4<*>", "222.2<*>", "latest<*>"
        );

        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  version: <*>\n"
        );
        editor.assertContextualCompletions("<*>",
                "123.4<*>", "222.2<*>", "333.3<*>", "latest<*>"
        );

        //when os and name are 'bogus' at least suggest proposals based on other prop
        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  name: centos-agent\n" +
                        "  os: bogus\n" +
                        "  version: <*>\n"
        );
        editor.assertContextualCompletions("<*>",
                "222.2<*>", "333.3<*>", "latest<*>"
        );

        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  os: centos\n" +
                        "  name: bogus\n" +
                        "  version: <*>\n"
        );
        editor.assertContextualCompletions("<*>",
                "222.2<*>", "333.3<*>", "latest<*>"
        );

        //when the os and name disagree, merge the proposals for both:
        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: good\n" +
                        "  os: centos\n" + //Contradicts the name
                        "  name: ubuntu-agent\n" + //Contradicts the os
                        "  version: <*>\n"
        );
        editor.assertContextualCompletions("<*>",
                "123.4<*>", "222.2<*>", "333.3<*>", "latest<*>"
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void reconcileStemcellVersionNoDirector() throws Exception {
        Editor editor;
        stemcellsProvider = mock(DynamicModelProvider.class);
        when(stemcellsProvider.getModel(any())).thenThrow(new IOException("Couldn't connect to bosh"));
        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: aaa\n" +
                        "  name: centos-agent\n" +
                        "  version: 222.2\n" +
                        "- alias: bbb\n" +
                        "  name: centos-agent\n" +
                        "  version: 123.4\n" +
                        "- alias: ddd\n" +
                        "  os: ubuntu\n" +
                        "  version: 333.3\n" +
                        "- alias: eee\n" +
                        "  os: ubuntu\n" +
                        "  version: latest\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(/*NONE*/);
    }

    @Test
    void reconcileStemcellVersionFromDirector() throws Exception {
        Editor editor;
        provideStemcellsFrom(
                new StemcellData("ubuntu-agent", "123.4", "ubuntu"),
                new StemcellData("ubuntu-agent", "222.2", "ubuntu"),
                new StemcellData("centos-agent", "222.2", "centos"),
                new StemcellData("centos-agent", "333.3", "centos")
        );

        editor = harness.newEditor(
                "stemcells:\n" +
                        "- alias: aaa\n" +
                        "  name: centos-agent\n" +
                        "  version: 222.2\n" +
                        "- alias: bbb\n" +
                        "  name: centos-agent\n" +
                        "  version: 123.4\n" +
                        "- alias: ddd\n" +
                        "  os: ubuntu\n" +
                        "  version: 333.3\n" +
                        "- alias: eee\n" +
                        "  os: ubuntu\n" +
                        "  version: latest\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "123.4|unknown 'StemcellVersion[name=centos-agent]'. Valid values are: [222.2, 333.3, latest]",
                "333.3|unknown 'StemcellVersion[os=ubuntu]'. Valid values are: [123.4, 222.2, latest]"
        );
    }

	private void provideStemcellsFrom(StemcellData... stemcellData) throws Exception {
		BoshCommandStemcellsProvider stemcells = new BoshCommandStemcellsProvider(cliConfig) {
			@Override
			protected String executeCommand(ExternalCommand command) throws Exception {
				String rows = gson.toJson(stemcellData);
				return "{\n" +
						"    \"Tables\": [\n" +
						"        {\n" +
						"            \"Rows\": " + rows +
						"        }\n" +
						"    ]\n" +
						"}";
			}
		};
		when(stemcellsProvider.getModel(any())).then(inv -> {
			return stemcells.getModel(null);
		});
	}

    @Test
    void contentAssistReleaseNameDef() throws Exception {
        provideReleasesFrom(
                new ReleaseData("foo", "123.4"),
                new ReleaseData("foo", "222.2"),
                new ReleaseData("bar", "222.2"),
                new ReleaseData("bar", "333.3")
        );

        Editor editor = harness.newEditor(
                "releases:\n" +
                        "- name: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "bar<*>",
                "foo<*>"
        );
    }

    @Test
    void reconcileReleaseNameDef() throws Exception {
        provideReleasesFrom(
                new ReleaseData("foo", "123.4"),
                new ReleaseData("foo", "222.2"),
                new ReleaseData("bar", "222.2"),
                new ReleaseData("bar", "333.3")
        );

        Editor editor = harness.newEditor(
                "releases:\n" +
                        "- name: foo\n" +
                        "- name: bar\n" +
                        "- name: bogus\n" +
                        "- name: url-makes-this-ok\n" +
                        "  url: file://blah"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus|unknown 'ReleaseName'. Valid values are: [foo, bar]");
    }

    @Test
    void contentAssistReleaseVersion() throws Exception {
        provideReleasesFrom(
                new ReleaseData("foo", "123.4"),
                new ReleaseData("foo", "222.2"),
                new ReleaseData("bar", "222.2"),
                new ReleaseData("bar", "333.3")
        );

        Editor editor = harness.newEditor(
                "releases:\n" +
                        "- version: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "123.4<*>",
                "222.2<*>",
                "333.3<*>",
                "latest<*>"
        );

        editor = harness.newEditor(
                "releases:\n" +
                        "- name: foo\n" +
                        "  version: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "123.4<*>",
                "222.2<*>",
                "latest<*>"
        );

        //Still get all suggestions even when 'url' property is added
        editor = harness.newEditor(
                "releases:\n" +
                        "- version: <*>\n" +
                        "  url: blah"
        );
        editor.assertContextualCompletions("<*>",
                "123.4<*>",
                "222.2<*>",
                "333.3<*>",
                "latest<*>"
        );

        editor = harness.newEditor(
                "releases:\n" +
                        "- name: foo\n" +
                        "  url: blah\n" +
                        "  version: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "123.4<*>",
                "222.2<*>",
                "333.3<*>",
                "latest<*>"
        );

    }

    @Test
    void reconcileReleaseVersion() throws Exception {
        Editor editor;
        provideReleasesFrom(
                new ReleaseData("foo", "123.4"),
                new ReleaseData("foo", "222.2"),
                new ReleaseData("bar", "222.2"),
                new ReleaseData("bar", "333.3")
        );

        editor = harness.newEditor(
                "releases:\n" +
                        "- version: bogus\n" +
                        "- version: url-makes-this-possibly-correct\n" +
                        "  url: file:///relesease-folder/blah-release.tar.gz\n" +
                        "- name: bar\n" +
                        "  version: url-makes-this-also-possibly-correct\n" +
                        "  url: file:///relesease-folder/other-release.tar.gz"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus|unknown 'ReleaseVersion'. Valid values are: [123.4, 222.2, 333.3, latest]");

        editor = harness.newEditor(
                "releases:\n" +
                        "- version: 123.4\n" +
                        "- version: latest\n" +
                        "- version: bogus\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus|unknown 'ReleaseVersion'. Valid values are: [123.4, 222.2, 333.3, latest]");

        editor = harness.newEditor(
                "releases:\n" +
                        "- name: foo\n" +
                        "  version: 123.4\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(
                "releases:\n" +
                        "- name: foo\n" +
                        "  version: 222.2\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(/*NONE*/);

        editor = harness.newEditor(
                "releases:\n" +
                        "- name: foo\n" +
                        "  version: 333.3\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("333.3|unknown 'ReleaseVersion[name=foo]'. Valid values are: [123.4, 222.2, latest]");

    }


	private void provideReleasesFrom(ReleaseData... stemcellData) throws Exception {
		BoshCommandReleasesProvider releases = new BoshCommandReleasesProvider(cliConfig) {
			@Override
			protected String executeCommand(ExternalCommand command) throws Exception {
				String rows = gson.toJson(stemcellData);
				return "{\n" +
						"    \"Tables\": [\n" +
						"        {\n" +
						"            \"Rows\": " + rows +
						"        }\n" +
						"    ]\n" +
						"}";
			}
		};
		when(releasesProvider.getModel(any())).then(in -> {
			return releases.getModel(null);
		});
	}

    @Test
    void contentAssistVMtype() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  stemcell: windoze\n" +
                        "  vm_type: <*>"
        );
        editor.assertContextualCompletions(
                "<*>"
        , // ==>
                "default<*>",
                "large<*>"
        );

        //Verify that the cache is working. Shouldn't read the cc provier more than once, evem for multiple CA requests.
        editor.assertCompletionLabels("default", "large");
        editor.assertCompletionLabels("default", "large");
        assertEquals(1, cloudConfigProvider.getReadCount());
    }

    @Test
    void reconcileVMtype() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  vm_type: bogus-vm\n" +
                        "- name: other-server\n" +
                        "  vm_type: large"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "bogus-vm|unknown 'VMType'. Valid values are: [default, large]"
        );
        assertEquals(1, cloudConfigProvider.getReadCount());
    }

    @Test
    void reconcileVMTypeWhenCloudConfigUnavailable() throws Exception {
        cloudConfigProvider.executeCommandWith(() -> null);
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  vm_type: bogus-vm\n" +
                        "- name: other-server\n" +
                        "  vm_type: large"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(/*NONE*/); //Should not complain about unknown vm_types, if we can't determine what valid vm_types actually exist.
    }

    @Test
    void reconcileVMTypeWhenCloudConfigThrows() throws Exception {
        cloudConfigProvider.executeCommandWith(() -> {
            throw new TimeoutException("Reading cloud config timed out");
        });
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  vm_type: bogus-vm\n" +
                        "- name: other-server\n" +
                        "  vm_type: large"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(/*NONE*/); //Should not complain about unknown vm_types, if we can't determine what valid vm_types actually exist.
    }

    @Test
    void contentAssistShowsWarningWhenCloudConfigThrows() throws Exception {
        cloudConfigProvider.executeCommandWith(() -> {
            throw new TimeoutException("Reading cloud config timed out");
        });
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  vm_type: <*>"
        );
        CompletionItem completion = editor.assertCompletionLabels("TimeoutException").get(0);
        completion = harness.resolveCompletionItem(completion);
        assertContains("Reading cloud config timed out", getDocString(completion));
    }

    @Test
    void reconcileNetworkName() throws Exception {
        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server\n" +
                        "  networks:\n" +
                        "  - name: default\n" +
                        "  - name: bogus-nw\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus-nw|unknown 'NetworkName'. Valid values are: [default]");
    }

    @Test
    void reconcileNetworkName2() throws Exception {
        cloudConfigProvider.readWith(() ->
                "networks:\n" +
                        "- name: public-nw\n" +
                        "- name: local-nw"
        );

        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server\n" +
                        "  networks:\n" +
                        "  - name: public-nw\n" +
                        "  - name: local-nw\n" +
                        "  - name: bogus-nw\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus-nw|unknown 'NetworkName'. Valid values are: [public-nw, local-nw]");
    }

    @Test
    void contentAssistNetworkName() throws Exception {
        cloudConfigProvider.readWith(() ->
                "networks:\n" +
                        "- name: public-nw\n" +
                        "- name: local-nw"
        );

        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server\n" +
                        "  networks:\n" +
                        "  - name: <*>"
        );
        editor.assertContextualCompletions("<*>",
                "local-nw<*>", "public-nw<*>"
        );
    }

    @Test
    void reconcileAZs() throws Exception {
        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server\n" +
                        "  azs: [z1, z2, z-bogus]\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("z-bogus|unknown 'AvailabilityZone'. Valid values are: [z1, z2, z3]");
    }

    @Test
    void contentAssistAZ() throws Exception {
        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server\n" +
                        "  azs:\n" +
                        "  - <*>"
        );
        editor.assertContextualCompletions("<*>",
                "z1<*>", "z2<*>", "z3<*>"
        );
    }

    @Test
    void reconcilePersistentDiskType() throws Exception {
        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server1\n" +
                        "  persistent_disk_type: default\n" +
                        "- name: my-server2\n" +
                        "  persistent_disk_type: bogus\n" +
                        "- name: my-server3\n" +
                        "  persistent_disk_type: large\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus|unknown 'DiskType'. Valid values are: [default, large]");
    }

    @Test
    void reconcilePersistentDiskType2() throws Exception {
        cloudConfigProvider.readWith(() ->
                "disk_types:\n" +
                        "- name: small-disk\n" +
                        "- name: large-disk\n"
        );
        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server1\n" +
                        "  persistent_disk_type: small-disk\n" +
                        "- name: my-server2\n" +
                        "  persistent_disk_type: bogus\n" +
                        "- name: my-server3\n" +
                        "  persistent_disk_type: large-disk\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus|unknown 'DiskType'. Valid values are: [small-disk, large-disk]");
    }

    @Test
    void reconcileVMExtensions() throws Exception {
        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server\n" +
                        "  vm_extensions:\n" +
                        "  - blah"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("blah|unknown 'VMExtension'. Valid values are: []");
    }

    @Test
    void reconcileVMExtensions2() throws Exception {
        cloudConfigProvider.readWith(() ->
                "vm_extensions:\n" +
                        "- name: pub-lbs\n" +
                        "  cloud_properties:\n" +
                        "    elbs: [main]"
        );
        Editor editor = harness.newEditor(
                "name: my-first-deployment\n" +
                        "instance_groups:\n" +
                        "- name: my-server\n" +
                        "  vm_extensions:\n" +
                        "  - pub-lbs\n" +
                        "  - bogus"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bogus|unknown 'VMExtension'. Valid values are: [pub-lbs]");
    }

    @Test
    void gotoReleaseDefinition() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  jobs:\n" +
                        "  - release: some-release\n" +
                        "- name: some-other-server\n" +
                        "  jobs:\n" +
                        "  - release: bogus-release\n" +
                        "releases:\n" +
                        "- name: some-release\n" +
                        "  url: https://release-hub.info/some-release.tar.gz?version=99.3.2\n" +
                        "  sha1: asddsfsd\n" +
                        "- name: other-release\n" +
                        "  url: https://release-hub.info/other-release.tar.gz?version=99.3.2\n" +
                        "  sha1: asddsfsd\n"
        );

        editor.assertGotoDefinition(editor.positionOf("some-release"),
                editor.rangeOf(
                        "releases:\n" +
                                "- name: some-release\n"
                ,
                        "some-release"
                ),
                editor.rangeOf("- release: some-release", "some-release")
        );
    }

    @Test
    void gotoStemcellDefinition() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups: \n" +
                        "- name: some-server\n" +
                        "  stemcell: default\n" +
                        "- name: windoze-server\n" +
                        "  stemcell: windoze\n" +
                        "- name: bad-server\n" +
                        "  stemcell: bogus-stemcell\n" +
                        "stemcells:\n" +
                        "- alias: default\n" +
                        "  os: ubuntu\n" +
                        "  version: 1346.77.1\n" +
                        "- alias: windoze\n" +
                        "  os: windows\n" +
                        "  version: 678.9.1\n"
        );

        editor.assertGotoDefinition(
                editor.positionOf("stemcell: windoze", "windoze"),
                editor.rangeOf("- alias: windoze", "windoze"),
                editor.rangeOf("stemcell: windoze", "windoze")
        );
    }

    @Test
    void bug_149769913() throws Exception {
        Editor editor = harness.newEditor(
                "releases:\n" +
                        "- name: learn-bosh\n" +
                        "  url: file:///blah\n" +
                        "  version:\n" +
                        "- name: blah-blah\n" +
                        "  version:"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);

        editor.assertProblems(
                "version:^^|cannot be blank",
                "version:^^|cannot be blank"
        );
    }

    @Test
    void snippet_toplevel() throws Exception {
        Editor editor = harness.newEditor("<*>");
        editor.assertCompletions(SNIPPET_COMPLETION,
                "name: $1\n" +
                        "releases:\n" +
                        "- name: $2\n" +
                        "  version: $3\n" +
                        "stemcells:\n" +
                        "- alias: $4\n" +
                        "  version: $5\n" +
                        "update:\n" +
                        "  canaries: $6\n" +
                        "  max_in_flight: $7\n" +
                        "  canary_watch_time: $8\n" +
                        "  update_watch_time: $9\n" +
                        "instance_groups:\n" +
                        "- name: $10\n" +
                        "  azs:\n" +
                        "  - $11\n" +
                        "  instances: $12\n" +
                        "  jobs:\n" +
                        "  - name: $13\n" +
                        "    release: $14\n" +
                        "  vm_type: $15\n" +
                        "  stemcell: $16\n" +
                        "  networks:\n" +
                        "  - name: $17<*>"
        );
    }

    @Test
    void snippet_disabledWhenPropertiesAlreadyDefined() throws Exception {
        Editor editor = harness.newEditor(
                "name:\n" +
                        "releases:\n" +
                        "stemcells:\n" +
                        "<*>"
        );

        editor.assertCompletionLabels(
                //"BoshDeploymentManifest Snippet",
                "instance_groups",
//				"releases Snippet",
//				"stemcells Snippet"
                "tags",
                "update",
                "variables",
                "- Stemcell Snippet",
                "- alias"
        );
    }

    @Test
    void snippet_nested_plain() throws Exception {
        Editor editor;
        //Plain exact completion
        editor = harness.newEditor(
                "instance_groups:\n" +
                        "- name: blah\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(c -> c.getLabel().equals("jobs"),
                "jo<*>"
        , // ------
                "jobs:\n" +
                        "  - name: $1\n" +
                        "    release: $2<*>"
        );
        editor.assertCompletionWithLabel("jobs",
                "instance_groups:\n" +
                        "- name: blah\n" +
                        "  jobs:\n" +
                        "  - name: $1\n" +
                        "    release: $2<*>"
        );
    }

    @Test
    void snippet_nested_indenting() throws Exception {
        Editor editor;
        //With extra indent:
        editor = harness.newEditor(
                "instance_groups:\n" +
                        "- name: blah\n" +
                        "<*>"
        );
        editor.assertCompletionWithLabel("→ jobs",
                "instance_groups:\n" +
                        "- name: blah\n" +
                        "  jobs:\n" +
                        "  - name: $1\n" +
                        "    release: $2<*>"
        );
        editor.assertContextualCompletions(c -> c.getLabel().equals("→ jobs"),
                "jo<*>"
        , // ------
                "  jobs:\n" +
                        "  - name: $1\n" +
                        "    release: $2<*>"
        );
    }

    @Test
    void snippet_dedented() throws Exception {
        Editor editor;
        editor = harness.newEditor(
                "name: \n" +
                        "variables:\n" +
                        "- name: voo\n" +
                        "  type: aaa\n" +
                        "<*>"
        );
        editor.assertContextualCompletions(DEDENTED_COMPLETION,
                "  <*>"
        , // ==>
                "instance_groups:\n" +
                        "- name: $1\n" +
                        "  azs:\n" +
                        "  - $2\n" +
                        "  instances: $3\n" +
                        "  jobs:\n" +
                        "  - name: $4\n" +
                        "    release: $5\n" +
                        "  vm_type: $6\n" +
                        "  stemcell: $7\n" +
                        "  networks:\n" +
                        "  - name: $8<*>"
        , //=========
                "releases:\n" +
                        "- name: $1\n" +
                        "  version: $2<*>"
        , //========
                "stemcells:\n" +
                        "- alias: $1\n" +
                        "  version: $2<*>"
        , //========
                "tags:\n" +
                        "  <*>"
        , //========
                "update:\n" +
                        "  canaries: $1\n" +
                        "  max_in_flight: $2\n" +
                        "  canary_watch_time: $3\n" +
                        "  update_watch_time: $4<*>"
        , //========
                "- name: $1\n" +
                        "  type: $2<*>"
        , //========
                "- name: <*>"
        );
    }

    @Test
    void relaxedCALessSpaces() throws Exception {
        Editor editor;
        editor = harness.newEditor(
                "name: \n" +
                        "variables:\n" +
                        "- name: voo\n" +
                        "  type: aaa\n" +
                        "<*>"
        );
        editor.assertContextualCompletions(DEDENTED_COMPLETION.and(SNIPPET_COMPLETION.negate()),
                "  <*>"
        , //==>
                "instance_groups:\n" +
                        "- name: $1\n" +
                        "  azs:\n" +
                        "  - $2\n" +
                        "  instances: $3\n" +
                        "  jobs:\n" +
                        "  - name: $4\n" +
                        "    release: $5\n" +
                        "  vm_type: $6\n" +
                        "  stemcell: $7\n" +
                        "  networks:\n" +
                        "  - name: $8<*>"
        , //----
                "releases:\n" +
                        "- name: $1\n" +
                        "  version: $2<*>"
        , //----
                "stemcells:\n" +
                        "- alias: $1\n" +
                        "  version: $2<*>"
        , //---
                "tags:\n" +
                        "  <*>"
        , //---
                "update:\n" +
                        "  canaries: $1\n" +
                        "  max_in_flight: $2\n" +
                        "  canary_watch_time: $3\n" +
                        "  update_watch_time: $4<*>"
        , //---
                "- name: <*>"
        );
    }

    @Test
    void relaxedCAmoreSpaces() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "instance_groups:\n" +
                        "- name: \n" +
                        "<*>"
        );
        editor.assertContextualCompletions(c -> c.getLabel().equals("→ jobs"),
                "jo<*>"
        , // ==>
                "  jobs:\n" +
                        "  - name: $1\n" +
                        "    release: $2<*>"
        );
    }

    @Test
    void keyCompletionThatNeedsANewline() throws Exception {
        Editor editor = harness.newEditor(
                "name: foo\n" +
                        "update: canwa<*>"
        );
        editor.assertCompletions(
                "name: foo\n" +
                        "update: \n" +
                        "  canary_watch_time: <*>"
        );
    }

    @Test
    void cloudconfigReconcile() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "bogus: bad\n" +
                        "azs:\n" +
                        "- name: z1\n" +
                        "  cloud_properties: {availability_zone: us-east-1a}\n" +
                        "- name: z2\n" +
                        "  cloud_properties: {availability_zone: us-east-1b}\n" +
                        "\n" +
                        "vm_types:\n" +
                        "- name: small\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: t2.micro\n" +
                        "    ephemeral_disk: {size: 3000, type: gp2}\n" +
                        "- name: medium\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: m3.medium\n" +
                        "    ephemeral_disk: {size: 30000, type: gp2}\n" +
                        "\n" +
                        "disk_types:\n" +
                        "- name: small\n" +
                        "  disk_size: 3000\n" +
                        "  cloud_properties: {type: gp2}\n" +
                        "- name: large\n" +
                        "  disk_size: 50_000\n" +
                        "  cloud_properties: {type: gp2}\n" +
                        "\n" +
                        "networks:\n" +
                        "- name: private\n" +
                        "  type: manual\n" +
                        "  subnets:\n" +
                        "  - range: 10.10.0.0/24\n" +
                        "    gateway: 10.10.0.1\n" +
                        "    az: z1\n" +
                        "    static: [10.10.0.62]\n" +
                        "    dns: [10.10.0.2]\n" +
                        "    cloud_properties: {subnet: subnet-f2744a86}\n" +
                        "  - range: 10.10.64.0/24\n" +
                        "    gateway: 10.10.64.1\n" +
                        "    az: z2\n" +
                        "    static: [10.10.64.121, 10.10.64.122]\n" +
                        "    dns: [10.10.0.2]\n" +
                        "    cloud_properties: {subnet: subnet-eb8bd3ad}\n" +
                        "- name: vip\n" +
                        "  type: vip\n" +
                        "\n" +
                        "compilation:\n" +
                        "  workers: 5\n" +
                        "  reuse_compilation_vms: true\n" +
                        "  az: z1\n" +
                        "  vm_type: medium\n" +
                        "  network: private\n"
        );

        editor.assertProblems("bogus|Unknown property");
    }

    @Test
    void cloudconfig_toplevel_hovers() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "azs: []\n" +
                        "vm_types: []\n" +
                        "vm_extensions: []\n" +
                        "disk_types: []\n" +
                        "networks: []\n" +
                        "compilation: {}\n"
        );
        editor.assertHoverContains("azs", "Specifies the AZs available to deployments.");
        editor.assertHoverContains("networks", "specifies a network configuration that jobs can reference.");
        editor.assertHoverContains("vm_types", "Specifies the [VM types](https://bosh.io/docs/terminology.html#vm-type) available to deployments.");
        editor.assertHoverContains("disk_types", "Specifies the [disk types](https://bosh.io/docs/terminology.html#disk-types) available to deployments.");
        editor.assertHoverContains("compilation", "A compilation definition allows to specify VM characteristics.");
        editor.assertHoverContains("vm_extensions", "Specifies the [VM extensions](https://bosh.io/docs/terminology.html#vm-extension) available to deployments.");
    }

    @Test
    void cloudconfig_compilation_subproperties() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "compilation:\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION.and(SNIPPET_COMPLETION.negate()),
                "<*>"
        ,
                "az: <*>"
        ,
                "network: <*>"
        ,
                "reuse_compilation_vms: <*>"
        ,
                "vm_type: <*>"
        ,
                "workers: <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION.and(SNIPPET_COMPLETION),
                "<*>"
        , // ==>
                "workers: $1\n" +
                        "  az: $2\n" +
                        "  vm_type: $3\n" +
                        "  network: $4<*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "compilation:\n" +
                        "  workers: not-int\n" +
                        "  reuse_compilation_vms: not-bool\n" +
                        "  az: z1\n" +
                        "  vm_type: default\n" +
                        "  network: private\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "not-int|NumberFormatException",
                "not-bool|unknown 'boolean'",
                "z1|unknown 'AZName'",
                "default|unknown 'VMTypeName'",
                "private|unknown 'NetworkName'"
        );
        editor.assertHoverContains("workers", "The maximum number of compilation VMs");
        editor.assertHoverContains("reuse_compilation_vms", "If `false`, BOSH creates a new compilation VM");
        editor.assertHoverContains("az", "Name of the AZ defined in AZs section");
        editor.assertHoverContains("vm_type", "Name of the VM type defined in VM types section");
        editor.assertHoverContains("network", "References a valid network name defined in the Networks block");
    }

    @Test
    void cloudconfig_vm_types_subproperties() throws Exception {
        Editor editor;

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_types:\n" +
                        "- <*>"
        );
        editor.assertContextualCompletions("<*>",
                "name: <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_types:\n" +
                        "- name: nice-vm\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>",
                "cloud_properties:\n    <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_types:\n" +
                        "- name: nice-vm\n" +
                        "- name: dup\n" +
                        "- name: dup\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "dup|Duplicate 'VMTypeName'",
                "dup|Duplicate 'VMTypeName'"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_types:\n" +
                        "- name: default\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: m1.small\n"
        );
        editor.assertHoverContains("name", "A unique name used to identify and reference the VM type");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties needed to create VMs");
    }

    @Test
    void cloudconfig_vm_types_reference() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_types:\n" +
                        "- name: small\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: t2.micro\n" +
                        "    ephemeral_disk: {size: 3000, type: gp2}\n" +
                        "- name: medium\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: m3.medium\n" +
                        "    ephemeral_disk: {size: 30000, type: gp2}\n" +
                        "compilation:\n" +
                        "  workers: 5\n" +
                        "  reuse_compilation_vms: true\n" +
                        "  az: z1\n" +
                        "  vm_type: <*>\n" +
                        "  network: private"
        );
        editor.assertContextualCompletions("<*>",
                "medium<*>",
                "small<*>"
        );
        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_types:\n" +
                        "- name: small\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: t2.micro\n" +
                        "    ephemeral_disk: {size: 3000, type: gp2}\n" +
                        "- name: medium\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: m3.medium\n" +
                        "    ephemeral_disk: {size: 30000, type: gp2}\n" +
                        "compilation:\n" +
                        "  workers: 5\n" +
                        "  reuse_compilation_vms: true\n" +
                        "  az: z1\n" +
                        "  vm_type: medium\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "z1|unknown 'AZName'"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_types:\n" +
                        "- name: small\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: t2.micro\n" +
                        "    ephemeral_disk: {size: 3000, type: gp2}\n" +
                        "- name: medium\n" +
                        "  cloud_properties:\n" +
                        "    instance_type: m3.medium\n" +
                        "    ephemeral_disk: {size: 30000, type: gp2}\n" +
                        "compilation:\n" +
                        "  workers: 5\n" +
                        "  reuse_compilation_vms: true\n" +
                        "  az: z1\n" +
                        "  vm_type: woot-vm\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "z1|unknown 'AZName'",
                "woot-vm|unknown 'VMTypeName'"
        );
    }

    @Test
    void cloudconfig_network_ca() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- <*>"
        );
        editor.assertCompletions(SNIPPET_COMPLETION.negate(),
                "networks:\n" +
                        "- name: <*>"
        );
        editor.assertCompletions(SNIPPET_COMPLETION,
                "networks:\n" +
                        "- name: $1\n" +
                        "  type: $2<*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  <*>"
        );
        editor.assertCompletions(PLAIN_COMPLETION,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION,
                "<*>"
        , // =>
                "dynamic<*>",
                "manual<*>",
                "vip<*>"
        );
    }

    @Test
    void cloudconfig_manual_network_ca() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: manual\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION,
                "<*>"
        , // =>
                "subnets:\n" + //snippet
                        "  - range: $1\n" +
                        "    gateway: $2<*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: manual\n" +
                        "  subnets:\n" +
                        "  - <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION.and(SNIPPET_COMPLETION.negate()),
                "<*>"
        , // ==>
                "az: <*>"
        ,
                "azs:\n" +
                        "    - <*>"
        ,
                "cloud_properties:\n" +
                        "      <*>"
        ,
                "dns:\n" +
                        "    - <*>"
        ,
                "gateway: <*>"
        ,
                "range: <*>"
        ,
                "reserved:\n" +
                        "    - <*>"
        ,
                "static:\n" +
                        "    - <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "azs:\n" +
                        "- name: zone-1\n" +
                        "- name: zone-2\n" +
                        "- name: zone-3\n" +
                        "networks:\n" +
                        "- name: foo\n" +
                        "  type: manual\n" +
                        "  subnets:\n" +
                        "  - az: <*>"
        );
        editor.assertContextualCompletions("<*>", "zone-1<*>", "zone-2<*>", "zone-3<*>");
        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "azs:\n" +
                        "- name: zone-1\n" +
                        "- name: zone-2\n" +
                        "- name: zone-3\n" +
                        "networks:\n" +
                        "- name: foo\n" +
                        "  type: manual\n" +
                        "  subnets:\n" +
                        "  - azs:\n" +
                        "    - <*>"
        );
        editor.assertContextualCompletions("<*>", "zone-1<*>", "zone-2<*>", "zone-3<*>");

    }

    @Test
    void cloudconfig_manual_network_hovers() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: my-network\n" +
                        "  type: manual\n" +
                        "\n" +
                        "  subnets:\n" +
                        "  - range:    10.10.0.0/24\n" +
                        "    gateway:  10.10.0.1\n" +
                        "    dns:      [10.10.0.2]\n" +
                        "\n" +
                        "    # IPs that will not be used for anything\n" +
                        "    reserved: [10.10.0.2-10.10.0.10]\n" +
                        "    az: z1\n" +
                        "\n" +
                        "    cloud_properties: {subnet: subnet-9be6c3f7}\n" +
                        "\n" +
                        "  - range:   10.10.1.0/24\n" +
                        "    gateway: 10.10.1.1\n" +
                        "    dns:     [10.10.1.2]\n" +
                        "\n" +
                        "    static: [10.10.1.11-10.10.1.20]\n" +
                        "\n" +
                        "    azs: [z2, z3]\n" +
                        "    cloud_properties: {subnet: subnet-9be6c6gh}"
        );
        editor.assertHoverContains("name", "Name used to reference this network configuration");
        editor.assertHoverContains("type", "The type of configuration. Should be one of `manual`, `dynamic` or `vip`");
        editor.assertHoverContains("subnets", "Lists subnets in this network");
        editor.assertHoverContains("range", "Subnet IP range that includes all IPs from this subnet");
        editor.assertHoverContains("gateway", "Subnet gateway IP");
        editor.assertHoverContains("dns", "DNS IP addresses for this subnet");
        editor.assertHoverContains("reserved", "Array of reserved IPs and/or IP ranges. BOSH does not assign IPs from this range to any VM");
        editor.assertHoverContains("static", "Array of static IPs and/or IP ranges.");
        editor.assertHoverContains("az", "AZ associated with this subnet");
        editor.assertHoverContains("azs", "List of AZs associated with this subnet");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties for the subnet");
    }

    @Test
    void cloudconfig_dynamic_network_ca() throws Exception {
        Editor editor;

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: dynamic\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION,
                "<*>"
        , // ==>
                "cloud_properties:\n" +
                        "    <*>"
        ,
                "dns:\n" +
                        "  - <*>"
        ,
                "subnets:\n" +
                        "  - <*>"
        );
    }

    @Test
    void cloudconfig_dynamic_network_mutex_props_reconcile() throws Exception {
        //There are essentially two alternate schemas for dynamic NW config. Because we have simply unified them into one (merged all properties)...
        // some properties can not be used together because they belong to different sub-schemas.
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: dynamic\n" +
                        "  dns: []\n" +
                        "  cloud_properties: {}\n" +
                        "  subnets: []"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "dns|Only one of 'dns' and 'subnets' should be defined",
                "cloud_properties|Only one of 'cloud_properties' and 'subnets' should be defined",
                "subnets|Only one of 'cloud_properties' and 'subnets' should be defined",
                "subnets|Only one of 'dns' and 'subnets' should be defined"
        );
    }

    @Test
    void cloudconfig_dynamic_network_hovers() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: dynamic\n" +
                        "  dns: []\n" +
                        "  cloud_properties: {}\n"
        );
        editor.assertHoverContains("name", "Name used to reference this network configuration");
        editor.assertHoverContains("type", "The type of configuration");
        editor.assertHoverContains("dns", "DNS IP addresses for this network");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties for the network");

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: my-network\n" +
                        "  type: dynamic\n" +
                        "  subnets:\n" +
                        "  - az: z1\n" +
                        "    dns: []\n" +
                        "    cloud_properties: {subnet: subnet-9be6c3f7}\n" +
                        "  - azs: [z2, z3]\n" +
                        "    cloud_properties: {subnet: subnet-9be6c384}"
        );
        editor.assertHoverContains("name", "Name used to reference this network configuration");
        editor.assertHoverContains("type", "The type of configuration");
        editor.assertHoverContains("subnets", "Lists subnets in this network");
        editor.assertHoverContains("dns", "DNS IP addresses for this subnet");
        editor.assertHoverContains("az", "AZ associated with this subnet");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties for the subnet");
        editor.assertHoverContains("azs", "List of AZs associated with this subnet");
    }

    @Test
    void cloudconfig_vip_network() throws Exception {
        Editor editor;

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: vip\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION,
                "<*>"
        , // ==>
                "cloud_properties:\n" +
                        "    <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: foo\n" +
                        "  type: vip\n" +
                        "  cloud_properties: {}"
        );
        editor.assertHoverContains("name", "Name used to reference this network configuration");
        editor.assertHoverContains("type", "The type of configuration");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties for the network");
    }

    @Test
    void cloudconfig_network_ref() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: private\n" +
                        "- name: vip\n" +
                        "\n" +
                        "compilation:\n" +
                        "  workers: 5\n" +
                        "  reuse_compilation_vms: true\n" +
                        "  az: z1\n" +
                        "  vm_type: medium\n" +
                        "  network: <*>"
        );
        editor.assertContextualCompletions("<*>", "private<*>", "vip<*>");

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "networks:\n" +
                        "- name: private\n" +
                        "  type: manual\n" +
                        "- name: vip\n" +
                        "  type: vip\n" +
                        "\n" +
                        "compilation:\n" +
                        "  workers: 5\n" +
                        "  reuse_compilation_vms: true\n" +
                        "  network: bad-network"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems("bad-network|unknown 'NetworkName'");
    }

    @Test
    void cloudconfig_azs() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "azs:\n" +
                        "- <*>"
        );
        editor.assertContextualCompletions("<*>",
                "name: <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "azs:\n" +
                        "- name: the-zone\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>",
                "cloud_properties:\n" +
                        "    <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "azs:\n" +
                        "- name: the-zone\n" +
                        "  cloud_properties: {}\n"
        );
        editor.assertHoverContains("name", "Name of an AZ within the Director");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties needed to associated with AZ");
    }

    @Test
    void cloudconfig_diskt_types() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "disk_types:\n" +
                        "- <*>"
        );
        editor.assertContextualCompletions("<*>",
                //snippet:
                  "name: $1\n" +
                        "  disk_size: $2<*>"
        ,
                //non-snippet:
                "name: <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "disk_types:\n" +
                        "- name: massive-disk\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>",
                "cloud_properties:\n    <*>",
                "disk_size: <*>"
        );

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "disk_types:\n" +
                        "- name: default\n" +
                        "  disk_size: 2\n" +
                        "  cloud_properties:\n" +
                        "    type: gp2\n"
        );
        editor.assertHoverContains("name", "A unique name used to identify and reference the disk type");
        editor.assertHoverContains("disk_size", "size in megabytes");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties needed to create disks");
    }

    @Test
    void cloudconfig_at_least_one_required() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "azs: []\n" +
                        "vm_types: []\n" +
                        "networks: []\n" +
                        "disk_types: []\n" +
                        "vm_extensions: []" //This one should be okay!
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(
                "[]|At least one 'AZ' is required",
                "[]|At least one 'VMType' is required",
                "[]|At least one 'Network' is required",
                "[]|At least one 'DiskType' is required"
        );
    }

    @Test
    void cloudconfig_vm_extensions() throws Exception {
        Editor editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_extensions:\n" +
                        "- name: pub-lbs\n" +
                        "  cloud_properties:\n" +
                        "    elbs: [main]\n"
        );
        editor.ignoreProblem(YamlSchemaProblems.MISSING_PROPERTY);
        editor.assertProblems(/*NONE*/);
        editor.assertHoverContains("name", "A unique name used to identify and reference the VM extension");
        editor.assertHoverContains("cloud_properties", "Describes any IaaS-specific properties needed to configure VMs");

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_extensions:\n" +
                        "- <*>"
        );
        editor.assertContextualCompletions("<*>", "name: <*>");

        editor = harness.newEditor(LanguageId.BOSH_CLOUD_CONFIG,
                "vm_extensions:\n" +
                        "- name: flubbergastly-vm\n" +
                        "  <*>"
        );
        editor.assertContextualCompletions(PLAIN_COMPLETION, "<*>",
                "cloud_properties:\n    <*>");
    }

    @Test
    void missingPropertiesQuickfix() throws Exception {
        Editor editor = harness.newEditor(
                "name: blah\n" +
                        "stemcells:\n" +
                        "- alias: ubuntu\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: 3421.11\n" +
                        "- alias: centos\n" +
                        "  os: centos-7\n" +
                        "  version: latest"
        );
        Diagnostic problem = editor.assertProblems("t|Properties [instance_groups, releases, update] are required").get(0);
        CodeAction quickfix = editor.assertCodeAction(problem);
        assertEquals("Add properties: [instance_groups, releases, update]", quickfix.getLabel());
        quickfix.perform();
        editor.assertText(
                "name: blah\n" +
                        "stemcells:\n" +
                        "- alias: ubuntu\n" +
                        "  os: ubuntu-trusty\n" +
                        "  version: 3421.11\n" +
                        "- alias: centos\n" +
                        "  os: centos-7\n" +
                        "  version: latest\n" +
                        "releases:\n" +
                        "- name: <*>\n" +
                        "  version: \n" +
                        "update:\n" +
                        "  canaries: \n" +
                        "  max_in_flight: \n" +
                        "  canary_watch_time: \n" +
                        "  update_watch_time: \n" +
                        "instance_groups:\n" +
                        "- name: \n" +
                        "  azs:\n" +
                        "  - \n" +
                        "  instances: \n" +
                        "  jobs:\n" +
                        "  - name: \n" +
                        "    release: \n" +
                        "  vm_type: \n" +
                        "  stemcell: \n" +
                        "  networks:\n" +
                        "  - name: "
        );
    }
}
