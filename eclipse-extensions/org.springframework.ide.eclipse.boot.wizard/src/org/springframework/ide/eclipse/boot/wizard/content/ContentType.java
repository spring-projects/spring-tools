/*******************************************************************************
 *  Copyright (c) 2013 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.content;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Assert;

public class ContentType<T extends GSContent> implements Describable, DisplayNameable {

	private final Class<T> klass;
	private final String description;

	public ContentType(Class<T> klass, String description) {
		Assert.isNotNull(klass);
		Assert.isNotNull(description);
		this.klass = klass;
		this.description = description;
	}

	public Class<T> getKlass() {
		return klass;
	}

//	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String toString() {
		return "ContentType("+klass.getSimpleName()+")";
	}

//	@Override
	public String getDisplayName() {
		return beatifyClassName(klass.getSimpleName());
	}

	private String beatifyClassName(String simpleName) {
		//Assume class name is camel case. Just split it up at capital letters and
		// insert spaces there.
		StringBuilder result = new StringBuilder();
		Matcher m = Pattern.compile("[A-Z]").matcher(simpleName);
		int pos1 = 0;
		boolean found = m.find();
		while (found) {
			int pos2 = m.start();
			if (pos2 > pos1) {
				result.append(simpleName.substring(pos1, pos2)+" ");
			}
			pos1 = pos2;
			found = m.find();
		}
		//Don't forget the last bit
		if (pos1 >= 0) {
			result.append(simpleName.substring(pos1));
		}
		return result.toString();
	};

}
