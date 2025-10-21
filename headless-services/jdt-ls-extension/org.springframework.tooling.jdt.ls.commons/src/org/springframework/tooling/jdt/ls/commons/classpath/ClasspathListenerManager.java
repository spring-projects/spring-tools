/*******************************************************************************
 * Copyright (c) 2019, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.classpath;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.springframework.tooling.jdt.ls.commons.Logger;

/**
 * An instance of this class provides a means to register
 * listeners that get notified when classpath for a IJavaProject
 * changes.
 *
 * @author Kris De Volder
 */
public class ClasspathListenerManager {

	Queue<Runnable> workQueue = new ConcurrentLinkedQueue<>();
	
	Job worker = new Job("Processing JDT Change Events") {
		{
			setSystem(true);
		}
		
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			while (!workQueue.isEmpty()) {
				workQueue.remove().run();
			}
			return Status.OK_STATUS;
		}
	};

	public interface ClasspathListener {
		void classpathChanged(IJavaProject jp);
		default void projectBuilt(IJavaProject jp) {}; 
	}

	private class MyListener implements IElementChangedListener {

		@Override
		public void elementChanged(ElementChangedEvent event) {
			workQueue.add(() -> visit(event.getDelta()));
			worker.schedule();
		}

		private void visit(IJavaElementDelta delta) {
			IJavaElement el = delta.getElement();
			switch (el.getElementType()) {
			case IJavaElement.JAVA_MODEL:
				visitChildren(delta);
				break;
			case IJavaElement.JAVA_PROJECT:
				IJavaProject jp = (IJavaProject)el;
				if (isCreatedOrDeleted(delta)
						|| isClasspathChanged(delta.getFlags())
						// Classpath unchanged but maven/gradle repo cache has JAR's removed or downloaded
						// See individual method comments for more details
						|| isClasspathManifestFileChanged(jp, delta)
						|| areClasspathJarsChanged(delta)) {
					listener.classpathChanged(jp);
				}
				break;
			default:
				break;
			}
		}

		/**
		 * Checks if any classpath JARs have been added/removed/content changed
		 * For the case when classpath stays the same while some classpath JARs are missing from maven/gradle repo cache.
		 * This handles Gradle case completely and partially handles Maven case 
		 * @param delta
		 * @return
		 */
		private boolean areClasspathJarsChanged(IJavaElementDelta delta) {
			for (IJavaElementDelta childDelta : delta.getAffectedChildren()) {
				if (childDelta.getElement() instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) childDelta.getElement();
					if (pkgRoot.isArchive()) {
						return true;
					}
				}
			}
			return false;
		}
		
		/**
		 * When Maven project update is completed .classpath file content changed is one
		 * of the resource delta's expected.
		 * 
		 * If maven cache JARs hav ebeen removed and then maven project update performed
		 * the following events sent: 1. JAR files removed 2. Once classpath is ready
		 * and downloaded .classpath file content changed comes in
		 * 
		 * Next update of the same project will result in the following 1. JAR files
		 * added 2. Immidiately after .classpath file content changed
		 * 
		 * Looks like M2E does a "refresh" before updating which is good, but no refresh
		 * after which is bad and hence JAR files added event come next Maven Update and
		 * we are forced to watch for .classpath content changed.
		 * 
		 * @param jp
		 * @param delta
		 * @return
		 */
		private boolean isClasspathManifestFileChanged(IJavaProject jp, IJavaElementDelta delta) {
			if (delta.getResourceDeltas() != null && (delta.getFlags() & (IJavaElementDelta.F_CONTENT | IJavaElementDelta.F_CHILDREN)) != 0) {
				IFile classpathFile = jp.getProject().getFile(IJavaProject.CLASSPATH_FILE_NAME);
				for (IResourceDelta resourceDelta : delta.getResourceDeltas()) {
					if (resourceDelta != null && classpathFile.equals(resourceDelta.getResource())) {
						switch (resourceDelta.getKind()) {
						case IResourceDelta.ADDED:
							return true;
						case IResourceDelta.CHANGED:
							return (resourceDelta.getFlags() & IResourceDelta.CONTENT) != 0;
						}
					}
				}
			}
			return false;
		}

		private boolean isCreatedOrDeleted(IJavaElementDelta delta) {
			int kind = delta.getKind();
			return kind == IJavaElementDelta.ADDED || kind==IJavaElementDelta.REMOVED;
		}

		private boolean isClasspathChanged(int flags) {
			return 0!= (flags & (
					IJavaElementDelta.F_CLASSPATH_CHANGED |
					IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED |
					IJavaElementDelta.F_CLOSED |
					IJavaElementDelta.F_OPENED
			));
		}

		public void visitChildren(IJavaElementDelta delta) {
			for (IJavaElementDelta c : delta.getAffectedChildren()) {
				visit(c);
			}
		}
	}

	private ClasspathListener listener;
	private MyListener myListener;
	private final Logger logger;
	
	public ClasspathListenerManager(Logger logger, ClasspathListener listener) {
		this.logger = logger;
		this.logger.debug("Setting up ClasspathListenerManager");
		this.listener = listener;
		JavaCore.addElementChangedListener(myListener=new MyListener(), ElementChangedEvent.POST_CHANGE);
	}
	
	public void dispose() {
		if (myListener!=null) {
			JavaCore.removeElementChangedListener(myListener);
			myListener = null;
		}
	}

}