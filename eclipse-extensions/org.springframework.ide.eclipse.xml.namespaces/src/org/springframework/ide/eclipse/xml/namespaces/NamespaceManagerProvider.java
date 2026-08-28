/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.xml.namespaces;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.springframework.ide.eclipse.xml.namespaces.model.INamespaceDefinition;
import org.springframework.ide.eclipse.xml.namespaces.model.INamespaceDefinitionListener;
import org.springframework.ide.eclipse.xml.namespaces.model.INamespaceDefinitionResolver;

import com.google.common.collect.ImmutableSet;

/**
 * Provides {@link INamespaceManager} instance loaded from
 * extension point.
 * 
 * @author Kris De Volder
 */
public class NamespaceManagerProvider {

	private static String EXTENSION_POINT = SpringXmlNamespacesPlugin.PLUGIN_ID+".namespacemanager";
	
	/**
	 * Dummy implementation to use when there is no contributed {@link INamespaceManager}
	 */
	private static INamespaceManager NULL = new INamespaceManager() {

		@Override
		public void unregisterNamespaceDefinitionListener(INamespaceDefinitionListener listener) {
		}

		@Override
		public void registerNamespaceDefinitionListener(INamespaceDefinitionListener listener) {
		}

		@Override
		public void notifyNamespaceDefinitionListeners(IProject project) {
		}

		@Override
		public INamespaceDefinitionResolver getNamespaceDefinitionResolver() {
			return new INamespaceDefinitionResolver() {
				
				@Override
				public INamespaceDefinition resolveNamespaceDefinition(String namespaceUri) {
					return null;
				}
				
				@Override
				public Set<INamespaceDefinition> getNamespaceDefinitions() {
					return ImmutableSet.of();
				}
			};
		}

		@Override
		public CompletableFuture<?> nameSpaceHandlersReady() {
			return CompletableFuture.completedFuture(null);
		}
	};

	private static INamespaceManager instance;
	
	public static synchronized INamespaceManager get() {
		if (instance == null) {
			instance = create();
		}
		return instance;
	}
	
	private static INamespaceManager create() {
		try {
			IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(EXTENSION_POINT);
			if (point != null) {
				IExtension[] extensions = point.getExtensions();
				if (extensions != null && extensions.length > 0) {
					Assert.isLegal(extensions.length == 1);
					IConfigurationElement[] configs = extensions[0].getConfigurationElements();
					if (configs != null) {
						Assert.isLegal(configs.length == 1);
						return (INamespaceManager) configs[0].createExecutableExtension("class");
					}
				}
			}
		} catch (Exception e) {
			SpringXmlNamespacesPlugin.log(e);
		}
		return NULL;
	}

}
