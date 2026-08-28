/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.properties.reconcile;

import static org.springframework.ide.vscode.boot.properties.reconcile.ApplicationPropertiesProblemType.PROP_DUPLICATE_KEY;
import static org.springframework.ide.vscode.boot.properties.reconcile.SpringPropertyProblem.problem;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.java.properties.parser.PropertiesFileEscapes;

/**
 * Instance of this class is fed the regions of names in a properties file, checks them for duplicates and
 * reports the duplicates to {@link IProblemCollector}.
 *
 * @author Kris De Volder
 */
public class DuplicateNameChecker {

	/**
	 * Keep track of seen names. The value in the map entries is either null
	 * or the Region for the first time the name was seen.
	 * <p>
	 * This is used so that the first occurrence can still be reported retroactively
	 * when the second occurrence is encountered.
	 */
	private Map<String, DocumentRegion> seen = new HashMap<>();

	IProblemCollector problems;

	public DuplicateNameChecker(IProblemCollector problems) {
		this.problems = problems;
	}

	public void check(DocumentRegion nameRegion) throws Exception {
		String name = PropertiesFileEscapes.unescape(nameRegion.toString());
		if (!name.isEmpty()) {
			if (seen.containsKey(name)) {
				DocumentRegion pending = seen.get(name);
				if (pending != null) {
					reportDuplicate(pending);
					seen.put(name, null);
				}
				reportDuplicate(nameRegion);
			} else {
				seen.put(name, nameRegion);
			}
		}
	}

	private void reportDuplicate(DocumentRegion nameRegion) throws Exception {
		String decodedKey = PropertiesFileEscapes.unescape(nameRegion.toString());
		problems.accept(problem(PROP_DUPLICATE_KEY,
				"Duplicate property '"+decodedKey+"'", nameRegion));
	}

	public void startNewSubDocument() {
		seen.clear();
	}

}
