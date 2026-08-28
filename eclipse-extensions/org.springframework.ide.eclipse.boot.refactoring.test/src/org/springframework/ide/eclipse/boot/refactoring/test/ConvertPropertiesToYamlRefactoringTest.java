/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
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
import org.springframework.ide.eclipse.boot.refactoring.ConvertPropertiesToYamlRefactoring;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

public class ConvertPropertiesToYamlRefactoringTest {

	public interface Checker<T> {
		void check(T it) throws Exception;
	}

	BootProjectTestHarness projects = new BootProjectTestHarness(getWorkspace());

	protected IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	@Before public void setup() throws Exception {
		StsTestUtil.deleteAllProjects();
	}

	@Test public void fileIsInTheWay() throws Exception {
		IProject project = projects.createBootProject("fileIsInTheWay");
		createFile(project, "src/main/resources/application.yml", "someting: already-in-here");
		IFile propsFile = project.getFile("src/main/resources/application.properties");
		assertTrue(propsFile.exists());
		ConvertPropertiesToYamlRefactoring refactoring = new ConvertPropertiesToYamlRefactoring(propsFile);

		RefactoringStatus status = refactoring.checkInitialConditions(new NullProgressMonitor());
		assertStatus(status, RefactoringStatus.FATAL, "'/fileIsInTheWay/src/main/resources/application.yml' already exists");
	}

	@Test public void hasComments() throws Exception {
		IProject project = projects.createBootProject("hasComments");
		do_hasComments_test(project, "#comment");
		do_hasComments_test(project, "!comment");
		do_hasComments_test(project, "    \t!comment");
		ConvertPropertiesToYamlRefactoring refactoring = do_hasComments_test(project, "    #!comment");

		perform(refactoring); //Despite the warning, the refactoring should be executable.
		assertFalse(project.getFile("src/main/resources/application.properties").exists());
		assertFile(project, "src/main/resources/application.yml",
				"other:\n" +
				"  property: othervalue\n" +
				"some:\n" +
				"  property: somevalue\n"
		);
	}

	@Test public void almostHasComments() throws Exception {
		do_conversionTest(
			"my.hello=Good morning!\n" +
			"my.goodbye=See ya # later\n"
			, // == >
			"my:\n" +
			"  goodbye: 'See ya # later'\n" +
			"  hello: Good morning!\n"
		);
	}


	@Test public void conversionWithListItems() throws Exception {
		do_conversionTest(
				"some.thing[0].a=first-a\n" +
				"some.thing[0].b=first-b\n" +
				"some.thing[1].a=second-a\n" +
				"some.thing[1].b=second-b\n"
				, // == >
				"some:\n" +
				"  thing:\n" +
				"  - a: first-a\n" +
				"    b: first-b\n" +
				"  - a: second-a\n" +
				"    b: second-b\n"
		);
	}

	@Test public void simpleConversion() throws Exception {
		do_conversionTest(
				"some.thing=vvvv\n" +
				"some.other.thing=blah\n"
				, // == >
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n"
		);
	}

	@Test public void nonStringyValueConversion() throws Exception {
		//See: https://www.pivotaltracker.com/story/show/154181583
		//Test that we do not add unnecessary quotes around certain types of values.
		do_conversionTest(
				"exponated=123.4E-12\n" +
				"server.port=8888\n" +
				"foobar.enabled=true\n" +
				"foobar.nice=false\n" +
				"fractional=0.78\n" +
				"largenumber=989898989898989898989898989898989898989898989898989898989898\n" +
				"longfractional=-0.989898989898989898989898989898989898989898989898989898989898\n"
				, // == >
				"exponated: '123.4E-12'\n" + //quotes are added because conversion to number changes the string value
				"foobar:\n" +
				"  enabled: true\n" +
				"  nice: false\n" +
				"fractional: 0.78\n" +
				"largenumber: 989898989898989898989898989898989898989898989898989898989898\n" +
				"longfractional: -0.989898989898989898989898989898989898989898989898989898989898\n" +
				"server:\n" +
				"  port: 8888\n"
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
				"server.port: 6789"
		);
		ConvertPropertiesToYamlRefactoring refactoring = new ConvertPropertiesToYamlRefactoring(input);
		assertOkStatus(refactoring.checkAllConditions(new NullProgressMonitor()));
		perform(refactoring);
		assertEquals(
			"server:\n" +
			"  port: 6789\n"
			,
			IOUtil.toString(project.getFile("no-extension.yml").getContents())
		);
	}

	@Test public void nonExistentInput() throws Exception {
		IProject project = projects.createProject("nonExistentInput");
		IFile input = project.getFile("doesnotexist.properties");
		ConvertPropertiesToYamlRefactoring refactoring = new ConvertPropertiesToYamlRefactoring(input);
		assertStatus(refactoring.checkInitialConditions(new NullProgressMonitor()),
				RefactoringStatus.FATAL, "is not accessible");
	}

	@Test public void multipleAssignmentProblem() throws Exception {
		do_conversionTest(
				"some.property=something\n" +
				"some.property=something-else"
				, // == >
				"some:\n" +
				"  property:\n" +
				"  - something\n" +
				"  - something-else\n"
 				, (status) -> {
					assertStatus(status, RefactoringStatus.WARNING, "Multiple values [something, something-else] assigned to 'some.property'.");
				}
		);
	}

	@Test public void scalarAndMapConflict() throws Exception {
		do_conversionTest(
				"some.property=a-scalar\n" +
				"some.property.sub=sub-value"
				,
				"some:\n" +
				"  property:\n" +
				"    sub: sub-value\n"
				, (status) -> {
					assertStatus(status, RefactoringStatus.ERROR, "Direct assignment 'some.property=a-scalar' can not be combined with sub-property assignment 'some.property.sub...'");
				}
		);
	}

	@Test public void scalarAndSequenceConflict() throws Exception {
		do_conversionTest(
				"some.property=a-scalar\n" +
				"some.property[0]=zero\n" +
				"some.property[1]=one\n"
				,
				"some:\n" +
				"  property:\n" +
				"  - zero\n" +
				"  - one\n"
				, (status) -> {
					assertStatus(status, RefactoringStatus.ERROR, "Direct assignment 'some.property=a-scalar' can not be combined with sequence assignment 'some.property[0]...'");
				}
		);
	}

	@Test public void mapAndSequenceConflict() throws Exception {
		do_conversionTest(
				"some.property.abc=val1\n" +
				"some.property.def=val2\n" +
				"some.property[0]=zero\n" +
				"some.property[1]=one\n"
				,
				"some:\n" +
				"  property:\n" +
				"    '0': zero\n" +
				"    '1': one\n" +
				"    abc: val1\n" +
				"    def: val2\n"
				, (status) -> {
					assertStatus(status, RefactoringStatus.WARNING, "'some.property' has some entries that look like list items and others that look like map entries");
				}
		);
	}

	@Test public void scalarAndMapAndSequenceConflict() throws Exception {
		do_conversionTest(
				"some.property=a-scalar\n" +
				"some.property.abc=val1\n" +
				"some.property.def=val2\n" +
				"some.property[0]=zero\n" +
				"some.property[1]=one\n"
				,
				"some:\n" +
				"  property:\n" +
				"    '0': zero\n" +
				"    '1': one\n" +
				"    abc: val1\n" +
				"    def: val2\n"
				, (status) -> {
					assertStatus(status, RefactoringStatus.ERROR, "Direct assignment 'some.property=a-scalar' can not be combined with sub-property assignment 'some.property.abc...'. ");
					assertStatus(status, RefactoringStatus.ERROR, "'some.property' has some entries that look like list items and others that look like map entries");
				}
		);
	}

	private void do_conversionTest(String input, String expectedOutput) throws Exception {
		do_conversionTest(input, expectedOutput, (status) -> {
			assertEquals(RefactoringStatus.OK, status.getSeverity());
		});
	}

	private void do_conversionTest(String input, String expectedOutput, Checker<RefactoringStatus> statusChecker) throws Exception {
		IProject project = projects.createProject("conversionTestProject");
		IFile propertiesFile = createFile(project, "application.properties", input);
		IFile yamlFile = project.getFile("application.yml");
		assertTrue(propertiesFile.exists());
		assertFalse(yamlFile.exists());

		ConvertPropertiesToYamlRefactoring refactoring = new ConvertPropertiesToYamlRefactoring(propertiesFile);
		statusChecker.check(refactoring.checkAllConditions(new NullProgressMonitor()));
		perform(refactoring);

		assertFalse(propertiesFile.exists());
		assertTrue(yamlFile.exists());

		assertEquals(expectedOutput, IOUtil.toString(yamlFile.getContents()));
	}

	private void assertFile(IProject project, String path, String expectedContents) throws Exception {
		IFile file = project.getFile(path);
		assertTrue(file.getFullPath().toString(), file.exists());
		assertEquals(expectedContents, IOUtil.toString(file.getContents()));
	}

	private void perform(ConvertPropertiesToYamlRefactoring refactoring) throws Exception {
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

	private ConvertPropertiesToYamlRefactoring do_hasComments_test(IProject project, String comment) throws Exception {
		IFile propsFile = createFile(project, "src/main/resources/application.properties",
				"some.property=somevalue\n"+
				comment +"\n" +
				"other.property=othervalue"
		);
		ConvertPropertiesToYamlRefactoring refactoring = new ConvertPropertiesToYamlRefactoring(propsFile);
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
}
