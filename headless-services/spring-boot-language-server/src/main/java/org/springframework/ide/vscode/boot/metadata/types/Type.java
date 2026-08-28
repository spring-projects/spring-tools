/*******************************************************************************
 * Copyright (c) 2015, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.metadata.types;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.commons.java.IArrayType;
import org.springframework.ide.vscode.commons.java.IClassType;
import org.springframework.ide.vscode.commons.java.IJavaType;
import org.springframework.ide.vscode.commons.java.IParameterizedType;
import org.springframework.ide.vscode.commons.java.IPrimitiveType;
import org.springframework.ide.vscode.commons.java.IVoidType;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.yaml.schema.YType;

/**
 * @author Kris De Volder
 */
public class Type implements YType {

	private final String erasure;
	private final Type[] params;

	public Type(String erasure, Type[] params) {
		this.erasure = erasure;
		this.params = params;
	}


	public boolean isGeneric() {
		return params != null;
	}

	public String getErasure() {
		return erasure;
	}
	public Type[] getParams() {
		return params;
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		toString(buf);
		return buf.toString();
	}

	private void toString(StringBuilder buf) {
		buf.append(getErasure());
		if (isGeneric()) {
			buf.append("<");
			boolean first = true;
			for (Type param : getParams()) {
				if (!first) {
					buf.append(",");
				}
				param.toString(buf);
				first = false;
			}
			buf.append(">");
		}
	}

	/**
	 * Attempt to convert given typeSignature to a corresponding Type object.
	 * <p>
	 * Not all valid typeSig have a representation as a Type object. This may
	 * return null if no corresponding representation can be constructed.
	 */
//	public static Type fromSignature(String typeSig, IType context) {
//		//TODO: does this work correctly with nested types (i.e like Map$Entry)
//		Type type = TYPE_FROM_SIG.get(typeSig);
//		if (type != null) {
//			return type;
//		}
//		int kind = Signature.getTypeSignatureKind(typeSig);
//		//Essentially, Type object only able to represent class types with with generic parameters
//		// as long as these generic parameters are fully concrete (i.e. do not contain unbound type
//		// variables. For now only support the simplest case (no generics) and bail out returning null if we
//		// see something we don't understand.
//		if (kind == Signature.CLASS_TYPE_SIGNATURE) {
//			boolean shouldResolve = typeSig.charAt(0) == Signature.C_UNRESOLVED;
//			String erasure = Signature.getTypeErasure(typeSig);
//			String pkg = Signature.getSignatureQualifier(erasure);
//			String nam = Signature.getSignatureSimpleName(erasure);
//			String[] params = Signature.getTypeParameters(typeSig);
//			String[] args = Signature.getTypeArguments(typeSig);
//			if (shouldResolve) {
//				erasure = tryToResolve(qualifiedName(pkg, nam), context);
//			} else {
//				erasure = qualifiedName(pkg, nam);
//			}
//			if (ArrayUtils.hasElements(params)) {
//				//TODO: handle this case
//				return null;
//			} else if (ArrayUtils.hasElements(args)) {
//				Type[] argTypes = new Type[args.length];
//				for (int i = 0; i < argTypes.length; i++) {
//					argTypes[i] = fromSignature(args[i], context);
//				}
//				return new Type(erasure, argTypes);
//			} else {
//				return new Type(erasure, null);
//			}
//		} else if (kind == Signature.ARRAY_TYPE_SIGNATURE) {
//			Type elementType = fromSignature(Signature.getElementType(typeSig), context);
//			if (elementType != null) {
//				int arrayCount = Signature.getArrayCount(typeSig);
//				return elementType.asArray(arrayCount);
//			}
//		}
//		return null;
//	}

	public static Type fromJavaType(IJavaType javaType) {
		if (javaType instanceof IPrimitiveType || javaType instanceof IVoidType) {
			Type type = TYPE_FROM_SIG.get(javaType.name());
			if (type != null) {
				return type;
			}
		} else if (javaType instanceof IClassType) {
			return new Type(((IClassType)javaType).getFQName(), null);
		} else if (javaType instanceof IParameterizedType) {
			IParameterizedType parameterizedType = (IParameterizedType) javaType;
			List<Type> arguments = parameterizedType.arguments().map(Type::fromJavaType).collect(Collectors.toList());
			Type owner = fromJavaType(parameterizedType.owner());
			return new Type(owner.getErasure(), arguments.toArray(new Type[arguments.size()]));
		} else if (javaType instanceof IArrayType) {
			IArrayType arrayType = (IArrayType) javaType;
			Type elementType = fromJavaType(arrayType.component());
			if (elementType != null) {
				return elementType.asArray(arrayType.dimensions());
			}
		}

		return null;
	}

	public Type asArray(int arrayCount) {
		Assert.isLegal(arrayCount > 0);
		StringBuilder arrayErasure = new StringBuilder(erasure);
		for (int i = 0; i < arrayCount; i++) {
			arrayErasure.append("[]");
		}
		return new Type(arrayErasure.toString(), params);
	}

//	private static String qualifiedName(String pkg, String nam) {
//		if (StringUtil.hasText(pkg)) {
//			return pkg + "." + nam;
//		} else {
//			return nam;
//		}
//	}
//
//	private static String tryToResolve(String typeName, IType context) {
//		try {
//			String[][] resolved = context.resolveType(typeName);
//			if (ArrayUtils.hasElements(resolved)) {
//				String pkg = resolved[0][0];
//				String nam = resolved[0][1];
//				if (StringUtil.hasText(pkg)) {
//					return pkg+"."+nam;
//				} else {
//					//No . in front of default package
//					return nam;
//				}
//			}
//		} catch (Exception e) {
//			Log.log(e);
//		}
//		return typeName;
//	}

	//////////////////////////////////////////////////

	/**
	 * Map of some known / common type signatures and their corresponding 'Type' representation.
	 * Note that springboot metadata 'normalizes' all primitive types to their corresponding box
	 * types. So we do the same here.
	 */
	private static final Map<String,Type> TYPE_FROM_SIG = new HashMap<String, Type>();
	static {
		sig2type("B", Byte.class);
		sig2type("C", Character.class);
		sig2type("D", Double.class);
		sig2type("F", Float.class);
		sig2type("I", Integer.class);
		sig2type("J", Long.class);
		sig2type("S", Short.class);
		sig2type("V", Void.class);
		sig2type("Z", Boolean.class);
	}
	private static void sig2type(String sig, Class<?> cls) {
		TYPE_FROM_SIG.put(sig, TypeParser.parse(cls.getName()));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((erasure == null) ? 0 : erasure.hashCode());
		result = prime * result + Arrays.hashCode(params);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Type other = (Type) obj;
		if (erasure == null) {
			if (other.erasure != null)
				return false;
		} else if (!erasure.equals(other.erasure))
			return false;
		if (!Arrays.equals(params, other.params))
			return false;
		return true;
	}
}