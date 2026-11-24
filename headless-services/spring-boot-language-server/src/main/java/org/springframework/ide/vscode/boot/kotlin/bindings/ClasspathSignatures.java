/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.kotlin.bindings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.java.IType;

/**
 * The generic signature of a class from the classpath: its type parameters and its supertypes with
 * their type arguments. The type index only knows the names of the supertypes, and Java code that
 * walks the hierarchy of a parameterized type needs the arguments (for example to find the domain
 * type of a Spring Data repository: {@code CrudRepository<T, ID>} extends {@code Repository<T, ID>}).
 * The signature is read from the class file, as the constants are ({@link ClasspathConstants}).
 *
 * @author Martin Lippert
 */
final class ClasspathSignatures {

	private static final Logger log = LoggerFactory.getLogger(ClasspathSignatures.class);

	private ClasspathSignatures() {
	}

	/**
	 * @param typeParameters the names of the type parameters of the class
	 * @param superclass the superclass, null if the signature has none (as for java.lang.Object)
	 */
	record ClassSignature(List<String> typeParameters, TypeSignature superclass, List<TypeSignature> interfaces) {
	}

	/** a type in a signature */
	sealed interface TypeSignature permits ClassTypeSignature, TypeVariableSignature, ArrayTypeSignature, BaseTypeSignature, WildcardSignature {
	}

	/** @param binaryName with '$' between the names of nested classes */
	record ClassTypeSignature(String binaryName, List<TypeSignature> arguments) implements TypeSignature {
	}

	record TypeVariableSignature(String name) implements TypeSignature {
	}

	record ArrayTypeSignature(TypeSignature component) implements TypeSignature {
	}

	/** @param descriptor the letter of a primitive type, as in descriptors */
	record BaseTypeSignature(char descriptor) implements TypeSignature {
	}

	/** @param bound null for {@code ?} */
	record WildcardSignature(TypeSignature bound) implements TypeSignature {
	}

	/**
	 * @return the signature of the class, null if the class is not generic and extends no generic
	 *         type (its class file has no signature then), or if the class file cannot be read
	 */
	static ClassSignature read(IType type) {
		try {
			IJavaModuleData module = type.classpathContainer();
			File container = module != null ? module.getContainer() : null;
			if (container == null) {
				return null;
			}

			byte[] bytes = ClasspathConstants.readClassFile(container, type.getFullyQualifiedName().replace('.', '/') + ".class");
			if (bytes == null) {
				return null;
			}

			String[] signature = new String[1];
			new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public void visit(int version, int access, String name, String classSignature, String superName, String[] interfaces) {
					signature[0] = classSignature;
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			return signature[0] != null ? parse(signature[0]) : null;
		}
		catch (Exception e) {
			log.debug("cannot read the signature of {}", type.getFullyQualifiedName(), e);
			return null;
		}
	}

	static ClassSignature parse(String signature) {
		List<String> typeParameters = new ArrayList<>();
		List<TypeSignature> interfaces = new ArrayList<>();
		TypeSignature[] superclass = new TypeSignature[1];

		new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
			@Override
			public void visitFormalTypeParameter(String name) {
				typeParameters.add(name);
			}

			@Override
			public SignatureVisitor visitClassBound() {
				return new TypeBuilder(bound -> {}); // the bounds are not needed
			}

			@Override
			public SignatureVisitor visitInterfaceBound() {
				return new TypeBuilder(bound -> {});
			}

			@Override
			public SignatureVisitor visitSuperclass() {
				return new TypeBuilder(type -> superclass[0] = type);
			}

			@Override
			public SignatureVisitor visitInterface() {
				return new TypeBuilder(interfaces::add);
			}
		});

		return new ClassSignature(typeParameters, superclass[0], interfaces);
	}

	/** reads one type of a signature, and hands it on when it is complete */
	private static final class TypeBuilder extends SignatureVisitor {

		private final Consumer<TypeSignature> result;
		private String binaryName;
		private List<TypeSignature> arguments;

		TypeBuilder(Consumer<TypeSignature> result) {
			super(Opcodes.ASM9);
			this.result = result;
		}

		@Override
		public void visitBaseType(char descriptor) {
			result.accept(new BaseTypeSignature(descriptor));
		}

		@Override
		public void visitTypeVariable(String name) {
			result.accept(new TypeVariableSignature(name));
		}

		@Override
		public SignatureVisitor visitArrayType() {
			return new TypeBuilder(component -> result.accept(new ArrayTypeSignature(component)));
		}

		@Override
		public void visitClassType(String name) {
			binaryName = name.replace('/', '.');
			arguments = new ArrayList<>();
		}

		@Override
		public void visitInnerClassType(String name) {
			// the arguments of the outer class are not kept, the ones of the nested class count
			binaryName = binaryName + "$" + name;
			arguments = new ArrayList<>();
		}

		@Override
		public void visitTypeArgument() {
			arguments.add(new WildcardSignature(null));
		}

		@Override
		public SignatureVisitor visitTypeArgument(char wildcard) {
			List<TypeSignature> target = arguments;
			return new TypeBuilder(argument -> target.add(wildcard == SignatureVisitor.INSTANCEOF ? argument : new WildcardSignature(argument)));
		}

		@Override
		public void visitEnd() {
			result.accept(new ClassTypeSignature(binaryName, List.copyOf(arguments)));
		}
	}

}
