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
package org.springframework.ide.eclipse.editor.support.reconcile;

import java.util.Iterator;

import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.springframework.ide.eclipse.editor.support.hover.HoverInformationControlCreator;

/**
 * Base class for annotation hovers
 *
 * @author Alex Boyko
 *
 * @param <T> either {@link Annotation} or its subclass
 */
public abstract class AbstractAnnotationHover<T extends Annotation> implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {

	final protected ISourceViewer sourceViewer;
	final protected QuickfixContext context;
	final private Class<T> annotationClass;

	public AbstractAnnotationHover(ISourceViewer sourceViewer, QuickfixContext context, Class<T> annotationClass) {
		this.sourceViewer = sourceViewer;
		this.context = context;
		this.annotationClass = annotationClass;
	}

	@Override
	public IInformationControlCreator getHoverControlCreator() {
		return new HoverInformationControlCreator(false, "F2 for focus");
	}

	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		IAnnotationModel model = sourceViewer.getAnnotationModel();
		T annot = getAnnotationAt(model, hoverRegion.getOffset());
		if (annot != null) {
			return annot.getText();
		}
		return null;
	}

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		IAnnotationModel model = sourceViewer.getAnnotationModel();
		T annot = getAnnotationAt(model, offset);
		if (annot != null) {
			Position pos = model.getPosition(annot);
			if (pos != null) {
				return new Region(pos.getOffset(), pos.getLength());
			}
		}
		return null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	final protected T getAnnotationAt(IAnnotationModel model, int offset) {
		if (model != null) {
			Iterator iter= model.getAnnotationIterator();
			T found = null;
			Position foundPos = null;
			while (iter.hasNext()) {
				Object _annotation= iter.next();
				if (annotationClass.isAssignableFrom(_annotation.getClass())) {
					T annotation = (T) _annotation;
					if (acceptAnnotation(annotation)) {
						Position pos= model.getPosition(annotation);
						if (isAtPosition(offset, pos)) {
							if (foundPos == null || pos.length < foundPos.length) {
								found = annotation;
								foundPos = pos;
							}
						}
					}
				}
			}
			return found;
		}
		return null;
	}

	protected boolean acceptAnnotation(T annotation) {
		return true;
	}

	private boolean isAtPosition(int offset, Position pos) {
		return (pos != null) && (offset >= pos.getOffset() && offset <= (pos.getOffset() +  pos.getLength()));
	}

}
