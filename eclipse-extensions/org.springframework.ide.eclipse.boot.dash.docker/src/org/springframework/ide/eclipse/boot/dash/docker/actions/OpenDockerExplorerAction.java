package org.springframework.ide.eclipse.boot.dash.docker.actions;

import org.eclipse.linuxtools.docker.core.DockerConnectionManager;
import org.eclipse.linuxtools.internal.docker.core.DockerConnection;
import org.eclipse.linuxtools.internal.docker.core.DockerConnection.Builder;
import org.eclipse.linuxtools.internal.docker.core.TCPConnectionSettings;
import org.eclipse.linuxtools.internal.docker.core.UnixSocketConnectionSettings;
import org.eclipse.linuxtools.internal.docker.ui.views.DockerExplorerView;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerRunTarget;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashModelAction;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

@SuppressWarnings("restriction")
public class OpenDockerExplorerAction extends AbstractBootDashModelAction {

	@SuppressWarnings("restriction")
	public OpenDockerExplorerAction(LiveExpression<BootDashModel> section, SimpleDIContext context) {
		super(section, context);
		this.setText("Open Docker Explorer");
		this.setToolTipText("Open Eclipse's Docker Explorer View");
//		this.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/remove_target.png"));
//		this.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/remove_target_disabled.png"));
	}

	private boolean shouldEnable() {
		return getRunTarget() instanceof DockerRunTarget;
	}

	public void updateEnablement() {
		this.setEnabled(shouldEnable());
	}

	public void updateVisibility() {
		this.setVisible(shouldEnable());
	}

	private static final String VIEW_TYPE_ID = DockerExplorerView.VIEW_ID;
	
	@Override
	public void run() {
		DockerRunTarget target = getRunTarget();
		if (target != null) {
			try {
				DockerConnectionManager connections = DockerConnectionManager.getInstance();
				if (connections.getAllConnections().isEmpty()) {
					Builder cb = new DockerConnection.Builder()
							.name(target.getName());
					DockerConnection c = null;
					String uri = target.getParams().getUri();
					if (uri.startsWith("unix:")) {
						c = cb.unixSocketConnection(new UnixSocketConnectionSettings(uri));
					} else if (uri.startsWith("tcp:")) {
						String host = uri.substring("tcp:".length());
						while (host.startsWith("/")) {
							host = host.substring(1);
						}
						c = cb.tcpConnection(new TCPConnectionSettings(host, null));
					}
					connections.addConnection(c);
				}
				
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				if (page != null) {
					page.showView(VIEW_TYPE_ID);
				}
			} catch (Exception e) {
				Log.log(e);
			}
		}
	}

	private DockerRunTarget getRunTarget() {
		BootDashModel section = sectionSelection.getValue();
		if (section != null) {
			RunTarget<?> t = section.getRunTarget();
			if (t instanceof DockerRunTarget) {
				return (DockerRunTarget) t;
			}
		}
		return null;
	}

}
