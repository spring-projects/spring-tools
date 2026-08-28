/*******************************************************************************
 * Copyright (c) 2012, 2023 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation.framework;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.validation.BootValidationActivator;
import org.springframework.ide.eclipse.boot.validation.preferences.ConfigProcessingPreferencesPage;
import org.springframework.ide.eclipse.boot.validation.rules.ValidationRuleDefinitions;
import org.springframework.ide.eclipse.editor.support.preferences.EditorType;
import org.springframework.ide.eclipse.editor.support.preferences.PreferencesBasedSeverityProvider;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemSeverity;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemType;
import org.springframework.ide.eclipse.editor.support.reconcile.SeverityProvider;
import org.springsource.ide.eclipse.commons.core.MarkerUtils;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager;

import com.google.common.collect.ImmutableMap;

/**
 * @author Torsten Juergeleit
 * @author Christian Dupuis
 * @author Martin Lippert
 * @author Kris De Volder
 */
public class SpringBootProjectBuilder extends IncrementalProjectBuilder {
	
	public static String MARKER_ID = BootValidationActivator.PLUGIN_ID+".problemmarker";

	public SpringBootProjectBuilder() {}
		
	private static Map<String, Object> classpathChanged = new ConcurrentHashMap<String, Object>();
	private static ClasspathListenerManager classpathListener;

	/**
	 * indicate that the classpath changed for the given project since the last build
	 * This mirrors the same behavior as the JavaBuilder, which keeps a state between
	 * builds and checks for classpath changes at every build.
	 * 
	 * This is triggered by a element change listener in SpringModel that keeps listening
	 * for classpath changes.
	 * 
	 * @param projectName The name of the project
	 */
	private static void classpathChanged(String projectName) {
		classpathChanged.put(projectName, ImmutableMap.of());
	}
	
	private static synchronized void ensureClasspathListener() {
		if (classpathListener == null) {
			classpathListener = new ClasspathListenerManager((jp) -> classpathChanged(jp.getElementName()));
		}
	}

	protected final IProject[] build(final int kind, Map<String, String> args, final IProgressMonitor monitor) throws CoreException {
		ensureClasspathListener();
		final IProject project = getProject();
		final IResourceDelta delta = getDelta(project);

		// check for classpath changes (that require a full build)
		Object removed = classpathChanged.remove(project.getName());
		final int buildKind = removed != null ? IncrementalProjectBuilder.FULL_BUILD : kind;
		
		Set<IResource> affectedResources = getAffectedResources(project, buildKind, delta);
		List<IValidationRule> rules = ValidationRuleDefinitions.getRuleDefinitions();
		if (!affectedResources.isEmpty() && !rules.isEmpty()) {
			monitor.beginTask("Validation of Boot Validation Rules", affectedResources.size() * rules.size());
			try {
				for (IResource rsrc : affectedResources) {
					IModelElement element = CompilationUnitElement.create(rsrc);
					MarkerUtils.deleteAllMarkers(rsrc, MARKER_ID);
					if (element != null) {
						for (IValidationRule rule : rules) {
							if (rule.supports(element)) {
								rule.validate(element, validationContext(rsrc, rule), new SubProgressMonitor(monitor, 1));
							} else {
								monitor.worked(1);
							}
						}
					}
				}
			} finally {
				monitor.done();
			}
		}
		return null;
	}
	
	private IValidationContext validationContext(IResource rsrc, IValidationRule rule) {
		SeverityProvider severityProvider = new PreferencesBasedSeverityProvider(ConfigProcessingPreferencesPage.util, rsrc.getProject(), BootValidationActivator.PLUGIN_ID, EditorType.JAVA);
		return (IResource cu, ProblemType problemId, String msg, int offset, int end) -> {
			ProblemSeverity severity = severityProvider.getSeverity(problemId);
			if (severity == ProblemSeverity.IGNORE) {
				return;
			}
			ValidationProblem problem = new ValidationProblem() {
				public int getStart() {
					return offset;
				}
				
				@Override
				public int getSeverity() {
					switch (severity) {
					case ERROR:
						return IMarker.SEVERITY_ERROR;
					case WARNING:
						return IMarker.SEVERITY_WARNING;
					default:
						throw new IllegalStateException("Missing switch case? "+severity);
					}
				}
				
				@Override
				public String getRuleId() {
					return rule.getId();
				}
				
				@Override
				public IResource getResource() {
					return rsrc;
				}
				
				@Override
				public String getMessage() {
					return msg;
				}
				
				@Override
				public int getEnd() {
					return end;
				}
				
				@Override
				public String getErrorId() {
					return problemId.toString();
				}
			};
			BootMarkerUtils.createProblemMarker(MARKER_ID, problem);
		};
	}

	/**
	 * Create a list of affected resources from a resource tree.
	 */
	public static class ResourceTreeVisitor implements IResourceVisitor {

		private Set<IResource> resources;

		public ResourceTreeVisitor() {
			this.resources = new LinkedHashSet<IResource>();
		}

		public Set<IResource> getResources() {
			return resources;
		}

		public boolean visit(IResource resource) throws CoreException {
			if (resource instanceof IFile) {
				resources.add(resource);
			} else if (resource instanceof IProject) {
				resources.add(resource);
			}
			return true;
		}
	}

	/**
	 * Collects all affected resources from the given {@link IResourceDelta}.
	 */
	private Set<IResource> getAffectedResources(IProject project, int kind, IResourceDelta delta) throws CoreException {
		Set<IResource> affectedResources;
		if (delta == null || kind == IncrementalProjectBuilder.FULL_BUILD) {
			ResourceTreeVisitor visitor = new ResourceTreeVisitor();
			project.accept(visitor);
			affectedResources = visitor.getResources();
		}
		else {
			ResourceDeltaVisitor visitor = new ResourceDeltaVisitor(kind);
			delta.accept(visitor);
			affectedResources = visitor.getResources();
		}
		return affectedResources;
	}


	/**
	 * Create a list of affected resources from a resource delta.
	 */
	public static class ResourceDeltaVisitor implements IResourceDeltaVisitor {

		private Set<IResource> resources;

		public ResourceDeltaVisitor(int kind) {
			this.resources = new LinkedHashSet<IResource>();
		}

		public Set<IResource> getResources() {
			return resources;
		}

		public boolean visit(IResourceDelta aDelta) throws CoreException {
			boolean visitChildren = false;

			IResource resource = aDelta.getResource();
			if (resource instanceof IProject) {

				// Only check projects with Spring beans nature
				visitChildren = BootPropertyTester.isBootProject((IProject)resource);
				if (visitChildren) {
					resources.add(resource);
				}
			}
			else if (resource instanceof IFolder) {
				resources.add(resource);
				visitChildren = true;
			}
			else if (resource instanceof IFile) {
				switch (aDelta.getKind()) {
				case IResourceDelta.ADDED:
				case IResourceDelta.CHANGED:
					resources.add(resource);
					visitChildren = true;
					break;

				case IResourceDelta.REMOVED:
					resources.add(resource);
					break;
				}
			}
			return visitChildren;
		}
	}

}