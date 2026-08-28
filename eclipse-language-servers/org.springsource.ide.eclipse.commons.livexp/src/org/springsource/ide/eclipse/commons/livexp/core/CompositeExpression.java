/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract superclass for creating expression that computes its value
 * based on a variable number of 'child' sub-expressions.
 * 
 * @author Kris De Volder
 */
public abstract class CompositeExpression<V> extends LiveExpression<V> {

	/**
	 * Create an empty composite expression with a given initial value.
	 * The initial value should correspond to the value that expression 
	 * will naturally compute for an empty list of subexpressions.
	 */
	public CompositeExpression(V initialValue) {
		super(initialValue);
	}
	
	private List<LiveExpression<V>> subexps;
	
	public CompositeExpression<V> addChild(LiveExpression<V> e) {
		if (subexps == null) {
			subexps = new ArrayList<LiveExpression<V>>();
		}
		subexps.add(e);
		dependsOn(e);
		return this;
	}
	
	public Iterable<LiveExpression<V>> getChildren() {
		return Collections.unmodifiableList(subexps);
	}

}
