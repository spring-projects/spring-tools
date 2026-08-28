/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.ARTIFACT_ID;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCIES;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCY;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCY_MANAGEMENT;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.GROUP_ID;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.childEquals;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChild;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChilds;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.getChild;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.ide.eclipse.boot.util.PomUtils.getArtifactId;
import static org.springframework.ide.eclipse.boot.util.PomUtils.getGroupId;
import static org.springframework.ide.eclipse.boot.util.PomUtils.getScope;
import static org.springframework.ide.eclipse.boot.util.PomUtils.getTextChild;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.MavenId;
import org.springframework.ide.eclipse.boot.core.SpringBootStarter;
import org.springframework.ide.eclipse.boot.core.SpringBootStarters;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springframework.ide.eclipse.boot.wizard.PopularityTracker;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@SuppressWarnings("restriction")
public class MavenStartersHarness {


	public void assertStarters(List<SpringBootStarter> starters, String... expectedIds) {
		Set<String> expecteds = new HashSet<>(Arrays.asList(expectedIds));
		for (SpringBootStarter starter : starters) {
			String id = starter.getId();
			if (expecteds.remove(id)) {
				//okay
			} else {
				fail("Unexpected starter found: "+starter);
			}
		}
		if (!expecteds.isEmpty()) {
			fail("Expected starters not found: "+expecteds);
		}
	}

	public void assertStarterDeps(List<Dependency> starters, String... expectedIds) {
		Set<String> expecteds = new HashSet<>(Arrays.asList(expectedIds));
		for (Dependency starter : starters) {
			String id = starter.getId();
			if (expecteds.remove(id)) {
				//okay
			} else {
				fail("Unexpected starter found: "+starter);
			}
		}
		if (!expecteds.isEmpty()) {
			fail("Expected starters not found: "+expecteds);
		}
	}

	/**
	 * Deps are string in this format "<gid>:<aid>@<scope>". The '@<scope>' part can be omited
	 * to indicate a dependency with default scope.
	 */
	public void assertMavenDeps(IProject project, String... _expectedDeps) throws IOException, CoreException {
		IDOMDocument pom = parsePom(project);
		Element depsEl = findChild(pom.getDocumentElement(), DEPENDENCIES);
		List<Element> depNodes = findChilds(depsEl, DEPENDENCY);
		List<String> actualDeps = new ArrayList<>();
		for (Element depEl : depNodes) {
			String dep = getGroupId(depEl) + ":" + getArtifactId(depEl);
			String scope = getScope(depEl) ;
			if (scope != null) {
				dep = dep + "@" + scope;
			}
			actualDeps.add(dep);
		}

		Collections.sort(actualDeps);
		List<String> expectedDeps = new ArrayList<>(Arrays.asList(_expectedDeps));
		Collections.sort(expectedDeps);

		assertEquals(onePerLine(expectedDeps), onePerLine(actualDeps));
	}

	public void assertBoms(IProject project, String... _expectedDeps) throws IOException, CoreException {
		IDOMDocument pom = parsePom(project);
		Element depsEl = getChild(pom.getDocumentElement(), DEPENDENCY_MANAGEMENT, DEPENDENCIES);
		List<Element> depNodes = findChilds(depsEl, DEPENDENCY);
		List<String> actualDeps = new ArrayList<>();
		for (Element depEl : depNodes) {
			String dep = getGroupId(depEl) + ":" + getArtifactId(depEl);
			if ("import".equals(getTextChild(depEl, "scope")) && "pom".equals(getTextChild(depEl, "type"))) {
				actualDeps.add(dep);
			}
		}
		Collections.sort(actualDeps);
		List<String> expectedDeps = new ArrayList<>(Arrays.asList(_expectedDeps));
		Collections.sort(expectedDeps);

		assertEquals(onePerLine(expectedDeps), onePerLine(actualDeps));

	}

	public IDOMDocument parsePom(IProject project) throws IOException, CoreException {
		return ((IDOMModel) StructuredModelManager.getModelManager().createUnManagedStructuredModelFor(project.getFile("pom.xml"))).getDocument();
	}

	public static Element findDependency(Document document, MavenId dependency) {
		Element dependenciesElement = findChild(document.getDocumentElement(), DEPENDENCIES);
		return findChild(dependenciesElement, DEPENDENCY, childEquals(GROUP_ID, dependency.getGroupId()),
				childEquals(ARTIFACT_ID, dependency.getArtifactId()));
	}

	public void assertUsageCounts(ISpringBootProject project, PopularityTracker popularities, String... idAndCount) throws Exception {
		Map<String, Integer> expect = new HashMap<>();
		for (String pair : idAndCount) {
			String[] pieces = pair.split(":");
			assertEquals(2, pieces.length);
			String id = pieces[0];
			int count = Integer.parseInt(pieces[1]);
			expect.put(id, count);
		}


		List<SpringBootStarter> knownStarters = project.getKnownStarters();
		assertFalse(knownStarters.isEmpty());
		for (SpringBootStarter starter : knownStarters) {
			String id = starter.getId();
			Integer expectedCountOrNull = expect.get(id);
			int expectedCount = expectedCountOrNull == null ? 0 : expectedCountOrNull;
			assertEquals("Usage count for '"+id+"'", expectedCount, popularities.getUsageCount(id));
			expect.remove(id);
		}

		assertTrue("Expected usage counts not found: "+expect, expect.isEmpty());
	}

	public Element findDependency(ISpringBootProject project, Document pom, String id) {
		try {
			SpringBootStarters starters = project.getStarterInfos();
			MavenId mid = starters.getMavenId(id);
			if (mid != null) {
				return findDependency(pom, mid);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	private String onePerLine(List<String> strings) {
		StringBuilder builder = new StringBuilder();
		for (String string : strings) {
			builder.append(string);
			builder.append('\n');
		}
		return builder.toString();
	}

	public String generateFakePom(SpringBootStarters knownStarters, List<String> starters) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		Document pom = dbf.newDocumentBuilder().newDocument();
		Element rootElement = pom.createElement("project");
		pom.appendChild(rootElement);

		Element depsEl = pom.createElement(DEPENDENCIES);
		rootElement.appendChild(depsEl);

		for (String starterId : starters) {
			SpringBootStarter starter = knownStarters.getStarter(starterId);
			Element dep = pom.createElement(DEPENDENCY);
			depsEl.appendChild(dep);

			{
				Element gid = pom.createElement(GROUP_ID);
				gid.appendChild(pom.createTextNode(starter.getGroupId()));
				dep.appendChild(gid);
			}
			{
				Element aid = pom.createElement(ARTIFACT_ID);
				aid.appendChild(pom.createTextNode(starter.getArtifactId()));
				dep.appendChild(aid);
			}
		}

		Transformer tf = TransformerFactory.newInstance().newTransformer();
		StringWriter stringWriter = new StringWriter();
		tf.transform(new DOMSource(pom), new StreamResult(stringWriter));
		return stringWriter.toString();
	}

}
