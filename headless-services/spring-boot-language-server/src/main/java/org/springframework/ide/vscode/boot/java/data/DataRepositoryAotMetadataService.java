/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * @author Martin Lippert
 */
public class DataRepositoryAotMetadataService {
	
	private static final String MODULE_JSON_PROP = "module";

	private static final String TYPE_JSON_PROP = "type";

	private static final String NAME_JSON_PROP = "name";

	private static final String METHODS = "methods";

	private static final Logger log = LoggerFactory.getLogger(DataRepositoryAotMetadataService.class);
	
	final private Gson gson = new GsonBuilder().registerTypeAdapter(DataRepositoryAotMetadata.class, new JsonDeserializer<DataRepositoryAotMetadata>() {

		@Override
		public DataRepositoryAotMetadata deserialize(JsonElement json, Type typeOfT,
				JsonDeserializationContext context) throws JsonParseException {
			JsonObject o = json.getAsJsonObject();
			JsonElement e = o.get(MODULE_JSON_PROP);
			if (e.isJsonPrimitive()) {
				String module = e.getAsString();
				String name = context.deserialize(o.get(NAME_JSON_PROP), String.class);
				String type = context.deserialize(o.get(TYPE_JSON_PROP), String.class);
				DataRepositoryModule moduleType = DataRepositoryModule.valueOf(module.toUpperCase());
				switch (moduleType) {
				case MONGODB:
					return new DataRepositoryAotMetadata(name, type, moduleType, context.deserialize(o.get(METHODS), MongoAotMethodMetadata[].class));
				case JPA:
					return new DataRepositoryAotMetadata(name, type, moduleType, context.deserialize(o.get(METHODS), JpaAotMethodMetadata[].class));
				}
			}
			return null;
		}
		
	}).create();

	public DataRepositoryAotMetadata getRepositoryMetadata(IJavaProject project, String repositoryType) {
		try {
			String metadataFilePath = repositoryType.replace('.', '/') + ".json";
			
			Optional<File> metadataFile = IClasspathUtil.getOutputFolders(project.getClasspath())
				.map(outputFolder -> outputFolder.getParentFile().toPath().resolve("spring-aot/main/resources/").resolve(metadataFilePath))
				.filter(Files::isRegularFile)
				.map(p -> p.toFile())
				.findFirst();
			
			if (metadataFile.isPresent()) {
				return readMetadataFile(metadataFile.get());
			}
			
		} catch (Exception e) {
			log.error("error finding spring data repository definition metadata file", e);
		}
		
		return null;
	}
	
	private DataRepositoryAotMetadata readMetadataFile(File file) {
		
		try (FileReader reader = new FileReader(file)) {
			DataRepositoryAotMetadata result = gson.fromJson(reader, DataRepositoryAotMetadata.class);
			return result;
		}
		catch (IOException e) {
			return null;
		}
	}
	
}
