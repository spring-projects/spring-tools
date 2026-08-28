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
package org.springframework.ide.eclipse.boot.dash.model;

import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import com.google.common.base.Objects;

/**
 * Represents the different states a 'refreshable' element may be in.
 *
 * @author Alex Boyko
 * @author Kris De Volder
 */
public class RefreshState {

	private static enum Id {
		READY,
		WARNING,
		ERROR,
		LOADING,
	}

	public static final RefreshState READY = new RefreshState(Id.READY);
	public static final RefreshState LOADING = new RefreshState(Id.LOADING);

	public static RefreshState error(String msg) {
		return new RefreshState(Id.ERROR, msg);
	}

	public static RefreshState error(Throwable e) {
		return error(ExceptionUtil.getMessage(e));
	}

	public static RefreshState warning(String message) {
		return new RefreshState(Id.WARNING, message);
	}

	public static RefreshState loading(String message) {
		return new RefreshState(Id.LOADING, message);
	}

	private Id id;
	private String message;

	private RefreshState(Id id) {
		this.id = id;
	}

	private RefreshState(Id id, String message) {
		this(id);
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return id + (message == null || message.isEmpty() ? "" : message);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id, message);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj.getClass() == getClass()) {
			RefreshState other = (RefreshState) obj;
			return Objects.equal(id, other.id) && Objects.equal(message, other.message);
		}
		return false;
	}

	public boolean isError() {
		return id == Id.ERROR;
	}
	public boolean isWarning() {
		return id == Id.WARNING;
	}
	public boolean isLoading() {
		return id == Id.LOADING;
	}

	public static RefreshState merge(RefreshState s1, RefreshState s2) {
		//For the caller's convenience... treat null as READY so callers don't
		// need to do a bunch of null checks.
		if (s1 == null) {
			s1 = READY;
		}
		if (s2 == null) {
			s2 = READY;
		}
		if (s1.id.compareTo(s2.id) < 0) {
			return s2;
		} else {
			return s1;
		}
	}


}