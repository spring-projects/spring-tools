/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util.template;

/**
 * Methods for creating Template istances and related stuff.
 *
 * @author Kris De Volder
 */
public class Templates {

	public static final TemplateEnv NULL_ENV = new TemplateEnv() {
		@Override
		public String getTemplateVar(char name) {
			return null;
		}
		public String toString() {
			return "NULL_ENV";
		}
	};

	public static Template create(String pattern) {
		//This could be optimized by 'compiling' pattern into somekind of object-graph
		//so that it doesn't actually require parsing and analyzing the pattern each time it gets
		//rendered. However... lets keep things simple for now. We aren't using this for huge
		//patterns or large amounts so it should be fine.
		//Also note that optimizing this wouldn't make make much sense unless the result of calls to
		// this method are actually reused more than once.
		if (pattern != null) {
			return new SimpleTemplate(pattern);
		}
		return null;
	}

}
