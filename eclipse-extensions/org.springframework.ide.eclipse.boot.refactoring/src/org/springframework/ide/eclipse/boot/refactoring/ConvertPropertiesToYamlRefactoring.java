/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.refactoring;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;
import org.eclipse.text.edits.ReplaceEdit;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class ConvertPropertiesToYamlRefactoring extends Refactoring {

	private static final String YAML_EXT = ".yml";
	private static final String PROPERTIES_EXT = ".properties";
	private static final Pattern COMMENT = Pattern.compile("(?m)^\\s*(\\#|\\!)");
	
	private final IFile propsFile;
	private IFile yamlFile;
	private String yamlContent;
	private int inputTextLen;

	public ConvertPropertiesToYamlRefactoring(IFile propertiesFile) {
		this.propsFile = propertiesFile;
	}

	@Override
	public String getName() {
		return "Convert .properties to .yaml";
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		if (!propsFile.isAccessible()) {
			return RefactoringStatus.createFatalErrorStatus("The resource '"+propsFile.getFullPath()+"' is not accessible");
		}
		this.yamlFile = yamlFileFor(propsFile);
		if (yamlFile.exists()) {
			return RefactoringStatus.createFatalErrorStatus("File '"+yamlFile.getFullPath()+"' already exists!");
		}
		return new RefactoringStatus();
	}

	private IFile yamlFileFor(IFile propsFile) {
		IContainer container = propsFile.getParent();
		String name = propsFile.getName();
		if (name.endsWith(PROPERTIES_EXT)) {
			name = name.substring(0, name.length()-PROPERTIES_EXT.length())+YAML_EXT;
		} else {
			name = name + YAML_EXT;
		}
		return container.getFile(Path.EMPTY.append(name));
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		RefactoringStatus status = new RefactoringStatus();
		try {
			if (hasComments(propsFile)) {
				status.merge(RefactoringStatus.createWarningStatus("The properties file has comments, which will be lost in the refactoring!"));
			}
		} catch (Exception e) {
			status.merge(RefactoringStatus.create(ExceptionUtil.status(e, "Problems reading file: "+propsFile.getFullPath())));
			return status;
		}
		Multimap<String, String> properties = null;
		try (InputStream content = propsFile.getContents()) {
			properties = load(content);
		} catch (Exception e) {
			status.merge(RefactoringStatus.create(ExceptionUtil.status(e, "Problems parsing as a .properties file: "+propsFile.getFullPath())));
		}
		if (properties != null) {
			PropertiesToYamlConverter converter = new PropertiesToYamlConverter(properties);
			this.yamlContent = converter.getYaml();
			status.merge(converter.getStatus());
		}
		return status;
	}

	protected Multimap<String, String> load(InputStream content) throws IOException {
		Multimap<String, String> map = Multimaps.newMultimap(
				new TreeMap<String, Collection<String>>(), 
				LinkedHashSet::new
		);
		Properties loader = new Properties() {
			private static final long serialVersionUID = 1L;
			public synchronized Object put(Object key, Object value) {
				map.put((String)key, (String)value);
				return super.put(key, value);
			}
		};
		loader.load(content);
		return map;
	}

	private boolean hasComments(IFile propsFile) throws Exception {
		String inputText = IOUtil.toString(propsFile.getContents());
		inputTextLen = inputText.length();
		return hasComments(inputText);
	}

	private boolean hasComments(String propsFileContent) {
		return COMMENT.matcher(propsFileContent).find();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		CompositeChange changes = new CompositeChange(getName()); 
		TextFileChange textChange = new TextFileChange(getName(), propsFile);
		textChange.setEdit(new ReplaceEdit(0, inputTextLen, yamlContent));
		changes.add(textChange);
		changes.add(new RenameResourceChange(propsFile.getFullPath(), yamlFile.getName()));
		changes.initializeValidationData(pm);
		return changes;
	}

}
