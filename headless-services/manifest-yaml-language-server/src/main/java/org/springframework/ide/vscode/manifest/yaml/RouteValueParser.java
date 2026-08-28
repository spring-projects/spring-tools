package org.springframework.ide.vscode.manifest.yaml;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;

import org.springframework.ide.vscode.commons.cloudfoundry.client.CFRoute;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.ConnectionException;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.NoTargetsException;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileException;
import org.springframework.ide.vscode.commons.util.RegexpParser;
import org.springframework.ide.vscode.commons.util.ValueParseException;

public class RouteValueParser extends RegexpParser {

	private static final String ROUTE_REGEX = "^([\\da-z\\.-]+)(:\\d{1,5})?((\\/[\\dA-Za-z\\.-]+)*\\/?)?$";
	private static final String ROUTE_TYPE_NAME = "Route";
	private static final String ROUTE_DESCRIPTION = "HTTP or TCP application root route";
	private static final int MAX_PORT_NUMBER = 65535;

	private Callable<Collection<String>> domains;

	public RouteValueParser(Callable<Collection<String>> domains) {
		super(ROUTE_REGEX, ROUTE_TYPE_NAME, ROUTE_DESCRIPTION);
		this.domains = domains;
	}

	private Matcher staticValidation(String str) throws Exception {
		return (Matcher) super.parse(str);
	}

	private Object dynamicValidation(String str, Matcher matcher) throws Exception {
		if (domains == null) {
			// If domains is unknown we can't do the dynamic checks, so bail out.
			return str;
		}
		try {
			Collection<String> cloudDomains = Collections.emptyList();
			try {
				cloudDomains = domains.call();
			} catch (ValueParseException e) {
				/*
				 * If domains hint provider throws exception it is
				 * ValueParserException not NoTargetsException. This means no
				 * communication with CF -> abort dyncamic validation
				 */
				return matcher;
			}
			if (cloudDomains == null) {
				// If domains is unknown we can't do the dynamic checks, so bail out.
				return str;
			}
			CFRoute route = CFRoute.builder().from(str, cloudDomains).build();
			if (route.getDomain() == null || route.getDomain().isEmpty()) {
				throw new ValueParseException("Domain is missing.");
			}
			if ((route.getPath() != null && !route.getPath().isEmpty()) && (route.getPort() != CFRoute.NO_PORT)) {
				throw new ValueParseException(
						"Unable to determine type of route. HTTP port may have a path but no port. TCP route may have port but no path.");
			}
			if (route.getPort() > MAX_PORT_NUMBER) {
				String portAndColumn = matcher.group(2);
				int start = str.indexOf(portAndColumn) + 1;
				int end = start + portAndColumn.length() - 1;
				throw new ValueParseException("Invalid port number. Port range must be between 1 and " + MAX_PORT_NUMBER, start, end);
			}
			if (!cloudDomains.contains(route.getDomain())) {
				String hostDomain = matcher.group(1);
				throw new ReconcileException("Unknown 'Domain'. Valid domains are: "+cloudDomains, ManifestYamlSchemaProblemsTypes.UNKNOWN_DOMAIN_PROBLEM, hostDomain.lastIndexOf(route.getDomain()), hostDomain.length());
			}
			return str;
		} catch (ConnectionException | NoTargetsException e) {
			// No connection to CF? Abort dynamic validation
			return str;
		}
	}

	@Override
	public Object parse(String str) throws Exception {
		Matcher matcher = staticValidation(str);
		if (matcher != null) {
			return dynamicValidation(str, matcher);
		}
		return null;
	}

}
