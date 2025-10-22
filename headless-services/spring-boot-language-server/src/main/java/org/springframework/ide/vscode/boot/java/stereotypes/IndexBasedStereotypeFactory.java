/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.api.Stereotypes;
import org.jmolecules.stereotype.catalog.StereotypeDefinition.Assignment;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.StereotypeDetector.AnalysisLevel;
import org.jmolecules.stereotype.catalog.support.StereotypeMatcher;
import org.springframework.ide.vscode.boot.java.commands.CachedSpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class IndexBasedStereotypeFactory implements StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {
	
	private final AbstractStereotypeCatalog catalog;
	private final IJavaProject project;

	private final CachedSpringMetamodelIndex springIndex;
	
	private static final StereotypeMatcher<StereotypeClassElement, StereotypeAnnotatedElement> STEREOTYPE_MATCHER = StereotypeMatcher
			.<StereotypeClassElement, StereotypeAnnotatedElement> isAnnotatedWith((element, fqn) -> isAnnotated(element, fqn))
			.orImplements((type, fqn) -> doesImplement(type, fqn));


	public IndexBasedStereotypeFactory(AbstractStereotypeCatalog catalog, IJavaProject project, CachedSpringMetamodelIndex springIndex) {
		this.catalog = catalog;
		this.project = project;
		this.springIndex = springIndex;
	}
	
	@Override
	public Stereotypes fromPackage(StereotypePackageElement pkg) {
		return new Stereotypes(fromAnnotatedElement(pkg, AnalysisLevel.DIRECT));
	}

	@Override
	public Stereotypes fromType(StereotypeClassElement type) {
		return new Stereotypes(fromTypeInternal(type, AnalysisLevel.DIRECT))
				.and(fromPackage(findPackageFor(type)));
	}

	@Override
	public Stereotypes fromMethod(StereotypeMethodElement method) {
		return new Stereotypes(fromAnnotatedElement(method, AnalysisLevel.DIRECT));
	}
	
	public void registerStereotypeDefinitions() {
		springIndex.getNodesOfType(project.getElementName(), StereotypeDefinitionElement.class).stream()
			.forEach(element -> registerStereotype(element));
	}
	
	private <T extends StereotypeAnnotatedElement> Collection<Stereotype> fromAnnotatedElement(StereotypeAnnotatedElement element, AnalysisLevel level) {

		var result = new ArrayList<Stereotype>();

		if (element != null) {
			result.addAll(catalog.getAnnotationBasedStereotypes(element, level, STEREOTYPE_MATCHER));
	
	//		for (Annotation annotation : element.getAnnotations()) {
	//			for (Class<?> type : fromAnnotation(annotation)) {
	//				result.add(registerStereotypeDefinition(type, Type.IS_ANNOTATED));
	//			}
	//		}
		}

		return result;
	}
	
	private Collection<Stereotype> fromTypeInternal(StereotypeClassElement type, AnalysisLevel level) {

		var result = new TreeSet<Stereotype>();

		result.addAll(catalog.getTypeBasedStereotypes(type, level, STEREOTYPE_MATCHER));

//		if (type.isAnnotation()) {
//
//			result.addAll(fromAnnotatedElement(type));
//
//			return result;
//		}
//
//		var candidates = type.getInterfaces();
//
//		for (Class<?> candidate : candidates) {
//			if (candidate.getAnnotation(STEREOTYPE_ANNOTATION) != null) {
//				result.add(registerStereotypeDefinition(candidate, Type.IS_ANNOTATED));
//			}
//		}
//
//		for (Class<?> candidate : candidates) {
//			result.addAll(fromTypeInternal(candidate));
//		}
//
//		var superType = type.getSuperclass();
//
//		if (superType != null && !Object.class.equals(superType)) {
//			result.addAll(fromTypeInternal(type.getSuperclass()));
//		}
//
//		if (!type.isAnnotation()) {
			result.addAll(fromAnnotatedElement(type, level));
//		}

		return result;
	}
	
	private static boolean isAnnotated(StereotypeAnnotatedElement element, String fqn) {
		return element.isAnnotatedWith(fqn);
	}
	
	private static boolean doesImplement(StereotypeClassElement type, String fqn) {
		return type.doesImplement(fqn);
	}
	
	private StereotypePackageElement findPackageFor(StereotypeClassElement type) {
		int index = type.getType().lastIndexOf('.');
		if (index >= 0) {
			String packageName = type.getType().substring(0, index);
			return springIndex.findPackageNode(packageName, this.project.getElementName());
		}
		return null;
	}
	
	private void registerStereotype(StereotypeDefinitionElement element) {

		var stereotype = element.createStereotype();
		var type = element.getType();
		var assignment = element.getAssignment();
		var location = element.getLocation();
		
		catalog.getOrRegister(stereotype, Assignment.of(type, assignment), location).getStereotype();
	}

}
