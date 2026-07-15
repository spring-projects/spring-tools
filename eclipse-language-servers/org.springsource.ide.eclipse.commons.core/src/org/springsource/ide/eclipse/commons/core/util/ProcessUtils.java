/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.util;

import java.io.IOException;
import java.util.List;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Process Utilities making use of JDK tools
 *
 * @author Alex Boyko
 *
 */
public class ProcessUtils {

	/**
	 * Attaches to a local process by its PID and starts (or reuses) its local JMX management
	 * agent on-demand, returning the JMX service URL the agent bound to. This avoids ever
	 * having to pick a JMX port before the target process exists: the port is only known once
	 * the target process has already bound it itself.
	 * @param pid the PID
	 * @return the JMX service URL of the process' local management agent, or {@code null} if
	 * no such process could be found or attached to
	 */
	public static String getLocalManagementAgentUrl(String pid) {
		List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
		VirtualMachineDescriptor vmd = vmds.stream().filter(descriptor -> descriptor.id().equals(pid)).findFirst().orElse(null);
		if (vmd != null) {
			try {
				VirtualMachine vm = VirtualMachine.attach(vmd);
				try {
					return vm.startLocalManagementAgent();
				} finally {
					vm.detach();
				}
			} catch (AttachNotSupportedException e) {
				CorePlugin.log(e);
			} catch (IOException e) {
				CorePlugin.log(e);
			}
		}
		return null;
	}

	/**
	 * Creates JMX Connector to a process specified by its PID
	 * @param pid the PID
	 * @return JMX connector
	 */
	public static JMXConnector createJMXConnector(String pid) {
		String agentUrl = getLocalManagementAgentUrl(pid);
		if (agentUrl != null) {
			try {
				JMXServiceURL serviceUrl = new JMXServiceURL(agentUrl);
				return JMXConnectorFactory.connect(serviceUrl, null);
			} catch (IOException e) {
				CorePlugin.log(e);
			}
		}
		return null;
	}

}
