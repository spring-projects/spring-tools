/*******************************************************************************
 * Copyright (c) 2007, 2011, 2016, 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Pivotal Inc. - copied from JDT and adapted for use in STS
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.templates;

import java.util.function.Function;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.java.AbstractTemplateCompletionProposalComputer;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager;

/**
 * Computer that computes the template proposals for the 'Boot' context type.
 *
 * @since 3.8.0
 */
@SuppressWarnings("restriction")
public class BootTemplateCompletionProposalComputer extends AbstractTemplateCompletionProposalComputer {

	/**
	 * The name of a type associated with this computer. This computer is only active when
	 * the given type is found on a projects's classpath.
	 */
	private static final String CONTEXT_TYPE_NAME= "org.springframework.boot.autoconfigure.SpringBootApplication"; //$NON-NLS-1$

	/**
	 * Engine used to compute the proposals for this computer
	 */
	private final TemplateEngine fAllTemplateEngine;
	private final TemplateEngine fMembersTemplateEngine;
	private final TemplateEngine fStatementsTemplateEngine;

	/**
	 * The Java project of the compilation unit for which a template
	 * engine has been computed last time if any
	 */
	private IJavaProject fCachedJavaProject;
	/**
	 * Is the 'context type' on class path of <code>fJavaProject</code>. Invalid
	 * if <code>fJavaProject</code> is <code>false</code>.
	 */
	private boolean fIsContextTypeOnClasspath;

	public BootTemplateCompletionProposalComputer() {
		// the registry type differs between Eclipse versions (jface.text.templates vs. text.templates), so don't name it
		Function<String, TemplateContextType> templateContextRegistry= JavaPlugin.getDefault().getTemplateContextRegistry()::getContextType;
		fAllTemplateEngine= createTemplateEngine(templateContextRegistry, BootContextType.ID_ALL);
		fMembersTemplateEngine= createTemplateEngine(templateContextRegistry, BootContextType.ID_MEMBERS);
		fStatementsTemplateEngine= createTemplateEngine(templateContextRegistry, BootContextType.ID_STATEMENTS);
		new ClasspathListenerManager((IJavaProject javaProject) -> {
			setCachedJavaProject(null);
		});
	}

	private static TemplateEngine createTemplateEngine(Function<String, TemplateContextType> templateContextRegistry, String contextTypeId) {
		TemplateContextType contextType= templateContextRegistry.apply(contextTypeId);
		Assert.isNotNull(contextType);
		return new TemplateEngine(contextType);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.java.TemplateCompletionProposalComputer#computeCompletionEngine(org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext)
	 */
	@Override
	protected TemplateEngine computeCompletionEngine(JavaContentAssistInvocationContext context) {
		ICompilationUnit unit= context.getCompilationUnit();
		if (unit == null)
			return null;

		IJavaProject javaProject= unit.getJavaProject();
		if (javaProject == null)
			return null;

		if (isContextTypeOnClasspath(javaProject)) {
			CompletionContext coreContext= context.getCoreContext();
			if (coreContext != null) {
				int tokenLocation= coreContext.getTokenLocation();
				if ((tokenLocation & CompletionContext.TL_MEMBER_START) != 0) {
					return fMembersTemplateEngine;
				}
				if ((tokenLocation & CompletionContext.TL_STATEMENT_START) != 0) {
					return fStatementsTemplateEngine;
				}
			}
			return fAllTemplateEngine;
		}

		return null;
	}

	/**
	 * Tells whether 'context type' is on the given project's class path.
	 *
	 * @param javaProject the Java project
	 * @return <code>true</code> if the given project's class path
	 */
	private synchronized boolean isContextTypeOnClasspath(IJavaProject javaProject) {
		if (!javaProject.equals(fCachedJavaProject)) {
			fCachedJavaProject= javaProject;
			try {
				IType type= javaProject.findType(CONTEXT_TYPE_NAME);
				fIsContextTypeOnClasspath= type != null;
			} catch (JavaModelException e) {
				fIsContextTypeOnClasspath= false;
			}
		}
		return fIsContextTypeOnClasspath;
	}

	/**
	 * Set the cached Java project.
	 *
	 * @param project or <code>null</code> to reset the cache
	 */
	private synchronized void setCachedJavaProject(IJavaProject project) {
		fCachedJavaProject= project;
	}

}
