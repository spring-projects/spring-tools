/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.bosh;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ide.vscode.bosh.models.BoshModels;
import org.springframework.ide.vscode.bosh.models.CachingModelProvider;
import org.springframework.ide.vscode.bosh.models.CloudConfigModel;
import org.springframework.ide.vscode.bosh.models.DynamicModelProvider;
import org.springframework.ide.vscode.bosh.models.ReleaseData;
import org.springframework.ide.vscode.bosh.models.ReleasesModel;
import org.springframework.ide.vscode.bosh.models.StemcellData;
import org.springframework.ide.vscode.bosh.models.StemcellModel;
import org.springframework.ide.vscode.bosh.models.StemcellsModel;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.PartialCollection;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParsers;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlAstCache;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.AbstractType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YAtomicType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YBeanType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YContextSensitive;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.schema.YamlSchema;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraints;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class BoshDeploymentManifestSchema extends SchemaSupport implements YamlSchema {

	private final YBeanType V2_TOPLEVEL_TYPE;
	private final YBeanType V1_TOPLEVEL_TYPE;
	private final YContextSensitive TOPLEVEL_TYPE;
	private final YTypeUtil TYPE_UTIL;

	private static final ImmutableSet<String> DEPRECATED_V1_PROPS = ImmutableSet.of("resource_pools", "networks", "compilation", "jobs", "disk_pools", "cloud_provider");
	private static final ImmutableSet<String>  SHARED_V1_V2_PROPS = ImmutableSet.of("name", "director_uuid", "releases", "update", "properties");
	private ImmutableList<YType> definitionTypes = null;
		//Note: 'director_uuid' is also deprecated. But its treated separately since it is deprecated and ignored by V2 client no matter what (i.e. deprecated in both schemas)

	public final YType t_string;
	public final YType t_ne_string;

	public final YType t_strings;

	public final YAtomicType t_boolean;
	public final YType t_any;
	public final YType t_params;
	public final YType t_string_params;
	public final YType t_pos_integer;
	public final YType t_strictly_pos_integer;
	public final YType t_uuid;
	public final YType t_integer_or_range;
	private YType t_stemcell_alias_def;
	private YType t_stemcell_alias_ref;
	private YType t_release_name_def;
	private YType t_release_name_ref;
	private YType t_instance_group_name_def;
	private YType t_var_name_def;
	private final YamlAstCache asts;
	private final ASTTypeCache astTypes;
	private DynamicModelProvider<CloudConfigModel> cloudConfigProvider;
	private DynamicModelProvider<StemcellsModel> stemcellsProvider;
	private DynamicModelProvider<ReleasesModel> releasesProvider;
	private List<Pair<YType, YType>> defAndRefTypes;

	public BoshDeploymentManifestSchema(YTypeFactory f, BoshModels models) {
		super(f);
		this.asts = models.asts;
		this.astTypes = models.astTypes;
		this.cloudConfigProvider = new CachingModelProvider<>(models.cloudConfigProvider, CloudConfigModel.class);
		this.stemcellsProvider = new CachingModelProvider<>(models.stemcellsProvider, StemcellsModel.class);
		this.releasesProvider = new CachingModelProvider<>(models.releasesProvider, ReleasesModel.class);
		TYPE_UTIL = f.TYPE_UTIL;

		t_string = f.yatomic("String");
		t_boolean = f.yenum("boolean", "true", "false");
		t_ne_string = f.yatomic("String")
				.parseWith(ValueParsers.NE_STRING);
		t_strings = f.yseq(t_string);
		t_any = f.yany("Object");
		t_params = f.ymap(t_string, t_any);
		t_string_params = f.ymap(t_string, t_string);
		t_pos_integer = f.yatomic("Positive Integer")
				.parseWith(ValueParsers.POS_INTEGER);
		t_strictly_pos_integer = f.yatomic("Strictly Positive Integer")
				.parseWith(ValueParsers.integerAtLeast(1));
		t_uuid = f.yatomic("UUID").parseWith(UUID::fromString);
		t_integer_or_range = f.yatomic("Integer or Range")
				.parseWith(BoshValueParsers.INTEGER_OR_RANGE);

		V2_TOPLEVEL_TYPE = createV2Schema();
		V1_TOPLEVEL_TYPE = createV1Schema(V2_TOPLEVEL_TYPE);

		TOPLEVEL_TYPE = f.contextAware("DeploymenManifestV1orV2", (dc) -> {
			boolean looksLikeV1 = dc.getDefinedProperties().contains("networks");
			return looksLikeV1 ? V1_TOPLEVEL_TYPE : V2_TOPLEVEL_TYPE;
		});
	}

	private YBeanType createV1Schema(AbstractType v2Schema) {
		YBeanType v1Schema = f.ybean("DeploymentManifestV1");
		Map<String, YTypedProperty> v2properties = v2Schema.getPropertiesMap();
		ImmutableSet<String> v1Props = ImmutableSet.<String>builder()
				.addAll(DEPRECATED_V1_PROPS)
				.addAll(SHARED_V1_V2_PROPS)
				.build();
		for (String name : v1Props) {
			YTypedProperty prop = v2properties.get(name);
			Assert.isNotNull(prop);
			v1Schema.addProperty(prop);
		}
		return v1Schema;
	}

	private YBeanType createV2Schema() {
		YBeanType v2Schema = f.ybean("BoshDeploymentManifest");
		addProp(v2Schema, "name", t_ne_string).isPrimary(true);
		addProp(v2Schema, "director_uuid", t_uuid).isDeprecated(
				"bosh v2 CLI no longer checks or requires director_uuid in the deployment manifest. " +
				"To achieve similar safety make sure to give unique deployment names across environments."
		);

		t_instance_group_name_def = f.yatomic("InstanceGroupName")
				.parseWith(ValueParsers.NE_STRING);

		t_stemcell_alias_def = f.yatomic("StemcellAlias")
				.parseWith(ValueParsers.NE_STRING);
		t_stemcell_alias_ref = f.yenumFromDynamicValues("StemcellAlias", (dc) -> PartialCollection.compute(() -> astTypes.getDefinedNames(dc, t_stemcell_alias_def)));
		t_release_name_def = f.yenumFromDynamicValues("ReleaseName", (dc) -> {
			PartialCollection<String> releaseNames = PartialCollection.compute(() -> releasesProvider.getModel(dc).getReleaseNames());
			return StringUtil.hasText(getCurrentEntityProperty(dc, "url")) ? releaseNames.addUncertainty() : releaseNames;
		});
		t_release_name_ref = f.yenumFromDynamicValues("ReleaseName", (dc) -> PartialCollection.compute(() -> astTypes.getDefinedNames(dc, t_release_name_def)));

		t_var_name_def = f.yatomic("VariableName")
				.parseWith(ValueParsers.NE_STRING);


		YAtomicType t_ip_address = f.yatomic("IPAddress"); //TODO: some kind of checking?
		t_ip_address.parseWith(ValueParsers.NE_STRING);

		YAtomicType t_url = f.yatomic("URL");
		t_url.parseWith(BoshValueParsers.url("http", "https", "file"));

		YAtomicType t_network_name = f.yenumFromDynamicValues("NetworkName",
				(dc) -> PartialCollection.compute(() -> cloudConfigProvider.getModel(dc).getNetworkNames()));
		YAtomicType t_disk_type = f.yenumFromDynamicValues("DiskType",
				(dc) -> PartialCollection.compute(() -> cloudConfigProvider.getModel(dc).getDiskTypes()));
		YAtomicType t_vm_extension = f.yenumFromDynamicValues("VMExtension",
				(dc) -> PartialCollection.compute(() -> cloudConfigProvider.getModel(dc).getVMExtensions()));
		YAtomicType t_vm_type = f.yenumFromDynamicValues("VMType",
				(dc) -> PartialCollection.compute(() -> cloudConfigProvider.getModel(dc).getVMTypes()));
		YAtomicType t_az = f.yenumFromDynamicValues("AvailabilityZone",
				(dc) -> PartialCollection.compute(() -> cloudConfigProvider.getModel(dc).getAvailabilityZones()));

		YBeanType t_network = f.ybean("Network");
		addProp(t_network, "name", t_network_name).isRequired(true);
		addProp(t_network, "static_ips", f.yseq(t_ip_address));
		addProp(t_network, "default", f.yseq(t_ne_string)); //TODO: Can we determine the set of valid values? How?

		YBeanType t_instance_group_env = f.ybean("InstanceGroupEnv");
		addProp(t_instance_group_env, "bosh", t_params);
		addProp(t_instance_group_env, "password", t_ne_string);

		YType t_release_version = f.yenumFromDynamicValues("ReleaseVersion",
			//message formatter:
			dc -> (s, values) -> {
				String name = getCurrentEntityProperty(dc, "name");
				if (StringUtil.hasText(name)) {
					return "'"+s+"' is an unknown 'ReleaseVersion[name="+name+"]'. Valid values are: "+values;
				} else {
					return "'"+s+"' is an unknown 'ReleaseVersion'. Valid values are: "+values;
				}
			},
			//value provider:
			dc -> {
				PartialCollection<ReleaseData> releases = PartialCollection.compute(() -> releasesProvider.getModel(dc).getReleases());
				if (StringUtil.hasText(getCurrentEntityProperty(dc, "url"))) {
					releases = releases.addUncertainty();
				} else {
					String name = getCurrentEntityProperty(dc, "name");
					if (StringUtil.hasText(name)) {
						releases = releases.map(r -> name.equals(r.getName()) ? r : null);
					}
				}
				return releases.map(r -> r.getVersion()).add("latest");
			}
		);

		YBeanType t_release = f.ybean("Release");
		addProp(t_release, "name", t_release_name_def).isPrimary(true);
		addProp(t_release, "version", t_release_version).isRequired(true);
		addProp(t_release, "url", t_url);
		addProp(t_release, "sha1", t_ne_string);
		t_release.require(BoshConstraints.SHA1_REQUIRED_FOR_HTTP_URL);
		addProp(v2Schema, "releases", f.yseq(t_release)).isRequired(true);

		YBeanType t_stemcell = f.ybean("Stemcell");

		YType t_stemcell_name_ref = f.yenumFromDynamicValues("StemcellName", (dc) ->
			PartialCollection.compute(() -> stemcellsProvider.getModel(dc).getStemcellNames())
		);
		YType t_stemcell_os_ref = f.yenumFromDynamicValues("StemcellOs", (dc) ->
			PartialCollection.compute(() -> stemcellsProvider.getModel(dc).getStemcellOss())
		);
		YType t_stemcell_version_ref = f.yenumFromDynamicValues("StemcellVersion",
			(dc) -> (parseString, validValues) -> {
				try {
					Predicate<StemcellData> filter = getCurrentStemcell(dc).createVersionFilter();
					if (filter!=StemcellModel.ALLWAYS_TRUE_FILTER) {
						return "'"+parseString+"' is an unknown 'StemcellVersion["+filter+"]'. Valid values are: "+validValues;
					}
				} catch (Exception e) {
					//ignore (parse error most likely)
				}
				return "'"+parseString+"' is an unknown 'StemcellVersion'. Valid values are: "+validValues;
			},
			(dc) -> {
				Predicate<StemcellData> filter = getCurrentStemcell(dc).createVersionFilter();
				return PartialCollection.compute(() -> stemcellsProvider.getModel(dc).getStemcells())
					.map(sc -> filter.test(sc) ? sc.getVersion() : null)
					.add("latest");
			}
		);

		addProp(t_stemcell, "alias", t_stemcell_alias_def).isPrimary(true);
		addProp(t_stemcell, "version", t_stemcell_version_ref).isRequired(true);
		addProp(t_stemcell, "name", t_stemcell_name_ref);
		addProp(t_stemcell, "os", t_stemcell_os_ref);
		t_stemcell.requireOneOf("name", "os");
		addProp(v2Schema, "stemcells", f.yseq(t_stemcell)).isRequired(true);

		YBeanType t_update = f.ybean("Update");
		addProp(t_update, "canaries", t_strictly_pos_integer).isRequired(true);
		addProp(t_update, "max_in_flight", t_pos_integer).isRequired(true);
		addProp(t_update, "canary_watch_time", t_integer_or_range).isRequired(true);
		addProp(t_update, "update_watch_time", t_integer_or_range).isRequired(true);
		addProp(t_update, "serial", t_boolean);
		addProp(v2Schema, "update", t_update).isRequired(true);

		YBeanType t_job = f.ybean("Job");
		addProp(t_job, "name", t_ne_string).isRequired(true);
		addProp(t_job, "release", t_release_name_ref).isRequired(true);
		addProp(t_job, "consumes", t_params);
		addProp(t_job, "provides", t_params);
		addProp(t_job, "properties", t_params);

		YBeanType t_instance_group = f.ybean("InstanceGroup");
		addProp(t_instance_group, "name", t_instance_group_name_def).isPrimary(true);
		addProp(t_instance_group, "azs", f.yseq(t_az)).isRequired(true);
		addProp(t_instance_group, "instances", t_pos_integer).isRequired(true); //Strictly positive? Or zero is okay?
		addProp(t_instance_group, "jobs", f.yseq(t_job)).isRequired(true);
		addProp(t_instance_group, "vm_type", t_vm_type).isRequired(true);
		addProp(t_instance_group, "vm_extensions", f.yseq(t_vm_extension));
		addProp(t_instance_group, "stemcell", t_stemcell_alias_ref).isRequired(true);
		addProp(t_instance_group, "persistent_disk_type", t_disk_type);
		addProp(t_instance_group, "networks", f.yseq(t_network)).isRequired(true);
		YType t_update_override = f.ybean("UpdateOverrides", t_update.getProperties()
				.stream()
				.map((YTypedProperty prop) ->
					f.yprop(prop).isRequired(false)
				)
				.toArray(sz -> new YTypedProperty[sz])
		);
		addProp(t_instance_group, "update", t_update_override);
		YType t_migration = t_params; //TODO: https://www.pivotaltracker.com/story/show/148712595
		addProp(t_instance_group, "migrated_from", f.yseq(t_migration));
		addProp(t_instance_group, "lifecycle", f.yenum("WorkloadType", "service", "errand"));
		addProp(t_instance_group, "properties", t_params).isDeprecated("Deprecated in favor of job level properties and links");
		addProp(t_instance_group, "env", t_instance_group_env);

		addProp(v2Schema, "instance_groups", f.yseq(t_instance_group)).isRequired(true);
		addProp(v2Schema, "properties", t_params).isDeprecated("Deprecated in favor of job level properties and links");

		YBeanType t_variable = f.ybean("Variable");
		addProp(t_variable, "name", t_var_name_def).isPrimary(true);
		YType t_variable_type = f.yenum("VariableType", "certificate", "password", "rsa", "ssh")
				.parseWith(ValueParsers.NE_STRING); //Override the parser -> no errors / warnings... in theory there could be other valid values.
		addProp(t_variable, "type", t_variable_type).isRequired(true);
		addProp(t_variable, "options", t_params);
		addProp(v2Schema, "variables", f.yseq(t_variable));

		addProp(v2Schema, "tags", t_params);

		for (String v1Prop : DEPRECATED_V1_PROPS) {
			addProp(v2Schema, v1Prop, t_any).isDeprecated("Deprecated: '"+v1Prop+"' is a V1 schema property. Consider migrating your deployment manifest to V2");
		}

		for (YType defType : getDefinitionTypes()) {
			v2Schema.require(Constraints.uniqueDefinition(this.astTypes, defType, YamlSchemaProblems.problemType("BOSH_DUPLICATE_"+defType, BoshSchemaProblems.CATEGORY)));
		}
		return v2Schema;
	}

	private StemcellModel getCurrentStemcell(DynamicSchemaContext dc) throws Exception {
		YamlPath path = dc.getPath();
		YamlFileAST ast = asts.getAst(dc.getDocument(), true);
		return new StemcellModel(path.dropLast().traverseToNode(ast));
	}

	private String getCurrentEntityProperty(DynamicSchemaContext dc, String propName) {
		YamlPath path = dc.getPath();
		YamlFileAST ast = asts.getSafeAst(dc.getDocument(), true);
		if (ast!=null) {
			return NodeUtil.asScalar(path.dropLast().thenValAt(propName).traverseToNode(ast));
		}
		return null;
	}

	@Override
	public YType getTopLevelType() {
		return TOPLEVEL_TYPE;
	}

	@Override
	public YTypeUtil getTypeUtil() {
		return TYPE_UTIL;
	}

	@Override
	protected String getResourcePathPrefix() {
		return "/deployment-manifest/";
	}

	public Collection<YType> getDefinitionTypes() {
		if (definitionTypes==null) {
			definitionTypes = getDefAndRefTypes().stream()
					.map(pair -> pair.getLeft())
					.collect(CollectorUtil.toImmutableList());
		}
		return definitionTypes;
	}

	/**
	 * @return Pairs of types. Each pair contains a 'def' type and a 'ref' type. Nodes with the ref-type
	 * shall be interpreted as reference to a corresponding node with the 'def' type if the def and ref node
	 * contain the same scalar value.
	 */
	public Collection<Pair<YType, YType>> getDefAndRefTypes() {
		if (defAndRefTypes==null) {
			defAndRefTypes = ImmutableList.of(
					Pair.of(t_instance_group_name_def, null),
					Pair.of(t_stemcell_alias_def, t_stemcell_alias_ref),
					Pair.of(t_release_name_def, t_release_name_ref),
					Pair.of(t_var_name_def, null)
			);
		}
		return defAndRefTypes;
	}

}
