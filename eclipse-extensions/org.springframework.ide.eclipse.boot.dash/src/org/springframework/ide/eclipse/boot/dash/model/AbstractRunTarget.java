/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import static org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn.NAME;
import static org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn.RUN_STATE_ICN;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.resource.ImageDescriptor;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RemoteRunTarget;
import org.springframework.ide.eclipse.boot.dash.util.template.Template;
import org.springframework.ide.eclipse.boot.dash.util.template.TemplateEnv;
import org.springframework.ide.eclipse.boot.dash.util.template.Templates;
import org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;

public abstract class AbstractRunTarget<Params> extends AbstractDisposable implements RunTarget<Params>, TemplateEnv {

	private static final String NAME_TEMPLATE = "NAME_TEMPLATE";

	private static final BootDashColumn[] DEFAULT_COLS = {
			RUN_STATE_ICN,
			NAME,
	};

	private String id;
	private String name;
	private RunTargetType<Params> type;
	private IPropertyStore propertyStore;

	public AbstractRunTarget(RunTargetType<Params> type, String id, String name) {
		Assert.isNotNull(name);
		Assert.isNotNull(id);
		Assert.isNotNull(type);
		this.id = id;
		this.name = name;
		this.type = type;
		IPropertyStore typeStore = type.getPropertyStore();
		if (typeStore != null) {
			propertyStore = PropertyStores.createSubStore(id, typeStore);
		}
	}

	public AbstractRunTarget(RunTargetType<Params> type, String idAndName) {
		this(type, idAndName, idAndName);
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return "RunTarget("+getType().getName()+", "+id+")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractRunTarget other = (AbstractRunTarget) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public RunTargetType getType() {
		return type;
	}

	@Override
	public String getNameTemplate() {
		PropertyStoreApi props = getPersistentProperties();
		if (props != null) {
			String localTemplate = props.get(NAME_TEMPLATE);
			if (localTemplate != null) {
				return localTemplate;
			}
		}
		return getType().getNameTemplate();
	}

	@Override
	public void setNameTemplate(String template) throws Exception {
		getPersistentProperties().put(NAME_TEMPLATE, template);
	}

	@Override
	public String getDisplayName() {
		Template template = Templates.create(getNameTemplate());
		if (template != null) {
			return template.render(this);
		}
		return getName();
	}

	@Override
	public String getTemplateVar(char name) {
		return null;
	}

	@Override
	public boolean hasCustomNameTemplate() {
		PropertyStoreApi props = getPersistentProperties();
		if (props != null) {
			return props.get(NAME_TEMPLATE) != null;
		}
		return false;
	}

	@Override
	public IPropertyStore getPropertyStore() {
		return propertyStore;
	}

	@Override
	public PropertyStoreApi getPersistentProperties() {
		IPropertyStore store = getPropertyStore();
		if (store != null) {
			return new PropertyStoreApi(store);
		}
		return null;
	}

	@Override
	public BootDashColumn[] getDefaultColumns() {
		return DEFAULT_COLS;
	}

	@Override
	public final ImageDescriptor getIcon() {
		if (this instanceof RemoteRunTarget) {
			if (((RemoteRunTarget<?,?>)this).isConnected()) {
				return getType().getIcon();
			} else {
				return getType().getDisconnectedIcon();
			}
		}
		return getType().getIcon();
	}

}
