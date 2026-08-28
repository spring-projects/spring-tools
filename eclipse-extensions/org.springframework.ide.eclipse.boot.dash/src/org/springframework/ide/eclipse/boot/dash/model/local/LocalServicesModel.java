/*******************************************************************************
 * Copyright (c) 2017, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.springframework.ide.eclipse.boot.core.cli.install.CloudCliInstall;
import org.springframework.ide.eclipse.boot.core.cli.install.IBootInstall;
import org.springframework.ide.eclipse.boot.dash.model.BootDashHyperlink;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.ButtonModel;
import org.springframework.ide.eclipse.boot.dash.model.LocalBootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.LocalCloudServiceDashElement;
import org.springframework.ide.eclipse.boot.dash.model.RefreshState;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel.FilterChoice;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * This contain the bits and pieces of the LocalBootDashModel that pertain to local services.
 *
 * @author Alex Boyko - Orignal implementation
 * @author Kris De Volder - Cleaned up code and separated from the rest, to make it easier to evolve.
 */
public class LocalServicesModel extends AbstractDisposable {

	private LocalBootDashModel bootDashModel;
	private LiveExpression<Boolean> hideCloudCliServices;
	private LiveVariable<RefreshState> refreshState = new LiveVariable<>(RefreshState.READY);
	private LiveSetVariable<LocalCloudServiceDashElement> cloudCliServices = addDisposableChild(new LiveSetVariable<>(AsyncMode.SYNC));

	private LiveSetVariable<ButtonModel> buttons = new LiveSetVariable<>();

	BootDashHyperlink enableCloudServicesButton = new BootDashHyperlink("Install local cloud services") {
		public void doPerform(UserInteractions ui) throws Exception {
			IBootInstall bootInstall = defaultBootInstall.getValue();
			if (bootInstall != null) {
				if (bootInstall.getExtension(CloudCliInstall.class) == null) {
					new AutoCloudCliInstaller(bootInstall).performInstall(ui);
				}
				if (bootInstall.getExtension(CloudCliInstall.class) != null) {
					viewerFilters.remove(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES);
				}
			}
		}
	};

	private LiveSetVariable<FilterChoice> viewerFilters;
	private LiveExpression<IBootInstall> defaultBootInstall;
	private LiveExpression<CloudCliInstall> cloudCliInstall;

	public LocalServicesModel(BootDashViewModel viewModel, LocalBootDashModel bootDashModel, LiveExpression<IBootInstall> defaultBootInstall) {
		this.defaultBootInstall = defaultBootInstall;
		this.viewerFilters = viewModel.getToggleFilters().getSelectedFilters();
		this.bootDashModel = bootDashModel;

		hideCloudCliServices = addDisposableChild(new LiveExpression<Boolean>() {
			{
				dependsOn(viewerFilters);
			}

			@Override
			protected Boolean compute() {
				return viewerFilters.contains(ToggleFiltersModel.FILTER_CHOICE_HIDE_LOCAL_SERVICES);
			}
		});
		cloudCliInstall = defaultBootInstall.then(bootInstall ->
			bootInstall == null ? null : bootInstall.getExtensionExp(CloudCliInstall.class)
		);
		cloudCliInstall.onChange(this, (e, v) -> {
			if (cloudCliInstall.getValue() != null) {
				buttons.remove(enableCloudServicesButton);
			} else {
				buttons.add(enableCloudServicesButton);
			}
			refresh();
		});
		hideCloudCliServices.onChange(this, (e, v) -> {
			refresh();
		});
	}

	@Override
	public void dispose() {
		super.dispose();
		if (cloudCliServices != null) {
			cloudCliServices.getValue().forEach(bde -> bde.dispose());
			cloudCliServices.dispose();
			cloudCliServices = null;
		};
	}

	public void refresh() {
		if (hideCloudCliServices.getValue()) {
			cloudCliServices.getValue().forEach(bde -> bde.dispose());
			cloudCliServices.replaceAll(Collections.emptySet());
			buttons.replaceAll(Collections.emptySet());
		} else {
			new Job("Loading local cloud services") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						if (cloudCliInstall.getValue() == null) {
							buttons.add(enableCloudServicesButton);
						}

						refreshState.setValue(RefreshState.loading("Fetching Local Cloud Sevices..."));
						List<LocalCloudServiceDashElement> newCloudCliservices = fetchLocalServices();
						cloudCliServices.getValue().forEach(bde -> bde.dispose());
						cloudCliServices.replaceAll(newCloudCliservices);
						return Status.OK_STATUS;
					} finally {
						refreshState.setValue(RefreshState.READY);
					}
				}
			}.schedule();
		}
	}

	private List<LocalCloudServiceDashElement> fetchLocalServices() {
		IBootInstall bootInstall = defaultBootInstall.getValue();
		if (bootInstall != null) {
			try {
				CloudCliInstall cloudCliInstall =  bootInstall.getExtension(CloudCliInstall.class);
				if (cloudCliInstall != null) {
					Version cloudCliVersion = cloudCliInstall.getVersion();
					if (cloudCliVersion != null
							&& CloudCliInstall.CLOUD_CLI_JAVA_OPTS_SUPPORTING_VERSIONS.match(cloudCliVersion)) {
						return Arrays.stream(cloudCliInstall.getCloudServices()).map(serviceId -> new LocalCloudServiceDashElement(bootDashModel, serviceId)).collect(Collectors.toList());
					}
				}
			} catch (Exception e) {
				Log.log(e);
			}
		}
		return Collections.emptyList();
	}

	public ObservableSet<LocalCloudServiceDashElement> getCloudCliServices() {
		return cloudCliServices;
	}

	public LiveExpression<RefreshState> getRefreshState() {
		return refreshState;
	}

	public ObservableSet<ButtonModel> getButtons() {
		return buttons;
	}

}
