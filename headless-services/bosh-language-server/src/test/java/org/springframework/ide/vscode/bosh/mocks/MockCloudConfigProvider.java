/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.bosh.mocks;

import java.util.concurrent.Callable;

import org.springframework.ide.vscode.bosh.BoshCliConfig;
import org.springframework.ide.vscode.bosh.models.BoshCommandCloudConfigProvider;
import org.springframework.ide.vscode.bosh.models.BoshCommandCloudConfigProviderTest;
import org.springframework.ide.vscode.commons.util.ExternalCommand;
import org.springframework.ide.vscode.commons.util.IOUtil;

public final class MockCloudConfigProvider extends BoshCommandCloudConfigProvider {

	//TODO: use mockito to create this mock instead of a subclass?

	static final String MOCK_DATA_RSRC = "/cmd-out/cloud-config.json";

	private Callable<String> cloudConfigCmdExecutor = () -> IOUtil.toString(BoshCommandCloudConfigProviderTest.class.getResourceAsStream(MOCK_DATA_RSRC));

	private int readCount = 0;

	private Callable<String> reader = null;

	public MockCloudConfigProvider(BoshCliConfig config) {
		super(config);
	}

	/**
	 * Override with a 'fake' which just returns some mock data. That way we can unit-test
	 * without requiring a real bosh setup.
	 */
	@Override
	protected String executeCommand(ExternalCommand cmd) throws Exception {
		readCount++;
		return cloudConfigCmdExecutor.call();
	}

	@Override
	protected String getBlock() throws Exception {
		if (reader == null) {
			return super.getBlock();
		}
		readCount++;
		return reader.call();
	}

	public MockCloudConfigProvider executeCommandWith(Callable<String> executor) {
		this.cloudConfigCmdExecutor = executor;
		return this;
	}

	public MockCloudConfigProvider readWith(Callable<String> reader) {
		this.reader = reader;
		return this;
	}

	public int getReadCount() {
		return readCount;
	}
}