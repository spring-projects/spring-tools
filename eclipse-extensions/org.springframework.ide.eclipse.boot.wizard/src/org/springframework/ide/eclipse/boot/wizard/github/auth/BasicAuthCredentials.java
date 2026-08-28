/*******************************************************************************
 *  Copyright (c) 2013-2017 Pivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.github.auth;

import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;

/**
 * Uses basic authentication username and passwd to access github rest api.
 *
 * @author Kris De Volder
 */
public class BasicAuthCredentials extends Credentials {

	private final Pattern host;
	private final String username;
	private final String passwd;

	public BasicAuthCredentials(Pattern host, String username, String passwd) {
		this.host = host;
		this.username = username;
		this.passwd = passwd;
	}

	@Override
	public String toString() {
		return "BasicAuthCredentials("+username+")";
	}

	private String computeAuthString() throws UnsupportedEncodingException {
		String authorisation = username + ":" + passwd;
		byte[] encodedAuthorisation = Base64.encodeBase64(authorisation.getBytes("utf8"));
		String authString = "Basic " + new String(encodedAuthorisation);
		return authString;
	}

	@Override
	public void apply(URLConnection conn) {
		try {
			if (matchHost(conn.getURL().getHost())) {
				conn.setRequestProperty("Authorization", computeAuthString());
			}
		} catch (UnsupportedEncodingException e) {
			//Shouldn't really be possible...
			BootWizardActivator.log(e);
		}
	}

	private boolean matchHost(String host) {
		if (this.host != null) {
			return this.host.matcher(host).matches();
		} else {
			return true;
		}
	}
}
