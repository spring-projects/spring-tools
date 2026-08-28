/*******************************************************************************
 * Copyright (c) 2013, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import static org.springsource.ide.eclipse.commons.livexp.ui.UIConstants.FIELD_TEXT_AREA_WIDTH;

import java.util.Collection;
import java.util.Objects;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.SelectionModel;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.util.Parser;

/**
 * Wizard section to choose one element from list of elements. Uses a pulldown Combo box to allow selecting
 * an element.
 */
public class ChooseOneSectionCombo<T> extends AbstractChooseOneSection<T> {
	
	private static final boolean isGtk = checkGtk();
	
	private static boolean checkGtk() {
		try { 
			return Class.forName("org.eclipse.swt.internal.gtk.GTK") != null;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean DEBUG = false; //(""+Platform.getLocation()).contains("kdvolder");

	private void debug(String string) {
		if (DEBUG) {
			System.out.println("handleModifyText "+string);
		}
	}


	private final SelectionModel<T> selection;
	private final String label; //Descriptive Label for this section
	private LiveExpression<T[]> options; //The elements to choose from
	private boolean useFieldLabelWidthHint = true;
	private boolean grabHorizontal = false;
	
	
	// Optional UI Elements
	private boolean showErrorMarker = false;
	private ControlDecoration errorMarker;
	private final ValueListener<ValidationResult> errorMarkerListener = UIValueListener.from((exp, result) -> {
		if (errorMarker != null && errorMarker.getControl() != null && !errorMarker.getControl().isDisposed()) {
			if (result != null && result.status == IStatus.ERROR) {
				errorMarker.show();
			} else {
				errorMarker.hide();
			}
		}
	});

	/**
	 * For a combo that allows text edits, a textInputParser must be provided to convert
	 * the input text into a selection value.
	 */
	private Parser<T> inputParser = null;

	public ChooseOneSectionCombo(IPageWithSections owner, String label, SelectionModel<T> selection, T[] options) {
		this(owner, label, selection, LiveExpression.constant(options));
		Assert.isNotNull(options);
	}

	public ChooseOneSectionCombo<T> useFieldLabelWidthHint(boolean use) {
		this.useFieldLabelWidthHint = use;
		return this;
	}

	public ChooseOneSectionCombo(IPageWithSections owner, String label, SelectionModel<T> selection, LiveExpression<T[]> options) {
		super(owner);
		DEBUG = DEBUG && "Service URL".equals(label);
		this.label = label;
		this.selection = selection;
		this.options = options;
	}

	@SuppressWarnings("unchecked")
	public ChooseOneSectionCombo(IPageWithSections owner, String label, LiveVariable<T> selection, Collection<T> options) {
		this(owner,
			label,
			new SelectionModel<>(selection),
			(T[])options.toArray()
		);
	}

	public ChooseOneSectionCombo(IPageWithSections owner, FieldModel<T> model,
			T[] options) {
		this(owner, model.getLabel(), new SelectionModel<>(model.getVariable(), model.getValidator()), LiveExpression.constant(options));
	}
	
	/**
	 * Enable's support for 'editable' text widget in the Combo. This means user can perform textual edits
	 * in addition to using the combo.
	 * <p>
	 * To support these 'free form' edits. A inputParser must be provided.
	 */
	public void allowTextEdits(Parser<T> inputParser) {
		this.inputParser = inputParser;
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return selection.validator;
	}

	public LiveVariable<T> getSelection() {
		return selection.selection;
	}

	@Override
	public void createContents(Composite page) {
		Composite field = new Composite(page, SWT.NONE);
		GridLayout layout = GridLayoutFactory.fillDefaults().numColumns(2).create();
		field.setLayout(layout);

		GridDataFactory.fillDefaults().grab(true, false).applyTo(field);

		Label fieldNameLabel = new Label(field, SWT.NONE);
		fieldNameLabel.setText(label);
		GridDataFactory labelGridData = GridDataFactory
				.fillDefaults()
				.align(SWT.BEGINNING, SWT.CENTER);
		if (useFieldLabelWidthHint) {
			labelGridData.hint(UIConstants.fieldLabelWidthHint(fieldNameLabel), SWT.DEFAULT);
		}
		labelGridData.applyTo(fieldNameLabel);

		final Combo combo = new Combo(field, inputParser == null ? SWT.READ_ONLY : SWT.NONE);
		{	//works around strange bug (?) in Combo widget. This code looks like it shouldn't do anything... 
			//but it actually does. Without it, the 'pulldown' from the combo will be transparant on Linux 
			// and hard to read.
			// See: https://www.pivotaltracker.com/story/show/174833544
			//Note avoid doing the workaround for editable combo (i.e. where text is editable).
			//because the call to 'setBackground' then messes up the background color of selected text.
			if (inputParser == null && isGtk) {
				Color comboBg = combo.getBackground();
				combo.setBackground(comboBg);
			}
		}

		options.addListener(new ValueListener<T[]>() {
			public void gotValue(org.springsource.ide.eclipse.commons.livexp.core.LiveExpression<T[]> exp, T[] value) {
				if (combo != null) {
					String oldText = combo.getText();
					combo.setItems(getLabels()); //This will clear the selection sometimes
					combo_setText(combo, oldText);
				}
			};
		});
		GridDataFactory gridData = GridDataFactory.fillDefaults();
		if (inputParser != null) {
			gridData = gridData
					.hint(FIELD_TEXT_AREA_WIDTH, SWT.DEFAULT)
					.minSize(FIELD_TEXT_AREA_WIDTH, SWT.DEFAULT);
		}
		if (grabHorizontal) {
			gridData = gridData.grab(grabHorizontal, false);
		}
		gridData.applyTo(combo);

		combo.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				handleModifyText(combo);
			}
		});
		
		if (showErrorMarker) {
			errorMarker = new ControlDecoration(fieldNameLabel, SWT.TOP | SWT.RIGHT);
			Image errorImage = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR)
					.getImage();

			errorMarker.setImage(errorImage);
			errorMarker.hide();
			LiveExpression<ValidationResult> validator = getValidator();
			if (validator != null) {
				validator.addListener(errorMarkerListener);
			}
		}
		
		selection.selection.addListener(UIValueListener.from((exp, newSelection) -> {
			if (newSelection != null && !combo.isDisposed()) {
				//Technically, not entirely correct. This might
				// select the wrong element if more than one option
				// has the same label text.
				String newText = labelProvider.getText(newSelection);
				combo_setText(combo, newText);
				if (!combo.getText().equals(newText)) {
					//widget rejected the selection. To avoid widget state
					// and model state getting out-of-sync, refelct current
					// widget state back to the model:
					handleModifyText(combo);
				}
			}
		}));
	}

	private void combo_setText(final Combo combo, String newText) {
		if (combo != null && !combo.isDisposed()) {
			String oldText = combo.getText();
			if (!Objects.equals(oldText, newText)) {
				//Avoid setting the text if its already set to a equal value. This can cause strange effects by
				// moving the cursor on some os-es. See https://issuetracker.springsource.com/browse/STS-4377
				combo.setText(newText);
			}
		}
	}

	private void handleModifyText(final Combo combo) {
		int selected = combo.getSelectionIndex();
		debug("selectedIdx = "+selected);
		T[] options = getOptionsArray();
		// BUG in OSX: Seems that when text edit is enabled in the combo control, and a new text is entered in the combo
		// that is NOT in the list of options, the new
		// text has an index that fluctuates from -1 to 0. -1 is the expected index because
		// the new text is not in the list of options, but for some reason sometimes it is 0, which may
		// result in the first element in the options being set instead of the new text in the control with the code below.
		// Therefore adding an additional check that guards when values from options are set in the selection ONLY if the value in the selected
		// index of the options matches the text in the combo. Otherwise, parse the combo text directly
		if (options != null && selected >= 0 && selected < options.length
				&& labelProvider.getText(options[selected]).equals(combo.getText())) {
			debug("setting selection based on idx = " + options[selected]);
			selection.selection.setValue(options[selected]);
		} else {
			T parsed = parse(combo.getText());
			debug("setting selection from combo = "+ parsed);
			selection.selection.setValue(parsed);
		}
		debug("Exiting: "+labelProvider.getText(selection.selection.getValue()) +" == "+combo.getText());
	}

	private T parse(String text) {
		try {
			if (inputParser != null) {
				return inputParser.parse(text);
			}
		} catch (Exception e) {
			//ignore unparsable input
		}
		return null;
	}

	private String[] getLabels() {
		String[] labels = new String[getOptionsArray().length];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = labelProvider.getText(getOptionsArray()[i]);
		}
		return labels;
	}

	private T[] getOptionsArray() {
		return options.getValue();
	}

	public LiveExpression<T[]> getOptions() {
		return options;
	}

	public ChooseOneSectionCombo<T> grabHorizontal(boolean grab) {
		this.grabHorizontal = grab;
		return this;
	}
	
	public ChooseOneSectionCombo<T> showErrorMarker(boolean showErrorMarker) {
		this.showErrorMarker = showErrorMarker;
		return this;
	}

	/**
	 * Convenience method that returns the options cast to LiveVariable. This method
	 * will throw an {@link ClassCastException} if the options were not provided
	 * via a LiveVariable.
	 */
	public LiveVariable<T[]> getOptionsVar() {
		return (LiveVariable<T[]>) options;
	}
}
