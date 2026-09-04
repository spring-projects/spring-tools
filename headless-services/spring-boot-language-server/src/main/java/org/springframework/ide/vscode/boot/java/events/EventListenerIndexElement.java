/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.events;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

/**
 * @author Martin Lippert
 */
public class EventListenerIndexElement extends AbstractSpringIndexElement implements SymbolElement {
	
	private final String eventType;
	private final Location location;
	private final String containerBeanType;
	private final AnnotationMetadata[] annotations;
	private final String contentHash;

	public EventListenerIndexElement(String eventType, Location location, String containerBeanType, AnnotationMetadata[] annotations, String contentHash) {
		this.eventType = eventType;
		this.location = location;
		this.containerBeanType = containerBeanType;
		this.annotations = annotations;
		this.contentHash = contentHash;
	}

	public String getEventType() {
		return eventType;
	}
	
	public AnnotationMetadata[] getAnnotations() {
		return annotations;
	}

	public Location getLocation() {
		return location;
	}

	public String getContainerBeanType() {
		return containerBeanType;
	}

	/**
	 * Hashed over the whole listener method, so that changes to its body show up as changes of
	 * the listener.
	 */
	@Override
	public String getContentHash() {
		return contentHash;
	}

	@Override
	public DocumentSymbol getDocumentSymbol() {
		DocumentSymbol symbol = new DocumentSymbol();
		symbol.setName("listens on: " + getSimpleType(eventType));
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
