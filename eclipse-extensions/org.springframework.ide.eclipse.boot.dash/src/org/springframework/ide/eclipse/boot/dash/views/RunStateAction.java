/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.swt.widgets.Display;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springsource.ide.eclipse.commons.frameworks.core.util.JobUtil;

import com.google.common.base.Objects;

/**
 * An action who's intended effect is to transition a BootDashElement to a
 * given goal state.
 *
 * @author Kris De Volder
 */
public abstract class RunStateAction extends AbstractBootDashElementsAction {

	private static final boolean DEBUG = false; //(""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	public boolean showInToolbar() {
		return true;
	}

	protected static class BdeSchedulingRule implements ISchedulingRule {

		private BootDashElement element;

		public BdeSchedulingRule(BootDashElement element) {
			this.element = element;
		}

		@Override
		public boolean contains(ISchedulingRule rule) {
			if (rule instanceof BdeSchedulingRule) {
				BootDashElement other = ((BdeSchedulingRule) rule).element;
				return Objects.equal(element, other);
			}
			return false;
		}

		@Override
		public boolean isConflicting(ISchedulingRule rule) {
			if (rule instanceof BdeSchedulingRule) {
				BootDashElement other = ((BdeSchedulingRule) rule).element;
				if (element != null && other != null) {
					return isAncestor(other, element) || isAncestor(other, element);
				}
			}
			return false;
		}

		private boolean isAncestor(BootDashElement ancestor, BootDashElement e) {
			while (e != null) {
				if (e.equals(ancestor)) {
					return true;
				} else {
					Object parent = e.getParent();
					e = parent instanceof BootDashElement ? (BootDashElement) parent : null;
				}
			}
			return false;
		}

	}

	private static final ISchedulingRule SCEDULING_RULE = JobUtil.lightRule("RunStateAction.RULE");
	final RunState goalState;
	private ElementStateListener stateListener = null;

	protected void configureJob(Job job) {
		ISchedulingRule rule = getSelectedElements().isEmpty() ? SCEDULING_RULE
				: MultiRule.combine(
						getSelectedElements().stream().map(BdeSchedulingRule::new).toArray(ISchedulingRule[]::new));
		job.setRule(rule);
	}

	public RunStateAction(Params params, RunState goalState) {
		super(params);
		debug("Create RunStateAction "+goalState);
		this.goalState = goalState;
		model.addElementStateListener(stateListener = new ElementStateListener() {
			public void stateChanged(BootDashElement e) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						updateEnablement();
					}
				});
			}
		});
	}
	@Override
	public void updateEnablement() {
		Collection<BootDashElement> selecteds = getSelectedElements();
		setEnabled(appliesTo(selecteds));
	}

	@Override
	public void updateVisibility() {
		/**
		 * TODO: Evaluate possibility of adding new API on BootDashModel and/or
		 * BootDashElement to check whether element supports run states.
		 * Currently run state == null means element doesn't support run states
		 */
		boolean visible = !getSelectedElements().isEmpty();
		for (BootDashElement e : getSelectedElements()) {
			boolean vis = isVisibleForElement(e);
			if (!vis) {
				visible = false;
				break;
			}
		}
		setVisible(visible);
	}

	protected boolean isVisibleForElement(BootDashElement e) {
		return e.getRunState() != null && e.supportedGoalStates().contains(goalState);
	}

	private boolean appliesTo(Collection<BootDashElement> selection) {
		for (BootDashElement e : selection) {
			if (!appliesTo(e)) {
				return false;
			}
		}
		return !selection.isEmpty();
	}

	private boolean appliesTo(BootDashElement e) {
		return goalStateAppliesTo(e) && currentStateAcceptable(e.getRunState()) && appliesToElement(e);
	}

	/**
	 * Subclass can override when action should only apply to
	 * certain boot dash elements
	 */
	protected boolean appliesToElement(BootDashElement e) {
		return true;
	}

	/**
	 * Subclass can override when action should only apply to
	 * processes in a specific runState.
	 */
	protected boolean currentStateAcceptable(RunState runState) {
		return true;
	}

	protected boolean goalStateAppliesTo(BootDashElement e) {
		return e.supportedGoalStates().contains(goalState);
	}


	@Override
	public String toString() {
		return "RunStateAction("+goalState+")";
	}

	/**
	 * Subclass can override to define custom 'work' this action does when it is triggered.
	 * Default implementation just calls 'setGoalState', on all selected / applicable elements.`
	 */
	protected Job createJob() {
		final Collection<BootDashElement> selecteds = getSelectedElements();
		if (!selecteds.isEmpty()) {
			return new Job("Suspending " + selecteds.size() + " Boot Dash Elements") {
				protected IStatus run(IProgressMonitor monitor) {
					monitor.beginTask("Stopping " + selecteds.size() + " Elements", selecteds.size());
					try {

						List<CompletableFuture<Void>> futures = new ArrayList<>(selecteds.size());
						for (BootDashElement el : selecteds) {
							if (appliesTo(el)) {
								futures.add(CompletableFuture.runAsync(() -> {
									try {
										el.setGoalState(goalState);
										monitor.worked(1);
									} catch (Exception e) {
										monitor.worked(1);
										throw new CompletionException(e);
									}
								}));
							}
						}
						try {
							CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get(60, TimeUnit.SECONDS);
						} catch (InterruptedException e) {
							BootActivator.createErrorStatus(e);
						} catch (ExecutionException e) {
							BootActivator.createErrorStatus(e);
						} catch (TimeoutException e) {
							BootActivator.createErrorStatus(e);
						}


						return Status.OK_STATUS;
					} finally {
						monitor.done();
					}
				}
			};
		}
		return null;
	}

	public final void run() {
		Job job = createJob();
		if (job != null) {
			configureJob(job);
			job.schedule();
		}
	}

	@Override
	public void dispose() {
		debug("DISPOSE RunStateAction "+getGoalState());
		super.dispose();
		if (stateListener != null) {
			//Avoid leaking model listeners
			model.removeElementStateListener(stateListener);
			stateListener = null;
		}
	}

	public RunState getGoalState() {
		return goalState;
	}


}
