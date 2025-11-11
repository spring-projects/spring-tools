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

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser.JLRMethod;

public record DataRepositoryAotMetadata (String name, String type, DataRepositoryModule module, IDataRepositoryAotMethodMetadata[] methods) {
	
	public IDataRepositoryAotMethodMetadata findMethod(IMethodBinding method) {
		String name = method.getName();
		
		for (IDataRepositoryAotMethodMetadata methodMetadata : methods()) {
			
			if (methodMetadata.getName() != null && methodMetadata.getName().equals(name)) {

				String signature = methodMetadata.getSignature();
				JLRMethod parsedMethodSignature = JLRMethodParser.parse(signature);
				
				if (parsedMethodSignature.getFQClassName().equals(name())
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
