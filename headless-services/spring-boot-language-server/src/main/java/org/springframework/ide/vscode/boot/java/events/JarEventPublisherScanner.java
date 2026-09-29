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
package org.springframework.ide.vscode.boot.java.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBytecode;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

/**
 * The JAR/bytecode counterpart of {@code ComponentIndexer.scanEventPublisherInvocations}: an
 * {@link EventPublisherIndexElement} per call of {@code ApplicationEventPublisher.publishEvent} in
 * a component that has an {@code ApplicationEventPublisher} injected - the only case in which the
 * source side looks for them, and the only classes whose bytecode is read for it
 * ({@link JarBytecode}). See {@code docs/structure-view-dependencies.md}, 5.6.
 *
 * <p>The event type is the type ASM's verifier tracks for the argument - the static type of the
 * expression in almost every case, but where a variable of a supertype is assigned a more specific
 * value right before, the more specific type. Also different from JDT's static type: a
 * parameterized event class comes without its type arguments, an anonymous one with its binary
 * name, a primitive boxed, and {@code publishEvent(null)} is left out. Publishers are in the order
 * of their source lines,
 * as the source side's visitor finds them.
 *
 * @author Martin Lippert
 */
public class JarEventPublisherScanner {

	private static final Logger log = LoggerFactory.getLogger(JarEventPublisherScanner.class);

	private static final Set<String> INJECTING = Set.of(Annotations.AUTOWIRED, Annotations.INJECT_JAVAX, Annotations.INJECT_JAKARTA);
	private static final Set<String> INJECTING_FIELD = Set.of(Annotations.AUTOWIRED, Annotations.INJECT_JAVAX, Annotations.INJECT_JAKARTA,
			Annotations.VALUE);

	public static void addEventPublishers(Bean component, JarType type) {
		if (!injectsEventPublisher(type)) {
			return;
		}

		record Found(int line, EventPublisherIndexElement element, String bindingKey) {}
		List<Found> found = new ArrayList<>();

		for (ClassNode classNode : JarBytecode.classWithNestedClasses(type)) {
			for (MethodNode method : JarBytecode.methodsWithBodies(classNode)) {
				Frame<BasicValue>[] frames;
				try {
					frames = new Analyzer<>(JarBytecode.verifier(classNode, type.index())).analyze(classNode.name, method);
				}
				catch (Exception e) {
					log.debug("cannot analyze '{}.{}' for event publishers", classNode.name, method.name, e);
					continue;
				}

				AbstractInsnNode[] instructions = method.instructions.toArray();
				for (int i = 0; i < instructions.length; i++) {
					if (instructions[i] instanceof MethodInsnNode call && isPublishEvent(call, type) && frames[i] != null) {
						Frame<BasicValue> frame = frames[i];
						Type argument = frame.getStack(frame.getStackSize() - 1).getType();
						if (argument == null || argument.getSort() == Type.OBJECT && argument.getInternalName().equals("null")) {
							continue;
						}

						String eventType = typeName(argument);
						EventPublisherIndexElement element = new EventPublisherIndexElement(eventType, type.placeholderLocation(),
								supertypesOf(argument, type), null);
						found.add(new Found(JarBytecode.lineOf(call), element, JarBytecode.bindingKeyOf(classNode, method, type.index())));
					}
				}
			}
		}

		found.stream().sorted(Comparator.comparingInt(Found::line)).forEach(publisher -> {
			type.bindingKeys().put(publisher.element(), publisher.bindingKey());
			component.addChild(publisher.element());
		});
	}

	/**
	 * {@code ComponentIndexer.postProcessComponent}'s gate, from {@code ASTUtils.findInjectionPoints}:
	 * an {@code ApplicationEventPublisher} parameter of the only constructor or of an
	 * {@code @Autowired}/{@code @Inject} method, or an {@code @Autowired}/{@code @Inject}/{@code @Value}
	 * field of that type.
	 */
	static boolean injectsEventPublisher(JarType type) {
		List<MethodInfo> methods = JarStereotypeScanner.sourceLevelMethodsOf(type.classInfo());

		List<MethodInfo> constructors = methods.stream().filter(MethodInfo::isConstructor).toList();
		if (constructors.size() == 1 && hasEventPublisherParameter(constructors.get(0))) {
			return true;
		}

		for (MethodInfo method : methods) {
			if (isAnnotatedWithAnyOf(method.declaredAnnotations(), INJECTING) && hasEventPublisherParameter(method)) {
				return true;
			}
		}

		for (FieldInfo field : type.classInfo().fieldsInDeclarationOrder()) {
			if (!field.isSynthetic() && isAnnotatedWithAnyOf(field.declaredAnnotations(), INJECTING_FIELD)
					&& Annotations.EVENT_PUBLISHER.equals(JdtStyleTypeNames.qualifiedName(field.type()))) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasEventPublisherParameter(MethodInfo method) {
		return method.parameterTypes().stream().anyMatch(parameter -> Annotations.EVENT_PUBLISHER.equals(JdtStyleTypeNames.qualifiedName(parameter)));
	}

	private static boolean isAnnotatedWithAnyOf(List<AnnotationInstance> annotations, Set<String> names) {
		return annotations.stream().anyMatch(annotation -> names.contains(JdtStyleTypeNames.qualifiedName(annotation.name())));
	}

	/**
	 * A call of a single-argument {@code publishEvent} that resolves to the one
	 * {@code ApplicationEventPublisher} declares - not to an override further down.
	 */
	private static boolean isPublishEvent(MethodInsnNode call, JarType type) {
		return call.name.equals("publishEvent") && Type.getArgumentTypes(call.desc).length == 1
				&& Annotations.EVENT_PUBLISHER.equals(JarBytecode.declaringTypeOf(call.owner, call.name, call.desc, type.index()));
	}

	private static String typeName(Type type) {
		if (type.getSort() == Type.OBJECT) {
			return JdtStyleTypeNames.qualifiedName(DotName.createSimple(type.getClassName()));
		}
		// an array, or a primitive - Type.getClassName() is the Java source rendering of either
		return type.getClassName().replace('$', '.');
	}

	private static Set<String> supertypesOf(Type type, JarType jarType) {
		if (type.getSort() != Type.OBJECT) {
			return Set.of();
		}
		ClassInfo classInfo = jarType.index().getClassByName(DotName.createSimple(type.getClassName()));
		return classInfo == null ? Set.of() : JarStereotypeScanner.supertypesOf(classInfo, jarType.index());
	}

}
