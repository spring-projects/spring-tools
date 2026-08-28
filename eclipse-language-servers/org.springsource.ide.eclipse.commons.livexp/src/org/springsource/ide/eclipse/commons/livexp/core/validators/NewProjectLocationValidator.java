/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc. and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 * Pivotal Software, Inc. - moved to commons for reuse across projects.
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.core.validators;

import static org.springsource.ide.eclipse.commons.livexp.core.ValidationResult.error;

import java.io.File;
import java.io.FilenameFilter;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.Path;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

/**
 * Validation logic for functionality that expects a new directory. 
 * The validator accepts paths that either do not exist or that point to an
 * empty directory.
 * 
 * @author Kris De Volder
 */
public class NewProjectLocationValidator extends Validator implements ValueListener<String> {
	
	private String elementName;
	private LiveExpression<String> pathExp;
	private LiveExpression<String> projectNameExp;

	public NewProjectLocationValidator(String elementName, LiveExpression<String> path, LiveExpression<String> projectName) {
		Assert.isNotNull(path);
		Assert.isNotNull(projectName);
		this.elementName = elementName;
		this.pathExp = path;
		this.projectNameExp = projectName;
		path.addListener(this);
		projectName.addListener(this);
	}

	/**
	 * For now only ignoring .git in empty checks, but maybe we should also ignore .svn and .cvs. The idea would be
	 * you can create new project in an empty .git repo. So if the location is empty except for a .git folder it is ok.
	 */
	private static final FilenameFilter IGNORE_SCM_META_DATA = new FilenameFilter() {
		public boolean accept(File dir, String name) {
			return !name.equals(".git");
		}
	};

	private File[] listFiles(File file) {
		return file.listFiles(IGNORE_SCM_META_DATA);
	}
	
	@Override
	protected ValidationResult compute() {
		String path = pathExp.getValue();
		if (path == null || "".equals(path)) {
			return error(elementName+" should be defined");
		}
		String lastSegment = new Path(path).lastSegment();
		String projectName = projectNameExp.getValue();
		if (projectName != null && lastSegment != null && !lastSegment.equals(projectName)) {
			return error(elementName+": last segment of path should be '"+projectName+"'");
		}
		File file = new File(path);
		if (file.exists()) {
			if (file.isDirectory()) {
				if (!isEmptyDirectory(file)) {
					return error("'"+file+"' is not empty (contains '"+listFiles(file)[0]+"')");
				}
			} else {
				return error("'"+file+"' exists but is not a directory");
			}
		}
		return ValidationResult.OK;
	}

	private boolean isEmptyDirectory(File file) {
		File[] files = listFiles(file);
		if (files != null) {
			return files.length == 0;
		}
		return false;
	}

	/**
	 * Called when the path being validated changes.
	 */
	public void gotValue(LiveExpression<String> exp, String path) {
//		if (DEBUG) {
//			System.out.println(elementName + " = " + "'"+path+"'");
//		}
		refresh();
	}

}
