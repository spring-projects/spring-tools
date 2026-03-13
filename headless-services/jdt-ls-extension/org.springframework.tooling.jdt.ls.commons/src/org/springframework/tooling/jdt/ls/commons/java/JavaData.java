/*******************************************************************************
 * Copyright (c) 2018, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.java;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.JavaElementData;
import org.springframework.ide.vscode.commons.protocol.java.JavaTypeData;
import org.springframework.ide.vscode.commons.protocol.java.JavaTypeData.JavaTypeKind;
import org.springframework.ide.vscode.commons.protocol.java.MemberData;
import org.springframework.ide.vscode.commons.protocol.java.TypeData;
import org.springframework.ide.vscode.commons.protocol.java.TypeData.AnnotationData;
import org.springframework.ide.vscode.commons.protocol.java.TypeData.ClasspathEntryData;
import org.springframework.ide.vscode.commons.protocol.java.TypeData.FieldData;
import org.springframework.ide.vscode.commons.protocol.java.TypeData.MethodData;
import org.springframework.ide.vscode.commons.protocol.java.TypeDescriptorData;
import org.springframework.tooling.jdt.ls.commons.Logger;
import org.springframework.tooling.jdt.ls.commons.classpath.ClasspathUtil;
import org.springframework.tooling.jdt.ls.commons.javadoc.JavadocUtils;
import org.springframework.tooling.jdt.ls.commons.resources.ResourceUtils;

import com.google.common.collect.ImmutableList;

public class JavaData {
	
	private Logger logger;
	
	private Function<IJavaElement, String> labelProvider;
	
	public JavaData(Function<IJavaElement, String> labelProvider, Logger logger) {
		this.labelProvider = labelProvider;
		this.logger = logger;
	}
	
	public TypeData typeData(String projectUri, String bindingKey, boolean lookInOtherProjects) {
		try {
			IJavaElement element = findElement(projectUri == null ? null : URI.create(projectUri), bindingKey, lookInOtherProjects);
			if (element instanceof IType) {
				return createTypeData((IType) element);
			}
		} catch (Exception e) {
			logger.log("Failed to fetch type data for element witn key: " + bindingKey);
			logger.log(e);
		}
		return null;
	}
	
	public static IJavaElement findElement(URI projectUri, String bindingKey, boolean lookInOtherProjects) throws Exception {
		IJavaProject javaProject = projectUri == null ? null : ResourceUtils.getJavaProject(projectUri);
		IJavaElement element = javaProject == null ? null : findElement(javaProject, bindingKey);
		if (lookInOtherProjects && element == null) {
			for (IJavaProject jp : ResourceUtils.allJavaProjects()) {
				if (jp != javaProject) {
					element = findElement(jp, bindingKey);
					if (element != null) {
						break;
					}
				}
			}
		}
		return element;
	}

	public static IJavaElement findElement(IJavaProject project, String bindingKey) {
		IJavaElement element = null;
		// JDT cannot find anonymous inner type from its binding key
		// Find its declaring type. If declaring type found then anonymous inner type is present in the binding key
		String declaringTypeFromAnonymousInnerType = delcaringTypeOfAnonymousInnerType(bindingKey);
		try {
			if (declaringTypeFromAnonymousInnerType == null) {
				element = project.findElement(bindingKey, null);
			} else {
				// Look for element inside the enclosing type that JDT can find. Brute force finding algorithm
				element = findInnerElement(project.findElement(declaringTypeFromAnonymousInnerType, null), bindingKey);
			}
		} catch (Throwable t) {
			// ignore
		}
		if (element == null) {
			// Try modifying the binding key to search for the alternate binding
			try {
				String alternateBinding = JavadocUtils.alternateBinding(bindingKey);
				if (alternateBinding != null) {
					element = project.findElement(alternateBinding, null);
				}
			} catch (Throwable t) {
				// ignore
			}
		}
		return element;
	}
	
	public static String toBindingKey(String fqName) {
		StringBuilder sb = new StringBuilder("L");
		sb.append(fqName.replace('.', '/'));
		sb.append(";");
		return sb.toString();
	}
	
	public static String toJdtFqName(String bindingKey) {
		return bindingKey.substring(1, bindingKey.length() - 1).replace('/', '.').replace('$', '.');
	}
	
	private static IJavaElement findInnerElement(IJavaElement container, String bindingKey) {
		if (container instanceof IField) {
			if (bindingKey.equals(((IField)container).getKey())) {
				return container;
			}
		} else if (container instanceof IMethod) {
			if (bindingKey.equals(((IMethod)container).getKey())) {
				return container;
			}			
		} else if (container instanceof IType) {
			if (bindingKey.equals(((IType)container).getKey())) {
				return container;
			}
		}
		
		if (container instanceof IParent) {
			try {
				for(IJavaElement e : ((IParent)container).getChildren()) {
					IJavaElement found = findInnerElement(e, bindingKey);
					if (found != null) {
						return found;
					}
				}
			} catch (JavaModelException e) {
				// ignore
			}
		}
		return null;
	}
	
	private static String delcaringTypeOfAnonymousInnerType(String bindingKey) {
		int idx = bindingKey.indexOf('$');
		if (idx >= 0) {
			String rest = bindingKey.substring(idx + 1);
			if (rest.charAt(rest.length() - 1) == ';') {
				rest = rest.substring(0, rest.length() - 1);
			}
			String[] tokens = rest.split("\\$");
			for (String token : tokens) {
				int dotIdx = token.indexOf('.');
				String typeToken = dotIdx < 0 ? token : token.substring(0, dotIdx);
				if (!typeToken.isEmpty()) {
					try {
						Integer.parseInt(typeToken);
						// Succeeded. There is inner anonymous type present.
						// Return it's enclosing type binding key which JDT should have no problem finding the element for
						int declaringTypeIdx = bindingKey.indexOf('$' + token);
						if (declaringTypeIdx < 0) {
							// Should not happen. Throw exception?
							return null;
						} else {
							return bindingKey.substring(0, declaringTypeIdx) + ';';
						}
					} catch (NumberFormatException e) {
						// ignore and continue looping
					}
				}
			}
		}
		return null;
	}
	
	private void fillJavaElementData(IJavaElement element, JavaElementData data) {
		data.setName(element.getElementName());
		data.setHandleIdentifier(element.getHandleIdentifier());
		if (labelProvider != null) {
			data.setLabel(labelProvider.apply(element));
		}
	}
	
	private FieldData createFieldData(IType type, IField field) {
		FieldData data = new FieldData();
		fillMemberData(field, data);
		data.setBindingKey(field.getKey());
		ImmutableList.Builder<AnnotationData> annotationsBuilder = ImmutableList.builder();
		try {
			for (IAnnotation annotation : field.getAnnotations()) {
				annotationsBuilder.add(createAnnotationData(type, annotation));
			}
			data.setType(createFromSignature(type, field.getTypeSignature()));
			data.setEnumConstant(field.isEnumConstant());
		} catch (JavaModelException e) {
			logger.log(e);
		}
		data.setAnnotations(annotationsBuilder.build());
		return data;
	}
	
	private void fillMemberData(IMember member, MemberData data) {
		fillJavaElementData(member, data);
		IType declaringType = member.getDeclaringType();
		if (declaringType != null) {
			data.setDeclaringType(declaringType.getKey());
		}
		try {
			data.setFlags(member.getFlags());
		} catch (JavaModelException e) {
			logger.log(e);
		}
	}
	
	private MethodData createMethodData(IType type, IMethod method) {
		MethodData data = new MethodData();
		fillMemberData(method, data);
		data.setBindingKey(method.getKey());
		ImmutableList.Builder<AnnotationData> annotationsBuilder = ImmutableList.builder();
		ImmutableList.Builder<JavaTypeData> parametersBuilder = ImmutableList.builder();
		try {
			data.setConstructor(method.isConstructor());
			data.setReturnType(createFromSignature(type, method.getReturnType()));
			// Replace 'V' return type at the end with resolved return type
			if (data.getBindingKey().endsWith(")V") && !"V".equals(data.getReturnType().getName())) {
				String key = data.getBindingKey();
				data.setBindingKey(key.substring(0, key.length() - 1) + data.getReturnType().getName());
			}
			for (IAnnotation annotation : method.getAnnotations()) {
				annotationsBuilder.add(createAnnotationData(type, annotation));
			}
			for (String parameter : method.getParameterTypes()) {
				JavaTypeData parameterData = createFromSignature(type, parameter);
				parametersBuilder.add(parameterData);
				// Replace unresolved parameters with resolved ones in the binding
				data.setBindingKey(data.getBindingKey().replace(parameter, parameterData.getName()));
			}
		} catch (JavaModelException e) {
			logger.log(e);
		}
		data.setAnnotations(annotationsBuilder.build());
		data.setParameters(parametersBuilder.build());
		try {
			String[] paramNames = method.getParameterNames();
			if (paramNames!=null) {
				data.setParameterNames(ImmutableList.copyOf(paramNames));
			}
		} catch (JavaModelException e) {
			//ignore.
		}
		return data;
	}

	public TypeData createTypeData(IType type) {
		TypeData data = new TypeData();
		fillTypeData(type, data);
		return data;
	}
	
	public TypeDescriptorData createTypeDescriptorData(IType type) {
		TypeDescriptorData data = new TypeDescriptorData();
		fillTypeDescriptorData(type, data);
		return data;
	}
	
	private void fillTypeDescriptorData(IType type, TypeDescriptorData data) {
		fillMemberData(type, data);		
		data.setFqName(type.getFullyQualifiedName());
		try {
			data.setAnnotation(type.isAnnotation());
			data.setClass(type.isClass());
			data.setEnum(type.isEnum());
			data.setInterface(type.isInterface());
			data.setRecord(type.isRecord());
			data.setSuperClassName(resolveFQName(type, type.getSuperclassName()));
			data.setSuperInterfaceNames(resolveFQNames(type, type.getSuperInterfaceNames()));
		} catch (JavaModelException e) {
			logger.log(e);
		}
	}
	
	private void fillTypeData(IType type, TypeData data) {
		fillTypeDescriptorData(type, data);
		
		data.setBindingKey(type.getKey());
		
		ImmutableList.Builder<FieldData> fieldsBuilder = ImmutableList.builder(); 
		ImmutableList.Builder<MethodData> methodsBuilder = ImmutableList.builder();
		ImmutableList.Builder<AnnotationData> annotationsBuilder = ImmutableList.builder();
		try {
			if (type.isRecord()) {
				for (IField field : type.getRecordComponents()) {
					fieldsBuilder.add(createFieldData(type, field));
				}
			} else {
				for (IField field : type.getFields()) {
					fieldsBuilder.add(createFieldData(type, field));
				}
				for (IMethod method : type.getMethods()) {
					methodsBuilder.add(createMethodData(type, method));
				}
			}
			for (IAnnotation annotation : type.getAnnotations()) {
				annotationsBuilder.add(createAnnotationData(type, annotation));
			}
		} catch (JavaModelException e) {
			logger.log(e);
		}
		data.setFields(fieldsBuilder.build());
		data.setMethods(methodsBuilder.build());
		data.setAnnotations(annotationsBuilder.build());
		
		data.setClasspathEntry(createClasspathEntryData(type));
	}
	
	private ClasspathEntryData createClasspathEntryData(IMember member) {
		ClasspathEntryData data = new ClasspathEntryData();
		
		IPackageFragmentRoot packageFragmentRoot = (IPackageFragmentRoot) member.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (packageFragmentRoot != null) {
			try {
				IClasspathEntry entry = packageFragmentRoot.getResolvedClasspathEntry();
				if (entry != null) {
					List<CPE> cpes = ClasspathUtil.createCpes(packageFragmentRoot.getJavaProject(), entry);
					Assert.isTrue(cpes.size() < 2);
					if (!cpes.isEmpty()) {
						data.setCpe(cpes.get(0));
					}
				}
			} catch (JavaModelException | MalformedURLException e) {
				logger.log(e);
			}
		}

		ITypeRoot typeRoot = member.getTypeRoot();
		try {
			if (typeRoot != null && typeRoot.getModule() != null) {
				data.setModule(typeRoot.getModule().getElementName());
			}
		} catch (JavaModelException e) {
			logger.log(e);
		}
		
		return data;
	}
	
	private AnnotationData createAnnotationData(IType type, IAnnotation annotation) {
		AnnotationData data = new AnnotationData();
		fillAnnotationData(type, annotation, data);
		return data;
	}
	
	private void fillAnnotationData(IType type, IAnnotation annotation, AnnotationData data) {
		fillJavaElementData(annotation, data);
		
		Map<String, Object> pairs = new HashMap<>();
		try {
			data.setFqName(resolveFQName(type, annotation.getElementName()));

			for (IMemberValuePair pair : annotation.getMemberValuePairs()) {
				pairs.put(pair.getMemberName(), pair.getValue().toString());
			}
		} catch (JavaModelException e) {
			logger.log(e);
		}

		data.setValuePairs(pairs);
	}
	
	private JavaTypeData createFromSignature(IType type, String signature) {
		JavaTypeData data = new JavaTypeData();
		String nameToSet = signature;

		char[] typeSignature = signature.toCharArray();

		String[] typeArguments = Signature.getTypeArguments(signature);
		if (typeArguments != null && typeArguments.length > 0) {
			JavaTypeData[] javaTypeArguments = new JavaTypeData[typeArguments.length];
			for (int i = 0; i < typeArguments.length; i++) {
				javaTypeArguments[i] = createFromSignature(type, typeArguments[i]);
				// In case binding key is unresolved replace each argument with resolved one.
				nameToSet = nameToSet.replace(typeArguments[i], javaTypeArguments[i].getName());
			}
			data.setKind(JavaTypeKind.PARAMETERIZED);
			LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
			String typeErasure = Signature.getTypeErasure(signature);
			JavaTypeData owner = createFromSignature(type, typeErasure);
			extras.put("owner", owner);
			extras.put("arguments", javaTypeArguments);
			data.setExtras(extras);
			// In case binding key is unresolved replace owner. Trim trailing ; from type erasure string and from the replacement
			nameToSet = nameToSet.replace(typeErasure.substring(0, typeErasure.length() - 1), owner.getName().substring(0, owner.getName().length() - 1));
			
		} else {
			// need a minimum 1 char
			if (typeSignature.length < 1) {
				throw new IllegalArgumentException();
			}
			char c = typeSignature[0];
			if (c == Signature.C_GENERIC_START) {
				int count = 1;
				for (int i = 1, length = typeSignature.length; i < length; i++) {
					switch (typeSignature[i]) {
						case 	Signature.C_GENERIC_START:
							count++;
							break;
						case Signature.C_GENERIC_END:
							count--;
							break;
					}
					if (count == 0) {
						if (i+1 < length)
							c = typeSignature[i+1];
						break;
					}
				}
			}
			switch (c) {
				case Signature.C_ARRAY :
					data.setKind(JavaTypeKind.ARRAY);
					LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
					extras.put("component", createFromSignature(type, Signature.getElementType(signature)));
					extras.put("dimensions", Signature.getArrayCount(typeSignature));
					data.setExtras(extras);
					break;
				case Signature.C_RESOLVED :
					data.setKind(JavaTypeKind.CLASS);
					break;
				case Signature.C_TYPE_VARIABLE :
					data.setKind(JavaTypeKind.TYPE_VARIABLE);
					break;
				case Signature.C_BOOLEAN :
					data.setKind(JavaTypeKind.BOOLEAN);
					break;
				case Signature.C_BYTE :
					data.setKind(JavaTypeKind.BYTE);
					break;
				case Signature.C_CHAR :
					data.setKind(JavaTypeKind.CHAR);
					break;
				case Signature.C_DOUBLE :
					data.setKind(JavaTypeKind.DOUBLE);
					break;
				case Signature.C_FLOAT :
					data.setKind(JavaTypeKind.FLOAT);
					break;
				case Signature.C_INT :
					data.setKind(JavaTypeKind.INT);
					break;
				case Signature.C_LONG :
					data.setKind(JavaTypeKind.LONG);
					break;
				case Signature.C_SHORT :
					data.setKind(JavaTypeKind.SHORT);
					break;
				case Signature.C_VOID :
					data.setKind(JavaTypeKind.VOID);
					break;
				case Signature.C_STAR :
				case Signature.C_SUPER :
				case Signature.C_EXTENDS :
					data.setKind(JavaTypeKind.WILDCARD);
					break;
				case Signature.C_UNRESOLVED:
					if (type != null) {
						// Attempt to resolve type. For some reason JDT has them unresolved for type members
						try {
							String resolvedType = resolveFQName(type, signature.substring(1, signature.length() - 1));
							if (resolvedType != null) {
								data.setKind(JavaTypeKind.CLASS);
								nameToSet = "L" + resolvedType + ";";
								break;
							}
						} catch (JavaModelException e) {
							data.setKind(JavaTypeKind.UNRESOLVED);
						}
					}
				case Signature.C_CAPTURE :
				case Signature.C_INTERSECTION :
				case Signature.C_UNION :
				default :
					data.setKind(JavaTypeKind.UNRESOLVED);
					break;
			}
		}
		data.setName(nameToSet.replace('.', '/'));
		return data;
	}
	
	private String[] resolveFQNames(IType type, String[] names) {
		if (names!=null) {
			String[] resolved = new String[names.length];
			for (int i = 0; i < resolved.length; i++) {
				try {
					resolved[i] = resolveFQName(type, names[i]);
				} catch (JavaModelException e) {
					logger.log(e);
					resolved[i] = names[i];
				}
			}
			return resolved;
		}
		return null;
	}


	private String resolveFQName(IType type, String name) throws JavaModelException {
		if (name!=null) {
			try {
				String[][] resolution = type.resolveType(name);
				if (resolution != null && resolution.length > 0 && resolution[0].length > 1) {
					return resolution[0][0] + "." + resolution[0][1].replace('.', '$');
				}
				return name;
			} catch (Throwable t) {
				throw new IllegalStateException("Failed to resolve `%s` from resolved type `%s`".formatted(name, type.getElementName()), t);
			}
		}
		return null;
	}

}
