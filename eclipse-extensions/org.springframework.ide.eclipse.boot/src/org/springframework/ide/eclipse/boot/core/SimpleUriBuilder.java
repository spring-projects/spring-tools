/*******************************************************************************
 * Copyright (c) 2013, 2016 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import java.net.URLEncoder;

import org.springframework.ide.eclipse.boot.util.Log;

/**
 * This is a simple replacement for org.apache.http.client.utils.URIBuilder
 * Why roll our own? Because we can't yet depend on the later versions of appache httpcomponents
 * in STS because an older version ships with Eclipse.
 * <p>
 * Mixing old-and new appache httplibs is suspected of causing this bug:
 * https://issuetracker.springsource.com/browse/STS-3647
 *
 * When Eclipse moves on to more recent versions of httpcomponents we can delete this class
 * and use its UriBuilder instead.
 *
 * @author Kris De Volder
 */
public class SimpleUriBuilder {

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
			Log.log(e);
		}
	}

	@Override
	public String toString() {
		return builder.toString();
	}

}
