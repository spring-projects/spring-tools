/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.metadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;

public class PropertiesLoader {

	public static final String MAIN_SPRING_CONFIGURATION_METADATA_JSON = "META-INF/spring-configuration-metadata.json";

	public static final String ADDITIONAL_SPRING_CONFIGURATION_METADATA_JSON = "META-INF/additional-spring-configuration-metadata.json";

	/**
	 * The default classpath location for config metadata loaded when scanning .jar files on the classpath.
	 */
	public static final String[] JAR_META_DATA_LOCATIONS = {
		MAIN_SPRING_CONFIGURATION_METADATA_JSON
		//Not scanning 'additional' metadata because it integrated already in the main data.
	};

	/**
	 * The default classpath location for config metadata loaded when scanning project output folders.
	 */
	public static final String[] PROJECT_META_DATA_LOCATIONS = {
		MAIN_SPRING_CONFIGURATION_METADATA_JSON,
		ADDITIONAL_SPRING_CONFIGURATION_METADATA_JSON
	};

    private static final Logger LOG = Logger.getLogger(PropertiesLoader.class.getName());

	private ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();

	/**
	 * Content hashes of the metadata json files read (not those in jars), to tell later whether such a
	 * file really changed: annotation processors rewrite it on every compile, mostly with the same content.
	 */
	private final Map<Path, String> jsonFileHashes = new HashMap<>();

	public ConfigurationMetadataRepository load(IClasspath classPath) {
		if (classPath != null) {
			try {
				IClasspathUtil.getBinaryRoots(classPath, (cpe) -> !cpe.isSystem()).forEach(fileEntry -> {
					if (fileEntry.exists()) {
						if (fileEntry.isDirectory()) {
							loadFromOutputFolder(fileEntry.toPath());
						} else {
							loadFromJar(fileEntry.toPath());
						}
					}
				});
			} catch (Exception e) {
	    		LOG.log(Level.SEVERE, "Failed to retrieve classpath", e);
			}
		}
		ConfigurationMetadataRepository repository = builder.build();
		return repository;
	}
	
	public ConfigurationMetadataRepository loadJson(Path path) {
		loadFromJsonFile(path);
		return builder.build();
	}

	private void loadFromOutputFolder(Path outputFolderPath) {
		if (outputFolderPath != null && Files.exists(outputFolderPath)) {
			Arrays.stream(PROJECT_META_DATA_LOCATIONS).forEach(mdLoc -> {
				loadFromJsonFile(outputFolderPath.resolve(mdLoc));
			});
		}
	}

	private void loadFromJsonFile(Path mdf) {
		if (Files.exists(mdf)) {
			try {
				byte[] content = Files.readAllBytes(mdf);
				jsonFileHashes.put(normalize(mdf), contentHash(content));
				loadFromInputStream(mdf, new ByteArrayInputStream(content));
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Error loading file '" + mdf + "'", e);
			}
		}
	}

	/**
	 * @return the content hashes of the metadata json files read so far, by normalized absolute path
	 */
	public Map<Path, String> getJsonFileHashes() {
		return jsonFileHashes;
	}

	static Path normalize(Path file) {
		return file.toAbsolutePath().normalize();
	}

	static String contentHash(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // every Java platform has SHA-256
		}
	}

	/**
	 * @return the content hash of the file, or null if it cannot be read (e.g. it was deleted)
	 */
	static String contentHash(Path file) {
		try {
			return contentHash(Files.readAllBytes(file));
		} catch (IOException e) {
			return null;
		}
	}

	private void loadFromJar(Path f) {
		JarFile jarFile = null;
		try {
			jarFile = new JarFile(f.toFile());
			//jarDump(jarFile);
			for (String loc : JAR_META_DATA_LOCATIONS) {
				ZipEntry e = jarFile.getEntry(loc);
				if (e != null) {
					loadFrom(jarFile, e);
				}
			}
		} catch (Throwable e) {
			LOG.log(Level.SEVERE, "Error loading JAR file", e);
		} finally {
			if (jarFile != null) {
				try {
					jarFile.close();
				} catch (IOException e) {
				}
			}
		}
	}


	private void loadFrom(JarFile jarFile, ZipEntry ze) {
		InputStream is = null;
		try {
			is = jarFile.getInputStream(ze);
			loadFromInputStream(jarFile.getName()+"["+ze.getName()+"]", is);
		} catch (Throwable e) {
			LOG.log(Level.SEVERE, "Error loading JAR file", e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private void loadFromInputStream(Object origin, InputStream is) throws IOException {
		builder.withJsonResource(origin, is);
	}

}
