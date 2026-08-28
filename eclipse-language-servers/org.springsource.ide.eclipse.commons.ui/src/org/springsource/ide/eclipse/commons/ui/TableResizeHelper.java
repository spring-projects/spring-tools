/*******************************************************************************
 * Copyright (c) 2013-2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.ui;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

/**
 * Resizes table columns such that columns fit the table width. The table is
 * initially resized when the resize is enabled, and its columns continue being
 * resized automatically as the user resizes the table.
 *
 */
public class TableResizeHelper {

	private final TableViewer tableViewer;

	public TableResizeHelper(TableViewer tableViewer) {
		this.tableViewer = tableViewer;
	}

	public void enableResizing() {
		ControlListener resizeListener = new ControlListener() {
			public void controlResized(ControlEvent e) {
				resizeTable();
			}
			public void controlMoved(ControlEvent e) {
			}
		};
		tableViewer.getTable().addControlListener(resizeListener);
		TableColumn[] cols = tableViewer.getTable().getColumns();
		if (cols != null) {
			for (int i = 0; i < cols.length-1; i++) {
				cols[i].addControlListener(resizeListener);
			}
		}

		// Initial resize of the columns
		resizeTable();

	}

	protected void resizeTable() {
		Composite tableComposite = tableViewer.getTable();//.getParent();
		Rectangle tableCompositeArea = tableComposite.getClientArea();
		int width = tableCompositeArea.width;
//		ScrollBar sb = tableViewer.getTable().getVerticalBar();
//		if (sb != null && sb.isVisible()) {
//			width = width - sb.getSize().x;
//		}
		resizeTableColumns(width, tableViewer.getTable());
	}

	protected void resizeTableColumns(int tableWidth, Table table) {
		TableColumn[] tableColumns = table.getColumns();
		int[] columnsOrder = table.getColumnOrder();

		if (tableColumns.length == 0) {
			return;
		}

		int total = 0;

		for (TableColumn column : tableColumns) {
			total += column.getWidth();
		}

		TableColumn lastColumn = tableColumns[columnsOrder[columnsOrder.length - 1]];
		int newWidth = (tableWidth - total) + lastColumn.getWidth();
		if (newWidth > 0) {
			lastColumn.setWidth(newWidth);
		}

	}

}
