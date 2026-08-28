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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.springframework.ide.eclipse.boot.dash.api.Deletable;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.devtools.DevtoolsPortRefresher;
import org.springframework.ide.eclipse.boot.dash.model.local.LocalServicesModel;
import org.springframework.ide.eclipse.boot.dash.util.LaunchConfRunStateTracker;
import org.springframework.ide.eclipse.boot.dash.util.LaunchConfigurationTracker;
import org.springframework.ide.eclipse.boot.dash.views.BootDashModelConsoleManager;
import org.springframework.ide.eclipse.boot.dash.views.BootDashTreeView;
import org.springframework.ide.eclipse.boot.dash.views.LocalElementConsoleManager;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager.ClasspathListener;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ProjectChangeListenerManager;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ProjectChangeListenerManager.ProjectChangeListener;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSets;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Model of the contents for {@link BootDashTreeView}, provides mechanism to attach listeners to model
 * and attaches itself as a workspace listener to keep the model in synch with workspace changes.
 *
 * @author Kris De Volder
 */
public class LocalBootDashModel extends AbstractBootDashModel implements DeletionCapabableModel {

	private IWorkspace workspace;
	private BootProjectDashElementFactory projectElementFactory;
	private LaunchConfDashElementFactory launchConfElementFactory;

	ProjectChangeListenerManager openCloseListenerManager;
	ClasspathListenerManager classpathListenerManager;

	private final LaunchConfRunStateTracker launchConfRunStateTracker = new LaunchConfRunStateTracker();
	final LaunchConfigurationTracker launchConfTracker = new LaunchConfigurationTracker(BootLaunchConfigurationDelegate.TYPE_ID);

	private LiveSetVariable<BootProjectDashElement> applications; //lazy created
	private ObservableSet<BootDashElement> allElements;

	private BootDashModelConsoleManager consoleManager;

	private DevtoolsPortRefresher devtoolsPortRefresher;
	private LiveExpression<Pattern> projectExclusion;
	private ValueListener<Pattern> projectExclusionListener;

	private IPropertyStore modelStore;

	private LocalServicesModel localServices;

	private LiveVariable<RefreshState> bootAppsRefreshState = new LiveVariable<>(RefreshState.READY);

	private LiveExpression<RefreshState> refreshState;
	private BundleListener bundleListener;

	public class WorkspaceListener implements ProjectChangeListener, ClasspathListener {

		@Override
		public void projectChanged(IProject project) {
			updateElementsFromWorkspace();
		}

		@Override
		public void classpathChanged(IJavaProject jp) {
			updateElementsFromWorkspace();
		}
	}

	public LocalBootDashModel(BootDashModelContext context, BootDashViewModel parent) {
		super(RunTargets.LOCAL, parent);
		this.workspace = context.getWorkspace();
		this.localServices = new LocalServicesModel(getViewModel(), this, context.getBootInstallManager().getDefaultInstallExp());
		this.refreshState = new LiveExpression<>() {
			{
				dependsOn(bootAppsRefreshState);
				dependsOn(localServices.getRefreshState());
				addListener((e,v) -> notifyModelStateChanged());
			}

			@Override
			protected RefreshState compute() {
				return RefreshState.merge(bootAppsRefreshState.getValue(), localServices.getRefreshState().getValue());
			}
		};
		this.launchConfElementFactory = new LaunchConfDashElementFactory(this, context.getLaunchManager());
		this.projectElementFactory = new BootProjectDashElementFactory(this, context.getProjectProperties(), launchConfElementFactory);
		this.consoleManager = new LocalElementConsoleManager();
		this.projectExclusion = context.getBootProjectExclusion();

		RunTargetType<?> type = getRunTarget().getType();
		IPropertyStore typeStore = PropertyStores.createForScope(type, context.getRunTargetProperties());
		this.modelStore = PropertyStores.createSubStore(getRunTarget().getId(), typeStore);

		// Listen to M2E JDT plugin active event to refresh local boot project dash elements.
		addMavenInitializationIssueEventHandling();

	}
	/**
	 * Refresh boot project dash elements once m2e JDT plugin is fully
	 * initialized. Boot project checks may not succeed in some cases if m2e JDT
	 * hasn't completed it's start procedure
	 */
	private void addMavenInitializationIssueEventHandling() {
		Bundle bundle = Platform.getBundle("org.eclipse.m2e.jdt");
		if (bundle != null) {
			bundleListener = new BundleListener() {
				@Override
				public void bundleChanged(BundleEvent event) {
					if (event.getBundle() == bundle && event.getType() == BundleEvent.STARTED) {
						try {
							updateElementsFromWorkspace();
							if (applications != null && applications.getValue() != null) {
								applications.getValue().stream().forEach(e -> e.refreshHasMainMethod());
							}
						} catch (Throwable t) {
							Log.log(t);
						} finally {
							bundle.getBundleContext().removeBundleListener(this);
						}
					}
				}
			};
			bundle.getBundleContext().addBundleListener(bundleListener);
		}
	}

	void init() {
		if (allElements == null) {
			this.applications = new LiveSetVariable<>(AsyncMode.SYNC);
			this.allElements = LiveSets.union(
					this.applications,
					this.localServices.getCloudCliServices()
			);
			WorkspaceListener workspaceListener = new WorkspaceListener();
			this.openCloseListenerManager = new ProjectChangeListenerManager(workspace, workspaceListener);
			this.classpathListenerManager = new ClasspathListenerManager(workspaceListener);
			projectExclusion.addListener(projectExclusionListener = new ValueListener<>() {
				public void gotValue(LiveExpression<Pattern> exp, Pattern value) {
					updateElementsFromWorkspace();
				}
			});

			refresh(null);

			this.devtoolsPortRefresher = new DevtoolsPortRefresher(this, projectElementFactory);

			final IResourceChangeListener workspaceBuildListener = new IResourceChangeListener() {

				@Override
				public void resourceChanged(IResourceChangeEvent event) {
					if (event.getSource() != null) {
						BuildDetectorVisitor visitor = new BuildDetectorVisitor();
						try {
							event.getDelta().accept(visitor);
							for (IProject p : visitor.getProjects()) {
								Optional<BootProjectDashElement> found = applications.getValue().stream()
										.filter(e -> p.equals(e.getProject())).findFirst();
								found.ifPresent(bde -> bde.refreshHasMainMethod());
							}
						} catch (CoreException e) {
							Log.log(e);
						}
					}

				}
			};

			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			workspace.addResourceChangeListener(workspaceBuildListener, IResourceChangeEvent.POST_BUILD);
			addDisposableChild(() -> workspace.removeResourceChangeListener(workspaceBuildListener));

			addDisposableChild(getViewModel().getToggleFilters().getSelectedFilters().onChange((e, v) -> {
				if (e.getValue().contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_NOT_RUNNABLE_APPS)) {
					for (BootProjectDashElement a : applications.getValue()) {
						a.refreshHasMainMethod();
					}
				}
			}));
		}
	}

	private static class BuildDetectorVisitor implements IResourceDeltaVisitor {

		private Set<IProject> projects = new HashSet<>();

		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			if (resource instanceof IProject) {
				projects.add((IProject) resource);
				return false;
			}
			return true;
		}

		public Set<IProject> getProjects() {
			return projects;
		}

	}

	/**
	 * When no longer needed the model should be disposed, otherwise it will continue
	 * listening for changes to the workspace in order to keep itself in synch.
	 */
	public void dispose() {
		if (applications != null) {
			applications.getValue().forEach(bde -> bde.dispose());
			applications.dispose();
			applications = null;
			openCloseListenerManager.dispose();
			openCloseListenerManager = null;
			classpathListenerManager.dispose();
			classpathListenerManager = null;
			devtoolsPortRefresher.dispose();
			devtoolsPortRefresher = null;
		}
		if (launchConfElementFactory != null) {
			launchConfElementFactory.dispose();
			launchConfElementFactory = null;
		}
		if (projectElementFactory != null) {
			projectElementFactory.dispose();
			projectElementFactory = null;
		}
		if (projectExclusionListener != null) {
			projectExclusion.removeListener(projectExclusionListener);
			projectExclusionListener=null;
		}
		if (localServices != null) {
			localServices.dispose();
			localServices = null;
		}
		if (allElements != null) {
			allElements.dispose();
			allElements = null;
		}
		launchConfTracker.dispose();
		launchConfRunStateTracker.dispose();
		// Remove bundle listener
		if (bundleListener != null) {
			Bundle bundle = Platform.getBundle("org.eclipse.m2e.jdt");
			if (bundle != null) {
				bundle.getBundleContext().removeBundleListener(bundleListener);
			}
		}

	}

	void updateElementsFromWorkspace() {
		new Job("Updating Local Contents for Boot Dashboard") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				doUpdateElementsFromWorkspace();
				return Status.OK_STATUS;
			}

		}.schedule();
	}

	private void doUpdateElementsFromWorkspace() {
		LiveSetVariable<BootProjectDashElement> apps = this.applications;
		if (apps != null) {
			Set<BootProjectDashElement> newElements = Arrays.stream(this.workspace.getRoot().getProjects())
					.map(projectElementFactory::createOrGet)
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
			apps.replaceAll(newElements);
			projectElementFactory.disposeAllExcept(newElements);
		}
	}

	public synchronized ObservableSet<BootDashElement> getElements() {
		init();
		return allElements;
	}

	@Override
	public ObservableSet<ButtonModel> getButtons() {
		return localServices.getButtons();
	}

	/**
	 * Trigger manual model refresh.
	 */
	public void refresh(UserInteractions ui) {
		updateElementsFromWorkspace();
		applications.getValue().stream().forEach(e -> e.refreshHasMainMethod());
		localServices.refresh();
	}

	@Override
	public BootDashModelConsoleManager getElementConsoleManager() {
		return consoleManager;
	}

	public LaunchConfRunStateTracker getLaunchConfRunStateTracker() {
		return launchConfRunStateTracker;
	}

	public BootProjectDashElementFactory getProjectElementFactory() {
		return projectElementFactory;
	}

	public LaunchConfDashElementFactory getLaunchConfElementFactory() {
		return launchConfElementFactory;
	}

	@Override
	public void delete(Collection<BootDashElement> elements, UserInteractions ui) {
		for (BootDashElement el : elements) {
			if (el instanceof Deletable) {
				try {
					((Deletable)el).delete();
				} catch (Exception e) {
					Log.log(e);
				}
			}
		}
	}

	@Override
	public boolean canDelete(BootDashElement element) {
		return element instanceof Deletable && ((Deletable)element).canDelete();
	}

	@Override
	public String getDeletionConfirmationMessage(Collection<BootDashElement> value) {
		return "Are you sure you want to delete the selected local launch configuration(s)? The configuration(s) will be permanently removed from the workspace.";
	}

	public IPropertyStore getModelStore() {
		return modelStore;
	}

	@Override
	public RefreshState getRefreshState() {
		return refreshState.getValue();
	}
}
