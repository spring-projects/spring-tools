/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;
import org.osgi.framework.Bundle;
import org.springframework.tooling.ls.eclipse.commons.console.ConsoleUtil.Console;
import org.springframework.tooling.ls.eclipse.commons.console.LanguageServerConsoles;
import org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ServerInfo;
import org.springframework.tooling.ls.eclipse.commons.preferences.LsPreferencesUtil;
import org.springsource.ide.eclipse.commons.core.util.IOUtil;

import com.google.common.base.Charsets;
import com.google.common.base.Supplier;

public abstract class STS4LanguageServerProcessStreamConnector extends ProcessStreamConnectionProvider {

	private static LanguageServerProcessReaper processReaper = new LanguageServerProcessReaper();

	private static final String LOG_RESOLVE_VM_ARG_PREFIX = "-Xlog:jni+resolve=";

	private Supplier<Console> consoles = null;
	private String connectorId;

	public STS4LanguageServerProcessStreamConnector(ServerInfo server) {
		this.connectorId = server.bundleId;
		this.consoles = LanguageServerConsoles.getConsoleFactory(server);
	}

	@Override
	public void start() throws IOException {
		super.start();

		Process process = LanguageServerProcessReaper.getProcess(this);
		processReaper.addProcess(connectorId, process);

		if (consoles!=null) {
			Console console = consoles.get();
			if (console!=null) {
				forwardTo(getLanguageServerLog(), console.out);
			} else {
				new Thread("Consume LS error stream") {
					@Override
					public void run() {
						try {
							IOUtil.consume(getLanguageServerLog());
						} catch (IOException e) {
							// ignore
						}
					}
				}.start();
			}
		}
	}

	protected JRE getJRE() {
		return JRE.currentJRE();
	}

	protected final void initExecutableJarCommand(Path lsDir, String jarPrefix, List<String> extraVmArgs) {
		try {
			Bundle bundle = Platform.getBundle(getPluginId());
			JRE runtime = getJRE();

			Assert.isNotNull(lsDir);

			File bundleFile = FileLocator.getBundleFileLocation(bundle).orElse(null);
			File bundleRoot = bundleFile.getAbsoluteFile();
			Path languageServerRoot = bundleRoot.toPath().resolve(lsDir);

			List<Path> jarFiles = Files.list(languageServerRoot).filter(Files::isRegularFile).filter(f -> {
				String fileName = f.toFile().getName();
				return fileName.endsWith(".jar") && fileName.startsWith(jarPrefix);
			}).limit(2).collect(Collectors.toList());

			if (jarFiles.size() > 1) {
				throw new IllegalStateException("More than one LS JAR files found: '%s' and '%s'".formatted(jarFiles.get(0), jarFiles.get(1)));
			} else if (jarFiles.isEmpty()) {
				throw new IllegalStateException("No LS JAR files found!");
			}

			Path jarFile = jarFiles.get(0).normalize();

			List<String> command = new ArrayList<>();

			command.add(runtime.getJavaExecutable());

			fillCommand(command, extraVmArgs);

			command.add("-jar");

			command.add(languageServerRoot.resolve(jarFile).toFile().toString());

			setCommands(command);
		} catch (Throwable t) {
			LanguageServerCommonsActivator.logError(t, "Failed to assemble executable LS JAR launch command");
		}

	}

	private void fillCommand(List<String> command, List<String> extraVmArgs) {
		command.add("-Dsts.lsp.client=eclipse");

		command.addAll(extraVmArgs);

		if (!hasVmArgStartingWith(command, LOG_RESOLVE_VM_ARG_PREFIX)) {
			command.add(LOG_RESOLVE_VM_ARG_PREFIX + "off");
		}

		LsPreferencesUtil.getServerInfo(getPluginId()).ifPresent(info -> {
			IPreferenceStore preferenceStore = LanguageServerCommonsActivator.getInstance().getPreferenceStore();
			if (!preferenceStore.getBoolean(info.preferenceKeyConsoleLog)) {
				String pathStr = preferenceStore.getString(info.preferenceKeyFileLog);
				if (pathStr != null && !pathStr.isBlank()) {
					command.add("-Dsts.log.file=" + pathStr);
				}
			}
			command.add("-XX:ErrorFile=" + Platform.getStateLocation(Platform.getBundle(getPluginId())).append("fatal-error-" + info.label.replaceAll("\\s+", "-").toLowerCase() + "_" + System.currentTimeMillis()));
		});

		command.add("-Dlanguageserver.hover-timeout=225");

	}

	protected final void initExplodedJarCommand(Path lsFolder, String mainClass, String configFileName, List<String> extraVmArgs) {
		try {
			Bundle bundle = Platform.getBundle(getPluginId());
			JRE runtime = getJRE();

			Assert.isNotNull(lsFolder);
			Assert.isNotNull(mainClass);

			List<String> command = new ArrayList<>();

			command.add(runtime.getJavaExecutable());
			command.add("-cp");

			File bundleFile = FileLocator.getBundleFileLocation(bundle).orElse(null);

			File bundleRoot = bundleFile.getAbsoluteFile();
			Path languageServerRoot = bundleRoot.toPath().resolve(lsFolder);

			StringBuilder classpath = new StringBuilder();
			classpath.append(languageServerRoot.resolve("BOOT-INF/classes").toFile());
			classpath.append(File.pathSeparator);
			classpath.append(languageServerRoot.resolve("BOOT-INF/lib").toFile());
			// Cannot have * in the java.nio.Path on Windows
			classpath.append(File.separator);
			classpath.append('*');

			if (runtime.toolsJar != null) {
				classpath.append(File.pathSeparator);
				classpath.append(runtime.toolsJar);
			}

			command.add(classpath.toString());

			if (configFileName != null) {
				command.add("-Dspring.config.location=file:" + languageServerRoot.resolve("BOOT-INF/classes").resolve(configFileName).toFile());
			}

			fillCommand(command, extraVmArgs);

			command.add(mainClass);

			setCommands(command);
		}
		catch (Throwable t) {
			LanguageServerCommonsActivator.logError(t, "Failed to assemble exploded LS JAR launch command");
		}
	}

	protected static boolean hasVmArgStartingWith(List<String> vmargs, String prefix) {
		for (String vmarg : vmargs) {
			if (vmarg.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected ProcessBuilder createProcessBuilder() {
		if (consoles==null) {
			return super.createProcessBuilder();
		}
		ProcessBuilder builder = new ProcessBuilder(getCommands());
		builder.directory(new File(getWorkingDirectory()));
		//Super does this, but we do not:
		//builder.redirectError(ProcessBuilder.Redirect.INHERIT);
		return builder;
	}

	private void forwardTo(InputStream is, OutputStream os) {
		Job consoleJob = new Job("Forward Language Server log output to console") {
			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {
					pipe(is, os);
				} catch (IOException e) {
				}
				finally {
					try {
						os.write("==== Process Terminated====\n".getBytes(Charsets.UTF_8));
						os.close();
					} catch (IOException e) {
					}
				}
				return Status.OK_STATUS;
			}

			void pipe(InputStream input, OutputStream output) throws IOException {
				try {
				    byte[] buf = new byte[1024*4];
				    int n = input.read(buf);
				    while (n >= 0) {
				      output.write(buf, 0, n);
				      n = input.read(buf);
				    }
				    output.flush();
				} finally {
					input.close();
				}
			}

		};
		consoleJob.setSystem(true);
		consoleJob.schedule();
	}

	private InputStream getLanguageServerLog() {
		return super.getErrorStream();
	}

	@Override
	public void stop() {
		super.stop();
		Process process = LanguageServerProcessReaper.getProcess(this);
		processReaper.removeProcess(process);
	}

	protected String getWorkingDirLocation() {
		return System.getProperty("user.dir");
	}

	protected abstract String getPluginId();

}
