/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.cache;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.util.UriUtil;

import org.springframework.ide.vscode.boot.java.utils.JavaDependencyMultimaps;
import org.springframework.ide.vscode.boot.java.utils.QualifiedTypeName;
import org.springframework.ide.vscode.boot.java.utils.SourceJavaFile;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

/**
 * @author Martin Lippert
 * 
 * @deprecated Use IndexCacheOnDiscDeltaBased - this class is no longer maintained and up-to-date with the latest index changes
 */
public class IndexCacheOnDisc implements IndexCache {

	private final File cacheDirectory;
	private final Map<IndexCacheKey, IndexCacheStore<? extends IndexCacheable>> stores;

	private static final Logger log = LoggerFactory.getLogger(IndexCacheOnDisc.class);

	public IndexCacheOnDisc(File cacheDirectory) {
		this.cacheDirectory = cacheDirectory;
		this.stores = new ConcurrentHashMap<>();

		if (!this.cacheDirectory.exists()) {
			this.cacheDirectory.mkdirs();
		}

		if (!this.cacheDirectory.exists()) {
			log.warn("symbol cache directory does not exist and cannot be created: " + this.cacheDirectory.toString());
		}
	}

	@Override
	public <T extends IndexCacheable> void store(IndexCacheKey cacheKey, String[] files, List<T> elements, Multimap<SourceJavaFile, QualifiedTypeName> dependencies, Class<T> type) {
		if (dependencies == null) {
			dependencies = ImmutableMultimap.of();
		}

		SortedMap<String, Long> timestampedFiles = new TreeMap<>();

		timestampedFiles = Arrays.stream(files)
				.filter(file -> new File(file).exists())
				.collect(Collectors.toMap(file -> file, file -> {
					try {
						return Files.getLastModifiedTime(new File(file).toPath()).toMillis();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}, (v1,v2) -> { throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));}, TreeMap::new));

		save(cacheKey, elements, timestampedFiles, JavaDependencyMultimaps.toSerializationMap(dependencies), type);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IndexCacheable> Pair<T[], Multimap<SourceJavaFile, QualifiedTypeName>> retrieve(IndexCacheKey cacheKey, String[] files, Class<T> type) {
		File cacheStore = new File(cacheDirectory, cacheKey.toString() + ".json");
		if (cacheStore.exists()) {

			Gson gson = createGson();

			try (JsonReader reader = new JsonReader(new FileReader(cacheStore))) {
				IndexCacheStore<T> store = gson.fromJson(reader, IndexCacheStore.class);

				SortedMap<String, Long> timestampedFiles = Arrays.stream(files)
						.filter(file -> new File(file).exists())
						.collect(Collectors.toMap(file -> file, file -> {
							try {
								return Files.getLastModifiedTime(new File(file).toPath()).toMillis();
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						},  (v1,v2) -> { throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));}, TreeMap::new));

				if (isFileMatch(timestampedFiles, store.getTimestampedFiles())) {
					this.stores.put(cacheKey, store);

					List<T> symbols = store.getSymbols();

					Map<String, Collection<String>> storedDependencies = store.getDependencies();
					Multimap<SourceJavaFile, QualifiedTypeName> dependencies = JavaDependencyMultimaps.fromSerializationMap(storedDependencies);

					return Pair.of(
							(T[]) symbols.toArray((T[]) Array.newInstance(type, symbols.size())),
							MultimapBuilder.hashKeys().hashSetValues().build(dependencies)
					);
				}
			}
			catch (Exception e) {
				log.error("error reading cached symbols", e);
			}
		}
		return null;
	}

	@Override
	public <T extends IndexCacheable> void removeFile(IndexCacheKey cacheKey, String file, Class<T> type) {
		@SuppressWarnings("unchecked")
		IndexCacheStore<T> cacheStore = (IndexCacheStore<T>) this.stores.get(cacheKey);

		if (cacheStore != null) {
			String docURI = UriUtil.toUri(new File(file)).toASCIIString();

			SortedMap<String, Long> timestampedFiles = new TreeMap<>(cacheStore.getTimestampedFiles());
			timestampedFiles.remove(file);

			List<T> cachedSymbols = cacheStore.getSymbols().stream()
					.filter(cachedSymbol -> !cachedSymbol.getDocURI().equals(docURI))
					.collect(Collectors.toList());
			Map<String, Collection<String>> changedDeps = new HashMap<>(cacheStore.getDependencies());
			changedDeps.remove(file);
			save(cacheKey, cachedSymbols, timestampedFiles, changedDeps, type);
		}
	}

	@Override
	public <T extends IndexCacheable> void removeFiles(IndexCacheKey cacheKey, String[] files, Class<T> type) {
		@SuppressWarnings("unchecked")
		IndexCacheStore<T> cacheStore = (IndexCacheStore<T>) this.stores.get(cacheKey);

		if (cacheStore != null) {
			
			SortedMap<String, Long> timestampedFiles = new TreeMap<>(cacheStore.getTimestampedFiles());
			Map<String, Collection<String>> changedDeps = new HashMap<>(cacheStore.getDependencies());
			Set<String> docURIs = new HashSet<>();
			
			for (String file : files) {
				String docURI = UriUtil.toUri(new File(file)).toASCIIString();
				docURIs.add(docURI);

				timestampedFiles.remove(file);
				changedDeps.remove(file);
			}

			List<T> cachedSymbols = cacheStore.getSymbols().stream()
					.filter(cachedSymbol -> !docURIs.contains(cachedSymbol.getDocURI()))
					.collect(Collectors.toList());

			save(cacheKey, cachedSymbols, timestampedFiles, changedDeps, type);
		}
	}

	@Override
	public void remove(IndexCacheKey cacheKey) {
		File cacheStore = new File(cacheDirectory, cacheKey.toString() + ".json");
		if (cacheStore.exists()) {
			cacheStore.delete();
			this.stores.remove(cacheKey);
		}
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String file, long lastModified,
			List<T> generatedSymbols, Set<QualifiedTypeName> dependencies, Class<T> type) {
		if (dependencies == null) {
			dependencies = ImmutableSet.of();
		}

		@SuppressWarnings("unchecked")
		IndexCacheStore<T> cacheStore = (IndexCacheStore<T>) this.stores.get(cacheKey);

		if (cacheStore != null) {
			String docURI = UriUtil.toUri(new File(file)).toASCIIString();

			SortedMap<String, Long> timestampedFiles = new TreeMap<>(cacheStore.getTimestampedFiles());
			timestampedFiles.put(file, lastModified);

			List<T> cachedSymbols = cacheStore.getSymbols().stream()
					.filter(cachedSymbol -> !cachedSymbol.getDocURI().equals(docURI))
					.collect(Collectors.toList());

			cachedSymbols.addAll(generatedSymbols);
			
			Map<String, Collection<String>> changedDependencies = new HashMap<>(cacheStore.getDependencies());
			if (dependencies.isEmpty()) {
				changedDependencies.remove(file);
			} else {
				changedDependencies.put(file, ImmutableSet.copyOf(
						dependencies.stream().map(QualifiedTypeName::name).collect(Collectors.toSet())));
			}

			save(cacheKey, cachedSymbols, timestampedFiles, changedDependencies, type);
		}
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String[] files, long[] lastModified,
			List<T> generatedSymbols, Multimap<SourceJavaFile, QualifiedTypeName> dependencies, Class<T> type) {
		if (dependencies == null) {
			dependencies = ImmutableMultimap.of();
		}

		@SuppressWarnings("unchecked")
		IndexCacheStore<T> cacheStore = (IndexCacheStore<T>) this.stores.get(cacheKey);

		if (cacheStore != null) {
			SortedMap<String, Long> timestampedFiles = new TreeMap<>(cacheStore.getTimestampedFiles());
			Set<String> allDocURIs = new HashSet<>();
			
			Map<String, Collection<String>> changedDependencies = new HashMap<>(cacheStore.getDependencies());
			
			for (int i = 0; i < files.length; i++) {
				
				// update cache internal map of timestamps per file
				String docURI = UriUtil.toUri(new File(files[i])).toASCIIString();
				allDocURIs.add(docURI);
				
				timestampedFiles.put(files[i], lastModified[i]);

				// update cache internal map of dependencies per file
				Collection<QualifiedTypeName> updatedDependencies = dependencies.get(SourceJavaFile.of(files[i]));
				if (updatedDependencies == null || updatedDependencies.isEmpty()) {
					changedDependencies.remove(files[i]);
				} else {
					changedDependencies.put(files[i], ImmutableSet.copyOf(
							updatedDependencies.stream().map(QualifiedTypeName::name).collect(Collectors.toSet())));
				}
			}

			// update cache internal list of cached symbols (by removing old ones and adding all new ones)
			List<T> cachedSymbols = cacheStore.getSymbols().stream()
					.filter(cachedSymbol -> !allDocURIs.contains(cachedSymbol.getDocURI()))
					.collect(Collectors.toList());
			cachedSymbols.addAll(generatedSymbols);

			// store the complete cache content of this project to disc
			save(cacheKey, cachedSymbols, timestampedFiles, changedDependencies, type);
		}
	}

	@Override
	public long getModificationTimestamp(IndexCacheKey cacheKey, String file) {
		IndexCacheStore<? extends IndexCacheable> cacheStore = this.stores.get(cacheKey);
		
		if (cacheStore != null) {
			Long result = cacheStore.getTimestampedFiles().get(file);
			if (result != null) {
				return result;
			}
		}

		return 0;
	}

	private <T extends IndexCacheable> void save(IndexCacheKey cacheKey, List<T> elements, SortedMap<String, Long> timestampedFiles,
			Map<String, Collection<String>> dependencies, Class<T> type) {
		IndexCacheStore<T> store = new IndexCacheStore<T>(timestampedFiles, elements, dependencies, type);
		this.stores.put(cacheKey, store);

		try (FileWriter writer = new FileWriter(new File(cacheDirectory, cacheKey.toString() + ".json")))
		{
			Gson gson = createGson();
			gson.toJson(store, writer);

			cleanupCache(cacheKey);
		}
		catch (Exception e) {
			log.error("cannot write symbol cache", e);
		}
	}

	private boolean isFileMatch(SortedMap<String, Long> files1, SortedMap<String, Long> files2) {
		if (files1.size() != files2.size()) return false;

		for (String file : files1.keySet()) {
			if (!files2.containsKey(file)) return false;
			if (!files1.get(file).equals(files2.get(file))) return false;
		}

		return true;
	}

	private void cleanupCache(IndexCacheKey cacheKey) {
		File[] cacheFiles = this.cacheDirectory.listFiles();

		for (int i = 0; i < cacheFiles.length; i++) {
			String fileName = cacheFiles[i].getName();
			IndexCacheKey key = IndexCacheKey.parse(fileName);

			if (key != null && !key.equals(cacheKey)
					&& key.getProject().equals(cacheKey.getProject())
					&& key.getIndexer().equals(cacheKey.getIndexer())
					&& key.getCategory().equals(cacheKey.getCategory())) {
				cacheFiles[i].delete();
				this.stores.remove(key);
			}
			// cleanup old cache files without category information (pre 4.19.1 release)
			else if (key != null && !key.equals(cacheKey)
					&& key.getProject().equals(cacheKey.getProject())
					&& key.getIndexer().equals(cacheKey.getIndexer())
					&& key.getCategory().equals("")) {
				cacheFiles[i].delete();
			}
		}
	}

	public static Gson createGson() {
		return new GsonBuilder()
				.registerTypeAdapter(Bean.class, new BeanJsonAdapter())
				.registerTypeAdapter(InjectionPoint.class, new InjectionPointJsonAdapter())
				.registerTypeAdapter(IndexCacheStore.class, new IndexCacheStoreAdapter())
				.create();
	}


	/**
	 * internal storage structure
	 */
	private static class IndexCacheStore<T extends IndexCacheable> {

		@SuppressWarnings("unused")
		private final String elementType;

		private final SortedMap<String, Long> timestampedFiles;
		private final List<T> elements;
		private final Map<String, Collection<String>> dependencies;

		public IndexCacheStore(SortedMap<String, Long> timestampedFiles, List<T> elements, Map<String, Collection<String>> dependencies, Class<T> elementType) {
			this.timestampedFiles = timestampedFiles;
			this.elements = elements;
			this.dependencies = dependencies;
			this.elementType = elementType.getName();
		}

		public Map<String, Collection<String>> getDependencies() {
			return dependencies;
		}

		public List<T> getSymbols() {
			return elements;
		}

		public SortedMap<String, Long> getTimestampedFiles() {
			return timestampedFiles;
		}
		
	}
	
	private static class IndexCacheStoreAdapter implements JsonDeserializer<IndexCacheStore<?>> {

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public IndexCacheStore<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
	        JsonObject parsedObject = json.getAsJsonObject();

			String className = parsedObject.get("elementType").getAsString();

			try {
				Class<?> elementType = Class.forName(className);

				JsonElement elementsObject = parsedObject.get("elements");
				Type elementListType = TypeToken.getParameterized(List.class, elementType).getType();
				List elements = context.deserialize(elementsObject, elementListType);

				JsonElement timestampedFilesObject = parsedObject.get("timestampedFiles");
				Type timestampsMapType = TypeToken.getParameterized(SortedMap.class, String.class, Long.class).getType();
				SortedMap timestampedFiles = context.deserialize(timestampedFilesObject, timestampsMapType);

				JsonElement dependenciesObject = parsedObject.get("dependencies");
				Map dependencies = context.deserialize(dependenciesObject, HashMap.class);

				return new IndexCacheStore(timestampedFiles, elements, dependencies, elementType);

			} catch (ClassNotFoundException e) {
	            throw new JsonParseException("cannot parse data from index cache with element type: " + className, e);
			}

		}
		
	}

	/**
	 * gson adapter to store subtype information for beans
	 */
	private static class BeanJsonAdapter implements JsonDeserializer<Bean> {

	    @Override
	    public Bean deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
	        JsonObject parsedObject = json.getAsJsonObject();
	        
	        String beanName = parsedObject.get("name").getAsString();
	        String beanType = parsedObject.get("type").getAsString();

	        JsonElement locationObject = parsedObject.get("location");
	        Location location = context.deserialize(locationObject, Location.class);

	        JsonElement injectionPointObject = parsedObject.get("injectionPoints");
	        InjectionPoint[] injectionPoints = context.deserialize(injectionPointObject, InjectionPoint[].class);
	        	        
	        JsonElement supertypesObject = parsedObject.get("supertypes");
	        Set<String> supertypes = context.deserialize(supertypesObject, Set.class);
	        
	        JsonElement annotationsObject = parsedObject.get("annotations");
	        AnnotationMetadata[] annotations = annotationsObject == null ? DefaultValues.EMPTY_ANNOTATIONS : context.deserialize(annotationsObject, AnnotationMetadata[].class);
	        
	        JsonElement isConfigurationObject = parsedObject.get("isConfiguration");
	        boolean isConfiguration = context.deserialize(isConfigurationObject, boolean.class);
	        
	        String symbolLabel = parsedObject.get("symbolLabel").getAsString();

	        return new Bean(beanName, beanType, location, injectionPoints, supertypes, annotations, isConfiguration, symbolLabel);
	    }
	}
	
	private static class InjectionPointJsonAdapter implements JsonDeserializer<InjectionPoint> {
		
	    @Override
	    public InjectionPoint deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
	        JsonObject parsedObject = json.getAsJsonObject();
	        
	        String injectionPointName = parsedObject.get("name").getAsString();
	        String injectionPointType = parsedObject.get("type").getAsString();

	        JsonElement locationObject = parsedObject.get("location");
	        Location location = context.deserialize(locationObject, Location.class);

	        JsonElement annotationsObject = parsedObject.get("annotations");
	        AnnotationMetadata[] annotations = annotationsObject == null ? DefaultValues.EMPTY_ANNOTATIONS : context.deserialize(annotationsObject, AnnotationMetadata[].class);

	        return new InjectionPoint(injectionPointName, injectionPointType, location, annotations);
	    }
	}
	
}
