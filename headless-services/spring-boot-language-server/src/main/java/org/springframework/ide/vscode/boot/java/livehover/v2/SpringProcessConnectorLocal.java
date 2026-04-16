/*******************************************************************************
 * Copyright (c) 2019, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * @author Martin Lippert
 */
public class SpringProcessConnectorLocal {

	private static final Logger log = LoggerFactory.getLogger(SpringProcessConnectorLocal.class);
	
	private static final String LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";
	

	private final Map<String, Boolean> projects;
	private final Set<SpringProcessDescriptor> processes;
	
	private final SpringProcessConnectorService processConnectorService;
	private final Executor statusUpdateThreadPool;
	
	private boolean projectsChanged;

	final private BootJavaConfig config;

	
	public SpringProcessConnectorLocal(SpringProcessConnectorService processConnector, ProjectObserver projectObserver, BootJavaConfig config) {
		this.config = config;
		this.projects = new ConcurrentHashMap<>();
		this.processes = Collections.synchronizedSet(new HashSet<>());
		this.statusUpdateThreadPool = Executors.newFixedThreadPool(10);

		this.projectsChanged = false;
		
		this.processConnectorService = processConnector;

		projectObserver.addListener(new ProjectObserver.Listener() {
			@Override
			public void created(IJavaProject project) {
				update(project);
			}
			
			@Override
			public void deleted(IJavaProject project) {
				projects.remove(project.getElementName());
			}
			
			@Override
			public void changed(IJavaProject project) {
				update(project);
			}
			
			protected void update(IJavaProject project) {
				boolean hasActuators = SpringProjectUtil.hasBootActuators(project);
				projects.put(project.getElementName(), hasActuators);
				projectsChanged = true;
			}
			
		});
	}
	
	/**
	 * checks whether this class can operate normally or not - it is recommended to check this before calling out to this class
	 * (if the attach to VirtualMachine library is not around, this class cannot really do anything and will throw exceptions)
	 */
	public boolean isAvailable() {
		if (config.isShowingAllJvmProcesses()) {
			try {
				Class<?> vmClass = VirtualMachine.class;
				return vmClass != null;
			}
			catch (NoClassDefFoundError e) {
				return false;
			}
		}
		return false;
	}
	
	public boolean isLocalProcess(String processKey) {
		return this.processes.stream().anyMatch(process -> processKey.equals(process.getProcessKey()));
	}
	
	public SpringProcessDescriptor[] getProcesses(boolean update, SpringProcessStatus... status) {
		if (update) {
			SpringProcessDescriptor[] newProcesses = updateProcesses();
			if (this.projectsChanged) {
				this.projectsChanged = false;

				SpringProcessDescriptor[] allProcesses = this.processes.toArray(new SpringProcessDescriptor[this.processes.size()]);
				updateStatus(allProcesses);
			}
			else {
				updateStatus(newProcesses);
			}
		}
		
		if (status != null && status.length > 0) {
			List<SpringProcessStatus> statusList = Arrays.asList(status);
			return processes.stream().filter((process) -> statusList.contains(process.getStatus())).toArray(SpringProcessDescriptor[]::new);
		}
		else {
			return (SpringProcessDescriptor[]) processes.toArray(new SpringProcessDescriptor[processes.size()]);
		}
	}

	public SpringProcessDescriptor[] updateProcesses() {
		List<VirtualMachineDescriptor> currentVms = VirtualMachine.list();
		Set<String> currentVMKeys = new HashSet<>();
		
		List<SpringProcessDescriptor> newProcesses = new ArrayList<>();

		for (VirtualMachineDescriptor vm : currentVms) {
			
			try {
				String processID = getProcessID(vm);
				String processName = getProcessName(vm);
				String processKey = SpringProcessConnectorService.getProcessKey(processID, processName);
				
				currentVMKeys.add(processKey);
				
				SpringProcessDescriptor descriptor = new SpringProcessDescriptor(processKey, processID, processName, vm);
				
				if (!processes.contains(descriptor)) {
					processes.add(descriptor);
					newProcesses.add(descriptor);
				}
			}
			catch (Exception e) {
				log.error("error looking into local process: " + vm.id(), e);
			}
		}
		
		Iterator<SpringProcessDescriptor> i = processes.iterator();
		while (i.hasNext()) {
			SpringProcessDescriptor processDescriptor = i.next();
			String processKey = processDescriptor.getProcessKey();
			if (!currentVMKeys.contains(processKey)) {
				i.remove();
				processConnectorService.disconnectProcess(processKey);
			}
		}
		
		return (SpringProcessDescriptor[]) newProcesses.toArray(new SpringProcessDescriptor[newProcesses.size()]);
	}
	
	private void updateStatus(SpringProcessDescriptor[] processes) {
		if (processes != null && processes.length > 0) {
			List<CompletableFuture<Void>> futures = new ArrayList<>();
	
			for (SpringProcessDescriptor process : processes) {
				futures.add(process.updateStatus(projects::containsKey, projects::get, statusUpdateThreadPool));
			}
			
			CompletableFuture<Void> allStatusUpdates = CompletableFuture.allOf((CompletableFuture[]) futures.toArray(new CompletableFuture[futures.size()]));
			try {
				allStatusUpdates.get(5, TimeUnit.SECONDS);
			}
			catch (Exception e) {
				log.info("timeout or problem occurred while updating the status of the new processes");
			}
		}
	}
	
	public CompletableFuture<Void> connectProcess(SpringProcessDescriptor descriptor) {
		VirtualMachine vm = null;
		VirtualMachineDescriptor vmDescriptor = descriptor.getVm();
		
		try {
			String jmxAddress = null;
			vm = VirtualMachine.attach(vmDescriptor);
			
			try {
				jmxAddress = vm.getAgentProperties().getProperty(LOCAL_CONNECTOR_ADDRESS);
			} catch (Exception e) {
				//ignore
			}
	
			if (jmxAddress == null) {
				try {
					jmxAddress = vm.startLocalManagementAgent();
				} catch (Exception e) {
					log.error("Error starting local management agent", e);
					return CompletableFuture.failedFuture(e);
				}
			}
			
			if (jmxAddress != null) {
				String processID = getProcessID(vmDescriptor);
				String processName = getProcessName(vmDescriptor);
				String urlScheme = "http";
				
				SpringProcessConnectorOverJMX connector = new SpringProcessConnectorOverJMX(ProcessType.LOCAL,
						descriptor.getProcessKey(), jmxAddress, urlScheme, processID, processName, descriptor.getProjectName(), null, null);

				return this.processConnectorService.connectProcess(descriptor.getProcessKey(), connector);
			}
			return CompletableFuture.failedFuture(new Exception("No JMX URL available!"));
		}
		catch (Exception e) {
			log.error("exception while connecting to jvm process", e);
			return CompletableFuture.failedFuture(e);
		}
		finally {
			if (vm != null) {
				try {
					vm.detach();
				}
				catch (Exception e) {
					log.error("error detaching from vm: " + vmDescriptor.id(), e);
				}
			}
		}
	}
	
	public boolean isKnownProcessKey(String processKey) {
		return this.processConnectorService.isKnownProcessKey(processKey);
	}

	private String getProcessID(VirtualMachineDescriptor descriptor) {
		return descriptor.id();
	}

	private String getProcessName(VirtualMachineDescriptor descriptor) {
		String rawName = descriptor.displayName();
		int firstSpace = rawName.indexOf(' ');
		return firstSpace < 0 ? rawName : rawName.substring(0, firstSpace);
	}

}
