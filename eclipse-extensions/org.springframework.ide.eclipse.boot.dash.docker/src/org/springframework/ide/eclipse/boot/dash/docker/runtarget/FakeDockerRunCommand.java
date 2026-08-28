package org.springframework.ide.eclipse.boot.dash.docker.runtarget;

import java.util.Map;
import java.util.Map.Entry;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;

public class FakeDockerRunCommand {

	private String image;
	private Map<String, String> labels;
	private String[] networkAliases;
	private String network;
	private Ports portBindings;
	private String[] env;

	public FakeDockerRunCommand withImage(String image) {
		this.image = image;
		return this;
	}

	public FakeDockerRunCommand withLabels(Map<String, String> labels) {
		this.labels = labels;
		return this;
	}

	@Override
	public String toString() {
		StringBuilder cmd = new StringBuilder("docker run");
		addOptions(cmd);
		addArgs(cmd, image);
		return cmd.toString();
	}

	private void addOptions(StringBuilder cmd) {
		addLabels(cmd);
		addNetwork(cmd);
		addAliases(cmd);
		addPortBindings(cmd);
		addEnv(cmd);
	}

	private void addEnv(StringBuilder cmd) {
		if (env != null && env.length > 0) {
			for (String b : env) {
				addArgs(cmd, "--env", b);
			}
		}
	}

	private void addPortBindings(StringBuilder cmd) {
		if (portBindings != null) {
			Map<ExposedPort, Binding[]> bindings = portBindings.getBindings();
			if (!bindings.isEmpty()) {
				for (Entry<ExposedPort, Binding[]> entry : bindings.entrySet()) {
					ExposedPort exposed = entry.getKey();
					for (Binding bindTo : entry.getValue()) {
						addArgs(cmd, "-p", 
								bindTo.getHostIp()+":"+bindTo.getHostPortSpec()+":"+exposed.getPort()+"/"+exposed.getProtocol()
						);
					}
				}
			}
		}
	}

	private void addNetwork(StringBuilder cmd) {
		if (network != null) {
			addArgs(cmd, "--network", network);
		}
	}

	public void addAliases(StringBuilder cmd) {
		if (networkAliases != null && networkAliases.length > 0) {
			for (String alias : networkAliases) {
				addArgs(cmd, "--network-alias", alias);
			}
		}
	}

	private void addLabels(StringBuilder cmd) {
		if (labels != null && !labels.isEmpty()) {
			for (Entry<String, String> entry : labels.entrySet()) {
				addArgs(cmd, "--label", entry.getKey()+"="+entry.getValue());
			}
		}
	}

	private void addArgs(StringBuilder cmd, String... arg) {
		cmd.append(" \\\n    ");
		cmd.append(CommandUtil.escape(arg));
	}

	public FakeDockerRunCommand withNetworkAliases(String... networkAliases) {
		this.networkAliases = networkAliases;
		return this;
	}

	public FakeDockerRunCommand withNetwork(Network network) {
		this.network = network.getName();
		return this;
	}

	public FakeDockerRunCommand withPortBindings(Ports portBindings) {
		this.portBindings = portBindings;
		return this;
	}

	public FakeDockerRunCommand withEnv(String... env) {
		this.env = env;
		return this;
	}


}
