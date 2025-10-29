/*******************************************************************************
 * Copyright (c) 2017, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.Jre;
import org.springframework.ide.vscode.commons.util.Assert;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 *
 * This wrapper around a classpath manages classpath data from and to a file-based cache (e.g. ".sts4-cache/classpath-data.json") with classpath data obtained
 * from a project (e.g., maven or gradle project) through an "update" operation.
 *
 * The cached classpath data is written to the file and loaded from it when instance of this classpath is created
 *
 * NOTE: Classpath data may not be available until an actual update is requested on this wrapper.
 *
 * As the wrapper is a classpath itself ,it delegates to the underlying classpath for classpath operations (e.g. getting classpath entries, resources, etc..). However, the data may not
 * be available until update is performed.
 *
 * The wrapper caches some of classpath data such as
 * <li> Classpath entries </li>
 * <li> Classpath resources </li>
 * <li> Output folder </li>
 * <li> Projects' name </li>
 *
 *
 * Implementation is somewhat experimental at the moment...
 *
 * @author Alex Boyko
 *
 * @param <T> a subclass of {@link IClasspath} the delegated to classpath created from current data
 */
public class DelegatingCachedClasspath implements IClasspath {

	private AtomicReference<ClasspathData> cachedData;
	private Callable<IClasspath> classpathCreator;
	private AtomicReference<IClasspath> cachedClasspath;

	private final ClasspathFileBasedCache fileBasedCache;


	public DelegatingCachedClasspath(Callable<IClasspath> delegateCreator, ClasspathFileBasedCache fileCache) {
		super();
		Assert.isLegal(delegateCreator != null);
		this.fileBasedCache = fileCache != null ? fileCache : ClasspathFileBasedCache.NULL;
		this.cachedClasspath = new AtomicReference<>(null);
		this.cachedData = new AtomicReference<>(loadFileBasedCache(fileCache));
		this.classpathCreator = delegateCreator;
	}

	private ClasspathData loadFileBasedCache(ClasspathFileBasedCache fileCache) {
		return fileCache != null ? fileCache.load() : ClasspathData.EMPTY_CLASSPATH_DATA;
	}

	public IClasspath delegate() {
		return cachedClasspath.get();
	}

	@Override
	public String getName() {
		return cachedData.get().getName();
	}

	@Override
	public ImmutableList<CPE> getClasspathEntries() throws Exception {
		return ImmutableList.copyOf(cachedData.get().getClasspathEntries());
	}
	
	@Override
	public Jre getJre() {
		return cachedData.get().getJre();
	}

	public boolean isCached() {
		return fileBasedCache.isCached();
	}

	public boolean update() throws Exception {
		try {
			final ClasspathData newData = createClasspathData();
			if (!Objects.equal(cachedData.get(), newData)) {
				cachedData.set(newData);
				fileBasedCache.persist(newData);
				return true;
			}
			return false;
		} catch (Exception e) {
			cachedData.set(ClasspathData.EMPTY_CLASSPATH_DATA);
			fileBasedCache.delete();
			throw e;
		}
	}

	private ClasspathData createClasspathData() throws Exception {
		IClasspath newDelegate = classpathCreator.call();
		cachedClasspath.set(newDelegate);
		if (newDelegate != null) {
			ClasspathData data = createClasspathData(newDelegate);
			if (data != null) {
				return data;
			}
		}
		return ClasspathData.EMPTY_CLASSPATH_DATA;
	}

	private ClasspathData createClasspathData(IClasspath d) {
		return ClasspathData.from(d);
	}
}
