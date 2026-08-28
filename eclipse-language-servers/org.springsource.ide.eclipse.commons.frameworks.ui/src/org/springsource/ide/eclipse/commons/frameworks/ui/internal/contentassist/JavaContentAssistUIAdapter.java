/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.ui.internal.contentassist;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.StubTypeContext;
import org.eclipse.jdt.internal.corext.refactoring.TypeContextChecker;
import org.eclipse.jdt.internal.ui.dialogs.FilteredTypesSelectionDialog;
import org.eclipse.jdt.internal.ui.refactoring.contentassist.CompletionContextRequestor;
import org.eclipse.jdt.internal.ui.refactoring.contentassist.ControlContentAssistHelper;
import org.eclipse.jdt.internal.ui.refactoring.contentassist.JavaTypeCompletionProcessor;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.dialogs.SearchPattern;
import org.springsource.ide.eclipse.commons.frameworks.core.FrameworkCoreActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.JavaParameterDescriptor;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.java.FrameworksJavaUtils;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.parameters.editors.IProjectSelectionChangeListener;


/**
 * This adapter allows callers to adapt UI controls to support Java content
 * assist and Java type browsing.
 * <p>
 * The controls are specified by a handler passed in by some UI component that
 * requires Java content assist and, optionally, Java type browsing. These UI
 * components are typically wizard pages and dialogues.
 * </p>
 * <p>
 * The adapter specifies content assist functionality that is added to a text
 * control where Java type names are entered
 * </p>
 * <p>
 * The adapter, also optionally specifies functionality that is added to a
 * browse button.
 * </p>
 * <p>
 * Subclasses can override the content assist getter to return content assist
 * providers more specific to their domain.
 * </p>
 * <p>
 * IMPORTANT: This content assist adapter is using an older Java content assist.
 * As of 3.6, it is using the same content assist as found in the Java New Type
 * wizard. A longer term TODO is to move away from the old Java content assist
 * and adopt the newer platform content assist.
 * </p>
 * @author Nieraj Singh
 */
public class JavaContentAssistUIAdapter implements
		IProjectSelectionChangeListener {

	private IJavaContentAssistHandler javaContentAssistHandler;
	private IJavaProject javaProject;
	private JavaParameterDescriptor javaParameter;

	private IContentAssistProcessor processor;

	/**
	 * The java parameter contains the type of java to search for or use in
	 * content assist. Must not be null {@link IllegalArgumentException} is
	 * thrown if the parameter is null.
	 * 
	 * 
	 * @param javaParameter
	 *           must not be null
	 */
	public JavaContentAssistUIAdapter(JavaParameterDescriptor javaParameter) {
		Assert.isLegal(javaParameter != null);
		this.javaParameter = javaParameter;
	}

	public static boolean isNotifierValid(IJavaContentAssistHandler adapt) {
		return adapt != null && adapt.getShell() != null
				&& adapt.getJavaTextControl() != null;
	}
	
	/**
     * Adapts a java int type to a Java search type. Returns -1 if it cannot
     * adapt
     * 
     * @param type
     * @return Java search type or -1 if it cannot adapt
     * @see IJavaSearchConstants
     */
    private static int adaptToJavaSearchType(int type) {
        switch (type) {
        case JavaParameterDescriptor.FLAG_INTERFACE:
            return IJavaSearchConstants.INTERFACE;
        case JavaParameterDescriptor.FLAG_CLASS:
            return IJavaSearchConstants.CLASS;
        case JavaParameterDescriptor.FLAG_PACKAGE:
            return IJavaSearchConstants.PACKAGE;
        }
        return -1;
    }

	protected void applyContentAssist(Text text) {
		processor = createContentAssistProcessor();
		if (processor != null) {
			ControlContentAssistHelper.createTextContentAssistant(text,
					processor);
			reconfigureContentAssistProcessor();
		}
	}

	/**
	 * Typically this method is not invoked by the subclass definition. It is is
	 * normally invoked by UI components that wish their UI controls adapted to
	 * the content assist and java type search mechanisms defined in this
	 * adapter.
	 * 
	 * @param handler
	 */
	public void adapt(IJavaContentAssistHandler handler) {
		this.javaContentAssistHandler = handler;
		Assert.isLegal(isNotifierValid(handler));

		Button browse = handler.getBrowseButtonControl();
		final Text text = handler.getJavaTextControl();

		applyContentAssist(text);

		text.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				notifyTextSet(text.getText());
			}

		});

		if (browse != null) {
			browse.addSelectionListener(new SelectionAdapter() {

				public void widgetSelected(SelectionEvent e) {
					browseButtonPressed();
				}

				public void widgetDefaultSelected(SelectionEvent e) {
					browseButtonPressed();
				}

			});
		}
	}

	/**
	 * Handles the browse button pressing.
	 */
	protected void browseButtonPressed() {

		if (javaProject == null || javaParameter == null) {
			return;
		}

		int type = javaParameter.getJavaElementType();
		BrowseButtonHandler browseHandler = null;
		switch (type) {
		case JavaParameterDescriptor.FLAG_CLASS:
		case JavaParameterDescriptor.FLAG_INTERFACE:
			browseHandler = new TypeBrowseButtonHandler(
					javaContentAssistHandler, javaParameter, javaProject);
			break;
		case JavaParameterDescriptor.FLAG_PACKAGE:
			browseHandler = new PackageBrowseButtonHandler(
					javaContentAssistHandler, javaParameter, javaProject);
			break;
		}

		if (browseHandler != null) {
			browseHandler.browseButtonPressed();
		}

	}

	protected void notifyTextSet(String text) {
		javaContentAssistHandler.handleJavaTypeSelection(text);
	}

	/**
	 * 
	 * @return for content assist support, this must not be null. Otherwise no
	 *        content assist support is added to the handler's text control
	 */
	protected IContentAssistProcessor createContentAssistProcessor() {
		if (javaParameter == null) {
			return null;
		}

		int type = javaParameter.getJavaElementType();
		switch (type) {
		case JavaParameterDescriptor.FLAG_CLASS:
		case JavaParameterDescriptor.FLAG_INTERFACE:
			return new JavaTypeCompletionProcessor(false, false, true);
		case JavaParameterDescriptor.FLAG_PACKAGE:
			return new JavaProjectPackageCompletionProcessor();
		}

		return null;
	}

	protected void reconfigureContentAssistProcessor() {

		if (javaProject == null) {
			return;
		}

		if (processor instanceof JavaTypeCompletionProcessor) {
			JavaTypeCompletionProcessor javaProcessor = (JavaTypeCompletionProcessor) processor;
			IPackageFragmentRoot[] roots = FrameworksJavaUtils
					.getFirstEncounteredSourcePackageFragmentRoots(javaProject);
			// Search for the first default package fragment in the package
			// fragment roots
			// of the first encountered source class path entry in the Java
			// project.
			// It seems that this works even when looking for proposals outside
			// of the package
			// fragment or Java project. It is a working "hack", but this should
			// be made extensible
			// such that specific domains can provide their own context
			if (roots != null) {
				IPackageFragment fragment = null;
				for (IPackageFragmentRoot root : roots) {
					try {
						IJavaElement[] members = root.getChildren();
						if (members != null) {
							for (IJavaElement element : members) {
								if (element instanceof IPackageFragment) {
									IPackageFragment frag = (IPackageFragment) element;
									if (frag.isDefaultPackage()) {
										fragment = frag;
										break;
									}
								}
							}
						}
						if (fragment != null) {
							break;
						}
					} catch (JavaModelException e) {
						FrameworkCoreActivator.logError(
								e.getLocalizedMessage(), e);
					}
				}

				if (fragment != null) {
					final IPackageFragment packFrag = fragment;
					javaProcessor
							.setCompletionContextRequestor(new CompletionContextRequestor() {
								public StubTypeContext getStubTypeContext() {
									return getContentAssistTypeContext(
											packFrag,
											JavaTypeCompletionProcessor.DUMMY_CLASS_NAME);
								}
							});
				}
			}
		} else if (processor instanceof JavaProjectPackageCompletionProcessor) {
			((JavaProjectPackageCompletionProcessor) processor)
					.setProject(javaProject);
		}
	}

	protected StubTypeContext getContentAssistTypeContext(
			IPackageFragment frag, String typeName) {
		if (javaParameter == null) {
			return null;
		}

		int type = javaParameter.getJavaElementType();
		switch (type) {
		case JavaParameterDescriptor.FLAG_CLASS:
			return TypeContextChecker.createSuperClassStubTypeContext(typeName,
					null, frag);
		case JavaParameterDescriptor.FLAG_INTERFACE:
			return TypeContextChecker.createSuperInterfaceStubTypeContext(
					typeName, null, frag);
		}
		return null;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.springsource.sts.frameworks.ui.internal.parameters.editors.
	 * IProjectSelectionChangeListener
	 * #projectSelectionChanged(org.eclipse.core.resources.IProject)
	 */
	public void projectSelectionChanged(IProject project) {
		if (project == null) {
			javaProject = null;
			return;
		}
		this.javaProject = JavaCore.create(project);
		reconfigureContentAssistProcessor();
	}

	public static abstract class BrowseButtonHandler {
		private IJavaContentAssistHandler handler;
		private JavaParameterDescriptor descriptor;
		private IJavaProject project;

		/**
		 * 
		 * @param handler cannot be null
		 * @param descriptor cannot be null
		 * @param project cannot be null
		 */
		public BrowseButtonHandler(IJavaContentAssistHandler handler,
				JavaParameterDescriptor descriptor, IJavaProject project) {
			this.handler = handler;
			this.descriptor = descriptor;
			this.project = project;
		}

		protected IJavaContentAssistHandler getHandler() {
			return handler;
		}

		protected JavaParameterDescriptor getJavaParameterDescriptor() {
			return descriptor;
		}

		protected IJavaProject getJavaProject() {
			return project;
		}
		
		protected void setValueInTextControl(String value) {
			Text text = getHandler().getJavaTextControl();
			if (text == null || text.isDisposed()) {
				return;
			}
			text.setText(value);
		}
		
		/**
		 * Set the value in the Text control and notify the handler that the
		 * value was set.
		 * @param value to set in text control and notify handler
		 */
		protected void setValue(String value) {
			setValueInTextControl(value);
			getHandler().handleJavaTypeSelection(value);
		}

		abstract public void browseButtonPressed();

	}

	public static class PackageBrowseButtonHandler extends BrowseButtonHandler {

		public PackageBrowseButtonHandler(IJavaContentAssistHandler handler,
				JavaParameterDescriptor descriptor, IJavaProject project) {
			super(handler, descriptor, project);
		}

		public void browseButtonPressed() {
			IPackageFragment[] projectFragments = getPackageFragments();
			if (projectFragments == null) {
				return;
			}

			Shell shell = getHandler().getShell();

			ElementListSelectionDialog dialog = new ElementListSelectionDialog(
					shell, new JavaElementLabelProvider(
							JavaElementLabelProvider.SHOW_DEFAULT));
			dialog.setIgnoreCase(false);
			dialog.setTitle(getJavaParameterDescriptor().getName());
			dialog.setMessage("Select a source package");
			dialog.setEmptyListMessage("No source packages to select in given project");
			dialog.setElements(projectFragments);
			dialog.setHelpAvailable(false);

			if (dialog.open() == Window.OK) {
				String dialogValue = ((IPackageFragment) dialog.getFirstResult())
				.getElementName();
				setValue(dialogValue);
			}
		}

		protected IPackageFragment[] getPackageFragments() {
			IPackageFragmentRoot[] roots;
			try {
				roots = getJavaProject().getAllPackageFragmentRoots();
				if (roots == null) {
					return null;
				}
			} catch (JavaModelException e1) {
				return null;
			}

			Text textControl = getHandler().getJavaTextControl();
			String pattern = textControl != null ? textControl.getText() : null;

			SearchPattern searchPattern = pattern != null ? new SearchPattern()
					: null;

			if (searchPattern != null) {
				searchPattern.setPattern(pattern);
			}

			List<IPackageFragment> packageFragments = new ArrayList<IPackageFragment>();
			for (IPackageFragmentRoot root : roots) {
				try {
					// To filter out all other package fragments from
					// dependencies,
					// ONLY check for source root types. A similar thing is done
					// for the Java new type wizard. If this needs to be changed
					// comment out the CPE_SOURCE check below.
					IClasspathEntry entry = root.getRawClasspathEntry();
					if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
						continue;
					}
					IJavaElement[] children = root.getChildren();
					for (IJavaElement child : children) {
						// Check for duplicates
						if (child instanceof IPackageFragment) {
							String name = ((IPackageFragment) child)
									.getElementName();

							// If no pattern specified, or it matches a pattern
							// add the fragment. Do not add if a patter is specified
							// but it does not match
							if (searchPattern == null
									|| searchPattern.matches(name)) {
								packageFragments.add((IPackageFragment) child);
							}
						}
					}
				} catch (JavaModelException e) {
					// ignore
				}
			}

			return packageFragments
					.toArray(new IPackageFragment[packageFragments.size()]);

		}
	}

	public static class TypeBrowseButtonHandler extends BrowseButtonHandler {

		public TypeBrowseButtonHandler(IJavaContentAssistHandler handler,
				JavaParameterDescriptor descriptor, IJavaProject project) {
			super(handler, descriptor, project);
		}

		private static final String TITLE = "Select a type";

		public void browseButtonPressed() {
			Text textControl = getHandler().getJavaTextControl();
			String pattern = textControl != null && !textControl.isDisposed() ? textControl.getText() : null;

			Shell shell = getHandler().getShell();

			int javaSearchType = adaptToJavaSearchType(getJavaParameterDescriptor()
							.getJavaElementType());

			if (javaSearchType == -1) {
				return;
			}

			IJavaElement[] elements = new IJavaElement[] { getJavaProject() };
			IJavaSearchScope scope = SearchEngine
					.createJavaSearchScope(elements);

			FilteredTypesSelectionDialog dialog = new FilteredTypesSelectionDialog(
					shell, false, null, scope, javaSearchType);
			dialog.setTitle(getJavaParameterDescriptor().getName());
			dialog.setMessage(TITLE);
			dialog.setInitialPattern(pattern);

			if (dialog.open() == Window.OK) {
				IType type = (IType) dialog.getFirstResult();
				if (type != null) {
					String qualifiedName = type.getFullyQualifiedName();
					setValue(qualifiedName);
				}
			}
		}

	}
}
