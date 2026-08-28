/*******************************************************************************
 * Copyright (c) 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.properties;

import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.JmxConnectable;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;

public class GenericRemoteAppGeneralPropertiesSection extends AbstractBdeGeneralPropertiesSection {

	@Override
	protected BootDashElementPropertyControl[] createPropertyControls() {
		return new BootDashElementPropertyControl[] {
				new RunStatePropertyControl(),
				new InstancesPropertyControl(),
				new ProjectPropertyControl(),
				new AppPropertyControl(),
				new UrlPropertyControl<>(BootDashElement.class, "URL:", (e) -> e.getUrl()),
				new DefaultPathPropertyControl(),
				new TagsPropertyControl(),
				new DebugPortPropertyControl(),
				new ReadOnlyStringPropertyControl<>(GenericRemoteAppElement.class, "Jmx URL:", (e) -> e.getJmxUrl(), false)
					.visibleWhen((e) -> e != null && e.getAppData() instanceof JmxConnectable)
		};
	}

}
