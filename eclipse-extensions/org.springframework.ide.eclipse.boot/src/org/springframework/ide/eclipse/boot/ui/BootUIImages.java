/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.ui;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.springframework.ide.eclipse.boot.core.BootActivator;

/**
 * @author Kris De Volder
 */
public class BootUIImages {

	public static final String BOOT_ICON = "icons/boot-icon.png";
	public static final String BOOT_DEVTOOLS_ICON = "icons/boot-devtools-icon.png";

	private static ImageRegistry images;

	public static Image getImage(String key) {
		return getRegistry().get(key);
	}

	private static synchronized ImageRegistry getRegistry() {
		if (images == null) {
			images = new ImageRegistry();
			register(BOOT_ICON);
			register(BOOT_DEVTOOLS_ICON);
		}
		return images;
	}

	private static void register(String key) {
		try {
			images.put(key, descriptor(key));
		} catch (Exception e) {
			BootActivator.log(e);
		}
	}

	public static ImageDescriptor descriptor(String key) throws MalformedURLException {
		return ImageDescriptor.createFromURL(url(key));
	}

	private static URL url(String key) throws MalformedURLException {
		return new URL("platform:/plugin/"+BootActivator.PLUGIN_ID+"/resources/"+key);
	}

}
