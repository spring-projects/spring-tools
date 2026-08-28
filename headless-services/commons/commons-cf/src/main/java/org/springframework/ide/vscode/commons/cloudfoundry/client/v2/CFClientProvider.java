/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.cloudfoundry.client.v2;

import java.time.Duration;
import java.util.Optional;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.ProxyConfiguration;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.OneTimePasscodeTokenProvider;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.reactor.tokenprovider.RefreshTokenGrantTokenProvider;
import org.cloudfoundry.reactor.uaa.ReactorUaaClient;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CFClientParams;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CFCredentials;

public class CFClientProvider {

		final ConnectionContext connection;
		final TokenProvider tokenProvider;

		//Note the three client objects below are 'stateless' wrappers and it would be
		// fine to recreate as needed instead of store them

		final CloudFoundryClient client;
		final ReactorUaaClient uaaClient;
		final ReactorDopplerClient doppler;

		private ProxyConfiguration getProxy(String host) {
			// TODO: enable proxy support for vscode. The code below retrieves proxy via Eclipse proxy service thus
			// not applicable when this is used outside of Eclipse
//			try {
//				if (StringUtils.hasText(host)) {
//					URL url = new URL("https://"+host);
//					// In certain cases, the activator would have stopped and the plugin may
//					// no longer be available. Usually onl happens on shutdown.
//					BootDashActivator plugin = BootDashActivator.getDefault();
//					if (plugin != null) {
//						IProxyService proxyService = plugin.getProxyService();
//						if (proxyService != null) {
//							IProxyData[] selectedProxies = proxyService.select(url.toURI());
//
//							// No proxy configured or not found
//							if (selectedProxies == null || selectedProxies.length == 0) {
//								return null;
//							}
//
//							IProxyData data = selectedProxies[0];
//							int proxyPort = data.getPort();
//							String proxyHost = data.getHost();
//							String user = data.getUserId();
//							String password = data.getPassword();
//							if (proxyHost != null) {
//								return ProxyConfiguration.builder()
//										.host(proxyHost)
//										.port(proxyPort == -1?Optional.empty():Optional.of(proxyPort))
//										.username(Optional.ofNullable(user))
//										.password(Optional.ofNullable(password))
//										.build();
////							return proxyHost != null ? new HttpProxyConfiguration(proxyHost, proxyPort,
////									data.isRequiresAuthentication(), user, password) : null;
//							}
//						}
//					}
//				}
//			} catch (Exception e) {
//				Log.log(e);
//			}
			return null;
		}

		public CFClientProvider(CFClientParams params) {
			long sslTimeout = Long.getLong("sts.bootdash.cf.client.ssl.handshake.timeout", 60); //TODO: make a preference for this?
			Optional<Boolean> keepAlive = getBooleanSystemProp("http.keepAlive");
			CloudFoundryClientCache.debug("cf client keepAlive = "+keepAlive);
			connection = DefaultConnectionContext.builder()
					.proxyConfiguration(Optional.ofNullable(getProxy(params.getHost())))
					.apiHost(params.getHost())
					.sslHandshakeTimeout(Duration.ofSeconds(sslTimeout))
					.keepAlive(keepAlive)
					.skipSslValidation(params.skipSslValidation())
					.build();

			tokenProvider = createTokenProvider(params);

			client = ReactorCloudFoundryClient.builder()
					.connectionContext(connection)
					.tokenProvider(tokenProvider)
					.build();

			uaaClient = ReactorUaaClient.builder()
					.connectionContext(connection)
					.tokenProvider(tokenProvider)
					.build();

			doppler = ReactorDopplerClient.builder()
					.connectionContext(connection)
					.tokenProvider(tokenProvider)
					.build();
		}

		private TokenProvider createTokenProvider(CFClientParams params) {
			CFCredentials creds = params.getCredentials();
			switch (creds.getType()) {
			case PASSWORD:
				return PasswordGrantTokenProvider.builder()
						.username(params.getUsername())
						.password(creds.getSecret())
						.build();
			case REFRESH_TOKEN:
				return RefreshTokenGrantTokenProvider.builder()
						.token(creds.getSecret())
						.build();
			case TEMPORARY_CODE:
				return OneTimePasscodeTokenProvider.builder()
						.passcode(creds.getSecret())
						.build();
			default:
				throw new IllegalStateException("BUG! Missing switch case?");
			}
		}

		private Optional<Boolean> getBooleanSystemProp(String name) {
			String str = System.getProperty(name);
			if (str != null) {
				return Optional.of(Boolean.valueOf(str));
			}
			return Optional.empty();
		}
	}