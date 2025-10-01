/*******************************************************************************
 * Copyright (c) 2019, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
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
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.index.cache.AbstractIndexCacheable;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheOnDisc;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.commons.util.UriUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class IndexCacheOnDiscTest {

	private static final IndexCacheKey CACHE_KEY_VERSION_1 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "1");

	private Path tempDir;
	private IndexCacheOnDisc cache;

	@BeforeEach
	public void setup() throws Exception {
		tempDir = Files.createTempDirectory("cachetest");
		cache = new IndexCacheOnDisc(tempDir.toFile());
	}

	@AfterEach
	public void deleteTempDir() throws Exception {
		FileUtils.deleteDirectory(tempDir.toFile());
	}

    @Test
    void testEmptyCache() throws Exception {
        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(new IndexCacheKey("something", "someIndexer", "someCategory", "0"), new String[0], CachedSymbol.class);
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

        List<CachedSymbol> generatedSymbols = new ArrayList<>();
        WorkspaceSymbol symbol = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location("docURI", new Range(new Position(3, 10), new Position(3, 20)))));
        generatedSymbols.add(new CachedSymbol("", timeFile1.toMillis(), symbol));

        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols, ImmutableMultimap.of(
                file1.toString(), "file1dep1",
                file2.toString(), "file2dep1",
                file2.toString(), "file2dep2"
        ), CachedSymbol.class);

        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, CachedSymbol.class);

        CachedSymbol[] cachedSymbols = result.getLeft();
        assertNotNull(cachedSymbols);
        assertEquals(1, cachedSymbols.length);

        assertEquals("symbol1", cachedSymbols[0].getSymbol().getName());
        assertEquals(SymbolKind.Field, cachedSymbols[0].getSymbol().getKind());
        assertEquals(new Location("docURI", new Range(new Position(3, 10), new Position(3, 20))), cachedSymbols[0].getSymbol().getLocation().getLeft());

        Multimap<String, String> dependencies = result.getRight();
        assertEquals(2, dependencies.keySet().size());
        assertEquals(dependencies.get(file1.toString()), ImmutableSet.of("file1dep1"));
        assertEquals(dependencies.get(file2.toString()), ImmutableSet.of("file2dep1", "file2dep2"));

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

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toString(), file2.toString(), file3.toString()};

        List<CachedSymbol> generatedSymbols = new ArrayList<>();
        WorkspaceSymbol symbol = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location("docURI", new Range(new Position(3, 10), new Position(3, 20)))));
        generatedSymbols.add(new CachedSymbol("", timeFile1.toMillis(), symbol));

        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols, null, CachedSymbol.class);

        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(new IndexCacheKey("someOtherProject", "someOtherIndexer", "someOtherCategory", "1"), files, CachedSymbol.class);
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

        List<CachedSymbol> generatedSymbols = new ArrayList<>();
        WorkspaceSymbol symbol = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location("docURI", new Range(new Position(3, 10), new Position(3, 20)))));
        generatedSymbols.add(new CachedSymbol("", timeFile1.toMillis(), symbol));

        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols, ImmutableMultimap.of(
                file1.toString(), "file1dep",
                file2.toString(), "file2dep"
        ), CachedSymbol.class);

        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 1000));

        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
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
        cache.store(CACHE_KEY_VERSION_1, files, new ArrayList<>(), null, CachedSymbol.class);

        String[] moreFiles = {file1.toString(), file2.toString(), file3.toString()};
        assertNull(cache.retrieve(CACHE_KEY_VERSION_1, moreFiles, CachedSymbol.class));
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
        cache.store(CACHE_KEY_VERSION_1, files, new ArrayList<>(), null, CachedSymbol.class);

        String[] fewerFiles = {file1.toString(), file2.toString()};
        assertNull(cache.retrieve(CACHE_KEY_VERSION_1, fewerFiles, CachedSymbol.class));
    }

    @Test
    void testDeleteOldCacheFileIfNewOneIsStored() throws Exception {
        IndexCacheKey key1 = CACHE_KEY_VERSION_1;
        cache.store(key1, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));

        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");
        cache.store(key2, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key2.toString() + ".json"))));
        assertFalse(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));
    }

    @Test
    void testDoNotRetrieveOldCacheDataIfNewerVersionIsStored() throws Exception {
        IndexCacheKey key1 = CACHE_KEY_VERSION_1;
        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");

        cache.store(key1, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertNotNull(cache.retrieve(key1, new String[0], CachedSymbol.class));
        assertNull(cache.retrieve(key2, new String[0], CachedSymbol.class));

        cache.store(key2, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertNull(cache.retrieve(key1, new String[0], CachedSymbol.class));
        assertNotNull(cache.retrieve(key2, new String[0], CachedSymbol.class));
    }

    @Test
    void testDeleteOldCacheFileFromPreviousReleasesIfNewOneIsStored() throws Exception {
        IndexCacheKey key1 = new IndexCacheKey("someProject", "someIndexer", "", "2");
        cache.store(key1, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));

        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");
        cache.store(key2, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key2.toString() + ".json"))));
        assertFalse(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));
    }

    @Test
    void testDoNotDeleteCacheFileFromOtherCategory() throws Exception {
        IndexCacheKey key1 = new IndexCacheKey("someProject", "someIndexer", "someCategory", "2");
        cache.store(key1, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));

        IndexCacheKey key2 = new IndexCacheKey("someProject", "someIndexer", "otherCategory", "2");
        cache.store(key2, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key2.toString() + ".json"))));
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));
    }

    @Test
    void testSymbolAddedToExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();

        List<CachedSymbol> generatedSymbols1 = new ArrayList<>();
        WorkspaceSymbol symbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location("docURI", new Range(new Position(3, 10), new Position(3, 20)))));
        generatedSymbols1.add(new CachedSymbol(doc1URI, timeFile1.toMillis(), symbol1));

        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols1, null, CachedSymbol.class);

        List<CachedSymbol> generatedSymbols2 = new ArrayList<>();
        symbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location(doc1URI, new Range(new Position(3, 10), new Position(3, 20)))));
        WorkspaceSymbol symbol2 = new WorkspaceSymbol("symbol2", SymbolKind.Interface, Either.forLeft(new Location(doc1URI, new Range(new Position(5, 5), new Position(5, 10)))));

        generatedSymbols2.add(new CachedSymbol(doc1URI, timeFile1.toMillis() + 2000, symbol1));
        generatedSymbols2.add(new CachedSymbol(doc1URI, timeFile1.toMillis() + 2000, symbol2));

        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));
        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedSymbols2, null, CachedSymbol.class);

        AbstractIndexCacheable[] cachedSymbols = cache.retrieveSymbols(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
        assertNotNull(cachedSymbols);
        assertEquals(2, cachedSymbols.length);

        assertEquals(timeFile1.toMillis() + 2000, cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file1.toString()));
    }

    @Test
    void testSymbolsAddedToMultipleFiles() throws Exception {

        // create 3 files with one symbol each
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

        List<CachedSymbol> generatedSymbols = new ArrayList<>();
        WorkspaceSymbol symbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location(doc1URI, new Range(new Position(3, 10), new Position(3, 20)))));
        WorkspaceSymbol symbol2 = new WorkspaceSymbol("symbol2", SymbolKind.Field, Either.forLeft(new Location(doc2URI, new Range(new Position(3, 10), new Position(3, 20)))));
        WorkspaceSymbol symbol3 = new WorkspaceSymbol("symbol3", SymbolKind.Field, Either.forLeft(new Location(doc3URI, new Range(new Position(3, 10), new Position(3, 20)))));

        generatedSymbols.add(new CachedSymbol(doc1URI, timeFile1.toMillis(), symbol1));
        generatedSymbols.add(new CachedSymbol(doc2URI, timeFile2.toMillis(), symbol2));
        generatedSymbols.add(new CachedSymbol(doc3URI, timeFile3.toMillis(), symbol3));

        // store original version of the symbols to the cache 
        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols, null, CachedSymbol.class);


        // create updated and new symbols
        List<CachedSymbol> updatedSymbols = new ArrayList<>();

        WorkspaceSymbol updatedSymbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location(doc1URI, new Range(new Position(3, 10), new Position(3, 20)))));
        WorkspaceSymbol newSymbol1 = new WorkspaceSymbol("symbol1-new", SymbolKind.Interface, Either.forLeft(new Location(doc1URI, new Range(new Position(5, 5), new Position(5, 10)))));

        updatedSymbols.add(new CachedSymbol(doc1URI, timeFile1.toMillis() + 2000, updatedSymbol1));
        updatedSymbols.add(new CachedSymbol(doc1URI, timeFile1.toMillis() + 2000, newSymbol1));
        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));

        WorkspaceSymbol updatedSymbol2 = new WorkspaceSymbol("symbol2-updated", SymbolKind.Field, Either.forLeft(new Location(doc2URI, new Range(new Position(3, 10), new Position(3, 20)))));
        updatedSymbols.add(new CachedSymbol(doc2URI, timeFile2.toMillis() + 3000, updatedSymbol2));
        assertTrue(file2.toFile().setLastModified(timeFile2.toMillis() + 3000));

        String[] updatedFiles = new String[]{file1.toAbsolutePath().toString(), file2.toAbsolutePath().toString()};
        long[] updatedModificationTimestamps = new long[]{timeFile1.toMillis() + 2000, timeFile2.toMillis() + 3000};

        // update multiple files in the cache
        cache.update(CACHE_KEY_VERSION_1, updatedFiles, updatedModificationTimestamps, updatedSymbols, null, CachedSymbol.class);

        // double check whether all changes got stored and retrieved correctly
        CachedSymbol[] cachedSymbols = cache.retrieveSymbols(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
        assertNotNull(cachedSymbols);
        assertEquals(4, cachedSymbols.length);

        assertSymbol(updatedSymbol1, cachedSymbols);
        assertSymbol(newSymbol1, cachedSymbols);
        assertSymbol(updatedSymbol2, cachedSymbols);
        assertSymbol(symbol3, cachedSymbols);

        assertEquals(timeFile1.toMillis() + 2000, cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file1.toString()));
        assertEquals(timeFile2.toMillis() + 3000, cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file2.toString()));
        assertEquals(timeFile3.toMillis(), cache.getModificationTimestamp(CACHE_KEY_VERSION_1, file3.toString()));
    }

	private void assertSymbol(WorkspaceSymbol enhancedSymbol, CachedSymbol[] cachedSymbols) {
		for (CachedSymbol cachedSymbol : cachedSymbols) {
			WorkspaceSymbol symbol = cachedSymbol.getSymbol();
			
			if (symbol.toString().equals(enhancedSymbol.toString())) {
				return;
			}
		}
		
		
		fail("symbol not found: " + enhancedSymbol.toString());
	}

    @Test
    void testDependencyAddedToExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        List<CachedSymbol> generatedSymbols = ImmutableList.of();

        Multimap<String, String> dependencies = ImmutableMultimap.of(file1.toString(), "dep1");
        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols, dependencies, CachedSymbol.class);

        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));
        Set<String> dependencies2 = ImmutableSet.of("dep1", "dep2");
        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedSymbols, dependencies2, CachedSymbol.class);

        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
        assertNotNull(result);
        assertEquals(ImmutableSet.of("dep1", "dep2"), result.getRight().get(file1.toString()));
    }

    @Test
    void testSymbolRemovedFromExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();

        List<CachedSymbol> generatedSymbols1 = new ArrayList<>();
        WorkspaceSymbol symbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location("docURI", new Range(new Position(3, 10), new Position(3, 20)))));
        generatedSymbols1.add(new CachedSymbol(doc1URI, timeFile1.toMillis(), symbol1));

        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols1, null, CachedSymbol.class);

        List<CachedSymbol> generatedSymbols2 = new ArrayList<>();
        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));

        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedSymbols2, null, CachedSymbol.class);

        AbstractIndexCacheable[] cachedSymbols = cache.retrieveSymbols(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
        assertNotNull(cachedSymbols);
        assertEquals(0, cachedSymbols.length);
    }

    @Test
    void testDependencyRemovedFromExistingFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");

        Files.createFile(file1);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        String[] files = {file1.toAbsolutePath().toString()};

        List<CachedSymbol> generatedSymbols1 = ImmutableList.of();

        ImmutableMultimap<String, String> dependencies1 = ImmutableMultimap.of(
                file1.toString(), "dep1",
                file1.toString(), "dep2"
        );

        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols1, dependencies1, CachedSymbol.class);

        List<CachedSymbol> generatedSymbols2 = new ArrayList<>();
        assertTrue(file1.toFile().setLastModified(timeFile1.toMillis() + 2000));

        Set<String> dependencies2 = ImmutableSet.of("dep2");
        cache.update(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), timeFile1.toMillis() + 2000, generatedSymbols2, dependencies2, CachedSymbol.class);

        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
        assertNotNull(result);
        assertEquals(ImmutableSet.of("dep2"), result.getRight().get(file1.toString()));
    }

    @Test
    void testSymbolAddedToNewFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");

        Files.createFile(file1);
        Files.createFile(file2);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        FileTime timeFile2 = Files.getLastModifiedTime(file2);
        String[] files = {file1.toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toString();
        String doc2URI = UriUtil.toUri(file2.toFile()).toString();

        List<CachedSymbol> generatedSymbols1 = new ArrayList<>();
        WorkspaceSymbol symbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location(doc1URI, new Range(new Position(3, 10), new Position(3, 20)))));
        generatedSymbols1.add(new CachedSymbol(doc1URI, timeFile1.toMillis(), symbol1));

        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols1, null, CachedSymbol.class);

        List<CachedSymbol> generatedSymbols2 = new ArrayList<>();
        WorkspaceSymbol symbol2 = new WorkspaceSymbol("symbol2", SymbolKind.Interface, Either.forLeft(new Location(doc2URI, new Range(new Position(5, 5), new Position(5, 10)))));

        generatedSymbols2.add(new CachedSymbol(doc2URI, timeFile2.toMillis(), symbol2));

        cache.update(CACHE_KEY_VERSION_1, file2.toString(), timeFile2.toMillis(), generatedSymbols2, null, CachedSymbol.class);

        AbstractIndexCacheable[] cachedSymbols = cache.retrieveSymbols(CACHE_KEY_VERSION_1, new String[]{file1.toString(), file2.toString()}, CachedSymbol.class);
        assertNotNull(cachedSymbols);
        assertEquals(2, cachedSymbols.length);
    }

    @Test
    void testDependencyAddedToNewFile() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");

        Files.createFile(file1);
        Files.createFile(file2);

        FileTime timeFile2 = Files.getLastModifiedTime(file2);
        String[] files = {file1.toString()};

        List<CachedSymbol> generatedSymbols1 = ImmutableList.of();
        Multimap<String, String> dependencies1 = ImmutableMultimap.of(
                file1.toString(), "dep1"
        );
        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols1, dependencies1, CachedSymbol.class);

        Set<String> dependencies2 = ImmutableSet.of("dep2");
        cache.update(CACHE_KEY_VERSION_1, file2.toString(), timeFile2.toMillis(), generatedSymbols1, dependencies2, CachedSymbol.class);

        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(CACHE_KEY_VERSION_1, new String[]{file1.toString(), file2.toString()}, CachedSymbol.class);
        assertNotNull(result);
        assertEquals(ImmutableSet.of("dep2"), result.getRight().get(file2.toString()));
        assertEquals(ImmutableSet.of("dep1"), result.getRight().get(file1.toString()));
    }

    @Test
    void testProjectDeleted() throws Exception {
        IndexCacheKey key1 = CACHE_KEY_VERSION_1;
        cache.store(key1, new String[0], new ArrayList<>(), null, CachedSymbol.class);
        assertTrue(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));

        cache.remove(key1);
        assertFalse(Files.exists(tempDir.resolve(Paths.get(key1.toString() + ".json"))));
    }

    @Test
    void testFileDeleted() throws Exception {
        Path file1 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile1");
        Path file2 = Paths.get(tempDir.toAbsolutePath().toString(), "tempFile2");

        Files.createFile(file1);
        Files.createFile(file2);

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        FileTime timeFile2 = Files.getLastModifiedTime(file2);
        String[] files = {file1.toAbsolutePath().toString(), file2.toAbsolutePath().toString()};

        String doc1URI = UriUtil.toUri(file1.toFile()).toASCIIString();
        String doc2URI = UriUtil.toUri(file2.toFile()).toASCIIString();

        List<CachedSymbol> generatedSymbols = new ArrayList<>();

        WorkspaceSymbol symbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location(doc1URI, new Range(new Position(3, 10), new Position(3, 20)))));
        WorkspaceSymbol symbol2 = new WorkspaceSymbol("symbol2", SymbolKind.Field, Either.forLeft(new Location(doc2URI, new Range(new Position(5, 10), new Position(5, 20)))));

        generatedSymbols.add(new CachedSymbol(doc1URI, timeFile1.toMillis(), symbol1));
        generatedSymbols.add(new CachedSymbol(doc2URI, timeFile2.toMillis(), symbol2));

        Multimap<String, String> dependencies = ImmutableMultimap.of(
                file1.toString(), "dep1",
                file2.toString(), "dep2"
        );
        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols, dependencies, CachedSymbol.class);
        cache.removeFile(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), CachedSymbol.class);

        files = new String[]{file2.toAbsolutePath().toString()};
        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
        CachedSymbol[] cachedSymbols = result.getLeft();
        assertNotNull(result);
        assertEquals(1, cachedSymbols.length);

        assertEquals("symbol2", cachedSymbols[0].getSymbol().getName());
        assertEquals(SymbolKind.Field, cachedSymbols[0].getSymbol().getKind());
        assertEquals(new Location(doc2URI, new Range(new Position(5, 10), new Position(5, 20))), cachedSymbols[0].getSymbol().getLocation().getLeft());

        Multimap<String, String> cachedDependencies = result.getRight();
        assertEquals(ImmutableSet.of(), cachedDependencies.get(file1.toString()));
        assertEquals(ImmutableSet.of("dep2"), cachedDependencies.get(file2.toString()));
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

        FileTime timeFile1 = Files.getLastModifiedTime(file1);
        FileTime timeFile2 = Files.getLastModifiedTime(file2);
        FileTime timeFile3 = Files.getLastModifiedTime(file3);
        FileTime timeFile4 = Files.getLastModifiedTime(file4);
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

        List<CachedSymbol> generatedSymbols = new ArrayList<>();

        WorkspaceSymbol symbol1 = new WorkspaceSymbol("symbol1", SymbolKind.Field, Either.forLeft(new Location(doc1URI, new Range(new Position(3, 10), new Position(3, 20)))));
        WorkspaceSymbol symbol2 = new WorkspaceSymbol("symbol2", SymbolKind.Field, Either.forLeft(new Location(doc2URI, new Range(new Position(5, 10), new Position(5, 20)))));
        WorkspaceSymbol symbol3 = new WorkspaceSymbol("symbol3", SymbolKind.Field, Either.forLeft(new Location(doc3URI, new Range(new Position(20, 11), new Position(20, 30)))));
        WorkspaceSymbol symbol4 = new WorkspaceSymbol("symbol4", SymbolKind.Field, Either.forLeft(new Location(doc4URI, new Range(new Position(4, 4), new Position(5, 5)))));

        generatedSymbols.add(new CachedSymbol(doc1URI, timeFile1.toMillis(), symbol1));
        generatedSymbols.add(new CachedSymbol(doc2URI, timeFile2.toMillis(), symbol2));
        generatedSymbols.add(new CachedSymbol(doc3URI, timeFile3.toMillis(), symbol3));
        generatedSymbols.add(new CachedSymbol(doc4URI, timeFile4.toMillis(), symbol4));

        Multimap<String, String> dependencies = ImmutableMultimap.of(
                file1.toString(), "dep1",
                file2.toString(), "dep2"
        );
        cache.store(CACHE_KEY_VERSION_1, files, generatedSymbols, dependencies, CachedSymbol.class);
//        cache.removeFile(CACHE_KEY_VERSION_1, file1.toAbsolutePath().toString(), CachedSymbol.class);
//        cache.removeFile(CACHE_KEY_VERSION_1, file3.toAbsolutePath().toString(), CachedSymbol.class);
        cache.removeFiles(CACHE_KEY_VERSION_1, new String[] {file1.toAbsolutePath().toString(), file3.toAbsolutePath().toString()}, CachedSymbol.class);

        files = new String[]{file2.toAbsolutePath().toString(), file4.toAbsolutePath().toString()};
        Pair<CachedSymbol[], Multimap<String, String>> result = cache.retrieve(CACHE_KEY_VERSION_1, files, CachedSymbol.class);
        CachedSymbol[] cachedSymbols = result.getLeft();
        assertNotNull(result);
        assertEquals(2, cachedSymbols.length);

        assertEquals("symbol2", cachedSymbols[0].getSymbol().getName());
        assertEquals(SymbolKind.Field, cachedSymbols[0].getSymbol().getKind());
        assertEquals(new Location(doc2URI, new Range(new Position(5, 10), new Position(5, 20))), cachedSymbols[0].getSymbol().getLocation().getLeft());

        assertEquals("symbol4", cachedSymbols[1].getSymbol().getName());
        assertEquals(SymbolKind.Field, cachedSymbols[1].getSymbol().getKind());
        assertEquals(new Location(doc4URI, new Range(new Position(4, 4), new Position(5, 5))), cachedSymbols[1].getSymbol().getLocation().getLeft());

        Multimap<String, String> cachedDependencies = result.getRight();
        assertEquals(ImmutableSet.of(), cachedDependencies.get(file1.toString()));
        assertEquals(ImmutableSet.of("dep2"), cachedDependencies.get(file2.toString()));
    }

}
