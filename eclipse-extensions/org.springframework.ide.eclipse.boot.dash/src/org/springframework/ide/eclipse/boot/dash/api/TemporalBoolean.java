/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.api;

public enum TemporalBoolean {

	/**
	 * True now, but maybe not forever.
	 */
	TRUE,
	/**
	 * False now, but maybe not forever
	 */
	FALSE,

	/**
	 * False now and forever.
	 */
	NEVER,

	/**
	 * True now and forever.
	 */
	ALLwAYS;

	public boolean isTrue() {
		return this == TRUE || this == ALLwAYS;
	}

	public boolean isFalse() {
		return this == FALSE || this == NEVER;
	}

	public static TemporalBoolean allways(boolean b) {
		return b ? ALLwAYS : NEVER;
	}

	public static TemporalBoolean now(boolean b) {
		return b ? TRUE : FALSE;
	}

}
