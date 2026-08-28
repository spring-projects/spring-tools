/*******************************************************************************
 *  Copyright (c) 2012,2013 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.ui.preferences;

import java.io.File;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.cli.IBootInstallFactory;
import org.springframework.ide.eclipse.boot.core.cli.install.IBootInstall;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;


/**
 * @author Christian Dupuis
 * @author Kris De Volder
 */
public class BootInstallDialog extends TitleAreaDialog {

	private Text urlText;

	private Text nameText;

	private Text versionText;

	private String url;

	private String name;

	private final IBootInstall install;

	private final InstalledBootInstallBlock prefPage;

	private IBootInstallFactory installFactory;

	public BootInstallDialog(Shell parentShell, IBootInstall install, InstalledBootInstallBlock parent, IBootInstallFactory installFactory) {
		super(parentShell);
		this.prefPage = parent;
		this.install = install;
		this.name = install.getName();
		this.url = install.getUrl();
		this.installFactory = installFactory;
	}

	public IBootInstall getResult() {
		try {
			return installFactory.newInstall(url, name);
		} catch (Exception e) {
			BootActivator.log(e);
		}
		return null;
	}
		

	private void clearError() {
		getButton(OK).setEnabled(true);
		setErrorMessage(null);
	}

	private void setError(String message) {
		getButton(OK).setEnabled(false);
		setErrorMessage(message);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jface.dialogs.TitleAreaDialog#createDialogArea(org.eclipse
	 * .swt.widgets.Composite)
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite parentComposite = (Composite) super.createDialogArea(parent);
		Composite composite = new Composite(parentComposite, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 10;
		layout.marginWidth = 10;
		layout.numColumns = 2;
		layout.makeColumnsEqualWidth = false;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label name = new Label(composite, SWT.WRAP);
		name.setText("Name:");
		// name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		nameText = new Text(composite, SWT.BORDER);
		nameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		if (install.getName() != null) {
			nameText.setText(install.getName());
		}
		nameText.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				BootInstallDialog.this.name = nameText.getText();
				validate(true);
			}
		});

		Label version = new Label(composite, SWT.WRAP);
		version.setText("Version:");
		// version.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		versionText = new Text(composite, SWT.BORDER);
		versionText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		versionText.setEnabled(false);
		if (install.getVersion() != null) {
			versionText.setText(install.getVersion());
		}

		Label directory = new Label(composite, SWT.WRAP);
		directory.setText("Location:");
		// directory.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		urlText = new Text(composite, SWT.BORDER);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = 250;
		urlText.setLayoutData(data);
		urlText.setEnabled(false);
		if (install.getUrl() != null) {
			urlText.setText(install.getUrl());
		}
		urlText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				validate(true);
			}
		});

		new Label(composite, SWT.WRAP);

		Composite buttonRow = new Composite(composite, SWT.NONE);
		buttonRow.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
		buttonRow.setLayout(new RowLayout());
		
		Button browseButton = new Button(buttonRow, SWT.PUSH);
		browseButton.setText("Folder...");
		browseButton.setToolTipText("Browse for a local Spring Boot Installation (unzipped into a folder)");
//		browseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(getShell());
				dialog.setMessage("Select Spring Boot installation directory");
				dialog.setText("Boot installation directory");
				String filePath = dialog.open();
				handleBrowseButtonResult(filePath);
			}
		});
		//GridDataFactory.fillDefaults().align(SWT.END, SWT.END).applyTo(browseButton);
		
		browseButton = new Button(buttonRow, SWT.PUSH);
		browseButton.setText("Zip...");
		browseButton.setToolTipText("Browse for a local zip file containing a Spring Boot Installation");
//		browseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
				dialog.setFilterExtensions(new String[] {"*.zip"});
				//dialog.setMessage("Select Spring Boot installation directory");
				dialog.setText("Boot Zip File");
				String filePath = dialog.open();
				handleBrowseButtonResult(filePath);
			}
		});
		//GridDataFactory.fillDefaults().align(SWT.END, SWT.END).applyTo(browseButton);
		
		setTitle("Configure Boot Installation");
		//TODO: wizard banner image
		//setTitleImage(RooUiActivator.getImageDescriptor("icons/full/wizban/roo_wizban.png").createImage());

		Dialog.applyDialogFont(composite);
		return composite;
	}

	/**
	 * Called after user clicks one of the two browse buttons to select a file or folder. 
	 */
	public void handleBrowseButtonResult(String filePath) {
		if (hasText(filePath)) {
			url = new File(filePath).toURI().toString();
			
			IBootInstall newInstall = installFactory.newInstall(url, null); //name=null means let domain object choose its default name
			newInstall.clearCache();
			
			urlText.setText(url); //Note: triggers validation by way of listener attached to the urlText
			
			//Don't duplicate default name generation logic here. It belongs in the 'domain' objects implementing IBootInstall
			nameText.setText(prefPage.generateName(newInstall.getName(), install));
			
			// don't trigger validation again already triggered in urlText listener.
			//validate(true);
		}
	}
	
	
	private boolean hasText(String result) {
		return result != null && !"".equals(result.trim());
	}
	
	protected void validate(boolean validateHome) {
		clearError();
		try {
			if (urlText.getText() == null || urlText.getText().equals("")) {
				setError("Select a Boot home directory or zip file");
				return;
			}
			else if (validateHome) {
				IBootInstall install = installFactory.newInstall(urlText.getText(), nameText.getText());
				IStatus status = install.validate();
				if (!status.isOK()) {
					setError(status.getMessage());
				}
				versionText.setText(install.getVersion());
	
			}
			if (nameText.getText() == null || nameText.getText().trim().equals("")) {
				setError("A name is required");
			}
			else {
				if (prefPage.isDuplicateName(nameText.getText(), install)) {
					setError("Name is not unique");
				}
			}
		} catch (Exception e) {
			BootActivator.log(e);
			setError(ExceptionUtil.getMessage(e));
		}
	}
}
