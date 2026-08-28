///*******************************************************************************
// * Copyright (c) 2015 Pivotal, Inc.
// * All rights reserved. This program and the accompanying materials
// * are made available under the terms of the Eclipse Public License v1.0
// * which accompanies this distribution, and is available at
// * https://www.eclipse.org/legal/epl-v10.html
// *
// * Contributors:
// *   Pivotal, Inc. - initial API and implementation
// *******************************************************************************/
//package org.springframework.ide.eclipse.boot.dash.views.sections;
//
//import java.util.Collections;
//import java.util.Set;
//
//import org.eclipse.jface.layout.GridDataFactory;
//import org.eclipse.jface.util.LocalSelectionTransfer;
//import org.eclipse.swt.SWT;
//import org.eclipse.swt.dnd.DND;
//import org.eclipse.swt.dnd.DropTarget;
//import org.eclipse.swt.dnd.Transfer;
//import org.eclipse.swt.layout.FillLayout;
//import org.eclipse.swt.layout.GridLayout;
//import org.eclipse.swt.widgets.Composite;
//import org.eclipse.swt.widgets.Control;
//import org.eclipse.ui.forms.events.ExpansionEvent;
//import org.eclipse.ui.forms.events.IExpansionListener;
//import org.eclipse.ui.forms.widgets.ExpandableComposite;
//import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
//import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelectionSource;
//import org.springframework.ide.eclipse.boot.dash.livexp.ObservableSet;
//import org.springframework.ide.eclipse.boot.dash.livexp.ui.ReflowUtil;
//import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
//import org.springframework.ide.eclipse.boot.dash.model.ModifiableModel;
//import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
//import org.springframework.ide.eclipse.boot.dash.views.ViewStyler;
//import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
//import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
//import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
//import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
//import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
//import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
//import org.springsource.ide.eclipse.commons.livexp.ui.PageSection;
//
///**
// * Section containing an ExpandableComposite that contains another
// * section which is shown/hidden inside the expandable composite.
// * <p>
// * The contained page section is assumed to be a SelectionSource<T>
// * and so the expandable composite is as well. The 'selection' in
// * the child are propagated as the selection of the composite,
// * but only when the composite is in the 'expanded' state.
// * In non-expanded state, the composite propagates an empty
// * selection instead.
// * <p>
// * Note: This has not been used in many contexts and may not
// * be re-usable as is in contexts where it hasn't been tested.
// * The component is somewhat fiddly w.r.t. how parent composite
// * need to reflow their layout when this element is
// * expanded/collapsed.
// *
// * @author Kris De Volder
// */
//public class ExpandableSectionWithSelection extends PageSection implements MultiSelectionSource, Disposable {
//
//	private MultiSelectionSource child;
//	private String title;
//	private LiveVariable<Boolean> expansionState = new LiveVariable<Boolean>(true);
//	private MultiSelection<?> selection;
//	private ViewStyler viewStyler;
//	private BootDashModel model;
//	private UserInteractions ui;
//	private DropTarget dropTarget;
//
//	public ExpandableSectionWithSelection(IPageWithSections owner, String title, MultiSelectionSource expandableContent, ViewStyler viewStyler, BootDashModel model, UserInteractions ui) {
//		super(owner);
//		this.title = title;
//		this.child = expandableContent;
//		this.selection = null;
//		this.viewStyler = viewStyler;
//		this.model = model;
//		this.ui = ui;
//	}
//
//	protected <T> MultiSelection<?> createSelection() {
//		if (child instanceof MultiSelectionSource) {
//			@SuppressWarnings("unchecked")
//			final MultiSelection<T> childSelection = (MultiSelection<T>) child.getSelection();
//			Class<T> selectionType = childSelection.getElementType();
//			MultiSelection<T> sel = MultiSelection.from(selectionType,
//				new ObservableSet<T>() {
//					{
//						this.dependsOn(expansionState);
//						this.dependsOn(childSelection.getElements());
//					}
//					@Override
//					protected Set<T> compute() {
//						boolean isExpanded = expansionState.getValue();
//						if (isExpanded) {
//							return childSelection.getValue();
//						}
//						return Collections.emptySet();
//					}
//				}
//			);
//			return sel;
//		} else {
//			//child is not a selection source
//			return MultiSelection.empty(Object.class);
//			//                         ^^^^^ Object is wrong, the type we'd want here is the type of 'null'. I.e. a type that can
//			// be assigned to any reference. But I don't beleave there is a java.lang.Class representing that.
//		}
//	}
//
//	@Override
//	public LiveExpression<ValidationResult> getValidator() {
//		return OK_VALIDATOR;
//	}
//
//	private void addDropSupport(Composite composite) {
//
//		if (model instanceof ModifiableModel && model.getRunTarget().canDeployAppsTo()) {
//			int ops = DND.DROP_COPY | DND.DROP_LINK | DND.DROP_DEFAULT;
//			Transfer[] transfers = new Transfer[] { LocalSelectionTransfer.getTransfer() };
//
//			dropTarget = new DropTarget(composite, ops);
//			dropTarget.setTransfer(transfers);
//			dropTarget.addDropListener(new ModelDropListener(model, ui));
//		}
//	}
//
//	@Override
//	public void createContents(final Composite page) {
//		final ExpandableComposite comp = new ExpandableComposite(page, SWT.NONE);
//
//		addDropSupport(comp);
//
//		GridDataFactory.fillDefaults().grab(true, false).applyTo(comp);
//		comp.setText(title);
//		comp.setLayout(new FillLayout());
//		comp.addExpansionListener(new IExpansionListener() {
//			public void expansionStateChanging(ExpansionEvent e) {
//			}
//			@Override
//			public void expansionStateChanged(ExpansionEvent e) {
//				expansionState.setValue(comp.isExpanded());
//				ReflowUtil.reflow(owner, comp);
//			}
//		});
//		expansionState.addListener(new ValueListener<Boolean>() {
//			public void gotValue(LiveExpression<Boolean> exp, Boolean value) {
//				if (value != null && comp != null && !comp.isDisposed()) {
//					boolean newState = value;
//					boolean currentState = comp.isExpanded();
//					if (currentState != newState) {
//						comp.setExpanded(newState);
//					}
//				}
//			}
//		});
//
//		Composite client = new Composite(comp, SWT.NONE);
//		client.setLayout(new GridLayout());
//
//		viewStyler.applyBackground(new Control[]{comp, client});
//
//		child.createContents(client);
//		comp.setClient(client);
//	}
//
//	public LiveVariable<Boolean> getExpansionState() {
//		return expansionState;
//	}
//
//	@Override
//	public synchronized MultiSelection<?> getSelection() {
//		if (selection == null) {
//			selection = createSelection();
//		}
//		return selection;
//	}
//
//	@Override
//	public void dispose() {
//		if (child != null) {
//			if (child instanceof Disposable) {
//				((Disposable) child).dispose();
//			}
//			child = null;
//		}
//		if (dropTarget != null && !dropTarget.isDisposed()) {
//			dropTarget.dispose();
//		}
//	}}
