/*******************************************************************************
 * Copyright (c) 2018, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons;

import java.net.URL;
import java.util.Objects;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.editors.text.EditorsPlugin;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.springframework.tooling.ls.eclipse.commons.STS4LanguageClientImpl.UpdateHighlights;
import org.springframework.tooling.ls.eclipse.commons.preferences.PreferenceConstants;

@SuppressWarnings("restriction")
public class LanguageServerCommonsActivator extends AbstractUIPlugin {

	private static final String BOOT_HINT_ANNOTATION_TYPE = "org.springframework.tooling.bootinfo";

	public static final String PLUGIN_ID = "org.springframework.tooling.ls.eclipse.commons";

	public static final String BOOT_KEY = "boot-key";

	private static LanguageServerCommonsActivator instance;

	private final IPropertyChangeListener PROPERTY_LISTENER = new IPropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			switch (event.getProperty()) {
			case PreferenceConstants.HIGHLIGHT_RANGE_COLOR_THEME:
				updateMarkerAnnotationPreferences();
				// Fall through to update highlights
			case PreferenceConstants.HIGHLIGHT_CODELENS_PREFS:
				new UpdateHighlights(null, true);
				break;
			default:
			}
		}

	};

	private AnnotationPreference bootHintAnnotationPreference;

	/*
	 * Singleton state. We assume there is going to be one server at most serving info about SpringIndex
	 */
	private SpringIndexState springIndexState = new SpringIndexState();

	public LanguageServerCommonsActivator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		instance = this;
		super.start(context);
		getImageRegistry().put(BOOT_KEY, getImageDescriptor("icons/boot.png"));

		getPreferenceStore().addPropertyChangeListener(PROPERTY_LISTENER);
		PlatformUI.getWorkbench().getThemeManager().addPropertyChangeListener(PROPERTY_LISTENER);

		bootHintAnnotationPreference = EditorsPlugin.getDefault().getMarkerAnnotationPreferences()
				.getAnnotationPreferences().stream().filter(Objects::nonNull)
				.filter(info -> BOOT_HINT_ANNOTATION_TYPE.equals(info.getAnnotationType())).findFirst().orElse(null);
		updateMarkerAnnotationPreferences();
	}

	/**
	 * Forwards theme colors on to marker preferences
	 */
	private void updateMarkerAnnotationPreferences() {
		RGB themeRgb = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry()
				.getRGB(PreferenceConstants.HIGHLIGHT_RANGE_COLOR_THEME);
		Display display = PlatformUI.getWorkbench().getDisplay();
		if (!display.isDisposed()) {
			display.asyncExec(() -> PreferenceConverter.setValue(EditorsPlugin.getDefault().getPreferenceStore(),
					bootHintAnnotationPreference.getColorPreferenceKey(), themeRgb));
		}
	}

	public Color getBootHighlightRangeColor() {
		return PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry().get(PreferenceConstants.HIGHLIGHT_RANGE_COLOR_THEME);
	}

	public final static ImageDescriptor getImageDescriptor(String path) {
		ImageDescriptor desc = ImageDescriptor.getMissingImageDescriptor();
		Bundle bundle = Platform.getBundle(PLUGIN_ID);
		URL url = null;
		if (bundle != null) {
			url = FileLocator.find(bundle, new Path(path), null);
			if (url != null) {
				desc = ImageDescriptor.createFromURL(url);
			}
		}
		return desc;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		getPreferenceStore().removePropertyChangeListener(PROPERTY_LISTENER);
		PlatformUI.getWorkbench().getThemeManager().removePropertyChangeListener(PROPERTY_LISTENER);
		super.stop(context);
	}

	public static LanguageServerCommonsActivator getInstance() {
		return instance;
	}

	public static void logError(Throwable t, String message) {
		instance.getLog().log(new Status(IStatus.ERROR, instance.getBundle().getSymbolicName(), message, t));
	}

	public static void logInfo(String message) {
		instance.getLog().log(new Status(IStatus.INFO, instance.getBundle().getSymbolicName(), message));
	}

	public SpringIndexState getSpringIndexState() {
		return springIndexState;
	}

}
