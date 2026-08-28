/*******************************************************************************
 *  Copyright (c) 2013, 2019 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.github;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springframework.ide.eclipse.boot.wizard.github.SimpleJsonRestClient.Response;
import org.springframework.ide.eclipse.boot.wizard.github.SimpleJsonRestClient.WebTarget;
import org.springframework.ide.eclipse.boot.wizard.github.auth.BasicAuthCredentials;
import org.springframework.ide.eclipse.boot.wizard.github.auth.Credentials;
import org.springframework.ide.eclipse.boot.wizard.github.auth.NullCredentials;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableMap;

/**
 * A GithubClient instance needs to configured with some credentials and then it is able to
 * talk to github using its rest api to obtain information about github repos, users,
 * organisations etc.
 *
 * @author Kris De Volder
 */
public class GithubClient {

	private static final Pattern GITHUB_HOST = Pattern.compile("(.*\\.|)github\\.com");
//		pattern should match 'github.com' and api.github.com'

	private static final int CONNECT_TIMEOUT = 10000;

	private static final boolean DEBUG = false;
//	private static final boolean LOG_GITHUB_RATE_LIMIT = false;

	private final Credentials credentials;
	private final SimpleJsonRestClient client;

	/**
	 * Create a GithubClient with default credentials. The default credentials
	 * are a basic authentication username plus password read from a "user.properties"
	 * fetched from the classloader.
	 */
	public GithubClient() {
		this(createDefaultCredentials());
	}

	public GithubClient(Credentials c) {
		this.credentials = c;
		this.client = new SimpleJsonRestClient(c);
	}

	public static Credentials createDefaultCredentials() {
		//Try system properties
		String username = System.getProperty("github.user.name");
		String password = System.getProperty("github.user.password");
		if (username != null && password != null) {
			return new BasicAuthCredentials(GITHUB_HOST, username, password);
		}
		//No credentials found. Try proceeding without credentials.
		return new NullCredentials();
	}

	private String addHost(String path) {
		if (path.startsWith("http")) {
			return path;
		}
		if (!path.startsWith("/")) {
			path = "/"+path;
		}
		return "https://api.github.com"+path;
	}

	/**
	 * Fetch info about repos under a given organization.
	 */
	public Repo[] getOrgRepos(String orgName) {
		return get("/orgs/{orgName}/repos", Repo[].class, ImmutableMap.of("orgName", orgName));

//		return get("/orgs/{orgName}/repos?per_page=100", Repo[].class, orgName);
	}

	/**
	 * Fetch the remaining rate limit.
	 */
	public RateLimitResponse getRateLimit() throws IOException {
		return get("/rate_limit", RateLimitResponse.class);
	}


	/**
	 * Fetch info about repos under a given user name
	 */
	public Repo[] getUserRepos(String userName) {
		return get("/users/{userName}/repos", Repo[].class, ImmutableMap.of("userName", userName));
	}

	/**
	 * Get repos for the authenticated user. This seems to be the only way to list private repos
	 * associated with a user. This only works over an authenticated github connection.
	 */
	public Repo[] getMyRepos() {
		try {
			return get("/user/repos", Repo[].class);
		} catch (Throwable e) {
			BootWizardActivator.log(e);
		}
		return new Repo[0];
	}

	/**
	 * Fetch info about a repo identified by an owner and a name
	 */
	public Repo getRepo(String owner, String repo) {
		return get("/repos/{owner}/{repo}", Repo.class, ImmutableMap.of(
				"owner", owner,
				"repo", repo
		));
	}

	public <T> T get(String url, Class<T> type) {
		return get(url, type, ImmutableMap.of());
	}

	/**
	 * Helper method to fetch json data from some url (or url template)
	 * and parse the data into an object of a given type.
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(String url, Class<T> type, Map<String, Object> vars) {
		try {
			url = addHost(url);
			if (type.isArray()) {
				Class<?> componentType = type.getComponentType();
				//Assume this means we have to support response pagination as described in:
				//https://developer.github.com/v3/#pagination
				ArrayList<Object> results = new ArrayList<>();
				WebTarget webtarget = client.target(url).resolveTemplates(vars);
				do {
					Response response = webtarget.get();
					Object[] pageResults = (Object[])response.readEntity(type);
					for (Object r : pageResults) {
						results.add(r);
					}
					url = getNextPageUrl(response);
					if (url != null) {
						webtarget = client.target(url);
					} else {
						webtarget = null;
					}
				} while (webtarget != null);
				return (T) results.toArray((Object[])Array.newInstance(componentType, results.size()));
			} else {
				return  client.target(url).resolveTemplates(vars).get(type);
			}
		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}
	}


	/**
	 * Get the url of the next page in a paginated result.
	 * May return null if there is no next page.
	 * <p>
	 * See https://developer.github.com/v3/#pagination
	 */
	private static <T> String getNextPageUrl(Response response) {
		List<String> linkHeader = response.getHeaders().get("link");
		if (linkHeader == null) {
			linkHeader = response.getHeaders().get("Link");
		}
		if (linkHeader != null) {
			//Example of header String:
			//<https://api.github.com/organizations/4161866/repos?page=2>; rel="next", <https://api.github.com/organizations/4161866/repos?page=2>; rel="last"
			Pattern nextPat = Pattern.compile("<([^<]*)>;\\s*rel=\"next\"");
			for (String string : linkHeader) {
				Matcher m = nextPat.matcher(string);
				if (m.find()) {
					return m.group(1);
				}
			}
		}
		return null; //no pagination info found
	}

	protected static String getNormalisedProtocol(String protocol) {
		return protocol.toUpperCase();
	}

    /**
     * Download content from a url and save to an outputstream. Use same credentials as
     * other operations in this client. May need to use this to download stuff like
     * zip file from github if the repo it comes from is private.
     */
	public void fetch(URL url, OutputStream writeTo) throws IOException {
		URLConnection conn = null;
		InputStream input = null;
		try {
			conn = BootActivator.getUrlConnectionFactory().createConnection(url);
			conn.setConnectTimeout(CONNECT_TIMEOUT);
			conn.setRequestProperty("Accept", "*/*");
			credentials.apply(conn);
			Log.info("Fetching content from: "+url);
			if (DEBUG) {
				Map<String, List<String>> reqHeaders = conn.getRequestProperties();
				System.out.println(">>> request:  "+url);
				for (Entry<String, List<String>> e : reqHeaders.entrySet()) {
					System.out.println(e.getKey()+" = ");
					for (String s : e.getValue()) {
						System.out.println("   "+s);
					}
				}
			}
			conn.connect();
			if (DEBUG) {
				System.out.println(">>> response:  "+url);
				Map<String, List<String>> headers = conn.getHeaderFields();
				for (Entry<String, List<String>> header : headers.entrySet()) {
					System.out.println(header.getKey()+":");
					for (String value : header.getValue()) {
						System.out.println("   "+value);
					}
				}
				System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
			}

			input = conn.getInputStream();
			IOUtil.pipe(input, writeTo);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Throwable e) {
					//ignore.
				}
			}
		}
	}

	/**
	 * For some quick add-hoc testing
	 */
	public static void main(String[] args) {
		GithubClient gh = new GithubClient();
		for (int i = 0; i < 5; i++) {
			Repo[] repos = gh.getOrgRepos("spring-guides");
			System.out.println(repos.length);
		}
	}

}
