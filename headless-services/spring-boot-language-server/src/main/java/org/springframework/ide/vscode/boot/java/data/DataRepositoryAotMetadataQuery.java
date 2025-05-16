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

/**
 * Details about the AOT generated query.
 * 
 * query: For JPA-based repositories, this field contains the generated SQL query statememt for the query method
 * filter, sort, projection, pipeline: Query details for MongoDB-based repository query methods
 */
public record DataRepositoryAotMetadataQuery(String query, String filter, String sort, String projection, String pipeline, String fields) {
}
