/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;

@Component
public class ClientHttpRequestFactoryProvider {

	private static final Logger log = LoggerFactory.getLogger(ClientHttpRequestFactoryProvider.class);

	private BootJavaConfig config;

	private HostExclusions proxyExclusions;

	public ClientHttpRequestFactoryProvider(BootJavaConfig config) {
		this.config = config;
		this.proxyExclusions = null;
		config.addListener(v -> {
			synchronized(ClientHttpRequestFactoryProvider.this) {
				proxyExclusions = null;
			}
		});
	}
	
	record HostExclusions(Set<String> hosts, List<Pattern> regexs) {
		
		public HostExclusions(Collection<String> exclusions) {
			this(new HashSet<>(), new ArrayList<>());
			for (String s : exclusions) {
				if (s.contains("*")) {
					// Regex
					String regexStr = s.replace(".", "\\.").replace("*", ".*");
					try {
						regexs.add(Pattern.compile(regexStr));
					} catch (PatternSyntaxException e) {
						log.error("Unnable to compile Regular Expression for %s".formatted(s), e);
					}
				} else {
					// Exact host string
					hosts.add(s);
				}
			}
		}
		
		boolean contains(String host) {
			if (hosts.contains(host)) {
				return true;
			}
			for (Pattern p : regexs) {
				if (p.matcher(host).matches()) {
					return true;
				}
			}
			return false;
		}
	}
	
	private synchronized HostExclusions getProxyExclusions() {
		if (proxyExclusions == null) {
			proxyExclusions = new HostExclusions(config.getRawSettings().getStringSet("http", "noProxy"));
		}
		return proxyExclusions;
	}
		
	public ClientHttpRequestFactory createRequestFactory(String host) {
		String proxyUrlStr = config.getRawSettings().getString("http", "proxy");
		if (proxyUrlStr == null || proxyUrlStr.isBlank()) {
			proxyUrlStr = getProxyUrlFromEnv();
		}
		
		Builder clientBuilder = HttpClient.newBuilder();
		if (proxyUrlStr != null && !proxyUrlStr.isBlank()) {
			if (!"localhost".equals(host) && !"127.0.0.1".equals(host) && !getProxyExclusions().contains(host)) {
				try {
					URL proxyUrl = URI.create(proxyUrlStr).toURL();
					clientBuilder.proxy(new ProxySelector() {

						@Override
						public List<Proxy> select(URI uri) {
							try {
								URL url = uri.toURL();
								if (url.getProtocol().startsWith("http")) {
									return List.of(new Proxy(Proxy.Type.HTTP,
											new InetSocketAddress(proxyUrl.getHost(),
													proxyUrl.getPort() < 0
															? "https".equals(proxyUrl.getProtocol()) ? 443 : 80
															: proxyUrl.getPort())));
								} else if (url.getProtocol().startsWith("sock")) {
									return List.of(new Proxy(Proxy.Type.SOCKS,
											new InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort())));
								}
							} catch (MalformedURLException e) {
								log.error("", e);
							}
							return Collections.emptyList();
						}

						@Override
						public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
						}
					});
					String username = null, password = null;
					if (proxyUrl.getUserInfo() == null || proxyUrl.getUserInfo().isBlank()) {
						username = config.getRawSettings().getString("http", "proxy-user");
						password = config.getRawSettings().getString("http", "proxy-password");
					} else {
						String userInfo = proxyUrl.getUserInfo();
						int idx = userInfo.indexOf(':');
						if (idx > 0 && idx < userInfo.length()) {
							username = userInfo.substring(0, idx);
							password = userInfo.substring(idx + 1);
						}
					}
					if (username != null && password != null && !username.isEmpty()) {
						final String user = username;
						final String pass = password;
						clientBuilder.authenticator(new Authenticator() {
							@Override
							protected PasswordAuthentication getPasswordAuthentication() {
								return new PasswordAuthentication(user, pass.toCharArray());
							}

						});
					}
				} catch (MalformedURLException e) {
					log.error("", e);
				}
			}
		}
		return new JdkClientHttpRequestFactory(clientBuilder.build());
	}
	
	private static String getProxyUrlFromEnv() {
		log.info("Environment size: " + System.getenv().size());
		log.info("Env var https_proxy: " + System.getenv("https_proxy"));
		String proxyUrl = System.getenv("https_proxy");
		if (proxyUrl == null || proxyUrl.isBlank()) {
			proxyUrl = System.getenv("HTTPS_PROXY");
		}
		if (proxyUrl == null || proxyUrl.isBlank()) {
			proxyUrl = System.getenv("http_proxy");
		}
		if (proxyUrl == null || proxyUrl.isBlank()) {
			proxyUrl = System.getenv("HTTP_PROXY");
		}
		log.info("Proxy URL from env: " + proxyUrl);
		return proxyUrl;
		
	}
	
}
