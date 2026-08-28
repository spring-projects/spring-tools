/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetTypes;
import org.springsource.ide.eclipse.commons.frameworks.core.util.ArrayEncoder;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

@SuppressWarnings("rawtypes")
public class RunTargetPropertiesManager implements ValueListener<ImmutableSet<RunTarget>> {

	private final BootDashModelContext context;

	private final RunTargetType<?>[] types;

	public static final String RUN_TARGET_KEY = "runTargets-v2";

	public RunTargetPropertiesManager(BootDashModelContext context, Collection<RunTargetType> types) {
		this(context, types.toArray(new RunTargetType[types.size()]));
	}

	public RunTargetPropertiesManager(BootDashModelContext context, RunTargetType[] types) {
		this.context = context;
		this.types = types;
	}

	@SuppressWarnings({ "unchecked" })
	public List<RunTarget> load() {

		List<RunTarget> targets = new ArrayList<>();
		for (RunTargetType type : types) {
			if (type == RunTargetTypes.LOCAL) {
				targets.add(RunTargets.LOCAL);
			} else if (type.canInstantiate()) {
				String serializedList = context.getRunTargetProperties().get(type, RUN_TARGET_KEY);
				if (serializedList != null) {
					String[] list = ArrayEncoder.decode(serializedList);
					for (String serializedParams : list) {
						Object params = type.parseParams(serializedParams);
						try {
							RunTarget target = type.createRunTarget(params);
							if (target != null) {
								targets.add(target);
							}
						} catch (Exception e) {
							//log and ignore invalid run targets
							Log.log(e);
						}
					}
				}
			}
		}
		return targets;
	}

	@Override
	public void gotValue(LiveExpression<ImmutableSet<RunTarget>> exp, ImmutableSet<RunTarget> value) {
		store(value);
	}

	public synchronized void store(Set<RunTarget> _targets) {
		// Only persist run target properties that can be instantiated
		Multimap<RunTargetType<?>, RunTarget<?>> targetsByType = MultimapBuilder.hashKeys().linkedListValues().build();
		for (RunTarget<?> target : _targets) {
			RunTargetType<?> type = target.getType();
			if (type.canInstantiate()) {
				targetsByType.put(type, target);
			}
		}

		for (RunTargetType type : types) {
			//Careful... must iterate all types, even when there are no more targets for a given type.
			// See: https://www.pivotaltracker.com/story/show/171950112
			if (type.canInstantiate()) {
				try {
					Collection<RunTarget<?>> targets = targetsByType.get(type);
					List<String> strings = new ArrayList<>(targets.size());
					for (RunTarget t : targets) {
						String s = type.serialize(t.getParams());
						if (s != null) {
							strings.add(s);
						}
					}
					context.getRunTargetProperties().put(type, RUN_TARGET_KEY, ArrayEncoder.encode(strings.toArray(new String[strings.size()])));
				} catch (Exception e) {
					Log.log(e);
				}
			}
		}
	}

}
