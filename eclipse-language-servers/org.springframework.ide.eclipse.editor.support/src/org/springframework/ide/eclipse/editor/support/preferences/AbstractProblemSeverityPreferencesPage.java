/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.preferences;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemSeverity;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemType;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Abstract superclass to easily implement a preference and/or project property page that allows
 * configuring problem severities (based on a list of {@link ProblemType}s).
 * <p>
 * The page will provide a list of pulldowns to choose each problem's severity.
 * <p>
 * This is meant to be used in conjunction with a {@link PreferencesBasedSeverityProvider}
 *
 * @author Kris De Volder
 */
public abstract class AbstractProblemSeverityPreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage, IWorkbenchPropertyPage {

	protected final ProblemSeverityPreferencesUtil util;

	/**
	 * Project for a project propertypage, or null for a workspace preference page.
	 */
	private IProject project;

	/**
	 * The state of the 'enable project specific settings' checkbox.
	 */
	private LiveVariable<Boolean> enablePreferences = new LiveVariable<>(true);

	private boolean initialized;


	private static final Comparator<ProblemType> PROBLEM_TYPE_COMPARATOR = new Comparator<ProblemType>() {
		public int compare(ProblemType o1, ProblemType o2) {
			return o1.getLabel().compareTo(o2.getLabel());
		}
	};
	private static final String[][] SEVERITY_NAMES_AND_VALUES = {
			{"Error", ProblemSeverity.ERROR.toString()},
			{"Warning", ProblemSeverity.WARNING.toString()},
			{"Info", ProblemSeverity.INFO.toString()},
			{"Hint", ProblemSeverity.HINT.toString()},
			{"Ignore", ProblemSeverity.IGNORE.toString()}
	};

	protected AbstractProblemSeverityPreferencesPage(ProblemSeverityPreferencesUtil util) {
		super(FieldEditorPreferencePage.GRID);
		this.util = util;
	}

	/**
	 * Ensures that default preference  values for all problem types are entered into the
	 * DefaultScope. Note that this mainly important for the preferences UI which displays the
	 * defaults from the peferences store. Other consumers of the preference will
	 * use ProblemType.getDefaultSeverity() when they can not find a default value in the store.
	 */
	protected void initializeDefaults() {
		IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(getPluginId());
		for (ProblemType problemType : getProblemTypes()) {
			defaults.put(util.getPreferenceName(problemType), problemType.getDefaultSeverity().toString());
			initProblemParameterDefaults(defaults, problemType);
		}
		try {
			defaults.flush();
		} catch (BackingStoreException e) {
			Log.log(e);
		}
	}

	/**
	 * @return e.g. {@code problem-parameters.spring-ai.} or {@code null} when this page has no per-problem parameters
	 */
	protected String getProblemParametersPreferencePrefix() {
		return null;
	}

	private void initProblemParameterDefaults(IEclipsePreferences defaults, ProblemType problemType) {
		String paramPrefix = getProblemParametersPreferencePrefix();
		if (paramPrefix == null) {
			return;
		}
		if (!(problemType instanceof ProblemSeverityPreferityPageFromMetadata.ProblemTypeData)) {
			return;
		}
		ProblemSeverityPreferityPageFromMetadata.ProblemTypeData ptd = (ProblemSeverityPreferityPageFromMetadata.ProblemTypeData) problemType;
		if (ptd.getParameters() == null) {
			return;
		}
		for (ProblemSeverityPreferityPageFromMetadata.ProblemParameterData param : ptd.getParameters()) {
			String prefKey = paramPrefix + problemType.getId() + "." + param.getKey();
			defaults.put(prefKey, param.getDefaultValue());
		}
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, getPluginId()));
		initializeDefaults();
	}

	@Override
	protected final IPreferenceStore doGetPreferenceStore() {
		return super.doGetPreferenceStore();
	}

	@Override
	protected void createFieldEditors() {
		if (!initialized) {
			initialized = true;
			initializeDefaults();
		}
		ProblemType[] problemTypes = getProblemTypes().toArray(new ProblemType[0]);
		Arrays.sort(problemTypes, PROBLEM_TYPE_COMPARATOR);

		for (ProblemType problemType : problemTypes) {
			ComboFieldEditor field = new ComboFieldEditor(
					util.getPreferenceName(problemType),
					problemType.getLabel(),
					SEVERITY_NAMES_AND_VALUES,
					getFieldEditorParent()
			);
			setTooltip(field, problemType.getDescription());
			addField(field);
			addProblemParameterFieldEditors(problemType);
		}
	}

	private void addProblemParameterFieldEditors(ProblemType problemType) {
		String paramPrefix = getProblemParametersPreferencePrefix();
		if (paramPrefix == null) {
			return;
		}
		if (!(problemType instanceof ProblemSeverityPreferityPageFromMetadata.ProblemTypeData)) {
			return;
		}
		ProblemSeverityPreferityPageFromMetadata.ProblemTypeData ptd = (ProblemSeverityPreferityPageFromMetadata.ProblemTypeData) problemType;
		if (ptd.getParameters() == null) {
			return;
		}
		Composite parent = getFieldEditorParent();
		for (ProblemSeverityPreferityPageFromMetadata.ProblemParameterData param : ptd.getParameters()) {
			String prefKey = paramPrefix + problemType.getId() + "." + param.getKey();
			String t = param.getType();
			if ("integer".equals(t)) {
				IntegerFieldEditor intEd = new IntegerFieldEditor(prefKey, "    " + param.getLabel(), parent);
				intEd.setValidRange(1, Integer.MAX_VALUE);
				addField(intEd);
				intEd.getLabelControl(parent).setToolTipText(param.getDescription());
			} else if ("boolean".equals(t)) {
				BooleanFieldEditor boolEd = new BooleanFieldEditor(prefKey, "    " + param.getLabel(), parent);
				addField(boolEd);
				boolEd.getLabelControl(parent).setToolTipText(param.getDescription());
			} else {
				StringFieldEditor strEd = new StringFieldEditor(prefKey, "    " + param.getLabel(), parent);
				addField(strEd);
				strEd.getLabelControl(parent).setToolTipText(param.getDescription());
			}
		}
	}

	@Override
	protected Control createContents(Composite parent) {
		if (isProjectPropertyPage()) {
			return createProjectPageContent(parent);
		} else {
			return super.createContents(parent);
		}
	}

	private Control createProjectPageContent(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		page.setLayout(new GridLayout());

		CheckboxWidget enablePreferencesCheckbox = new CheckboxWidget(page, enablePreferences);
		enablePreferencesCheckbox.setText("Enable project-specific settings");

		final Control preferencesControl = super.createContents(page);

		enablePreferences.addListener(new UIValueListener<Boolean>() {
			private ControlEnableState fBlockEnableState;

			protected void enablePreferenceContent(boolean enable) {
				if (enable) {
					if (fBlockEnableState != null) {
						fBlockEnableState.restore();
						fBlockEnableState = null;
					}
				}
				else {
					if (fBlockEnableState == null) {
						fBlockEnableState = ControlEnableState.disable(preferencesControl);
					}
				}
			}
			@Override
			protected void uiGotValue(LiveExpression<Boolean> exp, Boolean enable) {
				if (enable!=null) {
					enablePreferenceContent(enable);
				}
			}
		});

		return page;
	}


	private boolean isProjectPropertyPage() {
		return project!=null;
	}

	protected abstract List<ProblemType> getProblemTypes();

	protected void setTooltip(ComboFieldEditor field, String tooltip) {
		field.getLabelControl(getFieldEditorParent()).setToolTipText(tooltip);
	}

	@Override
	public IAdaptable getElement() {
		return project;
	}

	@Override
	public void setElement(IAdaptable element) {
		project = (IProject)element.getAdapter(IProject.class);
		if (project!=null) {
			setPreferenceStore(new ScopedPreferenceStore(new ProjectScope(project), getPluginId()));
			enablePreferences.setValue(getPreferenceStore().getBoolean(getEnableProjectPreferencesKey()));
		}
	}

	protected abstract String getPluginId();

	@Override
	public boolean performOk() {
		if (isProjectPropertyPage()) {
			getPreferenceStore().setValue(getEnableProjectPreferencesKey(), enablePreferences.getValue());
		}
		return super.performOk();
	}

	@Override
	protected void performDefaults() {
		if (isProjectPropertyPage()) {
			enablePreferences.setValue(getPreferenceStore().getDefaultBoolean(getEnableProjectPreferencesKey()));
		}
		super.performDefaults();
	}

	/**
	 * Determines the property-key that is used to strore whether project-specific preferences are enabled for
	 * the properties on this page.
	 */
	protected abstract String getEnableProjectPreferencesKey();


}
