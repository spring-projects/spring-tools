/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.cache;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.beans.ConfigPropertyIndexElement;
import org.springframework.ide.vscode.boot.java.data.QueryMethodIndexElement;
import org.springframework.ide.vscode.boot.java.events.EventListenerIndexElement;
import org.springframework.ide.vscode.boot.java.events.EventPublisherIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxRouteElementRangesIndexElement;
import org.springframework.ide.vscode.commons.RuntimeTypeAdapterFactory;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.AotProcessorElement;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeanMethodContainerElement;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.ProjectElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.UriUtil;

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
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * @author Martin Lippert
 */
public class IndexCacheOnDiscDeltaBased implements IndexCache {

	private final File cacheDirectory;
	private final Map<IndexCacheKey, ConcurrentMap<InternalFileIdentifier, Long>> timestamps;
	private final Map<IndexCacheKey, Integer> compactingCounter;
	private final int compactingCounterBoundary;
	
	private static final int DEFAULT_COMPACTING_TRIGGER = 20;

	private static final Logger log = LoggerFactory.getLogger(IndexCacheOnDiscDeltaBased.class);

	public IndexCacheOnDiscDeltaBased(File cacheDirectory) {
		this.cacheDirectory = cacheDirectory;

		if (!this.cacheDirectory.exists()) {
			this.cacheDirectory.mkdirs();
		}

		if (!this.cacheDirectory.exists()) {
			log.warn("symbol cache directory does not exist and cannot be created: " + this.cacheDirectory.toString());
		}
		
		this.timestamps = new ConcurrentHashMap<>();
		this.compactingCounter = new ConcurrentHashMap<>();
		this.compactingCounterBoundary = DEFAULT_COMPACTING_TRIGGER;
	}

	@Override
	public <T extends IndexCacheable> void store(IndexCacheKey cacheKey, String[] files, List<T> elements, Multimap<String, String> dependencies, Class<T> type) {
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

		IndexCacheStore<T> store = new IndexCacheStore<T>(timestampedFiles, elements, dependencies.asMap(), type);
		persist(cacheKey, new DeltaSnapshot<T>(store), false);
		
		// update local timestamp cache
		ConcurrentMap<InternalFileIdentifier, Long> timestampMap = timestampedFiles.entrySet().stream()
				.collect(Collectors.toConcurrentMap(e -> InternalFileIdentifier.fromPath(e.getKey()), e -> e.getValue()));
		this.timestamps.put(cacheKey, timestampMap);

		this.compactingCounter.put(cacheKey, 0);
		deleteOutdatedCacheFiles(cacheKey);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IndexCacheable> Pair<T[], Multimap<String, String>> retrieve(IndexCacheKey cacheKey, String[] files, Class<T> type) {
		File cacheStore = new File(cacheDirectory, cacheKey.toString() + ".json");
		if (cacheStore.exists()) {

			Pair<IndexCacheStore<T>, Integer> result = retrieveStoreFromIncrementalStorage(cacheKey, type);
			IndexCacheStore<T> store = result.getLeft();

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

				List<T> symbols = store.getSymbols();

				Map<String, Collection<String>> storedDependencies = store.getDependencies();
				Multimap<String, String> dependencies = MultimapBuilder.hashKeys().hashSetValues().build();

				if (storedDependencies!=null && !storedDependencies.isEmpty()) {
					for (Entry<String, Collection<String>> entry : storedDependencies.entrySet()) {
						dependencies.replaceValues(entry.getKey(), entry.getValue());
					}
				}

				// update local timestamp cache
				ConcurrentMap<InternalFileIdentifier, Long> timestampMap = timestampedFiles.entrySet().stream()
						.collect(Collectors.toConcurrentMap(e -> InternalFileIdentifier.fromPath(e.getKey()), e -> e.getValue()));
				this.timestamps.put(cacheKey, timestampMap);
				this.compactingCounter.put(cacheKey, result.getRight());
				compact(cacheKey, type);

				return Pair.of(
						(T[]) symbols.toArray((T[]) Array.newInstance(type, symbols.size())),
						MultimapBuilder.hashKeys().hashSetValues().build(dependencies)
						);
			}
		}
		return null;
	}

	@Override
	public <T extends IndexCacheable> void removeFile(IndexCacheKey cacheKey, String file, Class<T> type) {
		removeFiles(cacheKey, new String[] {file}, type);
	}

	@Override
	public <T extends IndexCacheable> void removeFiles(IndexCacheKey cacheKey, String[] files, Class<T> type) {
		persist(cacheKey, new DeltaDelete<T>(files), true);
		
		// update local timestamp cache
		Map<InternalFileIdentifier, Long> timestampsMap = this.timestamps.get(cacheKey);
		if (timestampsMap != null) {
			for (String file : files) {
				timestampsMap.remove(InternalFileIdentifier.fromPath(file));
			}
		}
		
		this.compactingCounter.merge(cacheKey, 1, Integer::sum);
		compact(cacheKey, type);
	}

	@Override
	public void remove(IndexCacheKey cacheKey) {
		File cacheStore = new File(cacheDirectory, cacheKey.toString() + ".json");
		if (cacheStore.exists()) {
			cacheStore.delete();
		}
		
		// update local timestamp cache
		this.timestamps.remove(cacheKey);
		this.compactingCounter.remove(cacheKey);
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String file, long lastModified,
			List<T> generatedSymbols, Set<String> dependencies, Class<T> type) {
		if (dependencies == null) {
			dependencies = ImmutableSet.of();
		}

		// creating and storing delta
		SortedMap<String, Long> timestampsDelta = new TreeMap<>();
		timestampsDelta.put(file, lastModified);

		Map<String, Collection<String>> dependenciesDelta = new HashMap<>();
		dependenciesDelta.put(file, ImmutableSet.copyOf(dependencies));

		IndexCacheStore<T> deltaStore = new IndexCacheStore<T>(timestampsDelta, generatedSymbols, dependenciesDelta, type);
		persist(cacheKey, new DeltaUpdate<T>(deltaStore), true);
		
		// update local timestamp cache
		Map<InternalFileIdentifier, Long> timestampsMap = this.timestamps.computeIfAbsent(cacheKey, (s) -> new ConcurrentHashMap<>());
		timestampsMap.put(InternalFileIdentifier.fromPath(file), lastModified);
		this.compactingCounter.merge(cacheKey, 1, Integer::sum);
		compact(cacheKey, type);
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String[] files, long[] lastModified,
			List<T> generatedSymbols, Multimap<String, String> dependencies, Class<T> type) {
		if (dependencies == null) {
			dependencies = ImmutableMultimap.of();
		}

		// creating and storing delta
		SortedMap<String, Long> timestampsDelta = new TreeMap<>();
		Map<String, Collection<String>> dependenciesDelta = new HashMap<>();

		for (int i = 0; i < files.length; i++) {
			timestampsDelta.put(files[i], lastModified[i]);
			dependenciesDelta.put(files[i], ImmutableSet.copyOf(dependencies.get(files[i])));
		}

		IndexCacheStore<T> deltaStore = new IndexCacheStore<T>(timestampsDelta, generatedSymbols, dependenciesDelta, type);
		persist(cacheKey, new DeltaUpdate<T>(deltaStore), true);
		
		// update local timestamp cache
		Map<InternalFileIdentifier, Long> timestampsMap = this.timestamps.computeIfAbsent(cacheKey, (s) -> new ConcurrentHashMap<>());
		for (int i = 0; i < files.length; i++) {
			timestampsMap.put(InternalFileIdentifier.fromPath(files[i]), lastModified[i]);
		}
		this.compactingCounter.merge(cacheKey, 1, Integer::sum);
		compact(cacheKey, type);
	}

	@Override
	public long getModificationTimestamp(IndexCacheKey cacheKey, String file) {
		InternalFileIdentifier fileID = InternalFileIdentifier.fromPath(file);
		
		Map<InternalFileIdentifier, Long> timestampsMap = this.timestamps.get(cacheKey);
		if (timestampsMap != null) {
			Long result = timestampsMap.get(fileID);
			if (result != null) {
				return result;
			}
		}

		return 0;
	}
	
	public int getCompactingCounterBoundary() {
		return compactingCounterBoundary;
	}

	private boolean isFileMatch(SortedMap<String, Long> files1, SortedMap<String, Long> files2) {
		if (files1.size() != files2.size()) return false;

		for (String file : files1.keySet()) {
			if (!files2.containsKey(file)) return false;
			if (!files1.get(file).equals(files2.get(file))) return false;
		}

		return true;
	}
	
	private <T extends IndexCacheable> void compact(IndexCacheKey cacheKey, Class<T> type) {
		if (this.compactingCounter.get(cacheKey) > this.compactingCounterBoundary) {
			log.info("compacting...");
			
			IndexCacheStore<T> compactedData = retrieveStoreFromIncrementalStorage(cacheKey, type).getLeft();
			persist(cacheKey, new DeltaSnapshot<T>(compactedData), false);
			this.compactingCounter.put(cacheKey, 0);
			
			deleteOutdatedCacheFiles(cacheKey);
		}
	}

	private void deleteOutdatedCacheFiles(IndexCacheKey cacheKey) {
		File[] cacheFiles = this.cacheDirectory.listFiles();

		for (int i = 0; i < cacheFiles.length; i++) {
			String fileName = cacheFiles[i].getName();
			IndexCacheKey key = IndexCacheKey.parse(fileName);

			if (key != null && !key.equals(cacheKey)
					&& key.getProject().equals(cacheKey.getProject())
					&& key.getIndexer().equals(cacheKey.getIndexer())
					&& key.getCategory().equals(cacheKey.getCategory())) {
				cacheFiles[i].delete();
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
	
	private <T extends IndexCacheable> void persist(IndexCacheKey cacheKey, DeltaElement<T> delta, boolean append) {
		DeltaStorage<T> deltaStorage = new DeltaStorage<T>(delta);

		try (Writer writer = new BufferedWriter(new FileWriter(new File(cacheDirectory, cacheKey.toString() + ".json"), append)))
		{
			Gson gson = createGson();
			gson.toJson(deltaStorage, writer);
			
			writer.write("\n");
		}
		catch (Exception e) {
			log.error("cannot write symbol cache", e);
		}
	}
	
	private <T extends IndexCacheable> Pair<IndexCacheStore<T>, Integer> retrieveStoreFromIncrementalStorage(IndexCacheKey cacheKey, Class<T> type) {
		IndexCacheStore<T> store = new IndexCacheStore<>(new TreeMap<>(), new ArrayList<T>(), new HashMap<>(), type);
		int deltaCounter = 0;

		File cacheStore = new File(cacheDirectory, cacheKey.toString() + ".json");
		if (cacheStore.exists()) {

			Gson gson = createGson();

			try (JsonReader reader = new JsonReader(new BufferedReader(new FileReader(cacheStore)))) {
				reader.setStrictness(Strictness.LENIENT);
				while (reader.peek() != JsonToken.END_DOCUMENT) {
					DeltaStorage<T> delta = gson.fromJson(reader, DeltaStorage.class);
					store = delta.storedElement.apply(store);
					deltaCounter++;
				}
				
			}
			catch (Exception e) {
				log.error("error reading cached symbols", e);
			}
		}
		return Pair.of(store, deltaCounter);
	}



	/**
	 * just keep a md5 hash internally for identifying files to save memory 
	 */
	private static class InternalFileIdentifier {
		
		public static InternalFileIdentifier fromPath(String fileName) {
			byte[] id = DigestUtils.md5(fileName);
			return new InternalFileIdentifier( id);
		}

		private byte[] id;

		private InternalFileIdentifier(byte[] id) {
			this.id = id;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(id);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			InternalFileIdentifier other = (InternalFileIdentifier) obj;
			return Arrays.equals(id, other.id);
		}
		
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
	

	//
	//
	// internal delta-based storage structure: snapshots, updates, and deletions
	//
	//
	
	
	private static record DeltaStorage<T extends IndexCacheable> (DeltaElement<T> storedElement) {}
	
	private static interface DeltaElement<T extends IndexCacheable> {
		public IndexCacheStore<T> apply(IndexCacheStore<T> store);
	}
	
	private static class DeltaSnapshot<T extends IndexCacheable> implements DeltaElement<T> {
		
		private IndexCacheStore<T> store;

		public DeltaSnapshot(IndexCacheStore<T> store) {
			this.store = store;
		}
		
		@Override
		public IndexCacheStore<T> apply(IndexCacheStore<T> store) {
			return this.store;
		}
	}
	
	private static class DeltaDelete<T extends IndexCacheable> implements DeltaElement<T> {
		
		private final String[] files;
		
		public DeltaDelete(String[] files) {
			this.files = files;
		}
		
		@Override
		public IndexCacheStore<T> apply(IndexCacheStore<T> store) {
			SortedMap<String, Long> timestampedFiles = store.getTimestampedFiles();
			Map<String, Collection<String>> changedDeps = store.getDependencies();

			Set<String> docURIs = new HashSet<>();
			
			for (String file : files) {
				String docURI = UriUtil.toUri(new File(file)).toASCIIString();
				docURIs.add(docURI);

				timestampedFiles.remove(file);
				changedDeps.remove(file);
			}
			
			List<T> symbols = store.getSymbols();
			for (Iterator<T> iterator = symbols.iterator(); iterator.hasNext();) {
				T t = iterator.next();
				
				if (docURIs.contains(t.getDocURI())) {
					iterator.remove();
				}
				
			}

			return store;
		}
		
	}
	
	private static class DeltaUpdate<T extends IndexCacheable> implements DeltaElement<T> {

		private IndexCacheStore<T> deltaStore;

		public DeltaUpdate(IndexCacheStore<T> deltaStore) {
			this.deltaStore = deltaStore;
		}
		
		@Override
		public IndexCacheStore<T> apply(IndexCacheStore<T> store) {
			SortedMap<String, Long> deltaTimestamps = deltaStore.getTimestampedFiles();
			SortedMap<String, Long> storeTimestamps = store.getTimestampedFiles();
			
			Map<String, Collection<String>> deltaDependencies = deltaStore.getDependencies();
			Map<String, Collection<String>> storeDependencies = store.getDependencies();
			
			List<T> deltaSymbols = deltaStore.getSymbols();
			List<T> storeSymbols = store.getSymbols();
			
			Set<String> allDocURIs = new HashSet<>();
			
			for (Iterator<String> iterator = deltaTimestamps.keySet().iterator(); iterator.hasNext();) {
				String file = iterator.next();
				long timestamp = deltaTimestamps.get(file);
				
				// update cache internal map of timestamps per file
				String docURI = UriUtil.toUri(new File(file)).toASCIIString();
				allDocURIs.add(docURI);
					
				storeTimestamps.put(file, timestamp);

				// update cache internal map of dependencies per file
				Collection<String> updatedDependencies = deltaDependencies.get(file);
				if (updatedDependencies == null || updatedDependencies.isEmpty()) {
					storeDependencies.remove(file);
				} else {
					storeDependencies.put(file, ImmutableSet.copyOf(updatedDependencies));
				}

				// update cache internal list of cached symbols (by removing old ones and adding all new ones)
				for (Iterator<T> symbols = storeSymbols.iterator(); symbols.hasNext();) {
					if (allDocURIs.contains(symbols.next().getDocURI())) {
						symbols.remove();
					}
				}
				
				storeSymbols.addAll(deltaSymbols);
			}
			
			return store;
		}
		
	}
	

	//
	//
	// GSON serialize / deserialize adapters for the various types involved here that have special needs around JSON
	//
	//

	
	public static Gson createGson() {
		return new GsonBuilder()
				.registerTypeAdapter(DeltaStorage.class, new DeltaStorageAdapter())
				.registerTypeAdapter(IndexCacheStore.class, new IndexCacheStoreAdapter())
				.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(SpringIndexElement.class, "_internal_node_type")
						.recognizeSubtypes()
						.registerSubtype(Bean.class)
						.registerSubtype(AotProcessorElement.class)
						.registerSubtype(BeanMethodContainerElement.class)
						.registerSubtype(ConfigPropertyIndexElement.class)
						.registerSubtype(DocumentElement.class)
						.registerSubtype(EventListenerIndexElement.class)
						.registerSubtype(EventPublisherIndexElement.class)
						.registerSubtype(ProjectElement.class)
						.registerSubtype(QueryMethodIndexElement.class)
						.registerSubtype(RequestMappingIndexElement.class)
						.registerSubtype(WebfluxRouteElementRangesIndexElement.class)
						.registerSubtype(AbstractSpringIndexElement.class))

				
				.create();
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

	private static class DeltaStorageAdapter implements JsonSerializer<DeltaStorage<?>>, JsonDeserializer<DeltaStorage<?>> {

	    @Override
	    public JsonElement serialize(DeltaStorage<?> deltaElement, Type typeOfSrc, JsonSerializationContext context) {
	        JsonObject result = new JsonObject();
	        result.add("type", new JsonPrimitive(deltaElement.storedElement.getClass().getName()));
	        result.add("data", context.serialize(deltaElement.storedElement));
	        return result;
	    }

	    @Override
	    public DeltaStorage<?> deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
	        JsonObject parsedObject = json.getAsJsonObject();
	        String className = parsedObject.get("type").getAsString();
	        JsonElement element = parsedObject.get("data");

	        try {
	            return new DeltaStorage<>(context.deserialize(element, Class.forName(className)));
	        } catch (ClassNotFoundException cnfe) {
	            throw new JsonParseException("cannot parse data from unknown SymbolAddOnInformation subtype: " + type, cnfe);
	        }
	    }
	}

	
}
