/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import java.util.Set;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;

import com.google.gson.Gson;

public class Bean extends AbstractSpringIndexElement implements SymbolElement {
	
	private final String name;
	private final String type;
	private final Location location;
	private final InjectionPoint[] injectionPoints;
	private final Set<String> supertypes;
	private final AnnotationMetadata[] annotations;
	private final boolean isConfiguration;
	private final String symbolLabel;

	public Bean(
			String name,
			String type,
			Location location,
			InjectionPoint[] injectionPoints,
			Set<String> supertypes,
			AnnotationMetadata[] annotations,
			boolean isConfiguration,
			String symbolLabel) {
		
		this.name = name;
		this.type = type;
		this.location = location;
		this.isConfiguration = isConfiguration;
		this.symbolLabel = symbolLabel;
		
		if (injectionPoints != null && injectionPoints.length == 0) {
			this.injectionPoints = null;
		}
		else {
			this.injectionPoints = injectionPoints;
		}
		
		if (supertypes != null && supertypes.size() == 0) {
			this.supertypes = null;
		}
		else {
			this.supertypes = supertypes;
		}

		if (annotations != null && annotations.length == 0) {
			this.annotations = null;
		}
		else {
			this.annotations = annotations;
		}
	}
	
	public String getName() {
		return name;
	}
	
	public String getType() {
		return type;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public InjectionPoint[] getInjectionPoints() {
		return injectionPoints == null ? DefaultValues.EMPTY_INJECTION_POINTS : injectionPoints;
	}

	public boolean isTypeCompatibleWith(String type) {
		return type != null && ((this.type != null && this.type.equals(type)) || (getSupertypes().contains(type)));
	}
	
	public AnnotationMetadata[] getAnnotations() {
		return annotations == null ? DefaultValues.EMPTY_ANNOTATIONS : annotations;
	}
	
	public boolean isConfiguration() {
		return isConfiguration;
	}
	
	public Set<String> getSupertypes() {
		return supertypes == null ? DefaultValues.EMPTY_SUPERTYPES : supertypes;
	}

	public String getSymbolLabel() {
		return symbolLabel;
	}
	
	@Override
	public DocumentSymbol getDocumentSymbol() {
		DocumentSymbol symbol = new DocumentSymbol();
		
		symbol.setName(this.symbolLabel);
		symbol.setKind(SymbolKind.Class);
		symbol.setRange(this.location.getRange());
		symbol.setSelectionRange(this.location.getRange());
		
		return symbol;
	}

	@Override
	public String toString() {
		Gson gson = new Gson();
		return gson.toJson(this);
	}

}
