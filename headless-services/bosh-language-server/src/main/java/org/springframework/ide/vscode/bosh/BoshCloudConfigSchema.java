/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
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

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ide.vscode.bosh.models.BoshModels;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.Lazy;
import org.springframework.ide.vscode.commons.util.PartialCollection;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParsers;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.AbstractType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YAtomicType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YBeanType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.schema.YamlSchema;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraints;

import com.google.common.collect.ImmutableList;

public class BoshCloudConfigSchema extends SchemaSupport implements YamlSchema {

	private final YBeanType toplevelType;
	private final YType t_az_ref;
	private final YType t_az_def;
	private final YType t_vm_type_ref;
	private final YType t_vm_type_def;
	private final YType t_network_ref;
	private final YType t_network_def;
	private final YType t_disk_type_def;
	private final YType t_disk_type_ref;
	private final YType t_vm_extension_def;
	private final YType t_vm_extension_ref;
	private Collection<YType> definitionTypes;
	private final Lazy<Collection<Pair<YType, YType>>> defAndRefTypes = new Lazy<>();
	private AbstractType t_any;
	private AbstractType t_ne_string;
	private YAtomicType t_boolean;
	private AbstractType t_pos_integer;
	private YType t_params;
	private YType t_ip_address;
	private YType t_ip_range;
	private YType t_ip_address_or_range;

	public BoshCloudConfigSchema(YTypeFactory f, BoshModels models) {
		super(f);

		t_any = f.yany("Object");
		t_ne_string = f.yatomic("String")
			.parseWith(ValueParsers.NE_STRING);
		t_boolean = f.yenum("boolean", "true", "false");
		t_pos_integer = f.yatomic("Positive Integer")
				.parseWith(ValueParsers.POS_INTEGER);
		t_params = f.ymap(t_ne_string, t_any);

		t_az_def = f.yatomic("AZName").parseWith(ValueParsers.NE_STRING);
		t_az_ref = f.yenumFromDynamicValues("AZName", (dc) -> PartialCollection.compute(() ->
			models.astTypes.getDefinedNames(dc, t_az_def)
		));
		t_vm_type_def = f.yatomic("VMTypeName").parseWith(ValueParsers.NE_STRING);
		t_vm_type_ref = f.yenumFromDynamicValues("VMTypeName", (dc) -> PartialCollection.compute(() ->
			models.astTypes.getDefinedNames(dc, t_vm_type_def)
		));
		t_network_def = f.yatomic("NetworkName").parseWith(ValueParsers.NE_STRING);
		t_network_ref = f.yenumFromDynamicValues("NetworkName",  (dc) -> PartialCollection.compute(() ->
			models.astTypes.getDefinedNames(dc, t_network_def)
		));
		t_ip_address = t_ne_string; //TODO? Syntax for ip addresses like 192.168.1.22 ?
		t_ip_range = t_ne_string; //TODO? Syntax for ip ranges like 10.10.0.0/22 ?
		t_ip_address_or_range = t_ne_string; //TODO?

		t_disk_type_def = f.yatomic("DiskTypeName").parseWith(ValueParsers.NE_STRING);
		t_disk_type_ref = f.yenumFromDynamicValues("DiskTypeName",(dc) -> PartialCollection.compute(() ->
			models.astTypes.getDefinedNames(dc, t_disk_type_def)
		));

		t_vm_extension_def = f.yatomic("VMExtensionName").parseWith(ValueParsers.NE_STRING);
		t_vm_extension_ref = f.yenumFromDynamicValues("VMExtensionName",(dc) -> PartialCollection.compute(() ->
			models.astTypes.getDefinedNames(dc, t_vm_extension_def)
		));

		YType t_network = createNetworkBlockSchema(models);

		YBeanType t_az = f.ybean("AZ");
		addProp(t_az, "name", t_az_def).isPrimary(true);
		addProp(t_az, "cloud_properties", t_params);

		YBeanType t_compilation = f.ybean("Compilation");
		addProp(t_compilation, "workers", t_pos_integer).isRequired(true);
		addProp(t_compilation, "reuse_compilation_vms", t_boolean);
		addProp(t_compilation, "az", t_az_ref).isRequired(true);
		addProp(t_compilation, "vm_type", t_vm_type_ref).isRequired(true);
		addProp(t_compilation, "network", t_network_ref).isRequired(true);

		YBeanType t_vm_type = f.ybean("VMType");
		addProp(t_vm_type, "name", t_vm_type_def).isPrimary(true);
		addProp(t_vm_type, "cloud_properties", t_params);

		YBeanType t_disk_type = f.ybean("DiskType");
		addProp(t_disk_type, "name", t_disk_type_def).isPrimary(true);
		addProp(t_disk_type, "disk_size", t_pos_integer).isRequired(true);
		addProp(t_disk_type, "cloud_properties", t_params);

		YBeanType t_vm_extension = f.ybean("VMExtension");
		addProp(t_vm_extension, "name", t_vm_extension_def).isPrimary(true);
		addProp(t_vm_extension, "cloud_properties", t_params);

		this.toplevelType = f.ybean("CloudConfig");
		addProp(toplevelType, "azs", f.yseq(t_az).notEmpty()).isRequired(true);
		addProp(toplevelType, "networks", f.yseq(t_network).notEmpty()).isRequired(true);
		addProp(toplevelType, "vm_types", f.yseq(t_vm_type).notEmpty()).isRequired(true);
		addProp(toplevelType, "vm_extensions", f.yseq(t_vm_extension));
		addProp(toplevelType, "disk_types", f.yseq(t_disk_type).notEmpty()).isRequired(true);
		addProp(toplevelType, "compilation", t_compilation).isRequired(true);

		ASTTypeCache astTypes = models.astTypes;
		for (YType defType : getDefinitionTypes()) {
			toplevelType.require(Constraints.uniqueDefinition(astTypes, defType, YamlSchemaProblems.problemType("BOSH_CC_DUPLICATE_"+defType, BoshSchemaProblems.CATEGORY)));
		}
	}

	private YType createNetworkBlockSchema(BoshModels models) {
		AbstractType t_manual_nw = f.ybean("ManualNetwork");
		{
			YBeanType t_subnet = f.ybean("Subnet[Manual]");
			addProp(t_subnet, "range", t_ip_range).isRequired(true);
			addProp(t_subnet, "gateway", t_ip_address).isRequired(true);
			addProp(t_subnet, "dns", f.yseq(t_ip_address));
			addProp(t_subnet, "reserved", f.yseq(t_ip_address_or_range));
			addProp(t_subnet, "static", f.yseq(t_ip_address_or_range));
			addProp(t_subnet, "az", t_az_ref);
			addProp(t_subnet, "azs", f.yseq(t_az_ref));
			addProp(t_subnet, "cloud_properties", t_params);

			addProp(t_manual_nw, "subnets", f.yseq(t_subnet)).isRequired(true);
		}

		AbstractType t_vip_nw = f.ybean("VipNetwork");
		addProp(t_vip_nw, "cloud_properties", t_params);

		AbstractType t_dynamic_nw = f.ybean("DynamicNetwork");
		addProp(t_dynamic_nw, "dns", f.yseq(t_ip_address));
		addProp(t_dynamic_nw, "cloud_properties", t_params);
		{
			YBeanType t_subnet = f.ybean("Subnet[Dynamic]");
			addProp(t_subnet, "dns", f.yseq(t_ip_address));
			addProp(t_subnet, "az", t_az_ref);
			addProp(t_subnet, "azs", f.yseq(t_az_ref));
			addProp(t_subnet, "cloud_properties", t_params);

			addProp(t_dynamic_nw, "subnets", f.yseq(t_subnet));
		}

		t_dynamic_nw.require(Constraints.mutuallyExclusive("dns", "subnets"));
		t_dynamic_nw.require(Constraints.mutuallyExclusive("cloud_properties", "subnets"));

		AbstractType t_network = f.contextAware("Network", dc -> {
			String type = models.getTypeTag(dc);
			if (StringUtil.hasText(type)) {
				switch (type) {
				case "manual":
					return t_manual_nw;
				case "dynamic":
					return t_dynamic_nw;
				case "vip":
					return t_vip_nw;
				default:
				}
			}
			return null;
		})
		.treatAsBean();

		//Add shared properties to the 'super' type.
		addProp(t_network, "name", t_network_def).isPrimary(true);
		addProp(t_network, "type", f.yenum("NetworkType", "manual", "dynamic", "vip")).isRequired(true);

		//Add shared properties to all 'sub' types.
		for (AbstractType subtype : ImmutableList.of(t_dynamic_nw, t_manual_nw, t_vip_nw)) {
			for (YTypedProperty sharedProp : t_network.getProperties()) {
				subtype.addProperty(sharedProp);
			}
		}
		return t_network;
	}

	/**
	 * @return Pairs of types. Each pair contains a 'def' type and a 'ref' type. Nodes with the ref-type
	 * shall be interpreted as reference to a corresponding node with the 'def' type if the def and ref node
	 * contain the same scalar value.
	 */
	public Collection<Pair<YType, YType>> getDefAndRefTypes() {
		return defAndRefTypes.load(() -> ImmutableList.of(
			Pair.of(t_vm_type_def, t_vm_type_ref),
			Pair.of(t_network_def, t_network_ref),
			Pair.of(t_az_def, t_az_ref),
			Pair.of(t_disk_type_def, t_disk_type_ref),
			Pair.of(t_vm_extension_def, t_vm_extension_ref)
		));
	}

	public Collection<YType> getDefinitionTypes() {
		if (definitionTypes == null) {
			definitionTypes = getDefAndRefTypes().stream()
					.map(pair -> pair.getLeft())
					.collect(CollectorUtil.toImmutableList());
		}
		return definitionTypes;
	}

	@Override
	public YType getTopLevelType() {
		return toplevelType;
	}

	@Override
	public YTypeUtil getTypeUtil() {
		return f.TYPE_UTIL;
	}

	@Override
	protected String getResourcePathPrefix() {
		return "/cloud-config/";
	}
}
