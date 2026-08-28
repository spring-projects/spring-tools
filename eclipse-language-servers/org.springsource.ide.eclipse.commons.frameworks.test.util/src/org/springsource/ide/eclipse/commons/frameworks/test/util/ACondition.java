/*******************************************************************************
 * Copyright (c) 2012, 2018 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.test.util;

import static org.junit.Assert.fail;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.Assert;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

import junit.framework.AssertionFailedError;


/**
 * Abstract class to create a "waitable" condition. This is to be used in implementing
 * tests where we want some condition to be true eventually, but cannot be sure it will be
 * true immediately.
 * <p>
 * Typical use would like so:
 * <code>
 * new ACondition() {
 * public boolean test() throws Exception {
 * ... some asserts ...
 * return ...something to test...;
 * }
 * }.waitFor(4000); // wait for 4 seconds or until test passes.
 * </code>
 * An exception will be thrown if the test method does not return true before the
 * timeout limit.
 * <p>
 * If the test method throws an exception this is treated the same as returning false.
 * If condition fails, we will try to rethrow a pertinent exception (typically the
 * exception thrown by the test method, the last time we tried to run it.
 *
 * @author Kris De Volder
 */
public abstract class ACondition {

	private String description = null;

	/**
	 * This method is deprecated because it is a common problem that client calls it
	 * and then forgets to call the waitFor method on the created instance. This results
	 * in test code that looks like it is testing something but doesn't because the
	 * asserts inside the ACondition are never actually executed.
	 * <p>
	 * Clients should instead call one of the constructor that takes a timeout
	 * as a parameter (which does the to waitFor automatically).
	 */
	@Deprecated
	public ACondition() {
	}

	/**
	 * This method is deprecated because it is a common problem that client calls it
	 * and then forgets to call the waitFor method on the created instance. This results
	 * in test code that looks like it is testing something but doesn't because the
	 * asserts inside the ACondition are never actually executed.
	 * <p>
	 * Clients should instead call one of the constructor that takes a timeout
	 * as a parameter (which does the to waitFor automatically).
	 */
	@Deprecated
	public ACondition(String description) {
		this.description = description;
	}

	public ACondition(long timeout) throws Exception {
		this();
		waitFor(timeout);
	}

	public ACondition(String description, long timeout) throws Exception {
		this(description);
		waitFor(timeout);
	}

	Throwable e = null;

	/**
	 * Deprecated. Use one of the constructors that accepts a timeout value and automatically
	 * calls waitFor.
	 */
	@Deprecated
	public void waitFor(long timeout) throws Exception {
		long SLEEP_FOR = Math.min(2000, timeout/5);
		long startTime = System.currentTimeMillis();
		long endTime = startTime + timeout;
		boolean result = false;
		StsTestUtil.waitForDisplay(); //Make sure this gets called at least once to avoid 'UI thread starvation'.
		while (!(result = doTest()) && System.currentTimeMillis() < endTime) {
			StsTestUtil.waitForDisplay(); // Avoids UI deadlock by allowing UI to process events.
			try {
				Thread.sleep(SLEEP_FOR);
			} catch (InterruptedException e) {
			}
		}
		if (!result) {
			//Try our best to create a 'nice' exception reflecting the reason for the test failure
			System.err.println("ACondition "+describe()+" timed out. Dumping current Thread stacks...\n" +
					StsTestUtil.getStackDumps()
			);
			if (e != null) {
				if (e instanceof Exception) {
					throw (Exception)e;
				} else if (e instanceof Error) {
					throw (Error)e;
				} else {
					throw new RuntimeException(e);
				}
			} else {
				throw new RuntimeException(getMessage());
			}
		}
		if (description != null) {
			System.out.println(description + " succeeded after: " + (System.currentTimeMillis() - startTime));
		}
	}

	private String describe() {
		if (description != null) {
			return "["+description+"]";
		}
		return "";
	}

	private boolean doTest() {
		boolean result = false;
		try {
			e = null;
			result = test();
		} catch (Throwable e) {
			this.e = e;
		}
		return result;
	}

	/**
	 * Test something. If the method returns true, the test passes.
	 * If it returns false or throws an exception the test fails (and will be
	 * retried until it passes or timeout is reached).
	 */
	public abstract boolean test() throws Exception;

	/**
	 * Message used when time out reached without an exception
	 */
	public String getMessage() {
		return "timed out";
	}

	public static void assertJobManagerIdle() {
		final IJobManager jm = Job.getJobManager();
		if (jm.isIdle()) {
			return; //OK!
		}
		//Make a nice message listing all the jobs and their present state.
		Job[] allJobs = jm.find(null);
		StringBuffer msg = new StringBuffer("JobManager not idle: \n");
		for (Job job : allJobs) {
			msg.append("   Job: "+job.getName() + " State: " + stateString(job) +"\n");
		}
		throw new AssertionFailedError(msg.toString());
	}

	public static String stateString(Job job) {
		int state = job.getState();
		switch (state) {
		case Job.RUNNING:
			return "RUNNING";
		case Job.SLEEPING:
			return "SLEEPING";
		case Job.WAITING:
			return "WAITING";
		case Job.NONE:
			return "NONE";
		default:
			return ""+state;
		}
	}

	/**
	 * A Java 8 friendlier way of using this class with a lambda.
	 * <p>
	 * Waits for some asserts to pass by periodically checking them until either:
	 * a) the asserts all pass (i.e. asserter returns without throwing); or b)
	 * the timeout is exceeded.
	 */
	public static void waitFor(String name, long timeout, final Asserter asserter) throws Exception {
		new ACondition("Wait for "+name, timeout) {
			@Override
			public boolean test() throws Exception {
				asserter.execute();
				return true;
			}
		};
	}

	/**
	 * Repeatedly check asserter for a given duration. If the asserter ever fails
	 * during that time this check fails.
	 */
	public static void expectAllways(String name, long timeout, final Asserter asserter) throws Exception {
		AtomicReference<Throwable> firstFail = new AtomicReference<>();
		try {
			waitFor(name, timeout, () -> {
				boolean success = false;
				try {
					asserter.execute();
					success = true;
				} catch (Throwable e) {
					firstFail.set(e);
				} finally {
					//First time asserter does not succeed... the 'waitFor' should end.
					Assert.assertFalse(success);
				}
			});
			//The waitfor above should fail. Reaching here means it passed.
			// Make sure we have some kind of exception. Create one if need be.
			if (firstFail.get() == null) {
				firstFail.set(new AssertionFailedError(name));
			}
		} catch (Throwable e) {
			//expected. The wait for should fail with negated asserter
		}
		Throwable e = firstFail.get();
		if (e != null) {
			if (e instanceof Exception) {
				throw (Exception)e;
			} else if (e instanceof Error) {
				throw (Error)e;
			} else {
				throw new RuntimeException(e);
			}
		}
	}

}
