/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.reconcile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;

/**
 * Adapts our {@link IReconcileEngine} interface to an Eclipse {@link ReconcileStrategy}.
 *
 * @author Kris De Volder
 */
public class ReconcileStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension {

	public static final SeverityProvider DEFAULT_SEVERITY_PROVIDER = new DefaultSeverityProvider();
	private ISourceViewer fViewer;
	private IReconcileEngine fEngine;

	private IDocument fDocument;
	private IProgressMonitor fProgressMonitor;
	private IProblemCollector fProblemCollector;

	public ReconcileStrategy(ISourceViewer viewer, IReconcileEngine engine) {
		this.fViewer = viewer;
		this.fEngine = engine;
	}

	/*
	 * @see org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension#initialReconcile()
	 */
	public void initialReconcile() {
		reconcile(new Region(0, fDocument.getLength()));
	}

	/*
	 * @see org.eclipse.jface.text.reconciler.IReconcilingStrategy#reconcile(org.eclipse.jface.text.reconciler.DirtyRegion,org.eclipse.jface.text.IRegion)
	 */
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		try {
			IRegion startLineInfo= fDocument.getLineInformationOfOffset(subRegion.getOffset());
			IRegion endLineInfo= fDocument.getLineInformationOfOffset(subRegion.getOffset() + Math.max(0, subRegion.getLength() - 1));
			if (startLineInfo.getOffset() == endLineInfo.getOffset())
				subRegion= startLineInfo;
			else
				subRegion= new Region(startLineInfo.getOffset(), endLineInfo.getOffset() + Math.max(0, endLineInfo.getLength() - 1) - startLineInfo.getOffset());

		} catch (BadLocationException e) {
			subRegion= new Region(0, fDocument.getLength());
		}
		reconcile(subRegion);
	}

	/*
	 * @see org.eclipse.jface.text.reconciler.IReconcilingStrategy#reconcile(org.eclipse.jface.text.IRegion)
	 */
	public void reconcile(IRegion region) {
		if (getAnnotationModel() == null || fProblemCollector == null)
			return;
		//Note: This isn't an 'incremental' reconciler. It always checks the whole document. The dirty
		// region is ignored.
		fEngine.reconcile(fDocument, fProblemCollector, fProgressMonitor);
	}

	/**
	 * Returns the annotation model to be used by this reconcile strategy.
	 *
	 * @return the annotation model of the underlying editor input or
	 *         <code>null</code> if none could be determined
	 */
	protected IAnnotationModel getAnnotationModel() {
		return fViewer.getAnnotationModel();
	}

	/*
	 * @see org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension#setProgressMonitor(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public final void setProgressMonitor(IProgressMonitor monitor) {
		fProgressMonitor= monitor;
	}

	@Override
	public void setDocument(IDocument document) {
		fDocument= document;
		fProblemCollector= createProblemCollector();
	}

	protected IDocument getDocument() {
		return fDocument;
	}

	protected IProblemCollector createProblemCollector() {
		IAnnotationModel model= getAnnotationModel();
		if (model == null)
			return null;
		return new SeverityAwareProblemCollector(model);
	}


	/**
	 * Problem collector.
	 */
	private class SeverityAwareProblemCollector implements IProblemCollector {

		/** Annotation model. */
		private IAnnotationModel fAnnotationModel;

		/** Annotations to add. */
		private Map<Annotation, Position> fAddAnnotations;

		/** Lock object for modifying the annotations. */
		private Object fLockObject;

		/**
		 * Initializes this collector with the given annotation model.
		 *
		 * @param annotationModel the annotation model
		 */
		public SeverityAwareProblemCollector(IAnnotationModel annotationModel) {
			Assert.isLegal(annotationModel != null);
			fAnnotationModel= annotationModel;
			if (fAnnotationModel instanceof ISynchronizable)
				fLockObject= ((ISynchronizable)fAnnotationModel).getLockObject();
			else
				fLockObject= fAnnotationModel;
		}

		public void accept(ReconcileProblem problem) {
			ProblemSeverity severity = getSeverities().getSeverity(problem);
			String annotationType = ReconcileProblemAnnotation.getAnnotationType(severity);
			if (annotationType != null) {
				fAddAnnotations.put(new ReconcileProblemAnnotation(annotationType, problem), new Position(problem.getOffset(), problem.getLength()));
			}
		}

		public void beginCollecting() {
			getSeverities().startSession();
			fAddAnnotations= new HashMap<>();
		}

		public void endCollecting() {
			List<Annotation> toRemove= new ArrayList<>();
			synchronized (fLockObject) {
				Iterator<Annotation> iter= fAnnotationModel.getAnnotationIterator();
				while (iter.hasNext()) {
					Annotation annotation= iter.next();
					if (ReconcileProblemAnnotation.TYPES.contains(annotation.getType()))
						toRemove.add(annotation);
				}
				Annotation[] annotationsToRemove= toRemove.toArray(new Annotation[toRemove.size()]);

				if (fAnnotationModel instanceof IAnnotationModelExtension)
					((IAnnotationModelExtension)fAnnotationModel).replaceAnnotations(annotationsToRemove, fAddAnnotations);
				else {
					for (int i= 0; i < annotationsToRemove.length; i++)
						fAnnotationModel.removeAnnotation(annotationsToRemove[i]);
					for (iter= fAddAnnotations.keySet().iterator(); iter.hasNext();) {
						Annotation annotation= iter.next();
						fAnnotationModel.addAnnotation(annotation, fAddAnnotations.get(annotation));
					}
				}
			}

			fAddAnnotations= null;
		}
	}

	protected SeverityProvider getSeverities() {
		return DEFAULT_SEVERITY_PROVIDER;
	}

}
