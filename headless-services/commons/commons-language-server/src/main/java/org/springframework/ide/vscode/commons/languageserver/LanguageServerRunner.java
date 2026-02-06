/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.ide.vscode.commons.languageserver.config.LanguageServerProperties;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A CommandLineRunner that launches a language server. This meant to be used as a Spring bean
 * in a SpringBoot app.
 *
 * @author Kris De Volder
 * @author Martin Lippert
 */
public class LanguageServerRunner implements CommandLineRunner {

	final static Logger log = LoggerFactory.getLogger(LanguageServerRunner.class);
	
	final public static Function<MessageConsumer, MessageConsumer> DEFAULT_MESSAGE_CONSUMER = (MessageConsumer consumer) -> {
		return (msg) -> {
			try {
//				long beforeConsumingMessage = System.currentTimeMillis();
				
				consumer.consume(msg);
				
//				long afterConsumingMessage = System.currentTimeMillis();
//				String shortMessage = StringUtils.left(msg.toString(), 140);
//				log.info("working on message took " + (afterConsumingMessage - beforeConsumingMessage) + "ms - message content: " + shortMessage);

			} catch (UnsupportedOperationException e) {
				//log a warning and ignore. We are getting some messages from vsCode the server doesn't know about
				log.warn("Unsupported message was ignored!", e);
			}
		};
	};


	@Override
	public void run(String... args) throws Exception {
		log.info("java.home = {}", System.getProperty("java.home"));
		//TODO: feels a bit wasteful to have thread dedicated to just waiting for the server to stop.
		//  Not sure how we can really avoid this though. Lsp4j is providing
		//  lots of api that returns Futures which the only way to deal with them is blocking threads calling
		//  their get method.
		new Thread(
			() -> {
				try {
					start();
				} catch (Exception e) {
					log.error("", e);
				}
			},
			"LanguageServerApp-lifecycle"
		).start();
	}

	/**
	 * System property that is set when the app launches. This makes it easy for the JVM process to recognized
	 * as a languageserver by using (for example) JMX to read the system properties.
	 */
	public static final String SYSPROP_LANGUAGESERVER_NAME = "sts4.languageserver.name";

	private LanguageServerProperties properties;
	private final SimpleLanguageServer languageServer;

	private Function<MessageConsumer, MessageConsumer> messageConsumer;

	private Optional<Consumer<GsonBuilder>> configureGsonOpt;
	
	Launcher<STS4LanguageClient> launcher;

	public LanguageServerRunner(LanguageServerProperties properties, SimpleLanguageServer languageServer, Function<MessageConsumer, MessageConsumer> messageConsumer, Optional<Consumer<GsonBuilder>> configureGsonOpt) {
		super();
		this.properties = properties;
		this.languageServer = languageServer;
		this.messageConsumer = messageConsumer;
		this.configureGsonOpt = configureGsonOpt;
	}

	public void start() throws Exception {
		LanguageServerRunner app = this;
		log.info("Server ready to start after {} ms", ManagementFactory.getRuntimeMXBean().getUptime());
		if (properties.isStandalone()) {
			app.startAsServer();
		} else {
			app.startAsClient();
		}
	}

	private static class Connection {
		final InputStream in;
		final OutputStream out;
		final Socket socket;

		private Connection(InputStream in, OutputStream out, Socket socket) {
			this.in = in;
			this.out = out;
			this.socket = socket;
		}

		void dispose() {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					log.error("", e);
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					log.error("", e);
				}
			}
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					log.error("", e);
				}
			}
		}
	}

	private void startAsClient() throws IOException {
		log.info("Starting LS as client");
		Connection connection = null;
		try {
			connection = connectToNode();
			runAsync(connection).get();
		} catch (Throwable t) {
			log.error("", t);
			System.exit(1);
		} finally {
			if (connection != null) {
				connection.dispose();
			}
		}
	}

	/**
	 * starts up the language server and let it listen for connections from the outside
	 * instead of connecting itself to an existing port or channel.
	 *
	 * This is meant for development only, to reduce turnaround times while working
	 * on the language server from within an IDE, so that you can start the language
	 * server right away in debug mode and let the vscode extension connect to that
	 * instance instead of vice versa.
	 *
	 * Source of inspiration:
	 * https://github.com/itemis/xtext-languageserver-example/blob/master/org.xtext.example.mydsl.ide/src/org/xtext/example/mydsl/ide/RunServer.java
	 */
	public void startAsServer() throws Exception {
		int serverPort = properties.getStandalonePort();
		log.info("Starting LS as standlone server port = {}", serverPort);

		launcher = createSocketLauncher(languageServer, STS4LanguageClient.class,
				new InetSocketAddress("localhost", serverPort), createServerThreads(), messageConsumer);

		languageServer.connect(launcher.getRemoteProxy());
		launcher.startListening().get();
	}

	/**
	 * Creates the thread pool / executor passed to lsp4j server intialization. From the looks of things,
	 * @return
	 */
	protected ExecutorService createServerThreads() {
		return Executors.newCachedThreadPool();
	}

	private <T> Launcher<T> createSocketLauncher(
			Object localService, Class<T> remoteInterface,
			SocketAddress socketAddress, ExecutorService executorService,
			Function<MessageConsumer, MessageConsumer> wrapper
	) throws Exception {
		AsynchronousServerSocketChannel serverSocket = AsynchronousServerSocketChannel.open().bind(socketAddress);
		AsynchronousSocketChannel socketChannel = serverSocket.accept().get();
		log.info("Client connected via socket");
		return Launcher.createIoLauncher(localService, remoteInterface, Channels.newInputStream(socketChannel),
				Channels.newOutputStream(socketChannel), executorService, wrapper, configureGsonOpt.orElse(null));
	}

	private static Connection connectToNode() throws IOException {
//		String port = System.getProperty("server.port");
//
//		if (port != null) {
//			Socket socket = new Socket("localhost", Integer.parseInt(port));
//
//			InputStream in = socket.getInputStream();
//			OutputStream out = socket.getOutputStream();
//
//			log.info("Connected to parent using socket on port {}", port);
//			return new Connection(in, out, socket);
//		}
//		else {
			InputStream in = System.in;
			PrintStream out = System.out;

			log.info("Connected to parent using stdio");

			return new Connection(in, out, null);
//		}
	}

	private Future<Void> runAsync(Connection connection) throws Exception {
		LanguageServer server = this.languageServer;
		ExecutorService executor = createServerThreads();
		launcher = Launcher.createIoLauncher(server,
				STS4LanguageClient.class,
				connection.in,
				connection.out,
				executor,
				messageConsumer,
				configureGsonOpt.orElse(null)
		);

		if (server instanceof LanguageClientAware) {
			LanguageClient client = launcher.getRemoteProxy();
			((LanguageClientAware) server).connect(client);
		}

		return launcher.startListening();
	}

	public Gson getGson() {
		// Should only return successfully if connected to client
		return launcher.getRemoteEndpoint().getJsonHandler().getGson();
	}
}
