/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.cloudfoundry.client.v2;

/**
 * Manages a set of CancelationTokens.
 *
 * @author Kris De Volder
 */
public class CancelationTokens {

	private String DEBUG = null;

	//Note: we don't actually have to keep a set of tokens explicitly.
	// The tokens use a 'id' which is incremented on each new token.
	//So it is easy to cancel all existing tokens based on a their
	//id simply by remembering the 'id' where the cancelation
	//occurred. All ids 'older' than the current id are 'canceled'.

	/**
	 * An uncancelable token that can be used by operations that don't
	 * need cancelation support.
	 */
	public static final CancelationToken NULL = new CancelationToken() {
		@Override
		public boolean isCanceled() {
			return false;
		}
	};

	private final Object SYNC = CancelationTokens.this;

	private int canceledAllBefore = 0;
	private int nextId = 0;

	public CancelationTokens() {
	}

	public CancelationTokens(String debug) {
		this.DEBUG = debug;
	}

	public interface CancelationToken {
		boolean isCanceled();
	}

	public synchronized CancelationToken create() {
		CancelationToken token = new ManagedToken();
		debug("creating cancelation token: "+token);
		return token;
	}

	private class ManagedToken implements CancelationToken {
		private int id;

		private ManagedToken() {
			synchronized (SYNC) {
				this.id = nextId++;
			}
		}

		public boolean isCanceled() {
			synchronized (SYNC) {
				boolean isCanceled = id < canceledAllBefore;
				debug("isCanceled? ["+id+"] => "+isCanceled);
				return isCanceled;
			}
		}

		@Override
		public String toString() {
			return "CancelToken("+id+")";
		}
	}

	public synchronized void cancelAll() {
		canceledAllBefore = nextId;
		debug("CancelationTokens < "+canceledAllBefore+" are Canceled");
	}

	private void debug(String string) {
		if (DEBUG != null) {
			System.out.println(DEBUG+": "+string);
		}
	}
}
