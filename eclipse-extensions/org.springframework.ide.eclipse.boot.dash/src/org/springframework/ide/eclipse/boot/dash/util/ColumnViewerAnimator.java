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
package org.springframework.ide.eclipse.boot.dash.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.progress.UIJob;

/**
 * A ColumnViewerAnimator manages a Job that periodically updates labels for
 * 'animated' label icons in a ColumnViewer (actually, only TableViewer or
 * TreeViewer are supported with current implementation).
 *
 * @author Kris De Volder
 */
public class ColumnViewerAnimator {

	/**
	 * Target provides abstraction needed so that we can 'setImage' easily on ViewerCell
	 * from either Table or Tree viewer.
	 */
	private static abstract class Target {
		private static final Target NULL_TARGET = new Target(null) {
			//Using 'widget = null' means this Target behaves like a disposed widget, which
			// means that the animator will ignore / remove it and not keep a Job spinning
			// to essentially do nothing animating it.
			void setImage(Image image) {
			}
		};
		private Widget widget;

		public Target(Widget widget) {
			this.widget = widget;
		}

		static Target from(ViewerCell cell) {
			final Widget item = cell.getItem();
			final int col = cell.getColumnIndex();
			if (item instanceof TableItem) {
				return new Target(item) {
					void setImage(Image image) {
						((TableItem)item).setImage(col, image);
					}
				};
			} else if (item instanceof TreeItem) {
				return new Target(item) {
					void setImage(Image image) {
						((TreeItem)item).setImage(col, image);
					}
				};
			} else {
				return NULL_TARGET;
			}
		}

		abstract void setImage(Image image);

		public boolean isDisposed() {
			return widget == null || widget.isDisposed();
		}
	}

	public class CellAnimation {
		public final Image[] imgs;
		public final Target item; //could be TableItem or TreeItem

		public CellAnimation(ViewerCell cell, Image[] imgs) {
			this.item = Target.from(cell);
			this.imgs = imgs;
		}

	}

	protected static final long INTERVAL = 100;

	private int animationCounter = 0;

	private ColumnViewer tv;

	public ColumnViewerAnimator(ColumnViewer tv) {
		this.tv = tv;
	}

	private Map<ViewerCell, CellAnimation> animatedElements = new HashMap<ViewerCell, CellAnimation>();

	private Job job;

	public void setAnimation(ViewerCell cell, Image[] images) {
		if (images == null || images.length == 0) {
			cell.setImage(null);
			stopAnimation(cell);
		} else if (images.length == 1) {
			stopAnimation(cell);
			cell.setImage(images[0]);
		} else {
			cell.setImage(currentImage(images));
			startAnimation(cell, images);
		}
	}

	private synchronized void stopAnimation(Object e) {
		animatedElements.remove(e);
	}

	private synchronized void startAnimation(ViewerCell cell, Image[] imgs) {
		animatedElements.put(cell, new CellAnimation(cell, imgs));
		ensureJob();
		job.schedule();
	}

	private synchronized CellAnimation[] getAnimations() {
		//Copy elements to avoid CME.
		return animatedElements.values().toArray(new CellAnimation[animatedElements.size()]);
	}

	private void ensureJob() {
		if (job == null) {
			job = new UIJob("Animate table icons") {
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					if (!tv.getControl().isDisposed()) {
						animationCounter++;
						for (CellAnimation a : getAnimations()) {
							Image[] imgs = a.imgs;
							if (a.item.isDisposed()) {
								//See bug: https://www.pivotaltracker.com/story/show/100608788
								stopAnimation(a);
							} else {
								a.item.setImage(imgs[animationCounter%imgs.length]);
							}
						}
						if (job != null && animatedElements.size() > 0) {
							job.schedule(INTERVAL);
						}
					}
					return Status.OK_STATUS;
				}

			};
			job.setSystem(true);
		}
	}

	private Image currentImage(Image[] images) {
		return images[animationCounter%images.length];
	}

	public void dispose() {
		job = null;
	}

}