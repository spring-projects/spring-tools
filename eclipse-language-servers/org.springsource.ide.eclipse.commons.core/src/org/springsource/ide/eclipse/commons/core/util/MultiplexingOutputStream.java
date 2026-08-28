/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
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
import java.io.OutputStream;

/**
 * A wrapper around multiple output streams that mulitplexes any output written to this Stream to all
 * the wrapped Streams.
 * @author Kris De Volder
 */
public class MultiplexingOutputStream extends OutputStream {
	
	OutputStream[] streams = null;
	
	public MultiplexingOutputStream(OutputStream... streams) {
		super();
		if (streams == null) {
			this.streams = new OutputStream[0];
		} else {
			this.streams = streams;
		}
	}

	/**
	 * Writes the byte b to each wrapped OutputStream. 
	 * <p>
	 * If one of the Streams throws an Exception this exception is
	 * propagated. No guarantees are made in this case that output
	 * is written to the other Streams.
	 */
	@Override
	public void write(int b) throws IOException {
		for (OutputStream s : streams) {
			s.write(b);
		}
	}
	
	/**
	 * Writes to each wrapped OutputStream. 
	 * <p>
	 * If one of the Streams throws an Exception this exception is
	 * propagated. No guarantees are made in this case that output
	 * is written to the other Streams.
	 */
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		for (OutputStream s : streams) {
			s.write(b, off, len);
		}
	}

	/**
	 * Attempt to close all of the underlying Streams. If any of the Streams
	 * throws an exception it is guaranteed that at least one of those
	 * exceptions will be propagated.
	 * <p>
	 * Even if one of the streams throws an Exception, we will still
	 * attempt to close the other Streams.
	 */
	@Override
	public void close() throws IOException {
		super.close();
		Throwable caught = null;
		for (OutputStream s : streams) {
			try {
				s.close();
			} catch (Throwable e) {
				caught = e;
			}
		}
		if (caught != null) {
			if (caught instanceof IOException) {
				throw (IOException)caught;
			} else {
				// If not a Declared exception it must be an unchecked exception.
				throw (Error)caught;
			}
		}
	}
	
	@Override
	public void flush() throws IOException {
		for (OutputStream s : streams) {
			s.flush();
		}
	}

	
}
