package org.springframework.ide.eclipse.boot.dash.views;

import java.net.URL;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.PlatformUI;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.DevtoolsConnectable;
import org.springframework.ide.eclipse.boot.dash.api.TemporalBoolean;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.base.Objects;

public class RestartDevtoolsClientAction extends AbstractBootDashElementsAction {

	private ElementStateListener stateListener;

	public RestartDevtoolsClientAction(Params params) {
		super(params);
		this.setText("Restart Remote DevTools Client");
		this.setToolTipText("Start local devtools 'client' process for remote application.");
		URL url = FileLocator.find(Platform.getBundle("org.springframework.ide.eclipse.boot"), new Path("resources/icons/boot-devtools-icon.png"), null);
		if (url != null) {
			this.setImageDescriptor(ImageDescriptor.createFromURL(url));
		}
		if (model != null) {
			model.addElementStateListener(stateListener = new ElementStateListener() {
				public void stateChanged(BootDashElement e) {
					if (getSelectedElements().contains(e) && !PlatformUI.getWorkbench().isClosing()) {
						PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
							public void run() {
								updateEnablement();
							}
						});
					}
				}
			});
		}
	}

	@Override
	public void updateVisibility() {
		boolean visible = false;
		if (!getSelectedElements().isEmpty()) {
			visible = true;
			for (BootDashElement e : getSelectedElements()) {
				if (!visibleForElement(e)) {
					visible = false;
					break;
				}
			}
		}
		setVisible(visible);
	}

	private boolean visibleForElement(BootDashElement e) {
		if (e instanceof GenericRemoteAppElement) {
			App data = ((GenericRemoteAppElement) e).getAppData();
			return ((DevtoolsConnectable)data).isDevtoolsConnectable() != TemporalBoolean.NEVER;
		}
		return false;
	}

	@Override
	public void updateEnablement() {
		boolean enable = false;
		if (!getSelectedElements().isEmpty()) {
			enable = true;
			for (BootDashElement e : getSelectedElements()) {
				if (!enableForElement(e)) {
					enable = false;
					break;
				}
			}
		}
		this.setEnabled(enable);
	}

	private boolean enableForElement(BootDashElement bde) {
		try {
			IProject project = bde.getProject();
			if (bde instanceof GenericRemoteAppElement && project != null && bde.getRunState().isActive()) {
				if (BootPropertyTester.fastHasDevTools(project)) {
					App data = ((GenericRemoteAppElement)bde).getAppData();
					if (data instanceof DevtoolsConnectable) {
						return ((DevtoolsConnectable)data).isDevtoolsConnectable().isTrue();
					}
				}
			}
		} catch (TimeoutException e) {
			//expected from fastHasDevTools
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	@Override
	public void run() {
		for (BootDashElement _e : getSelectedElements()) {
			if (_e instanceof GenericRemoteAppElement && _e.getProject() != null) {
				GenericRemoteAppElement e = (GenericRemoteAppElement) _e;
				e.restartRemoteDevtoolsClient();
			}
		}
	}

	@Override
	public void dispose() {
		if (model != null && stateListener != null) {
			model.removeElementStateListener(stateListener);
			stateListener = null;
		}
		super.dispose();
	}

}
