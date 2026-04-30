/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.cache.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.index.cache.AbstractIndexCacheable;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheOnDiscDeltaBased;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.boot.java.utils.QualifiedTypeName;
import org.springframework.ide.vscode.boot.java.utils.SourceJavaFile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class IndexCacheOnDiscDeltaBasedTest {

	public static class TestCacheElement extends AbstractIndexCacheable {

		private final String name;
		private final String kind;

		public TestCacheElement(String docURI, String name, String kind) {
			super(docURI);
			this.name = name;
			this.kind = kind;
		}

		public String getName() {
			return name;
		}

		public String getKind() {
			return kind;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			TestCacheElement that = (TestCacheElement) o;
			return Objects.equals(getDocURI(), that.getDocURI())
					&& Objects.equals(name, that.name)
					&& Objects.equals(kind, that.kind);
		}

		@Override
		public int hashCode() {
			return Objects.hash(getDocURI(), name, kind);
		}

		@Override
		public String toString() {
			return "TestCacheElement [docURI=" + getDocURI() + ", name=" + name + ", kind=" + kind + "]";
		}
	}

	private static final String STORAGE_FILE_EXTENSION = ".json";

	private static final IndexCacheKey CACHE_KEY_VERSION_1 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "1");

	private Path tempDir;
	private IndexCacheOnDiscDeltaBased cache;

	@BeforeEach
	public void setup() throws Exception {
		tempDir = Files.createTempDirectory("cachetest");
		cache = new IndexCacheOnDiscDeltaBased(tempDir.toFile());
	}

	@AfterEach
	public void deleteTempDir() throws Exception {
		FileUtils.deleteDirectory(tempDir.toFile());
	}

    @Test
    void testEmptyCache() throws Exception {
        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(new IndexCacheKey("something", "someIndexer", "someCategory", "0"), new String[0], TestCacheElement.class);
        assertNull(result);
    }

    @Test
    void testSimpleValidCache() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");
        Path file3 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile3");

        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toString(), file2.toString(), file3.toString()};

        List<TestCacheElement> generatedElements = new ArrayList<>();
        generatedElements.add(new TestCacheElement("", "element1", "field"));

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements, ImmutableMultimap.<SourceJavaFile, QualifiedTypeName>builder()
                .put(SourceJavaFile.of(file1.toString()), QualifiedTypeName.of("file1dep1"))
                .put(SourceJavaFile.of(file2.toString()), QualifiedTypeName.of("file2dep1"))
                .put(SourceJavaFile.of(file2.toString()), QualifiedTypeName.of("file2dep2"))
                .build(), TestCacheElement.class);

        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, TestCacheElement.class);

        TestCacheElement[] cachedElements = result.getLeft();
        assertNotNull(cachedElements);
        assertEquals(1, cachedElements.length);

        assertEquals("element1", cachedElements[0].getName());
        assertEquals("field", cachedElements[0].getKind());

        Multimap<SourceJavaFile, QualifiedTypeName> dependencies = result.getRight();
        assertEquals(2, dependencies.keySet().size());
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("file1dep1")), ImmutableSet.copyOf(dependencies.get(SourceJavaFile.of(file1.toString()))));
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("file2dep1"), QualifiedTypeName.of("file2dep2")), ImmutableSet.copyOf(dependencies.get(SourceJavaFile.of(file2.toString()))));

        assertEquals(timeFile1.toMillis(), cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file1.toString()));
        assertEquals(0, cache.getModificationTimestamp(CACHE_KEY_VERSION_1, "random-non-existing-file"));
    }

    @Test
    void testDifferentCacheKey() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");
        Path file3 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile3");

        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        String[] files = {file1.toString(), file2.toString(), file3.toString()};

        List<TestCacheElement> generatedElements = new ArrayList<>();
        generatedElements.add(new TestCacheElement("", "element1", "field"));

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements, null, TestCacheElement.class);

        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(new IndexCacheKey("someOtherProject", "someOtherIndexer", "someOtherCategory", "1"), files, TestCacheElement.class);
        assertNull(result);
    }

    @Test
    void testFileTouched() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");
        Path file3 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile3");

        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toString(), file2.toString(), file3.toString()};

        List<TestCacheElement> generatedElements = new ArrayList<>();
        generatedElements.add(new TestCacheElement("", "element1", "field"));

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements, ImmutableMultimap.<SourceJavaFile, QualifiedTypeName>builder()
                .put(SourceJavaFile.of(file1.toString()), QualifiedTypeName.of("file1dep"))
                .put(SourceJavaFile.of(file2.toString()), QualifiedTypeName.of("file2dep"))
                .build(), TestCacheElement.class);

        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 1000));

        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        assertNull(result);
    }

    @Test
    void testMoreFiles() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");
        Path file3 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile3");

        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        String[] files = {file1.toString(), file2.toString()};
        cache.store(CACHE_KEY_VERSION_1, files, new ArrayList<>(), null, TestCacheElement.class);

        String[] moreFiles = {file1.toString(), file2.toString(), file3.toString()};
        assertNull(cache.retrieve(CACHE_KEY_VERSION_1, moreFiles, TestCacheElement.class));
    }

    @Test
    void testFewerFiles() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");
        Path file3 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile3");

        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        String[] files = {file1.toString(), file2.toString(), file3.toString()};
        cache.store(CACHE_KEY_VERSION_1, files, new ArrayList<>(), null, TestCacheElement.class);

        String[] fewerFiles = {file1.toString(), file2.toString()};
        assertNull(cache.retrieve(CACHE_KEY_VERSION_1, fewerFiles, TestCacheElement.class));
    }

    @Test
    void testDeleteOldCacheFileIfNewOneIsStored() throws Exception {
        IndexCacheKey key1 = CACHE_KEY_VERSION_1;
        cache.store(key1, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));

        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");
        cache.store(key2, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key2.toString() + STORAGE_FILE_EXTENSION))));
        assertFalse(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));
    }

    @Test
    void testDoNotRetrieveOldCacheDataIfNewerVersionIsStored() throws Exception {
        IndexCacheKey key1 = CACHE_KEY_VERSION_1;
        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");

        cache.store(key1, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertNotNull(cache.retrieve(key1, new String[0], TestCacheElement.class));
        assertNull(cache.retrieve(key2, new String[0], TestCacheElement.class));

        cache.store(key2, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertNull(cache.retrieve(key1, new String[0], TestCacheElement.class));
        assertNotNull(cache.retrieve(key2, new String[0], TestCacheElement.class));
    }

    @Test
    void testDeleteOldCacheFileFromPreviousReleasesIfNewOneIsStored() throws Exception {
        IndexCacheKey key1 = new IndexCacheKey("someProject", "someIndexer", "", "2");
        cache.store(key1, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));

        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");
        cache.store(key2, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key2.toString() + STORAGE_FILE_EXTENSION))));
        assertFalse(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));
    }

    @Test
    void testDoNotDeleteCacheFileFromOtherCategory() throws Exception {
        IndexCacheKey key1 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");
        cache.store(key1, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));

        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "otherCategory", "2");
        cache.store(key2, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key2.toString() + STORAGE_FILE_EXTENSION))));
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));
    }

    @Test
    void testDeleteAllCacheFilesForCategoriesInRemovalSet() throws Exception {
        String deprecatedCategory = "deprecatedCategory";
        cache = new IndexCacheOnDiscDeltaBased(tempDir.toFile(), Set.of(deprecatedCategory));

        IndexCacheKey keyToRemove1 = new IndexCacheKey("projectA", "indexerX", deprecatedCategory, "1");
        IndexCacheKey keyToRemove2 = new IndexCacheKey("projectB", "indexerY", deprecatedCategory, "2");
        IndexCacheKey keyToKeep = new IndexCacheKey("projectA", "indexerX", "keptCategory", "1");

        Files.writeString(tempDir.resolve(keyToRemove1.toString() + STORAGE_FILE_EXTENSION), "{}");
        Files.writeString(tempDir.resolve(keyToRemove2.toString() + STORAGE_FILE_EXTENSION), "{}");
        Files.writeString(tempDir.resolve(keyToKeep.toString() + STORAGE_FILE_EXTENSION), "{}");

        assertTrue(Files.exists(tempDir.resolve(keyToRemove1.toString() + STORAGE_FILE_EXTENSION)));
        assertTrue(Files.exists(tempDir.resolve(keyToRemove2.toString() + STORAGE_FILE_EXTENSION)));
        assertTrue(Files.exists(tempDir.resolve(keyToKeep.toString() + STORAGE_FILE_EXTENSION)));

        IndexCacheKey keyForStore = new IndexCacheKey("projectA", "indexerX", "someCategory", "1");
        cache.store(keyForStore, new String[0], new ArrayList<>(), null, TestCacheElement.class);

        assertFalse(Files.exists(tempDir.resolve(keyToRemove1.toString() + STORAGE_FILE_EXTENSION)));
        assertFalse(Files.exists(tempDir.resolve(keyToRemove2.toString() + STORAGE_FILE_EXTENSION)));
        assertTrue(Files.exists(tempDir.resolve(keyToKeep.toString() + STORAGE_FILE_EXTENSION)));
    }

    @Test
    void testDeleteAllCacheFilesForMultipleCategoriesInRemovalSet() throws Exception {
        cache = new IndexCacheOnDiscDeltaBased(tempDir.toFile(), Set.of("oldCategory", "removedCategory"));

        IndexCacheKey keyOld = new IndexCacheKey("p", "i", "oldCategory", "1");
        IndexCacheKey keyRemoved = new IndexCacheKey("p", "i", "removedCategory", "1");
        IndexCacheKey keyCurrent = new IndexCacheKey("p", "i", "currentCategory", "1");

        Files.writeString(tempDir.resolve(keyOld.toString() + STORAGE_FILE_EXTENSION), "{}");
        Files.writeString(tempDir.resolve(keyRemoved.toString() + STORAGE_FILE_EXTENSION), "{}");
        Files.writeString(tempDir.resolve(keyCurrent.toString() + STORAGE_FILE_EXTENSION), "{}");

        cache.store(keyCurrent, new String[0], new ArrayList<>(), null, TestCacheElement.class);

        assertFalse(Files.exists(tempDir.resolve(keyOld.toString() + STORAGE_FILE_EXTENSION)));
        assertFalse(Files.exists(tempDir.resolve(keyRemoved.toString() + STORAGE_FILE_EXTENSION)));
        assertTrue(Files.exists(tempDir.resolve(keyCurrent.toString() + STORAGE_FILE_EXTENSION)));
    }

    @Test
    void testElementAddedToExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();

        List<TestCacheElement> generatedElements1 = new ArrayList<>();
        generatedElements1.add(new TestCacheElement(doc1URI, "element1", "field"));

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements1, null, TestCacheElement.class);

        List<TestCacheElement> generatedElements2 = new ArrayList<>();
        generatedElements2.add(new TestCacheElement(doc1URI, "element1", "field"));
        generatedElements2.add(new TestCacheElement(doc1URI, "element2", "interface"));

        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));
        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedElements2, null, TestCacheElement.class);

        TestCacheElement[] cachedElements = cache.retrieveSymbols(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        assertNotNull(cachedElements);
        assertEquals(2, cachedElements.length);

        assertEquals(timeFile1.toMillis() + 2000, cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file1.toString()));
    }

    @Test
    void testStorageFileIncrementallyUpdatedAndCompacted() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();

        List<TestCacheElement> generatedElements1 = new ArrayList<>();
        generatedElements1.add(new TestCacheElement(doc1URI, "element1", "field"));

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements1, null, TestCacheElement.class);

        Path path = tempDir.resolve(Paths.get(CACHE_KEY_VERSION_1.toString() + STORAGE_FILE_EXTENSION));
        long initialCacheStorageSize = Files.size(path);
        long lastCacheStorageSize = initialCacheStorageSize;
        
        int compactingBoundary = cache.getCompactingCounterBoundary();

        for (int i = 0; i < compactingBoundary; i++) {
            cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + (100 * i), generatedElements1, null, TestCacheElement.class);

            long updatedCacheStorageSize = Files.size(path);
            assertTrue(updatedCacheStorageSize > lastCacheStorageSize, "cache storage size in iteration: " + i);
            
            lastCacheStorageSize = updatedCacheStorageSize;

            long newModificationTimestamp = cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file1.toString());
            assertEquals(timeFile1.toMillis() + (100 * i), newModificationTimestamp);

        }
        
        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + (100 * compactingBoundary), generatedElements1, null, TestCacheElement.class);

        long updatedCacheStorageSize = Files.size(path);
        assertTrue(updatedCacheStorageSize < lastCacheStorageSize, "cache storage size after compacting");
        
        lastCacheStorageSize = updatedCacheStorageSize;

        long newModificationTimestamp = cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file1.toString());
        assertEquals(timeFile1.toMillis() + (100 * compactingBoundary), newModificationTimestamp);
    }

    @Test
    void testElementsAddedToMultipleFiles() throws Exception {

        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");
        Path file3 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile3");

        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        FileTime timeFile2 = Files.getLastModifiedTime(file2);
        FileTime timeFile3 = Files.getLastModifiedTime(file3);

        String[] files = {file1.toAbsolutePath().toString(), file2.toAbsolutePath().toString(), file3.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();
        String doc2URI = UriUtil.toUri(file2.toFile()).toString();
        String doc3URI = UriUtil.toUri(file3.toFile()).toString();

        List<TestCacheElement> generatedElements = new ArrayList<>();
        TestCacheElement element1 = new TestCacheElement(doc1URI, "element1", "field");
        generatedElements.add(element1);

        TestCacheElement element2 = new TestCacheElement(doc2URI, "element2", "field");
        generatedElements.add(element2);

        TestCacheElement element3 = new TestCacheElement(doc3URI, "element3", "field");
        generatedElements.add(element3);

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements, null, TestCacheElement.class);

        List<TestCacheElement> updatedElements = new ArrayList<>();

        TestCacheElement updatedElement1 = new TestCacheElement(doc1URI, "element1", "field");
        TestCacheElement newElement1 = new TestCacheElement(doc1URI, "element1-new", "interface");

        updatedElements.add(updatedElement1);
        updatedElements.add(newElement1);
        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));

        TestCacheElement updatedElement2 = new TestCacheElement(doc2URI, "element2-updated", "field");
        updatedElements.add(updatedElement2);
        assertTrue(file2.toFile().setLastModified(timeFile2.toMillis() + 3000));

        String[] updatedFiles = new String[]{file1.toAbsolutePath().toString(), file2.toAbsolutePath().toString()};
        long[] updatedModificationTimestamps = new long[]{timeFile1.toMillis() + 2000, timeFile2.toMillis() + 3000};

        cache.update(CACHE_KEY_VERSION_1, updatedFiles, updatedModificationTimestamps, updatedElements, null, TestCacheElement.class);

        TestCacheElement[] cachedElements = cache.retrieveSymbols(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        assertNotNull(cachedElements);
        assertEquals(4, cachedElements.length);

        assertElement(updatedElement1, cachedElements);
        assertElement(newElement1, cachedElements);
        assertElement(updatedElement2, cachedElements);
        assertElement(element3, cachedElements);

        assertEquals(timeFile1.toMillis() + 2000, cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file1.toString()));
        assertEquals(timeFile2.toMillis() + 3000, cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file2.toString()));
        assertEquals(timeFile3.toMillis(), cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file3.toString()));
    }

	private void assertElement(TestCacheElement expected, TestCacheElement[] cachedElements) {
		for (TestCacheElement cached : cachedElements) {
			if (cached.getName().equals(expected.getName()) && cached.getKind().equals(expected.getKind())) {
				return;
			}
		}
		fail("element not found: " + expected.toString());
	}

    @Test
    void testDependencyAddedToExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        List<TestCacheElement> generatedElements = ImmutableList.of();

        Multimap<SourceJavaFile, QualifiedTypeName> dependencies = ImmutableMultimap.of(
                SourceJavaFile.of(file1.toAbsolutePath().toString()), QualifiedTypeName.of("dep1"));
        cache.store(CACHE_KEY_VERSION_1, files, generatedElements, dependencies, TestCacheElement.class);

        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));
        Set<QualifiedTypeName> dependencies2 = ImmutableSet.of(QualifiedTypeName.of("dep1"), QualifiedTypeName.of("dep2"));
        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedElements, dependencies2, TestCacheElement.class);

        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        assertNotNull(result);
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("dep1"), QualifiedTypeName.of("dep2")),
        		ImmutableSet.copyOf(result.getRight().get(SourceJavaFile.of(file1.toAbsolutePath().toString()))));
    }

    @Test
    void testElementRemovedFromExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();

        List<TestCacheElement> generatedElements1 = new ArrayList<>();
        generatedElements1.add(new TestCacheElement(doc1URI, "element1", "field"));

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements1, null, TestCacheElement.class);

        List<TestCacheElement> generatedElements2 = new ArrayList<>();
        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));

        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedElements2, null, TestCacheElement.class);

        TestCacheElement[] cachedElements = cache.retrieveSymbols(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        assertNotNull(cachedElements);
        assertEquals(0, cachedElements.length);
    }

    @Test
    void testDependencyRemovedFromExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        List<TestCacheElement> generatedElements1 = ImmutableList.of();

        ImmutableMultimap<SourceJavaFile, QualifiedTypeName> dependencies1 = ImmutableMultimap.<SourceJavaFile, QualifiedTypeName>builder()
                .put(SourceJavaFile.of(file1.toAbsolutePath().toString()), QualifiedTypeName.of("dep1"))
                .put(SourceJavaFile.of(file1.toAbsolutePath().toString()), QualifiedTypeName.of("dep2"))
                .build();

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements1, dependencies1, TestCacheElement.class);

        List<TestCacheElement> generatedElements2 = new ArrayList<>();
        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));

        Set<QualifiedTypeName> dependencies2 = ImmutableSet.of(QualifiedTypeName.of("dep2"));
        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedElements2, dependencies2, TestCacheElement.class);

        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        assertNotNull(result);
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("dep2")),
        		ImmutableSet.copyOf(result.getRight().get(SourceJavaFile.of(file1.toAbsolutePath().toString()))));
    }

    @Test
    void testElementAddedToNewFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");

        Files.createFile(file1);
        Files.createFile(file2);

        FileTime timeFile2 = Files.getLastModifiedTime(file2);
        String[] files = {file1.toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();
        String doc2URI = UriUtil.toUri(file2.toFile()).toString();

        List<TestCacheElement> generatedElements1 = new ArrayList<>();
        generatedElements1.add(new TestCacheElement(doc1URI, "element1", "field"));

        cache.store(CACHE_KEY_VERSION_1, files, generatedElements1, null, TestCacheElement.class);

        List<TestCacheElement> generatedElements2 = new ArrayList<>();
        generatedElements2.add(new TestCacheElement(doc2URI, "element2", "interface"));

        cache.update(CACHE_KEY_VERSION_1, file2.toString(), timeFile2.toMillis(), generatedElements2, null, TestCacheElement.class);

        TestCacheElement[] cachedElements = cache.retrieveSymbols(CACHE_KEY_VERSION_1, new String[]{file1.toString(), file2.toString()}, TestCacheElement.class);
        assertNotNull(cachedElements);
        assertEquals(2, cachedElements.length);
    }

    @Test
    void testDependencyAddedToNewFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");

        Files.createFile(file1);
        Files.createFile(file2);

        FileTime timeFile2 = Files.getLastModifiedTime(file2);
        String[] files = {file1.toString()};

        List<TestCacheElement> generatedElements1 = ImmutableList.of();
        Multimap<SourceJavaFile, QualifiedTypeName> dependencies1 = ImmutableMultimap.of(
                SourceJavaFile.of(file1.toString()), QualifiedTypeName.of("dep1"));
        cache.store(CACHE_KEY_VERSION_1, files, generatedElements1, dependencies1, TestCacheElement.class);

        Set<QualifiedTypeName> dependencies2 = ImmutableSet.of(QualifiedTypeName.of("dep2"));
        cache.update(CACHE_KEY_VERSION_1, file2.toString(), timeFile2.toMillis(), generatedElements1, dependencies2, TestCacheElement.class);

        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(CACHE_KEY_VERSION_1, new String[]{file1.toString(), file2.toString()}, TestCacheElement.class);
        assertNotNull(result);
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("dep2")), ImmutableSet.copyOf(result.getRight().get(SourceJavaFile.of(file2.toString()))));
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("dep1")), ImmutableSet.copyOf(result.getRight().get(SourceJavaFile.of(file1.toString()))));
    }

    @Test
    void testProjectDeleted() throws Exception {
        IndexCacheKey key1 = CACHE_KEY_VERSION_1;
        cache.store(key1, new String[0], new ArrayList<>(), null, TestCacheElement.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));

        cache.remove(key1);
        assertFalse(Files.exists(tempDir.resolve(Paths.get(key1.toString() + STORAGE_FILE_EXTENSION))));
    }

    @Test
    void testFileDeleted() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");

        Files.createFile(file1);
        Files.createFile(file2);

        String[] files = {file1.toAbsolutePath().toString(), file2.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toASCIIString();
        String doc2URI = UriUtil.toUri(file2.toFile()).toASCIIString();

        List<TestCacheElement> generatedElements = new ArrayList<>();
        generatedElements.add(new TestCacheElement(doc1URI, "element1", "field"));
        generatedElements.add(new TestCacheElement(doc2URI, "element2", "field"));

        Multimap<SourceJavaFile, QualifiedTypeName> dependencies = ImmutableMultimap.of(
                SourceJavaFile.of(file1.toAbsolutePath().toString()), QualifiedTypeName.of("dep1"),
                SourceJavaFile.of(file2.toAbsolutePath().toString()), QualifiedTypeName.of("dep2"));
        cache.store(CACHE_KEY_VERSION_1, files, generatedElements, dependencies, TestCacheElement.class);
        cache.removeFile(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), TestCacheElement.class);

        files = new String[]{file2.toAbsolutePath().toString()};
        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        TestCacheElement[] cachedElements = result.getLeft();
        assertNotNull(result);
        assertEquals(1, cachedElements.length);

        assertEquals("element2", cachedElements[0].getName());
        assertEquals("field", cachedElements[0].getKind());
        assertEquals(doc2URI, cachedElements[0].getDocURI());

        Multimap<SourceJavaFile, QualifiedTypeName> cachedDependencies = result.getRight();
        assertEquals(ImmutableSet.of(), ImmutableSet.copyOf(cachedDependencies.get(SourceJavaFile.of(file1.toAbsolutePath().toString()))));
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("dep2")), ImmutableSet.copyOf(cachedDependencies.get(SourceJavaFile.of(file2.toAbsolutePath().toString()))));
    }

    @Test
    void testMultipleFilesDeleted() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");
        Path file3 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile3");
        Path file4 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile4");

        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);
        Files.createFile(file4);

        String[] files = {
        		file1.toAbsolutePath().toString(),
        		file2.toAbsolutePath().toString(),
        		file3.toAbsolutePath().toString(),
        		file4.toAbsolutePath().toString()
        };

        String doc1URI = UriUtil.toUri(file1.toFile()).toASCIIString();
        String doc2URI = UriUtil.toUri(file2.toFile()).toASCIIString();
        String doc3URI = UriUtil.toUri(file3.toFile()).toASCIIString();
        String doc4URI = UriUtil.toUri(file4.toFile()).toASCIIString();

        List<TestCacheElement> generatedElements = new ArrayList<>();
        generatedElements.add(new TestCacheElement(doc1URI, "element1", "field"));
        generatedElements.add(new TestCacheElement(doc2URI, "element2", "field"));
        generatedElements.add(new TestCacheElement(doc3URI, "element3", "field"));
        generatedElements.add(new TestCacheElement(doc4URI, "element4", "field"));

        Multimap<SourceJavaFile, QualifiedTypeName> dependencies = ImmutableMultimap.of(
                SourceJavaFile.of(file1.toAbsolutePath().toString()), QualifiedTypeName.of("dep1"),
                SourceJavaFile.of(file2.toAbsolutePath().toString()), QualifiedTypeName.of("dep2"));
        cache.store(CACHE_KEY_VERSION_1, files, generatedElements, dependencies, TestCacheElement.class);
        cache.removeFiles(CACHE_KEY_VERSION_1, new String[] {file1.toAbsolutePath().toString(), file3.toAbsolutePath().toString()}, TestCacheElement.class);

        files = new String[]{file2.toAbsolutePath().toString(), file4.toAbsolutePath().toString()};
        Pair<TestCacheElement[], Multimap<SourceJavaFile, QualifiedTypeName>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, TestCacheElement.class);
        TestCacheElement[] cachedElements = result.getLeft();
        assertNotNull(result);
        assertEquals(2, cachedElements.length);

        assertEquals("element2", cachedElements[0].getName());
        assertEquals("field", cachedElements[0].getKind());
        assertEquals(doc2URI, cachedElements[0].getDocURI());

        assertEquals("element4", cachedElements[1].getName());
        assertEquals("field", cachedElements[1].getKind());
        assertEquals(doc4URI, cachedElements[1].getDocURI());

        Multimap<SourceJavaFile, QualifiedTypeName> cachedDependencies = result.getRight();
        assertEquals(ImmutableSet.of(), ImmutableSet.copyOf(cachedDependencies.get(SourceJavaFile.of(file1.toAbsolutePath().toString()))));
        assertEquals(ImmutableSet.of(QualifiedTypeName.of("dep2")), ImmutableSet.copyOf(cachedDependencies.get(SourceJavaFile.of(file2.toAbsolutePath().toString()))));
    }

}
