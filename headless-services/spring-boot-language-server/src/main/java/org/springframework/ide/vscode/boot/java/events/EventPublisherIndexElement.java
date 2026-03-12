/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.events;

import java.util.Set;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

/**
 * @author Martin Lippert
 */
public class EventPublisherIndexElement extends AbstractSpringIndexElement implements SymbolElement {
	
	private final String eventType;
	private final Location location;
	private final Set<String> eventTypesFromHierarchy;

	public EventPublisherIndexElement(String eventType, Location location, Set<String> eventTypesFromHierarchy) {
		this.eventType = eventType;
		this.location = location;
		this.eventTypesFromHierarchy = eventTypesFromHierarchy;
	}

	public String getEventType() {
		return eventType;
	}
	
	public Location getLocation() {
		return location;
	}

	public Set<String> getEventTypesFromHierarchy() {
		return eventTypesFromHierarchy;
	}

	@Override
	public DocumentSymbol getDocumentSymbol() {
		DocumentSymbol symbol = new DocumentSymbol();
		symbol.setName("publishes: " + getSimpleType(eventType));
		symbol.setKind(SymbolKind.Event);
		symbol.setRange(location.getRange());
		symbol.setSelectionRange(location.getRange());
		
		return symbol;
	}
	
	private String getSimpleType(String fullyQualifiedType) {
		int index = fullyQualifiedType.lastIndexOf('.');
		if (index > 0 && index < fullyQualifiedType.length()) {
			return fullyQualifiedType.substring(index + 1);
		}
		else {
			return fullyQualifiedType;
		}
	}

}
