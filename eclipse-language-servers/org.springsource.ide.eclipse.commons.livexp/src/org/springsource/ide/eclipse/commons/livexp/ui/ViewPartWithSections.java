/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.part.ViewPart;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * @author Kris De Volder
 */
public class ViewPartWithSections extends ViewPart implements UIContext, IPageWithSections, Reflowable {

	private final boolean enableScrolling;
	private Scroller scroller;
	protected Composite page;
	protected final ViewStyler viewStyler = new ViewStyler();

	public ViewPartWithSections(boolean enableScrolling) {
		super();
		this.enableScrolling = enableScrolling;
	}

	@Override
	public void createPartControl(Composite parent) {
		if (enableScrolling) {
			scroller = new Scroller(parent, SWT.V_SCROLL | SWT.H_SCROLL);
			page = scroller.getBody();
		} else {
			page = new Composite(parent, SWT.NONE);
		}
		page.setLayout(new GridLayout());

		viewStyler.applyBackground(new Control[]{page});


		for (IPageSection s : getSections()) {
			s.createContents(page);
		}
	}


	@Override
	public void setFocus() {
		page.setFocus();
	}

	////////////////////////////////////////////////////////////////////////

	@Override
	public Shell getShell() {
		return getSite().getShell();
	}

	@Override
	public IRunnableContext getRunnableContext() {
		return getSite().getWorkbenchWindow();
	}

	////////////////////////////////////////////////////////////////////////
	/// Sections... lazy creation etc.

	private List<IPageSection> sections;

	protected synchronized List<IPageSection> getSections() {
		if (sections == null) {
			sections = safeCreateSections();
		}
		return sections;
	}

	private List<IPageSection> safeCreateSections() {
		try {
			return createSections();
		} catch (CoreException e) {
			Log.log(e);
			return Arrays.asList(new IPageSection[] {
					new CommentSection(this, "View contents couldn't be created because of an unexpected error:+\n"+ExceptionUtil.getMessage(e)+"\n\n"
							+ "Check the error log for details"),
					new ValidatorSection(Validator.alwaysError(ExceptionUtil.getMessage(e)), this)
			});
		}
	}

	/**
	 * This method should be implemented to generate the contents of the page.
	 */
	protected List<IPageSection> createSections() throws CoreException {
		//This default implementation is meant to be overridden
		return Arrays.asList(new IPageSection[]{
				new CommentSection(this, "Override ViewPartWithSections.createSections() to provide real content."),
				new ValidatorSection(Validator.alwaysError("Subclass must implement validation logic"), this)
		});
	}

	@Override
	public void dispose() {
		super.dispose();
		if (sections != null) {
			for (IPageSection s : sections) {
				if (s instanceof Disposable) {
					((Disposable) s).dispose();
				}
			}
			sections = null;
		}
	}

	@Override
	public boolean reflow() {
		if (enableScrolling) {
			if (scroller != null && !scroller.isDisposed()) {
				scroller.reflow(true);
			}
		} else {
			if (page != null && !page.isDisposed()) {
				page.layout();
			}
		}
		return true;
	}

}
