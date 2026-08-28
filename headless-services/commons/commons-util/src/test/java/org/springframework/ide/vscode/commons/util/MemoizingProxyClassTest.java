/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.util.MemoizingProxy.Builder;

import com.google.common.collect.ImmutableList;

public class MemoizingProxyClassTest {

	TestSubject proxy;
	
	public static class TestSubject {
		
		List<String> invocations = new ArrayList<>();
		String name;
		int otherConstructorArg;
		
		public TestSubject(String name, int otherConstructorArg) {
			this.name = name;
			this.otherConstructorArg = otherConstructorArg;
		}
		
		public int throwsError() throws IOException {
			invocations.add("throwsError");
			throw new IOException("Problem");
		}
		
		public String getName() {
			invocations.add("getName");
			return name;
		}
		
		public String getMessage(String someArgument) {
			invocations.add("getMessage");
			return getName() + someArgument;
		}
		
		public String getMyName() {
			invocations.add("getMyName");
			return getName();
		}
	}
	
	private TestSubject defaultTestSubject() throws Exception {
		return MemoizingProxy.builder(TestSubject.class, Duration.ofMinutes(1), CONSTRUCTOR_ARG_TYPES).newInstance("Johny", 45);
	}

	private void assertInvocations(String...expectedInvocations) {
		assertEquals(ImmutableList.copyOf(expectedInvocations), proxy.invocations);
		proxy.invocations.clear();
	}
	
	private void sleep(int millis) {
		long endTime = System.currentTimeMillis()+millis;
		long timeLeft = endTime - System.currentTimeMillis();
		while (timeLeft > 0) {
			try {
				Thread.sleep(timeLeft);
			} catch (InterruptedException e) {
			}
			timeLeft = endTime - System.currentTimeMillis();
		}
	}

	private static final Class<?>[] CONSTRUCTOR_ARG_TYPES = {
			String.class, int.class
	};
	
	@Test
	public void constructorCalled() throws Exception {
		this.proxy = defaultTestSubject();
		assertEquals(proxy.name, "Johny"); //Constructor was called so name should be set
		assertEquals(proxy.otherConstructorArg, 45); //Constructor was called so name should be set
	}

	@Test
	public void zeroArgMethodCached() throws Exception {
		this.proxy = defaultTestSubject();
		assertEquals(proxy.getName(), "Johny");
		assertInvocations("getName");
		assertEquals(proxy.getName(), "Johny");
		assertInvocations(/*NONE*/);
	}

	@Test public void exceptionsCached() throws Exception {
		this.proxy = defaultTestSubject();
		callMethodThatThrows();
		assertInvocations("throwsError");
		callMethodThatThrows();
		assertInvocations(/*NONE*/);
	}

	private void callMethodThatThrows() {
		try {
			this.proxy.throwsError();
			fail("should have thrown");
		} catch (IOException e) {
			assertEquals("Problem", e.getMessage());
		}
	}
	
	@Test
	public void methodWithArgumentNotCached() throws Exception {
		this.proxy = defaultTestSubject();
		assertInvocations(/*NONE*/);
		
		assertEquals(proxy.getMessage(" whatever"), "Johny whatever");
		assertInvocations("getMessage", "getName");
		
		assertEquals(proxy.getMessage(" whatever"), "Johny whatever");
		assertInvocations("getMessage");
		
	}

	@Test
	public void callsViaThisCached() throws Exception {
		this.proxy = defaultTestSubject();
		assertInvocations(/*NONE*/);
		
		assertEquals(proxy.getMessage(" whatever"), "Johny whatever");
		assertInvocations("getMessage", "getName");
		
		assertEquals(proxy.getMyName(), "Johny");
		assertInvocations("getMyName");
	}
	
	@Test public void cacheExpires() throws Exception {
		this.proxy = MemoizingProxy.builder(TestSubject.class, Duration.ofMillis(10), CONSTRUCTOR_ARG_TYPES).newInstance("Johny", 45);
		assertEquals("Johny", proxy.getMyName());
		assertInvocations("getMyName", "getName");
		sleep(20);
		assertEquals("Johny", proxy.getMyName());
		assertInvocations("getMyName", "getName");
	}
	
	@Test public void proxyClassReused() throws Exception {
		//Using the new 'builder' api allows re-using the same class (if used properly)
		Builder<TestSubject> builder = MemoizingProxy.builder(TestSubject.class, Duration.ofMinutes(1), CONSTRUCTOR_ARG_TYPES);
		TestSubject proxy1 = builder.newInstance("Freddy", 12);
		TestSubject proxy2 = builder.newInstance("Johny", 45);
		assertFalse(proxy1.equals(proxy2)); // different instance...
		assertEquals(proxy1.getClass(), proxy2.getClass()); //same class
	}

	@Test public void multiThreaded() throws Exception {
		this.proxy = defaultTestSubject();
		ExecutorService manyThreads = Executors.newFixedThreadPool(100);
		Future<?>[] futures = new Future<?>[1000]; 
		
		for (int i = 0; i < futures.length; i++) {
			futures[i] = manyThreads.submit(() -> {
				assertEquals("Johny", proxy.getMyName());
			});
		}
		
		for (Future<?> future : futures) {
			future.get();
		}
		
		//Should only have one call to each method all the rest should hit the cache
		assertInvocations("getMyName", "getName");
		
		manyThreads.shutdown();
	}

}
