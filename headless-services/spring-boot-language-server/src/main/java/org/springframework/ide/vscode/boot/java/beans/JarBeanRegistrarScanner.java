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
package org.springframework.ide.vscode.boot.java.beans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBytecode;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;

/**
 * The JAR/bytecode counterpart of {@code ComponentIndexer.indexBeanRegistrarImplementation} for a
 * {@code BeanRegistrar} component: a child {@link Bean} per {@code BeanRegistry.registerBean} call
 * in its {@code register} method - and in the lambdas written there - for the four overloads the
 * source side reads. Only these classes' bytecode is read for it ({@link JarBytecode}). See
 * {@code docs/structure-view-dependencies.md}, 5.6.
 *
 * <p>Bytecode only has a bean's class where it is written as a class literal ({@code Foo.class}),
 * and its name where it is a constant - a call with a class or name from a variable is left out,
 * where the source side can still take the class from the argument's generic type.
 *
 * @author Martin Lippert
 */
public class JarBeanRegistrarScanner {

	private static final Logger log = LoggerFactory.getLogger(JarBeanRegistrarScanner.class);

	private static final String REGISTER_DESCRIPTOR = "(Lorg/springframework/beans/factory/BeanRegistry;Lorg/springframework/core/env/Environment;)V";

	private static final String CLASS = "Ljava/lang/Class;";
	private static final String STRING = "Ljava/lang/String;";
	private static final String CONSUMER = "Ljava/util/function/Consumer;";

	public static void addRegisteredBeans(Bean registrar, JarType type) {
		if (!JarStereotypeScanner.supertypesOf(type.classInfo(), type.index()).contains(Annotations.BEAN_REGISTRAR_INTERFACE)) {
			return;
		}

		record Found(int line, Bean bean, String bindingKey) {}
		List<Found> found = new ArrayList<>();

		ClassNode classNode = JarBytecode.ownClass(type);
		if (classNode == null) {
			return;
		}

		for (MethodNode method : registerMethodWithItsLambdas(classNode)) {
			Frame<SourceValue>[] frames;
			try {
				frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
			}
			catch (Exception e) {
				log.debug("cannot analyze '{}.{}' for registered beans", classNode.name, method.name, e);
				continue;
			}

			AbstractInsnNode[] instructions = method.instructions.toArray();
			for (int i = 0; i < instructions.length; i++) {
				if (instructions[i] instanceof MethodInsnNode call && call.name.equals("registerBean") && frames[i] != null
						&& Annotations.BEAN_REGISTRY_INTERFACE.equals(JarBytecode.declaringTypeOf(call.owner, call.name, call.desc, type.index()))) {
					Bean bean = registeredBean(call, frames[i], type);
					if (bean != null) {
						found.add(new Found(JarBytecode.lineOf(call), bean, JarBytecode.bindingKeyOf(classNode, method, type.index())));
					}
				}
			}
		}

		found.stream().sorted(Comparator.comparingInt(Found::line)).forEach(registered -> {
			type.bindingKeys().put(registered.bean(), registered.bindingKey());
			registrar.addChild(registered.bean());
		});
	}

	/**
	 * {@code ComponentIndexer.findRegisterMethod}: the class's own {@code register(BeanRegistry,
	 * Environment)} - plus the lambdas written in it, whose bodies bytecode keeps as methods of their
	 * own: found by the {@code invokedynamic} instructions that create them, however the compiler
	 * named them, and however deeply nested. Anonymous classes written in it are not read.
	 */
	private static List<MethodNode> registerMethodWithItsLambdas(ClassNode classNode) {
		List<MethodNode> result = new ArrayList<>();
		for (MethodNode method : classNode.methods) {
			boolean syntheticOrBridge = (method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
			if (!syntheticOrBridge && method.name.equals("register") && method.desc.equals(REGISTER_DESCRIPTOR) && method.instructions.size() > 0) {
				addWithLambdas(method, classNode, result);
			}
		}
		return result;
	}

	private static void addWithLambdas(MethodNode method, ClassNode classNode, List<MethodNode> result) {
		if (result.contains(method)) {
			return;
		}
		result.add(method);

		for (AbstractInsnNode instruction : method.instructions) {
			if (instruction instanceof InvokeDynamicInsnNode indy) {
				for (Object argument : indy.bsmArgs) {
					if (argument instanceof Handle handle && handle.getOwner().equals(classNode.name)) {
						classNode.methods.stream()
								.filter(candidate -> candidate.name.equals(handle.getName()) && candidate.desc.equals(handle.getDesc()))
								.filter(candidate -> (candidate.access & Opcodes.ACC_SYNTHETIC) != 0 && candidate.instructions.size() > 0)
								.findFirst()
								.ifPresent(lambda -> addWithLambdas(lambda, classNode, result));
					}
				}
			}
		}
	}

	/**
	 * {@code ComponentIndexer.scanBeanRegistryInvocations}' four overloads.
	 */
	private static Bean registeredBean(MethodInsnNode call, Frame<SourceValue> frame, JarType type) {
		Type[] parameters = Type.getArgumentTypes(call.desc);
		List<String> descriptors = Arrays.stream(parameters).map(Type::getDescriptor).toList();

		Object[] arguments = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			arguments[i] = constantOf(frame.getStack(frame.getStackSize() - parameters.length + i));
		}

		if (descriptors.equals(List.of(CLASS)) || descriptors.equals(List.of(CLASS, CONSUMER))) {
			// <T> String registerBean(Class<T> beanClass[, Consumer<Spec<T>> customizer])
			return arguments[0] instanceof Type beanClass ? bean(null, beanClass, type) : null;
		}
		if (descriptors.equals(List.of(STRING, CLASS)) || descriptors.equals(List.of(STRING, CLASS, CONSUMER))) {
			// <T> void registerBean(String name, Class<T> beanClass[, Consumer<Spec<T>> customizer])
			return arguments[0] instanceof String name && arguments[1] instanceof Type beanClass ? bean(name, beanClass, type) : null;
		}
		return null;
	}

	private static Object constantOf(SourceValue value) {
		if (value.insns.size() == 1 && value.insns.iterator().next() instanceof LdcInsnNode ldc) {
			return ldc.cst;
		}
		return null;
	}

	/**
	 * {@code ComponentIndexer.addChildBeanFromRegistryInvocation}: named as given, or after the
	 * class's simple name.
	 */
	private static Bean bean(String name, Type beanClass, JarType type) {
		DotName className = DotName.createSimple(beanClass.getClassName());
		String beanName = name != null ? name : BeanUtils.getBeanNameFromType(JdtStyleTypeNames.simpleName(className));
		String beanType = className.toString();

		ClassInfo classInfo = type.index().getClassByName(className);
		Set<String> supertypes = classInfo == null ? Set.of() : JarStereotypeScanner.supertypesOf(classInfo, type.index());

		return new Bean(beanName, beanType, type.placeholderLocation(), DefaultValues.EMPTY_INJECTION_POINTS, supertypes,
				DefaultValues.EMPTY_ANNOTATIONS, false, BeanUtils.COMPONENT_LABEL_STRATEGY.createLabel(beanName, null, beanType));
	}

}
