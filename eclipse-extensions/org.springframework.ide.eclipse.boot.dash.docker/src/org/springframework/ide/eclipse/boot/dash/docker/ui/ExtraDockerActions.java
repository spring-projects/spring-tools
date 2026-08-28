package org.springframework.ide.eclipse.boot.dash.docker.ui;

import java.util.Collection;

import org.eclipse.core.runtime.Platform;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.docker.actions.OpenDockerExplorerAction;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveProcessCommandsExecutor;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashAction;
import org.springframework.ide.eclipse.boot.dash.views.BootDashActions;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class ExtraDockerActions implements BootDashActions.Factory {

	@Override
	public Collection<AbstractBootDashAction> create(
			BootDashActions actions, 
			BootDashViewModel model,
			MultiSelection<BootDashElement> selection, 
			LiveExpression<BootDashModel> section, 
			SimpleDIContext context,
			LiveProcessCommandsExecutor liveProcessCmds) {
		Builder<AbstractBootDashAction> contributions = ImmutableList.builder();
		if (section != null) {
			if (Platform.getBundle("org.eclipse.linuxtools.docker.ui") != null) {
				contributions.add(new OpenDockerExplorerAction(section, context));
			}
		}
		return contributions.build();
	}
}
