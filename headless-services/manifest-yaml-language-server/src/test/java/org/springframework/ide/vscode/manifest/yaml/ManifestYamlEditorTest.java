/*******************************************************************************
 * Copyright (c) 2016, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.ide.vscode.languageserver.testharness.Editor.PLAIN_COMPLETION;
import static org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness.assertDocumentation;

import java.io.IOException;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFBuildpack;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFDomain;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFServiceInstance;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFStack;
import org.springframework.ide.vscode.commons.cloudfoundry.client.ClientRequests;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.NoTargetsException;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;
import org.springframework.ide.vscode.manifest.yaml.bootiful.ManifestLanguageServerTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.collect.ImmutableList;

@ExtendWith(SpringExtension.class)
@ManifestLanguageServerTest
public class ManifestYamlEditorTest {

	@Autowired
	MockCloudfoundry cloudfoundry;

	@Autowired
	LanguageServerHarness harness;

	@BeforeEach
	public void initHarness() throws Exception {
		harness.intialize(null);
		System.setProperty("lsp.yaml.completions.errors.disable", "false"); //Yuck! Do we really need this??
	}

	@Test public void testReconcileCatchesParseError() throws Exception {
		Editor editor = harness.newEditor(
				"somemap: val\n"+
				"- sequence"
		);
		editor.assertProblems(
				"-|expected <block end>"
		);
	}

	@Test public void reconcileRunsOnDocumentOpenAndChange() throws Exception {
		Editor editor = harness.newEditor(
				"somemap: val\n"+
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
	public void reconcileMisSpelledPropertyNames() throws Exception {
		Editor editor;

		editor = harness.newEditor(
				"aplications:\n" +
				"  - buildpack: zbuildpack\n" +
				"    domain: zdomain\n" +
				"    name: foo"
		);
		editor.assertProblems("aplications|Unknown property");

		//mispelled or not allowed at toplevel
		editor = harness.newEditor(
				"name: foo\n" +
				"buildpeck: yahah\n" +
				"memory: 1G\n" +
				"memori: 1G\n"
		);
		editor.assertProblems(
				"name|Unknown property",
				"buildpeck|Unknown property",
				"memory|deprecated",
				"memori|Unknown property"
		);

		//mispelled or not allowed as nested
		editor = harness.newEditor(
				"applications:\n" +
				"- name: fine\n" +
				"  buildpeck: yahah\n" +
				"  memory: 1G\n" +
				"  memori: 1G\n" +
				"  applications: bad\n"
		);
		editor.assertProblems(
				"buildpeck|Unknown property",
				"memori|Unknown property",
				"applications|Unknown property"
		);
	}
	
	@Test public void zeroInstancesIsFine() throws Exception {
		//See: https://www.pivotaltracker.com/story/show/165839251
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: some-name\n" +
				"  instances: -1\n" +
				"- name: some-other-name\n" +
				"  instances: 0"
		);
		editor.assertProblems("-1|Value must be at least 0");
	}

	@Test
	public void reconcileStructuralProblems() throws Exception {
		Editor editor;

		//forgot the 'applications:' heading
		editor = harness.newEditor(
				"- name: foo"
		);
		editor.assertProblems(
				"- name: foo|Expecting a 'Map' but found a 'Sequence'"
		);

		//forgot to make the '-' after applications
		editor = harness.newEditor(
				"applications:\n" +
				"  name: foo"
		);
		editor.assertProblems(
				"name: foo|Expecting a 'Sequence' but found a 'Map'"
		);

		//Using a 'composite' element where a scalar type is expected
		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  memory:\n"+
				"  - bad sequence\n" +
				"  instances:\n" +
				"    bad: map\n"
		);
		editor.assertProblems(
				"- bad sequence|Expecting a 'Memory' but found a 'Sequence'",
				"bad: map|Expecting a 'Positive Integer' but found a 'Map'"
		);

		// Add a structural case for `buildpacks` (old `buildpack` was scalar, but `buildpacks` is sequence)
		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  buildpacks: bad scalar\n"
		);
		editor.assertProblems(
				"bad scalar|Expecting a 'Sequence' but found a 'Scalar'"
				);
	}
	
	@Test
	public void reconcileWithAnchor() throws Exception {
		Editor editor = harness.newEditor(
				"defaults: &defaults\n" + 
				"  buildpacks:\n" + 
				"    - staticfile_buildpack\n" + 
				"  memory: 1G\n" +
				"  bad: bogus\n" +
				"\n" + 
				"\n" + 
				"applications:\n" + 
				"- name: bigapp\n" + 
				"  <<: *defaults\n" + 
				"- name: smallapp\n" + 
				"  <<: *defaults\n" + 
				"  memory: 256M"
		);
		editor.assertProblems("bad|Unknown property");
	}

	@Test
	public void reconcileSimpleTypes() throws Exception {
		Editor editor;

		//check for 'format' errors:
		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  instances: not a number\n" +
				"  no-route: notBool\n"+
				"  memory: 1024\n" +
				"  disk_quota: 2048\n" +
				"  health-check-type: unhealthy"
		);
		editor.assertProblems(
				"not a number|NumberFormatException",
				"notBool|boolean",
				"1024|doesn't end with a valid unit of memory",
				"2048|doesn't end with a valid unit of memory",
				"unhealthy|Health Check Type"
		);

		//check for 'range' errors:
		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  instances: -3\n" +
				"  memory: -1024M\n" +
				"  disk_quota: -2048M\n"
		);
		editor.assertProblems(
				"-3|Value must be at least 0",
				"-1024M|Negative value is not allowed",
				"-2048M|Negative value is not allowed"
		);

		//check that correct values are indeed accepted
		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  instances: 2\n" +
				"  no-route: true\n"+
				"  memory: 1024M\n" +
				"  disk_quota: 2048MB\n"
		);
		editor.assertProblems(/*none*/);

		//check that correct values are indeed accepted
		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  instances: 2\n" +
				"  no-route: false\n" +
				"  memory: 1024m\n" +
				"  disk_quota: 2048mb\n"
		);
		editor.assertProblems(/*none*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  instances: 2\n" +
				"  memory: 1G\n" +
				"  disk_quota: 2g\n"
		);
		editor.assertProblems(/*none*/);
	}

	@Test
	public void noListIndent() throws Exception {
		Editor editor;
		editor = harness.newEditor("appl<*>");
		editor.assertCompletions(
				"applications:\n"+
				"- name: <*>"
		);

		editor = harness.newEditor("serv<*>");
		editor.assertCompletions(
				"services:\n"+
				"- <*>"
		);
	}
	
	@Test 
	public void toplevelPropertiesDeprecated() throws Exception {
		Editor editor = harness.newEditor(
			"applications: []\n" +
			"inherit: blah\n" +
			"name: sample-app\n" +
			"buildpack: some-pack\n" +
			"command: some-command\n" +
			"disk_quota: 1G\n" +
			"domain: some-domain\n" +
			"domains: []\n" +
			"env: {}\n" +
			"host: some-host\n" +
			"hosts: []\n" +
			"instances: 12\n" +
			"memory: 1G\n" +
			"no-hostname: true\n" +
			"no-route: true\n" +
			"path: some-path\n" +
			"random-route: true\n" +
			"routes: []\n" +
			"services: []\n" +
			"stack: linux\n" +
			"timeout: 100\n"
		);
		editor.ignoreProblem("UnknownDomainProblem");
		editor.ignoreProblem("UnknownStackProblem");
		editor.assertProblems(
				"name|Unknown",
				"buildpack|deprecated",
				"command|deprecated", 
				"disk_quota|deprecated",
				"domain|deprecated",
				"domains|deprecated",
				"env|deprecated",
				"host|Unknown",
				"hosts|Unknown",
				"instances|deprecated",
				"memory|deprecated",
				"no-hostname|deprecated",
				"no-route|deprecated",
				"path|deprecated",
				"random-route|deprecated",
				"routes|Unknown",
				"services|deprecated",
				"stack|deprecated",
				"timeout|deprecated"
		);
	}

	@Test
	public void toplevelCompletions() throws Exception {
		Editor editor;
		editor = harness.newEditor("<*>");
		editor.assertCompletions(
				"applications:\n"+
				"- name: <*>",
				// ---------------
				"inherit: <*>",
				// ----------------
				"buildpack: <*>",
				//-----------------
				"buildpacks:\n" + 
				"- <*>",
				//-----------------
				"command: <*>",
				//-----------------
				"disk_quota: <*>",
				//-----------------
				"domain: <*>", 
				//-----------------
				"domains:\n" + 
				"- <*>",
				//-----------------
				"env:\n" + 
				"  <*>", 
				//-----------------
				"health-check-http-endpoint: <*>",
				//-----------------
				"health-check-type: <*>", 
				//-----------------
				"instances: <*>",
				//-----------------
				"memory: <*>", 
				//-----------------
				"no-hostname: <*>", 
				//-----------------
				"no-route: <*>", 
				//-----------------
				"path: <*>",
				//-----------------
				"random-route: <*>",
				//-----------------
				"services:\n" + 
				"- <*>", 
				//-----------------
				"stack: <*>", 
				//-----------------
				"timeout: <*>" 
		);

		editor = harness.newEditor("ranro<*>");
		editor.assertCompletions(
				"random-route: <*>"
		);
	}

	@Test
	public void nestedCompletions() throws Exception {
		Editor editor;
		editor = harness.newEditor(
				"applications:\n" +
				"- <*>"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: <*>"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  <*>"
		);
		editor.assertCompletions(PLAIN_COMPLETION,
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  buildpack: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  buildpacks:\n"+
				"  - <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  command: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  disk_quota: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  docker:\n" +
				"    image: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  domain: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  domains:\n"+
				"  - <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  env:\n"+
				"    <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  health-check-http-endpoint: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  health-check-type: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  host: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  hosts:\n"+
				"  - <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  instances: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  memory: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  no-hostname: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  no-route: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  path: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  random-route: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n"+
				"  - route: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  services:\n"+
				"  - <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  stack: <*>",
				// ---------------
				"applications:\n" +
				"- name: foo\n" +
				"  timeout: <*>"
		);
	}

	@Test
	public void completionDetailsAndDocs() throws Exception {
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  build<*>"
		);
		editor.assertCompletionDetails("buildpack", "Buildpack", "If your application requires a custom buildpack");
	}

	@Test
	public void valueCompletions() throws Exception {
		assertCompletions("disk_quota: <*>",
				"disk_quota: 1024M<*>",
				"disk_quota: 256M<*>",
				"disk_quota: 512M<*>"
		);
		assertCompletions("memory: <*>",
				"memory: 1024M<*>",
				"memory: 256M<*>",
				"memory: 512M<*>"
		);
		assertCompletions("no-hostname: <*>",
				"no-hostname: false<*>",
				"no-hostname: true<*>"
		);
		assertCompletions("no-route: <*>",
				"no-route: false<*>",
				"no-route: true<*>"
		);
		assertCompletions("random-route: <*>",
				"random-route: false<*>",
				"random-route: true<*>"
		);

		assertCompletions("health-check-type: <*>",
				"health-check-type: http<*>",
//				"health-check-type: none<*>", Still valid, but not suggested because its deprecated
				"health-check-type: port<*>",
				"health-check-type: process<*>"
		);
	}

	@Test public void reconcileHealthCheckType() throws Exception {
		Editor editor;
		Diagnostic problem;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  health-check-type: none"
		);
		problem = editor.assertProblems("none|'none' is deprecated in favor of 'process'").get(0);
		assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  health-check-type: port"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  health-check-type: process"
		);
		editor.assertProblems(/*NONE*/);
	}

	@Test public void reconcileHealthHttpEndPointIgnoredWarning() throws Exception {
		Editor editor;
		Diagnostic problem;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: process\n" +
				"  health-check-http-endpoint: /health"
		);
		editor.assertProblems("health-check-http-endpoint|This has no effect unless `health-check-type` is `http` (but it is currently set to `process`)");

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-http-endpoint: /health"
		);
		problem = editor.assertProblems("health-check-http-endpoint|This has no effect unless `health-check-type` is `http` (but it is currently set to `port`)").get(0);
		assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"defaults: &defaults\n" +
				"  health-check-type: http\n" +
				"applications:\n" +
				"- name: my-app\n" +
				"  <<: *defaults\n" +
				"  health-check-type: process\n" +
				"  health-check-http-endpoint: /health"
		);
		editor.assertProblems("health-check-http-endpoint|This has no effect unless `health-check-type` is `http` (but it is currently set to `process`)");

		editor = harness.newEditor(
				"applications:\n" +
				"- name: sample-app\n" +
				"  health-check-http-endpoint: /health\n"
		);
		editor.assertProblems("health-check-http-endpoint|This has no effect unless `health-check-type` is `http` (but it is currently set to `port`)");

		editor = harness.newEditor(
				"applications:\n" +
				"- name: sample-app\n" +
				"  health-check-type: process\n" +
				"  health-check-http-endpoint: /health"
		);
		editor.assertProblems("health-check-http-endpoint|This has no effect unless `health-check-type` is `http` (but it is currently set to `process`)");
	}

	@Test public void reconcileHealthHttpEndpointValidation() throws Exception {
		Editor editor;
		Diagnostic problem;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health/additionalpath"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health/applog.txt"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health?check=true"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /?check=true"
		);
		editor.assertProblems(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint:"
		);
		problem = editor.assertProblems("|Path requires a value staring with '/'").get(0);
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: health"
		);
		problem = editor.assertProblems("health|Path must start with a '/'").get(0);
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: ?check=true"
		);
		problem = editor.assertProblems("?check=true|Path must start with a '/'").get(0);
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: health/additionalpath"
		);
		problem = editor.assertProblems("health/additionalpath|Path must start with a '/'").get(0);
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: https://health/additionalpath"
		);
		problem = editor.assertProblems("https://health/additionalpath|Path contains scheme").get(0);
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health/ additionalpath"
		);
		problem = editor.assertProblems("/health/ additionalpath|Illegal character in path").get(0);
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());
	}

	@Test public void reconcileRoutesWithNoHost() throws Exception {
		Editor editor;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  no-hostname: true\n" +
				"  routes:\n" +
				"  - route: myapp.org"
		);
		editor.ignoreProblem("UnknownDomainProblem");

		editor.assertProblems(
				"no-hostname|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [no-hostname]"
			);

		editor = harness.newEditor(
				"defaults: &defaults\n" +
				"  no-hostname: true\n" +
				"applications:\n" +
				"- name: my-app\n" +
				"  <<: *defaults\n" +
				"  routes:\n" +
				"  - route: myapp.org"
		);
		editor.ignoreProblem("UnknownDomainProblem");

		editor.assertProblems(
				"no-hostname|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [no-hostname]"
		);

		editor = harness.newEditor(
				"defaults: &defaults\n" +
				"  no-hostname: true\n" +
				"applications:\n" +
				"- name: my-app\n" +
				"  routes:\n" +
				"  - route: myapp.org\n" +
				"  <<: *defaults\n" +
				"  no-hostname: true\n"
		);
		editor.ignoreProblem("UnknownDomainProblem");

		editor.assertProblems(
				"routes|Property cannot co-exist with properties [no-hostname]",
				"no-hostname|Property cannot co-exist with property 'routes'"
		);

		editor = harness.newEditor(
				"defaults: &defaults\n" +
				"  no-hostname: true\n" +
				"applications:\n" +
				"- name: my-app\n" +
				"  <<: *defaults\n" +
				"  host: some-app\n" +
				"  routes:\n" +
				"  - route: myapp.org"
		);
		editor.ignoreProblem("UnknownDomainProblem");

		editor.assertProblems(
				"no-hostname|Property cannot co-exist with property 'routes'",
				"host|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [host, no-hostname]"
		);

		editor = harness.newEditor(
				"defaults: &defaults\n" +
				"  no-hostname: true\n" +
				"applications:\n" +
				"- name: my-app\n" +
				"  <<: *defaults\n" +
				"  routes:\n" +
				"  - route: myapp.org\n" +
				"- name: app2\n" +
				"  <<: *defaults\n" +
				"  routes:\n" +
				"  - route: my-route.org"
		);
		editor.ignoreProblem("UnknownDomainProblem");

		editor.assertProblems(
				"no-hostname|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [no-hostname]",
				"routes|Property cannot co-exist with properties [no-hostname]"
		);
	}

	@Test public void randomRoutesWithRoutesValidation() throws Exception {
		Editor editor;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: moriarty-app\n" +
				"  random-route: true\n" +
				"  routes:\n" +
				"  - route: tcp.local2.pcfdev.io:61001"
		);
		editor.ignoreProblem("UnknownDomainProblem");
		editor.assertProblems(
				"random-route|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [random-route]"
		);

		editor = harness.newEditor(
				"defaults: &defaults\n" +
				"  random-route: true\n" +
				"applications:\n" +
				"- name: moriarty-app\n" +
				"  <<: *defaults\n" +
				"  routes:\n" +
				"  - route: tcp.local2.pcfdev.io:61001"
		);
		editor.ignoreProblem("UnknownDomainProblem");
		editor.assertProblems(
				"random-route|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [random-route]"
		);

		editor = harness.newEditor(
				"defaults: &defaults\n" +
				"  random-route: true\n" +
				"applications:\n" +
				"- name: moriarty-app\n" +
				"  <<: *defaults\n" +
				"  routes:\n" +
				"  - route: tcp.local2.pcfdev.io:61001"
		);
		editor.ignoreProblem("UnknownDomainProblem");
		editor.assertProblems(
				"random-route|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [random-route]"
		);

	}

	@Test public void deprecatedHealthCheckTypeQuickfix() throws Exception {
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  health-check-type: none"
		);
		Diagnostic problem = editor.assertProblems("none|'none' is deprecated in favor of 'process'").get(0);
		assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
		CodeAction quickfix = editor.assertCodeAction(problem);
		assertEquals("Replace deprecated value 'none' by 'process'", quickfix.getLabel());
		quickfix.perform();

		editor.assertRawText(
				"applications:\n" +
				"- name: foo\n" +
				"  health-check-type: process"
		);
	}

	@Test
	public void reconcileDeprecatedBuildpackWarning() throws Exception {
		Editor editor;
		Diagnostic problem;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  buildpack: java_buildpack\n" +
				"  buildpacks:\n"+
				"  - java_buidpack\n"
		);

		editor.assertProblems(/*NONE*/); // no problems right now. But in the future...
		// TODO: this is what it should really do:
//		problem = editor.assertProblems(
//				"buildpack|Deprecated: Use `buildpacks` instead.")
//				.get(0);
//		assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
	}

	@Test
	public void reconcileBuildpacks() throws Exception {
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  buildpacks:\n" +
				"  - java_buildpack");

		editor.assertProblems(/* NONE */);
	}

	@Test
	public void hoverInfos() throws Exception {
		Editor editor = harness.newEditor(
			"memory: 1G\n" +
			"#comment\n" +
			"inherit: base-manifest.yml\n"+
			"applications:\n" +
			"- buildpack: zbuildpack\n" +
			"  buildpacks: []\n" +
			"  domain: zdomain\n" +
			"  name: foo\n" +
			"  command: java main.java\n" +
			"  disk_quota: 1024M\n" +
			"  domains:\n" +
			"  - pivotal.io\n" +
			"  - otherdomain.org\n" +
			"  env:\n" +
			"    RAILS_ENV: production\n" +
			"    RACK_ENV: production\n" +
			"  host: apppage\n" +
			"  hosts:\n" +
			"  - apppage2\n" +
			"  - appage3\n" +
			"  instances: 2\n" +
			"  no-hostname: true\n" +
			"  no-route: true\n" +
			"  path: somepath/app.jar\n" +
			"  random-route: true\n" +
			"  routes:\n" +
			"  - route: tcp-example.com:1234\n" +
			"  services:\n" +
			"  - instance_ABC\n" +
			"  - instance_XYZ\n" +
			"  stack: cflinuxfs2\n" +
			"  timeout: 80\n" +
			"  health-check-type: none\n" +
			"  health-check-http-endpoint: /health\n"
		);

		editor.assertIsHoverRegion("memory");
		editor.assertIsHoverRegion("inherit");
		editor.assertIsHoverRegion("applications");
		editor.assertIsHoverRegion("buildpack");
		editor.assertIsHoverRegion("domain");
		editor.assertIsHoverRegion("name");
		editor.assertIsHoverRegion("command");
		editor.assertIsHoverRegion("disk_quota");
		editor.assertIsHoverRegion("domains");
		editor.assertIsHoverRegion("env");
		editor.assertIsHoverRegion("host");
		editor.assertIsHoverRegion("hosts");
		editor.assertIsHoverRegion("instances");
		editor.assertIsHoverRegion("no-hostname");
		editor.assertIsHoverRegion("no-route");
		editor.assertIsHoverRegion("path");
		editor.assertIsHoverRegion("random-route");
		editor.assertIsHoverRegion("routes");
		editor.assertIsHoverRegion("services");
		editor.assertIsHoverRegion("stack");
		editor.assertIsHoverRegion("timeout");
		editor.assertIsHoverRegion("health-check-type");

		editor.assertHoverContains("memory", "Use the `memory` attribute to specify the memory limit");
		editor.assertHoverContains("1G", "Use the `memory` attribute to specify the memory limit");
		editor.assertHoverContains("inherit", "For example, every child of a parent manifest called `base-manifest.yml` begins like this");
		editor.assertHoverContains("buildpack", "use the `buildpack` attribute to specify its URL or name");
		editor.assertHoverContains("buildpacks", "custom buildpack, you can use the `buildpacks` attribute");
	    editor.assertHoverContains("name", "The `name` attribute is the only required attribute for an application in a manifest file");
	    editor.assertHoverContains("command", "On the command line, use the `-c` option to specify the custom start command as the following example shows");
	    editor.assertHoverContains("disk_quota", "Use the `disk_quota` attribute to allocate the disk space for your app instance");
	    editor.assertHoverContains("domain", "You can use the `domain` attribute when you want your application to be served");
	    editor.assertHoverContains("domains", "Use the `domains` attribute to provide multiple domains");
	    editor.assertHoverContains("env", "The `env` block consists of a heading, then one or more environment variable/value pairs");
	    editor.assertHoverContains("host", "Use the `host` attribute to provide a hostname, or subdomain, in the form of a string");
	    editor.assertHoverContains("hosts", "Use the `hosts` attribute to provide multiple hostnames, or subdomains");
	    editor.assertHoverContains("instances", "Use the `instances` attribute to specify the number of app instances that you want to start upon push");
	    editor.assertHoverContains("no-hostname", "By default, if you do not provide a hostname, the URL for the app takes the form of `APP-NAME.DOMAIN`");
	    editor.assertHoverContains("no-route", "You can use the `no-route` attribute with a value of `true` to prevent a route from being created for your application");
	    editor.assertHoverContains("path", "You can use the `path` attribute to tell Cloud Foundry where to find your application");
	    editor.assertHoverContains("random-route", "Use the `random-route` attribute to create a URL that includes the app name and random words");
	    editor.assertHoverContains("routes", "Each route for this app is created if it does not already exist");
	    editor.assertHoverContains("services", "The `services` block consists of a heading, then one or more service instance names");
	    editor.assertHoverContains("stack", "Use the `stack` attribute to specify which stack to deploy your application to.");
	    editor.assertHoverContains("timeout", "The `timeout` attribute defines the number of seconds Cloud Foundry allocates for starting your application");
	    editor.assertHoverContains("health-check-type", "Use the `health-check-type` attribute to");
	    editor.assertHoverContains("health-check-http-endpoint", "customize the endpoint for the `http`");
	}

	@Test
	public void noHoverInfos() throws Exception {
		Editor editor = harness.newEditor(
		    "#comment\n" +
			"applications:\n" +
			"- buildpack: zbuildpack\n" +
			"  name: foo\n" +
			"  domains:\n" +
			"  - pivotal.io\n" +
			"  - otherdomain.org\n"

		);
		editor.assertNoHover("comment");

		// May fail in the future if hover support is added, but if hover support is added in the future,
		// it is expected that these should start to fail, as right now they have no hover
		editor.assertNoHover("pivotal.io");
		editor.assertNoHover("otherdomain.org");
	}

	@Test public void stacksCompletion() throws Exception {
		List<CFStack> stacks = ImmutableList.of(
				mockStack("linux"), mockStack("windows")
		);
		when(cloudfoundry.client.getStacks()).thenReturn(stacks);
		Editor editor = harness.newEditor(
				"stack: <*>"
		);
		CompletionItem c = editor.assertCompletions(
				"stack: linux<*>",
				"stack: windows<*>"
		).get(0);

		assertDocumentation("an-org : a-space [test.io]", c);
	}

	@Test public void domainReconcile() throws Exception {
		List<CFDomain> domains = ImmutableList.of(mockDomain("one.com"), mockDomain("two.com"));
		when(cloudfoundry.client.getDomains()).thenReturn(domains);
		Editor editor;
		Diagnostic p;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: sample-app\n" +
				"  domain: bad.com"
		);
		p = editor.assertProblems("bad.com|unknown 'Domain'. Valid values are: [one.com, two.com]").get(0);
		assertEquals(DiagnosticSeverity.Warning, p.getSeverity());

		editor= harness.newEditor(
				"applications:\n" +
				"- name: sample-app\n" +
				"  domains:\n" +
				"  - one.com\n" +
				"  - bad.com\n" +
				"  - two.com"
		);
		editor.assertProblems("bad.com|unknown 'Domain'. Valid values are: [one.com, two.com]");
	}

	@Test public void stacksReconcile() throws Exception {
		List<CFStack> stacks = ImmutableList.of(
				mockStack("linux"), mockStack("windows")
		);
		when(cloudfoundry.client.getStacks()).thenReturn(stacks);
		{
			Editor editor = harness.newEditor(
					"applications:\n" +
					"- name: foo\n" +
					"  stack: android<*>"
			);
			Diagnostic p = editor.assertProblems("android|'android' is an unknown 'Stack'. Valid values are: [linux, windows]").get(0);
			assertEquals(DiagnosticSeverity.Warning, p.getSeverity());
		}

		{
			Editor editor = harness.newEditor(
					"applications:\n" +
					"- name: foo\n" +
					"  stack: <*>"
			);
			Diagnostic p = editor.assertProblems("|'Stack' cannot be blank").get(0);
			assertEquals(DiagnosticSeverity.Error, p.getSeverity());
		}

	}

	private CFStack mockStack(String name) {
		CFStack stack = Mockito.mock(CFStack.class);
		when(stack.getName()).thenReturn(name);
		return stack;
	}

	@Test
	public void reconcileDuplicateKeys() throws Exception {
		ImmutableList<CFDomain> domains = ImmutableList.of(mockDomain("pivotal.io"), mockDomain("otherdomain.org"));
		when(cloudfoundry.client.getDomains()).thenReturn(domains);
		Editor editor = harness.newEditor(
				"#comment\n" +
				"applications:\n" +
				"- buildpacks:\n" +
				"  - zbuildpack\n" +
				"  name: foo\n" +
				"  domains:\n" +
				"  - pivotal.io\n" +
				"  domains:\n" +
				"  - otherdomain.org\n"
		);
		editor.assertProblems(
				"domains|Duplicate key",
				"domains|Duplicate key"
		);
	}

	@Test public void PT_137299017_extra_space_with_completion() throws Exception {
		assertCompletions(
				"applications:\n" +
				"- name: foo\n" +
				"  random-route:<*>"
				, // == >
				"applications:\n" +
				"- name: foo\n" +
				"  random-route: false<*>"
				, // --
				"applications:\n" +
				"- name: foo\n" +
				"  random-route: true<*>"
		);
	}

	@Test public void PT_137722057_extra_space_with_completion() throws Exception {
		assertCompletions(
				"applications:\n" +
				"-<*>",
				// == =>
				"applications:\n" +
				"- name: <*>"
		);

		//Second example
		assertCompletions(
				"applications:\n" +
				"-<*>\n" +
				"- name: test"
				, // == >
				"applications:\n" +
				"- name: <*>\n" +
				"- name: test"
		);

	}

	@Test public void numberOfYamlDocumentsShouldBeExactlyOne() throws Exception {
		Editor editor;

		{
			//when the file is empty (there is no AST at all)
			editor = harness.newEditor("#Emptyfile");
			editor.assertProblems("#Emptyfile|'Cloudfoundry Manifest' must have at least some Yaml content");
		}

		{
			//when the file has too many documents... then highlight the '---' marker introducing the first document
			//exceeding the range.
			editor = harness.newEditor(
					"---\n" +
					"applications:\n"+
					"- name: foo\n" +
					"  bad-one: xx\n" +
					"---\n" +
					"applications:\n"+
					"- name: foo\n" +
					"  bad-two: xx"
			);
			editor.assertProblems(
				"bad-one|Unknown property", //should still reconcile the documents even thought there's too many of them!
				"---|'Cloudfoundry Manifest' should not have more than 1 Yaml Document",
				"bad-two|Unknown property" //should still reconcile the documents even thought there's too many of them!
			);
			//also check the location of the marker since there are two occurrences of '---' in the editor text.
			Diagnostic problem = editor.assertProblem("---");
			assertTrue(problem.getRange().getStart().getLine() > 1);
		}

		{
			// Also check that looking for the '---' isn't confused by extra whitespace
			editor = harness.newEditor(
					"---\n" +
					"applications:\n"+
					"- name: foo\n" +
					"  \n"+
					"---\n" +
					"   \n"+
					"applications:\n"+
					"- name: foo\n"
			);
			editor.assertProblems(
				"---|'Cloudfoundry Manifest' should not have more than 1 Yaml Document"
			);
			//also check the location of the marker since there are two occurrences of '---' in the editor text.
			Diagnostic problem = editor.assertProblem("---");
			assertTrue(problem.getRange().getStart().getLine() > 1);
		}

	}

	@Test public void namePropertyIsRequired() throws Exception {
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: this-is-good\n" +
				"- memory: 1G\n" +
				"- name:\n"
		);
		editor.assertProblems(
				"-^ memory: 1G|Property 'name' is required",
				"|should not be empty"
		);
	}

	@Test public void noReconcileErrorsWhenNoTargets() throws Exception {
		Editor editor;
		cloudfoundry.reset();
		when(cloudfoundry.defaultParamsProvider.getParams()).thenReturn(ImmutableList.of());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  buildpacks:\n  " +
				"  - bad-buildpack\n" +
				"  stack: blah\n" +
				"  domain: something-domain.com\n" +
				"  services:\n" +
				"  - bad-service\n" +
				"  bogus: bad" //a token error to make sure reconciler is actually running!
		);
		editor.assertProblems("bogus|Unknown property");

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo-foo\n" +
				"  buildpacks:\n  " +
				"  - bad-buildpack\n" +
				"  routes:\n" +
				"  - route: foo.blah/fooo\n"
		);
		editor.assertProblems(/*NONE*/);
	}

	@Test
	public void noReconcileErrorsWhenCFFactoryThrows() throws Exception {
		reset(cloudfoundry.factory);
		when(cloudfoundry.factory.getClient(any(), any())).thenThrow(new IOException("Can't create a client!"));
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  buildpacks: \n" +
				"  - bad-buildpack\n" +
				"  services:\n" +
				"  - bad-service\n" +
				"  bogus: bad" //a token error to make sure reconciler is actually running!
		);
		editor.assertProblems("bogus|Unknown property");

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo-foo\n" +
				"  buildpacks: \n" +
				"  - bad-buildpack\n" +
				"  routes:\n" +
				"  - route: foo.blah/fooo\n"
		);
		editor.assertProblems(/*NONE*/);
	}

	@Test
	public void noReconcileErrorsWhenClientThrows() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		when(cfClient.getBuildpacks()).thenThrow(new IOException("Can't get buildpacks"));
		when(cfClient.getServices()).thenThrow(new IOException("Can't get services"));
		when(cfClient.getStacks()).thenThrow(new IOException("Can't get stacks"));
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  buildpacks:\n  " +
				"  - bad-buildpack\n" +
				"  stack: bad-stack\n" +
				"  services:\n" +
				"  - bad-service\n" +
				"  bogus: bad" //a token error to make sure reconciler is actually running!
		);
		editor.assertProblems("bogus|Unknown property");
	}

	@Test
	public void reconcileCFService() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		CFServiceInstance service = Mockito.mock(CFServiceInstance.class);
		when(service.getName()).thenReturn("myservice");
		when(cfClient.getServices()).thenReturn(ImmutableList.of(service));
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  services:\n" +
				"  - myservice\n"

		);
		// Should have no problems
		editor.assertProblems(/*none*/);
	}

	@Test
	public void reconcileShowsWarningOnUnknownService() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		CFServiceInstance service = Mockito.mock(CFServiceInstance.class);
		when(service.getName()).thenReturn("myservice");
		when(cfClient.getServices()).thenReturn(ImmutableList.of(service));
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  services:\n" +
				"  - bad-service\n"

		);
		editor.assertProblems("bad-service|There is no service instance called");

		Diagnostic problem = editor.assertProblem("bad-service");

		assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
	}

	@Test
	public void reconcileShowsWarningOnNoService() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		when(cfClient.getServices()).thenReturn(ImmutableList.of());
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  services:\n" +
				"  - bad-service\n");
		editor.assertProblems("bad-service|There is no service instance called");

		Diagnostic problem = editor.assertProblem("bad-service");

		assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
	}

	@Test
	public void delayedConstraints() throws Exception {
		// This tests the two different types of delayed constraints:
		// Slow delayed constraints that require CF connection (services
		// and "faster" delayed constraints that check that 'routes' property
		// cannot exist with 'domain' and 'host'
		ClientRequests cfClient = cloudfoundry.client;
		when(cfClient.getServices()).thenReturn(ImmutableList.of());

		List<CFDomain> domains = ImmutableList.of(mockDomain("test.cfapps.io"));
		when(cloudfoundry.client.getDomains()).thenReturn(domains);
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
			    "  host: foosite\n" +
				"  domain: test.cfapps.io\n" +
				"  routes:\n" +
				"  - route: test.cfapps.io/path\n" +
				"  services:\n" +
				"  - bad-service\n");
		editor.assertProblems(
				// These are the "fast" delayed constraints
				"host|Property cannot co-exist with property 'routes'",
				"domain|Property cannot co-exist with property 'routes'",
				"routes|Property cannot co-exist with properties [domain, host]",
				// This is the "slow" delayed constraint
				"bad-service|There is no service instance called");
	}

	@Test
	public void servicesContentAssistShowErrorMessageWhenNotLoggedIn() throws Exception {
		reset(cloudfoundry.defaultParamsProvider);

		when(cloudfoundry.defaultParamsProvider.getParams()).thenThrow(new NoTargetsException("No Cloudfoundry Targets: Please login"));

		String textBefore =
				"applications:\n" +
				"- name: foo\n" +
				"  services:\n" +
				"  - <*>";
		Editor editor = harness.newEditor(
				textBefore
		);

		//Applying the single completion should do nothing in the editor:
		editor.assertCompletions(textBefore);

		//The message from the exception should appear in the 'doc string':
		editor.assertCompletionDetails("No Cloudfoundry Targets", "Error", "Please login");

	}

	@Test
	public void servicesContentAssistWhenNotLoggedIn_ErrorProposalsDisabled() throws Exception {
		System.setProperty("lsp.yaml.completions.errors.disable", "true");

		reset(cloudfoundry.defaultParamsProvider);

		when(cloudfoundry.defaultParamsProvider.getParams()).thenThrow(new NoTargetsException("No Cloudfoundry Targets: Please login"));

		String textBefore =
				"applications:\n" +
				"- name: foo\n" +
				"  services:\n" +
				"  - <*>";
		Editor editor = harness.newEditor(
				textBefore
		);

		//Applying the single completion should do nothing in the editor:
		assertEquals(0, editor.assertCompletions().size());
	}

	@Test
	public void servicesContentAssistShowErrorMessageWhenNotLoggedIn_nonEmptyQueryString() throws Exception {
		reset(cloudfoundry.defaultParamsProvider);

		when(cloudfoundry.defaultParamsProvider.getParams()).thenThrow(new NoTargetsException("No Cloudfoundry Targets: Please login"));

		String textBefore =
				"applications:\n" +
				"- name: foo\n" +
				"  services:\n" +
				"  - something<*>";
		Editor editor = harness.newEditor(
				textBefore
		);

		//Applying the single completion should do nothing in the editor:
		editor.assertCompletions(textBefore);

		//The message from the exception should appear in the 'doc string':
		CompletionItem completion = editor.assertCompletionDetails("No Cloudfoundry Targets", "Error", "Please login");
		//query string should match the 'filter text' otherwise vscode will filter the item and it will be gone!
		assertEquals("something", completion.getFilterText());
	}

	@Test
	public void serviceContentAssist() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		CFServiceInstance service = Mockito.mock(CFServiceInstance.class);
		when(service.getName()).thenReturn("mysql");
		when(service.getPlan()).thenReturn("medium");
		when(cfClient.getServices()).thenReturn(ImmutableList.of(service));

		CompletionItem completion = assertCompletions(
				"services:\n" +
				"  - <*>"
				, // == >
				"services:\n" +
				"  - mysql<*>"
		).get(0);
		assertEquals("mysql - medium", completion.getLabel());
		assertDocumentation("an-org : a-space [test.io]", completion);
	}

	@Test
	public void deprecatedBuildpackContentAssist() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		CFBuildpack buildPack = Mockito.mock(CFBuildpack.class);
		when(buildPack.getName()).thenReturn("java_buildpack");
		when(cfClient.getBuildpacks()).thenReturn(ImmutableList.of(buildPack));

		CompletionItem completion = assertCompletions("buildpack: <*>", "buildpack: java_buildpack<*>").get(0);
		assertEquals("java_buildpack", completion.getLabel());
		assertDocumentation("an-org : a-space [test.io]", completion);
	}

	@Test
	public void buildpacksContentAssist() throws Exception {
		// Test for newer `buildpacks` property
		// See: PT 162499688
		ClientRequests cfClient = cloudfoundry.client;
		CFBuildpack buildPack = Mockito.mock(CFBuildpack.class);
		when(buildPack.getName()).thenReturn("java_buildpack");
		when(cfClient.getBuildpacks()).thenReturn(ImmutableList.of(buildPack));

		CompletionItem completion = assertCompletions("buildpacks:\n  - <*>", "buildpacks:\n  - java_buildpack<*>").get(0);
		assertEquals("java_buildpack", completion.getLabel());
		assertDocumentation("an-org : a-space [test.io]", completion);
	}

	@Test
	public void buildbackContentAssistNoTargets() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;

		CFBuildpack buildPack = Mockito.mock(CFBuildpack.class);
		when(buildPack.getName()).thenReturn("java_buildpack");
		when(cfClient.getBuildpacks()).thenReturn(ImmutableList.of(buildPack));

		String title = "No targets";
		String description = "Use CLI to login";
		when(cloudfoundry.defaultParamsProvider.getParams()).thenThrow(new NoTargetsException(title + ": " + description));

		CompletionItem completion = assertCompletions("buildpack: <*>", "buildpack: <*>").get(0);
		assertEquals(title, completion.getLabel());
		assertDocumentation(description, completion);
	}

	@Test
	public void domainContentAssist() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		CFDomain domain = Mockito.mock(CFDomain.class);
		when(domain.getName()).thenReturn("cfapps.io");
		when(cfClient.getDomains()).thenReturn(ImmutableList.of(domain));

		CompletionItem completion = assertCompletions("domain: <*>", "domain: cfapps.io<*>").get(0);
		assertDocumentation("an-org : a-space [test.io]", completion);
	}

	@Test
	public void domainsContentAssist() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;

		CFDomain domain = Mockito.mock(CFDomain.class);
		when(domain.getName()).thenReturn("cfapps.io");
		when(cfClient.getDomains()).thenReturn(ImmutableList.of(domain));

		CompletionItem completion = assertCompletions(
				"domains:\n" +
				"- <*>"
				, // == =>
				"domains:\n" +
				"- cfapps.io<*>"
		).get(0);

		assertDocumentation("an-org : a-space [test.io]", completion);
	}

	@Test
	public void reconcileRouteFormat() throws Exception {
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: https://springsource.org\n");
		editor.assertProblems("https://springsource.org|is not a valid 'Route'");
		Diagnostic problem = editor.assertProblem("https://springsource.org");
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: spring source.org\n");
		editor.assertProblems("spring source.org|is not a valid 'Route'");
		problem = editor.assertProblem("spring source.org");
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: springsource.org:kuku\n");
		editor.assertProblems("springsource.org:kuku|is not a valid 'Route'");
		problem = editor.assertProblem("springsource.org:kuku");
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());


		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: springsource.org/kuku?p=23\n");
		editor.assertProblems("springsource.org/kuku?p=23|is not a valid 'Route'");
		problem = editor.assertProblem("springsource.org/kuku?p=23");
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: springsource.org:645788\n");
		editor.assertProblems("springsource.org:645788|is not a valid 'Route'");
		problem = editor.assertProblem("springsource.org:645788");
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());
	}

	@Test
	public void reconcileRoute_Advanced() throws Exception {
		ImmutableList<CFDomain> domains = ImmutableList.of(mockDomain("somedomain.com"));
		when(cloudfoundry.client.getDomains()).thenReturn(domains);
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: springsource.org:8765/path\n");
		editor.assertProblems("springsource.org:8765/path|Unable to determine type of route");
		Diagnostic problem = editor.assertProblem("springsource.org:8765/path");
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: host.springsource.org:66000\n");
		editor.assertProblems("66000|Invalid port");
		problem = editor.assertProblem("66000");
		assertEquals(DiagnosticSeverity.Error, problem.getSeverity());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: host.springsource.org\n");
		editor.assertProblems("springsource.org|Unknown 'Domain'. Valid domains are: [somedomain.com]");
		problem = editor.assertProblem("springsource.org");
		assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
	}

	private CFDomain mockDomain(String name) {
		CFDomain domain = mock(CFDomain.class);
		when(domain.getName()).thenReturn(name);
		return domain;
	}

	@Test
	public void reconcileRouteValidDomain() throws Exception {
		ClientRequests cfClient = cloudfoundry.client;
		CFDomain domain = Mockito.mock(CFDomain.class);
		when(domain.getName()).thenReturn("springsource.org");
		when(cfClient.getDomains()).thenReturn(ImmutableList.of(domain));
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n" +
				"  - route: host.springsource.org\n");
		editor.assertProblems();
	}

	@Test public void dashedContentAssistForServices() throws Exception {
		Editor editor;
		CFServiceInstance service = mock(CFServiceInstance.class);
		when(cloudfoundry.client.getServices()).thenReturn(ImmutableList.of(service));
		when(service.getName()).thenReturn("my-service");
		when(service.getPlan()).thenReturn("cheap-plan");

		editor = harness.newEditor(
				"services:\n"+
				"- blah\n"+
				"ser<*>"
		);
		editor.assertCompletionWithLabel((s) -> s.startsWith("- "),
				"services:\n"+
				"- blah\n"+
				"- my-service<*>"
		);

		editor = harness.newEditor(
				"services:\n"+
				"- blah\n"+
				"<*>"
		);
		editor.assertCompletionWithLabel((s) -> s.startsWith("- "),
				"services:\n"+
				"- blah\n"+
				"- my-service<*>"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  services:<*>\n"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: foo\n" +
				"  services: \n"+
				"  - my-service<*>\n"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  services: <*>\n"
		);
		editor.assertCompletionLabels("- my-service - cheap-plan");
		editor.assertCompletions(
				"applications:\n" +
				"- name: foo\n" +
				"  services: \n" +
				"  - my-service<*>\n"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  services: ser<*>\n"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: foo\n" +
				"  services: \n" +
				"  - my-service<*>\n"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  services: \n" +
				"  - blah\n" +
				"  <*>\n"
		);
		editor.assertCompletionWithLabel((s) -> s.startsWith("- "),
				"applications:\n" +
				"- name: foo\n" +
				"  services: \n" +
				"  - blah\n" +
				"  - my-service<*>\n"
		);
	}

	@Test public void noRelaxedValueCompletionsInListItemContexts() throws Exception {
		//See: https://www.pivotaltracker.com/story/show/144393355

		CFServiceInstance service = mock(CFServiceInstance.class);
		when(cloudfoundry.client.getServices()).thenReturn(ImmutableList.of(service));
		when(service.getName()).thenReturn("my-service");
		when(service.getPlan()).thenReturn("cheap-plan");

		Editor editor = harness.newEditor(
				"services:\n" +
				"- some-service\n" +
				"<*>"
		);
		editor.assertCompletions((c) -> c.getLabel().contains("my-service"),
				"services:\n" +
				"- some-service\n" +
				"- my-service<*>"
		);
	}

	@Test public void autoInsertRouteProperty() throws Exception {
		Editor editor;

		editor = harness.newEditor(
				"applications:\n" +
				"- name: foo\n" +
				"  ro<*>"
		);
		editor.assertCompletions(c -> c.getLabel().startsWith("routes"),
				"applications:\n" +
				"- name: foo\n" +
				"  routes:\n"+
				"  - route: <*>"
		);
	}

	@Test public void gotoSymbolInDocument() throws Exception {
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  routes:\n" +
				"  - route: myapp.org\n" +
				"- name: app2\n" +
				"  routes:\n" +
				"  - route: my-route.org"
		);

		editor.assertDocumentSymbols(
				"my-app",
				"app2"
		);
	}

	@Test public void contentAssistInsideRouteDomain() throws Exception {
		Editor editor;

		ImmutableList<CFDomain> domains = ImmutableList.of(
				mockDomain("cfapps.io"),
				mockDomain("dsyer.com")
		);
		when(cloudfoundry.client.getDomains()).thenReturn(domains);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route:<*>"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: cfapps.io<*>"
				, // == == == == == == ==
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: dsyer.com<*>"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: <*>"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: cfapps.io<*>"
				, // == == == == == == ==
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: dsyer.com<*>"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: dsyer.<*>"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: dsyer.com<*>"
				, // == == == == == == ==
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: dsyer.cfapps.io<*>"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.<*>"
		);
		CompletionItem c = editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.cfapps.io<*>"
				, // == == == == == == ==
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.dsyer.com<*>"
		).get(0);
		assertEquals("cfapps.io", c.getLabel());

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: foo.bar.<*>"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: foo.bar.cfapps.io<*>"
				, // == == == == == == ==
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: foo.bar.dsyer.com<*>"
		);

		/// no content assist inside of path or port section of route:

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: foo.bar.com/<*>"
		);
		editor.assertCompletions(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: foo.bar.com:<*>"
		);
		editor.assertCompletions(/*NONE*/);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: foo.bar.com:7777/blah<*>"
		);
		editor.assertCompletions(/*NONE*/);

		// Martin's most fancy example:
		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.cf<*>pps.io/superpath\n" +
				"  memory: 1024M\n"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.cfapps.io<*>/superpath\n" +
				"  memory: 1024M\n"
		);

		//Kris's variants of Martin's example:
		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.ds<*>pps.io/superpath\n" +
				"  memory: 1024M\n"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.dsyer.com<*>/superpath\n" +
				"  memory: 1024M\n"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.ds<*>pps.io:8888/superpath\n" +
				"  memory: 1024M\n"
		);
		editor.assertCompletions(
				"applications:\n" +
				"- name: test\n" +
				"  routes:\n" +
				"  - route: test.dsyer.com<*>:8888/superpath\n" +
				"  memory: 1024M\n"
		);

	}

	@Test
	public void dockerAttributesValidation() throws Exception {
		Editor editor;
		
		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  docker:\n" +
				"    image: docker-image-repository/docker-image-name\n" +
				"    bogus: bad"
		);
		editor.assertProblems("bogus|Unknown");

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  docker:\n" +
				"    username: myself\n"
		);
		editor.assertProblems(
				"docker|'image' is required"
		);
		
		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  buildpacks:\n" +
				"  - java-buildpack\n" +
				"  docker:\n" +
				"    image: somewhere/someimage\n"
		);
		editor.assertProblems(
				"buildpacks|Only one of 'docker' and 'buildpacks'",
				"docker|Only one of 'docker' and 'buildpacks'"
		);

		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  buildpack: java-buildpack\n" +
				"  docker:\n" +
				"    image: somewhere/someimage\n"
		);
		editor.assertProblems(
				"buildpack|Only one of 'docker' and 'buildpack'",
				"docker|Only one of 'docker' and 'buildpack'"
		);
		
		editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  path: /somehere/in/filesystem\n" +
				"  docker:\n" +
				"    image: somewhere/someimage\n"
		);
		editor.assertProblems(
				"path|Only one of 'docker' and 'path'",
				"docker|Only one of 'docker' and 'path'"
		);
	}

	@Test
	public void dockerAttributesHovers() throws Exception {
		Editor editor = harness.newEditor(
				"applications:\n" +
				"- name: my-app\n" +
				"  docker:\n" +
				"    image: docker-image-repository/docker-image-name\n" +
				"    username: myself"
		);
		editor.assertHoverContains("docker", "If your app is contained in a Docker image");
		editor.assertHoverContains("image", "Docker image");
		editor.assertHoverContains("username", "If your app is contained in a Docker image");
	}

	//////////////////////////////////////////////////////////////////////////////

	private List<CompletionItem> assertCompletions(String textBefore, String... textAfter) throws Exception {
		Editor editor = harness.newEditor(textBefore);
		return editor.assertCompletions(textAfter);
	}

}
