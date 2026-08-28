/*******************************************************************************
 * Copyright (c) 2020, 2021 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.docker.runtarget;

import static org.eclipse.ui.plugin.AbstractUIPlugin.imageDescriptorFromPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.resource.ImageDescriptor;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.docker.ui.DefaultDockerUserInteractions;
import org.springframework.ide.eclipse.boot.dash.docker.ui.DockerUserInteractions;
import org.springframework.ide.eclipse.boot.dash.docker.ui.SelectDockerDaemonDialog;
import org.springframework.ide.eclipse.boot.dash.docker.ui.SelectDockerDaemonDialog.Model;
import org.springframework.ide.eclipse.boot.dash.model.MissingLiveInfoMessages;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.AbstractRemoteRunTargetType;
import org.springframework.ide.eclipse.editor.support.util.HtmlSnippet;
import org.springsource.ide.eclipse.commons.frameworks.core.util.JobUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.util.StringUtils;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

public class DockerRunTargetType extends AbstractRemoteRunTargetType<DockerTargetParams> {
	
	public static final String PLUGIN_ID = "org.springframework.ide.eclipse.boot.dash.docker";

	public DockerRunTargetType(SimpleDIContext injections) {
		super(injections, "Docker");
	}

	@Override
	public CompletableFuture<?> openTargetCreationUi(LiveSetVariable<RunTarget> targets) {
		return JobUtil.runInJob("Docker Target Creation", mon -> {
			DockerRunTarget target = login(targets);
			if (target != null) {
				targets.add(target);
			}
		});
	}

	private DockerRunTarget login(LiveSetVariable<RunTarget> targets) {
		String uri = inputDockerUrl();
		if (StringUtils.hasText(uri)) {
			Set<String> existing = new HashSet<>(targets.getValues().size());
			for (RunTarget t : targets.getValues()) {
				if (t instanceof DockerRunTarget) {
					DockerRunTarget dt = (DockerRunTarget) t;
					existing.add(dt.getParams().getUri());
				}
			}
			if (existing.contains(uri)) {
				ui().errorPopup("Duplicate Target", "A target with the same uri ("+uri+") already exists!");
			} else {
				try {
					DockerClient client = createDockerClient(uri);
					return new DockerRunTarget(this, new DockerTargetParams(uri), client);
				} catch (Error e) {
					DefaultDockerUserInteractions.openBundleWiringError(e);
				}
			}
		}
		return null;
	}

	public static DockerClient createDockerClient(String uri) {
		DefaultDockerClientConfig conf = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(uri).build();
		
		DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
				.dockerHost(conf.getDockerHost())
				.sslConfig(conf.getSSLConfig())
				.maxConnections(200)
				.build();
		return DockerClientImpl.getInstance(conf, httpClient);
	}

	private String inputDockerUrl() {
		DockerUserInteractions ui = injections().getBean(DockerUserInteractions.class);
		Model model = new SelectDockerDaemonDialog.Model();
		ui.selectDockerDaemonDialog(model);
		if (model.okPressed.getValue()) {
			return model.daemonUrl.getValue();
		} else {
			return null;
		}
	}

	@Override
	public RunTarget<DockerTargetParams> createRunTarget(DockerTargetParams params) {
		return new DockerRunTarget(this, params, null);
	}

	@Override
	public ImageDescriptor getIcon() {
		return imageDescriptorFromPlugin(PLUGIN_ID, "/icons/docker.png");
	}

	@Override
	public ImageDescriptor getDisconnectedIcon() {
		return imageDescriptorFromPlugin(PLUGIN_ID, "/icons/docker-inactive.png");
	}


	@Override
	public DockerTargetParams parseParams(String uri) {
		return new DockerTargetParams(uri);
	}

	@Override
	public String serialize(DockerTargetParams p) {
		return p == null ? null : p.getUri();
	}

	@Override
	public MissingLiveInfoMessages getMissingLiveInfoMessages() {
		return new MissingLiveInfoMessages() {
			@Override
			public HtmlSnippet getMissingInfoMessage(String appName, String actuatorEndpoint) {
				
				return buffer -> {
					buffer.raw("<p>");
					buffer.raw("<b>");
					buffer.text(appName);
					buffer.raw("</b>");
					buffer.text(" must be running with JMX and actuator endpoint enabled:");
					buffer.raw("</p>");

					buffer.raw("<ol>");

					buffer.raw("<li>");
					buffer.text("Enable actuator.");
					buffer.raw("</li>");

					buffer.raw("<li>");
					buffer.text("Enable actuator endpoint ");
					buffer.raw("<b>");
					buffer.text(actuatorEndpoint);
					buffer.raw("</b>");
					buffer.text(" in the application.");
					buffer.raw("</li>");
					buffer.raw("</ol>");

					buffer.href(EXTERNAL_DOCUMENT_LINK, "See documentation");
				};
				
			}
		};
	}
	
	

}
