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
package org.springframework.ide.vscode.boot.java.beans;

import org.springframework.ide.vscode.boot.index.cache.AbstractIndexCacheable;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

public class CachedBean extends AbstractIndexCacheable {
	
	private final SpringIndexElement element;

	public CachedBean(String docURI, SpringIndexElement bean) {
		super(docURI);
		this.element = bean;
	}
	
	public SpringIndexElement getBean() {
		return this.element;
	}

}
