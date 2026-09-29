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
package org.springframework.ide.vscode.boot.java.beans;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.DotName;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.data.JarDataRepositoryScanner;
import org.springframework.ide.vscode.boot.java.requestmapping.JarRequestMappingScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;

/**
 * The JAR/bytecode counterpart of {@link ComponentIndexer}'s decisions: which beans a scanned class
 * contributes, and which children - request mappings, query methods, properties, ... - each gets.
 * Those children are what the structure tree shows as a type's members, exactly as it shows the
 * children of the live index's beans for a workspace project
 * ({@code StructureViewUtil.membersOf}). See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Mirrors {@link ComponentIndexer#index}: a repository gets a bean; otherwise a component (a
 * class meta-annotated with {@code @Component} or {@code @Named}) does; either is then
 * post-processed like {@code postProcessComponent}, children added in the same order. A class that is
 * neither can still get a bean of its own as a {@code @ConfigurationProperties} class, as on the
 * AST side. Records follow {@code ComponentIndexer}'s record path: properties only. The AST side
 * attaches other members of a non-bean class (its {@code @Bean} methods, listeners, AI methods) to
 * containers the tree never reads, so they are not produced here either.
 *
 * @author Martin Lippert
 */
public class JarBeanIndexer {

	private static final DotName FEIGN_CLIENT = DotName.createSimple(Annotations.FEIGN_CLIENT);

	public static List<Bean> beansOf(JarType type) {
		List<Bean> result = new ArrayList<>();

		// SpringIndexerJava never hands an enum declaration to the bean indexers
		if (type.classInfo().isEnum()) {
			return result;
		}

		if (type.classInfo().isRecord()) {
			if (isComponent(type) || JarConfigurationPropertiesScanner.isConfigurationProperties(type)) {
				Bean bean = bean(type);
				if (JarConfigurationPropertiesScanner.isConfigurationProperties(type)) {
					JarConfigurationPropertiesScanner.addConfigurationProperties(bean, type);
				}
				result.add(bean);
			}
			return result;
		}

		Bean bean = null;

		if (JarDataRepositoryScanner.isRepository(type)) {
			bean = bean(type);
			JarDataRepositoryScanner.addQueryMethods(bean, type);
		}

		if (bean == null && isComponent(type)) {
			bean = bean(type);
		}

		if (bean != null) {
			postProcessComponent(bean, type);
			result.add(bean);
		}
		else if (JarConfigurationPropertiesScanner.isConfigurationProperties(type)) {
			Bean propertiesBean = bean(type);
			JarConfigurationPropertiesScanner.addConfigurationProperties(propertiesBean, type);
			result.add(propertiesBean);
		}

		// FeignClientIndexer: a bean of its own for a directly annotated Feign client, with its
		// request mappings, next to whatever else the class is
		if (type.classInfo().declaredAnnotation(FEIGN_CLIENT) != null) {
			Bean feignClient = bean(type);
			JarRequestMappingScanner.addRequestMappings(feignClient, type);
			result.add(feignClient);
		}

		return result;
	}

	/**
	 * {@code ComponentIndexer.postProcessComponent}'s children, in its order.
	 */
	private static void postProcessComponent(Bean bean, JarType type) {
		JarRequestMappingScanner.addRequestMappings(bean, type);
		if (JarConfigurationPropertiesScanner.isConfigurationProperties(type)) {
			JarConfigurationPropertiesScanner.addConfigurationProperties(bean, type);
		}
	}

	public static boolean isComponent(JarType type) {
		return type.ownAnnotationTypes().contains(Annotations.COMPONENT)
				|| type.ownAnnotationTypes().contains(Annotations.NAMED_JAKARTA)
				|| type.ownAnnotationTypes().contains(Annotations.NAMED_JAVAX);
	}

	public static boolean isConfiguration(JarType type) {
		return type.ownAnnotationTypes().contains(Annotations.CONFIGURATION);
	}

	private static Bean bean(JarType type) {
		String simpleName = JdtStyleTypeNames.simpleName(type.classInfo().name());
		return new Bean(BeanUtils.getBeanNameFromType(simpleName), type.element().getType(), type.placeholderLocation(),
				DefaultValues.EMPTY_INJECTION_POINTS, JarStereotypeScanner.supertypesOf(type.classInfo(), type.index()),
				DefaultValues.EMPTY_ANNOTATIONS, isConfiguration(type), simpleName);
	}

}
