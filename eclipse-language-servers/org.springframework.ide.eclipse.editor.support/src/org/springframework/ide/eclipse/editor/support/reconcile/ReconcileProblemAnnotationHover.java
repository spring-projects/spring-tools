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
package org.springframework.ide.eclipse.editor.support.reconcile;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;

public class ReconcileProblemAnnotationHover extends AbstractAnnotationHover<ReconcileProblemAnnotation> {

	public ReconcileProblemAnnotationHover(ISourceViewer sourceViewer, QuickfixContext context) {
		super(sourceViewer, context, ReconcileProblemAnnotation.class);
	}

	@Override
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		IAnnotationModel model = sourceViewer.getAnnotationModel();
		ReconcileProblemAnnotation annot = getAnnotationAt(model, hoverRegion.getOffset());
		if (annot != null) {
			return new SpringPropertyProblemHoverInfo(annot.getSpringPropertyProblem(), context);
		}
		return null;
	}

}
