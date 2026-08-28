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
import org.springframework.ide.vscode.commons.util.MemoizingProxyInterfaceTest.TestInterface;

import com.google.common.collect.ImmutableList;

public class MemoizingProxyInterfaceTest {

	public interface TestInterface {
		int throwsError() throws IOException;
		String getMyName();
		String getMessage(String someArgument);
		String getName();
		int getAge();
	}

	TestInterface proxy;
	private TestSubject delegate;
	
	public static class TestSubject implements TestInterface {
		
		List<String> invocations = new ArrayList<>();
		String name;
		int age;
		
		public TestSubject(String name, int otherConstructorArg) {
			this.name = name;
			this.age = otherConstructorArg;
		}

		@Override
		public int throwsError() throws IOException {
			invocations.add("throwsError");
			throw new IOException("Problem");
		}
		
		@Override
		public String getName() {
			invocations.add("getName");
			return name;
		}
		
		@Override
		public String getMessage(String someArgument) {
			invocations.add("getMessage");
			return getName() + someArgument;
		}
		
		@Override
		public String getMyName() {
			invocations.add("getMyName");
			return getName();
		}

		@Override
		public int getAge() {
			return age;
		}
	}
	
	private void defaultTestSubject() throws Exception {
		this.delegate = new TestSubject("Johny", 45);
		this.proxy = MemoizingProxy.builder(TestInterface.class, Duration.ofMinutes(1)).delegateTo(delegate);
	}

	private void assertInvocations(String...expectedInvocations) {
		assertEquals(ImmutableList.copyOf(expectedInvocations), delegate.invocations);
		delegate.invocations.clear();
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

	@Test
	public void constructorCalled() throws Exception {
		defaultTestSubject();
		assertEquals(proxy.getName(), "Johny"); //Constructor was called so name should be set
		assertEquals(proxy.getAge(), 45); //Constructor was called so name should be set
	}

	@Test
	public void zeroArgMethodCached() throws Exception {
		defaultTestSubject();
		assertEquals(proxy.getName(), "Johny");
		assertInvocations("getName");
		assertEquals(proxy.getName(), "Johny");
		assertInvocations(/*NONE*/);
	}

	@Test public void exceptionsCached() throws Exception {
		defaultTestSubject();
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
		defaultTestSubject();
		assertInvocations(/*NONE*/);
		
		assertEquals(proxy.getMessage(" whatever"), "Johny whatever");
		assertInvocations("getMessage", "getName");
		
		assertEquals(proxy.getMessage(" whatever"), "Johny whatever");
		assertInvocations("getMessage", "getName");
	}

	@Test
	public void callsViaThisNotCached() throws Exception {
		defaultTestSubject();
		assertInvocations(/*NONE*/);
		
		assertEquals(proxy.getMessage(" whatever"), "Johny whatever");
		assertInvocations("getMessage", "getName");
		
		assertEquals(proxy.getMyName(), "Johny");
		assertInvocations("getMyName", "getName");
	}
	
	@Test public void cacheExpires() throws Exception {
		this.delegate = new TestSubject("Johny", 45);
		this.proxy = MemoizingProxy.builder(TestInterface.class, Duration.ofMillis(10)).delegateTo(delegate);
		assertEquals("Johny", proxy.getMyName());
		assertInvocations("getMyName", "getName");
		sleep(20);
		assertEquals("Johny", proxy.getMyName());
		assertInvocations("getMyName", "getName");
	}
	
	@Test public void proxyClassReused() throws Exception {
		Builder<TestInterface> builder = MemoizingProxy.builder(TestInterface.class, Duration.ofMinutes(1));
		TestSubject delegate1 = new TestSubject("Freddy", 12);
		TestInterface proxy1 = builder.delegateTo(delegate1);
		TestSubject delegate2 = new TestSubject("Johny", 45);
		TestInterface proxy2 = builder.delegateTo(delegate2);
		
		assertFalse(proxy1.equals(proxy2)); // different instance...
		assertEquals(proxy1.getClass(), proxy2.getClass()); //same class
	}

	@Test public void multiThreaded() throws Exception {
		defaultTestSubject();
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
