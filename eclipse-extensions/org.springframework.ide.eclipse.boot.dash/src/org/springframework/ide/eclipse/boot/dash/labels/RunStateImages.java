/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.labels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.model.RunState;

/**
 * @author Kris De Volder
 */
public class RunStateImages {

	private Map<Object, Image[]> animations = new HashMap<>();

	public synchronized Image[] getAnimation(RunState state) throws Exception {
		Image[] anim = animations.get(state);
		if (anim == null) {
			String url = state.getImageUrl();
			animations.put(state, anim = createAnimation(url));
		}
		return anim;
	}

	private Image[] createAnimation(String urlString) throws Exception {
		// For a png there might be animation frames to load (ImageLoader cannot
		// pull out frames for an animated png)
		// Given an input url of the form "foo.png" this will search
		// for "foo_1.png", "foo_2.png" etc, until it can find no more frames
		int dot = urlString.lastIndexOf('.');
		String prefix = urlString.substring(0, dot);
		String suffix = urlString.substring(dot+1);

		List<Image> images = new ArrayList<>();
		int count = 1;
		ImageDescriptor descriptor = null;
		while ((descriptor = BootDashActivator.getImageDescriptor(prefix+"_"+Integer.toString(count++)+"."+suffix)) != null) {
			images.add(descriptor.createImage());
		}

		if (images.size() != 0) {
			// Animation frames were found, return them
			return images.toArray(new Image[images.size()]);
		}
		else {
			ImageDescriptor imageDescriptor = BootDashActivator.getImageDescriptor(urlString);
			Image image;
			if (imageDescriptor != null) {
				image = imageDescriptor.createImage();
			} else {
				image = BootDashActivator.getImageDescriptor("/icons/rs_unknown.gif").createImage();
			}
			return new Image[] {image};
		}


//		input = cl.getResourceAsStream(urlString);
//		try {
//			ImageData[] data = loader.load(input);
//			Image[] imgs = new Image[data.length];
//			for (int i = 0; i < imgs.length; i++) {
//				imgs[i] = new Image(Display.getDefault(), data[i]);
//			}
//			return imgs;
//		} finally {
//			input.close();
//		}
	}

	public void dispose() {
		if (animations != null) {
			for (Image[] anim : animations.values()) {
				if (anim != null) {
					for (Image image : anim) {
						image.dispose();
					}
				}
			}
			animations = null;
		}
	}

}
