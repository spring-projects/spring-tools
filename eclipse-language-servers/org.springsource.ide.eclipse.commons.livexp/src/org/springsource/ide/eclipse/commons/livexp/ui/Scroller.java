/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.forms.widgets.SharedScrolledComposite;
import org.eclipse.ui.progress.UIJob;

public class Scroller extends SharedScrolledComposite implements Reflowable {

	private UIJob reflowJob;

	public Scroller(Composite parent) {
		this(parent, SWT.V_SCROLL | SWT.H_SCROLL);
	}

	public Scroller(Composite parent, int style) {
		super(parent, style);

		setFont(parent.getFont());

//		fToolkit= JavaPlugin.getDefault().getDialogsFormToolkit();

		setExpandHorizontal(true);
		setExpandVertical(true);

		Composite body= new Composite(this, SWT.NONE);
		body.setFont(parent.getFont());
		setContent(body);
	}

	public Composite getBody() {
		return (Composite) getContent();
	}

	@Override
	public boolean reflow() {
		return reflowAsync();
	}
	
	public boolean reflowAsync() {
		if (reflowJob == null) {
			reflowJob = new UIJob(Display.getDefault(), "Reflow Wizard Contents") {
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					// Must guard against widget disposed, as
					// this is an asynch operation, and it is possible that
			        // the widget may get disposed after scheduling a job, but before it can run
					if (!isDisposed()) {
						reflow(true);
					}
					return Status.OK_STATUS;
				}
			};
			reflowJob.setSystem(true);
		}
		reflowJob.schedule();
		return true;
	}

}
