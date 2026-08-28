/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.util;

import java.io.IOException;
import java.io.InputStream;

public class IOUtil {

	private static final int BUF_SIZE = 1024;

	/**
	 * Consume all data in an input stream, discdaring the data and closing the stream upon completion.
	 */
	public static void consume(InputStream stream) throws IOException {
		try {
			byte[] buf = new byte[BUF_SIZE];
			while (stream.read(buf) >= 0) {}
		} finally {
			try {
				stream.close();
			} catch (IOException e) {
				//ignore (or it may mask a more important / interesting error in the body of the try
			}
		}
	}

}
