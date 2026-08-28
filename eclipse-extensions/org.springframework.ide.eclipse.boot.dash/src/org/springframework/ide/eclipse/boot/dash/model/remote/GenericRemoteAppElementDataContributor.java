/*******************************************************************************
 * Copyright (c) 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.remote;

import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.ClasspathPropertyTester;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder.Contributor;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder.RemoteAppData;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;

import com.google.common.collect.ImmutableSet;

public class GenericRemoteAppElementDataContributor implements Contributor, ElementStateListener {

	private ObservableSet<RemoteAppData> remoteApps;

	public GenericRemoteAppElementDataContributor(SimpleDIContext injections) {
		BootDashViewModel bdv = injections.getBean(BootDashViewModel.class);
		bdv.addElementStateListener(this);
		this.remoteApps = ObservableSet.<RemoteAppData>builder().refresh(AsyncMode.ASYNC)
		.compute(() -> {
			ImmutableSet.Builder<RemoteAppData> allApps = ImmutableSet.builder();
			ImmutableSet<BootDashModel> models = bdv.getSectionModels().getValue();
			for (BootDashModel bootDashModel : models) {
				if (bootDashModel instanceof GenericRemoteBootDashModel) {
					ObservableSet<BootDashElement> childrenExp = bootDashModel.getElements();
					collectFrom(childrenExp, allApps);
				}
			}
			return allApps.build();
		})
		.build();
		remoteApps.setRefreshDelay(2_000); //This delay should not be necessary, but...
			// there seems to be a race condition in spring-boot-ls v2 live hover connection mechanics.
			// when updates to the remote apps fire in quick succession, it often starts shutting down
			// the connection but then doesn't reconnect again when the same url is added again while
			// the disconnect is still in progress.
			// Adding refresh delay avoids this issue by never sending updates in quick succession
//		remoteApps.onChange((e, v) -> {
//			System.out.println(">>> remote jmx apps");
//			ImmutableSet<RemoteAppData> vals = remoteApps.getValues();
//			for (RemoteAppData remoteAppData : vals) {
//				System.out.println(remoteAppData);
//			}
//			System.out.println("<<< remote jmx apps");
//		});
		remoteApps.refresh(); //need one initial manual refresh because registering for
						// boot dash element changes only triggers when something changes.
	}

	private void collectFrom(ObservableSet<BootDashElement> childrenExp, ImmutableSet.Builder<RemoteAppData> allApps) {
		for (BootDashElement child : childrenExp.getValues()) {
			if (child instanceof GenericRemoteAppElement) {
				collectFrom((GenericRemoteAppElement)child, allApps);
			}
		}
	}

	private void collectFrom(GenericRemoteAppElement child, ImmutableSet.Builder<RemoteAppData> allApps) {
		String actuatorUrl = child.getActuatorUrlHere();
		if (actuatorUrl != null) {
			RemoteAppData data = new RemoteAppData(actuatorUrl, child.getLiveHost());
			data.setUrlScheme("http");
			data.setPort(""+child.getLivePort());
			data.setKeepChecking(false);
			data.setManualConnect(!child.hasClasspathProperty(ClasspathPropertyTester.HAS_ACTUATORS));
			data.setProcessId(child.getAppData().getName());
			data.setProcessName(child.getConsoleDisplayName());
			allApps.add(data);
		}
		collectFrom(child.getChildren(), allApps);
	}

	@Override
	public ObservableSet<RemoteAppData> getRemoteApps() {
		return remoteApps;
	}

	@Override
	public void stateChanged(BootDashElement e) {
		remoteApps.refresh();
	}
}
