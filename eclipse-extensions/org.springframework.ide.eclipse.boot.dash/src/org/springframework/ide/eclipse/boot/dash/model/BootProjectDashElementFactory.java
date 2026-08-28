/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springsource.ide.eclipse.commons.core.pstore.IScopedPropertyStore;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;

/**
 * Manages the creating of BootProjectDashElement. It keeps track of all the created instances and
 * ensures that if an element represents the same entity then the instance is reused.
 *
 * @author Kris De Volder
 */
public class BootProjectDashElementFactory implements Disposable {

	private LaunchConfDashElementFactory launchConfElementFactory;
	private LocalBootDashModel model;
	private IScopedPropertyStore<IProject> projectProperties;

	private Map<IProject, BootProjectDashElement> cache;

	public BootProjectDashElementFactory(LocalBootDashModel model, IScopedPropertyStore<IProject> projectProperties, LaunchConfDashElementFactory launchConfElementFactory) {
		this.cache = new MapMaker()
				.concurrencyLevel(1) //single thread only so don't waste space for 'connurrencyLevel' support
				.makeMap();
		this.model = model;
		this.launchConfElementFactory = launchConfElementFactory;
		this.projectProperties = projectProperties;
	}

	public BootProjectDashElement createOrGet(IProject p) {
		if (BootPropertyTester.workaroundMavenBundleInitializationIssue(p)) {
			return null;
		}

		if (BootPropertyTester.isBootProject(p)) {
			BootProjectDashElement el;
			synchronized (this) {
				el = cache.get(p);
				if (el == null) {
					cache.put(p, el = new BootProjectDashElement(p, model, projectProperties, this, launchConfElementFactory));
				}
			}
			return el;
		}
		return null;
	}

	public void dispose() {
		disposeAllExcept(ImmutableSet.of());
		cache = null;
	}

	/**
	 * Clients should call this to allow factory to remove/dispose elements that are no longer interesting.
	 */
	public synchronized void disposeAllExcept(Set<? extends BootDashElement> toRetain) {
		if (cache != null) {
			Iterator<BootProjectDashElement> iter = cache.values().iterator();
			while (iter.hasNext()) {
				BootProjectDashElement element = iter.next();
				if (!toRetain.contains(element)) {
					iter.remove();
					element.dispose();
				}
			}
		}
	}
}
