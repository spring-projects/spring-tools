/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.sourcelookup.advanced.AdvancedJavaLaunchDelegate;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.util.ProcessListenerAdapter;
import org.springframework.ide.eclipse.boot.util.ProcessTracker;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public abstract class AbstractBootLaunchConfigurationDelegate extends AdvancedJavaLaunchDelegate {

	private static final String JDT_JAVA_APPLICATION = "org.eclipse.jdt.launching.localJavaApplication";

	private static final String SILENT_EXIT_EXCEPTION = "org.springframework.boot.devtools.restart.SilentExitExceptionHandler$SilentExitException";

	private static final String M2E_CLASSPATH_PROVIDER = "org.eclipse.m2e.launchconfig.classpathProvider";

	protected static final String M2E_SOURCEPATH_PROVIDER = "org.eclipse.m2e.launchconfig.sourcepathProvider";
	public static final String JAVA_LAUNCH_CONFIG_TYPE_ID = IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION;
	public static final String ENABLE_DEBUG_OUTPUT = "spring.boot.debug.enable";
	public static final boolean DEFAULT_ENABLE_DEBUG_OUTPUT = false;

	private static final String BOOT_MAVEN_SOURCE_PATH_PROVIDER = "org.springframework.ide.eclipse.boot.launch.BootMavenSourcePathProvider";
	private static final String BOOT_MAVEN_CLASS_PATH_PROVIDER = "org.springframework.ide.eclipse.boot.launch.BootMavenClassPathProvider";
	private static final String BUILDSHIP_CLASS_PATH_PROVIDER = "org.eclipse.buildship.core.classpathprovider";

	protected static final String PROGRAM_ARG_PREFIX = "--";
	protected static final String SYSTEM_PROP_PREFIX = "-D";

	/**
	 * Spring boot properties are stored as launch confiuration properties with
	 * an extra prefix added to property name to avoid name clashes with
	 * other launch config properties.
	 */
	private static final String PROPS_PREFIX = "spring.boot.prop.";

	private static final String APPLICATION_PROPERTIES = "spring.boot.app.properties";

	/**
	 * To be able to store multiple assignment to the same spring boot
	 * property name we add a 'oid' at the end of each stored
	 * property name. ?_SEPERATOR is used to separate the 'real'
	 * property name from the 'oid' string.
	 */
	private static final char OID_SEPERATOR = ':';

	/*
	 * Remove once old launch properties format is irrelevant
	 */
	@Deprecated
	public static class PropVal {
		public String name;
		public String value;
		public boolean isChecked;

		public PropVal(String name, String value, boolean isChecked) {
			//Don't use null, use empty Strings
			Assert.isNotNull(name);
			Assert.isNotNull(value);
			this.name = name;
			this.value = value;
			this.isChecked = isChecked;
		}

		@Override
		public String toString() {
			return (isChecked ? "[X] " : "[ ] ") +
					name + "="+ value;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (isChecked ? 1231 : 1237);
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PropVal other = (PropVal) obj;
			if (isChecked != other.isChecked)
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}
	}

	public static List<ILaunchConfiguration> getLaunchConfigs(IProject p, String confTypeId) {
		try {
			ILaunchManager lm = getLaunchMan();
			ILaunchConfigurationType type = lm.getLaunchConfigurationType(confTypeId);
			if (type != null) {
				ILaunchConfiguration[] configs = lm.getLaunchConfigurations(type);
				if (configs != null && configs.length > 0) {
					ArrayList<ILaunchConfiguration> result = new ArrayList<>();
					for (ILaunchConfiguration conf : configs) {
						if (p.equals(getProject(conf))) {
							result.add(conf);
						}
					}
					return result;
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return Collections.emptyList();
	}

	public static void clearProperties(ILaunchConfigurationWorkingCopy conf) {
		try {
			//note: e43 doesn't use generics for conf.getAttributes, hence the
			// funky casting below.
			conf.removeAttribute(APPLICATION_PROPERTIES);

			// Legacy properties
			for (Object _prefixedProp : conf.getAttributes().keySet()) {
				String prefixedProp = (String) _prefixedProp;
				if (prefixedProp.startsWith(PROPS_PREFIX)) {
					conf.removeAttribute(prefixedProp);
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
	}

	public static String getMainType(ILaunchConfiguration config) throws CoreException {
		return config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String)null);
	}

	public static void setMainType(ILaunchConfigurationWorkingCopy config, String typeName) {
		config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, typeName);
	}

	@SuppressWarnings("unchecked")
	private static List<PropVal> getProperties(ILaunchConfiguration conf) {
		ArrayList<PropVal> props = new ArrayList<>();
		try {
			//Note: in e43 conf.getAttributes doesn't use generics yet. So to
			//build with 4.3 we need to to some funky casting below.
			for (Object _e : conf.getAttributes().entrySet()) {
				try {
					Map.Entry<String, Object> e = (Entry<String, Object>) _e;
					String prefixed = e.getKey();
					if (prefixed.startsWith(PROPS_PREFIX)) {
						String name = prefixed.substring(PROPS_PREFIX.length());
						int dotPos = name.lastIndexOf(OID_SEPERATOR);
						if (dotPos >= 0) {
							name = name.substring(0, dotPos);
						}
						String valueEnablement = (String)e.getValue();
						String value = valueEnablement.substring(1);
						boolean enabled = valueEnablement.charAt(0) == '1';
						props.add(new PropVal(name, value, enabled));
					}
				} catch (Exception ignore) {
					//silently ignore invalid property data.
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return props;
	}

	public static String getRawApplicationProperties(ILaunchConfiguration conf) {
		try {
			if (conf.hasAttribute(APPLICATION_PROPERTIES)) {
				return conf.getAttribute(APPLICATION_PROPERTIES, "");
			} else {
				// Legacy properties
				Properties properties = new Properties();
				getProperties(conf).forEach(p -> properties.setProperty(p.name, p.value));
				StringWriter writer = new StringWriter();
				properties.store(writer, null);
				// Remove all comments generated by java.util.Properties serialization
				return writer.getBuffer().toString().replaceAll("(?m)^\\s*#.*$", "").trim();
			}
		} catch (Exception e) {
			Log.log(e);
			return "";
		}
	}

	public static void setRawApplicationProperties(ILaunchConfigurationWorkingCopy conf, String props) {
		conf.setAttribute(APPLICATION_PROPERTIES, props);
	}

	public static Properties getApplicationProperties(ILaunchConfiguration conf) {
		String propertiesString = getRawApplicationProperties(conf);
		Properties properties = new Properties();
		try {
			properties.load(new ByteArrayInputStream(propertiesString.getBytes()));
		} catch (IOException e) {
			Log.log(e);
		}
		return properties;
	}

	@SuppressWarnings("rawtypes")
	protected void addPropertiesArguments(List<String> args, Properties props, String prefix) {
		for (Map.Entry e : props.entrySet()) {
			String name = (String) e.getKey();
			String value = (String) e.getValue();
			//spring boot doesn't like empty option keys/values so skip those.
			if (!name.isEmpty()) {
				args.add(propertyAssignmentArgument(name, value, prefix));
			}
		}
	}

	protected String propertyAssignmentArgument(String name, String value, String prefix) {
		if (name.contains("=")) {
			//spring boot has no handling of escape sequences like '\='
			//so we cannot represent keys containing '='.
			throw new IllegalArgumentException("property name shouldn't contain '=':"+name);
		}
		StringBuilder sb = new StringBuilder();
		sb.append(prefix);
		sb.append(name);
		if (!value.isEmpty()) {
			sb.append('=');
			sb.append(value);
		}
		return sb.toString();
	}


	public static boolean getEnableDebugOutput(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(ENABLE_DEBUG_OUTPUT, DEFAULT_ENABLE_DEBUG_OUTPUT);
		} catch (Exception e) {
			Log.log(e);
			return DEFAULT_ENABLE_DEBUG_OUTPUT;
		}
	}

	public static void setEnableDebugOutput(ILaunchConfigurationWorkingCopy conf, boolean enable) {
		conf.setAttribute(ENABLE_DEBUG_OUTPUT, enable);
	}

	/**
	 * Get the project associated with this a luanch config. Note that this
	 * method returns an IProject reference regardless of whether or not the
	 * project exists.
	 */
	public static IProject getProject(ILaunchConfiguration conf) {
		try {
			String pname = getProjectName(conf);
			if (StringUtil.hasText(pname)) {
				IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(pname);
				//debug(conf, "getProject => "+p);
				return p;
			}
		} catch (Exception e) {
			Log.log(e);
		}
		//debug(conf, "getProject => NULL");
		return null;
	}

	public static String getProjectName(ILaunchConfiguration conf)
			throws CoreException {
		return conf.getAttribute(ATTR_PROJECT_NAME, "");
	}

	public static void setProject(ILaunchConfigurationWorkingCopy conf, IProject p) {
		//debug(conf, "setProject <= "+p);
		if (p == null) {
			conf.removeAttribute(ATTR_PROJECT_NAME);
		} else {
			conf.setAttribute(ATTR_PROJECT_NAME, p.getName());
		}
	}

	/**
	 * Enable maven classpath provider if applicable to this conf.
	 * Addresses https://issuetracker.springsource.com/browse/STS-4085
	 */
	static void enableMavenClasspathProvider(ILaunchConfigurationWorkingCopy conf) {
		try {
			if (conf.getType().getIdentifier().equals(JAVA_LAUNCH_CONFIG_TYPE_ID)) {
				//Take care not to add this a 'real' Boot launch config or it will cause m2e to throw exceptions
				//These 'magic' attributes should only be added to a 'cloned' copy of our config with the right type.
				IProject p = getProject(conf);
				if (p != null && p.hasNature(SpringBootCore.M2E_NATURE)) {
					if (!conf.hasAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER)) {
						conf.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, M2E_CLASSPATH_PROVIDER);
					}
					if (!conf.hasAttribute(IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER)) {
						conf.setAttribute(IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER, M2E_SOURCEPATH_PROVIDER);
					}
				}
			}
		} catch (Exception e) {
			BootActivator.log(e);
		}
	}

	public static ILaunchManager getLaunchMan() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	@Override
	public void launch(ILaunchConfiguration conf, String mode, final ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		conf = configureClassPathProviders(conf);
		if (ILaunchManager.DEBUG_MODE.equals(mode) && isIgnoreSilentExitException(conf)) {
			final IgnoreExceptionOfType breakpointListener = new IgnoreExceptionOfType(launch, SILENT_EXIT_EXCEPTION);
			new ProcessTracker(new ProcessListenerAdapter() {
				@Override
				public void debugTargetTerminated(ProcessTracker tracker, IDebugTarget target) {
					if (launch.equals(target.getLaunch())){
						breakpointListener.dispose();
						tracker.dispose();
					}
				}
			});
		}
		super.launch(conf, mode, launch, monitor);
	}

	protected ILaunchConfiguration configureClassPathProviders(ILaunchConfiguration conf) throws CoreException {
		IProject project = BootLaunchConfigurationDelegate.getProject(conf);
		if (project != null) {
			if (project.hasNature(SpringBootCore.M2E_NATURE)) {
				conf = modify(conf, (ILaunchConfigurationWorkingCopy wc) -> {
					enableMavenClasspathProviders(wc);
				});
			} else if (project.hasNature(SpringBootCore.BUILDSHIP_NATURE)) {
				conf = modify(conf, wc -> {
					enableGradleClasspathProviders(wc);
				});
			}
		}
		return conf;
	}

	@Override
	public String[][] getClasspathAndModulepath(ILaunchConfiguration configuration) throws CoreException {
		if (configuration.hasAttribute(IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE)) {
			//TODO: This is a dirty hack. We 'trick' BuildShip to treat our launch config as if it is a plain JDT launch by making
			// a temporary copy of it.
			//A request to make BuildShip provide a cleaner way to do this was filed here:
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=543328
			//If that bug is resolved this code should be removed.
			configuration = BootLaunchConfigurationDelegate.copyAs(configuration, JDT_JAVA_APPLICATION);
		}
		return super.getClasspathAndModulepath(configuration);
	}

	@Override
	public String[] getClasspath(ILaunchConfiguration configuration) throws CoreException {
		if (configuration.hasAttribute(IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE)) {
			//TODO: This is a dirty hack. We 'trick' BuildShip to treat our launch config as if it is a plain JDT launch by making
			// a temporary copy of it.
			//A request to make BuildShip provide a cleaner way to do this was filed here:
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=543328
			//If that bug is resolved this code should be removed.
			configuration = BootLaunchConfigurationDelegate.copyAs(configuration, JDT_JAVA_APPLICATION);
		}
		return super.getClasspath(configuration);
	}

	@Override
	public String[][] getBootpathExt(ILaunchConfiguration configuration) throws CoreException {
		if (configuration.hasAttribute(IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE)) {
			//TODO: This is a dirty hack. We 'trick' BuildShip to treat our launch config as if it is a plain JDT launch by making
			// a temporary copy of it.
			//A request to make BuildShip provide a cleaner way to do this was filed here:
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=543328
			//If that bug is resolved this code should be removed.
			configuration = BootLaunchConfigurationDelegate.copyAs(configuration, JDT_JAVA_APPLICATION);
		}
		return super.getBootpathExt(configuration);
	}

	public static void enableMavenClasspathProviders(ILaunchConfigurationWorkingCopy wc) {
		setAttribute(wc, IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER, BOOT_MAVEN_SOURCE_PATH_PROVIDER);
		setAttribute(wc, IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, BOOT_MAVEN_CLASS_PATH_PROVIDER);
		try {
			if (!wc.hasAttribute(IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE)) {
				setAttribute(wc, IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE, true);
			}
		} catch (CoreException e) {
			Log.log(e);
		}
	}

	public static void enableGradleClasspathProviders(ILaunchConfigurationWorkingCopy wc) {
		/* This is found in typical java launch config for buildship project. It plays a crucial role in
		 * computing correct runtime classpath:
		 *
		 * <booleanAttribute key="org.eclipse.jdt.launching.ATTR_EXCLUDE_TEST_CODE" value="true"/>
		 * <stringAttribute key="org.eclipse.jdt.launching.CLASSPATH_PROVIDER" value="org.eclipse.buildship.core.classpathprovider"/>
		 */
		setAttribute(wc, IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, BUILDSHIP_CLASS_PATH_PROVIDER);
	}

	private ILaunchConfiguration modify(ILaunchConfiguration conf, Consumer<ILaunchConfigurationWorkingCopy> mutator) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = conf.getWorkingCopy();
		try {
			mutator.accept(wc);
		} finally {
			if (wc.isDirty()) {
				 conf = wc.doSave();
			}
		}
		return conf;
	}

	private static void setAttribute(ILaunchConfigurationWorkingCopy wc, String a, boolean v) {
		try {
			if (!wc.hasAttribute(a) || v != wc.getAttribute(a, false)) {
				wc.setAttribute(a, v);
			}
		} catch (CoreException e) {
			Log.log(e);
		}
	}

	private static void setAttribute(ILaunchConfigurationWorkingCopy wc, String a, String v) {
		try {
			if (!Objects.equals(v, wc.getAttribute(a, (String)null))) {
				wc.setAttribute(a, v);
			}
		} catch (CoreException e) {
			Log.log(e);
		}
	}

	public static boolean isIgnoreSilentExitException(ILaunchConfiguration conf) {
		//This might be controlled by individual launch conf in future, but for now, it is just a global preference.
		return BootPreferences.getInstance().isIgnoreSilentExit();
	}

}
