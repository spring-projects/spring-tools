/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.properties;

import static org.springsource.ide.eclipse.commons.ui.UiUtil.openUrl;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IWorkbenchPart;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.Failable;
import org.springframework.ide.eclipse.boot.dash.model.MissingLiveInfoMessages;
import org.springframework.ide.eclipse.boot.dash.model.actuator.RequestMapping;
import org.springframework.ide.eclipse.boot.dash.util.Utils;
import org.springframework.ide.eclipse.boot.dash.views.RequestMappingLabelProvider;
import org.springframework.ide.eclipse.boot.dash.views.RequestMappingsColumn;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;
import org.springsource.ide.eclipse.commons.ui.SpringUIUtils;

import com.google.common.collect.ImmutableList;

/**
 * Tabbed properties view section for live request mappings
 *
 * @author Alex Boyko
 * @author Kris De Volder
 */
public class RequestMappingPropertiesSection extends LiveDataPropertiesSection<ImmutableList<RequestMapping>> {

	private class DoubleClickListener extends MouseAdapter {
		DoubleClickListener(TableViewer tv) {
			tv.getTable().addMouseListener(this);
		}

		@Override
		public void mouseDoubleClick(MouseEvent e) {
			ViewerCell cell = tv.getCell(new Point(e.x, e.y));
			if (cell != null) {
				Object clicked = cell.getElement();
				if (clicked instanceof RequestMapping){
					RequestMapping rm = (RequestMapping) clicked;
					int colIdx = cell.getColumnIndex();
					RequestMappingsColumn col = RequestMappingsColumn.values()[colIdx];
					switch (col) {
					case PATH:
						BootDashElement bde = getBootDashElement();
						String url = Utils.createUrl(bde.getLiveHost(), bde.getLivePort(), rm.getPath());
						if (url != null) {
							openUrl(url);
						}
						break;
					case SRC:
						IJavaElement javaElement = rm.getMethod();
						if (javaElement == null) {
							javaElement = rm.getType();
						}

						if (javaElement != null) {
							SpringUIUtils.openInEditor(javaElement);
						}
					default:
						break;
					}
	//						MessageDialog.openInformation(page.getShell(), "clickety click!",
	//								"Double-click on : "+ clicked);
				}
			}
		}

	}

	private TableViewer tv;
	private RequestMappingLabelProvider labelProvider;
	private Stylers stylers;
	private ViewerComparator sorter = new ViewerComparator() {

		 @Override
		 public int compare(Viewer viewer, Object e1, Object e2) {
			 if (e1 instanceof RequestMapping && e2 instanceof RequestMapping) {
				 RequestMapping rm1 = (RequestMapping) e1;
				 RequestMapping rm2 = (RequestMapping) e2;
				 int cat1 = getCategory(rm1);
				 int cat2 = getCategory(rm2);
				 if (cat1 != cat2) {
					 return cat1-cat2;
				 } else {
					 return rm1.getPath().compareTo(rm2.getPath());
				 }
			 }
			 return 0;
		 }

		private int getCategory(RequestMapping rm) {
			if (rm.isUserDefined()) {
				return 0;
			} else {
				return 1;
			}
		}
	};

	public void setInput(IWorkbenchPart part, ISelection selection) {
		super.setInput(part, selection);
		tv.setInput(getBootDashElement());
	}

	@Override
	public void dispose() {
		if (labelProvider != null) {
			labelProvider.dispose();
			labelProvider = null;
		}
		if (stylers != null) {
			stylers.dispose();
			stylers = null;
		}
		super.dispose();
	}

	public class ContentProvider implements IStructuredContentProvider {


		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			/*
			 * Nothing. Rely on the section refresh mechanism that should refresh the table
			 */
		}

		@Override
		public Object[] getElements(Object inputElement) {
			return data.hasFailed() ? new Object[0] : data.getValue().toArray();
		}
	}

	private void createContextMenu(Viewer viewer) {
	    MenuManager contextMenu = new MenuManager("#ViewerMenu"); //$NON-NLS-1$
	    contextMenu.setRemoveAllWhenShown(true);
	    contextMenu.addMenuListener(new IMenuListener() {
	        @Override
	        public void menuAboutToShow(IMenuManager mgr) {
	            fillContextMenu(mgr);
	        }

	    });

	    Menu menu = contextMenu.createContextMenu(viewer.getControl());
	    viewer.getControl().setMenu(menu);
	}

	private void fillContextMenu(IMenuManager contextMenu) {
		if (getStructuredSelection().size() == 1) {
			final RequestMapping rm = (RequestMapping) getStructuredSelection().getFirstElement();
			final BootDashElement bde = getBootDashElement();
			Action makeDefaultAction = new Action("Make Default") {
				@Override
				public void run() {
					bde.setDefaultRequestMappingPath(rm.getPath());
					tv.refresh();
					/*
					 * Just refresh doesn't cause repaint for some reason
					 */
					tv.getTable().redraw();
				}
			};
			makeDefaultAction.setEnabled(!rm.getPath().equals(bde.getDefaultRequestMappingPath()));
			contextMenu.add(makeDefaultAction);
		}
	}

	private IStructuredSelection getStructuredSelection() {
		//Watch out, this is not Eclipse 4.4 api:
		//return tv.getStructuredSelection();
		//So do this instead:
		return (IStructuredSelection) tv.getSelection();
	}

	@Override
	protected Control createSectionDataControls(Composite composite) {
		this.tv = new TableViewer(composite, SWT.BORDER|SWT.FULL_SELECTION/*|SWT.NO_SCROLL*/);

		tv.setContentProvider(new ContentProvider());
		tv.setComparator(sorter);
//		tv.setLabelProvider(labelProvider = new RequestMappingLabelProvider(tv.getTable().getFont(), input));
		tv.setInput(getBootDashElement());
		tv.getTable().setHeaderVisible(true);
		stylers = new Stylers(tv.getTable().getFont());

		for (RequestMappingsColumn colType : RequestMappingsColumn.values()) {
			TableViewerColumn col = new TableViewerColumn(tv, colType.getAlignment());
			col.setLabelProvider(new RequestMappingLabelProvider(stylers, getBootDashElementLiveExpression(), colType));
			TableColumn colWidget = col.getColumn();
			colWidget.setText(colType.getLabel());
			colWidget.setWidth(colType.getDefaultWidth());
		}

		createContextMenu(tv);

		new DoubleClickListener(tv);

		return tv.getControl();
	}

	@Override
	protected void refreshDataControls() {
		tv.refresh();
	}

	@Override
	protected Failable<ImmutableList<RequestMapping>> fetchData() {
		BootDashElement bde = getBootDashElement();
		if (bde != null) {
			return bde.getLiveRequestMappings();
		} else {
			return Failable.error(MissingLiveInfoMessages.noSelectionMessage("Request Mapping"));
		}
	}

}
