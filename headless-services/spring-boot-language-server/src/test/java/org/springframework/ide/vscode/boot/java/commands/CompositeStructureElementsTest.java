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
package org.springframework.ide.vscode.boot.java.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.api.Stereotypes;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * @author Martin Lippert
 */
public class CompositeStructureElementsTest {

	private final StereotypeMethodElement hostMethod = mock(StereotypeMethodElement.class);
	private final StereotypeMethodElement dependencyMethod = mock(StereotypeMethodElement.class);

	private final StereotypeClassElement hostType = type("com.example.app.HostType", hostMethod);
	private final StereotypeClassElement dependencyType = type("com.example.app.shared.SharedType", dependencyMethod);

	private final StructureElements host = part(hostType);
	private final StructureElements dependency = part(dependencyType);

	@Test
	void typesAreTheHostsFollowedByEachDependencysOnceEach() {
		StructureElements alsoContainingTheHostType = part(hostType, dependencyType);

		CompositeStructureElements composite = new CompositeStructureElements(host, List.of(dependency, alsoContainingTheHostType));

		assertEquals(List.of(hostType, dependencyType), composite.types());
	}

	@Test
	void whatDescribesTheWholeTreeComesFromTheHost() {
		StereotypePackageElement mainPackage = new StereotypePackageElement("com.example.app", null, true);
		StereotypePackageElement packageNode = new StereotypePackageElement("com.example.app.shared", null);
		when(host.mainApplicationPackage()).thenReturn(mainPackage);
		when(host.packageNode("com.example.app.shared")).thenReturn(packageNode);

		CompositeStructureElements composite = new CompositeStructureElements(host, List.of(dependency));

		assertSame(mainPackage, composite.mainApplicationPackage());
		assertSame(packageNode, composite.packageNode("com.example.app.shared"));
		verify(dependency, never()).mainApplicationPackage();
	}

	@Test
	void membersComeFromThePartTheTypeCameFrom() {
		List<StructureMember> members = List.of(new StructureMember("listener", null, null));
		when(dependency.membersOf(dependencyType)).thenReturn(members);

		CompositeStructureElements composite = new CompositeStructureElements(host, List.of(dependency));

		assertSame(members, composite.membersOf(dependencyType));
		verify(host, never()).membersOf(dependencyType);
	}

	@Test
	void methodLabelsComeFromThePartTheMethodCameFromWhateverTheContextualType() {
		when(dependency.methodLabel(dependencyMethod, hostType)).thenReturn("@/shared -- GET");

		CompositeStructureElements composite = new CompositeStructureElements(host, List.of(dependency));

		assertEquals("@/shared -- GET", composite.methodLabel(dependencyMethod, hostType));
	}

	@Test
	void stereotypesComeFromThePartTheElementCameFrom() {
		Stereotypes typeStereotypes = mock(Stereotypes.class);
		Stereotypes methodStereotypes = mock(Stereotypes.class);
		when(dependency.stereotypeFactory().fromType(dependencyType)).thenReturn(typeStereotypes);
		when(dependency.stereotypeFactory().fromMethod(dependencyMethod)).thenReturn(methodStereotypes);

		// a package the host does not have: nothing to add from there
		when(host.packageNode("com.example.app.shared")).thenReturn(new StereotypePackageElement("com.example.app.shared", null));

		CompositeStructureElements composite = new CompositeStructureElements(host, List.of(dependency));

		assertSame(typeStereotypes, composite.stereotypeFactory().fromType(dependencyType));
		assertSame(methodStereotypes, composite.stereotypeFactory().fromMethod(dependencyMethod));
	}

	@Test
	void bindingKeysComeFromThePartTheElementCameFrom() {
		when(dependency.bindingKeyOf(dependencyType)).thenReturn("Lcom/example/app/shared/SharedType;");
		when(dependency.bindingKeyOf(dependencyMethod)).thenReturn("Lcom/example/app/shared/SharedType;.handle()V");

		CompositeStructureElements composite = new CompositeStructureElements(host, List.of(dependency));

		assertEquals("Lcom/example/app/shared/SharedType;", composite.bindingKeyOf(dependencyType));
		assertEquals("Lcom/example/app/shared/SharedType;.handle()V", composite.bindingKeyOf(dependencyMethod));
		verify(host, never()).bindingKeyOf(dependencyType);
	}

	/**
	 * A part that shows only stereotyped types - a JAR dependency - has its types filtered by the
	 * composite, with the composite's own stereotype resolution: a stereotype of the type's own, one
	 * of the host's package of the same name, or one of its methods all count.
	 */
	@Test
	void aPartShowingOnlyStereotypedTypesIsFilteredWithTheCompositesStereotypes() {
		StereotypeMethodElement listenerMethod = mock(StereotypeMethodElement.class);
		StereotypeClassElement plain = type("com.example.app.Plain");
		StereotypeClassElement ownStereotype = type("com.example.app.Own");
		StereotypeClassElement hostPackageStereotype = type("com.example.app.stereotyped.ByPackage");
		StereotypeClassElement methodStereotype = type("com.example.app.WithListener", listenerMethod);

		Stereotypes some = stereotypes();

		StructureElements jar = part(plain, ownStereotype, hostPackageStereotype, methodStereotype);
		when(jar.showsOnlyStereotypedTypes()).thenReturn(true);
		when(jar.stereotypeFactory().fromType(any())).thenReturn(Stereotypes.NONE);
		when(jar.stereotypeFactory().fromType(ownStereotype)).thenReturn(some);
		when(jar.stereotypeFactory().fromMethod(any())).thenReturn(Stereotypes.NONE);
		when(jar.stereotypeFactory().fromMethod(listenerMethod)).thenReturn(some);

		StereotypePackageElement stereotypedPackage = new StereotypePackageElement("com.example.app.stereotyped", Set.of("com.example.Marker"));
		when(host.packageNode("com.example.app.stereotyped")).thenReturn(stereotypedPackage);
		when(host.packageNode("com.example.app")).thenReturn(new StereotypePackageElement("com.example.app", null));
		when(host.stereotypeFactory().fromPackage(stereotypedPackage)).thenReturn(some);

		CompositeStructureElements composite = new CompositeStructureElements(host, List.of(jar));

		assertEquals(List.of(hostType, ownStereotype, hostPackageStereotype, methodStereotype), composite.types());
	}

	private static Stereotypes stereotypes() {
		return new Stereotypes(List.of(mock(Stereotype.class)));
	}

	private static StereotypeClassElement type(String name, StereotypeMethodElement... methods) {
		StereotypeClassElement type = mock(StereotypeClassElement.class);
		when(type.getType()).thenReturn(name);
		when(type.getMethods()).thenReturn(List.of(methods));
		return type;
	}

	@SuppressWarnings("unchecked")
	private static StructureElements part(StereotypeClassElement... types) {
		StructureElements part = mock(StructureElements.class);
		StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> factory = mock(StereotypeFactory.class);
		when(part.types()).thenReturn(List.of(types));
		when(part.stereotypeFactory()).thenReturn(factory);
		return part;
	}

}
