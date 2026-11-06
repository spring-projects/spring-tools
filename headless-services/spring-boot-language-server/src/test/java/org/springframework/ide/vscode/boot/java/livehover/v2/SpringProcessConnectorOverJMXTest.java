/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorRemote.RemoteBootAppData;

/**
 * @author Alex Boyko
 */
class SpringProcessConnectorOverJMXTest {

	@Test
	void stubUrlIsRedacted() {
		String url = "service:jmx:rmi://127.0.0.1/stub/rO0ABXNyAC5qYXZheC5tYW5hZ2VtZW50";
		assertEquals("service:jmx:rmi://127.0.0.1/stub/<redacted>", SpringProcessConnectorOverJMX.redact(url));
	}

	@Test
	void otherUrlsAreKept() {
		String url = "service:jmx:rmi:///jndi/rmi://127.0.0.1:5000/jmxrmi";
		assertEquals(url, SpringProcessConnectorOverJMX.redact(url));
		assertNull(SpringProcessConnectorOverJMX.redact(null));
	}

	@Test
	void toStringDoesNotLeakStub() {
		SpringProcessConnectorOverJMX c = new SpringProcessConnectorOverJMX(ProcessType.LOCAL, "k",
				"service:jmx:rmi://127.0.0.1/stub/SECRETSTUB", "http", "1", "n", "p", null, null);
		assertFalse(c.toString().contains("SECRETSTUB"));
	}


	@Test
	void connectUsesCredentialsForPasswordProtectedJmx() throws Exception {
		JMXAuthenticator authenticator = credentials -> {
			String[] c = (String[]) credentials;
			if (c != null && c.length == 2 && "user".equals(c[0]) && "secret".equals(c[1])) {
				return new Subject(true, java.util.Set.of(new JMXPrincipal("user")), java.util.Set.of(), java.util.Set.of());
			}
			throw new SecurityException("Invalid credentials");
		};
		Map<String, Object> env = new HashMap<>();
		env.put(JMXConnectorServer.AUTHENTICATOR, authenticator);
		JMXConnectorServer server = JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL("service:jmx:rmi://127.0.0.1"), env, ManagementFactory.getPlatformMBeanServer());
		server.start();
		try {
			String url = server.getAddress().toString();

			SpringProcessConnectorOverJMX withCreds = new SpringProcessConnectorOverJMX(ProcessType.REMOTE, "k", url, "http", "1", "n", "p", null, null, "user", "secret");
			withCreds.connect();
			withCreds.disconnect();

			SpringProcessConnectorOverJMX wrongCreds = new SpringProcessConnectorOverJMX(ProcessType.REMOTE, "k", url, "http", "1", "n", "p", null, null, "user", "wrong");
			assertThrows(SecurityException.class, wrongCreds::connect);

			SpringProcessConnectorOverJMX noCreds = new SpringProcessConnectorOverJMX(ProcessType.REMOTE, "k", url, "http", "1", "n", "p", null, null);
			assertThrows(SecurityException.class, noCreds::connect);
		} finally {
			server.stop();
		}
	}

	@Test
	void remoteAppDataDoesNotLeakPassword() {
		RemoteBootAppData a = new RemoteBootAppData();
		a.setJmxurl("service:jmx:rmi://localhost:1234/jndi/rmi://localhost:1234/jmxrmi");
		a.setJmxUser("user");
		a.setJmxPassword("TOPSECRET");
		assertFalse(a.toString().contains("TOPSECRET"));
		RemoteBootAppData b = new RemoteBootAppData();
		b.setJmxurl(a.getJmxurl());
		b.setJmxUser("user");
		b.setJmxPassword("OTHER");
		assertFalse(a.equals(b), "different passwords must not be treated as the same app");
		assertTrue(a.equals(a));
	}

}
