/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.core.initializr.HttpRedirectionException;
import org.springframework.ide.eclipse.boot.wizard.InitializrFactoryModel.ModelFactory;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.StringFieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.ui.OkButtonHandler;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * Abstract base class for the shared functionality of a ui that depends on
 * an initializer service url.
 * <p>
 * Once the url is chosen the model for the rest of the ui is created dynamically.
 *
 * @author Kris De Volder
 */
public final class InitializrFactoryModel<M> implements OkButtonHandler {

	@FunctionalInterface
	public interface ModelFactory<M> {
		M createModel(String url) throws Exception;
	}

	protected final String[] urls;

	private final StringFieldModel serviceUrlField = new StringFieldModel("Service URL", null);
	private final LiveVariable<ValidationResult> modelValidator = new LiveVariable<>(ValidationResult.OK);
	private final LiveExpression<M> model = new AsyncLiveExpression<M>(null, "Building UI model") {
		{
			dependsOn(serviceUrlField.getVariable());
		}
		@Override
		protected M compute() {
			modelValidator.setValue(ValidationResult.info("Contacting web service and building ui model..."));
			try {
				M m = factory.createModel(getServiceUrlField().getValue());
				modelValidator.setValue(ValidationResult.OK);
				return m;
			} catch (Exception _e) {
				Throwable e = ExceptionUtil.getDeepestCause(_e);
				if (e instanceof HttpRedirectionException) {
					serviceUrlField.getVariable().setValue(((HttpRedirectionException)e).redirectedTo);
				} else {
					modelValidator.setValue(ValidationResult.error(ExceptionUtil.getMessage(e)));
				}
			}
			return null;
		}

	};

	private final ModelFactory<M> factory;

	public InitializrFactoryModel(ModelFactory<M> factory) {
		this.factory = factory;
		this.urls = BootPreferences.getInitializrUrls();
		serviceUrlField.validator(modelValidator);
		serviceUrlField.getVariable().setValue(BootPreferences.getInitializrUrl());
	}

	public StringFieldModel getServiceUrlField() {
		return serviceUrlField;
	}

	public String[] getUrls() {
		return urls;
	}

	public LiveExpression<M> getModel() {
		return model;
	}

	public void save() {
		if (serviceUrlField.getValue() != null) {
			BootPreferences.addInitializrUrl(serviceUrlField.getValue());
		}
	}

	@Override
	public void performOk() throws Exception {
		M model = getModel().getValue();
		if (model != null && model instanceof OkButtonHandler) {
			BootPreferences.addInitializrUrl(serviceUrlField.getValue());
			((OkButtonHandler)model).performOk();
		}
	}

}
