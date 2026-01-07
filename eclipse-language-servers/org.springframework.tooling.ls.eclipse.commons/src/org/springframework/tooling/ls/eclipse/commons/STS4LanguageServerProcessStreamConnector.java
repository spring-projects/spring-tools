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
import java.nio.charset.StandardCharsets;
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

public abstract class STS4LanguageServerProcessStreamConnector extends ProcessStreamConnectionProvider {

	private static LanguageServerProcessReaper processReaper = new LanguageServerProcessReaper();

	private static final String LOG_RESOLVE_VM_ARG_PREFIX = "-Xlog:jni+resolve=";

	private final ServerInfo serverInfo;

	public STS4LanguageServerProcessStreamConnector(ServerInfo serverInfo) {
		this.serverInfo = serverInfo;
	}

	@Override
	public void start() throws IOException {
		super.start();

		Process process = LanguageServerProcessReaper.getProcess(this);
		processReaper.addProcess(serverInfo.bundleId(), process);

		streamLogging();
	}

	private void streamLogging() {
		switch (getLoggingTarget()) {
		case CONSOLE:
			Console console = LanguageServerConsoles.getConsoleFactory(serverInfo.label()).get();
			if (console != null) {
				forwardTo(getLanguageServerLog(), console.out);
				return;
			}
			// fall through to NONE case if there is no console created to simply consume the error log.
		case OFF:
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
			break;
		case LSP4E:
			// Otherwise logging to ERROR LOG nothing to do see `getErrorStream()`
			break;
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
			String pathStr = preferenceStore.getString(info.preferenceKeyFileLog());
  			if (pathStr != null && !pathStr.isBlank()) {
				command.add("-Dlogging.file.name=" + pathStr);
			}
			command.add("-Dlogging.level.root=" + preferenceStore.getString(info.preferenceKeyLogLevel()));
			if (getLoggingTarget() == LoggingTarget.CONSOLE) {
				command.add("-Dspring.output.ansi.enabled=ALWAYS");
			}
			command.add("-XX:ErrorFile=" + Platform.getStateLocation(Platform.getBundle(getPluginId())).append("fatal-error-" + info.label().replaceAll("\\s+", "-").toLowerCase() + "_" + System.currentTimeMillis()));
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
					is.transferTo(os);
				} catch (IOException e) {
				}
				finally {
					try {
						os.write("==== Process Terminated====\n".getBytes(StandardCharsets.UTF_8));
						os.close();
					} catch (IOException e) {
					}
				}
				return Status.OK_STATUS;
			}

		};
		consoleJob.setSystem(true);
		consoleJob.schedule();
	}

	@Override
	public InputStream getErrorStream() {
		return getLoggingTarget() == LoggingTarget.LSP4E ? getLanguageServerLog() : null;
	}

	private InputStream getLanguageServerLog() {
		return super.getErrorStream();
	}

	private LoggingTarget getLoggingTarget() {
		LanguageServerCommonsActivator plugin = LanguageServerCommonsActivator.getInstance();
		try {
			return LoggingTarget.valueOf(plugin.getPreferenceStore().getString(serverInfo.prefernceKeyLogTarget()));
		} catch (Exception e) {
			plugin.getLog().error("Cannot determine language server logging target", e);
			return LoggingTarget.OFF;
		}
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
