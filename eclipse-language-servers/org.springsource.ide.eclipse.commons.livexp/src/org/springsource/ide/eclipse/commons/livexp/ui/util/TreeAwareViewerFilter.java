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
package org.springsource.ide.eclipse.commons.livexp.ui.util;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.ui.progress.UIJob;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.livexp.util.Filters;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;

/**
 * A wrapper around a Filter, adapting the filter to become 'tree aware' so it can
 * be used as ViewerFilter for a TreeViewer.
 * <p>
 * The resulting filter uses these two rules:
 * <p>
 * a) If the wrapped filter accepts a child, then the resulting filter must also
 * accept the child's parent (and grandparent etc.)
 * <p>
 * b) If the wrapped filter accepts a (parent) node, then the resulting filter
 * must also accept all the children (and grandchildren etc.) of that node.
 *
 * @author Kris De Volder
 */
public class TreeAwareViewerFilter extends ViewerFilter {

	private static final boolean DEBUG = false;
	
	private Filter<String> baseFilter;
	private LabelProvider labels;

	private Cache<Object, Boolean> baseAcceptsChild = CacheBuilder.newBuilder().build();
	private ITreeContentProvider treeContent;
	private TreeViewer viewer;

	public TreeAwareViewerFilter(TreeViewer viewer, Filter<String> baseFilter, LabelProvider labels, ITreeContentProvider treeContent) {
		this.viewer = viewer;
		this.labels = labels;
		this.treeContent = treeContent;
		setFilter(baseFilter);
	}

	class UpdateExpansionStates extends UIJob {
		
		Set<Object> toExpand = new HashSet<>();
		Set<Object> toCollapse = new HashSet<>();

		public UpdateExpansionStates() {
			super("Update Tree Viewer Expansions After Search");
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			if (toExpand.isEmpty() && toCollapse.isEmpty()) {
				//No work to be done.
				return Status.OK_STATUS;
			} 
			HashSet<Object> expandedElements = new HashSet<>(ImmutableList.copyOf(viewer.getExpandedElements()));
			synchronized (this) {
				expandedElements.addAll(toExpand);
				expandedElements.removeAll(toCollapse);
				if (DEBUG) {
					debug("expanding ...");
					for (Object object : toExpand) {
						debug("   "+labels.getText(object));
					}
					debug("collapsing ...");
					for (Object object : toCollapse) {
						debug("   "+labels.getText(object));
					}
				}
				toExpand.clear();
				toCollapse.clear();
			}
			viewer.setExpandedElements(expandedElements.toArray());
			return Status.OK_STATUS;
		}

		public synchronized void collapseElement(Object e) {
			toExpand.remove(e);
			toCollapse.add(e);
		}

		public synchronized void expandElement(Object e) {
			while (e != null) {
				toExpand.add(e);
				toCollapse.remove(e);
				e = treeContent.getParent(e);
			}
			schedule();
		}

		public void setExpanded(Object e, boolean expand) {
			if (expand) {
				expandElement(e);
			} else {
				collapseElement(e);
			}
		}
	}
	
	UpdateExpansionStates updateExpansionStates = new UpdateExpansionStates();
	
	private synchronized boolean accept(Object e) {
		//Useful special case, avoid lots of work if the filter just accepts everything anyways.
		if (baseFilter.isTrivial()) {
			//Note: in this case we deliberately skip auto collapse / expand logic.
			// Unless something is entered in the search box,only the user is in control 
			// of expanding/collapsing elements
			return true;
		}
//		return debug("accept "+labels.getText(e), () -> {
			boolean baseAccepts = baseAccepts(e);
			boolean accepts = baseAccepts || baseAcceptsParent(e) || baseAcceptsChild(e);
			if (accepts) {
				//Yes, its fine to call baseAcceptsChild a second time because it is cached.
				updateExpansionStates.setExpanded(e, baseAccepts || baseAcceptsChild(e));
			}
			return accepts;
//		});
	}

	private boolean baseAcceptsParent(Object e) {
		Object parent = treeContent.getParent(e);
		if (parent != null) {
			return baseAccepts(parent) || baseAcceptsParent(parent);
		}
		return false;
	}
	
	private boolean baseAcceptsChild(Object e) {
//		return debug("baseAcceptsChild "+labels.getText(e), () -> {
			try {
				return baseAcceptsChild.get(e, () -> {
					Object[] children = treeContent.getChildren(e);
					if (children != null) {
						for (Object c : children) {
							if (baseAccepts(c)) {
								return true;
							}
						}
						//Also check children's children
						for (Object c : children) {
							if (baseAcceptsChild(c)) {
								return true;
							}
						}
					}
					return false;
				});
			} catch (Exception er) {
				Log.log(er);
				return false;
			}
//		});
	}

	private boolean baseAccepts(Object e) {
		String label = labels.getText(e);
		if (label == null) {
			label = "";
		}
		return baseFilter.accept(label);
	}

	public synchronized void setFilter(Filter<String> baseFilter) {
		this.baseAcceptsChild.invalidateAll();
		this.baseFilter = baseFilter == null ? Filters.acceptAll() : baseFilter;
	}
	
	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		return accept(element);
	}
	
/////////////////////////
/// For debugging 
///

//	private static int indent = 0;

	private static void debug(String msg) {
		if (DEBUG) {
			System.out.println(msg);
		}
	}
	
//	private <T> T debug(String string, Supplier<T> callable) {
//		debug_in(string);
//		T r = callable.get();
//		debug_out(string +" => "+r);
//		return r;
//	}
//
//	private void debug_in(String string) {
//		for (int i = 0; i < indent; i++) {
//			System.out.print("  ");
//		}
//		System.out.println("> "+string);
//		indent++;
//	}
//
//	private void debug_out(String string) {
//		indent--;
//		for (int i = 0; i < indent; i++) {
//			System.out.print("  ");
//		}
//		System.out.println("< "+string);
//	}

}
