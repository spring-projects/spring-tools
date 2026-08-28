/*******************************************************************************
 * Copyright (c) 2020, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.docker.runtarget;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.frameworks.core.util.StringUtils;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveCounter;
import org.springsource.ide.eclipse.commons.livexp.core.OnDispose;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

public class DockerDeployments extends AbstractDisposable {
	
	public interface Listener {
		void added(DockerDeployment d);
		void removed(DockerDeployment d);
		void updated(DockerDeployment d);
	}

	private static final String PERSISTENCE_KEY = DockerDeployments.class.getName();
	private static final Listener[] NO_LISTENERS = {};
	private static final DockerDeployment[] NO_DEPLOYMENTS = {};
	private final Map<String, DockerDeployment> byName = new HashMap<>();
	private final PropertyStoreApi persistentProperties;
	private LiveCounter persistTrigger = new LiveCounter();
	private List<Listener> listeners = new ArrayList<>();
	
	public DockerDeployments(PropertyStoreApi persistentProperties) {
		this.persistentProperties = persistentProperties;
		restore();
		persistTrigger.onChange(this, (_e, _v) -> persist());
	}

	private void restore() {
		Yaml yaml = yaml();
		String serialized = persistentProperties.get(PERSISTENCE_KEY);
		try {
			if (StringUtils.hasText(serialized)) {
				DockerDeploymentList deserialized = yaml.loadAs(serialized, DockerDeploymentList.class);
				for (DockerDeployment d : deserialized.getDeployments()) {
					byName.put(d.getName(), d);
				}
				persistTrigger.increment();
			}
		} catch (Exception e) {
			Log.log(e);
		}
	}

	private synchronized void persist() {
		Yaml yaml = yaml();
		String serialized = yaml.dump(new DockerDeploymentList(byName.values()));
		try {
			persistentProperties.put(PERSISTENCE_KEY, serialized);
		} catch (Exception e) {
			Log.log(e);
		}
	}

	private Yaml yaml() {
		// e429 is based on snakeyaml 2.0 which has a non-backward compatible change in CustomClassLoaderConstructor cosntrcutors
		try {
			CustomClassLoaderConstructor cclc = CustomClassLoaderConstructor.class.getConstructor(Class.class, ClassLoader.class).newInstance(DockerDeploymentList.class, DockerDeployments.class.getClassLoader());
			return new Yaml(cclc);
		} catch (Exception e) {
			try {
				CustomClassLoaderConstructor cclc = CustomClassLoaderConstructor.class.getConstructor(Class.class, ClassLoader.class, LoaderOptions.class).newInstance(DockerDeploymentList.class, DockerDeployments.class.getClassLoader(), new LoaderOptions());
				return new Yaml(cclc);
			} catch (Exception e1) {
				throw new IllegalStateException(e1);
			}
		}
	}
	
	public void createOrUpdate(DockerDeployment deployment) {
		Listener[] listeners = NO_LISTENERS;
		DockerDeployment old = null;
		synchronized (this) {
			old = byName.put(deployment.getName(), deployment);
			listeners = this.listeners.toArray(NO_LISTENERS);
		}
		for (Listener l : listeners ) {
			if (old == null) {
				l.added(deployment);
			} else {
				l.updated(deployment);
			}
		}
		persistTrigger.increment();
	}

	public void remove(String name) {
		Listener[] listeners = NO_LISTENERS;
		DockerDeployment removed = null;
		synchronized (this) {
			removed = byName.remove(name);
			if (removed != null) {
				listeners = this.listeners.toArray(NO_LISTENERS);
			}
		}
		for (Listener l : listeners ) {
			l.removed(removed);
		}
		persistTrigger.increment();
	}

	public void addListener(OnDispose owner, Listener l) {
		DockerDeployment[] deployments = NO_DEPLOYMENTS;
		synchronized (this) {
			listeners.add(l);
			deployments = byName.values().toArray(NO_DEPLOYMENTS);
		}
		owner.onDispose(d -> removeListener(l));
		for (DockerDeployment d : deployments) {
			l.added(d);
		}
	}

	private synchronized void removeListener(Listener l) {
		listeners.remove(l);
	}

	public synchronized DockerDeployment get(String name) {
		return byName.get(name);
	}

}
