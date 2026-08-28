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
package org.springframework.ide.vscode.commons.cloudfoundry.client;
public enum LoginMethod  {
	PASSWORD,
	TEMPORARY_CODE;

	public String getLabel() {
		String[] pieces = name().split("_");
		StringBuilder label = new StringBuilder();
		for (int i = 0; i < pieces.length; i++) {
			if (i > 0) {
				label.append(" ");
			}
			label.append(pieces[i].toLowerCase());
		}
		return label.toString();
	}
}