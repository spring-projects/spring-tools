/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.livebean;

import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.DEFAULT_ENABLE_JMX;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.DEFAULT_ENABLE_LIFE_CYCLE;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.DEFAULT_ENABLE_LIVE_BEAN_SUPPORT;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.DEFAULT_TERMINATION_TIMEOUT;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.setEnableJMX;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.setEnableLifeCycle;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.setEnableLiveBeanSupport;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.setTerminationTimeout;
import static org.springsource.ide.eclipse.commons.livexp.core.ValidationResult.error;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.osgi.framework.Bundle;
import org.springframework.ide.eclipse.boot.launch.BootLaunchActivator;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.util.ILaunchConfigurationTabModel;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

/**
 * Model for 'enable live bean support' widgetry on a launchconfiguration tab.
 *
 * @author Kris De Volder
 */
public class EnableJmxFeaturesModel implements ILaunchConfigurationTabModel {

	public final String timeOutFieldName = "Termination timeout";

	public final LiveVariable<Boolean> jmxEnabled;
	public final LiveVariable<Boolean> liveBeanEnabled;
	public final LiveVariable<Boolean> lifeCycleEnabled;

	public final LiveVariable<String> terminationTimeout;

	private final Validator validator;

	private LiveVariable<Boolean> dirtyState = new LiveVariable<>(false);

	@SuppressWarnings("unchecked")
	public EnableJmxFeaturesModel() {
		this.jmxEnabled = new LiveVariable<>(DEFAULT_ENABLE_JMX);
		this.liveBeanEnabled = new LiveVariable<>(DEFAULT_ENABLE_LIVE_BEAN_SUPPORT());
		this.lifeCycleEnabled = new LiveVariable<>(DEFAULT_ENABLE_LIFE_CYCLE);

		autoDisableWhenJmxDisabled(liveBeanEnabled, lifeCycleEnabled);

		this.terminationTimeout = new LiveVariable<>("");
		jmxEnabled.addListener(makeDirty());
		liveBeanEnabled.addListener(makeDirty());
		lifeCycleEnabled.addListener(makeDirty());
		terminationTimeout.addListener(makeDirty());

		this.validator = new Validator() {
			{
				dependsOn(jmxEnabled);
				dependsOn(lifeCycleEnabled);
				dependsOn(liveBeanEnabled);
				dependsOn(terminationTimeout);
			}

			@Override
			protected ValidationResult compute() {
				if (lifeCycleEnabled.getValue()) {
					String timeoutStr = terminationTimeout.getValue();
					if (!hasText(timeoutStr)) {
						return error(timeOutFieldName+" must be specified");
					}
					timeoutStr = timeoutStr.trim();
					try {
						long timeout = Long.parseLong(timeoutStr);
						if (!(timeout > 0)) {
							return error(timeOutFieldName+" must be positive");
						}
					} catch (NumberFormatException e) {
						return error(timeOutFieldName+" can't be parsed as an Integer");
					}
				}
//				if (liveBeanEnabled.getValue() && !jmxEnabled.getValue()) {
//					return error("Live Bean support requires JMX to be enabled");
//				}
//				if (lifeCycleEnabled.getValue() && !jmxEnabled.getValue()) {
//					return error("Lifecycle Management requires JMX to be enabled");
//				}
				return ValidationResult.OK;
			}

		};
	}

	private void autoDisableWhenJmxDisabled(@SuppressWarnings("unchecked") LiveVariable<Boolean>... featuresToDisable) {
		jmxEnabled.addListener((exp, value) -> {
			if (!value) {
				for (LiveVariable<Boolean> feature : featuresToDisable) {
					feature.setValue(false);
				}
			}
		});
	}

	private boolean hasText(String portStr) {
		return portStr != null && !portStr.trim().equals("");
	}

	@SuppressWarnings("rawtypes")
	protected ValueListener makeDirty() {
		return new ValueListener() {
			public void gotValue(LiveExpression exp, Object value) {
				dirtyState.setValue(true);
			}
		};
	}

	@Override
	public void initializeFrom(ILaunchConfiguration conf) {
		jmxEnabled.setValue(BootLaunchConfigurationDelegate.getEnableJmx(conf));
		liveBeanEnabled.setValue(BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(conf));
		lifeCycleEnabled.setValue(BootLaunchConfigurationDelegate.getEnableLifeCycle(conf));
		terminationTimeout.setValue(""+BootLaunchConfigurationDelegate.getTerminationTimeout(conf));
		getDirtyState().setValue(false);
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy conf) {
		setEnableJMX(conf, jmxEnabled.getValue());
		setEnableLiveBeanSupport(conf, liveBeanEnabled.getValue());
		setEnableLifeCycle(conf, lifeCycleEnabled.getValue());
		setTerminationTimeout(conf, StringUtil.trim(terminationTimeout.getValue()));
		getDirtyState().setValue(false);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy conf) {
		setEnableLiveBeanSupport(conf, DEFAULT_ENABLE_LIVE_BEAN_SUPPORT());
		setEnableLifeCycle(conf, DEFAULT_ENABLE_LIFE_CYCLE);
		setEnableJMX(conf, DEFAULT_ENABLE_JMX);
		setTerminationTimeout(conf, ""+DEFAULT_TERMINATION_TIMEOUT);
	}

	@Override
	public LiveVariable<Boolean> getDirtyState() {
		return dirtyState;
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return validator;
	}

	public boolean isLiveBeanSupported() {
		BootLaunchActivator activator = BootLaunchActivator.getInstance();
		if (activator != null) {
			return activator.isLiveBeanSupported();
		}
		return false;
	}
}
