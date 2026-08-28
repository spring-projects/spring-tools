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
package org.springframework.ide.vscode.commons.util;

/**
 * A range of integers between (inclusive) an optional lower and upper bound.
 *
 * @author Kris De Volder
 */
public class IntegerRange {

	public static final IntegerRange ANY = new IntegerRange(null, null);

	private final Integer lowerBound;
	private final Integer upperBound;

	public boolean isInRange(int x) {
		return !isTooSmall(x) && !isTooLarge(x);
	}

	public boolean isTooLarge(int x) {
		return upperBound != null && x > upperBound;
	}

	public boolean isTooSmall(int x) {
		return lowerBound != null && x < lowerBound;
	}

	private IntegerRange(Integer lowerBound, Integer upperBound) {
		super();
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}


	public static IntegerRange atLeast(int lowerBound) {
		return new IntegerRange(lowerBound, null);
	}

	public static IntegerRange atMost(int upperBound) {
		return new IntegerRange(null, upperBound);
	}

	public static IntegerRange exactly(int x) {
		return new IntegerRange(x, x);
	}

	@Override
	public String toString() {
		return
			"IntegerRange(" +
				maybeStr(lowerBound) +
				".." +
				maybeStr(upperBound) +
			")";
	}

	private String maybeStr(Integer bound) {
		if (bound != null) {
			return bound.toString();
		}
		return "";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lowerBound == null) ? 0 : lowerBound.hashCode());
		result = prime * result + ((upperBound == null) ? 0 : upperBound.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IntegerRange other = (IntegerRange) obj;
		if (lowerBound == null) {
			if (other.lowerBound != null)
				return false;
		} else if (!lowerBound.equals(other.lowerBound))
			return false;
		if (upperBound == null) {
			if (other.upperBound != null)
				return false;
		} else if (!upperBound.equals(other.upperBound))
			return false;
		return true;
	}

	public Integer getUpperBound() {
		return upperBound;
	}

	public Integer getLowerBound() {
		return lowerBound;
	}


}
