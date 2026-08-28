package org.springframework.ide.eclipse.boot.dash.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;

import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class LineBasedStreamGobler extends Thread {

	private final Consumer<String> echo;
	private BufferedReader toRead; //Stream to read. This is nulled after all input has been consumed.

	/**
	 * Creates a LineBasedStreamGobler that reads input from an input stream
	 * and writes it out to an outputstream.
	 */
	public LineBasedStreamGobler(InputStream toRead, Consumer<String> lineConsumer) {
		this.toRead = new BufferedReader(new InputStreamReader(toRead));
		this.echo = lineConsumer;
		start();
	}

	@Override
	public void run() {
		while (toRead != null) {
			try {
				String line = toRead.readLine();
				if (line == null) {
					toRead = null;
				} else {
					echo.accept(line);
				}
			} catch (IOException e) {
				toRead = null;
				Log.log(e);
			}
		}
	}
}
