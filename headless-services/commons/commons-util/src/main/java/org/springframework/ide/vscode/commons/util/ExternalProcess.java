/*******************************************************************************
 * Copyright (c) 2012, 2016 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Convenient wrapper around a {@link Process}. Simplifies the synchronous execution of external
 * commands by handling reading from out and error streams and either buffering the result output 
 * for later retrieval, or forwarding the to output to designated streams. 
 * 
 * @author Kris De Volder
 */
public class ExternalProcess {
	
	/**
	 * A thread that keeps reading input from a Stream until the end is reached
	 * or there's some error reading the Stream.
	 */
	public static class StreamGobler extends Thread {
		
		private final OutputStream echo;
		private InputStream toRead; //Stream to read. This is nulled after all input has been consumed.
		
		/**
		 * Creates a StreamGobler that reads input from an input stream
		 * and buffers up all input it has read for later retrieval via
		 * the getOut() method.
		 */
		public StreamGobler(InputStream toRead) {
			this(toRead, new ByteArrayOutputStream());
		}

		/**
		 * Creates a StreamGobler that reads input from an input stream
		 * and writes it out to an outputstream.
		 */
		public StreamGobler(InputStream toRead, OutputStream forwardTo) {
			this.toRead = toRead;
			this.echo = forwardTo;
			start();
		}

		@Override
		public void run() {
			byte[] buf = new byte[256];
			while (toRead != null) {
				try {
					int i = toRead.read(buf);
					if (i == -1) {
						//EOF
						toRead = null; //Done!
					} else {
						append(buf, i);
					}
				} catch (IOException e) {
					toRead = null;
					ByteArrayOutputStream errMsg = new ByteArrayOutputStream();
					e.printStackTrace(new PrintStream(errMsg));
					append(errMsg.toByteArray());
				}
			}
		}
		
		private void append(byte[] buf) {
			append(buf, buf.length);
		}

		private void append(byte[] buf, int len) {
			if (echo != null) {
				try {
					echo.write(buf, 0, len);
				} catch (IOException e) {
				}
			}
		}

		public String getContents() throws InterruptedException {
			try {
				this.join();
				if (echo instanceof ByteArrayOutputStream) {
					return ((ByteArrayOutputStream)echo).toString();
				} else {
					return null;
				}
			} finally {
				toRead = null;
			}
		}
	}

	private Process process;
	private StreamGobler err; // Standard error is to be read from here
	private StreamGobler out; // Standard out is to be read from here
	private int exitValue = -9999;
	private ExternalCommand cmd;
	
	/**
	 * Creates an external process and waits for it to terminate. The output and error streams
	 * will be read and forwarded to System.out and System.err	 
	 */
	public ExternalProcess(File workingDir, ExternalCommand cmd) throws IOException, InterruptedException, TimeoutException {
		this(workingDir, cmd, false, null);
	}

	private void init(File workingDir, ExternalCommand cmd,
			OutputStream outStream, OutputStream errStream, 
			Duration timeout) throws IOException,
			InterruptedException, TimeoutException {
		this.cmd = cmd; 
		ProcessBuilder processBuilder = new ProcessBuilder(cmd.getProgramAndArgs());
		processBuilder.directory(workingDir);
		cmd.configure(processBuilder);
		process = processBuilder.start();
		err = new StreamGobler(process.getErrorStream(), errStream);
		out = new StreamGobler(process.getInputStream(), outStream);
		if (timeout == null) {
			exitValue = process.waitFor();
		} else {
			if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				exitValue = process.exitValue();
			} else {
				process.destroy();
				exitValue = 999; //Set some non-0 value as that is what some callers might use to determine 'failure' occurred. 
				throw new TimeoutException("Command timed out: "+this);
			}
		}
		if (exitValue != 0) {
			throw new IOException("Command execution failed:\n"+this);
		}
	}

	public ExternalProcess(File workingDir, ExternalCommand cmd, boolean captureStreams, Duration timeout) throws IOException, InterruptedException, TimeoutException {
		if (captureStreams) {
			init(workingDir, cmd, new ByteArrayOutputStream(), new ByteArrayOutputStream(), timeout);
		} else {
			init(workingDir, cmd, System.err, System.err, timeout);
		}
		
	}

	public ExternalProcess(File workingDir, ExternalCommand cmd, boolean captureStreams) throws IOException, InterruptedException, TimeoutException {
		this(workingDir, cmd, captureStreams, null);
	}

	public String getOut() throws InterruptedException {
		return out.getContents();
	}
	
	public String getErr() throws InterruptedException {
		return err.getContents();
	}
	
	@Override
	public String toString() {
		StringBuffer result = new StringBuffer();
		try  {
			process.exitValue();
			result.append(">>>> ExternalProcess: ");
			result.append(cmd+"\n");
			result.append("exitValue = "+exitValue+"\n");
			String strOut = getOut();
			if (strOut != null) {
				result.append("------- System-out -------\n");
				result.append(strOut);
			}
			String strErr = getErr();
			if (strErr != null) {
				result.append("------- System-err -------\n");
				result.append(strErr);
			}
			result.append("<<<< ExternalProcess");
			return result.toString();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return result.toString();
		} catch (IllegalThreadStateException e) {
			return "ExternalProcess(RUNNING, "+cmd+")";
		}
	}

	public int getExitValue() {
		return exitValue;
	}
}
