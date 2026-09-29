/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.events;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.MethodInfo;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBindingKeys;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;

/**
 * The JAR/bytecode counterpart of {@link EventListenerIndexer} and of
 * {@code ComponentIndexer.indexEventListenerInterfaceImplementation}: an
 * {@link EventListenerIndexElement} per {@code @EventListener} method, and one for the
 * {@code onApplicationEvent} method of an {@code ApplicationListener} - the same elements, so the
 * same {@code listens on: EventType} label. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Not read: the listener method's annotations, which the element carries but the tree does not
 * render. Several {@code @EventListener}-annotated annotations on one method are read in the order
 * Jandex keeps them (sorted by name), not as written.
 *
 * @author Martin Lippert
 */
public class JarEventListenerScanner {

	/**
	 * {@code ComponentIndexer.indexEventListeners}: one element per annotation of a method that is
	 * (meta-)annotated with {@code @EventListener}; the event type is the single parameter's, or
	 * none.
	 */
	public static void addEventListeners(Bean bean, JarType type) {
		for (MethodInfo method : JarStereotypeScanner.sourceLevelMethodsOf(type.classInfo())) {
			for (AnnotationInstance annotation : method.declaredAnnotations()) {
				if (JarStereotypeScanner.metaAnnotationTypesOf(annotation.name(), type.index()).contains(Annotations.EVENT_LISTENER)) {
					String eventType = method.parametersCount() == 1 ? JdtStyleTypeNames.qualifiedName(method.parameterType(0)) : "";
					add(bean, type, method, eventType);
				}
			}
		}
	}

	/**
	 * {@code ComponentIndexer.indexEventListenerInterfaceImplementation}: an
	 * {@code ApplicationListener} anywhere in the hierarchy, and the class's own first
	 * {@code onApplicationEvent} - not the bridge method the compiler adds for the generic one.
	 */
	public static void addApplicationListener(Bean bean, JarType type) {
		if (!JarStereotypeScanner.supertypesOf(type.classInfo(), type.index()).contains(Annotations.APPLICATION_LISTENER)) {
			return;
		}

		for (MethodInfo method : JarStereotypeScanner.sourceLevelMethodsOf(type.classInfo())) {
			if (method.name().equals("onApplicationEvent")) {
				if (method.parametersCount() == 1) {
					add(bean, type, method, JdtStyleTypeNames.qualifiedName(method.parameterType(0)));
				}
				return;
			}
		}
	}

	private static void add(Bean bean, JarType type, MethodInfo method, String eventType) {
		EventListenerIndexElement element = new EventListenerIndexElement(eventType, type.placeholderLocation(),
				JdtStyleTypeNames.qualifiedName(type.classInfo().name()), DefaultValues.EMPTY_ANNOTATIONS, null);
		type.bindingKeys().put(element, JarBindingKeys.of(method));
		bean.addChild(element);
	}

}
