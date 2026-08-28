package org.springframework.ide.eclipse.boot.dash.model.runtargettypes;

import java.util.Collection;

import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteBootDashModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

public interface RemoteRunTarget<Client, Params> extends RunTarget<Params> {

	public enum ConnectMode {
		INTERACTIVE, // connect operation directly triggered by user action. In this case popping up dialogs to ask for
		               // information such as password is okay
		AUTOMATIC   // connect operation triggered during startup to re-establish  connection to a persisted target.
		             // in this case the implementation should avoid popping up dialogs. Instead if not sufficient info
					// was persisted, then the connect operation siltently aborts / does nothing.
	}

	default boolean isConnected() {
		return getClientExp().getValue() != null;
	}
	LiveExpression<Client> getClientExp();
	default Client getClient() {
		return getClientExp().getValue();
	}

	/**
	 * Typically long-running (network access), avoid calling in UI thread).
	 */
	Collection<App> fetchApps() throws Exception;

	/**
	 * Disconnects the remote target and removes/disposes its
	 * client.
	 */
	void disconnect();

	/**
	 * Attempts to connects to remote target. When succesful a 'client'
	 * is created in the process and this client is subsequently available
	 * as the value of the getClient LiveExp.
	 * <p>
	 * May throw an exception signaling that something went wrong
	 * trying to connect.
	 * @param mode
	 */
	void connect(ConnectMode mode) throws Exception;

}
