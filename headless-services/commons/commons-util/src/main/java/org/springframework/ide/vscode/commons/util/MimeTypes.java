/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.MediaType;

public class MimeTypes {
	
	private static final Logger log = LoggerFactory.getLogger(MimeTypes.class);

	public static String[] getKnownMimeTypes() {
		try {
			Field f = MediaType.class.getDeclaredField("knownTypes");
			f.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<MediaType, MediaType> map = (Map<MediaType, MediaType>) f.get(null);
			TreeSet<String> mediaTypes = new TreeSet<>();
			for (MediaType m : map.keySet()) {
				mediaTypes.add(m.toString());
			}
			return mediaTypes.toArray(new String[mediaTypes.size()]);
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

}
