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
package org.springframework.ide.vscode.commons.yaml.path;

import java.util.stream.Stream;

import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.Streams;

public class RepeatingYamlTraversal extends AbstractYamlTraversal {

	private YamlTraversal step;

	public RepeatingYamlTraversal(YamlTraversal step) {
		Assert.isLegal(!step.canEmpty()); //This implementation is still too simplistic to handle that properly!
				// If you hit this assert, then it may be time to make it more sophisticated.
		this.step = step;
	}

	@Override
	public <T extends YamlNavigable<T>> Stream<T> traverseAmbiguously(T start) {
		if (start == null) {
			return Stream.empty();
		} else {
			return Stream.concat(
				Streams.fromNullable(start),
				step.traverseAmbiguously(start).flatMap(next -> {
					return this.traverseAmbiguously(next);
				})
			);
		}
	}

	@Override
	public String toString() {
		return "Repeat("+step+")";
	}

	@Override
	public YamlTraversal repeat() {
		//don't make 'Repeat(Repeat(...))'
		return this;
	}

	@Override
	public boolean canEmpty() {
		return true;
	}
}
