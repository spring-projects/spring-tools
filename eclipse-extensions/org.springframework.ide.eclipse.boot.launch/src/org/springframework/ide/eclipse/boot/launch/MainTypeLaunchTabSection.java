/*******************************************************************************
 * Copyright (c) 2005, 2012, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Pivotal Software - Bits and pieces copied to create MainTypeSelectionSection
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.internal.ui.SWTFactory;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.launcher.DebugTypeSelectionDialog;
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages;
import org.eclipse.jdt.internal.debug.ui.launcher.MainMethodSearchEngine;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.launch.util.DelegatingLaunchConfigurationTabSection;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.SelectionModel;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.UIConstants;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * A LaunchConfigurationTabSection hat allows user to choose main type
 * in a project.
 * <p>
 * Basically a simplified version to the JDT JavaMainTab. some of the
 * rarely used options have been removed and the code converted
 * to be useable as a LaunchConfigurationTabSection so it can be
 * composed more freely with other LaunchTabSectons onto a page.
 *
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class MainTypeLaunchTabSection extends DelegatingLaunchConfigurationTabSection {

	public MainTypeLaunchTabSection(IPageWithSections owner, LiveVariable<IProject> project, MainTypeNameLaunchTabModel model) {
		super(owner, model, createUi(owner, project, model));
	}

	private static IPageSection createUi(IPageWithSections owner, final LiveVariable<IProject> project, final SelectionModel<String> mainTypeName) {
		return new WizardPageSection(owner) {

			private Text fMainText;
			private Button fSearchButton;

			private LiveVariable<String> mainTypeName() {
				return mainTypeName.selection;
			}
			private LiveVariable<IProject> project() {
				return project;
			}

			@Override
			public LiveExpression<ValidationResult> getValidator() {
				return mainTypeName.validator;
			}

			public void createContents(Composite parent) {
				String label = "Main type";
				GridDataFactory grabHor = GridDataFactory.fillDefaults().grab(true, false);

				Composite field = new Composite(parent, SWT.NONE);
				GridLayout layout = GridLayoutFactory.fillDefaults().numColumns(3).create();
//		        GridLayout layout = new GridLayout();
		 //       layout.numColumns = 3;
//		        layout.marginBottom = 0;
//		        layout.marginTop = 0;
//		        layout.marginLeft = 0;
//		        layout.marginRight = 0;
//		        layout.marginWidth = 0;
				field.setLayout(layout);
				grabHor.applyTo(field);

				Label fieldNameLabel = new Label(field, SWT.NONE);
				fieldNameLabel.setText(label);
		        GridDataFactory.fillDefaults()
		        	.hint(UIConstants.fieldLabelWidthHint(fieldNameLabel), SWT.DEFAULT)
		        	.align(SWT.BEGINNING, SWT.CENTER)
		        	.applyTo(fieldNameLabel);

				fMainText = new Text(field, SWT.BORDER);
				grabHor.applyTo(fMainText);
				fMainText.addModifyListener(new ModifyListener() {
					public void modifyText(ModifyEvent e) {
						mainTypeName().setValue(fMainText.getText());
					}
				});
				mainTypeName().addListener(new ValueListener<String>() {
					public void gotValue(LiveExpression<String> exp, String newName) {
						if (fMainText!=null) {
							if (newName!=null) {
								String oldName = fMainText.getText();
								//Don't set the text if its not actually changed.
								// Otherwise change events from typing in the widget will cause the
								// text to be set and this (on Mac OS) cause the widget's text selection
								// to be reset as well.
								if (!oldName.equals(newName)) {
									fMainText.setText(newName);
								}
							}
						}
					}
				});
				fSearchButton = SWTFactory.createPushButton(field, "Search...", null);
				fSearchButton.addSelectionListener(new SelectionListener() {
					public void widgetDefaultSelected(SelectionEvent e) {
					}
					public void widgetSelected(SelectionEvent e) {
						handleSearchButtonSelected();
					}
				});
			}

			/**
			 * Show a dialog that lists all main types
			 */
			protected void handleSearchButtonSelected() {
				IJavaProject project = getJavaProject();
				IJavaElement[] elements = null;
				if ((project == null) || !project.exists()) {
					IJavaModel model = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
					if (model != null) {
						try {
							elements = model.getJavaProjects();
						}
						catch (JavaModelException e) {JDIDebugUIPlugin.log(e);}
					}
				}
				else {
					elements = new IJavaElement[]{project};
				}
				if (elements == null) {
					elements = new IJavaElement[]{};
				}
				int constraints = IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES;
				IJavaSearchScope searchScope = SearchEngine.createJavaSearchScope(elements, constraints);
				MainMethodSearchEngine engine = new MainMethodSearchEngine();
				IType[] types = null;
				try {
					types = engine.searchMainMethods(owner.getRunnableContext(), searchScope, false);
				}
				catch (Exception e) {
					BootActivator.log(e);
					return;
				}
				DebugTypeSelectionDialog mmsd = new DebugTypeSelectionDialog(owner.getShell(), types, LauncherMessages.JavaMainTab_Choose_Main_Type_11);
				if (mmsd.open() == Window.CANCEL) {
					return;
				}
				Object[] results = mmsd.getResult();
				IType type = (IType)results[0];
				if (type != null) {
					fMainText.setText(type.getFullyQualifiedName());
					project().setValue(type.getJavaProject().getProject());
				}
			}

			private IJavaProject getJavaProject() {
				try {
					return JavaCore.create(project().getValue());
				} catch (Exception e) {
					BootActivator.log(e);
					return null;
				}
			}
		};
	}

}
