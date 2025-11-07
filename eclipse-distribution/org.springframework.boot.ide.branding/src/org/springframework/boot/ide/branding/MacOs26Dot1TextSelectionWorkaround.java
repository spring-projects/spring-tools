/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.boot.ide.branding;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * Workaround for
 * https://github.com/eclipse-platform/eclipse.platform.swt/pull/2694
 * 
 * contributed by Sebastian Ratz
 */
@SuppressWarnings({ "nls" })
class MacOs26Dot1TextSelectionWorkaround implements WeavingHook {

	@Override
	public void weave(WovenClass wovenClass) {

		if (!"org.eclipse.swt.graphics.TextLayout".equals(wovenClass.getClassName())) {
			return;
		}

		try {
			ClassReader cr = new ClassReader(wovenClass.getBytes());
			ClassWriter cw = new ClassWriter(cr, 0);
			ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
					if ("draw".equals(name)
							&& "(Lorg/eclipse/swt/graphics/GC;IIIILorg/eclipse/swt/graphics/Color;Lorg/eclipse/swt/graphics/Color;I)V"
									.equals(desc)) {
						return new MethodVisitor(Opcodes.ASM9, mv) {
							@Override
							public void visitLdcInsn(Object value) {
								if (value instanceof Double d && d.doubleValue() == 2.147483647E9d) { // 0x7fffffff
									super.visitLdcInsn(0.5e7d); // OS.MAX_TEXT_CONTAINER_SIZE
								} else {
									super.visitLdcInsn(value);
								}
							}
						};
					}
					return mv;
				}
			};
			cr.accept(cv, 0);
			wovenClass.setBytes(cw.toByteArray());
		} catch (Throwable t) {
			// Ignore for the moment
		}
	}

	public static void installIfNecessary() {
		if (!Platform.OS_MACOSX.equals(Platform.getOS())) {
			return;
		}
		if (FrameworkUtil.getBundle(SWT.class).getVersion().compareTo(Version.parseVersion("3.132.0")) >= 0) {
			return;
		}
		FrameworkUtil.getBundle(MacOs26Dot1TextSelectionWorkaround.class).getBundleContext().registerService(WeavingHook.class.getName(),
				new MacOs26Dot1TextSelectionWorkaround(), null);
	}

}

