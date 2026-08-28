package org.springframework.ide.eclipse.boot.dash.views;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
import org.springframework.ide.eclipse.boot.dash.api.SystemPropertySupport;
import org.springframework.ide.eclipse.boot.dash.devtools.DevtoolsUtil;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Action for starting/restarting Remove DevTools Client application
 *
 * @author Alex Boyko
 *
 */
public class EnableRemoteDevtoolsAction extends AbstractBootDashElementsAction {

	private ElementStateListener stateListener;
	private boolean enable;

	/**
	 * For testing code to be able to observe / synchronize with the end of a executed action.
	 */
	public CompletableFuture<Void> lastOperation;

	public EnableRemoteDevtoolsAction(Params params) {
		super(params);
		this.setText("Enable Remote DevTools Server");
		this.setToolTipText("Enables server-side Remote DevTools support for remote application.");
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
		if (getSelectedElements().size() == 1) {
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
			return data instanceof SystemPropertySupport;
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
			if (visibleForElement(bde) && project != null) {
				if (BootPropertyTester.fastHasDevTools(bde.getProject())) {
					App data = ((GenericRemoteAppElement)bde).getAppData();
					if (data instanceof SystemPropertySupport) {
						String secret = ((SystemPropertySupport)data).getSystemProperty(DevtoolsUtil.REMOTE_SECRET_PROP);
						this.enable = secret == null;
						if (enable) {
							this.setText("Enable Remote DevTools Server");
							this.setToolTipText("Enables server-side Remote DevTools support for remote application.");
						} else {
							this.setText("Disable Remote DevTools Server");
							this.setToolTipText("Disables server-side Remote DevTools support for remote application.");
						}
						return true;
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	@Override
	public void run() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BootDashElement _e : getSelectedElements()) {
			if (_e instanceof GenericRemoteAppElement && _e.getProject() != null) {
				GenericRemoteAppElement e = (GenericRemoteAppElement) _e;
				futures.add(e.enableDevtools(enable));
			}
		}
		lastOperation = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
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
