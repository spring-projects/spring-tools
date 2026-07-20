package org.springframework.ide.vscode.commons.cloudfoundry.client;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.cloudfoundry.operations.routes.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.StringUtil;

public class CFRouteBuilder {
	
	private static final Logger log = LoggerFactory.getLogger(CFRouteBuilder.class);
	
	private String domain;
	private String host;
	private String path;
	private int port = CFRoute.NO_PORT;
	private String fullRoute;

	public CFRoute build() {
		return new CFRouteImpl(this.domain, this.host, this.path, this.port, this.fullRoute);
	}

	public CFRouteBuilder domain(String domain) {
		this.domain = domain;
		// may seem like the more ideal place is to build the full route when
		// building the route, rather than repeating
		// the process each time a domain, host, path or port value is set
		// but the "from" option should be allowed to overwrite the full route
		// as well since it already
		// has the full value. Therefore re-construct the full value if the
		// route is being built piece by piece, but not in from
		this.fullRoute = buildRouteVal(this.host, this.domain, this.path, this.port);
		return this;
	}

	public CFRouteBuilder host(String host) {
		this.host = host;
		this.fullRoute = buildRouteVal(this.host, this.domain, this.path, this.port);
		return this;
	}

	public CFRouteBuilder path(String path) {
		this.path = path;
		this.fullRoute = buildRouteVal(this.host, this.domain, this.path, this.port);
		return this;
	}

	public CFRouteBuilder port(int port) {
		this.port = port;
		this.fullRoute = buildRouteVal(this.host, this.domain, this.path, this.port);
		return this;
	}

	public CFRouteBuilder from(Route route) {
		// Route doesn't seem to have API to get a port
		this.port = CFRoute.NO_PORT;
		this.domain = route.getDomain();
		this.host = route.getHost();
		this.path = route.getPath();
		this.fullRoute = buildRouteVal(this.host, this.domain, this.path, this.port);
		return this;
	}

	/**
	 * Builds a {@link CFRoute} given a desiredUrl. This does NOT validate, and
	 * will attempt to build a route the best way it can given the desiredUrl.
	 * External components, like the CF Java client, can then validate the
	 * CFRoute.
	 *
	 * @param desiredUrl
	 * @param domains
	 * @return this builder
	 */
	public CFRouteBuilder from(String desiredUrl, Collection<String> domains) {


		//If it is empty or null, there is nothing to build. However, be sure that the
		// full route value is non-null, even if the "components" may be null
		if (!StringUtil.hasText(desiredUrl)) {
			this.fullRoute = CFRoute.EMPTY_ROUTE;
			return this;
		} else {
			// Be sure to set the full route.
			this.fullRoute = desiredUrl;
		}

		// Based on CLI cf/actors/routes.go and testing CLI directly with
		// different "routes" values:
		// 1. Paths is not allowed in TCP route (valid TCP route:
		// "tcp.spring.io:8888")
		// 2. Ports are not allowed in HTTP route (valid HTTP route:
		// "myapps.cfapps.io/pathToApp/home")
		// 3. Schemes (e.g. "https://") are not allowed in routes values.
		// Anything that has a ":" is assumed to be TCP route followed by a port
		// 4. Route can just be domain, or host and domain
		//
		// Therefore, routes values cannot be treated as URIs or URLs, but a
		// combination of domain, host, path and port
		// NOTE: The validation above doesn't need to take place here. The
		// client or CF will validate correct combinations of routes.
		// However, We may want to implement similar
		// validation to the CF manifest editor though.

		String matchedHost = null;
		String hostAndDomain = null;

		// Split into hostDomain segment, port and path
		int slashIndex = desiredUrl.indexOf('/');
		if (slashIndex >= 0) {
			hostAndDomain = desiredUrl.substring(0, slashIndex);
			String tempPath = desiredUrl.substring(slashIndex);
			// Do not set empty strings. If there is no path, then it should be
			// null
			if (StringUtil.hasText(tempPath)) {
				this.path = tempPath;
			}
		} else {
			hostAndDomain = desiredUrl;
		}

		// CF Route builder does not validate, so don't allow exceptions to
		// prevent parsing of the route. The builder should attempt to build
		// a route the best way it can, even if it may have invalid information.
		// This allows external participants, like the CF Java client, to
		// perform validation
		try {
			String[] portSegments = hostAndDomain.split(":");
			if (portSegments.length == 2) {
				hostAndDomain = portSegments[0];
				this.port = Integer.parseInt(portSegments[1]);
			}
		} catch (NumberFormatException e) {
			log.error("", e);
		}

		this.domain = findDomain(hostAndDomain, domains);

		if (this.domain != null) {
			matchedHost = hostAndDomain.substring(0, hostAndDomain.length() - this.domain.length());
			if (matchedHost.endsWith(".")) {
				matchedHost = matchedHost.substring(0, matchedHost.length() - 1);
			}

			// Don't set empty strings
			if (StringUtil.hasText(matchedHost)) {
				this.host = matchedHost;
			}
		} else {
			// Do a basic split on '.', where first segment is the host, and the
			// rest domain
			int firstDotIndex = hostAndDomain.indexOf('.');
			if (firstDotIndex >= 0) {
				String tempDomain = hostAndDomain.substring(firstDotIndex + 1);
				// Don't set empty strings
				if (StringUtil.hasText(tempDomain)) {
					this.domain = tempDomain;
				}

				String tempHost = hostAndDomain.substring(0, firstDotIndex);
				if (StringUtil.hasText(tempHost)) {
					this.host = tempHost;
				}
			} else {
				if (StringUtil.hasText(hostAndDomain)) {
					this.host = hostAndDomain;
				}
			}
		}

		return this;
	}

	public static String findDomain(String hostDomain, Collection<String> domains) {
		while (hostDomain != null) {
			// find exact match
			for (String name : domains) {
				if (hostDomain.equals(name)) {
					return hostDomain;
				}
			}
			// Otherwise split on the first "." and try again
			int dotIndex = hostDomain.indexOf(".");
			if (dotIndex >= 0 && dotIndex + 1 < hostDomain.length()) {
				hostDomain = hostDomain.substring(dotIndex + 1);
			} else {
				return null;
			}
		}
		return null;
	}

	/**
	 * A basic building of a full route value. It performs no validation, just
	 * builds based on whether the parameter are set
	 *
	 * @param host
	 * @param domain
	 * @param path
	 * @param port
	 * @return Route value build with the given components. Always returns a non-null route. Empty route if no arguments are passed.
	 */
	public static String buildRouteVal(String host, String domain, String path, int port) {

		StringBuilder builder = new StringBuilder();
		if (StringUtil.hasText(host)) {
			builder.append(host);
		}

		if (StringUtil.hasText(domain)) {
			if (StringUtil.hasText(host)) {
				builder.append('.');
			}
			builder.append(domain);
		}

		if (port != CFRoute.NO_PORT) {
			builder.append(':');
			builder.append(Integer.toString(port));
		}

		if (StringUtil.hasText(path)) {
			if (!path.startsWith("/")) {
				builder.append('/');
			}
			builder.append(path);
		}

		return builder.toString();
	}

	public CFRouteBuilder from(String desiredUrl, List<CFDomain> cloudDomains) {
		List<String> domains = cloudDomains
								.stream()
								.map(CFDomain::getName)
								.collect(Collectors.toList());
		return from(desiredUrl, domains);
	}
}