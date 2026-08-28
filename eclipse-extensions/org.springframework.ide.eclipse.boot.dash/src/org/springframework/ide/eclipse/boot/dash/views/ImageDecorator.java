/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.swt.graphics.Image;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.ui.ImageDescriptorRegistry;

/**
 * Responsible for creating optionally decorated images from
 * image descriptors.  Keeps track of the created images and
 * can dispose them when no longer used.
 *
 * @author Kris De Volder
 */
public class ImageDecorator implements Disposable {

	private static final boolean DEBUG = false; //(""+Platform.getLocation()).contains("kdvolder");

	/**
	 * Keeps track of undecorated images.
	 */
	private ImageDescriptorRegistry images;

	/**
	 * Keeps track of decorated images.
	 */
	private Map<Object, Image> decoratedImages;

	@Override
	public void dispose() {
		if (images != null) {
			images.dispose();
			images = null;
		}
		if (decoratedImages != null) {
			for (Image i : decoratedImages.values()) {
				i.dispose();
			}
			decoratedImages = null;
		}
	}

	public Image get(ImageDescriptor icon, ImageDescriptor decoration) {
		if (decoration == null) {
			return get(icon);
		} else {
			if (decoratedImages == null) {
				decoratedImages = new HashMap<>();
			}
			Object key = keyFor(icon, decoration);
			Image existing = decoratedImages.get(key);
			if (existing == null) {
				debug("Decorating: "+icon + " with "+decoration);
				Image baseImg = get(icon);
				DecorationOverlayIcon overlayer = new DecorationOverlayIcon(baseImg,
						decoration, IDecoration.BOTTOM_RIGHT);
				decoratedImages.put(key, existing = overlayer.createImage());
			}
			return existing;
		}
	}

	private Image get(Image baseImg, ImageDescriptor decoration) {
		if (decoration == null) {
			return baseImg;
		} else {
			if (decoratedImages == null) {
				decoratedImages = new HashMap<>();
			}
			Object key = keyFor(baseImg, decoration);
			Image existing = decoratedImages.get(key);
			if (existing == null) {
				debug("Decorating: "+baseImg + " with "+decoration);
				DecorationOverlayIcon overlayer = new DecorationOverlayIcon(baseImg,
						decoration, IDecoration.TOP_RIGHT);
				decoratedImages.put(key, existing = overlayer.createImage());
			}
			return existing;
		}
	}


	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	/**
	 * Get plain, undecorated image.
	 */
	private Image get(ImageDescriptor desc) {
		if (images == null) {
			images = new ImageDescriptorRegistry();
		}
		return images.get(desc);
	}

	private Object keyFor(Object icon, ImageDescriptor decoration) {
		return Arrays.asList(icon, decoration);
	}

	public Image[] decorateImages(Image[] anim, ImageDescriptor decoration) {
		Image[] decorated = new Image[anim.length];
		for (int i = 0; i < decorated.length; i++) {
			decorated[i] = get(anim[i], decoration);
		}
		return decorated;
	}

}
