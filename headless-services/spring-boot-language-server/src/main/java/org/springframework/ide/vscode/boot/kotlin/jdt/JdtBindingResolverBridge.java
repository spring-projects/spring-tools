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
package org.springframework.ide.vscode.boot.kotlin.jdt;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.eclipse.jdt.core.dom.AST;

/**
 * Installs a {@link JdtBindingSource} on a JDT {@link AST}, so that the {@code resolveBinding()}
 * methods of its nodes answer from that source.
 * <p>
 * JDT has no public API for this. The resolver of an AST is a package-private
 * {@code BindingResolver}, so the bridge needs a subclass of it in the package
 * {@code org.eclipse.jdt.core.dom} ({@code SpringToolsBindingResolver}). The JDT jar is signed, so
 * that class cannot simply be loaded from our jar. Its bytes are read as a resource and defined
 * with a private lookup on {@link AST}: the class then belongs to the class loader, the package
 * and the protection domain of the JDT classes, and the signer check passes. This works as long as
 * the JDT classes are not in a named module that keeps the package closed, which is the case for
 * the class path the language server runs on.
 *
 * @author Martin Lippert
 */
public final class JdtBindingResolverBridge {

	private static final String RESOLVER_CLASS = "org.eclipse.jdt.core.dom.SpringToolsBindingResolver";

	private static volatile MethodHandle install;

	private JdtBindingResolverBridge() {
	}

	public static void install(AST ast, JdtBindingSource source) {
		try {
			installer().invokeExact(ast, source);
		}
		catch (RuntimeException | Error e) {
			throw e;
		}
		catch (Throwable e) {
			throw new IllegalStateException("cannot install the binding resolver", e);
		}
	}

	private static MethodHandle installer() throws Exception {
		MethodHandle result = install;
		if (result == null) {
			synchronized (JdtBindingResolverBridge.class) {
				result = install;
				if (result == null) {
					Class<?> resolverClass = defineResolverClass();
					result = MethodHandles.publicLookup().findStatic(resolverClass, "install",
							MethodType.methodType(void.class, AST.class, JdtBindingSource.class));
					install = result;
				}
			}
		}
		return result;
	}

	private static Class<?> defineResolverClass() throws IOException, IllegalAccessException {
		MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(AST.class, MethodHandles.lookup());

		String resource = RESOLVER_CLASS.replace('.', '/') + ".class";
		try (InputStream in = JdtBindingResolverBridge.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IOException("missing resource " + resource);
			}
			return lookup.defineClass(in.readAllBytes());
		}
		catch (LinkageError e) {
			// defined already, by an earlier copy of this class in another class loader that shares the JDT one
			try {
				return lookup.findClass(RESOLVER_CLASS);
			}
			catch (ClassNotFoundException notFound) {
				throw e;
			}
		}
	}

}
