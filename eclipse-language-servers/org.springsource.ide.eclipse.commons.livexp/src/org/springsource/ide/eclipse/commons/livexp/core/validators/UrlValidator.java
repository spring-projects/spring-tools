/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.core.validators;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.StringFieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;

/**
 * Basic validator for URLs. It merely verifies that the String can be parsed into a URI instance.
 * Some tolerance for leading and trailing whitespace is built-in (i.e. leading / trailing white space
 * is stripped off before validating).
 *
 * A null value is treated the same as the empty String.
 *
 * @author Kris De Volder
 */
public class UrlValidator extends LiveExpression<ValidationResult> {

	private String fieldName;
	private LiveExpression<String> url;
	private boolean nullable;
	private Set<String> allowedSchemes = null;

	public UrlValidator(String fieldName, LiveVariable<String> url) {
		this(fieldName, url, false);
	}

	public UrlValidator(String fieldName, LiveExpression<String> url, boolean nullable) {
		this.fieldName = fieldName;
		this.url = url;
		this.nullable = nullable;
		dependsOn(url);
	}

	public UrlValidator(StringFieldModel field) {
		this(field.getLabel(), field.getVariable());
	}

	@Override
	protected ValidationResult compute() {
		String str = url.getValue();
		if (str == null) {
			str = "";
		} else {
			str = str.trim();
		}
		if (nullable && str.equals("") ) {
			return ValidationResult.OK;
		} else if (!nullable && "".equals(str)) {
			return ValidationResult.error(fieldName+" needs to be provided");
		} else {
			try {
				URI uri = new URI(str);
				if (allowedSchemes != null) {
					if (!allowedSchemes.contains(uri.getScheme())) {
						return ValidationResult.error(fieldName+" url scheme should be one of: "+allowedSchemes);
					}
				}
			} catch (URISyntaxException e) {
				return ValidationResult.error(fieldName+" is not a valid URL: URISyntaxException "+e.getMessage());
			}
		}
		return ValidationResult.OK;
	}

	public UrlValidator allowedSchemes(String... schemes) {
		this.allowedSchemes = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(schemes)));
		return this;
	}

}
