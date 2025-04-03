/*******************************************************************************
 * Copyright (c) 2012, 2025 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.maintype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.debug.ui.launcher.MainMethodSearchEngine;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.FrameworkCoreActivator;

/**
 * Provides access to heuristic algorithms to 'guess' the main type for a
 * project.
 * 
 * Note: these algorithms may depend on project type and require access to
 * various optional dependencies. E.g. gradle tooling for gradle projects, m2e
 * for pom based algorithms etc.
 * <p>
 * Ideally in the future additional algorithms should be contributable via
 * extension point so that we don't need to add a gazilion dependencies in this
 * plugin, but clients can depend on this plugin and implicitly gain access if
 * the required stuff is installed.
 * 
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class MainTypeFinder {

	private static MainTypeFinder instance;

	private static final IType[] EMPTY = new IType[0];

	/**
	 * Find the most likely main types that should be run when a user select this
	 * project with 'run as'.
	 * 
	 * @return Types with main method or empty value if the 'most likely' main type
	 *         can't be determined.
	 * @throws CoreException
	 *             if something unexpected happened..
	 */
	public static IType[] guessMainTypes(IJavaProject project, IProgressMonitor mon)
			throws CoreException {
		return getInstance().internalGuessMainTypes(project, mon);
	}

	private static synchronized MainTypeFinder getInstance() {
		if (instance == null) {
			instance = new MainTypeFinder();
		}
		return instance;
	}

	private Map<Confidence, IMainTypeFinder[]> algos;

	/**
	 * Singelton, use getInstance or call one of the static public methods.
	 */
	private MainTypeFinder() {
		// TODO: support contributing additional algos with priorities via
		// extension point
		// Created as linked hash map so that iteration starts from highest to
		// lowest confidence
		this.algos = new LinkedHashMap<MainTypeFinder.Confidence, IMainTypeFinder[]>();
		
		// Add finders based on whether they return main types based on the confidence that
		// they are exact types requested. For example, main types from pom files have high
		// confidence that they are the types requested, whereas types from source have lower
		// confidence that they are the types requested. Each confidence itself also has a priority
		// order, with more authoritative finders listed first.
		this.algos.put(Confidence.CERTAIN, new IMainTypeFinder[] {
				// add more here if they should be considered more authorative
				new FindInPom()
				// add more here if they should be considered less authorative
		});

		this.algos.put(Confidence.HIGH, new IMainTypeFinder[] {
				// add more here if they should be considered more authorative
				new FindInSource(),
				// add more here if they should be considered less authorative
		});

		this.algos.put(Confidence.LOW, new IMainTypeFinder[] {
				// add more here if they should be considered more authorative
				new FindInLibs()
				// add more here if they should be considered less authorative
		});

	}

	private IType[] internalGuessMainTypes(IJavaProject project, IProgressMonitor mon)
			throws CoreException {
		Throwable error = null;
		mon.beginTask("Search main types", algos.size());
		Set<IType> totalTypes = new LinkedHashSet<IType>();

		try {

			for(Entry<Confidence, IMainTypeFinder[]> entry : algos.entrySet()) {
				// Collect all types per confidence level
				for (IMainTypeFinder algo : entry.getValue()) {
					try {
						IType[] found = algo.findMain(project, new SubProgressMonitor(mon, 1));
						for (IType type : found) {
							totalTypes.add(type);
						}
					} catch (Throwable e) {
						if (error == null) {
							// if multiple errors keep only the first one.
							error = e;
						}
					}
				}
				// Stop when types of the highest confidence are found
				if (!totalTypes.isEmpty()) {
					break;
				}
			}
	
			mon.worked(1);
		} finally {
			mon.done();
		}
		if (!totalTypes.isEmpty()) {
			return totalTypes.toArray(new IType[0]);
		} else {
			// If there was an error throw it as an explanation of the failure.
			if (error != null) {
				throw ExceptionUtil.coreException(error);
			}
		}
		return EMPTY;
	}

	private static final String MAIN_CLASS_PROP = "start-class";

	public static class FindInPom implements IMainTypeFinder {

		public IType[] findMain(IJavaProject jp, IProgressMonitor mon)
				throws Exception {
			mon.beginTask("Search main in pom", 1);
			try {
				IProject p = jp.getProject();
				IFile pomFile = p.getFile("pom.xml");
				if (pomFile.exists()) {
					PomParser pomParser = new PomParser(pomFile);
					String starterClassName = pomParser
							.getProperty(MAIN_CLASS_PROP);
					if (starterClassName != null) {
						IType mainType = jp.findType(starterClassName);
						if (mainType != null) {
							return new IType[] { mainType };
						}
						throw ExceptionUtil.coreException("'pom.xml' defines '"
								+ MAIN_CLASS_PROP + "' as '" + starterClassName
								+ "' but it could not be found");
					}
				}
			} finally {
				mon.done();
			}
			return EMPTY;
		}
	}

	public static class FindInSource implements IMainTypeFinder {

		public IType[] findMain(IJavaProject javaProject,
				IProgressMonitor monitor) throws Exception {
			monitor.beginTask("Search main types in project source", 1);
			try {

				MainMethodSearchEngine engine = new MainMethodSearchEngine();
				IJavaSearchScope scope = createSearchScope(javaProject);

				boolean includeSubtypes = true;
				IType[] types =  engine
						.searchMainMethods(monitor, scope, includeSubtypes);
				if (types == null) {
					types = EMPTY;
				}
				return types;
			} catch (Exception e) {
				FrameworkCoreActivator.log(e);
			} finally {
				monitor.done();
			}
			return EMPTY;
		}

		private IJavaSearchScope createSearchScope(IJavaProject javaProject) throws JavaModelException {
			List<IJavaElement> pfrs = new ArrayList<>();
			for (IPackageFragmentRoot pfr : javaProject.getAllPackageFragmentRoots()) {
				IClasspathEntry cpe = pfr.getRawClasspathEntry();
				if (isInteresting(cpe)) {
					pfrs.add(pfr);
				}
			}

			int constraints = IJavaSearchScope.SOURCES;
				constraints |= IJavaSearchScope.APPLICATION_LIBRARIES;
		
			return SearchEngine.createJavaSearchScope(
					pfrs.toArray(new IJavaElement[pfrs.size()]), constraints);
		}

		private boolean isInteresting(IClasspathEntry cpe) {
			if (cpe==null) {
				return false;
			}
			int e_kind = cpe.getEntryKind();
			return  e_kind == IClasspathEntry.CPE_SOURCE 
			|| ( 
				e_kind == IClasspathEntry.CPE_CONTAINER && 
				"org.jetbrains.kotlin.core.KOTLIN_CONTAINER".equals(cpe.getPath().segment(0))
			);
		}
	}
	
	public static class FindInLibs implements IMainTypeFinder {
		
		public IType[] findMain(IJavaProject javaProject,
				IProgressMonitor monitor) throws Exception {
			monitor.beginTask("Search main types in project source", 1);
			try {

				MainMethodSearchEngine engine = new MainMethodSearchEngine();
				IJavaSearchScope scope = SearchEngine.createJavaSearchScope(true, new IJavaElement[] { javaProject }, IJavaSearchScope.APPLICATION_LIBRARIES);

				boolean includeSubtypes = true;
				IType[] types =  engine
						.searchMainMethods(monitor, scope, includeSubtypes);
				if (types == null) {
					types = EMPTY;
				}
				return types;
			} catch (Exception e) {
				FrameworkCoreActivator.log(e);
			} finally {
				monitor.done();
			}
			return EMPTY;
		}

	}

	/**
	 * Confidence that a search result for main types in a project is what is expected.
	 *
	 */
	public static enum Confidence {
		CERTAIN, HIGH, LOW
	}
}
