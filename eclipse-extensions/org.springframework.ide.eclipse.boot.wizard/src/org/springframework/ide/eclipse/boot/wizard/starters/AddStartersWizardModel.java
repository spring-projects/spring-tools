/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.starters;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.core.initializr.HttpRedirectionException;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrService;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrUrl;
import org.springframework.ide.eclipse.boot.wizard.starters.eclipse.ResourceCompareInput;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Option;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.StringFieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.OkButtonHandler;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 *
 * Model for the add starters wizard that requires  a local project that will be used to:
 *
 * <p/>
 *
 * 1. Build a primary "initializr model" that contains BOTH local project information, like boot version, as well  as information downloaded
 * from initializr, like dependencies specific for that boot version. Wizard  dependency selections are also stored in this model.
 *
 * <p/>
 *
 * 2. Build a compare model that manages comparison between the local project and a project downloaded from initializr. The compare model depends on the initializr model,
 * as initializr model information is required to do a comparison.
 *
 * <p/>
 *
 * NOTE: the wizard model creates the initializr and compare models ON DEMAND or when the service URL changes, rather than when the wizard model is first created, via an external model loader. The reason for this is to allow
 * integration into the wizard's UI progress mechanism.
 *
 *
 *
 */
public class AddStartersWizardModel implements OkButtonHandler, Disposable {

	public static final Object JOB_FAMILY = "AddStartersWizardModel.JOB_FAMILY";

	private final AddStartersPreferences preferences;
	private final IProject project;
	private final AddStartersInitializrService initializrService;


	private Job asyncModelLoadJob;


	private List<Disposable> disposables = new ArrayList<>();
	private Runnable externalModelLoader;

	/*
	 *
	 * MODEL ELEMENTS
	 *
	 */
	private final FieldModel<String> bootVersionField = new StringFieldModel("Spring Boot Version:", "");
	private final FieldModel<String> urlField = new StringFieldModel("Service URL:", null);
	private final LiveVariable<InitializrModel> initializrModel = new LiveVariable<InitializrModel>(null);
	private final LiveVariable<AddStartersCompareModel> compareModel = new LiveVariable<AddStartersCompareModel>(null);

	// Use ObservableSet
	/*
	 *
	 * VALIDATORS
	 *
	 */
	//  Validates the initializr model. Any errors resulting from loading from initializr are handled here
	private final LiveVariable<ValidationResult> initializrValidator = new LiveVariable<ValidationResult>();


	/*
	 * Error markers used to mark validation errors in specific fields
	 */
	private final LiveVariable<ValidationResult> bootVersionErrorMarker = new LiveVariable<ValidationResult>();
	private final LiveVariable<ValidationResult> urlErrorMarker = new LiveVariable<ValidationResult>();

	{
		// Add error markers to their respective fields
		urlField.validator(urlErrorMarker);
		bootVersionField.validator(bootVersionErrorMarker);

		// When service URL changes, load the model
		LiveVariable<String> variable = urlField.getVariable();
		addDisposable(variable.onChange((exp, val) -> {
			asyncLoadModel();
		}));

		// Link the error markers to the initializr validator, so that errors are propagated to the initializr validator
		addDisposable(urlErrorMarker.onChange((exp, val) -> {
			initializrValidator.setValue(exp.getValue());
		}));

		addDisposable(bootVersionErrorMarker.onChange((exp, val) -> {
			initializrValidator.setValue(exp.getValue());
		}));
	}

	private String[] serviceUrlOptions;


	public AddStartersWizardModel(IProject project, AddStartersPreferences preferences, AddStartersInitializrService initializrService) throws Exception {
		this.project = project;
		this.preferences = preferences;
		this.initializrService = initializrService;

		// Link the initializr Model with the compare model
		initializrModel.onChange((exp, val) -> {
			InitializrModel initializrModel = exp.getValue();
			if (initializrModel == null) {
				compareModel.setValue(null);
			} else {
				InitializrUrl initializrUrl = new InitializrUrl(urlField.getValue());
				AddStartersCompareModel compareModel = new AddStartersCompareModel(initializrService.getProjectDownloader(initializrUrl), initializrModel);
				this.compareModel.setValue(compareModel);
			}
		});
	}

	public FieldModel<String> getBootVersion() {
		return bootVersionField;
	}

	public FieldModel<String> getServiceUrl() {
		return urlField;
	}

	public LiveExpression<InitializrModel> getInitializrModel() {
		return initializrModel;
	}

	public LiveExpression<AddStartersCompareModel> getCompareModel() {
		return compareModel;
	}

	/**
	 *
	 * @return Validator that notifies when errors occur in any of the components of the  model.
	 */
	public LiveExpression<ValidationResult> getValidator() {
		return this.initializrValidator;
	}

	@Override
	public void performOk() {
		InitializrModel model = initializrModel.getValue();
		if (model != null) {
			model.updateDependencyCount();
		}

		if (urlField.getValue() != null) {
			preferences.addInitializrUrl(urlField.getValue());
		}

		AddStartersCompareModel comparison = compareModel.getValue();
		if (comparison != null) {
			ResourceCompareInput editorInput = comparison.getCompareEditorInput().getValue();
			if (editorInput != null && editorInput.isSaveNeeded()) {
				// This will save changes in the editor.
				if (editorInput.okPressed()) {
					// Update project
					ISpringBootProject bootProject = initializrModel.getValue().getProject();
					bootProject.updateProjectConfiguration();
				}
			}
		}
	}

	private void handleLoadingError(InitializrModel model, String url, Throwable e) {
		StringBuffer detailsBuffer = new StringBuffer();
		String shortMessage = "Error encountered while resolving initializr content for: " + project.getName();

		// This error is specific to boot version validator, so inform the boot version
		// validator
		Throwable deepestCause = ExceptionUtil.getDeepestCause(e);
		ValidationResult addStartersError = AddStartersErrorUtil.getError(shortMessage, deepestCause);

		if (deepestCause instanceof FileNotFoundException) {
			detailsBuffer.append(
					"Initializr content for the project's boot version is not available. Consider updating the project to a newer version of Spring Boot:");

			if (model != null) {
				// Try fetching available boot versions
				Option[] availableBootVersions = getSupportedBootVersions(url);
				if (availableBootVersions != null && availableBootVersions.length > 0) {
					detailsBuffer.append('\n');
					for (Option option : availableBootVersions) {
						detailsBuffer.append('\n');
						detailsBuffer.append(option.getId());
					}
				}
			}

			addStartersError = AddStartersErrorUtil.getError(shortMessage, detailsBuffer.toString(), e);

			// Notify that this boot version is unsupported
			this.bootVersionErrorMarker.setValue(addStartersError);

		} else if (deepestCause instanceof UnknownHostException || deepestCause instanceof ConnectException) {
		   this.urlErrorMarker.setValue(addStartersError);
		} else {
			// some other  error unrelated to the boot version or URL
			this.initializrValidator.setValue(addStartersError);
		}
	}

	synchronized public void addModelLoader(Runnable externalModelLoader) {
		this.externalModelLoader = externalModelLoader;

		// Setting URL will trigger model loading.
		setUrlValues();
	}

	private void setUrlValues() {
		String[] urls = preferences.getInitializrUrls();
		this.serviceUrlOptions = urls;

		String initializrUrl = preferences.getInitializrUrl();
		urlField.getVariable().setValue(initializrUrl);
	}

	synchronized private void asyncLoadModel() {
		// If the model loader is not yet available, don't do anything
		if (AddStartersWizardModel.this.externalModelLoader == null) {
			return;
		}
		if (asyncModelLoadJob == null) {
			// Load the model async as it can be long running or potentially block on connection to initializr
			asyncModelLoadJob = new Job("Loading initializr model") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					clearPreviousModel();

					// Perform basic URL and connection validation BEFORE invoking the
					// external model loader, as model loading could be slow.
					// This is to fix a bug where we want a "fast"
					// typing experience when a user enters a URL in the URL field, and
					// want basic validation on each character typed to be fast.
					ValidationResult result = basicUrlValidation();
					if (result.isOk()) {
						result = basicUrlConnection();
						if (result.isOk()) {
							AddStartersWizardModel.this.externalModelLoader.run();
						}
					}
					return Status.OK_STATUS;
				};

				@Override
				public boolean belongsTo(Object family) {
					return family == JOB_FAMILY;
				}
			};
		}
		asyncModelLoadJob.schedule();
	}

	/**
	 * Synchronous initializr model creation with info from initializr
	 */
	public void createInitializrModel(IProgressMonitor monitor) {
		InitializrModel initializrModel = null;
		Exception error = null;
		String url = urlField.getValue();

		try {
			writeToMonitor(monitor, "Getting Boot project...");
			ISpringBootProject bootProject = getBootProject(url);
			bootVersionField.setValue(bootProject.getBootVersion());
			writeToMonitor(monitor, "Creating Initializr model...");
			initializrModel = new InitializrModel(bootProject, preferences);
			writeToMonitor(monitor, "Fetching starter information from Spring Boot Initializr...");
			initializrModel.downloadDependencies();
		} catch (Exception _e) {
			error = _e;
		}

		// FIRST set the model even if there are errors
		if (initializrModel != null) {
			this.initializrModel.setValue(initializrModel);
		}

		if (error != null) {
			Throwable e = ExceptionUtil.getDeepestCause(error);
			if (e instanceof HttpRedirectionException) {
				urlField.getVariable().setValue(((HttpRedirectionException)e).redirectedTo);
			} else {
				handleLoadingError(initializrModel, url, e);
			}
		} else {
			initializrValidator.setValue(ValidationResult.OK);
		}
	}

	private Option[] getSupportedBootVersions(String url)  {
		try {
			return initializrService.getSupportedBootReleaseVersions(url);
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	private void clearPreviousModel() {
		initializrModel.setValue(null);
		bootVersionErrorMarker.setValue(null);
		urlErrorMarker.setValue(null);
		initializrValidator.setValue(null);
	}

	private void writeToMonitor(IProgressMonitor monitor, String msg) {
		if (monitor != null) {
			if (StringUtil.hasText(msg)) {
				monitor.subTask(msg);
			} else {
				monitor.done();
			}
		}
	}

	private ValidationResult basicUrlValidation() {
		String url = urlField.getValue();
		ValidationResult result = null;
		if (!StringUtil.hasText(url)) {
			String msg = "Missing initializr service URL";
			 result = AddStartersError.from(msg, msg);
		} else {
			try {
				new URL(url);
				result = ValidationResult.OK;
			} catch (Exception e) {

				String shortMessage = "Error encountered while resolving initializr content for: "
						+ project.getName();

				result = AddStartersErrorUtil.getError(shortMessage, e);
			}
		}

		if (!result.isOk()) {
			urlErrorMarker.setValue(result);
		}

		return  result;
	}

	private ValidationResult basicUrlConnection() {

		String url = urlField.getValue();
		try {
			initializrService.checkBasicConnection(new URL(url));
		} catch (Exception e) {
			Throwable deepestCause = ExceptionUtil.getDeepestCause(e);
			String shortMessage = "Error encountered while resolving initializr content for: " + project.getName();

			ValidationResult addStartersError = AddStartersErrorUtil.getError(shortMessage, deepestCause);
			urlErrorMarker.setValue(addStartersError);
			return addStartersError;

		}
		return ValidationResult.OK;
	}


	private ISpringBootProject getBootProject(String url) throws Exception {
		InitializrService initializr = initializrService.getService(() -> url);
		SpringBootCore core = new SpringBootCore(initializr);
		return core.project(project);
	}

	protected void addDisposable(Disposable d) {
		if (d != null  && !disposables.contains(d)) {
			disposables.add(d);
		}
	}

	@Override
	public void dispose() {
		for (Disposable disposable : disposables) {
			disposable.dispose();
		}
	}

	public String[] getServiceUrlOptions() {
		return this.serviceUrlOptions != null ? serviceUrlOptions : new String[0];
	}
}