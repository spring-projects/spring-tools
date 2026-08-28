/*******************************************************************************
 * Copyright (c) 2016, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UriUtil {
	
	private static final Logger log = LoggerFactory.getLogger(UriUtil.class);


	public static URI toUri(File file) {
		try {
			return new URI("file", "", file.getAbsoluteFile().toURI().getPath(), null);
		} catch (URISyntaxException e) {
			log.error("", e);
			return file.getAbsoluteFile().toURI();
		}
	}

	public static String normalize(String uriVal) {
		try {
			if (uriVal != null && uriVal.startsWith("file:")) {
				File file = new File(URI.create(uriVal)).getCanonicalFile();
				uriVal = file.toURI().toASCIIString();
				//Careful!!! If the project uri points to a existing project... then it will be
				//a directory and then the uri we computed will get a slash at the end.
				//If, on the other hand, it doesn't exist because it got deleted. Then it will not get a slash 
				//(because something that doesn't exist isn't considered to be a directory).
				//We really don't want the uri to change after it got deleted. 
				//So... make sure we never have the slash:
				while (uriVal.endsWith("/")) {
					uriVal = uriVal.substring(0, uriVal.length()-1);
				}
				//Careful on windows, drive letters
				return uriVal;
			}
		} catch (Exception e) {

		}
		return uriVal;
	}

	/**
	 * Caution, the implementation assumes that it is only called with
	 * normalized uris!
	 */
	public static boolean contains(String projectUri, String uri) {
		if (projectUri.length() < uri.length()) {
			return uri.startsWith(projectUri) && (
					projectUri.charAt(projectUri.length()-1) == '/' ||
					uri.charAt(projectUri.length()) == '/'
			);
		} else if (projectUri.length() > uri.length()) {
			return false;
		} else {
			return projectUri.equals(uri);
		}
	}

	public static File toFile(String docURI) {
		try {
			return new File(new URI(docURI));
		} catch (Exception e) {
			return null;
		}
	}

	public static String toFileString(String docURI) {
		try {
			return new File(new URI(docURI)).getAbsolutePath();
		} catch (Exception e) {
			return null;
		}
	}

}
