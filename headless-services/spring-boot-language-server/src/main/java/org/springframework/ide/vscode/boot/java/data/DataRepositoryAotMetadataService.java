/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser.JLRMethod;

import com.google.gson.Gson;

/**
 * @author Martin Lippert
 */
public class DataRepositoryAotMetadataService {
	
	private static final Logger log = LoggerFactory.getLogger(DataRepositoryAotMetadataService.class);

	public DataRepositoryAotMetadata getRepositoryMetadata(IJavaProject project, String repositoryType) {
		try {
			String metadataFilePath = repositoryType.replace('.', File.separatorChar);
			
			Optional<File> metadataFile = IClasspathUtil.getOutputFolders(project.getClasspath())
				.map(outputFolder -> new File(outputFolder.getParentFile(), "spring-aot/main/resources/" + metadataFilePath + ".json"))
				.filter(file -> file.exists())
				.findFirst();
			
			if (metadataFile.isPresent()) {
				return readMetadataFile(metadataFile.get());
			}
			
		} catch (Exception e) {
			log.error("error finding spring data repository definition metadata file", e);
		}
		
		return null;
	}
	
	private DataRepositoryAotMetadata readMetadataFile(File file) {
		
		try (FileReader reader = new FileReader(file)) {
			Gson gson = new Gson();
			DataRepositoryAotMetadata result = gson.fromJson(reader, DataRepositoryAotMetadata.class);
			
			return result;
		}
		catch (IOException e) {
			return null;
		}
	}

	public String getQueryStatement(DataRepositoryAotMetadata metadata, IMethodBinding method) {
		DataRepositoryAotMetadataMethod methodMetadata = findMethod(metadata, method);
		return methodMetadata.getQueryStatement(metadata);
	}
	
	public DataRepositoryAotMetadataMethod findMethod(DataRepositoryAotMetadata metadata, IMethodBinding method) {
		String name = method.getName();
		
		for (DataRepositoryAotMetadataMethod methodMetadata : metadata.methods()) {
			
			if (methodMetadata.name() != null && methodMetadata.name().equals(name)) {

				String signature = methodMetadata.signature();
				JLRMethod parsedMethodSignature = JLRMethodParser.parse(signature);
				
				if (parsedMethodSignature.getFQClassName().equals(metadata.name())
						&& parsedMethodSignature.getMethodName().equals(method.getName())
						&& parsedMethodSignature.getReturnType().equals(method.getReturnType().getQualifiedName())
						&& parameterMatches(parsedMethodSignature, method)) {
					return methodMetadata;
				}
			}
		}
		
		return null;
	}

	private boolean parameterMatches(JLRMethod parsedMethodSignature, IMethodBinding method) {
		String[] parsedParameeterTypes = parsedMethodSignature.getParameters();
		ITypeBinding[] methodParameters = method.getParameterTypes();
		
		if (parsedParameeterTypes == null || methodParameters == null || parsedParameeterTypes.length != methodParameters.length) {
			return false;
		}
		
		for (int i = 0; i < parsedParameeterTypes.length; i++) {
			String qualifiedName = methodParameters[i].getQualifiedName();
			if (qualifiedName != null && !qualifiedName.equals(parsedParameeterTypes[i])) {
				return false;
			}
		}
		
		return true;
	}


}
