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
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Method bodies read with ASM, for the two kinds of member only a method body tells
 * ({@code docs/structure-view-dependencies.md}, 5.6): event publishers and the beans a
 * {@code BeanRegistrar} registers. Read only for the classes that could have one - everything else
 * a JAR contributes comes from Jandex alone - and resolved against the same Jandex index the rest of
 * the scan uses, never by loading classes.
 *
 * @author Martin Lippert
 */
public class JarBytecode {

	private static final Logger log = LoggerFactory.getLogger(JarBytecode.class);

	// how many class files have been read - for tests to tell that ungated classes are not
	private static final AtomicInteger classesRead = new AtomicInteger();

	/**
	 * The class itself - {@code null} if it cannot be read.
	 */
	public static ClassNode ownClass(JarType type) {
		try (JarFile jar = new JarFile(type.jarFile())) {
			return read(jar, internalName(type.classInfo().name()), type);
		}
		catch (IOException e) {
			log.warn("cannot read the bytecode of '{}' in '{}'", type.classInfo().name(), type.jarFile(), e);
			return null;
		}
	}

	/**
	 * The class and every class nested in it - member, local and anonymous classes, whose bodies
	 * the source side visits as part of the class's own declaration. Lambda bodies are methods of
	 * the class they are written in already. The nested classes are the class's nest members (Java
	 * 11 bytecode lists them all, however deeply nested), or for older bytecode, the classes named
	 * after it.
	 */
	public static List<ClassNode> classWithNestedClasses(JarType type) {
		List<ClassNode> result = new ArrayList<>();
		String own = internalName(type.classInfo().name());

		try (JarFile jar = new JarFile(type.jarFile())) {
			ClassNode ownClass = read(jar, own, type);
			if (ownClass == null) {
				return result;
			}
			result.add(ownClass);

			List<String> nested = ownClass.nestMembers != null ? ownClass.nestMembers : namedAfter(jar, own);
			for (String name : nested) {
				ClassNode nestedClass = read(jar, name, type);
				if (nestedClass != null) {
					result.add(nestedClass);
				}
			}
		}
		catch (IOException e) {
			log.warn("cannot read the bytecode of '{}' in '{}'", type.classInfo().name(), type.jarFile(), e);
		}

		return result;
	}

	public static int classesReadSoFar() {
		return classesRead.get();
	}

	private static List<String> namedAfter(JarFile jar, String own) {
		List<String> result = new ArrayList<>();
		Enumeration<JarEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName();
			if (name.startsWith(own + "$") && name.endsWith(".class")) {
				result.add(name.substring(0, name.length() - ".class".length()));
			}
		}
		return result;
	}

	/**
	 * A class file of the JAR, or {@code null} if it is not there or ASM cannot read it - a class
	 * file newer than ASM knows, say: that costs only the members read from bytecode.
	 */
	private static ClassNode read(JarFile jar, String internalName, JarType type) {
		JarEntry entry = jar.getJarEntry(internalName + ".class");
		if (entry == null) {
			return null;
		}
		try (InputStream in = jar.getInputStream(entry)) {
			classesRead.incrementAndGet();
			ClassNode classNode = new ClassNode();
			new ClassReader(in).accept(classNode, ClassReader.SKIP_FRAMES);
			return classNode;
		}
		catch (IOException | RuntimeException e) {
			log.warn("cannot read the bytecode of '{}' in '{}'", internalName, type.jarFile(), e);
			return null;
		}
	}

	private static String internalName(DotName name) {
		return name.toString().replace('.', '/');
	}

	/**
	 * The source line of the instruction, for ordering what is found the way the source side's
	 * visitor finds it - in the order it is written.
	 */
	public static int lineOf(AbstractInsnNode instruction) {
		for (AbstractInsnNode current = instruction; current != null; current = current.getPrevious()) {
			if (current instanceof LineNumberNode line) {
				return line.line;
			}
		}
		return -1;
	}

	/**
	 * The type declaring the method a call resolves to - {@code IMethodBinding.getDeclaringClass()}:
	 * the call's owner, or the first of its supertypes that declares a method of that name and
	 * descriptor. {@code null} if that is not on the index.
	 */
	public static String declaringTypeOf(String owner, String name, String descriptor, IndexView index) {
		ClassInfo classInfo = index.getClassByName(DotName.createSimple(owner.replace('/', '.')));
		if (classInfo == null) {
			return null;
		}
		for (MethodInfo method : classInfo.methods()) {
			if (method.name().equals(name) && descriptorOf(method).equals(descriptor)) {
				return JdtStyleTypeNames.qualifiedName(classInfo.name());
			}
		}
		// the superclass first: JDT resolves an inherited method to the class declaring it before an
		// interface does
		List<DotName> supertypes = new ArrayList<>();
		if (classInfo.superName() != null) {
			supertypes.add(classInfo.superName());
		}
		supertypes.addAll(classInfo.interfaceNames());
		for (DotName supertype : supertypes) {
			String found = declaringTypeOf(supertype.toString().replace('.', '/'), name, descriptor, index);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static String descriptorOf(MethodInfo method) {
		StringBuilder descriptor = new StringBuilder("(");
		method.parameterTypes().forEach(parameter -> descriptor.append(descriptorOf(parameter)));
		descriptor.append(')').append(descriptorOf(method.returnType()));
		return descriptor.toString();
	}

	private static String descriptorOf(org.jboss.jandex.Type type) {
		String binaryName = JdtStyleTypeNames.binaryName(type);
		if (binaryName.startsWith("[") || binaryName.length() == 1) {
			return binaryName.replace('.', '/');
		}
		return "L" + binaryName.replace('.', '/') + ";";
	}

	/**
	 * ASM's {@link SimpleVerifier} - which tracks the static type of every value - answering its
	 * questions about the class hierarchy from the Jandex index instead of by loading classes. A type
	 * not on the index is treated leniently: assignable to anything, so an incomplete classpath does
	 * not fail the analysis of a method.
	 */
	public static SimpleVerifier verifier(ClassNode classNode, IndexView index) {
		Type currentClass = Type.getObjectType(classNode.name);
		Type superClass = classNode.superName == null ? null : Type.getObjectType(classNode.superName);
		List<Type> interfaces = classNode.interfaces.stream().map(Type::getObjectType).toList();
		boolean isInterface = (classNode.access & Opcodes.ACC_INTERFACE) != 0;

		return new SimpleVerifier(Opcodes.ASM9, currentClass, superClass, interfaces, isInterface) {

			@Override
			protected boolean isInterface(Type type) {
				ClassInfo classInfo = classInfo(type);
				return classInfo != null && classInfo.isInterface();
			}

			@Override
			protected Type getSuperClass(Type type) {
				ClassInfo classInfo = classInfo(type);
				return classInfo == null || classInfo.superName() == null ? null
						: Type.getObjectType(classInfo.superName().toString().replace('.', '/'));
			}

			@Override
			protected boolean isAssignableFrom(Type type, Type other) {
				if (type.equals(other) || type.getDescriptor().equals("Ljava/lang/Object;")) {
					return true;
				}
				if (type.getSort() == Type.ARRAY || other.getSort() == Type.ARRAY) {
					return true; // lenient - and never asked in practice: arrays are reduced to their elements before
				}
				ClassInfo otherInfo = classInfo(other);
				if (otherInfo == null || classInfo(type) == null) {
					return true;
				}
				return isSubtype(otherInfo, DotName.createSimple(type.getClassName()));
			}

			/**
			 * ASM's own relaxation for an interface - any reference will do, as the JVM verifier has
			 * it - answered without the class loading it would do for that.
			 */
			@Override
			protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
				Type expectedType = expected.getType();
				Type type = value.getType();
				if (expectedType != null && type != null && (expectedType.getSort() == Type.OBJECT || expectedType.getSort() == Type.ARRAY)
						&& (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) {
					return isAssignableFrom(expectedType, type) || isInterface(expectedType);
				}
				return super.isSubTypeOf(value, expected);
			}

			@Override
			protected Class<?> getClass(Type type) {
				throw new UnsupportedOperationException("classes are looked up in the index, not loaded: " + type);
			}

			private boolean isSubtype(ClassInfo classInfo, DotName supertype) {
				if (classInfo.name().equals(supertype)) {
					return true;
				}
				List<DotName> supertypes = new ArrayList<>(classInfo.interfaceNames());
				if (classInfo.superName() != null) {
					supertypes.add(classInfo.superName());
				}
				for (DotName candidate : supertypes) {
					ClassInfo candidateInfo = index.getClassByName(candidate);
					// a supertype not on the index could be anything - lenient, as for an unknown type
					if (candidateInfo == null || isSubtype(candidateInfo, supertype)) {
						return true;
					}
				}
				return false;
			}

			private ClassInfo classInfo(Type type) {
				return type.getSort() == Type.OBJECT ? index.getClassByName(DotName.createSimple(type.getClassName())) : null;
			}
		};
	}

	/**
	 * The binding key of the method a call is found in, for its member to open it by: the method
	 * itself where it is one of the class's own, or else - in a lambda body or a nested class the
	 * index does not know - the class it is in.
	 */
	public static String bindingKeyOf(ClassNode classNode, MethodNode method, IndexView index) {
		ClassInfo classInfo = index.getClassByName(DotName.createSimple(classNode.name.replace('/', '.')));
		if (classInfo != null && (method.access & Opcodes.ACC_SYNTHETIC) == 0) {
			for (MethodInfo candidate : classInfo.methods()) {
				if (candidate.name().equals(method.name) && descriptorOf(candidate).equals(withoutEnclosingInstance(method.desc, candidate))) {
					return JarBindingKeys.of(candidate);
				}
			}
		}
		return "L" + classNode.name + ";";
	}

	/**
	 * Jandex leaves the enclosing instance out of an inner class constructor's parameters - the
	 * descriptor has it.
	 */
	private static String withoutEnclosingInstance(String descriptor, MethodInfo method) {
		if (method.isConstructor() && method.descriptorParametersCount() == method.parametersCount() + 1) {
			Type[] arguments = Type.getArgumentTypes(descriptor);
			Type[] declared = Arrays.copyOfRange(arguments, 1, arguments.length);
			return Type.getMethodDescriptor(Type.getReturnType(descriptor), declared);
		}
		return descriptor;
	}

	/**
	 * The methods of the class to analyze - all of them, lambda bodies included, but no abstract or
	 * native ones, which have no body.
	 */
	public static List<MethodNode> methodsWithBodies(ClassNode classNode) {
		return classNode.methods.stream().filter(method -> method.instructions.size() > 0).toList();
	}

}
