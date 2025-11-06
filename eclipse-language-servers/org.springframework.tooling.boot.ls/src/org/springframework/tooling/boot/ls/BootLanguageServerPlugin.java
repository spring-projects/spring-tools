/*******************************************************************************
 * Copyright (c) 2017, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.springframework.tooling.boot.ls.prefs.CategoryProblemsSeverityPrefsPage;
import org.springframework.tooling.boot.ls.prefs.LiveInformationPreferencePage;

/**
 * Boot-Java LS extension plugin
 * 
 * @author Alex Boyko
 *
 */
public class BootLanguageServerPlugin extends AbstractUIPlugin {
	
	private static final String STEREOTYPE_IMG_PREFIX = "stereotype-";
	
	public static final String SPRING_ICON = "SPRING_ICON";

	public static String PLUGIN_ID = "org.springframework.tooling.boot.ls";

	private static final Object LSP4E_COMMAND_SYMBOL_IN_WORKSPACE = "org.eclipse.lsp4e.symbolinworkspace";
	
	// The shared instance
	private static BootLanguageServerPlugin plugin;

	public static final String BOOT_LS_DEFINITION_ID = "org.eclipse.languageserver.languages.springboot";
	
	public BootLanguageServerPlugin() {
		// Empty
	}

	public static IEclipsePreferences getPreferences() {
		return InstanceScope.INSTANCE.getNode(PLUGIN_ID);
	}
	
	@Override
	public void start(BundleContext context) throws Exception {
		plugin = this;
		super.start(context);
		deactivateDuplicateKeybindings();
		LiveInformationPreferencePage.manageCodeMiningPreferences();
		
		CategoryProblemsSeverityPrefsPage.loadProblemCategoriesIntoPreferences();
	}
	
	

	@Override
	public void stop(BundleContext context) throws Exception {
		super.stop(context);
		plugin = null;
	}
	
	public static BootLanguageServerPlugin getDefault() {
		return plugin;
	}

	private void deactivateDuplicateKeybindings() {
		IBindingService service = PlatformUI.getWorkbench().getService(IBindingService.class);
		if (service != null) {
			List<Binding> newBindings = new ArrayList<>();
			Binding[] bindings = service.getBindings();

			for (Binding binding : bindings) {
				String commandId = null;

				if (binding != null && binding.getParameterizedCommand() != null && binding.getParameterizedCommand().getCommand() != null) {
					commandId = binding.getParameterizedCommand().getCommand().getId();

					if (commandId == null) {
						newBindings.add(binding);
					}
					else if (!commandId.equals(LSP4E_COMMAND_SYMBOL_IN_WORKSPACE)) {
						newBindings.add(binding);
					}
				}
				else {
					newBindings.add(binding);
				}
			}

			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					try {
						service.savePreferences(service.getActiveScheme(),
								newBindings.toArray(new Binding[newBindings.size()]));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
		}
	}

	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
		super.initializeImageRegistry(reg);
		reg.put(SPRING_ICON, imageDescriptorFromPlugin(PLUGIN_ID, "icons/spring_obj.gif"));
		
		// Add setereotype icons to the registry
		Enumeration<String> paths = getBundle().getEntryPaths("icons/stereotypes");
		while(paths.hasMoreElements()) {
			String relativePath = paths.nextElement();
			IPath p = new Path(relativePath);
			if (p.getFileExtension().equals("svg")) {
				String fileName = p.lastSegment();
				String name = fileName.substring(0, fileName.length() - 4);
				
				try {
					URI uri = new URI("platform", null, "/plugin/" + PLUGIN_ID + "/" + p, null);
					reg.put(STEREOTYPE_IMG_PREFIX + name, createStereotypeImageDescriptor(uri.toURL(), p));
				} catch (URISyntaxException | MalformedURLException e) {
					getLog().log(Status.error("Failed to create image descriptor for " + p, e));
				}
			}
		}
	}
	
	private ImageDescriptor createStereotypeImageDescriptor(URL url, IPath p) {
		RGB rgb = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry()
				.getRGB(JFacePreferences.INFORMATION_FOREGROUND_COLOR);
		if (rgb != null) {
			final String fileName = p.lastSegment();
			try {
				String rgbStr = "%02x-%02x-%02x".formatted(rgb.red, rgb.green, rgb.blue);
				java.nio.file.Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), PLUGIN_ID, rgbStr);
				if (!Files.exists(tempDir)) {
					Files.createDirectories(tempDir);
				}
				java.nio.file.Path tempSvgPath = tempDir.resolve(fileName);
				if (!Files.exists(tempSvgPath)) {
					String svg = new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8)
							.replace("currentColor", "rgb(%d,%d,%d)".formatted(rgb.red, rgb.green, rgb.blue));
					Files.createFile(tempSvgPath);
					Files.write(tempSvgPath, svg.getBytes(StandardCharsets.UTF_8));
				}
				return ImageDescriptor.createFromURL(tempSvgPath.toUri().toURL());
			} catch (IOException e) {
				getLog().error("Failed to create theme compatible SVG icon " + p, e);
			}
		}
		return ImageDescriptor.createFromURL(url);
	}
	
	public Image getStereotypeImage(String name) {
		return getImageRegistry().get(STEREOTYPE_IMG_PREFIX + name);
	}
	
}
