/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.util;

import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;

/**
 * Helper class that makes it easy to keep trying to execute a bit of code
 * repeatedly until it either succeeds (no exceptions) or times out.
 * <P>
 * Warning: this is not meant to wrap long blocking operations. Long blocking
 * operation will not be interupted to force the timeout to be obeyed.
 *
 * @author Kris De Volder
 */
public abstract class WaitFor {

	private static final long DEFAULT_INTERVAL = 200 /*ms*/;

	public WaitFor(long timeout) throws Exception {
		this(timeout, DEFAULT_INTERVAL);
	}

	public WaitFor(long timeout, long interval) throws Exception {
		waitForIt(timeout, interval);
	}

	private void waitForIt(long timeout, long interval) throws Exception {
		long endTime = System.currentTimeMillis() + timeout;
		Throwable e = null;
		boolean retry;
		do {
			try {
				run();
				e = null;
			} catch (Throwable _e) {
				e = _e;
			}
			retry = e != null && System.currentTimeMillis() < endTime;
			if (retry) {
				try {
					//System.out.println("Failed: "+e.getMessage());
					Thread.sleep(DEFAULT_INTERVAL);
					//System.out.println("Retrying");
				} catch (InterruptedException ie) {
				}
			}
		} while (retry);
		if (e != null) {
			//System.out.println("FAIL");
			throw ExceptionUtil.exception(e);
		}
		//System.out.println("SUCCESS");
	}

	public abstract void run() throws Exception;

}
