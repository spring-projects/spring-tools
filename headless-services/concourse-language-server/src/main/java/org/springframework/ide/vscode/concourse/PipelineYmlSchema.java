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
package org.springframework.ide.vscode.concourse;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.SymbolKind;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.MimeTypes;
import org.springframework.ide.vscode.commons.util.PartialCollection;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.ValueParsers;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlAstCache;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaValueParsers;
import org.springframework.ide.vscode.commons.yaml.reconcile.TypeBasedYamlHierarchicalSymbolHandler.HierarchicalDefType;
import org.springframework.ide.vscode.commons.yaml.schema.BasicYValueHint;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.AbstractType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YAtomicType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YBeanType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YBeanUnionType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YSeqType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YTypedPropertyImpl;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;
import org.springframework.ide.vscode.commons.yaml.schema.YamlSchema;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraint;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraints;
import org.springframework.ide.vscode.concourse.ConcourseModel.ResourceModel;
import org.springframework.ide.vscode.concourse.ConcourseModel.StepModel;
import org.springframework.ide.vscode.concourse.github.GithubInfoProvider;
import org.springframework.ide.vscode.concourse.github.GithubRepoContentAssistant;
import org.springframework.ide.vscode.concourse.github.GithubValueParsers;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;

import com.google.common.collect.ImmutableList;

/**
 * @author Kris De Volder
 * @author LukeBalizet
 */
public class PipelineYmlSchema implements YamlSchema {

	//TODO: the infos for composing this should probably be integrated somehow in the ResourceTypeRegistry so
	// we only have a list of built-in resource types in a single place.
	public static final YValueHint[] BUILT_IN_RESOURCE_TYPES = {
			hint("git", "The 'git' resource can pull and push to git repositories."),
			hint("hg", "The 'hg' resource can pull and push to Mercurial repositories."),
			hint("time",  "The 'time' resource can start jobs on a schedule or timestamp outputs."),
			hint("s3", "The 's3' resource can fetch from and upload to S3 buckets."),
			hint("archive", "The 'archive' resource can fetch and extract .tar.gz archives."),
			hint("semver", "The 'semver' resource can set or bump version numbers."),
			hint("github-release", "The 'github-release' resource can fetch and publish versioned GitHub resources."),
			hint("docker-image", "The 'docker-image' resource can fetch, build, and push Docker images."),
			hint("registry-image", "Supports checking, fetching, and pushing of images to Docker registries."),
			hint("tracker", "The 'tracker' resource can deliver stories and bugs on Pivotal Tracker."),
			hint("pool", "The 'pool' resource allows you to configure how to serialize use of an external system. "
					+ "This lets you prevent test interference or overwork on shared systems."),
			hint("cf", "The cf resource can deploy an application to Cloud Foundry."),
			hint("bosh-io-release", "The bosh-io-release resource can track and fetch new BOSH releases from bosh.io."),
			hint("bosh-io-stemcell", "The bosh-io-stemcell resource can track and fetch new BOSH stemcells from bosh.io."),
			hint("bosh-deployment", "The bosh-deployment resource can deploy BOSH stemcells and releases."),
			hint("vagrant-cloud", "The vagrant-cloud resource can fetch and publish Vagrant boxes to Atlas.")
	};

	private final AbstractType TOPLEVEL_TYPE;
	private final YTypeUtil TYPE_UTIL;

	public final YTypeFactory f = new YTypeFactory();
	public final YType t_string = f.yatomic("String");
	public final YType t_ne_string = f.yatomic("String")
			.parseWith(ValueParsers.NE_STRING);
	public final YType t_identifier = f.yatomic("Identifier")
			.parseWith(ConcourseValueParsers.IDENTIFIER);
	public final YType t_opt_string = f.yatomic("String")
			.parseWith(YamlSchemaValueParsers.OPT_STRING);

	public final YType t_strings = f.yseq(t_string);
	public final YType t_pair = f.ybean("NameValuePair",
			f.yprop("name", t_string),
			f.yprop("value", t_string)
	);
	public final YType t_pair_list = f.yseq(t_pair);

	public final YAtomicType t_boolean = f.yenum("boolean", "true", "false");
	public final YType t_any = f.yany("Object");
	public final YType t_params = f.ymap(t_string, t_any);
	public final YType t_string_params = f.ymap(t_string, t_string);
	public final YType t_resource_version = f.yunion("ResourceVersion",
			f.yenum("ResourceVersionString", "latest", "every"),
			t_string_params
	);
	public final YType t_pos_integer = f.yatomic("Positive Integer")
			.parseWith(ValueParsers.POS_INTEGER);
	public final YType t_strictly_pos_integer = f.yatomic("Strictly Positive Integer")
			.parseWith(ValueParsers.integerAtLeast(1));

	public final AbstractType t_resource_name;
	public final YAtomicType t_maybe_resource_name;
	public final AbstractType t_job_name;
	public final YAtomicType t_resource_type_name;
	public final YType t_mime_type = f.yatomic("MimeType")
			.parseWith(ValueParsers.NE_STRING)
			.addHints(MimeTypes.getKnownMimeTypes());

	public final YType t_duration = f.yatomic("Duration")
			.parseWith(ConcourseValueParsers.DURATION);
	public final YType t_duration_or_never = f.yatomic("DurationOrNever")
			.parseWith(ConcourseValueParsers.DURATION)
			.alsoAccept("never")
			.addHints("never");
	public final YType t_time_of_day = f.yatomic("TimeOfDay")
			.parseWith(ConcourseValueParsers.TIME_OF_DAY);
	public final YType t_location = f.yatomic("Location")
			//Note: we could have used f.yenum here too. But it saves memory if we don't keep the large set of ValueHints in memory.
			// That's why we attach custom hint provider and parser here that do essentially the same thing.
			.setHintProvider(() -> {
				return ZoneId.getAvailableZoneIds().stream()
				.map(BasicYValueHint::new)
				.collect(Collectors.toList());
			})
			.parseWith(ValueParser.of((zoneId) -> {
				if (!ZoneId.getAvailableZoneIds().contains(zoneId)) {
					throw new ValueParseException("Unknown 'Location'. See https://en.wikipedia.org/wiki/List_of_tz_database_time_zones");
				}
				return zoneId;
			}));

	public final AbstractType task;

	private final ResourceTypeRegistry resourceTypes = new ResourceTypeRegistry();

	private final ConcourseModel models;
	private final YamlAstCache asts;

	public final YType t_semver = f.yatomic("Semver")
			.parseWith(ValueParsers.NE_STRING); //TODO: use real semver parser.

	public final YType t_aws_region = f.yenum("AWSRegion",
			//See https://docs.aws.amazon.com/general/latest/gr/rande.html
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
	);

	public final YType t_day = f.yenum("Day",
			//See https://github.com/concourse/time-resource#source-configuration
			"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
	);

	private List<YType> definitionTypes = new ArrayList<>();
	private List<HierarchicalDefType> hierarchicDefinitions;

	private GithubInfoProvider github;

	public PipelineYmlSchema(ConcourseModel models, GithubInfoProvider github) {
		this.github = github;
		this.models = models;
		this.asts = models.getAstCache();
		models.setResourceTypeRegistry(resourceTypes);
		TYPE_UTIL = f.TYPE_UTIL;

		// define schema types
		TOPLEVEL_TYPE = f.ybean("Pipeline");

		t_resource_type_name = f.yenumFromHints("ResourceType Name",
				(dc) -> (parseString, validValues) ->  {
					return "The '"+parseString+"' Resource Type does not exist. Existing types: "+validValues;
				},
				(DynamicSchemaContext dc) -> {
					return PartialCollection.compute(() -> models.getResourceTypeNameHints(dc, true));
				}
		);

		t_resource_name = f.yenum("Resource Name",
				(parseString, validValues) ->  {
					return "The '"+parseString+"' resource does not exist. Existing resources: "+validValues;
				},
				(DynamicSchemaContext dc) -> {
					return (models.getResourceNames(dc));
				}
		);
		t_maybe_resource_name = f.yatomic("ResourceName | TaskOutput");
		t_maybe_resource_name.setHintProvider((DynamicSchemaContext dc) -> {
			//Putting the Callable into a local variable is strange, but the compiler doesn't like it if
			// we return it directly. Too much complexity for Java type-inference?
			PartialCollection<YValueHint> hints = PartialCollection.compute(() -> YTypeFactory.hints(models.getResourceNames(dc)));
			return hints;
		});
		t_maybe_resource_name.parseWith(ValueParsers.NE_STRING);

		t_job_name = f.yenum("Job Name",
				(parseString, validValues) ->  {
					return "The '"+parseString+"' Job does not exist. Existing jobs: "+validValues;
				},
				(DynamicSchemaContext dc) -> {
					return models.getJobNames(dc);
				}
		).require(models::passedJobHasInteractionWithResource);
		AbstractType t_job_name_pattern = f.yGlobEnum("Job Name Pattern",
				(parseString, validValues) ->  {
					return "The '"+parseString+"' Job does not match any existing job: "+validValues;
				},
				(DynamicSchemaContext dc) -> {
					return models.getJobNames(dc);
				}
		);

		YAtomicType t_resource_name_def = f.yatomic("Resource Name");
		t_resource_name_def.parseWith(ConcourseValueParsers.resourceNameDef(models));
		t_resource_name_def.require(models.isUsed(t_resource_name, "Resource"));
		YAtomicType jobNameDef = f.yatomic("Job Name");
		jobNameDef.parseWith(ConcourseValueParsers.jobNameDef(models));
		YAtomicType resourceTypeNameDef = f.yatomic("ResourceType Name");
		resourceTypeNameDef.parseWith(ConcourseValueParsers.resourceTypeNameDef(models));

		YType resourceSource = f.contextAware("ResourceSource", (dc) ->
			resourceTypes.getSourceType(getResourceTypeTag(models, dc))
		);

		AbstractType t_resource = f.ybean("Resource");
		addProp(t_resource, "name", t_resource_name_def).isPrimary(true);
		addProp(t_resource, "type", t_resource_type_name).isRequired(true);
		addProp(t_resource, "source", resourceSource);
		addProp(t_resource, "check_every", t_duration_or_never);
		addProp(t_resource, "tags", t_strings);
		addProp(t_resource, "webhook_token", t_ne_string);
		addProp(t_resource, "icon", t_ne_string);
		addProp(t_resource, "old_name", t_ne_string);

		AbstractType t_image_resource = f.ybean("ImageResource");
		{
			Map<String, YTypedProperty> resourceProperties = t_resource.getPropertiesMap();
			addProp(t_image_resource, "type", t_resource_type_name).isRequired(true);
			t_image_resource.addProperty(resourceProperties.get("source"));
			addProp(t_image_resource, "params", t_params);

			//TODO: make ImageResourceParams dynamic based on resource type. Somewhat like below, but that code isn't exactly
			// right (yet :-)
//			addProp(t_image_resource, "params", f.contextAware("ImageResourceParams", (dc) ->
//				resourceTypes.getInParamsType(getImageResourceType(models, dc))
//			));
			addProp(t_image_resource, "version", t_resource_version);
		}

		YAtomicType t_platform = f.yenum("Platform", "windows", "linux", "darwin");
		t_platform.parseWith(ValueParsers.NE_STRING); //no errors because in theory platform are just strings.

		AbstractType t_input = f.ybean("TaskInput");
		addProp(t_input, "name", t_ne_string).isPrimary(true);
		addProp(t_input, "path", t_opt_string);
		addProp(t_input, "optional", t_boolean);

		AbstractType t_output = f.ybean("TaskOutput");
		addProp(t_output, "name", t_ne_string).isPrimary(true);
		addProp(t_output, "path", t_opt_string);

		AbstractType t_command = f.ybean("Command");
		addProp(t_command, "path", t_ne_string).isRequired(true);
		addProp(t_command, "args", t_strings);
		addProp(t_command, "dir", t_ne_string);
		addProp(t_command, "user", t_string);

		YBeanType cache = f.ybean("TaskCache");
		addProp(cache, "path", t_ne_string).isRequired(true);

		task = f.ybean("TaskConfig");
		addProp(task, "platform", t_platform).isRequired(true);
		addProp(task, "image_resource", t_image_resource);
		addProp(task, "rootfs_uri", t_ne_string);
		addProp(task, "image", t_ne_string).isDeprecated("The 'image' property in 'TaskConfig' is renamed to 'rootfs_uri' in Concourse 3.0");
		addProp(task, "inputs", f.yseq(t_input));
		addProp(task, "caches", f.yseq(cache));
		addProp(task, "outputs", f.yseq(t_output));
		addProp(task, "run", t_command).isRequired(true);
		addProp(task, "params", t_params);
		task.require(Constraints.schemaContextAware((DynamicSchemaContext dc) -> {
			LanguageId languageId = dc.getDocument().getLanguageId();
			if (LanguageId.CONCOURSE_PIPELINE.equals(languageId)) {
				Node parentImageDef = models.getParentPropertyNode("image", dc);
				if (parentImageDef==null) {
					return Constraints.requireOneOf("image_resource", "rootfs_uri", "image");
				} else {
					return Constraints.deprecated((name) ->
								"Deprecated: This attribute in the task config will be ignored! "+
								"The 'image' attribute on the task itself takes precedence.",
							"image_resource", "rootfs_uri", "image"
					);
				}
			} else {
				return Constraints.requireAtMostOneOf("image_resource", "rootfs_uri", "image");
			}
		}));

		AbstractType t_put_get_name = f.contextAware("Name", (dc) -> {
			if (models.getParentPropertyNode("resource", dc)!=null) {
				return null;
			} else {
				return t_resource_name;
			}
		})
		.treatAsAtomic()
		.parseWith(ValueParsers.NE_STRING);

		YBeanType getStep = f.ybean("GetStep");
		addProp(getStep, "get", t_put_get_name);
		addProp(getStep, "resource", t_resource_name);
		addProp(getStep, "version", t_resource_version);
		addProp(getStep, "passed", f.yseq(t_job_name));
		addProp(getStep, "params", f.contextAware("GetParams", (dc) ->
			resourceTypes.getInParamsType(getResourceType("get", models, dc))
		));
		addProp(getStep, "trigger", t_boolean);

		YBeanType putStep = f.ybean("PutStep");
		addProp(putStep, "put", t_put_get_name);
		addProp(putStep, "resource", t_resource_name);
		addProp(putStep, "inputs", f.yunion("PutStepInputs", t_strings, f.yenum("PutStepInputsAllOrDetect", "all", "detect")));
		addProp(putStep, "params", f.contextAware("PutParams", (dc) ->
			resourceTypes.getOutParamsType(getResourceType("put", models, dc))
		));
		addProp(putStep, "get_params", f.contextAware("GetParams", (dc) ->
			resourceTypes.getInParamsType(getResourceType("put", models, dc))
		));
		addProp(putStep, "no_get", t_boolean);
		putStep.require((DynamicSchemaContext dc, Node parent, Node _map, YType type, IProblemCollector problems) -> {
			if (_map instanceof MappingNode) {
				MappingNode map = (MappingNode) _map;
				StepModel step = models.newStep(map);
				String resourceName = step.getResourceName();
				if (resourceName!=null) {
					ResourceModel resource = models.getResource(dc.getDocument(), resourceName);
					if (resource!=null) {
						if ("git".equals(resource.getType()) && !resource.hasSourceProperty("branch")) {
							problems.accept(YamlSchemaProblems.schemaProblem(
									"Resource of type 'git' is used in a 'put' step, so it should define 'branch' attribute in its 'source', but it doesn't.",
									step.getResourceNameNode()
							));
						}
					}
				}
			}
		});
		YBeanType taskStep = f.ybean("TaskStep");
		addProp(taskStep, "task", t_ne_string);
		addProp(taskStep, "file", t_string);
		addProp(taskStep, "config", task);
		addProp(taskStep, "privileged", t_boolean);
		addProp(taskStep, "params", t_params);
		addProp(taskStep, "vars", t_params);
		addProp(taskStep, "image", t_resource_name);
		addProp(taskStep, "input_mapping",  f.ymap(t_ne_string, t_maybe_resource_name));
		addProp(taskStep, "output_mapping", t_string_params);
		taskStep.requireOneOf("config", "file");
		taskStep.require(Constraints.implies("vars", "file"));

		YBeanType setPipelineStep = f.ybean("SetPipelineStep");
		addProp(setPipelineStep, "set_pipeline", t_ne_string);
		addProp(setPipelineStep, "file", t_ne_string).isRequired(true);
		addProp(setPipelineStep, "instance_vars", t_params);
		addProp(setPipelineStep, "vars", t_params);
		addProp(setPipelineStep, "var_files", t_strings);
		addProp(setPipelineStep, "team", t_ne_string);

		YBeanType loadVarStep = f.ybean("LoadVarStep");
		addProp(loadVarStep, "load_var", t_identifier);
		addProp(loadVarStep, "file", t_ne_string).isRequired(true);
		addProp(loadVarStep, "format", f.yenum("LoadVarFormat", "json", "yaml", "yml", "trim", "raw"));
		addProp(loadVarStep, "reveal", t_boolean);

		YBeanType aggregateStep = f.ybean("AggregateStep");
		YBeanType doStep = f.ybean("DoStep");
		YBeanType tryStep = f.ybean("TryStep");
		YBeanType inParallelStep = f.ybean("InParallelStep");

		YBeanType[] stepTypes = {
				getStep,
				putStep,
				taskStep,
				setPipelineStep,
				loadVarStep,
				aggregateStep,
				inParallelStep,
				doStep,
				tryStep
		};
		
		

		YBeanUnionType step = f.yBeanUnion("Step", stepTypes);
		addProp(aggregateStep, "aggregate", f.yseq(step)).isDeprecated("Deprecated in favor of `in_parallel`");
		YBeanType inParallelStepOptions = f.ybean("InParallelStepOptions");
		addProp(inParallelStepOptions, "steps", f.yseq(step));
		addProp(inParallelStepOptions, "limit", t_pos_integer);
		addProp(inParallelStepOptions, "fail_fast", t_boolean);
		addProp(inParallelStep, "in_parallel", f.yunion(null, 
				f.yseq(step),
				inParallelStepOptions
		));
		addProp(doStep, "do", f.yseq(step));
		addProp(tryStep, "try", step);

		YBeanType acrossVar = f.ybean("AcrossVar");
		addProp(acrossVar, "var", t_identifier).isPrimary(true);
		addProp(acrossVar, "values", f.yseq(t_any)).isRequired(true);
		addProp(acrossVar, "max_in_flight", f.yatomic("AcrossVarMaxInFlight").parseWith(ConcourseValueParsers.MAX_IN_FLIGHT_OR_ALL));
		addProp(acrossVar, "fail_fast", t_boolean);
		
		// shared properties applicable for any subtype of Step:
		for (AbstractType subStep : stepTypes) {
			addProp(step, subStep, "on_success", step);
			addProp(step, subStep, "on_failure", step);
			addProp(step, subStep, "on_abort", step);
			addProp(step, subStep, "on_error", step);
			addProp(step, subStep, "ensure", step);
			addProp(step, subStep, "tags", t_strings);
			addProp(step, subStep, "timeout", t_duration);
			addProp(step, subStep, "attempts", t_strictly_pos_integer);
			if (subStep!=getStep && subStep!=putStep) { // doc says 'across' doesn't work with put and get
				addProp(step, subStep, "across", f.yseq(acrossVar)); //TODO: create proper schema
			}
		}
		models.setStepType(step);

		YBeanType t_retention_config = f.ybean("RetentionConfig");
		addProp(t_retention_config, "days", t_pos_integer);
		addProp(t_retention_config, "builds", t_pos_integer);
		addProp(t_retention_config, "minimum_succeeded_builds", t_pos_integer);

		AbstractType job = f.ybean("Job");
		addProp(job, "name", jobNameDef).isPrimary(true);
		addProp(job, "old_name", t_ne_string);
		addProp(job, "plan", f.yseq(step)).isRequired(true);
		addProp(job, "serial", t_boolean);
		addProp(job, "build_logs_to_retain", t_pos_integer).isDeprecated("Deprecated in favor of `build_log_retention`");
		addProp(job, "build_log_retention", t_retention_config);
		addProp(job, "serial_groups", t_strings);
		addProp(job, "max_in_flight", t_pos_integer);
		addProp(job, "public", t_boolean);
		addProp(job, "disable_manual_trigger", t_boolean);
		addProp(job, "interruptible",  t_boolean);
		addProp(job, "ensure",  step);
		addProp(job, "on_success",  step);
		addProp(job, "on_failure",  step);
		addProp(job, "on_error",  step);
		addProp(job, "on_abort", step);

		AbstractType resourceType = f.ybean("ResourceType");
		addProp(resourceType, "name", resourceTypeNameDef).isPrimary(true);
		addProp(resourceType, "type", t_resource_type_name).isRequired(true);
		addProp(resourceType, "source", resourceSource);
		addProp(resourceType, "privileged", t_boolean);
		addProp(resourceType, "params", f.contextAware("GetParams", (dc) ->
			resourceTypes.getInParamsType( getParentPropertyValue("type", models, dc))
		));
		addProp(resourceType, "check_every", t_duration);
		addProp(resourceType, "tags", t_strings);
		addProp(resourceType, "unique_version_history", t_boolean);

		YType t_group_name_def= f.yatomic("Group Name")
				.parseWith(ValueParsers.NE_STRING);
		
		/**
		 * VAR_SOURCE definition
		 */
		YBeanType t_var_source_type = f.ybean("VarSource");
		addProp(t_var_source_type, "name", t_ne_string).isPrimary(true);
		addProp(t_var_source_type, "type", f.yenum("VarSourceTypes", "vault", "ssm", "dummy", "secretmanager")).isRequired(true);
		addProp(t_var_source_type, "config", f.contextAware("VarSourceConfig", (dc) -> {
			YamlPath path = dc.getPath();
			if (path != null) {
				YamlFileAST root = asts.getSafeAst(dc.getDocument());
				if (root!=null) {
					String value = NodeUtil.asScalar(path.dropLast().append(YamlPathSegment.valueAt("type")).traverseToNode(root));
					switch (value) {
					case "vault":
						YBeanType vaultConfig = f.ybean("VaultConfig");
						addProp(vaultConfig, "url", t_ne_string).isPrimary(true);
						addProp(vaultConfig, "ca_cert", t_string);
						addProp(vaultConfig, "path_prefix", t_string);
						addProp(vaultConfig, "lookup_templates", f.yseq(t_string));
						addProp(vaultConfig, "shared_path", t_string);
						addProp(vaultConfig, "namespace", t_string);
						addProp(vaultConfig, "client_cert", t_string);
						addProp(vaultConfig, "client_key", t_string);
						addProp(vaultConfig, "client_name", t_string);
						addProp(vaultConfig, "insecure_skip_verify", t_boolean);
						addProp(vaultConfig, "client_token", t_string);
						addProp(vaultConfig, "auth_backend", t_string);
						addProp(vaultConfig, "auth_params", f.ymap(t_ne_string, t_string));
						addProp(vaultConfig, "auth_max_ttl", t_duration);
						addProp(vaultConfig, "auth_retry_max", t_duration);
						addProp(vaultConfig, "auth_retry_initial", t_duration);
						return vaultConfig;
					case "ssm":
						YBeanType ssmConfig = f.ybean("SSMConfig");
						addProp(ssmConfig, "region", t_string).isPrimary(true);
						return ssmConfig;
					case "dummy":
						YBeanType dummyConfig = f.ybean("DummyConfig");
						addProp(dummyConfig, "vars", t_params).isPrimary(true);
						return dummyConfig;
					case "secretmanager":
						YBeanType smcConfig = f.ybean("SecretManagerConfig");
						addProp(smcConfig, "aws-secretsmanager-region", t_ne_string).isPrimary(true);
						addProp(smcConfig, "aws-secretsmanager-access-key", t_ne_string);
						addProp(smcConfig, "aws-secretsmanager-secret-key", t_ne_string);
						addProp(smcConfig, "aws-secretsmanager-session-token", t_ne_string);
						addProp(smcConfig, "aws-secretsmanager-pipeline-secret-template", t_string);
						addProp(smcConfig, "aws-secretsmanager-team-secret-template", t_string);
						return smcConfig;
					default:
						return null;
					}
				}
			}
			return null;
		}).treatAsBean()).isRequired(true);

		AbstractType group = f.ybean("Group");
		addProp(group, "name", t_group_name_def).isPrimary(true);
		addProp(group, "resources", f.yseq(t_resource_name));
		addProp(group, "jobs", f.yseq(t_job_name_pattern));

		YType t_background_image_def = f.yatomic("Background Image")
				.parseWith(ValueParsers.NE_STRING);

		AbstractType t_display = f.ybean("Display");
		addProp(t_display, "background_image", t_background_image_def).isRequired(true);

		YSeqType t_resources = f.yseq(t_resource);
		YSeqType t_jobs = f.yseq(job);
		YSeqType t_resourceTypes = f.yseq(resourceType);
		AbstractType t_groups = f.yseq(group).require(models::jobAssignmentIsComplete);
		addProp(TOPLEVEL_TYPE, "resources", t_resources);
		addProp(TOPLEVEL_TYPE, "jobs", t_jobs);
		addProp(TOPLEVEL_TYPE, "var_sources", f.yseq(t_var_source_type));
		addProp(TOPLEVEL_TYPE, "resource_types", t_resourceTypes);
		addProp(TOPLEVEL_TYPE, "groups", t_groups);
		addProp(TOPLEVEL_TYPE, "display", t_display);

		definitionTypes = ImmutableList.of(
				jobNameDef,
				resourceTypeNameDef,
				t_resource_name_def,
				t_group_name_def,
				t_background_image_def
		);
		hierarchicDefinitions = ImmutableList.of(
				new HierarchicalDefType(t_resources, null, SymbolKind.File, "Resources"),
				new HierarchicalDefType(t_resource, YamlPath.fromSimpleProperty("name"), SymbolKind.File, "Resource"),

				new HierarchicalDefType(t_jobs, null, SymbolKind.Method, "Jobs"),
				new HierarchicalDefType(job, YamlPath.fromSimpleProperty("name"), SymbolKind.Method, "Job"),

				new HierarchicalDefType(t_resourceTypes, null, SymbolKind.Interface, "Resource Types"),
				new HierarchicalDefType(resourceType, YamlPath.fromSimpleProperty("name"), SymbolKind.Interface, "Resource Type"),

				new HierarchicalDefType(t_groups, null, SymbolKind.Package, "Groups"),
				new HierarchicalDefType(group, YamlPath.fromSimpleProperty("name"), SymbolKind.Package, "Groups"),

				new HierarchicalDefType(t_display, null, SymbolKind.Package, "Display Settings")
		);

		initializeDefaultResourceTypes();
	}

	private static YValueHint hint(String value, String description) {
		return YTypeFactory.hint(value, value + " - " + description);
	}

	private void initializeDefaultResourceTypes() {
		// git :
		{
			AbstractType t_git_repo_uri = f.yatomic("GitRepoUri");
			t_git_repo_uri.setCustomContentAssistant(new GithubRepoContentAssistant(github));
			t_git_repo_uri.parseWith(GithubValueParsers.uri(github));
			
			AbstractType t_https_tunnel = f.ybean("HttpsTunnel");
			addProp(t_https_tunnel, "proxy_host", t_ne_string).isRequired(true);
			addProp(t_https_tunnel, "proxy_port", t_pos_integer).isRequired(true);
			addProp(t_https_tunnel, "proxy_user", t_ne_string);
			addProp(t_https_tunnel, "proxy_password", t_ne_string);
			
			AbstractType t_commit_filters = f.ybean("CommitFilters");
			addProp(t_commit_filters, "exclude", f.yseq(t_ne_string));
			addProp(t_commit_filters, "include", f.yseq(t_ne_string));

			AbstractType source = f.ybean("GitSource");
			addProp(source, "uri", t_git_repo_uri).isPrimary(true);
			addProp(source, "branch", t_ne_string); //It's more complicated than that! Its only required in 'put' step. So we'll check this as a contraint in put steps!
			addProp(source, "private_key", t_ne_string);
			addProp(source, "private_key_user", t_ne_string);
			addProp(source, "private_key_passphrase", t_ne_string);
			addProp(source, "forward_agent", t_boolean);
			addProp(source, "username", t_ne_string);
			addProp(source, "password", t_string);
			addProp(source, "paths", t_strings);
			addProp(source, "ignore_paths", t_strings);
			addProp(source, "skip_ssl_verification", t_boolean);
			addProp(source, "tag_filter", t_ne_string);
			addProp(source, "tag_regex", t_ne_string);
			addProp(source, "fetch_tags", t_boolean);
			addProp(source, "submodule_credentials", f.yseq(f.ybean("SubmoduleCredential", 
					f.yprop("host", t_ne_string).isRequired(true),
					f.yprop("username", t_ne_string).isRequired(true),
					f.yprop("password", t_ne_string).isRequired(true)
			)));
			addProp(source, "git_config", t_pair_list);
			addProp(source, "disable_ci_skip", t_boolean);
			addProp(source, "commit_verification_keys", t_strings);
			addProp(source, "commit_verification_key_ids", t_strings);
			addProp(source, "gpg_keyserver", t_ne_string);
			addProp(source, "git_crypt_key", t_ne_string);
			addProp(source, "https_tunnel", t_https_tunnel);
			addProp(source, "commit_filter", t_commit_filters);
			addProp(source, "version_depth", t_strictly_pos_integer);
			source.require(Constraints.mutuallyExclusive("tag_filter", "tag_regex"));

			AbstractType get = f.ybean("GitGetParams");
			addProp(get, "depth", t_strictly_pos_integer);
			addProp(get, "fetch_tags", t_boolean);
			addProp(get, "submodules", f.yany("GitSubmodules").addHints("all", "none"));
			addProp(get, "submodule_recursive", t_boolean);
			addProp(get, "submodule_remote", t_boolean);
			addProp(get, "disable_git_lfs", t_boolean);
			addProp(get, "clean_tags", t_boolean);
			addProp(get, "fetch", t_strings).isDeprecated(true); //Warning: t_strings is just a guess. This property is undocumented. The example I've seen seem to use list of git branch/tag names.
			addProp(get, "short_ref_format", t_ne_string);
			addProp(get, "timestamp_format", t_ne_string);
			addProp(get, "describe_ref_options", t_ne_string);

			AbstractType put = f.ybean("GitPutParams");
			addProp(put, "repository", t_ne_string).isPrimary(true);
			addProp(put, "rebase", t_boolean);
			addProp(put, "merge", t_boolean);
			addProp(put, "returning", f.yenum("MergeReturning", "merged", "unmerged"));
			addProp(put, "tag", t_ne_string);
			addProp(put, "only_tag", t_boolean);
			addProp(put, "tag_prefix", t_string);
			addProp(put, "force", t_boolean);
			addProp(put, "annotate", t_ne_string);
			addProp(put, "notes", t_ne_string);
			addProp(put, "branch", t_ne_string);
			put.require(Constraints.requireAtMostOneOf("rebase", "merge"));
			resourceTypes.def("git", source, get, put);
		}

		// Shared by docker-image and registry-image
		AbstractType registry_mirror = f.ybean("RegistryMirror");
		addProp(registry_mirror, "host", t_ne_string).isPrimary(true);
		addProp(registry_mirror, "username", t_ne_string);
		addProp(registry_mirror, "password", t_ne_string);
		
		//docker-image:
		{
			
			AbstractType source = f.ybean("DockerImageSource");
			addProp(source, "repository", t_ne_string).isPrimary(true);
			addProp(source, "tag", t_ne_string);
			addProp(source, "username", t_ne_string);
			addProp(source, "password", t_ne_string);
			addProp(source, "aws_access_key_id", t_ne_string);
			addProp(source, "aws_secret_access_key", t_ne_string);
			addProp(source, "aws_session_token", t_ne_string);
			addProp(source, "insecure_registries", t_strings);
			addProp(source, "registry_mirror", t_ne_string);
			addProp(source, "ca_certs", f.yseq(f.ybean("CaCertsEntry",
					f.yprop("domain", t_ne_string),
					f.yprop("cert", t_ne_string)
			)));
			addProp(source, "client_certs", f.yseq(f.ybean("ClientCertsEntry",
					f.yprop("domain", t_ne_string),
					f.yprop("key", t_ne_string),
					f.yprop("cert", t_ne_string)
			)));
			addProp(source, "max_concurrent_downloads", t_pos_integer);
			addProp(source, "max_concurrent_uploads", t_pos_integer);

			AbstractType get = f.ybean("DockerImageGetParams");
			addProp(get, "save", t_boolean);
			addProp(get, "rootfs", t_boolean);
			addProp(get, "skip_download", t_boolean);

			AbstractType put = f.ybean("DockerImagePutParams");
			addProp(put, "additional_tags", t_ne_string);
			addProp(put, "build", t_ne_string);
			addProp(put, "build_args", t_string_params);
			addProp(put, "build_args_file", t_ne_string);
			addProp(put, "cache", t_boolean);
			addProp(put, "cache_from", t_strings);
			addProp(put, "cache_tag", t_ne_string);
			addProp(put, "dockerfile", t_ne_string);
			addProp(put, "import_file", t_ne_string);
			addProp(put, "labels", t_string_params);
			addProp(put, "labels_file", t_ne_string);
			addProp(put, "load", t_ne_string);
			addProp(put, "load_base", t_ne_string);
			addProp(put, "load_bases", t_strings);
			addProp(put, "load_file", t_ne_string);
			addProp(put, "load_repository", t_ne_string);
			addProp(put, "load_tag", t_ne_string);
			addProp(put, "pull_repository", t_ne_string).isDeprecated(true);
			addProp(put, "pull_tag", t_ne_string).isDeprecated(true);
			addProp(put, "tag", t_ne_string).isDeprecated(true);
			addProp(put, "tag_file", t_ne_string);

			addProp(put, "tag_as_latest", t_boolean);
			addProp(put, "tag_prefix", t_ne_string);
			addProp(put, "target_name", t_ne_string);

			resourceTypes.def("docker-image", source, get, put);
		}
		//registry-image
		{
			AbstractType source = f.ybean("RegistryImageSource");
			addProp(source, "repository", t_ne_string).isPrimary(true);
			addProp(source, "insecure", t_boolean);
			addProp(source, "tag", t_ne_string);
			addProp(source, "variant", t_ne_string);
			addProp(source, "semver_constraint", t_ne_string);
			addProp(source, "username", t_ne_string);
			addProp(source, "password", t_ne_string);
			addProp(source, "aws_access_key_id", t_ne_string);
			addProp(source, "aws_secret_access_key", t_ne_string);
			addProp(source, "aws_session_token", t_ne_string);
			addProp(source, "aws_region", t_aws_region);
			addProp(source, "aws_role_arn", t_ne_string);
			addProp(source, "aws_role_arns", t_strings);
			addProp(source, "debug", t_boolean);
			addProp(source, "registry_mirror", registry_mirror);
			{
				AbstractType contentTrust = f.ybean("RegistryImageContentTrust");
				addProp(contentTrust, "server", t_ne_string);
				addProp(contentTrust, "repository_key_id", t_ne_string).isRequired(true);
				addProp(contentTrust, "repository_key", t_ne_string).isRequired(true);
				addProp(contentTrust, "repository_passphrase", t_ne_string).isRequired(true);
				addProp(contentTrust, "tls_key", t_ne_string);
				addProp(contentTrust, "tls_cert", t_ne_string);
				
				addProp(source, "content_trust", contentTrust);
			}
			addProp(source, "ca_certs", t_strings);
			
			AbstractType get = f.ybean("RegistryImageGetParams");
			addProp(get, "format", f.yenum("RegistryImageFormat", "rootfs", "oci"));
			addProp(get, "skip_download", t_boolean);
			
			AbstractType put = f.ybean("RegistryImagePutParams");
			addProp(put, "image", t_ne_string).isPrimary(true);
			addProp(put, "version", t_ne_string);
			addProp(put, "bump_aliases", t_boolean);
			addProp(put, "additional_tags", t_ne_string);

			resourceTypes.def("registry-image", source, get, put);
			
			source.require(Constraints.mutuallyExclusive("aws_role_arn", "aws_role_arns"));
		}
		//s3
		{
			YType t_canned_acl = f.yenum("S3CannedAcl",
					//See https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#canned-acl
					"private", "public-read", "public-read-write", "aws-exec-read",
					"authenticated-read", "bucket-owner-read", "bucket-owner-full-control",
					"log-delivery-write"
			);

			AbstractType source = f.ybean("S3Source");
			addProp(source, "bucket", t_ne_string).isPrimary(true);
			addProp(source, "access_key_id", t_ne_string);
			addProp(source, "secret_access_key", t_ne_string);
			addProp(source, "session_token", t_ne_string);
			addProp(source, "aws_role_arn", t_ne_string);
			addProp(source, "region_name", t_aws_region);
			addProp(source, "private", t_boolean);
			addProp(source, "cloudfront_url", t_ne_string);
			addProp(source, "endpoint", t_ne_string);
			addProp(source, "disable_ssl", t_boolean);
			addProp(source, "skip_ssl_verification", t_boolean);
			addProp(source, "skip_download", t_boolean);
			addProp(source, "server_side_encryption", t_ne_string);
			addProp(source, "sse_kms_key_id", t_ne_string);
			addProp(source, "use_v2_signing", t_boolean);
			addProp(source, "regexp", t_ne_string);
			addProp(source, "versioned_file", t_ne_string);
			source.requireOneOf("regexp", "versioned_file");
			addProp(source, "initial_path", t_ne_string);
			addProp(source, "initial_version", t_ne_string);
			source.require(Constraints.mutuallyExclusive("initial_path", "initial_version"));
			source.require(Constraints.implies("initial_version", "versioned_file"));
			source.require(Constraints.implies("initial_path", "regexp"));
			addProp(source, "initial_content_text", t_ne_string);
			addProp(source, "initial_content_binary", t_ne_string);
			source.require(Constraints.mutuallyExclusive("initial_content_text", "initial_content_binary"));

			AbstractType get = f.ybean("S3GetParams");
			addProp(get, "unpack", t_boolean);
			addProp(get, "skip_download", t_boolean);

			AbstractType put = f.ybean("S3PutParams");
			addProp(put, "file", t_ne_string).isPrimary(true);
			addProp(put, "acl", t_canned_acl);
			addProp(put, "content_type", t_mime_type);

			resourceTypes.def("s3", source, get, put);
		}
		//pool
		{
			AbstractType source = f.ybean("PoolSource");
			addProp(source, "uri", t_ne_string).isRequired(true);
			addProp(source, "branch", t_ne_string).isRequired(true);
			addProp(source, "pool", t_ne_string).isRequired(true);
			addProp(source, "private_key", t_ne_string);
			addProp(source, "username", t_ne_string);
			addProp(source, "password", t_string);
			addProp(source, "retry_delay", t_duration);

			AbstractType get = f.ybean("PoolGetParams");
			//get params deliberately left empty

			AbstractType put = f.ybean("PoolPutParams");
			addProp(put, "acquire", t_boolean);
			addProp(put, "claim", t_ne_string);
			addProp(put, "release", t_ne_string);
			addProp(put, "add", t_ne_string);
			addProp(put, "add_claimed", t_ne_string);
			addProp(put, "remove", t_ne_string);
			put.requireOneOf(put.getPropertyNames());

			resourceTypes.def("pool", source, get, put);
		}
		//semver
		{
			AbstractType git_source = f.ybean("GitSemverSource");
			addProp(git_source, "uri", t_ne_string).isPrimary(true);
			addProp(git_source, "branch", t_ne_string).isRequired(true);
			addProp(git_source, "file", t_ne_string).isRequired(true);
			addProp(git_source, "private_key", t_ne_string);
			addProp(git_source, "username", t_ne_string);
			addProp(git_source, "password", t_ne_string);
			addProp(git_source, "git_user", t_ne_string);
			addProp(git_source, "depth", t_strictly_pos_integer);
			addProp(git_source, "commit_message", t_ne_string);
			addProp(git_source, "skip_ssl_verification", t_boolean);

			AbstractType s3_source = f.ybean("S3SemverSource");
			addProp(s3_source, "bucket", t_ne_string).isPrimary(true);
			addProp(s3_source, "key", t_ne_string).isRequired(true);
			addProp(s3_source, "access_key_id", t_ne_string).isRequired(true);
			addProp(s3_source, "secret_access_key", t_ne_string).isRequired(true);
			addProp(s3_source, "assume_role_arn", t_ne_string);
			addProp(s3_source, "region_name", t_aws_region);
			addProp(s3_source, "endpoint", t_ne_string);
			addProp(s3_source, "disable_ssl", t_boolean);

			AbstractType swift_source = f.ybean("SwiftSemverSource");
			addProp(swift_source, "openstack", t_any).isPrimary(true);

			AbstractType gcs_source = f.ybean("GcsSemverSource");
			addProp(gcs_source, "bucket", t_ne_string).isRequired(true);
			addProp(gcs_source, "key", t_ne_string).isRequired(true);
			addProp(gcs_source, "json_key", t_ne_string).isRequired(true);

			AbstractType[] driverSpecificSources = {
					git_source, s3_source, swift_source, gcs_source
			};

			AbstractType source = f.contextAware("SemverSource", (dc) -> {
				switch (getSemverDriverName(dc)) {
				case "git":
					return git_source;
				case "s3":
					return s3_source;
				case "swift":
					return swift_source;
				case "gcs":
					return gcs_source;
				default:
					return null;
				}
			}).treatAsBean();
			addProp(source, "initial_version", t_semver);
			addProp(source, "driver", f.yenum("SemverDriver", "git", "s3", "swift", "gcs")).isPrimary(true, false);
			for (AbstractType s : driverSpecificSources) {
				for (YTypedProperty p : source.getProperties()) {
					s.addProperty(p);
				}
			}

			AbstractType get = f.ybean("SemverGetParams");
			addProp(get, "bump", f.yenum("SemverBump", "major", "minor", "patch", "final"));
			addProp(get, "pre", t_ne_string);
			addProp(get, "pre_without_version", t_boolean);
			addProp(get, "build_without_version", t_boolean);


			AbstractType put = f.ybean("SemverPutParams");
			for (YTypedProperty p : get.getProperties()) {
				put.addProperty(p);
			}
			addProp(put, "file", t_ne_string);

			resourceTypes.def("semver", source, get, put);
		}
		//time:
		{
			AbstractType source = f.ybean("TimeSource");
			addProp(source, "interval", t_duration);
			addProp(source, "location", t_location);
			addProp(source, "start", t_time_of_day);
			addProp(source, "stop", t_time_of_day);
			addProp(source, "days", f.yseq(t_day));

			AbstractType get = f.ybean("TimeGetParams");
			//get params deliberately left empty

			AbstractType put = f.ybean("TimePutParams");
			//put params deliberately left empty

			resourceTypes.def("time", source, get, put);
		}
		//cloudfoundry:
		{
			YAtomicType t_cf_api_url = f.yatomic("CFApiUrl");
			t_cf_api_url.addHints("https://api.run.pivotal.io");
			t_cf_api_url.parseWith(ValueParsers.NE_STRING);

			AbstractType source = f.ybean("CloudFoundrySource");
			addProp(source, "api", t_cf_api_url).isRequired(true);
			addProp(source, "username", t_ne_string);
			addProp(source, "password", t_ne_string);
			addProp(source, "client_id", t_ne_string);
			addProp(source, "client_secret", t_ne_string);
			addProp(source, "organization", t_ne_string).isRequired(true);
			addProp(source, "space", t_ne_string).isRequired(true);
			addProp(source, "skip_cert_check", t_boolean);
			addProp(source, "verbose", t_boolean);
			
			source.require(Constraints.together("username", "password"));
			source.require(Constraints.together("client_id", "client_secret"));
			source.require(Constraints.requireAtLeastOneOf("username", "password", "client_id", "client_secret"));
			source.require(Constraints.mutuallyExclusive(
					ImmutableList.of("username", "password"), 
					ImmutableList.of("client_id", "client_secret")
			));

			AbstractType get = f.ybean("CloudFoundryGetParams");
			//get params deliberately left empty

			AbstractType put = f.ybean("CloudFoundryPutParams");
			addProp(put, "manifest", t_ne_string).isRequired(true);
			addProp(put, "path", t_ne_string);
			addProp(put, "current_app_name", t_ne_string);
			addProp(put, "environment_variables", t_string_params);
			addProp(put, "vars", t_params);
			addProp(put, "vars_files", t_strings);
			addProp(put, "docker_username", t_ne_string);
			addProp(put, "docker_password", t_ne_string);
			addProp(put, "show_app_log", t_boolean);
			addProp(put, "no_start", t_boolean);
			put.require(Constraints.mutuallyExclusive("no_start", "current_app_name"));

			resourceTypes.def("cf", source, get, put);
		}
	}

	private String getSemverDriverName(DynamicSchemaContext dc) {
		String driver = getSiblingPropertyValue(dc, "driver");
		return driver!=null ? driver : "s3";
	}

	private Node getResourceNameNode(String resourceNameProp, DynamicSchemaContext dc) {
		Node resourceName = models.getParentPropertyNode("resource", dc);
		if (resourceName==null) {
			resourceName = models.getParentPropertyNode(resourceNameProp, dc);
		}
		return resourceName;
	}

	private String getResourceName(String resourceNameProp, DynamicSchemaContext dc) {
		Node resourceName = getResourceNameNode(resourceNameProp, dc);
		return NodeUtil.asScalar(resourceName);
	}

	private String getResourceType(String resourceNameProp, ConcourseModel models, DynamicSchemaContext dc) {
		String resourceName = getResourceName(resourceNameProp, dc);
		if (resourceName!=null) {
			return models.getResourceType(dc.getDocument(), resourceName);
		}
		return null;
	}

	private String getResourceTypeTag(ConcourseModel models, DynamicSchemaContext dc) {
		return getParentPropertyValue("type", models, dc);
	}

	private String getParentPropertyValue(String propName, ConcourseModel models, DynamicSchemaContext dc) {
		return NodeUtil.asScalar(models.getParentPropertyNode(propName, dc));
	}

	private String getSiblingPropertyValue(DynamicSchemaContext dc, String propName) {
		YamlPath path = dc.getPath();
		if (path!=null) {
			YamlFileAST root = asts.getSafeAst(dc.getDocument());
			if (root!=null) {
				return NodeUtil.asScalar(path.append(YamlPathSegment.valueAt(propName)).traverseToNode(root));
			}
		}
		return null;
	}

	private YTypedPropertyImpl prop(AbstractType beanType, String name, YType type) {
		YTypedPropertyImpl prop = f.yprop(name, type);
		prop.setDescriptionProvider(descriptionFor(beanType, name));
		return prop;
	}

	private YTypedPropertyImpl addProp(AbstractType superType, AbstractType bean, String name, YType type) {
		YTypedPropertyImpl p = prop(superType, name, type);
		bean.addProperty(p);
		return p;
	}

	private YTypedPropertyImpl addProp(AbstractType bean, String name, YType type) {
		return addProp(bean, bean, name, type);
	}

	public static Renderable descriptionFor(YType owner, String propName) {
		String typeName = owner.toString();
		return Renderables.fromClasspath(PipelineYmlSchema.class, "/desc/"+typeName+"/"+propName);
	}

	@Override
	public AbstractType getTopLevelType() {
		return TOPLEVEL_TYPE;
	}

	@Override
	public YTypeUtil getTypeUtil() {
		return TYPE_UTIL;
	}

	public YamlSchema getTaskSchema() {
		return new YamlSchema() {

			@Override
			public YTypeUtil getTypeUtil() {
				return TYPE_UTIL;
			}

			@Override
			public YType getTopLevelType() {
				return task;
			}

			@Override
			public String toString() {
				return "TaskYamlSchema";
			}
		};
	}

	public List<YType> getDefinitionTypes() {
		return definitionTypes;
	}

	public List<HierarchicalDefType> getHierarchicalDefinitionTypes() {
		return hierarchicDefinitions;
	}

}
