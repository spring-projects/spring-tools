/*******************************************************************************
 * Copyright (c) 2015, 2021 Pivotal, Inc.
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.springframework.ide.eclipse.beans.ui.live.model.TypeLookup;
import org.springframework.ide.eclipse.beans.ui.live.model.TypeLookupImpl;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens.CancelationToken;
import org.springframework.ide.eclipse.boot.dash.util.Utils;
import org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager.ClasspathListener;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSets;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableSet;

/**
 * Abstract base class that is convenient to implement {@link BootDashElement}.
 * @author Kris De Volder
 */
public abstract class WrappingBootDashElement<T> extends AbstractDisposable implements BootDashElement {

	public static final String TAGS_KEY = "tags";

	private static final String DEFAULT_RM_PATH_KEY = "default.request-mapping.path";
	public static final String DEFAULT_RM_PATH_DEFAULT = "/";

	protected final T delegate;

	private CancelationTokens cancelationTokens = new CancelationTokens();

	private BootDashModel bootDashModel;
	private TypeLookup typeLookup;

	@SuppressWarnings("rawtypes")
	private ValueListener elementStateNotifier = new ValueListener() {
		public void gotValue(LiveExpression exp, Object value) {
			getBootDashModel().notifyElementChanged(WrappingBootDashElement.this, "ValueChanged("+exp+", "+value+")");
		}
	};

	private ValueListener<?> elementNotifier;

	@Override
	public BootDashColumn[] getColumns() {
		return getTarget().getDefaultColumns();
	}

	public WrappingBootDashElement(BootDashModel bootDashModel, T delegate) {
		this.bootDashModel = bootDashModel;
		this.delegate = delegate;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((delegate == null) ? 0 : delegate.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		@SuppressWarnings("rawtypes")
		WrappingBootDashElement other = (WrappingBootDashElement) obj;
		if (delegate == null) {
			if (other.delegate != null)
				return false;
		} else if (!delegate.equals(other.delegate))
			return false;
		return true;
	}


	@Override
	public String toString() {
		return delegate.toString();
	}

	protected TypeLookup getTypeLookup() {
		if (typeLookup == null) {
			typeLookup = new TypeLookupImpl(getName(), getProject());
		}
		return typeLookup;
	}

	public abstract PropertyStoreApi getPersistentProperties();

	@Override
	public LinkedHashSet<String> getTags() {
		try {
			String[] tags = getPersistentProperties() == null ? null : getPersistentProperties().get(TAGS_KEY, (String[])null);
			if (tags != null) {
				return new LinkedHashSet<>(Arrays.asList(tags));
			}
		} catch (Exception e) {
			BootDashActivator.log(e);
		}
		return new LinkedHashSet<>();
	}

	@Override
	public void setTags(LinkedHashSet<String> newTags) {
		try {
			if (newTags == null || newTags.isEmpty()) {
				getPersistentProperties().put(TAGS_KEY, (String[])null);
			} else {
				getPersistentProperties().put(TAGS_KEY, newTags.toArray(new String[newTags.size()]));
			}
			bootDashModel.notifyElementChanged(this, "setTags");
		} catch (Exception e) {
			BootDashActivator.log(e);
		}
	}

	@Override
	public final String getDefaultRequestMappingPath() {
		String storedValue = getPersistentProperties() == null ? null : getPersistentProperties().get(DEFAULT_RM_PATH_KEY);
		if (storedValue != null) {
			return storedValue;
		}
		//inherit a default value from parent node?
		Object parent = getParent();
		if (parent instanceof BootDashElement) {
			String inheritedValue = ((BootDashElement) parent).getDefaultRequestMappingPath();
			return inheritedValue;
		}
		return null;
	}

	@Override
	public final void setDefaultRequestMappingPath(String defaultPath) {
		try {
			getPersistentProperties().put(DEFAULT_RM_PATH_KEY, defaultPath);
			getBootDashModel().notifyElementChanged(this, "setDefaultRequestMappingPath");
		} catch (Exception e) {
			BootDashActivator.log(e);
		}
	}

	private Map<ClasspathPropertyTester, LiveExpression<Boolean>> classpathProperties = new HashMap<>();

	@Override
	public boolean projectHasClasspathProperty(ClasspathPropertyTester tester) {
		LiveExpression<Boolean> exp;
		synchronized (classpathProperties) {
			exp = classpathProperties.computeIfAbsent(tester, t -> {
				LiveExpression<Boolean> propertyExp = new LiveExpression<Boolean>(false) {
					@Override
					protected Boolean compute() {
						IProject p = getProject();
						try {
							if (p != null && p.isAccessible()) {
								IJavaProject jp = JavaCore.create(p);
								if (jp.exists()) {
									IClasspathEntry[] classpath = jp.getResolvedClasspath(true);
									if (classpath != null) {
										return tester.test(classpath);
									}
								}
							}
						} catch (Exception e) {
							Log.log(e);
						}
						//Reaching here means we couldn't apply the tester, e.g. because classpath not (yet) available.
						return false;
					}
				};
				propertyExp.refresh();
				ClasspathListenerManager classpathListener = new ClasspathListenerManager(new ClasspathListener() {
					public void classpathChanged(IJavaProject jp) {
						if (jp.getProject().equals(getProject())) {
							propertyExp.refresh();
						}
					}
				});
				this.dependsOn(propertyExp);
				this.addDisposableChild(classpathListener);
				this.addDisposableChild(propertyExp);
				return propertyExp;
			});
		}
		return exp.getValue();
	}

	@Override
	public final boolean projectHasDevtoolsDependency() {
		return projectHasClasspathProperty(ClasspathPropertyTester.HAS_DEVTOOLS);
	}



	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void dependsOn(LiveExpression<?> liveProperty) {
		liveProperty.addListener(new ValueListener() {
			public void gotValue(LiveExpression exp, Object value) {
				getBootDashModel().notifyElementChanged(WrappingBootDashElement.this, "livePropertyChanged("+exp+", "+value+")");
			}
		});
	}

	public BootDashModel getBootDashModel() {
		return bootDashModel;
	}

	@Override
	public IJavaProject getJavaProject() {
		return getProject() != null ? JavaCore.create(getProject()) : null;
	}

	@Override
	public ObservableSet<BootDashElement> getChildren() {
		return LiveSets.emptySet(BootDashElement.class);
	}

	@Override
	public ImmutableSet<BootDashElement> getCurrentChildren() {
		return getChildren().getValue();
	}

	@Override
	public ImmutableSet<ILaunchConfiguration> getLaunchConfigs() {
		//Default implementation for BDEs that do not have any relation to launch configs
		//Subclass should override when elements relate to launch configs.
		return ImmutableSet.of();
	}

	/**
	 * Gets a summary of the 'livePorts' for this node and its children. This default implementation
	 * is provided for nodes that only have a single port. Nodes that need to compute an actual
	 * summary should override this.
	 */
	@Override
	public ImmutableSet<Integer> getLivePorts() {
		int port = getLivePort();
		if (port > 0) {
			return ImmutableSet.of(port);
		} else {
			return ImmutableSet.of();
		}
	}

	/**
	 * Ensure that element state notifications are fired when a given liveExp's value changes.
	 */
	@SuppressWarnings("unchecked")
	protected void addElementState(LiveExpression<?> state) {
		state.addListener(elementStateNotifier);
	}

	/**
	 * Ensure that model state notifications are fired when a given liveExp's value changes.
	 */
	protected void addModelState(LiveExpression<?> state) {
		state.onChange((e,v) -> {
			getBootDashModel().notifyModelStateChanged();
		});
	}

	@Override
	public String getUrl() {
		return Utils.createUrl(getLiveHost(), getLivePort(), getDefaultRequestMappingPath());
	}

	private synchronized ValueListener<?> getElementNotifier() {
		if (elementNotifier == null) {
			elementNotifier = new ValueListener<Object>() {
				@Override
				public void gotValue(LiveExpression<Object> exp, Object value) {
					getBootDashModel().notifyElementChanged(WrappingBootDashElement.this, "ValueChanged("+exp+", "+value+")");
				}
			};
		}
		return elementNotifier;
	}

	/**
	 * Attach a listener to a given liveExp so that the model's 'notifyElementChanged' is called
	 * any time the liveExps value changes.
	 */
	@SuppressWarnings("unchecked")
	protected void addElementNotifier(LiveExpression<?> exp) {
		@SuppressWarnings("rawtypes")
		ValueListener notifier = getElementNotifier();
		exp.addListener(notifier);
	}

	public CancelationToken createCancelationToken() {
		return cancelationTokens.create();
	}

	public void cancelOperations() {
		cancelationTokens.cancelAll();
	}

	@Override
	public RunTarget getTarget() {
		return getBootDashModel().getRunTarget();
	}
}
