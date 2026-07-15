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

import java.util.List;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Attaches to a local JVM process by its OS PID and starts (or reuses) its local JMX
 * management agent on-demand, returning the JMX service URL the agent bound to.
 * <p>
 * This avoids picking a JMX port before the target JVM exists: the port is only known once
 * the target JVM has already bound it itself.
 *
 * @author Alex Boyko
 */
class LocalJvmAttach {

	private static final String LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

	private LocalJvmAttach() {
	}

	static String startLocalManagementAgent(String pid) throws Exception {
		List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
		VirtualMachineDescriptor vmd = vmds.stream().filter(d -> d.id().equals(pid)).findFirst().orElse(null);
		if (vmd == null) {
			throw new IllegalStateException("No local JVM found for pid " + pid);
		}
		return startLocalManagementAgent(vmd);
	}

	static String startLocalManagementAgent(VirtualMachineDescriptor vmd) throws Exception {
		VirtualMachine vm = VirtualMachine.attach(vmd);
		try {
			String jmxAddress = null;
			try {
				jmxAddress = vm.getAgentProperties().getProperty(LOCAL_CONNECTOR_ADDRESS);
			} catch (Exception e) {
				//ignore
			}
			if (jmxAddress == null) {
				jmxAddress = vm.startLocalManagementAgent();
			}
			if (jmxAddress == null) {
				throw new IllegalStateException("Could not start local management agent for pid " + vmd.id());
			}
			return jmxAddress;
		} finally {
			vm.detach();
		}
	}

}
