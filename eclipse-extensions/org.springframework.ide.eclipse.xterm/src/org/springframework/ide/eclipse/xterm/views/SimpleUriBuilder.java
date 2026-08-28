/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.xterm.views;

import java.net.URLEncoder;

import org.springframework.ide.eclipse.xterm.XtermPlugin;

class SimpleUriBuilder {

	private final StringBuilder builder;

	private int numparams = 0;

	public SimpleUriBuilder(String baseUrl) {
		builder = new StringBuilder(baseUrl);
	}

	public void addParameter(String name, String paramValue) {
		try {
			if (numparams == 0) {
				builder.append("?");
			} else {
				builder.append("&");
			}
			numparams++;

			builder.append(URLEncoder.encode(name, "UTF-8"));
			if (paramValue != null) {
				builder.append("=");
				builder.append(URLEncoder.encode(paramValue, "UTF-8"));
			}
		} catch (Exception e) {
			XtermPlugin.log(e);
		}
	}

	@Override
	public String toString() {
		return builder.toString();
	}

}