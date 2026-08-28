/*******************************************************************************
 * Copyright (c) 2015, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.sections;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.PlatformUI;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.labels.BootDashLabels;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveProcessCommandsExecutor;
import org.springframework.ide.eclipse.boot.dash.livexp.ElementwiseListener;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelectionSource;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ModelStateListener;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.ButtonModel;
import org.springframework.ide.eclipse.boot.dash.model.ModifiableModel;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.util.HiddenElementsLabel;
import org.springframework.ide.eclipse.boot.dash.util.MenuUtil;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashAction;
import org.springframework.ide.eclipse.boot.dash.views.AddRunTargetAction;
import org.springframework.ide.eclipse.boot.dash.views.BootDashActions;
import org.springframework.ide.eclipse.boot.dash.views.RunStateAction;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.PageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;
import org.springsource.ide.eclipse.commons.livexp.ui.util.ReflowUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.ui.UiUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Displays all runtargets and elements in a single 'unified' tree viewer.
 *
 * @author Kris De Volder
 */
public class BootDashUnifiedTreeSection extends PageSection implements MultiSelectionSource {

	private static final boolean DEBUG = false;

	private <T> void debug(final String name, LiveExpression<T> watchable) {
		if (DEBUG) {
			watchable.addListener(new ValueListener<T>() {
				public void gotValue(LiveExpression<T> exp, T value) {
					System.out.println(name +": "+ value);
				}
			});
		}
	}

	protected static final Object[] NO_OBJECTS = new Object[0];

	private MenuManager menuMgr;
	private CustomTreeViewer tv;
	private BootDashViewModel model;
	private MultiSelection<Object> mixedSelection; // selection that may contain section or element nodes or both.
	private MultiSelection<BootDashElement> selection;
	private LiveExpression<BootDashModel> sectionSelection;
	private BootDashActions actions;
	private LiveExpression<Filter<BootDashElement>> searchFilterModel;
	private Stylers stylers;
	private final SimpleDIContext context;


	private final IPropertyChangeListener THEME_LISTENER = new IPropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			switch (event.getProperty()) {
			case BootDashLabels.TEXT_DECORATION_COLOR_THEME:
			case BootDashLabels.ALT_TEXT_DECORATION_COLOR_THEME:
			case BootDashLabels.MUTED_TEXT_DECORATION_COLOR_THEME:
			case JFacePreferences.HYPERLINK_COLOR:
				if (!tv.getTree().isDisposed()) {
					tv.refresh(true);
				}
				break;
			}
		}
	};

	public static class BootModelViewerSorter extends ViewerComparator {

		private final BootDashViewModel viewModel;

		public BootModelViewerSorter(BootDashViewModel viewModel) {
			this.viewModel = viewModel;
		}

		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			if (e1 instanceof BootDashModel && e2 instanceof BootDashModel) {
				return this.viewModel.getModelComparator().compare((BootDashModel) e1, (BootDashModel) e2);
			} else if (e1 instanceof BootDashElement && e2 instanceof BootDashElement) {
				BootDashElement bde1 = (BootDashElement) e1;
				BootDashElement bde2 = (BootDashElement) e2;
				if (bde1.getBootDashModel() == bde2.getBootDashModel()) {
					Comparator<BootDashElement> comparator = bde1.getBootDashModel().getElementComparator();
					if (comparator != null) {
						return comparator.compare(bde1, bde2);
					}
				}
			} else if (e1 instanceof ButtonModel && e2 instanceof ButtonModel) {
				return ((ButtonModel)e1).getLabel().compareTo(((ButtonModel)e2).getLabel());
			} else if (e1 instanceof ButtonModel) {
				return -1;
			} else if (e2 instanceof ButtonModel) {
				return +1;
			}
			return super.compare(viewer, e1, e2);
		}
	}

	final private ValueListener<Filter<BootDashElement>> FILTER_LISTENER = new UIValueListener<>() {
		public void uiGotValue(LiveExpression<Filter<BootDashElement>> exp, Filter<BootDashElement> value) {
			tv.refresh();
			final Tree t = tv.getTree();
			t.getDisplay().asyncExec(new Runnable() {
				public void run() {
					Composite parent = t.getParent();
					parent.layout();
				}
			});
		}
	};

	final private ElementStateListener ELEMENT_STATE_LISTENER = new ElementStateListener() {
		public void stateChanged(final BootDashElement e) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					if (tv != null && !tv.getControl().isDisposed()) {
						//tv.update(e, null);
						tv.refresh(e, true);
						tv.getControl().redraw();
					}
				}
			});
		}
	};

	final private ModelStateListener MODEL_STATE_LISTENER = new ModelStateListener() {
		public void stateChanged(final BootDashModel model) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					if (tv != null && !tv.getControl().isDisposed()) {
						tv.refresh();
						/*
						 * TODO: ideally the above should do the repaint of
						 * the control's area where the tree item is
						 * located, but for some reason repaint doesn't
						 * happen. #refresh() didn't trigger the repaint either
						 */
						tv.getControl().redraw();
					} else {
						model.removeModelStateListener(MODEL_STATE_LISTENER);
					}
				}
			});
		}
	};

	final private ValueListener<ImmutableSet<RunTarget>> RUN_TARGET_LISTENER = new UIValueListener<>() {
		protected void uiGotValue(LiveExpression<ImmutableSet<RunTarget>> exp, ImmutableSet<RunTarget> value) {
			if (tv != null && !tv.getControl().isDisposed()) {
				tv.refresh();
			}
		}
	};

	@SuppressWarnings({"rawtypes", "unchecked"}) //Raw types, because the types are getting in the way.
		//This is fine because we don't really care about the values here, so we don't really care about
		//their types either.
	private final ValueListener ELEMENTS_SET_LISTENER = new UIValueListener() {
		protected void uiGotValue(LiveExpression exp, Object value) {
			if (tv != null && !tv.getControl().isDisposed()) {
				//TODO: refreshing the whole table is overkill, but is a bit tricky to figure out which BDM
				// this set of elements belong to. If we did know then we could just refresh the node representing its section
				// only.
				tv.refresh();
			} else {
				//This listener can't easily be removed because of the intermediary adapter that adds it to a numner of different
				// things. So at least remove it when model remains chatty after view got disposed.
				exp.removeListener(this);
			}
		}
	};

	/**
	 * Listener which adds element set listener to each section model.
	 */
	@SuppressWarnings("unchecked")
	final private ValueListener<ImmutableSet<BootDashModel>> ELEMENTS_SET_LISTENER_ADAPTER = new ElementwiseListener<>() {
		protected void added(LiveExpression<ImmutableSet<BootDashModel>> exp, BootDashModel e) {
			e.getElements().addListener((ValueListener<ImmutableSet<BootDashElement>>) ELEMENTS_SET_LISTENER);
			e.getButtons().addListener((ValueListener<ImmutableSet<ButtonModel>>) ELEMENTS_SET_LISTENER);
			e.addModelStateListener(MODEL_STATE_LISTENER);
		}
		protected void removed(LiveExpression<ImmutableSet<BootDashModel>> exp, BootDashModel e) {
			e.getElements().removeListener((ValueListener<ImmutableSet<BootDashElement>>) ELEMENTS_SET_LISTENER);
			e.getButtons().removeListener((ValueListener<ImmutableSet<ButtonModel>>) ELEMENTS_SET_LISTENER);
			e.removeModelStateListener(MODEL_STATE_LISTENER);
		}
	};

	public static class CustomTreeViewer extends TreeViewer {

		private LiveVariable<Integer> hiddenElementCount = new LiveVariable<>(0);

		public CustomTreeViewer(Composite page, int style) {
			super(page, style);
		}

		@Override
		public void refresh(Object obj) {
			super.refresh(obj);
			// Every sub-tree refresh should update the hidden elements label
			int totalElements = countChildren(getRoot());
			int filteredElements = countFilteredChildren(getRoot());
			hiddenElementCount.setValue(totalElements - filteredElements);
		}

		private int countChildren(Object element) {
			int count = 0;
			for (Object o : getRawChildren(element)) {
				count += 1 + countChildren(o);
			}
			return count;
		}

		private int countFilteredChildren(Object element) {
			int count = 0;
			for (Object o : super.getFilteredChildren(element)) {
				count += 1 + countFilteredChildren(o);
			}
			return count;
		}

	}

	public BootDashUnifiedTreeSection(IPageWithSections owner, BootDashViewModel model, SimpleDIContext context) {
		super(owner);
		Assert.isNotNull(context);
		context.assertDefinitionFor(UserInteractions.class);
		this.context = context;
		this.model = model;
		this.searchFilterModel = model.getFilter();
	}

	@Override
	public void createContents(Composite page) {
		tv = new CustomTreeViewer(page, SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI);
		tv.setExpandPreCheckFilters(true);
		tv.setContentProvider(new BootDashTreeContentProvider());
		tv.setComparator(new BootModelViewerSorter(this.model));
		tv.setInput(model);
		tv.getTree().setLinesVisible(false);

		stylers = new Stylers(tv.getTree().getFont());
		tv.setLabelProvider(new BootDashTreeLabelProvider(stylers, tv));

		ColumnViewerToolTipSupport.enableFor(tv);

		GridDataFactory.fillDefaults().grab(true, true).applyTo(tv.getTree());

		new HiddenElementsLabel(page, tv.hiddenElementCount);

		tv.getControl().addControlListener(new ControlListener() {
			public void controlResized(ControlEvent e) {
				ReflowUtil.reflow(owner, tv.getControl());
			}

			public void controlMoved(ControlEvent e) {
			}
		});

		actions = new BootDashActions(model, getElementSelection(), getSectionSelection(), context, LiveProcessCommandsExecutor.getDefault());
		hookContextMenu();

		// Careful, either selection or tableviewer might be created first.
		// in either case we must make sure the listener is added when *both*
		// have been created.
		if (selection != null) {
			addViewerSelectionListener();
		}

		tv.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				if (selection != null) {
					BootDashElement selected = selection.getSingle();
					if (selected != null) {
						String url = selected.getUrl();
						if (url != null) {
							UiUtil.openUrl(url);
						}
					}
					else {
						BootDashModel section = sectionSelection.getValue();
						section.performDoubleClickAction(ui());
					}
				}
			}
		});
		tv.getTree().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent evt) {
				Point point = new Point(evt.x, evt.y);
				ViewerCell cell = tv.getCell(point);
				if (cell != null) {
					Object element = cell.getElement();
					if (element instanceof ButtonModel) {
						ButtonModel button = (ButtonModel) element;
						Job job = new Job(button.getLabel()) {
							@Override
							protected IStatus run(IProgressMonitor monitor) {
								try {
									button.perform(ui());
								} catch (Exception e) {
									Log.log(e);
								}
								return Status.OK_STATUS;
							}

						};
						job.schedule();
					}
				}
			}

		});

		model.getRunTargets().addListener(RUN_TARGET_LISTENER);
		model.getSectionModels().addListener(ELEMENTS_SET_LISTENER_ADAPTER);

		model.addElementStateListener(ELEMENT_STATE_LISTENER);

		if (searchFilterModel != null) {
			searchFilterModel.addListener(FILTER_LISTENER);
			tv.addFilter(new ViewerFilter() {
				@Override
				public boolean select(Viewer viewer, Object parentElement, Object element) {
					if (searchFilterModel.getValue() != null && element instanceof BootDashElement) {
						return searchFilterModel.getValue().accept((BootDashElement) element);
					}
					return true;
				}
			});
		}

		tv.getTree().addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				model.removeElementStateListener(ELEMENT_STATE_LISTENER);
				model.getRunTargets().removeListener(RUN_TARGET_LISTENER);
				model.getSectionModels().removeListener(ELEMENTS_SET_LISTENER_ADAPTER);
				for (BootDashModel m : model.getSectionModels().getValue()) {
					m.removeModelStateListener(MODEL_STATE_LISTENER);
				}

				if (searchFilterModel != null) {
					searchFilterModel.removeListener(FILTER_LISTENER);
				}

				if (actions != null) {
					actions.dispose();
					actions = null;
				}
				if (stylers != null) {
					stylers.dispose();
					stylers = null;
				}

				PlatformUI.getWorkbench().getThemeManager().removePropertyChangeListener(THEME_LISTENER);
			}
		});

		PlatformUI.getWorkbench().getThemeManager().addPropertyChangeListener(THEME_LISTENER);

		addDragSupport(tv);
		addDropSupport(tv);

	}

	private LiveExpression<BootDashModel> getSectionSelection() {
		if (sectionSelection == null) {
			sectionSelection = getMixedSelection().toSingleSelection().filter(BootDashModel.class);
			debug("sectionSelection", sectionSelection);
		}
		return sectionSelection;
	}

	private synchronized MultiSelection<Object> getMixedSelection() {
		if (mixedSelection == null) {
			mixedSelection = MultiSelection.from(Object.class, new ObservableSet<>() {
				@Override
				protected ImmutableSet<Object> compute() {
					if (tv != null) {
						ISelection s = tv.getSelection();
						if (s instanceof IStructuredSelection) {
							Object[] elements = ((IStructuredSelection) s).toArray();
							return ImmutableSet.copyOf(elements);
						}
					}
					return ImmutableSet.of();
				}
			});
			debug("mixedSelection", mixedSelection.getElements());
		}
		if (tv != null) {
			addViewerSelectionListener();
		}
		return mixedSelection;
	}


	@Override
	public synchronized MultiSelection<Object> getSelection() {
		return getMixedSelection();
	}

	private synchronized MultiSelection<BootDashElement> getElementSelection() {
		if (selection == null) {
			selection = getMixedSelection().filter(BootDashElement.class);
			debug("selection", selection.getElements());
		}
		return selection;
	}

	private void addViewerSelectionListener() {
		tv.setSelection(new StructuredSelection(Arrays.asList(mixedSelection.getValue().toArray())));
		tv.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				mixedSelection.getElements().refresh();
			}
		});
	}

	private void hookContextMenu() {
		this.menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(tv.getControl());
		tv.getControl().setMenu(menu);
	}


	private void fillContextMenu(IMenuManager manager) {
		for (RunStateAction a : actions.getRunStateActions()) {
			addVisible(manager, a);
		}
		addVisible(manager, actions.getOpenBrowserAction());
		addVisible(manager, actions.getOpenConsoleAction());
		addVisible(manager, actions.getOpenInPackageExplorerAction());
		addVisible(manager, actions.getShowPropertiesViewAction());
		MenuUtil.addDynamicSubmenu(manager, actions.getLiveDataConnectionManagement());

		manager.add(new Separator());

		addVisible(manager, actions.getOpenConfigAction());
		addVisible(manager, actions.getDuplicateConfigAction());
		addVisible(manager, actions.getDeleteConfigsAction());

		manager.add(new Separator());

		addSubmenu(manager, "Deploy and Run On...", BootDashActivator.getImageDescriptor("icons/run-on-cloud.png"), actions.getRunOnTargetActions());
		addSubmenu(manager, "Deploy and Debug On...", BootDashActivator.getImageDescriptor("icons/debug-on-cloud.png"), actions.getDebugOnTargetActions());
		manager.add(new Separator());

		for (AddRunTargetAction a : actions.getAddRunTargetActions()) {
			addVisible(manager, a);
		}
		manager.add(new Separator());

		addVisible(manager, actions.getConnectAction());
		addVisible(manager, actions.getRemoveRunTargetAction());
		addVisible(manager, actions.getRefreshRunTargetAction());
		addVisible(manager, actions.getDeleteAppsAction());
		addVisible(manager, actions.getEnableDevtoolsAction());
		addVisible(manager, actions.getRestartDevtoolsClientAction());
		for (IAction a : actions.getInjectedActions(AbstractBootDashAction.Location.CONTEXT_MENU)) {
			addVisible(manager, a);
		}

		manager.add(new Separator());

		ImmutableList.Builder<IAction> customizeActions = ImmutableList.builder();
		customizeActions.add(actions.getCustomizeTargetLabelAction());
		for (IAction a : actions.getInjectedActions(AbstractBootDashAction.Location.CUSTOMIZE_MENU)) {
			customizeActions.add(a);
		}
		addSubmenu(manager, "Customize...", null, customizeActions.build());
	}

	/**
	 * Adds a submenu containing a given list of actions. The menu is only added if
	 * there is at least one visible action in the list.
	 * @param imageDescriptor
	 */
	private void addSubmenu(IMenuManager parent, String label, ImageDescriptor imageDescriptor, ImmutableList<IAction> actions) {
		if (actions != null && !actions.isEmpty()) {
			boolean notEmpty = false;
			MenuManager submenu = new MenuManager(label);
			for (IAction a : actions) {
				notEmpty |= addVisible(submenu, a);
			}
			if (notEmpty) {
				submenu.setImageDescriptor(imageDescriptor);
				parent.add(submenu);
			}
		}
	}

	public static boolean addVisible(IMenuManager manager, IAction a) {
		if (a != null && isVisible(a)) {
			manager.add(a);
			return true;
		}
		return false;
	}

	public static boolean isVisible(IAction a) {
		if (a instanceof AbstractBootDashAction) {
			return ((AbstractBootDashAction) a).isVisible();
		}
		return true;
	}

	private void addDragSupport(final TreeViewer viewer) {
		int ops = DND.DROP_COPY;

		final Transfer[] transfers = new Transfer[] { LocalSelectionTransfer.getTransfer() };

		DragSourceAdapter listener = new DragSourceAdapter() {

//			@Override
//			public void dragSetData(DragSourceEvent event) {
//				IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
//				event.data = selection.getFirstElement();
//				LocalSelectionTransfer.getTransfer().setSelection(selection);
//			}
//
//			@Override
//			public void dragStart(DragSourceEvent event) {
//				if (event.detail == DND.DROP_NONE || event.detail == DND.DROP_DEFAULT) {
//					event.detail = DND.DROP_COPY;
//				}
//				dragSetData(event);
//			}

			@Override
			public void dragSetData(DragSourceEvent event) {
				Set<BootDashElement> selection = getElementSelection().getValue();
				BootDashElement[] elements = selection.toArray(new BootDashElement[selection.size()]);
				LocalSelectionTransfer.getTransfer().setSelection(new StructuredSelection(elements));
				event.detail = DND.DROP_COPY;
			}

			@Override
			public void dragStart(DragSourceEvent event) {
				if (!canDeploySelection(getElementSelection().getValue())) {
					event.doit = false;
				} else {
					dragSetData(event);
				}
			}
		};
		viewer.addDragSupport(ops, transfers, listener);
	}

	private void addDropSupport(final TreeViewer tv) {
		int ops = DND.DROP_COPY;
		final LocalSelectionTransfer transfer = LocalSelectionTransfer.getTransfer();
		Transfer[] transfers = new Transfer[] { LocalSelectionTransfer.getTransfer() };
		DropTarget dropTarget = new DropTarget(tv.getTree(), ops);
		dropTarget.setTransfer(transfers);
		dropTarget.addDropListener(new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetEvent event) {
				checkDropable(event);
			}

			@Override
			public void dragOver(DropTargetEvent event) {
				checkDropable(event);
				event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL;
			}

			@Override
			public void dropAccept(DropTargetEvent event) {
				checkDropable(event);
			}

			private void checkDropable(DropTargetEvent event) {
				if (canDrop(event)) {
					event.detail = DND.DROP_COPY & event.operations;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}

			private boolean canDrop(DropTargetEvent event) {
				BootDashModel droppedOn = getDropTarget(event);
				if (droppedOn != null && droppedOn instanceof ModifiableModel) {
					ModifiableModel target = (ModifiableModel) droppedOn;
					if (transfer.isSupportedType(event.currentDataType)) {
						Object[] elements = getDraggedElements();
						if (ArrayUtils.isNotEmpty(elements) && target.canBeAdded(Arrays.asList(elements))) {
							return true;
						}
					}
				}
				return false;
			}


			/**
			 * Determines which BootDashModel a droptarget event represents (i.e. what thing
			 * are we dropping or dragging onto?
			 */
			private BootDashModel getDropTarget(DropTargetEvent event) {
				Point loc = tv.getTree().toControl(new Point(event.x, event.y));
				ViewerCell cell = tv.getCell(loc);
				if (cell != null) {
					Object el = cell.getElement();
					if (el instanceof BootDashModel) {
						return (BootDashModel) el;
					}
				}
				//Not a valid place to drop
				return null;
			}

			@Override
			public void drop(DropTargetEvent event) {
				if (canDrop(event)) {
					BootDashModel model = getDropTarget(event);
					final Object[] elements = getDraggedElements();
					if (model instanceof ModifiableModel) {
						final ModifiableModel modifiableModel = (ModifiableModel) model;
						Job job = new Job("Performing deployment to " + model.getRunTarget().getName()) {

							@Override
							protected IStatus run(IProgressMonitor monitor) {
								if (modifiableModel != null && selection != null) {
									try {
										modifiableModel.add(Arrays.asList(elements));

									} catch (Exception e) {
										ui().errorPopup("Failed to Add Element", e.getMessage());
									}
								}
								return Status.OK_STATUS;
							}

						};
						job.schedule();
					}
				}
				super.drop(event);
			}

			private Object[] getDraggedElements() {
				ISelection sel = transfer.getSelection();
				if (sel instanceof IStructuredSelection) {
					return ((IStructuredSelection)sel).toArray();
				}
				return NO_OBJECTS;
			}

		});
	}

	private boolean canDeploySelection(Set<BootDashElement> selection) {
		if (selection.isEmpty()) {
			//Careful... don't return 'true' if nothing is selected.
			return false;
		}
		for (BootDashElement e : selection) {
			if (!e.getBootDashModel().getRunTarget().canDeployAppsFrom()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return Validator.OK;
	}

	private UserInteractions ui() {
		return context.getBean(UserInteractions.class);
	}

	public MenuManager getMenuMgr() {
		return menuMgr;
	}

}
