/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.refactoring.test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.assertContains;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.createFile;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.refactoring.ConvertYamlToPropertiesRefactoring;
import org.springframework.ide.eclipse.boot.refactoring.test.ConvertPropertiesToYamlRefactoringTest.Checker;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

public class ConvertYamlToPropertiesRefactoringTest {

	BootProjectTestHarness projects = new BootProjectTestHarness(getWorkspace());

	protected IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	@Before public void setup() throws Exception {
		StsTestUtil.deleteAllProjects();
	}

	private void do_conversionTest(String input, String expectedOutput) throws Exception {
		do_conversionTest(input, expectedOutput, (status) -> {
			assertEquals(RefactoringStatus.OK, status.getSeverity());
		});
	}

	private void do_conversionTest(String input, String expectedOutput, Checker<RefactoringStatus> statusChecker) throws Exception {
		IProject project = projects.createProject("conversionTestProject");
		IFile yamlFile = createFile(project, "application.yml", input);
		IFile propsFile = project.getFile("application.properties");
		if (propsFile.exists()) {
			propsFile.delete(true, new NullProgressMonitor());
		}

		assertTrue(yamlFile.exists());
		assertFalse(propsFile.exists());

		ConvertYamlToPropertiesRefactoring refactoring = new ConvertYamlToPropertiesRefactoring(yamlFile);
		statusChecker.check(refactoring.checkAllConditions(new NullProgressMonitor()));
		perform(refactoring);

		assertFalse(yamlFile.exists());
		assertTrue(propsFile.exists());

		assertEquals(expectedOutput, IOUtil.toString(propsFile.getContents()));
	}

	private void assertFile(IProject project, String path, String expectedContents) throws Exception {
		IFile file = project.getFile(path);
		assertTrue(file.getFullPath().toString(), file.exists());
		assertEquals(expectedContents, IOUtil.toString(file.getContents()));
	}

	private void perform(ConvertYamlToPropertiesRefactoring refactoring) throws Exception {
		Change change = refactoring.createChange(new NullProgressMonitor());
		IWorkspace workspace = getWorkspace();
		CompletableFuture<Void> result = new CompletableFuture<>();
		Job job = new Job(refactoring.getName()) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					workspace.run(new PerformChangeOperation(change), monitor);
					result.complete(null);
				} catch (Throwable e) {
					result.completeExceptionally(e);
				}
				return Status.OK_STATUS;
			}
		};
		job.setRule(workspace.getRuleFactory().buildRule());
		job.schedule();
		result.get();
	}

	private ConvertYamlToPropertiesRefactoring do_hasComments_test(IProject project, String comment) throws Exception {
		IFile yamlFile = createFile(project, "src/main/resources/application.yml",
				"some:\n  property: somevalue\n"+
				comment +"\n" +
				"other:\n  property: othervalue"
		);
		ConvertYamlToPropertiesRefactoring refactoring = new ConvertYamlToPropertiesRefactoring(yamlFile);
		assertOkStatus(refactoring.checkInitialConditions(new NullProgressMonitor()));
		assertStatus(refactoring.checkFinalConditions(new NullProgressMonitor()), RefactoringStatus.WARNING, "has comments, which will be lost");
		return refactoring;
	}

	private ConvertYamlToPropertiesRefactoring do_hasInlineComments_test(IProject project, String comment) throws Exception {
		IFile yamlFile = createFile(project, "src/main/resources/application.yml",
				"some:\n  property: somevalue\n"+
				"foo: bar " + comment +"\n" +
				"other:\n  property: othervalue"
		);
		ConvertYamlToPropertiesRefactoring refactoring = new ConvertYamlToPropertiesRefactoring(yamlFile);
		assertOkStatus(refactoring.checkInitialConditions(new NullProgressMonitor()));
		assertStatus(refactoring.checkFinalConditions(new NullProgressMonitor()), RefactoringStatus.WARNING, "has comments, which will be lost");
		return refactoring;
	}

	private void assertOkStatus(RefactoringStatus s) {
		assertEquals(RefactoringStatus.OK, s.getSeverity());
	}

	private void assertStatus(RefactoringStatus status, int expectedSeverity, String expectedMessageFragment) {
		assertEquals(expectedSeverity, status.getSeverity());
		StringBuilder allMessages = new StringBuilder();
		for (RefactoringStatusEntry entry : status.getEntries()) {
			allMessages.append(entry.getMessage());
			allMessages.append("\n-------------\n");
		}
		assertContains(expectedMessageFragment, allMessages.toString());
	}

	@Test public void fileIsInTheWay() throws Exception {
		IProject project = projects.createBootProject("fileIsInTheWay");
		IFile yamlFile = createFile(project, "src/main/resources/application.yml", "someting: already-in-here");
		IFile propsFile = project.getFile("src/main/resources/application.properties");
		assertTrue(propsFile.exists());
		ConvertYamlToPropertiesRefactoring refactoring = new ConvertYamlToPropertiesRefactoring(yamlFile);

		RefactoringStatus status = refactoring.checkInitialConditions(new NullProgressMonitor());
		assertStatus(status, RefactoringStatus.FATAL, "'/fileIsInTheWay/src/main/resources/application.properties' already exists");
	}

	@Test public void hasLineComments() throws Exception {
		IProject project = projects.createBootProject("hasComments");
		IFile propsFile = project.getFile("src/main/resources/application.properties");
		if (propsFile.exists()) {
			propsFile.delete(true, new NullProgressMonitor());
		}

		do_hasComments_test(project, "#comment");
		ConvertYamlToPropertiesRefactoring refactoring = do_hasComments_test(project, "    #!comment");

		perform(refactoring); //Despite the warning, the refactoring should be executable.
		assertFalse(project.getFile("src/main/resources/application.yml").exists());
		assertFile(project, "src/main/resources/application.properties",
				"some.property=somevalue\n" +
				"other.property=othervalue\n"
		);
	}

	@Test public void hasInlineComments() throws Exception {
		IProject project = projects.createBootProject("hasComments");
		IFile propsFile = project.getFile("src/main/resources/application.properties");
		if (propsFile.exists()) {
			propsFile.delete(true, new NullProgressMonitor());
		}

		do_hasInlineComments_test(project, "#comment");
		ConvertYamlToPropertiesRefactoring refactoring = do_hasInlineComments_test(project, "    #!comment");

		perform(refactoring); //Despite the warning, the refactoring should be executable.
		assertFalse(project.getFile("src/main/resources/application.yml").exists());
		assertFile(project, "src/main/resources/application.properties",
				"some.property=somevalue\n" +
				"foo=bar\n" +
				"other.property=othervalue\n"
		);
	}

	@Test public void almostHasComments() throws Exception {
		do_conversionTest(
			"my:\n" +
			"  goodbye: 'See ya # later'\n" +
			"  hello: Good morning!\n"
			, // == >
			"my.goodbye=See ya \\# later\n" +
			"my.hello=Good morning\\!\n"
		);
	}

	@Test public void conversionWithListItems() throws Exception {
		do_conversionTest(
				"some:\n" +
				"  thing:\n" +
				"  - a: first-a\n" +
				"    b: first-b\n" +
				"  - a: second-a\n" +
				"    b: second-b\n"
				, // == >
				"some.thing[0].a=first-a\n" +
				"some.thing[0].b=first-b\n" +
				"some.thing[1].a=second-a\n" +
				"some.thing[1].b=second-b\n"
		);
	}

	@Test public void simpleConversion() throws Exception {
		do_conversionTest(
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n"
				, // == >
				"some.other.thing=blah\n" +
				"some.thing=vvvv\n"
		);
	}

	@Test public void emptyFileConversion() throws Exception {
		do_conversionTest(
				""
				, // == >
				""
		);
	}

	@Test public void unusualName() throws Exception {
		IProject project = projects.createProject("unusualName");
		IFile input = createFile(project, "no-extension",
				"server:\n  port: 6789\n"
		);
		ConvertYamlToPropertiesRefactoring refactoring = new ConvertYamlToPropertiesRefactoring(input);
		assertOkStatus(refactoring.checkAllConditions(new NullProgressMonitor()));
		perform(refactoring);
		assertEquals(
			"server.port=6789\n",
			IOUtil.toString(project.getFile("no-extension.properties").getContents())
		);
	}

	@Test public void list() throws Exception {
		do_conversionTest(
				"some:\n" +
				"  property:\n" +
				"  - something\n" +
				"  - something-else\n"
				, // == >
				"some.property[0]=something\n" +
				"some.property[1]=something-else\n"
		);
	}

	@Test public void mapAndSequenceConflict() throws Exception {
		do_conversionTest(
				"some:\n" +
				"  property:\n" +
				"    '0': zero\n" +
				"    '1': one\n" +
				"    abc: val1\n" +
				"    def: val2\n"
				,
				"some.property.0=zero\n" +
				"some.property.1=one\n" +
				"some.property.abc=val1\n" +
				"some.property.def=val2\n"
		);
	}

	@Test public void multipleDocsConversion() throws Exception {
		do_conversionTest(
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n" +
				"\n" +
				"---\n" +
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n" +
				"\n" +
				"---\n" +
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n"
				, // == >
				"some.other.thing=blah\n" +
				"some.thing=vvvv\n" +
				"#---\n" +
				"some.other.thing=blah\n" +
				"some.thing=vvvv\n" +
				"#---\n" +
				"some.other.thing=blah\n" +
				"some.thing=vvvv\n"
		);
	}


}
