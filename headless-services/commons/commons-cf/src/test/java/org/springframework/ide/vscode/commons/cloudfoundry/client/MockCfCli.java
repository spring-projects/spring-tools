/*******************************************************************************
 * Copyright (c) 2017, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.cloudfoundry.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.Mockito;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CFClientParams;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CFCredentials;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CfCliParamsProvider;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.ClientParamsProvider;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;

import com.google.common.collect.ImmutableList;

public class MockCfCli {

	public final static CFClientParams DEFAULT_PARAMS = new CFClientParams("test.io", "testuser",
			CFCredentials.fromRefreshToken("refreshtoken"), false);

	public final CloudFoundryClientFactory factory = mock(CloudFoundryClientFactory.class);
	public final ClientRequests client = mock(ClientRequests.class);
	public final ClientParamsProvider paramsProvider = mock(ClientParamsProvider.class);


	public MockCfCli() {
		try {
			//program some default behavior into mocks... most tests will use this.
			//other tests should 'reset' the mocks and reprogram them as needed.
			when(factory.getClient(any(), any())).thenReturn(client);
			when(paramsProvider.getParams()).thenReturn(ImmutableList.of(DEFAULT_PARAMS));
			when(paramsProvider.getMessages()).thenReturn(CfCliParamsProvider.CLI_PROVIDER_MESSAGES);

		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}
	}

	/**
	 * Reset the mocks. Use this if the default's programmed into the mocks don't suite your test case.
	 * <p>
	 * Note: you may also choose to call {@link Mockito}.mock directly if you do not want to
	 * reset all of the mocks.
	 */
	public void reset() throws Exception {
		Mockito.reset(factory, client, paramsProvider);
	}

}
