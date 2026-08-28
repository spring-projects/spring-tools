/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.Bundle;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class M2ELogbackCustomizer extends Job {

	/**
	 * Snippet to add to the logback.xml file.
	 */
	private static String SNIPPET = "<logger name=\"net.schmizz\" level=\"OFF\" />";

	public M2ELogbackCustomizer() {
		super("M2ELogbackCustomizer");
		setSystem(true);
	}

	int retries = 120;

	private boolean isStateLocationInitialized() {
		if (!Platform.isRunning()) {
			return false;
		}

		Bundle resourcesBundle = Platform.getBundle("org.eclipse.core.resources");
		if (resourcesBundle == null) {
			return false;
		}

		return resourcesBundle.getState() == Bundle.ACTIVE;
	}

	@Override
	protected IStatus run(IProgressMonitor arg0) {
//		Log.info("M2ELogbackCustomizer starting...");
		try {
			if (!isStateLocationInitialized()) {
//				Log.info("M2ELogbackCustomizer state location not initalized...");
			} else {
				Bundle logbackConfigBundle = Platform.getBundle("org.eclipse.m2e.logback.configuration");
//				Log.info("M2ELogbackCustomizer logbackConfigBundle = "+logbackConfigBundle);
				String version = logbackConfigBundle.getVersion().toString();
//				Log.info("M2ELogbackCustomizer version = "+version);
				IPath statelocationPath = Platform.getStateLocation(logbackConfigBundle);
//				Log.info("M2ELogbackCustomizer statelocationPath = "+statelocationPath);

				if (statelocationPath != null) {
					File stateDir = statelocationPath.toFile();
					File logbackFile = new File(stateDir, "logback."+version+".xml");
//					Log.info("M2ELogbackCustomizer logbackFile = "+logbackFile);
					if (!logbackFile.isFile()) {
//						Log.info("M2ELogbackCustomizer logbackFile is not a file");
					} else {
						String logbackConf = IOUtil.toString(new FileInputStream(logbackFile));
						int insertionPoint = logbackConf.indexOf("</configuration>");
//						Log.info("M2ELogbackCustomizer inseertionPoint = "+insertionPoint);
						if (insertionPoint >= 0) {
							if (logbackConf.contains(SNIPPET)) {
								//nothing to do
//								Log.info("M2ELogbackCustomizer snippet already present, DONE");
								return Status.OK_STATUS;
							} else {
//								Log.info("M2ELogbackCustomizer inserting snippet");
								logbackConf = logbackConf.substring(0, insertionPoint)
										+SNIPPET+"\n" + logbackConf.substring(insertionPoint);
								IOUtil.pipe(new ByteArrayInputStream(logbackConf.getBytes("UTF8")), logbackFile);
							}
						}
					}
				}
			}
		}catch (Exception e) {
//			Log.warn(e);
			//ignore
		}
		retry();
		return Status.OK_STATUS;
	}

	private void retry() {
		if (retries-- > 0) {
//			Log.info("M2ELogbackCustomizer will try again later");
			this.schedule(1000);
		} else {
//			Log.info("M2ELogbackCustomizer no more retry attempts left");
		}
	}

}
