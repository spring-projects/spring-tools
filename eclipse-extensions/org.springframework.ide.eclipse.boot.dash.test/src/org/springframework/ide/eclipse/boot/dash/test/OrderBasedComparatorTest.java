package org.springframework.ide.eclipse.boot.dash.test;

import static java.lang.Integer.signum;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Comparator;

import org.junit.Test;
import org.springframework.ide.eclipse.boot.dash.util.OrderBasedComparator;

public class OrderBasedComparatorTest {

	Comparator<String> comparator = new OrderBasedComparator<String>("foo", "bar", "zor");

	private void assertCompare(int expect, String a, String b) {
		assertEquals(expect, signum(comparator.compare(a, b)));
	}

	@Test
	public void testValidCompares() throws Exception {
		assertCompare( 0, "foo", "foo");
		assertCompare(-1, "foo", "bar");
		assertCompare(-1, "foo", "zor");

		assertCompare(+1, "bar", "foo");
		assertCompare( 0, "bar", "bar");
		assertCompare(-1, "bar", "zor");

		assertCompare(+1, "zor", "foo");
		assertCompare(+1, "zor", "bar");
		assertCompare( 0, "zor", "zor");
	}

	@Test
	public void testSorting() throws Exception {
		String[] elements = {"bar", "foo", "zor"};
		String[] sortedElements = {"foo", "bar", "zor"};

		assertTrue(elements != sortedElements); // if this is not the case test will pass even if comparator
		                                       // causes incorrect sorting!
		Arrays.sort(elements, comparator);
		assertArrayEquals(sortedElements, elements);
	}

	@Test(expected=IllegalArgumentException.class)
	public void invalidCompare1() throws Exception {
		comparator.compare("foo", "BAD");
	}

	@Test(expected=IllegalArgumentException.class)
	public void invalidCompare2() throws Exception {
		comparator.compare("BAD", "foo");
	}

}
