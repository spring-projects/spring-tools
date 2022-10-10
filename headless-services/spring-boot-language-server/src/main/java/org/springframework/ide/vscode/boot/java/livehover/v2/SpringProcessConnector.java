/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

/**
 * @author Martin Lippert
 */
public interface SpringProcessConnector {
	
	ProcessType getProcessType();
	String getProcessKey();

	void connect() throws Exception;
	SpringProcessLiveData refresh(SpringProcessLiveData currentData) throws Exception;
	void disconnect() throws Exception;
	
	void addConnectorChangeListener(SpringProcessConnectionChangeListener listener);
	void removeConnectorChangeListener(SpringProcessConnectionChangeListener listener);
	String getProjectName();
	String getProcessId();
	String getProcessName();
	SpringProcessGcPausesMetricsLiveData refreshGcPausesMetrics(SpringProcessLiveData current, String metricName) throws Exception;
	SpringProcessMemoryMetricsLiveData refreshMemoryMetrics(SpringProcessLiveData current, String metricName) throws Exception;
}
