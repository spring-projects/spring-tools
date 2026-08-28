/*******************************************************************************
 * Copyright (c) 2013, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.livexp.ui.DynamicSection;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.ui.ChooseOneSectionCombo;
import org.springsource.ide.eclipse.commons.livexp.ui.CommentSection;
import org.springsource.ide.eclipse.commons.livexp.ui.DescriptionSection;
import org.springsource.ide.eclipse.commons.livexp.ui.GroupSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.ProjectLocationSection;
import org.springsource.ide.eclipse.commons.livexp.ui.StringFieldSection;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Parser;

import com.google.common.collect.ImmutableList;

public class NewSpringBootWizard extends Wizard implements INewWizard, IImportWizard {

	private static final ImageDescriptor IMAGE = BootWizardImages.BOOT_WIZARD_ICON;
	public static final String NO_CONTENT_AVAILABLE = "No content available.";

	private InitializrFactoryModel<NewSpringBootWizardModel> model;

	private IStructuredSelection selection;

	private WorkingSetSection workingSetSection;

	private WizardPageWithSections projectPage;

	public NewSpringBootWizard() throws Exception {
		setDefaultPageImageDescriptor(IMAGE);
	}

	//@Override
	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		try {
			model = NewSpringBootWizardFactoryModel.create(BootActivator.getUrlConnectionFactory(), BootActivator.getDefault().getPreferenceStore());
			this.selection = selection;
		} catch (Exception e) {
			MessageDialog.openError(workbench.getActiveWorkbenchWindow().getShell(), "Error opening the wizard",
					ExceptionUtil.getMessage(e)+"\n\n"+
					"Note that this wizard uses a webservice and needs internet access.\n"+
					"A more detailed error message may be found in the Eclipse error log."
			);
			//Ensure exception is logged. (Eclipse UI may not log it!).
			BootWizardActivator.log(e);
			throw new Error(e);
		}
	}

	@Override
	public void addPages() {
		super.addPages();
		Assert.isLegal(model != null, "The Spring Starter Wizard model was not initialized. Unable to open the wizard.");
		addPage(projectPage = new ProjectDetailsPage(model));
		addPage(new MultipleViewsDependencyPage(model));
		addPage(new PageThree(model));
	}


	@Override
	public boolean canFinish() {
		return super.canFinish() && getContainer().getCurrentPage() != projectPage;
	}

	public class ProjectDetailsSection extends GroupSection {

		private final NewSpringBootWizardModel model;

		public ProjectDetailsSection(IPageWithSections owner, NewSpringBootWizardModel model) {
			super(owner, null);
			this.model = model;
			addSections(createSections().toArray(new WizardPageSection[0]));
			focusSectionIndex = 0;
		}

		protected  List<WizardPageSection> createSections() {
			List<WizardPageSection> sections = new ArrayList<>();

			FieldModel<String> projectName = model.getProjectName();
			sections.add(new StringFieldSection(owner, projectName));
			sections.add(new ProjectLocationSection(owner, model.getLocation(), projectName.getVariable(), model.getLocationValidator()));

			WizardPageSection radios = createRadioGroupsSection(owner);
			if (radios != null) {
				sections.add(radios);
			}

			for (FieldModel<String> f : model.stringInputs) {
				//caution! we already created the section for projectName because we want it at the top
				if (projectName != f) {
					sections.add(new StringFieldSection(owner, f));
				}
			}

			sections.add(workingSetSection = new WorkingSetSection(owner, selection));

			return sections;
		}

		private WizardPageSection createRadioGroupsSection(IPageWithSections owner) {
			boolean notEmpty = false;
			RadioGroup bootVersion = model.getBootVersion(); //This is placed specifically somewhere else so must skip it here
			ArrayList<WizardPageSection> radioSections = new ArrayList<>();
			for (RadioGroup radioGroup : model.getRadioGroups().getGroups()) {
				if (radioGroup != bootVersion) {
					if (radioGroup.getRadios().length > 1) {
						//Don't add a UI elements for something that offers no real choice
						radioSections.add(
							new ChooseOneSectionCombo<>(owner, radioGroup.getLabel(), radioGroup.getSelection(), radioGroup.getRadios()).grabHorizontal(true)
							//new ChooseOneSectionCombo<RadioInfo>(owner, radioGroup.getLabel(), radioGroup.getSelection(), radioGroup.getRadios())
						);
						notEmpty = true;
					}
				}
			}
			if (notEmpty) {
				return new GroupSection(owner, null, radioSections.toArray(new WizardPageSection[radioSections.size()])).columns(2, false);
			}
			return null;
		}

	}

	public class ProjectDetailsPage extends WizardPageWithSections {

		protected InitializrFactoryModel<NewSpringBootWizardModel> model;

		public ProjectDetailsPage(InitializrFactoryModel<NewSpringBootWizardModel> model) {
			super("page1", "New Spring Starter Project", null);
			this.model = model;
		}

		@Override
		protected List<WizardPageSection> createSections() {

			ChooseOneSectionCombo<String> comboSection = new ChooseOneSectionCombo<>(this, model.getServiceUrlField(), model.getUrls())
					.grabHorizontal(true);
			comboSection.allowTextEdits(Parser.IDENTITY);

			// Note: have to set the project page in the dynamic section because the dynamic section composite
			// gets created at the start, and determines the initial size of the wizard page, regardless of its content.
			DynamicSection dynamicSection = new DynamicSection(this, model.getModel().apply((dynamicModel) -> {
				if (dynamicModel != null) {
					return new ProjectDetailsSection(this, dynamicModel);
				}
				return new CommentSection(this, NO_CONTENT_AVAILABLE);
			}));

			return ImmutableList.of(comboSection, dynamicSection);
		}

		@Override
		public void setVisible(boolean visible) {
			super.setVisible(visible);
			if (visible) {
				getSections().get(1).setFocus();
			}
		}

	}


	public static class PageThree extends WizardPageWithSections {

		private final InitializrFactoryModel<NewSpringBootWizardModel> model;

		protected PageThree(InitializrFactoryModel<NewSpringBootWizardModel> model) {
			super("page3", "New Spring Starter Project", null);
			this.model = model;
		}

		protected WizardPageSection createDynamicContent(NewSpringBootWizardModel model) {
			List<WizardPageSection> sections = new ArrayList<>();

			sections.add(new GroupSection(this, "Site Info",
					new StringFieldSection(this, "Base Url", model.baseUrl, model.baseUrlValidator),
					new DescriptionSection(this, model.downloadUrl).label("Full Url").readOnly(false)
			));

			return new GroupSection(this, null, sections.toArray(new WizardPageSection[0])).grabVertical(true);
		}

		@Override
		protected List<WizardPageSection> createSections() {

			DynamicSection dynamicSection = new DynamicSection(this, model.getModel().apply((dynamicModel) -> {
				if (dynamicModel != null) {
					return createDynamicContent(dynamicModel);
				}
				return new CommentSection(this, NO_CONTENT_AVAILABLE);
			} ));
			return ImmutableList.of(dynamicSection);
		}
	}

	@Override
	public boolean performFinish() {
		if (model.getModel().getValue() == null) {
			return false;
		}
		model.getModel().getValue().setWorkingSets(workingSetSection.getWorkingSets()); //must be in ui thread. Don't put in job!
		WorkspaceJob job = new WorkspaceJob("Import Getting Started Content") {
			@Override
			public IStatus runInWorkspace(IProgressMonitor mon) {
				try {
					// TODO: maybe the model factory should have a perform finish that does a save, which then calls the underlying model perform finish
					model.save();
					model.getModel().getValue().performFinish(mon);
					return Status.OK_STATUS;
				} catch (Throwable e) {
					return ExceptionUtil.status(e);
				}
			}
		};
		//WARNING: Do not set a scheduling rule here. It breaks gradle import by causing a deadlock or rule conflict.
		//job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
		//See: https://www.pivotaltracker.com/story/show/128781771
		job.setPriority(Job.BUILD);
		job.setUser(true); //shows progress in default eclipse config
		job.schedule();
		return true;
	}

}
