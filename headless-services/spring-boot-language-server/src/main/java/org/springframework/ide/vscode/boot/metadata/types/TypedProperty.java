/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.metadata.types;

import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation;
import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation.Level;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;

/**
 * Represents a property on a Type that can be accessed by name.
 *
 * @author Kris De Volder
 */
public class TypedProperty implements YTypedProperty {

	/**
	 * The name of the property
	 */
	private final String name;

	/**
	 * The type of value associated with the property.
	 */
	private final Type type;

	/**
	 * Provides a description for this property.
	 */
	private final Renderable descriptionProvider;

	private final Deprecation deprecation;

	public TypedProperty(String name, Type type, Deprecation deprecation) {
		this(name, type, Renderables.NO_DESCRIPTION, deprecation);
	}

	public TypedProperty(String name, Type type, Renderable descriptionProvider, Deprecation deprecation) {
		this.name = name;
		this.type = type;
		this.descriptionProvider = descriptionProvider;
		this.deprecation = deprecation;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public String toString() {
		return name + "::" + type;
	}

	@Override
	public Renderable getDescription() {
		//TODO: real implementation that somehow gets this from somewhere (i.e. the JavaDoc)
		// Note that presently the application.yml and application.properties editor do not actually
		// use this description provider but produce hover infos in a different way (so this is only
		// used in Schema-based content assist, reconciling and hovering.
		//So in that sense putting a good implementation here is kind of pointless right now.
		//More refactoring needs to be done to also make use of this.
		return descriptionProvider;
	}

	public static Type typeOf(TypedProperty typedProperty) {
		if (typedProperty != null) {
			return typedProperty.getType();
		}
		return null;
	}

	@Override
	public boolean isDeprecated() {
		return deprecation != null;
	}

	public String getDeprecationReplacement() {
		if (deprecation != null) {
			return deprecation.getReplacement();
		}
		return null;
	}

	public String getDeprecationReason() {
		if (deprecation != null) {
			return deprecation.getReason();
		}
		return null;
	}

	public Level getDeprecationLevel() {
		return deprecation == null ? null : deprecation.getLevel();
	}

	public Deprecation getDeprecation() {
		return deprecation;
	}

}
