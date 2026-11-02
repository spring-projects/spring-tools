/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
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
import java.util.Collection;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

/**
 * Basic Jandex Index using only Reactor Flux and Jandex only constructs
 * independent of Javadoc provider logic. Thus this index can be shared via a
 * static object
 *
 * @author Alex Boyko
 *
 */
public class BasicJandexIndex {

	private static final Logger log = LoggerFactory.getLogger(BasicJandexIndex.class);

	private static final String USER_HOME = "user.home";

	@FunctionalInterface
	public static interface IndexFileFinder {
		File findIndexFile(File jarFile);
	}

	public static File getIndexFolder() {
		File folder = new File(System.getProperty(USER_HOME), ".sts-jandex");
		if (!folder.isDirectory()) {
			folder.mkdirs();
		}
		return folder;
	}

	private ImmutableList<ModuleJandexIndex> modules;

	BasicJandexIndex(IClasspath classpath, IndexFileFinder indexFileFinder) {
		ImmutableList.Builder<ModuleJandexIndex> builder = ImmutableList.builder();
		try {
			classpath.getClasspathEntries().forEach(cpe -> {
				File binaryLocation = IClasspathUtil.binaryLocation(cpe);
				builder.addAll(IndexRoutines.fromCPE(cpe, indexFileFinder.findIndexFile(binaryLocation)));
			});
		} catch (Exception e) {
			log.error("", e);
		}
		this.modules = builder.build();
	}

	Tuple2<IJavaModuleData, ClassInfo> getClassByName(DotName fqName) {
		for (ModuleJandexIndex m : modules) {
			IndexView indexView = m.getIndex().get();
			if (indexView != null) {
				ClassInfo info = indexView.getClassByName(fqName);
				if (info != null) {
					return Tuples.of(m, info);
				}
			}
		}
		return null;
	}

	public IJavaModuleData findClasspathResourceForType(String fqName) {
		Tuple2<IJavaModuleData, ClassInfo> match = getClassByName(DotName.createSimple(fqName));
		return match == null ? null : match.getT1();
	}

	private Collection<String> getKnownPackages(ModuleJandexIndex module) {
		ImmutableSet.Builder<String> builder = ImmutableSet.builder();
		IndexView indexView = module.getIndex().get();
		if (indexView != null) {
			Collection<ClassInfo> knownClasses = indexView.getKnownClasses();
			if (knownClasses != null) {
				for (ClassInfo info : knownClasses) {
					String name = info.name().toString();
					int lastDot = name.lastIndexOf('.');
					if (lastDot > 0) { // classes in the default package have no package to offer
						builder.add(name.substring(0, lastDot));
					}
				}
			}
		}
		return builder.build();
	}

	private List<Tuple2<IJavaModuleData, ClassInfo>> getKnownTypeTuples(ModuleJandexIndex module) {
		ImmutableList.Builder<Tuple2<IJavaModuleData, ClassInfo>> builder = ImmutableList.builder();
		IndexView indexView = module.getIndex().get();
		if (indexView != null) {
			Collection<ClassInfo> knownClasses = indexView.getKnownClasses();
			if (knownClasses != null) {
				for (ClassInfo info : knownClasses) {
					builder.add(Tuples.of(module, info));
				}
			}
		}
		return builder.build();
	}

	private List<Tuple2<IJavaModuleData, ClassInfo>> getAllKnownSubclasses(ModuleJandexIndex module, DotName name, boolean isInterface) {
		ImmutableList.Builder<Tuple2<IJavaModuleData, ClassInfo>> builder = ImmutableList.builder();
		IndexView indexView = module.getIndex().get();
		if (indexView != null) {
			Collection<ClassInfo> subTypes = isInterface ? indexView.getAllKnownImplementors(name) : indexView.getAllKnownSubclasses(name);
			if (subTypes != null) {
				for (ClassInfo info : subTypes) {
					builder.add(Tuples.of(module, info));
				}
			}
		}
		return builder.build();
	}

	Flux<Tuple3<IJavaModuleData, ClassInfo, Double>> fuzzySearchTypes(String searchTerm) {
		Flux<Tuple3<IJavaModuleData, ClassInfo, Double>> flux = Flux.fromIterable(modules).publishOn(Schedulers.parallel())
			.flatMap(m -> Flux.fromIterable(getKnownTypeTuples(m)))
			.map(t -> Tuples.of(t.getT1(), t.getT2(), FuzzyMatcher.matchScore(searchTerm, t.getT2().name().toString())))
			.filter(t -> t.getT3() != 0.0);

		return flux;
	}

	public Flux<Tuple2<String, Double>> fuzzySearchPackages(String searchTerm) {
		Flux<Tuple2<String, Double>> flux = Flux.fromIterable(modules).publishOn(Schedulers.parallel())
			.flatMap(m -> Flux.fromIterable(getKnownPackages(m)))
			.map(pkg -> Tuples.of(pkg, FuzzyMatcher.matchScore(searchTerm, pkg)))
			.filter(t -> t.getT2() != 0.0);

		return flux;
	}

	Flux<Tuple2<IJavaModuleData, ClassInfo>> allSubtypesOf(DotName name, boolean isInterface) {
		Flux<Tuple2<IJavaModuleData, ClassInfo>> flux = Flux.fromIterable(modules).publishOn(Schedulers.parallel())
			.flatMap(module -> Flux.fromIterable(getAllKnownSubclasses(module, name, isInterface)));

		return flux;
	}
}
