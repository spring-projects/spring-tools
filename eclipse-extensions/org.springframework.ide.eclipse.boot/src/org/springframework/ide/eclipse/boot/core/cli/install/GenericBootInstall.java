/*******************************************************************************
 *  Copyright (c) 2013 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.cli.install;

import java.io.File;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.springframework.ide.eclipse.boot.core.BootActivator;

/**
 * A 'Generic' representation of a boot install. Can be used to represent a
 * Boot install for which BootInstallFactory can not determine the appropriate
 * type. This may be because the install is in fact not valid.
 * <p>
 * We still have to be able to represent it because users may enter invalid data
 * in the UI. Or installs that where valid before might have gotten deleted
 * by a user.
 */
public class GenericBootInstall extends BootInstall {

	private String errorMessage;

	public GenericBootInstall(String urlString, String name, String errorMessage) {
		super(urlString, name);
		this.errorMessage = errorMessage;
	}

	@Override
	public File getHome() throws Exception {
		return null;
	}

	@Override
	public IStatus validate() {
		if (errorMessage == null) {
			return Status.OK_STATUS;
		} else {
			return new Status(IStatus.ERROR, BootActivator.PLUGIN_ID, errorMessage);
		}

	}

	@Override
	public void clearCache() {
		//Nothing to do since this install is something likely bogus it does not
		// exist or at least it can't be determined to be real install
	}

}
