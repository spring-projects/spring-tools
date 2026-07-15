/*******************************************************************************
 * Copyright (c) 2015, 2022 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import static org.eclipse.debug.core.DebugPlugin.ATTR_PROCESS_FACTORY_ID;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS;
import static org.springsource.ide.eclipse.commons.core.util.StringUtil.hasText;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.console.ConsolePlugin;
import org.osgi.framework.Bundle;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.launch.livebean.JmxBeanSupport;
import org.springframework.ide.eclipse.boot.launch.livebean.JmxBeanSupport.Feature;
import org.springframework.ide.eclipse.boot.launch.process.BootProcessFactory;
import org.springframework.ide.eclipse.boot.launch.profiles.ProfileHistory;
import org.springsource.ide.eclipse.commons.core.util.OsUtils;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * @author Kris De Volder
 */
public class BootLaunchConfigurationDelegate extends AbstractBootLaunchConfigurationDelegate {

	private static DeletedLaunchConfTerminator deletedLaunchConfTerminator = null;

	public synchronized static void ensureDeletedLaunchConfTerminator() {
		if (deletedLaunchConfTerminator==null) {
			deletedLaunchConfTerminator = new DeletedLaunchConfTerminator(DebugPlugin.getDefault().getLaunchManager(), (ILaunch l) -> {
				try {
					return l!=null && Boolean.valueOf(l.getAttribute(BOOT_LAUNCH_MARKER));
				} catch (Exception e) {
					Log.log(e);
					return false;
				}
			});
		}
	}



//	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");
//	private static void debug(String string) {
//		if (DEBUG) {
//			System.out.println(string);
//		}
//	}

	public static final String TYPE_ID = "org.springframework.ide.eclipse.boot.launch";

	/**
	 * Launch attribute that helps recognize a launch as a boot launch even after the launch configuration has
	 * been deleted.
	 */
	public static final String BOOT_LAUNCH_MARKER = "isBootLaunch";

	public static final String ENABLE_LIVE_BEAN_SUPPORT = "spring.boot.livebean.enable";
	public static final boolean DEFAULT_ENABLE_LIVE_BEAN_SUPPORT() {
		BootLaunchActivator ins = BootLaunchActivator.getInstance();
		return ins!=null && ins.isLiveBeanSupported();
	}

	public static final String ENABLE_JMX = "spring.boot.jmx.enable";
	public static final boolean DEFAULT_ENABLE_JMX = true;

	public static final String JMX_PORT = "spring.boot.livebean.port";

	public static final int DEFAULT_JMX_PORT = 0; //means pick it dynamically

	public static final String ANSI_CONSOLE_OUTPUT = "spring.boot.ansi.console";

	public static final String FAST_STARTUP = "spring.boot.fast.startup";

	private static final String PROFILE = "spring.boot.profile";
	public static final String DEFAULT_PROFILE = "";

	public static final String ENABLE_LIFE_CYCLE = "spring.boot.lifecycle.enable";
	public static final boolean DEFAULT_ENABLE_LIFE_CYCLE = true;

	public static final String HIDE_FROM_BOOT_DASH = "spring.boot.dash.hidden";
	public static final boolean DEFAULT_HIDE_FROM_BOOT_DASH = false;

	public static final String PROCESS_ID = "spring.boot.process.id";

	private static final String ENABLE_CHEAP_ENTROPY_VM_ARGS = "-Djava.security.egd=file:/dev/./urandom ";
	private static final String TERMINATION_TIMEOUT = "spring.boot.lifecycle.termination.timeout";
	public static final long DEFAULT_TERMINATION_TIMEOUT = 15000; // 15 seconds

	public static final String USE_THIN_WRAPPER = "spring.boot.thinwrapper.enable";
	public static final boolean DEFAULT_USE_THIN_WRAPPER = false;

	public static final String SPRING_PROJECT_NAME_ATTRIBUTE = "spring.boot.project.name";

	public static final String AUTO_CONNECT = "spring.boot.auto-connect";
	public static final boolean DEFAULT_AUTO_CONNECT = true;

	// Preference for Eclipse ANSI Console support.
	// See org.eclipse.ui.internal.console.ansi.preferences.AnsiConsolePreferenceConstants
	// Because this class is only available in Eclipse 4.25 and higher, we cannot add a
	// direct reference to this type for backward compatibility
	public static final String PREF_ANSI_CONSOLE_ENABLED = "ANSI_support_enabled";

	private ProfileHistory profileHistory = new ProfileHistory();

	/**
	 * Use threadlocal to gain access to current launch in some of the methods
	 * (i.e. getVMArguments in particular) of the {@link AbstractBootLaunchConfigurationDelegate}
	 * framework that, unfortunately don't pass it along as parameters. It's either this, or copy
	 * a whole bunch of inherited code just so we can modify it to add an extra argument.
	 */
	private static final ThreadLocal<ILaunch> CURRENT_LAUNCH = new ThreadLocal<>();

	@Override
	public void launch(ILaunchConfiguration conf, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		ensureDeletedLaunchConfTerminator();
		launch.setAttribute(BOOT_LAUNCH_MARKER, "true");
		CURRENT_LAUNCH.set(launch);
		try {
			profileHistory.updateHistory(getProject(conf), getProfile(conf));
			super.launch(conf, mode, launch, monitor);
		} finally {
			CURRENT_LAUNCH.remove();
		}
	}

	@Override
	public String getProgramArguments(ILaunchConfiguration conf) throws CoreException {
		if (useThinWrapper(conf)) {
			ArrayList<String> args = new ArrayList<>();
			String realMain = super.getMainTypeName(conf);
			//--thin.main=com.example.SampleApplication
			// --thin.archive=target/classes - the first one is the main class of the boot app, as already configured in the boot launch config, the second one points to the compiled classes (the entry that was on the regular classpath before without the maven dependencies)
			args.add("--thin.main="+realMain);
			args.add("--thin.archive="+getThinArchive(conf));
			args.addAll(Arrays.asList(DebugPlugin.parseArguments(super.getProgramArguments(conf))));
			return DebugPlugin.renderArguments(args.toArray(new String[args.size()]), null);
		}
		return super.getProgramArguments(conf);
	}

	private String getThinArchive(ILaunchConfiguration conf) throws CoreException {
		IRuntimeClasspathEntry[] entries = {
			JavaRuntime.newProjectRuntimeClasspathEntry(getJavaProject(conf))
		};
		IRuntimeClasspathEntry[] realClasspath = JavaRuntime.resolveRuntimeClasspath(entries, conf);
		StringBuilder classpathString = new StringBuilder();
		boolean first = true;
		for (IRuntimeClasspathEntry entry : realClasspath) {
			if (!first) {
				classpathString.append(File.pathSeparatorChar);
			}
			classpathString.append(entry.getLocation());
			first = false;
		}
		if (first) {
			throw new IllegalStateException("Could not determine the 'thin archive' location");
		}
		return classpathString.toString();
	}

	@Override
	public String getVMArguments(ILaunchConfiguration conf)
			throws CoreException {
		try {
			List<String> vmArgs = new ArrayList<>();
			vmArgs.addAll(Arrays.asList(DebugPlugin.parseArguments(super.getVMArguments(conf))));

			Properties props = getApplicationProperties(conf);
			String profile = getProfile(conf);
			boolean debugOutput = getEnableDebugOutput(conf);
			boolean enableAnsiConsole = supportsAnsiConsoleOutput() && getEnableAnsiConsoleOutput(conf);
			if (debugOutput) {
				vmArgs.add("-Ddebug");
			}
			if (hasText(profile)) {
				vmArgs.add(propertyAssignmentArgument("spring.profiles.active", profile, SYSTEM_PROP_PREFIX));
			}
			if (enableAnsiConsole) {
				vmArgs.add(propertyAssignmentArgument("spring.output.ansi.enabled", "always", SYSTEM_PROP_PREFIX));
			}
			addPropertiesArguments(vmArgs, props, SYSTEM_PROP_PREFIX);

			// VM args for JMX connection
			EnumSet<JmxBeanSupport.Feature> enabled = getEnabledJmxFeatures(conf);
			if (!enabled.isEmpty()) {
				int port = 0;
				try {
					port = Integer.parseInt(getJMXPort(conf));
				} catch (Exception e) {
					//ignore: bad data in launch config.
				}
				// port==0 means 'auto': no port is picked here. The child JVM starts without any
				// com.sun.management.jmxremote* args, and the JMX agent is started later
				// on-demand via Attach API against the child's real PID (see BootProcessFactory /
				// SpringApplicationLifeCycleClientManager). An explicitly pinned nonzero port
				// keeps using the old fixed-port args.
				String[] enableLiveBeanArgs = DebugPlugin.parseArguments(JmxBeanSupport.jmxBeanVmArgs(port, enabled));
				for (int i = 0; i < enableLiveBeanArgs.length; i++) {
					vmArgs.add(i, enableLiveBeanArgs[i]);
				}
				if (port!=0) {
					ILaunch currentLaunch = CURRENT_LAUNCH.get();
					if (currentLaunch!=null) {
						currentLaunch.setAttribute(JMX_PORT, ""+port);
					}
				}
			}
			// Fast startup VM args
			String fastStartupArgs = BootActivator.getDefault().getPreferenceStore().getString(BootPreferences.PREF_BOOT_FAST_STARTUP_JVM_ARGS);
			boolean fastStartup = getFastStartup(conf) && fastStartupArgs != null && !fastStartupArgs.isEmpty();
			if (fastStartup && !fastStartupArgs.trim().isEmpty()) {
				// Add space to separate fast startup args from the preceding arguments
				vmArgs.addAll(Arrays.asList(DebugPlugin.parseArguments(fastStartupArgs)));
			}

			String projectName = AbstractBootLaunchConfigurationDelegate.getProjectName(conf);
			vmArgs.add("-D" + SPRING_PROJECT_NAME_ATTRIBUTE + "=" + projectName);

			return DebugPlugin.renderArguments(vmArgs.toArray(new String[vmArgs.size()]), null);
		} catch (Exception e) {
			Log.log(e);
		}
		return super.getVMArguments(conf);
	}

	public static EnumSet<Feature> getEnabledJmxFeatures(ILaunchConfiguration conf) {
		EnumSet<Feature> enabled = EnumSet.noneOf(Feature.class);
		if (getEnableJmx(conf)) {
			enabled.add(Feature.JMX);
		}
		if (getEnableLiveBeanSupport(conf)) {
			 enabled.add(Feature.LIVE_BEAN_GRAPH);
		}
		if (getEnableLifeCycle(conf)) {
			enabled.add(Feature.LIFE_CYCLE);
		}
		return enabled;
	}

	public static boolean isHiddenFromBootDash(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(HIDE_FROM_BOOT_DASH, DEFAULT_HIDE_FROM_BOOT_DASH);
		} catch (CoreException e) {
			Log.log(e);
		}
		return DEFAULT_HIDE_FROM_BOOT_DASH;
	}

	public static void setHiddenFromBootDash(ILaunchConfigurationWorkingCopy conf, boolean hide) {
		conf.setAttribute(HIDE_FROM_BOOT_DASH, hide);
	}

	/**
	 * Retrieve the 'Enable Life Cycle Tracking' option from the config. Note that
	 * this doesn't necesarily mean that this feature is effectively enabled as
	 * it is only supported on recent enough versions of Boot.
	 * <p>
	 * See also the 'supportsLifeCycleManagement' method.
	 */
	public static boolean getEnableLifeCycle(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(ENABLE_LIFE_CYCLE, DEFAULT_ENABLE_LIFE_CYCLE);
		} catch (Exception e) {
			Log.log(e);
		}
		return DEFAULT_ENABLE_LIFE_CYCLE;
	}

	public static boolean getFastStartup(ILaunchConfiguration conf) {
		boolean defaultValue = BootActivator.getDefault().getPreferenceStore().getBoolean(BootPreferences.PREF_BOOT_FAST_STARTUP_DEFAULT);
		try {
			return conf.getAttribute(FAST_STARTUP, defaultValue);
		} catch (Exception e) {
			Log.log(e);
		}
		return defaultValue;
	}

	public static void setEnableJMX(ILaunchConfigurationWorkingCopy wc, boolean enable) {
		wc.setAttribute(ENABLE_JMX, enable);
	}

	public static void setEnableLifeCycle(ILaunchConfigurationWorkingCopy wc, boolean enable) {
		wc.setAttribute(ENABLE_LIFE_CYCLE, enable);
	}

	public static void setFastStartup(ILaunchConfigurationWorkingCopy wc, boolean enable) {
		wc.setAttribute(FAST_STARTUP, enable);
	}

	public static boolean canUseLifeCycle(ILaunchConfiguration conf) {
		return BootLaunchConfigurationDelegate.getEnableLifeCycle(conf)
				&& BootLaunchConfigurationDelegate.supportsLifeCycleManagement(conf);
	}

	public static boolean canUseLifeCycle(ILaunch launch) {
		ILaunchConfiguration conf = launch.getLaunchConfiguration();
		return conf!=null && canUseLifeCycle(conf);
	}

	public static boolean supportsLifeCycleManagement(ILaunchConfiguration conf) {
		IProject p = getProject(conf);
		if (p!=null) {
			return BootPropertyTester.supportsLifeCycleManagement(p);
		}
		return false;
	}

	/**
	 * Sets minimal default values to create a runnable launch configuration.
	 */
	public static void setDefaults(ILaunchConfigurationWorkingCopy wc,
			IProject project,
			String mainType
	) throws CoreException {
		setProcessFactory(wc, BootProcessFactory.class);
		setProject(wc, project);
		if (project!=null && project.hasNature(SpringBootCore.M2E_NATURE)) {
			enableMavenClasspathProviders(wc);
		} else if (project!=null && project.hasNature(SpringBootCore.BUILDSHIP_NATURE)) {
			enableGradleClasspathProviders(wc);
		}
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE, true);
		if (mainType!=null) {
			setMainType(wc, mainType);
		}
		setEnableJMX(wc, DEFAULT_ENABLE_JMX);
		setAutoConnect(wc, DEFAULT_AUTO_CONNECT);
		setEnableLiveBeanSupport(wc, DEFAULT_ENABLE_LIVE_BEAN_SUPPORT());
		setEnableLifeCycle(wc, DEFAULT_ENABLE_LIFE_CYCLE);
		setTerminationTimeout(wc,""+DEFAULT_TERMINATION_TIMEOUT);
		setJMXPort(wc, ""+DEFAULT_JMX_PORT);
		if (!OsUtils.isWindows()) {
			setVMArgs(wc, ENABLE_CHEAP_ENTROPY_VM_ARGS);
		}
	}

	public static void setTerminationTimeout(ILaunchConfigurationWorkingCopy wc, String value) {
		wc.setAttribute(TERMINATION_TIMEOUT, ""+value);
	}

	public static String getTerminationTimeout(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(TERMINATION_TIMEOUT, ""+DEFAULT_TERMINATION_TIMEOUT);
		} catch (Exception e) {
			Log.log(e);
			return ""+DEFAULT_TERMINATION_TIMEOUT;
		}
	}

	public static long getTerminationTimeoutAsLong(ILaunchConfiguration conf) {
		String v = getTerminationTimeout(conf);
		if (StringUtil.hasText(v)) {
			try {
				return Long.parseLong(v);
			} catch (Exception e) {
				Log.log(e);
			}
		}
		return DEFAULT_TERMINATION_TIMEOUT;
	}


	private static void setVMArgs(ILaunchConfigurationWorkingCopy wc, String vmArgs) {
		wc.setAttribute(ATTR_VM_ARGUMENTS, vmArgs);
	}

	/**
	 * Notes:
	 * <p>
	 *  1. we are assuming that the processFactoryId is the same as the classname of
	 *  the class that implements it. This is not a given, but a convenient and logical convention.
	 *  <p>
	 *  2. The class must be registered to this ID using plugin.xml (extension point
	 *  org.eclipse.debug.core.processFactories)
	 */
	public static void setProcessFactory(ILaunchConfigurationWorkingCopy wc, Class<? extends BootProcessFactory> klass) {
		wc.setAttribute(ATTR_PROCESS_FACTORY_ID, klass.getName());
	}

	public static boolean getEnableJmx(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(ENABLE_JMX, DEFAULT_ENABLE_JMX);
		} catch (Exception e) {
			Log.log(e);
		}
		return DEFAULT_ENABLE_JMX;
	}

	public static boolean getEnableLiveBeanSupport(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(ENABLE_LIVE_BEAN_SUPPORT, DEFAULT_ENABLE_LIVE_BEAN_SUPPORT());
		} catch (Exception e) {
			Log.log(e);
		}
		return DEFAULT_ENABLE_LIVE_BEAN_SUPPORT();
	}

	public static String getJMXPort(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(JMX_PORT, "");
		} catch (CoreException e) {
			Log.log(e);
		}
		return "";
	}

	public static boolean getAutoConnect(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(AUTO_CONNECT, DEFAULT_AUTO_CONNECT);
		} catch (CoreException e) {
			Log.log(e);
		}
		return false;
	}

	public static void setAutoConnect(ILaunchConfigurationWorkingCopy conf, boolean autoConnect) {
		conf.setAttribute(AUTO_CONNECT, autoConnect);
	}

	public static void setEnableLiveBeanSupport(ILaunchConfigurationWorkingCopy conf, boolean value) {
		conf.setAttribute(ENABLE_LIVE_BEAN_SUPPORT, value);
	}

	public static void setJMXPort(ILaunchConfigurationWorkingCopy conf, String portAsStr) {
		conf.setAttribute(JMX_PORT, portAsStr);
	}

	public static String getProfile(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(PROFILE, DEFAULT_PROFILE);
		} catch (CoreException e) {
			Log.log(e);
			return DEFAULT_PROFILE;
		}
	}

	public static void setProfile(ILaunchConfigurationWorkingCopy conf, String profile) {
		conf.setAttribute(PROFILE, profile);
	}

	public static ILaunchConfiguration duplicate(ILaunchConfiguration conf) throws CoreException {
		String newName = DebugPlugin.getDefault().getLaunchManager().generateLaunchConfigurationName(conf.getName());
		ILaunchConfigurationWorkingCopy copy = conf.copy(newName);

		int existingJmxPort = getJMXPortAsInt(conf);
		if (existingJmxPort>0) {
			//change port on duplicated config, but only if it was set to a specific port.
			setJMXPort(copy, ""+JmxBeanSupport.randomPort());
		}
		return copy.doSave();
	}


	public static ILaunchConfigurationWorkingCopy createWorkingCopy(String nameHint) throws CoreException {
		String name = getLaunchMan().generateLaunchConfigurationName(nameHint);
		return getConfType().newInstance(null, name);
	}

	public static ILaunchConfigurationType getConfType() {
		return getLaunchMan().getLaunchConfigurationType(TYPE_ID);
	}

	public static ILaunchConfiguration createConf(IType type) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy(type);
		return wc.doSave();
	}

	public static ILaunchConfigurationWorkingCopy createWorkingCopy(IType type) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = null;
		ILaunchConfigurationType configType = getConfType();
		IProject project = type.getJavaProject().getProject();
		String projectName = type.getJavaProject().getElementName();
		String shortTypeName = type.getTypeQualifiedName('.');
		String typeName = type.getFullyQualifiedName();
		wc = configType.newInstance(null, getLaunchMan().generateLaunchConfigurationName(
				projectName+" - "+shortTypeName));
		BootLaunchConfigurationDelegate.setDefaults(wc, project, typeName);
		wc.setMappedResources(new IResource[] {type.getUnderlyingResource()});
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE, !isTestCode(type));
		return wc;
	}

	private static boolean isTestCode(IType type) throws JavaModelException {
		IPackageFragmentRoot packageFragmentRoot = (IPackageFragmentRoot) type.getPackageFragment().getParent();
		IJavaProject javaProject = packageFragmentRoot.getJavaProject();
		if (javaProject != null) {
			IClasspathEntry entry = javaProject.getClasspathEntryFor(packageFragmentRoot.getPath());
			if (entry != null && !entry.isTest()) {
				return false;
			}
		}
		return true;
	}

	public static ILaunchConfiguration createConf(IProject project) throws CoreException {
		return createConf(JavaCore.create(project));
	}

	public static ILaunchConfiguration createConf(IJavaProject project) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = null;
		ILaunchConfigurationType configType = getConfType();
		String projectName = project.getElementName();
		wc = configType.newInstance(null, getLaunchMan().generateLaunchConfigurationName(projectName));
		BootLaunchConfigurationDelegate.setDefaults(wc, project.getProject(), null);
		wc.setMappedResources(new IResource[] {project.getUnderlyingResource()});
		return wc.doSave();
	}

	public static int getJMXPortAsInt(ILaunchConfiguration conf) {
		String jmxPortStr = getJMXPort(conf);
		if (jmxPortStr!=null) {
			try {
				return Integer.parseInt(jmxPortStr);
			} catch (Exception e) {
				//Ignore
			}
		}
		return -1;
	}

	public static int getJMXPortAsInt(ILaunch launch) {
		String jmxPortStr = launch.getAttribute(JMX_PORT);
		if (jmxPortStr!=null) {
			try {
				return Integer.parseInt(jmxPortStr);
			} catch (Exception e) {
				//Ignore
			}
		}
		return -1;
	}

	public static long getTerminationTimeoutAsLong(ILaunch launch) {
		ILaunchConfiguration conf = launch.getLaunchConfiguration();
		if (conf!=null) {
			return BootLaunchConfigurationDelegate.getTerminationTimeoutAsLong(conf);
		}
		return BootLaunchConfigurationDelegate.DEFAULT_TERMINATION_TIMEOUT;
	}

	/**
	 * This is for STS based on Eclipse 4.24 and older.
	 *
	 * @return true if "Legacy" ANSI plugin is available.
	 */
	private static boolean isLegacyAnsiPluginInstalled() {
		Bundle bundle = Platform.getBundle("net.mihai-nita.ansicon.plugin");
		return bundle != null && bundle.getState() != Bundle.UNINSTALLED;
	}

	public static boolean supportsAnsiConsoleOutput() {
		return isPlatformAnsiSupportEnabled() || isLegacyAnsiPluginInstalled();
	}

	/**
	 *
	 * @return true if ANSI plugin support in Eclipse platform is enabled. This applies for Eclipse 4.25 and higher
	 */
	private static boolean isPlatformAnsiSupportEnabled() {
		IPreferenceStore prefStore = ConsolePlugin.getDefault().getPreferenceStore();
		return prefStore.getBoolean(PREF_ANSI_CONSOLE_ENABLED);
	}

	public static boolean getEnableAnsiConsoleOutput(ILaunchConfiguration conf) {
		boolean defaultValue = supportsAnsiConsoleOutput();
		try {
			return conf.getAttribute(ANSI_CONSOLE_OUTPUT, defaultValue);
		} catch (CoreException e) {
			return defaultValue;
		}
	}

	public static void setEnableAnsiConsoleOutput(ILaunchConfigurationWorkingCopy wc, boolean enable) {
		wc.setAttribute(ANSI_CONSOLE_OUTPUT, enable);
	}

	@Override
	public String[][] getClasspathAndModulepath(ILaunchConfiguration conf) throws CoreException {
		//with Java 9 Beta installed we need this method, because getClasspath is no longer called.
		try {
			if (useThinWrapper(conf)) {
				return new String[][] {
					getClasspath(conf),
					new String[] {}
				};
	 		};
	 		return super.getClasspathAndModulepath(conf);
		} catch (Throwable e) {
			throw ExceptionUtil.coreException(e);
		}
	}

	@Override
	public String[] getClasspath(ILaunchConfiguration conf) throws CoreException {
		if (useThinWrapper(conf)) {
			File thinWrapper = BootPreferences.getInstance().getThinWrapper();
			Assert.isLegal(thinWrapper!=null, "'Use thin wrapper' option is selected, but thin wrapper is not defined");
			Assert.isLegal(thinWrapper.isFile(), "'Use thin wrapper' option is selected, but thin wrapper ("+thinWrapper+") is not an existing file");
			return new String[] {
					thinWrapper.getAbsolutePath()
			};
		}
		return super.getClasspath(conf);
	}

	@Override
	public String getMainTypeName(ILaunchConfiguration conf) throws CoreException {
		try {
			if (useThinWrapper(conf)) {
				return getThinWrapperMain(conf);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return super.getMainTypeName(conf);
	}

	protected String getThinWrapperMain(ILaunchConfiguration conf) throws Exception {
		File thinWrapper = BootPreferences.getInstance().getThinWrapper();
		try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(thinWrapper))) {
			ZipEntry zipEntry;
			while (null!=(zipEntry = zipStream.getNextEntry())) {
				String name = zipEntry.getName();
				if (name.equals("META-INF/MANIFEST.MF")) {
					Manifest manifest = new Manifest(zipStream);
					String mainClass = manifest.getMainAttributes().getValue("Main-Class");
					if (mainClass!=null) {
						return mainClass;
					} else {
						throw new IllegalArgumentException("Thin wrapper '"+thinWrapper+"' doesn't have a 'Main-Class' attribute in its jar manifest");
					}
				}
			}
			throw new IllegalArgumentException("No META-INF/MANIFEST.MF found in '"+thinWrapper+"'. Is it a proper 'thin boot wrapper' jar?");
		}
	}

	/**
	 * Copy a given launch config into a 'clone' that has all the same attributes but
	 * a different type id.
	 */
	public static ILaunchConfigurationWorkingCopy copyAs(ILaunchConfiguration conf,
			String newType) throws CoreException {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType launchConfigurationType = launchManager
				.getLaunchConfigurationType(newType);
		ILaunchConfigurationWorkingCopy wc = launchConfigurationType.newInstance(null,
				launchManager.generateLaunchConfigurationName(conf.getName()));
		wc.setAttributes(conf.getAttributes());
		return wc;
	}

	public static boolean useThinWrapper(ILaunchConfiguration conf) {
		try {
			return BootPreferences.getInstance().getThinWrapper()!=null && conf.getAttribute(USE_THIN_WRAPPER, DEFAULT_USE_THIN_WRAPPER);
		} catch (CoreException e) {
			Log.log(e);
		}
		return DEFAULT_USE_THIN_WRAPPER;
	}

	public static void setUseThinWrapper(ILaunchConfigurationWorkingCopy wc, boolean b) {
		wc.setAttribute(USE_THIN_WRAPPER, b);
	}

}
