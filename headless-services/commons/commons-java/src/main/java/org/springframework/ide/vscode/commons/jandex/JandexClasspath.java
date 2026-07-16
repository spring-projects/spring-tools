/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.jandex;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.jandex.JandexIndex.JavadocProviderFactory;
import org.springframework.ide.vscode.commons.java.ClasspathIndex;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.util.FileObserver;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.BaseEncoding;

import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

/**
 * Classpath with Jandex Java index for searching types
 *
 * @author Alex Boyko
 *
 */
public final class JandexClasspath implements ClasspathIndex {

	public static final Logger log = LoggerFactory.getLogger(JandexClasspath.class);

	public enum JavadocProviderTypes {
//		JAVA_PARSER, //Used to be based on githb java parser. If need something back that can extract docs from source code, we have to implement
						// based on JDT parser. But at the moment this wasn't being used so just got removed.
		HTML
	}

	private Supplier<JandexIndex> javaIndex;
	private final IClasspath classpath;
	private final FileObserver fileObserver;
	private final JavadocProviderFactory javadocProviderFactory;

	public JandexClasspath(IClasspath classpath, FileObserver fileObserver, JavadocProviderFactory javadocProviderFactory) {
		this.fileObserver = fileObserver;
		this.classpath = classpath;
		this.javadocProviderFactory = javadocProviderFactory;
		this.javaIndex = Suppliers.synchronizedSupplier(Suppliers.memoize(() -> createIndex()));
	}

	protected JandexIndex createIndex() {
		log.info("Creating JandexIndex for "+classpath.getName());
		attachFolderListeners();
		return new JandexIndex(classpath, jarFile -> findIndexFile(jarFile), javadocProviderFactory);
	}

	private Disposable.Composite subscriptions = Disposables.composite();

	private void attachFolderListeners() {
		synchronized (this) {
			subscriptions.dispose();
			subscriptions = Disposables.composite();
		}
		for (File cpe : IClasspathUtil.getBinaryRoots(classpath, Classpath::isSource)) {
			final List<String> rebuildGlobPattern = Arrays.asList(cpe.toString().replace(File.separator, "/") + "/**/*.class");
			Disposable disposable = fileObserver.onAnyChange(rebuildGlobPattern, (uri) -> reindex());
			subscriptions.add(disposable);
		}
	}

	@Override
	public void dispose() {
		Composite toDispose = subscriptions;
		subscriptions = null;
		if (toDispose!=null) {
			toDispose.dispose();
		}
	}

	@Override
	public IType findType(String fqName) {
		return javaIndex.get().findType(fqName);
	}

	@Override
	public Flux<Tuple2<IType, Double>> fuzzySearchTypes(String searchTerm, boolean includeBinaries, boolean includeSystemLibs) {
		return javaIndex.get().fuzzySearchITypes(searchTerm);
	}

	@Override
	public Flux<Tuple2<IType, Double>> camelcaseSearchTypes(String searchTerm, boolean includeBinaries,
			boolean includeSystemLibs) {
//		throw new UnsupportedOperationException("Not implemented for Jandex index!");
		return fuzzySearchTypes(searchTerm, includeBinaries, includeSystemLibs);
	}

	@Override
	public Flux<Tuple2<String, Double>> fuzzySearchPackages(String searchTerm, boolean includeBinaries, boolean includeSystemLibs) {
		return javaIndex.get().fuzzySearchPackages(searchTerm);
	}

	@Override
	public Flux<IType> allSubtypesOf(String fqName, boolean includeFocusType, boolean detailed) {
		IType type = javaIndex.get().findType(fqName);
		if (type == null) {
			return Flux.empty();
		} else {
			return Flux.concat(includeFocusType ? Flux.fromStream(Stream.of(type)) : Flux.empty(), javaIndex.get().allSubtypesOf(type));
		}
	}

	private File findIndexFile(File jarFile) {
		File indexFolder = getIndexFolder();
		if (indexFolder == null) {
			return null;
		}
		String canonicalPath;
		try {
			canonicalPath = jarFile.getCanonicalPath();
		} catch (IOException e) {
			canonicalPath = jarFile.getAbsolutePath();
		}
		return new File(indexFolder, jarFile.getName() + "-" + JandexClasspath.digestPath(canonicalPath) + "-" + jarFile.lastModified() + ".jdx");
	}
	
	static String digestPath(String path) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] bytes = md.digest(path.getBytes(StandardCharsets.UTF_8));
			return BaseEncoding.base32().encode(bytes).replace('/', '_'); //slashes are trouble in file names.
		} catch (NoSuchAlgorithmException e) {
			// shouldn't happen!
			throw new IllegalStateException(e);
		}
	}

	protected File getIndexFolder() {
		return JandexIndex.getIndexFolder();
	}

	@Override
	public IJavaModuleData findClasspathResourceContainer(String fqName) {
		return javaIndex.get().findClasspathResourceForType(fqName);
	}

	private void reindex() {
		log.info("Clearing JandexIndex for "+classpath.getName());
		this.javaIndex = Suppliers.synchronizedSupplier(Suppliers.memoize(() -> createIndex()));
	}

	private static void updateQueue(Queue<String> queue, Set<String> exclusion, IType type) {
		for (String t : type.getSuperInterfaceNames()) {
			if (!exclusion.contains(t)) {
				queue.add(t);
				exclusion.add(t);
			}
		}
		String superClass = type.getSuperclassName();
		if (superClass != null && !exclusion.contains(superClass)) {
			queue.add(superClass);
			exclusion.add(superClass);
		}
	}

	@Override
	public Flux<IType> allSuperTypesOf(String fqName, boolean includeFocusType, boolean detailed) {
		Queue<String> queue = new LinkedList<>();
		HashSet<String> visited = new HashSet<>();
		queue.add(fqName);
		visited.add(fqName);
		Flux<IType> typesFlux = Flux.generate(() -> queue, (state, sink) -> {
			if (state.peek() == null) {
				sink.complete();
			} else {
				String typeName = state.poll();
				IType nextType = findType(typeName);
				if (nextType != null) {
					sink.next(nextType);
					updateQueue(state, visited, nextType);
				}
			}
			return state;
		});
		return includeFocusType ? typesFlux : typesFlux.skip(1);
	}

}
