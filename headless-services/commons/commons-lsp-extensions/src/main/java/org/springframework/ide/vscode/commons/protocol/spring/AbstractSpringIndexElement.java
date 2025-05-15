/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSpringIndexElement implements SpringIndexElement {
	
	public static final List<SpringIndexElement> NO_CHILDREN = List.of();
	private List<SpringIndexElement> children;

	public AbstractSpringIndexElement() {
		this.children = NO_CHILDREN;
	}

	@Override
	public List<SpringIndexElement> getChildren() {
		return Collections.unmodifiableList(this.children);
	}
	
	public void addChild(SpringIndexElement child) {
		if (children == NO_CHILDREN) {
			children = new ArrayList<>();
		}

		this.children.add(child);
	}
	
	public void removeChild(SpringIndexElement doc) {
		boolean removed = this.children.remove(doc);
		
		if (removed && this.children.size() == 0) {
			this.children = NO_CHILDREN;
		}
	}



}
