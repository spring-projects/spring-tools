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
package org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget;

import org.springframework.ide.vscode.commons.cloudfoundry.client.LoginMethod;
import org.springframework.ide.vscode.commons.util.Assert;

public class CFCredentials {

	public enum CFCredentialType {
		PASSWORD,
		TEMPORARY_CODE,
		REFRESH_TOKEN;

		public LoginMethod toLoginMethod() {
			switch (this) {
			case PASSWORD:
				return LoginMethod.PASSWORD;
			case TEMPORARY_CODE:
				return LoginMethod.TEMPORARY_CODE;
			default:
				return null;
			}
		}
	}

	private final CFCredentialType type;
	private final String secret;

	/**
	 * Deprecated, use fromLogin instead
	 */
	@Deprecated
	public static CFCredentials fromPassword(String password) {
		return fromLogin(LoginMethod.PASSWORD, password);
	}


	public static CFCredentials fromLogin(LoginMethod method, String secret) {
		CFCredentialType type;
		switch (method) {
		case PASSWORD:
			type = CFCredentialType.PASSWORD;
			break;
		case TEMPORARY_CODE:
			type = CFCredentialType.TEMPORARY_CODE;
			break;
		default:
			throw new IllegalStateException("Bug! Missing switch case?");
		}
		return new CFCredentials(type, secret);
	}

	public static CFCredentials fromRefreshToken(String refreshToken) {
		Assert.isNotNull(refreshToken);
		return new CFCredentials(CFCredentialType.REFRESH_TOKEN, refreshToken);
	}

	public String getSecret() {
		return secret;
	}

	/////////////////////////////////////////////////////////////////////////


	/**
	 * Private constuctor, use static `fromXXX` factory methods instead.
	 */
	private CFCredentials(CFCredentialType type, String secret) {
		this.type = type;
		this.secret = secret;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((secret == null) ? 0 : secret.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CFCredentials other = (CFCredentials) obj;
		if (secret == null) {
			if (other.secret != null)
				return false;
		} else if (!secret.equals(other.secret))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "CFCredentials [type=" + type + ", secret=" + hidePassword(type, secret) + "]";
	}

	private String hidePassword(CFCredentialType type, String password) {
		if (password == null) {
			return null;
		}
		return (type == CFCredentialType.PASSWORD || type == CFCredentialType.REFRESH_TOKEN)
				? "****"
				: password;
	}


	public CFCredentialType getType() {
		return type;
	}

	public static CFCredentials fromSsoToken(String ssoToken) {
		return CFCredentials.fromLogin(LoginMethod.TEMPORARY_CODE, ssoToken);
	}
}