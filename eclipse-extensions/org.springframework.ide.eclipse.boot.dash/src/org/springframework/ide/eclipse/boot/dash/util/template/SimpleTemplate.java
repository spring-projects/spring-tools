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
 * Implements a simple Templating language and substitution algorithm.
 * <p>
 * A template is a string containing template variables of the for '%x' where
 * x is any single character other than '%'.
 * <p>
 * A '%' can be escaped by preceding by repeating it. I.e. '%%' in the template
 * expands to a single '%' in the output.
 * <p>
 * A '%' that occurs at the end of the template is not substituted (i.e. it is automatically
 * 'escaped'.
 * <p>
 * If a template variable is not bound (i.e. {@link TemplateEnv} returns null for it) then it is
 * not substituted (the variable just remains in the output string unchanged).
 *
 * @author Kris De Volder
 */
public class SimpleTemplate implements Template {

	/**
	 * A template variable is a single character preceded by this character.
	 */
	private final char VAR_CHAR = '%';

	private String pattern;

	public SimpleTemplate(String pattern) {
		this.pattern = pattern;
	}

	@Override
	public String render(TemplateEnv env) {
		int len = pattern.length();
		int nextChar = 0; //position of next input char to read from the pattern.
		StringBuilder output = new StringBuilder();
		while (nextChar < len) {
			int nextVarAt = pattern.indexOf(VAR_CHAR, nextChar);
			if (nextVarAt >= 0) {
				output.append(pattern.substring(nextChar, nextVarAt));
				char varName = getVarName(nextVarAt);
				output.append(getValue(varName, env));
				//next char should be right after the processed var, and the var is something like "%u"
				nextChar = nextVarAt+2;
			} else {
				//no more vars
				output.append(pattern.substring(nextChar));
				nextChar = len;
			}
		}
		return output.toString();
	}

	private Object getValue(char varName, TemplateEnv env) {
		if (varName == VAR_CHAR) {
			//its not actually a real var,  but an escaped '%'
			return VAR_CHAR;
		}
		String resolved = env.getTemplateVar(varName);
		return resolved != null ? resolved : new String(new char[]{VAR_CHAR, varName});
	}

	private char getVarName(int varPos) {
		int namePos = varPos+1;
		//If there's nothing after a '%' then treat it the same as a escaped '%' (rather than blowing up)
		return namePos < pattern.length() ? pattern.charAt(namePos) : VAR_CHAR;
	}

}
