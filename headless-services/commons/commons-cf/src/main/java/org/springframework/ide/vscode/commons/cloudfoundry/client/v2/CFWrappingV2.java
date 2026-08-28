/*******************************************************************************
 * Copyright (c) 2016, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.cloudfoundry.client.v2;

import org.cloudfoundry.operations.buildpacks.Buildpack;
import org.cloudfoundry.operations.domains.Domain;
import org.cloudfoundry.operations.services.ServiceInstanceSummary;
import org.cloudfoundry.operations.services.ServiceInstanceType;
import org.cloudfoundry.operations.stacks.Stack;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFBuildpack;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFDomain;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFEntities;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFServiceInstance;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CFStack;

/**
 * Various helper methods to 'wrap' objects returned by CF client into our own
 * types, so that we do not directly expose library types to our code.
 *
 * @author Kris De Volder
 */
public class CFWrappingV2 {

	public static CFBuildpack wrap(Buildpack buildpack) {
		String name = buildpack.getName();
		return CFEntities.createBuildpack(name);
	}

	public static CFDomain wrap(Domain domain) {
		String name = domain.getName();
		return CFEntities.createDomain(name);
	}

	public static CFServiceInstance wrap(ServiceInstanceSummary serviceInstance) {
		String name = serviceInstance.getName();
		String plan = serviceInstance.getPlan();
		String service = serviceInstance.getType() == ServiceInstanceType.USER_PROVIDED ? "user-provided"
				: serviceInstance.getService();

		return CFEntities.createServiceInstance(name, service, plan);
	}

	public static CFStack wrap(Stack stack) {
		if (stack != null) {
			String name = stack.getName();
			return new CFStack() {
				@Override
				public String getName() {
					return name;
				}

				@Override
				public String toString() {
					return "CFStack("+name+")";
				}
			};
		}
		return null;
	}

}
