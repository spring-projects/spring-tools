/*******************************************************************************
 * Copyright (c) 2017, 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.boot.ls.remoteapps;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springsource.ide.eclipse.commons.livexp.core.LiveSets;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;

/**
 * A RemoteAppsDataHolder provides the means to connect providers of RemoteAppData
 * with consumers of RemoteAppData. At present there is one consumer and three producers.
 * <p>
 * The single consumer is the Spring Boot Language Server eclipse plugin. It uses remote app data
 * to scrape live (hover, bean etc.) information from remote boot processes.
 * <p>
 * The three producers of the information are:
 * <p>
 * a) the "Remote Boot Apps" preferences page (which is defined in the language server
 * plugin and allows for users to manually enter information.
 * <p>
 * b) the BootDashBoard cloudfoundry plugin, which contributes information automatically
 * when boot apps are deployed to cf from boot dash with JMX tunnelling enabled.
 * c) BootDashBoard itself via GenericRemoteAppElement framework. This provides remote app
 *    data automatically, for elements that support JMX connections.
 * <p>
 * Note: it would be more logical if this class was defined inside boot.ls plugin, (since it
 * is the (only) consumer of the data). But because of how
 * STS 4 and STS 3 components are currently being built, it is not possible for STS 3 components
 * to depend directly on STS 4 components. It is however possible for STS 4 to depend on
 * STS 3 components. I.e. boot.ls can depend on boot.dashboard but not vice-versa.
 * This is why {@link RemoteBootAppsDataHolder}
 * 'extension point' is defined here, allowing boot.dash and spring.boot.ls to both access
 * the api and contribute and/or consume remote-app data.
 *
 * @author Kris De Volder
 */
public class RemoteBootAppsDataHolder {

	private static RemoteBootAppsDataHolder DEFAULT;

	public synchronized static RemoteBootAppsDataHolder getDefault() {
		if (DEFAULT == null) {
			DEFAULT = new RemoteBootAppsDataHolder(() -> ExentionPointLoader.load(RemoteBootAppsDataHolder.Contributor.class));
		}
		return DEFAULT;
	}

	/**
	 * Json serialization-friendly data class
	 */
	public static class RemoteAppData {
		private String jmxurl;
		private String host;
		private String urlScheme = "https";
		private String port = "443";
		private boolean manualConnect = false;
		    //manualConnect defaults to false to be 'backwards compatible'. I.e the 'old'
		    //behavior was to autoconnect. This flag was added to allow disabling autconnect
			//under some circumstances (e.g. a docker app that has no actuators).

		private boolean keepChecking = true;
			//keepChecking defaults to true. Boot dash automatic remote apps should override this explicitly.
			//Reason. All other 'sources' of remote apps are 'manual' and we want them to default to
			//'keepChecking' even if the user doesn't set this to true manually.
		private String processId = null;
		private String processName = null;

		public RemoteAppData() {
		}

		public RemoteAppData(String jmxurl, String host) {
			super();
			this.jmxurl = jmxurl;
			this.host = host;
		}

		public String getJmxurl() {
			return jmxurl;
		}

		public void setJmxurl(String jmxurl) {
			this.jmxurl = jmxurl;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public String getUrlScheme() {
			return urlScheme;
		}

		public void setUrlScheme(String urlScheme) {
			this.urlScheme = urlScheme;
		}

		public String getPort() {
			return port;
		}

		public void setPort(String port) {
			this.port = port;
		}

		public boolean isKeepChecking() {
			return keepChecking;
		}

		public void setKeepChecking(boolean keepChecking) {
			this.keepChecking = keepChecking;
		}

		public boolean isManualConnect() {
			return manualConnect;
		}

		public void setManualConnect(boolean manualConnect) {
			this.manualConnect = manualConnect;
		}

		public String getProcessId() {
			return processId;
		}

		public void setProcessId(String processId) {
			this.processId = processId;
		}

		public String getProcessName() {
			return processName;
		}

		public void setProcessName(String processName) {
			this.processName = processName;
		}

		@Override
		public String toString() {
			return "RemoteAppData [jmxurl=" + jmxurl + ", host=" + host + ", urlScheme=" + urlScheme + ", port=" + port
					+ ", manualConnect=" + manualConnect + ", keepChecking=" + keepChecking + ", processId=" + processId
					+ ", processName=" + processName + "]";
		}

		@Override
		public int hashCode() {
			return Objects.hash(host, jmxurl, keepChecking, manualConnect, port, processId, processName, urlScheme);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			RemoteAppData other = (RemoteAppData) obj;
			return Objects.equals(host, other.host) && Objects.equals(jmxurl, other.jmxurl)
					&& keepChecking == other.keepChecking && manualConnect == other.manualConnect
					&& Objects.equals(port, other.port) && Objects.equals(processId, other.processId)
					&& Objects.equals(processName, other.processName) && Objects.equals(urlScheme, other.urlScheme);
		}

	}

	/**
	 * A producer of remote RemoteAppData can contribute its data by injecting a bean
	 * that implements this interface (via {@link SimpleDIContext}.
	 */
	public interface Contributor {
		ObservableSet<RemoteAppData> getRemoteApps();
	}

	/**
	 * Computes the union of all Contributors.
	 */
	private final ObservableSet<RemoteAppData> union;

	public RemoteBootAppsDataHolder(Supplier<List<Contributor>> contributions) {
		union = union(contributions.get());
	}

	public static ObservableSet<RemoteAppData> union(List<Contributor> contributors) {
		if (contributors.isEmpty()) {
			return LiveSets.emptySet(RemoteAppData.class);
		} else if (contributors.size() == 1) {
			return contributors.get(0).getRemoteApps();
		} else {
			ObservableSet<RemoteAppData> union = contributors.get(0).getRemoteApps();
			for (int i = 1; i < contributors.size(); i++) {
				union = LiveSets.union(union, contributors.get(i).getRemoteApps());
			}
			return union;
		}
	}

	/**
	 * A consumer of RemoteAppData uses this method to obtain data
	 * and/or listen for changes to the data.
	 */
	public ObservableSet<RemoteAppData> getRemoteApps() {
		return union;
	}

}
