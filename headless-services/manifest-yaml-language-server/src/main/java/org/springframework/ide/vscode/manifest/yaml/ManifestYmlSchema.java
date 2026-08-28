/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;

import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.IntegerRange;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.ValueParsers;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.AbstractType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YAtomicType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YBeanType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YTypedPropertyImpl;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;
import org.springframework.ide.vscode.commons.yaml.schema.YamlSchema;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraints;
import org.yaml.snakeyaml.nodes.Node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public final class ManifestYmlSchema implements YamlSchema {

	private static final String HEALTH_CHECK_HTTP_ENDPOINT_PROP = "health-check-http-endpoint";
	private static final String HEALTH_CHECK_TYPE_PROP = "health-check-type";

	private final AbstractType TOPLEVEL_TYPE;
	private final YTypeUtil TYPE_UTIL;

	public final AbstractType t_route_string;
	private final YAtomicType t_application_name;

	private ImmutableList<YType> definitionTypes = null;
	public final YTypeFactory f;

	private static final Set<String> TOPLEVEL_EXCLUDED = ImmutableSet.of(
		"name", "host", "hosts", "routes", "docker"
	);

	@Override
	public IntegerRange expectedNumberOfDocuments() {
		return IntegerRange.exactly(1);
	}

	private void verify_heatth_check_http_end_point_constraint(DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) {
		YamlFileAST ast = dc.getAST();
		if (ast != null) {
			Node markerNode = YamlPathSegment.keyAt(HEALTH_CHECK_HTTP_ENDPOINT_PROP).traverseNode(node);
			if (markerNode != null) {
				String healthCheckType = getEffectiveHealthCheckType(ast, dc.getPath(), node);
				if (!"http".equals(healthCheckType)) {
					problems.accept(YamlSchemaProblems.problem(ManifestYamlSchemaProblemsTypes.IGNORED_PROPERTY,
							"This has no effect unless `"+HEALTH_CHECK_TYPE_PROP+"` is `http` (but it is currently set to `"+healthCheckType+"`)", markerNode));
				}
			}
		}
	}

	/**
	 * Determines the actual health-check-type that applies to a given node, taking into account
	 * inheritance from parent node, and default value.
	 */
	private String getEffectiveHealthCheckType(YamlFileAST ast, YamlPath path, Node node) {
		String explicit = NodeUtil.getScalarProperty(node, HEALTH_CHECK_TYPE_PROP);
		if (explicit != null) {
			return explicit;
		}
		if (path.size() > 2) {
			//Must consider inherited props!
			YamlPath parentPath = path.dropLast(2);
			Node parent = parentPath.traverseToNode(ast);
			String inherited = NodeUtil.getScalarProperty(parent, HEALTH_CHECK_TYPE_PROP);
			if (inherited != null) {
				return inherited;
			}
		}
		return "port";
	}

	public ManifestYmlSchema(ManifestYmlHintProviders providers) {
		Callable<Collection<YValueHint>> buildpackProvider = providers.getBuildpackProviders();
		Callable<Collection<YValueHint>> servicesProvider = providers.getServicesProvider();
		Callable<Collection<YValueHint>> domainsProvider = providers.getDomainsProvider();
		Callable<Collection<YValueHint>> stacksProvider = providers.getStacksProvider();


		f = new YTypeFactory();
		TYPE_UTIL = f.TYPE_UTIL;

		// define schema types
		TOPLEVEL_TYPE = f.ybean("Cloudfoundry Manifest");
		TOPLEVEL_TYPE.require(this::verify_heatth_check_http_end_point_constraint);

		AbstractType application = f.ybean("Application");
		application.require(this::verify_heatth_check_http_end_point_constraint);
		application.require(
				ManifestConstraints.mutuallyExclusive("routes", "domain", "domains", "host", "hosts", "no-hostname", "random-route"));

		YAtomicType t_path = f.yatomic("Path");

		YAtomicType t_buildpack = f.yatomic("Buildpack");
		//t_buildpack.require(Constraints.deprecateProperty((name) ->
		//   "Deprecated: Use `buildpacks` instead.", "buildpack"));
		if (buildpackProvider != null) {
			t_buildpack.setHintProvider(buildpackProvider);
		}

		YAtomicType t_stack = f.yatomic("Stack");
		if (stacksProvider != null) {
			t_stack.setHintProvider(stacksProvider);
			t_stack.parseWith(ManifestYmlValueParsers.fromCFValueHints(stacksProvider, t_stack, ManifestYamlSchemaProblemsTypes.UNKNOWN_STACK_PROBLEM));
		}

		YAtomicType t_domain = f.yatomic("Domain");
		if (domainsProvider != null) {
			t_domain.setHintProvider(domainsProvider);
			t_domain.parseWith(ManifestYmlValueParsers.fromCFValueHints(domainsProvider, t_domain, ManifestYamlSchemaProblemsTypes.UNKNOWN_DOMAIN_PROBLEM));
		}

		YAtomicType t_service = f.yatomic("Service");
		if (servicesProvider != null) {
			t_service.setHintProvider(servicesProvider);
			t_service.parseWith(new CFServicesValueParser(t_service.toString(),
					YTypeFactory.valuesFromHintProvider(servicesProvider)));
		}

		YAtomicType t_boolean = f.yenum("boolean", "true", "false");
		YAtomicType t_ne_string = f.yatomic("String");
		t_ne_string.parseWith(ValueParsers.NE_STRING);

		YAtomicType t_health_check_endpoint_string = f.yatomic("String");
		t_health_check_endpoint_string.parseWith(ManifestYmlValueParsers.healthCheckEndpointPath());

		t_application_name = f.yatomic("ApplicationName");
		t_application_name.parseWith(ValueParsers.NE_STRING);

		YType t_string = f.yatomic("String");

		t_route_string = f.yatomic("RouteUri")
			.parseWith(new RouteValueParser(YTypeFactory.valuesFromHintProvider(domainsProvider)))
			.setCustomContentAssistant(new RouteContentAssistant(domainsProvider, this));

		YBeanType route = f.ybean("Route");
		route.addProperty(f.yprop("route", t_route_string).isRequired(true));

		YAtomicType t_memory = f.yatomic("Memory");
		t_memory.addHints("256M", "512M", "1024M");
		t_memory.parseWith(ManifestYmlValueParsers.MEMORY);
		t_memory.sealHints();

		YAtomicType t_health_check_type = f.yenumBuilder("Health Check Type", "none", "process", "port", "http")
				.deprecateWithReplacement("none", "process")
				.build();

		YAtomicType t_strictly_pos_integer = f.yatomic("Strictly Positive Integer");
		t_strictly_pos_integer.parseWith(ValueParsers.integerAtLeast(1));

		YAtomicType t_pos_integer = f.yatomic("Positive Integer");
		t_pos_integer.parseWith(ManifestYmlValueParsers.POS_INTEGER);

		YType t_env = f.ymap(t_string, t_string);

		// define schema structure...
		TOPLEVEL_TYPE.addProperty(f.yprop("applications", f.yseq(application)));
		TOPLEVEL_TYPE.addProperty("inherit", t_string, descriptionFor("inherit"));

		AbstractType t_host = f.yatomic("Host").parseWith(ValueParsers.NE_STRING);

		YType t_docker = f.ybean("Docker", 
				f.yprop("image", t_ne_string).isRequired(true).setDescriptionProvider(descriptionFor("docker")),
				f.yprop("username", t_ne_string).setDescriptionProvider(descriptionFor("docker"))
		);
		YTypedPropertyImpl[] props = {
			f.yprop("buildpack", t_buildpack),
			//TODO: replace the above with the below to make 'buildpack' deprecated once we have proper support for `buildpacks` in cf push.
			//f.yprop("buildpack", t_buildpack).isDeprecated("Deprecated: Use `buildpacks` instead."),
			//Note: don't forget to also re-enable the test case called 'reconcileDeprecatedBuildpackWarning'.
			f.yprop("buildpacks", f.yseq(t_buildpack)),
			f.yprop("command", t_string),
			f.yprop("disk_quota", t_memory),
			f.yprop("docker", t_docker),
			f.yprop("domain", t_domain),
			f.yprop("domains", f.yseq(t_domain)),
			f.yprop("env", t_env),
			f.yprop("host", t_ne_string),
			f.yprop("hosts", f.yseq(t_host)),
			f.yprop("instances", t_pos_integer),
			f.yprop("memory", t_memory),
			f.yprop("name", t_application_name).isRequired(true),
			f.yprop("no-hostname", t_boolean),
			f.yprop("no-route", t_boolean),
			f.yprop("path", t_path),
			f.yprop("random-route", t_boolean),
			f.yprop("routes", f.yseq(route)),
			f.yprop("services", f.yseq(t_service)),
			f.yprop("stack", t_stack),
			f.yprop("timeout", t_pos_integer),

			f.yprop(HEALTH_CHECK_TYPE_PROP, t_health_check_type),
			f.yprop(HEALTH_CHECK_HTTP_ENDPOINT_PROP, t_health_check_endpoint_string)
		};

		for (YTypedPropertyImpl prop : props) {
			prop.setDescriptionProvider(descriptionFor(prop));
			if (!TOPLEVEL_EXCLUDED.contains(prop.getName())) {
				TOPLEVEL_TYPE.addProperty(prop.copy()
						.isDeprecated("Use of toplevel properties is deprecated: Instead, use Yaml anchors (&) and extend (<<) to define and re-use shared defaults")
				);
			}
			application.addProperty(prop);
		}
		application.require(Constraints.mutuallyExclusive("docker", "path"));
		application.require(Constraints.mutuallyExclusive("docker", "buildpack"));
		application.require(Constraints.mutuallyExclusive("docker", "buildpacks"));
	}

	private Renderable descriptionFor(String propName) {
		return Renderables.fromClasspath(this.getClass(), "/description-by-prop-name/"+propName);
	}

	private Renderable descriptionFor(YTypedPropertyImpl prop) {
		return descriptionFor(prop.getName());
	}

	@Override
	public AbstractType getTopLevelType() {
		return TOPLEVEL_TYPE;
	}

	@Override
	public YTypeUtil getTypeUtil() {
		return TYPE_UTIL;
	}

	public Collection<YType> getDefinitionTypes() {
		// These are the types of "interest" for symbol navigation.
		if (definitionTypes == null) {
			definitionTypes = ImmutableList.of(t_application_name);
		}
		return definitionTypes;
	}
}
