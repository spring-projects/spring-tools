/*******************************************************************************
 * Copyright (c) 2012 - 2014 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.service.prefs.BackingStoreException;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;

/**
 * Provides access to externalized resources such as urls. Resources are
 * specified through an extension point.
 *
 * @author Steffen Pingel
 * @author Christian Dupuis
 */
public class ResourceProvider {

	public static final String RECORD_SEPARATOR_PATTERN = "[\\r\\n\\s]";

	public static final String FIELD_SEPARATOR = "|";

	public static final String FIELD_SEPARATOR_PATTERN = "\\|";

	public static final String RECORD_SEPARATOR = "\n";

	public static class Property {

		public String type;

		private String id;

		private String label;

		private String defaultValue;

		private boolean multiValue;

		private boolean userConfigurable;

		private String contributedValue;

		private final ResourceProvider provider;

		private Property(ResourceProvider provider) {
			this.provider = provider;
		}

		public String getDefaultValue() {
			String value = contributedValue;
			if (value == null) {
				value = defaultValue;
			}
			return value;
		}

		public String getLabel() {
			return label;
		}

		public String getValue() {
			String value = provider.getPreferences().get(id, null);
			if (value == null) {
				value = contributedValue;
			}
			if (value == null) {
				value = defaultValue;
			}
			return value;
		}

		public boolean isMultiValue() {
			return multiValue;
		}

		public boolean isUserConfigurable() {
			return userConfigurable;
		}

		public void setValue(String value) {
			provider.getPreferences().put(id, value);
			try {
				provider.getPreferences().flush();
			} catch (BackingStoreException e) {
				StatusHandler.log(new Status(IStatus.ERROR,
						CorePlugin.PLUGIN_ID,
						"Error occurred while saving preferences", e));
			}
		}

	}

	private static class ExtensionPointReader {

		private static final String ATTRIBUTE_ID = "id";

		private static final String ATTRIBUTE_VALUE = "value";

		private static final String ATTRIBUTE_LABEL = "label";

		private static final String ATTRIBUTE_TYPE = "type";

		private static final String ATTRIBUTE_MULTI_VALUE = "multiValue";

		private static final String ATTRIBUTE_DEFAULT_VALUE = "defaultValue";

		private static final String ATTRIBUTE_USER_CONFIGURABLE = "userConfigurable";

		private static final String ELEMENT_URL = "url";

		private static final String ELEMENT_PROPERTY = "propertyDefinition";

		private static final String EXTENSION_ID_REFERENCES = "com.springsource.sts.core.resources";

		private final Map<String, String> valueByKey = new HashMap<String, String>();

		private final Map<String, Property> propertyById = new HashMap<String, Property>();

		public void read(ResourceProvider resourceProvider) {
			IExtensionRegistry registry = Platform.getExtensionRegistry();
			IExtensionPoint extensionPoint = registry
					.getExtensionPoint(EXTENSION_ID_REFERENCES);
			IExtension[] extensions = extensionPoint.getExtensions();

			// read property definitions
			for (IExtension extension : extensions) {
				IConfigurationElement[] elements = extension
						.getConfigurationElements();
				for (IConfigurationElement element : elements) {
					if (element.getName().compareTo(ELEMENT_PROPERTY) == 0) {
						Property p = new Property(resourceProvider);
						p.id = element.getAttribute(ATTRIBUTE_ID);
						p.label = element.getAttribute(ATTRIBUTE_LABEL);
						p.defaultValue = element
								.getAttribute(ATTRIBUTE_DEFAULT_VALUE);
						p.multiValue = Boolean.parseBoolean(element
								.getAttribute(ATTRIBUTE_MULTI_VALUE));
						p.userConfigurable = Boolean.parseBoolean(element
								.getAttribute(ATTRIBUTE_USER_CONFIGURABLE));
						p.type = element.getAttribute(ATTRIBUTE_TYPE);
						propertyById.put(p.id, p);
					}
				}
			}

			// read contributed definitions
			for (IExtension extension : extensions) {
				IConfigurationElement[] elements = extension
						.getConfigurationElements();
				for (IConfigurationElement element : elements) {
					if (element.getName().compareTo(ELEMENT_URL) == 0) {
						String key = element.getAttribute(ATTRIBUTE_ID);
						String value = element.getAttribute(ATTRIBUTE_VALUE);
						valueByKey.put(key, value);

						Property p = propertyById.get(key);
						if (p == null) {
							// property was not defined explicitly
							p = new Property(resourceProvider);
							p.id = key;
							p.label = "";
							propertyById.put(p.id, p);
						}
						p.contributedValue = value;
					}
				}
			}
		}

	}

	private final IEclipsePreferences preferences;

	private static ResourceProvider instance = new ResourceProvider();

	public static ResourceProvider getInstance() {
		return instance;
	}

	public static String getUrl(String key) {
		return getUrls(key)[0];
	}

	public static String[] getUrls(String key) {
		String[] urls = parseUrls(key, instance.getValue(key));
		String[] defaultUrls = parseUrls(key, instance.getDefaultValue(key));

		Set<String> cleanableUrls = new HashSet<>();
		for (String url : defaultUrls) {
			if (url.startsWith("https:")) {
				cleanableUrls.add("http:"+url.substring(6));
			} else if (url.startsWith("http:")) {
				cleanableUrls.add(url);
			}
		}

		for (int i = 0; i < urls.length; i++) {
			String url = urls[i];
			if (cleanableUrls.contains(url)) {
				urls[i] = "https:"+url.substring(5);
			}
		}
		return urls;
	}

	private static String[] parseUrls(String key, String value) {
		if (value == null) {
			throw new RuntimeException(NLS.bind(
					"No URL found for key: ''{0}''", key));
		}
		if (value.isEmpty()) {
			return new String[0];
		}

		// Post process the urls to replace UUID and version number placeholders
		if (value.contains("%UUID%")) {
			value = value.replace("%UUID%", CorePlugin.getUUID());
		}
		if (value.contains("%VERSION%") || value.contains("%SHORT_VERSION%")) {
			Bundle bundle = CorePlugin.getDefault().getBundle();
			Version version = new Version((String) bundle.getHeaders().get(
					"Bundle-Version")); //$NON-NLS-1$
			value = value.replace("%VERSION%", version.toString());
			value = value.replace("%SHORT_VERSION%", version.getMajor() + "."
					+ version.getMinor());
		}
		String[] values = value.split(RECORD_SEPARATOR_PATTERN);
		return discardLocationNames(values);
	}

	private static String[] discardLocationNames(String[] values) {
		for (int i = 0; i < values.length; i++) {
			String nameUrlString = values[i];
			String[] nameUrlStrings = nameUrlString
					.split(FIELD_SEPARATOR_PATTERN);
			if (nameUrlStrings.length == 2) {
				String url = nameUrlStrings[1];
				values[i] = url;
			}
		}
		return values;
	}

	private final Map<String, Property> propertyById;

	private final List<PropertyChangeListener> listeners;

	private ResourceProvider() {
		this.listeners = new CopyOnWriteArrayList<PropertyChangeListener>();
		this.preferences = new InstanceScope().getNode(CorePlugin.PLUGIN_ID);
		this.preferences
				.addPreferenceChangeListener(new IPreferenceChangeListener() {
					public void preferenceChange(PreferenceChangeEvent event) {
						Property p = propertyById.get(event.getKey());
						if (p != null) {
							for (PropertyChangeListener listener : listeners) {
								listener.propertyChange(new PropertyChangeEvent(
										ResourceProvider.this, p.id, event
												.getOldValue(), event
												.getNewValue()));
							}
						}
					}
				});

		DefaultScope.INSTANCE.getNode(CorePlugin.PLUGIN_ID).addPreferenceChangeListener(new IPreferenceChangeListener() {
			@Override
			public void preferenceChange(PreferenceChangeEvent event) {
				Property p = propertyById.get(event.getKey());
				if (p != null) {
					p.defaultValue = (String)event.getNewValue();
				}
			}
		});

		ExtensionPointReader reader = new ExtensionPointReader();
		reader.read(this);
		this.propertyById = reader.propertyById;
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		listeners.add(listener);
	}

	public IEclipsePreferences getPreferences() {
		return preferences;
	}

	public Collection<Property> getProperties() {
		return Collections.unmodifiableCollection(propertyById.values());
	}

	public Property getProperty(String id) {
		return propertyById.get(id);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		listeners.remove(listener);
	}

	private String getValue(String key) {
		Property p = propertyById.get(key);
		return (p != null) ? p.getValue() : null;
	}

	private String getDefaultValue(String key) {
		Property p = propertyById.get(key);
		return (p != null) ? p.getDefaultValue() : null;
	}


}
