/*******************************************************************************
 * Copyright (c) 2014 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.workspace;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.springsource.ide.eclipse.commons.frameworks.core.FrameworkCoreActivator;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

/**
 * An instance of this class provides a means to register
 * listeners that get notified when classpath for a IJavaProject
 * changes.
 *
 * @author Kris De Volder
 */
public class ClasspathListenerManager implements Disposable {

	public interface ClasspathListener {
		public abstract void classpathChanged(IJavaProject jp);
	}

	private class MyListener implements IElementChangedListener {

		private Set<String> knownProjectNames = Collections.synchronizedSet(new HashSet<>());

		//@Override
		public void elementChanged(ElementChangedEvent event) {
			visit(event.getDelta());
		}

		private void visit(IJavaElementDelta delta) {
			IJavaElement el = delta.getElement();
			switch (el.getElementType()) {
			case IJavaElement.JAVA_MODEL:
				visitChildren(delta);
				break;
			case IJavaElement.JAVA_PROJECT:
				IJavaProject jp = (IJavaProject) el;
				if (isNewProject(jp) || isClasspathChanged(delta.getFlags())) {
					listener.classpathChanged(jp);
				}
				break;
			default:
				break;
			}
		}

		private boolean isNewProject(IJavaProject jp) {
			return knownProjectNames.add(jp.getElementName());
		}

		private boolean isClasspathChanged(int flags) {
			return 0 != (flags & (
					IJavaElementDelta.F_CLASSPATH_CHANGED |
					IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED
			));
		}

		public void visitChildren(IJavaElementDelta delta) {
			for (IJavaElementDelta c : delta.getAffectedChildren()) {
				visit(c);
			}
		}

		public void addKnownProject(IJavaProject jp) {
			this.knownProjectNames.add(jp.getElementName());
		}
	}

	private ClasspathListener listener;
	private MyListener myListener;

	/**
	 * @param initialEvent If true, events are fired immediately on all existing java 
	 * projects, treating the connection of the listener itself as a change event. 
	 * This allows clients to become aware of all classpaths from the start and 
	 * continually monitor them for changes from that point onward.
	 */
	public ClasspathListenerManager(ClasspathListener listener, boolean initialEvent) {
		this.listener = listener;
		myListener=new MyListener();
		if (initialEvent) {
			for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				try {
					if (p.isAccessible() && p.hasNature(JavaCore.NATURE_ID)) {
						IJavaProject jp = JavaCore.create(p);
						listener.classpathChanged(jp);
						myListener.addKnownProject(jp);
					}
				} catch (CoreException e) {
					FrameworkCoreActivator.log(e);
				}
			}
		}
		JavaCore.addElementChangedListener(myListener, ElementChangedEvent.POST_CHANGE);
	}

	public ClasspathListenerManager(ClasspathListener listener) {
		this(listener, false);
	}

	public void dispose() {
		if (myListener != null) {
			JavaCore.removeElementChangedListener(myListener);
			myListener = null;
		}
	}

}