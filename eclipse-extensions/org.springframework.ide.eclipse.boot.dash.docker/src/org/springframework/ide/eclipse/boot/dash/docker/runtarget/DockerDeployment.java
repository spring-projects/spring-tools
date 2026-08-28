/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.docker.runtarget;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ide.eclipse.boot.dash.api.DesiredInstanceCount;
import org.springframework.ide.eclipse.boot.dash.model.Nameable;
import org.springframework.ide.eclipse.boot.dash.model.RunState;

import com.google.common.collect.ImmutableMap;

/**
 * Data class containing info about a 'deployment' (i.e. what gets created when you
 * drag and drop a project onto a docker target. 
 * <p>
 * This is similar in concept to a k8s deployment. It is an object that describes a
 * indented state of some containers running in docker. It contains all the
 * information needed to create the container.
 * <p>
 * This object must be easy to serialize as it needs to be persisted across Eclipse
 * restarts.
 */
public class DockerDeployment implements Nameable, DesiredInstanceCount {
	
	private String name; // name of the deployment, currently this is also the name of the deployed project.
	private RunState runState;
	private String buildId;
	
	/**
	 * Records the DockerRunTarget 'sessionId' that was in effect when this deployment was created.
	 * This allows for deployment synchronization to distinguish between a deployment created in the
	 * current session or an older deployment (restored from persistent data). 
	 * <p>
	 * See: https://www.pivotaltracker.com/story/show/173136687
	 */
	private String sessionId;
	
	private HashMap<String, String> sysprops;
	
	public DockerDeployment() {}

	public DockerDeployment(DockerDeployment copyFrom) {
		this.name = copyFrom.name;
		this.runState = copyFrom.runState;
		this.buildId = copyFrom.buildId;
		this.sessionId = copyFrom.sessionId;
		this.sysprops = copyFrom.sysprops;
		if (sysprops != null) {
			sysprops = new HashMap<>(sysprops);
		}
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public RunState getRunState() {
		return runState;
	}
	public void setRunState(RunState runState) {
		this.runState = runState;
	}

	public DockerDeployment withGoalState(RunState newGoalState, String sessionId) {
		DockerDeployment d = new DockerDeployment(this);
		d.setRunState(newGoalState);
		d.setSessionId(sessionId);
		return d;
	}

	public String getBuildId() {
		return buildId;
	}

	public Map<String,String> getSystemProperties() {
		if (sysprops == null) {
			return ImmutableMap.of();
		}
		return sysprops;
	}
	
	/**
	 * When you set a buildId you should *also* set the session id as well. These two id's are pair that
	 * allways belong together. The only scenario were this method is called 'by itself' is by Yaml deserialization.
	 */
	public void setBuildId(String buildId) {
		this.buildId = buildId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}
	
	public String getSessionId() {
		return sessionId;
	}

	public int getDesiredInstances() {
		return 1;
	}

	public void setSystemProperty(String name, String value) {
		if (sysprops == null) {
			sysprops = new HashMap<>();
		}
		if (value == null) {
			sysprops.remove(name);
		} else {
			sysprops.put(name, value);
		}
	}
}
