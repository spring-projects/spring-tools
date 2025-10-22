package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerProjectExecutor;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.springframework.tooling.jdt.ls.commons.BootProjectTracker;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

@SuppressWarnings({ "restriction", "serial" })
class StructureClient {
	
	record Groups (String projectName, List<Group> groups) {}
	record Group (String identifier, String displayName) {}
	record StructureParameter(boolean updateMetadata, Map<String, List<String>>  groups) {}

	private static final String FETCH_SPRING_BOOT_STRUCTURE = "sts/spring-boot/structure";
	private static final String FETCH_STRUCTURE_GROUPS = "sts/spring-boot/structure/groups";
	
	private static final Predicate<ServerCapabilities> WS_STRUCTURE_CMD_CAP = capabilities -> capabilities.getExecuteCommandProvider().getCommands().contains(FETCH_SPRING_BOOT_STRUCTURE);
	private static final Predicate<ServerCapabilities> WS_GROUPS_CMD_CAP = capabilities -> capabilities.getExecuteCommandProvider().getCommands().contains(FETCH_STRUCTURE_GROUPS);
	
	CompletableFuture<List<StereotypeNode>> fetchStructure(StructureParameter param) {
		return getExecutor(WS_STRUCTURE_CMD_CAP).map(lss -> {
			List<CompletableFuture<@Nullable Object>> res = lss.computeAll(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(FETCH_SPRING_BOOT_STRUCTURE, List.of(param))));
			final List<StereotypeNode> nodes = Collections.synchronizedList(new ArrayList<>());
			final Gson gson = new GsonBuilder().registerTypeAdapter(StereotypeNode.class, new StereotypeNodeDeserializer()).create();
			for (CompletableFuture<@Nullable Object> f : res) {
				f.thenAccept(o -> {
					JsonElement json = null;
					if (o instanceof List) {
						json = gson.toJsonTree(o);
					} else if (o instanceof JsonElement) {
						json = (JsonElement) o;
					}
					if (json != null) {
						List<StereotypeNode> n = gson.fromJson(json, new TypeToken<List<StereotypeNode>>() {}.getType());
						if (n != null) {
							nodes.addAll(n);
						}
					}
				});
			}
			return CompletableFuture.allOf(res.toArray(new CompletableFuture[res.size()])).thenApply(v -> nodes);
		}).orElse( CompletableFuture.completedFuture(List.of()));
	}
	
	CompletableFuture<List<Groups>> fetchGroups() {
		return getExecutor(WS_GROUPS_CMD_CAP).map(lss -> {
			List<CompletableFuture<@Nullable Object>> res = lss.computeAll(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(FETCH_STRUCTURE_GROUPS, List.of())));
			final List<Groups> groups = Collections.synchronizedList(new ArrayList<>());
			final Gson gson = new GsonBuilder().create();
			for (CompletableFuture<@Nullable Object> f : res) {
				f.thenAccept(o -> {
					JsonElement json = null;
					if (o instanceof List) {
						json = gson.toJsonTree(o);
					} else if (o instanceof JsonElement) {
						json = (JsonElement) o;
					}
					if (json != null) {
						Groups[] g = gson.fromJson(json, Groups[].class);
						if (g != null) {
							groups.addAll(Arrays.asList(g));
						}
					}
				});
			}
			return CompletableFuture.allOf(res.toArray(new CompletableFuture[res.size()])).thenApply(v -> groups);
		}).orElse(CompletableFuture.completedFuture(List.of()));
	}
	
	private Optional<LanguageServerProjectExecutor> getExecutor(Predicate<ServerCapabilities> capabilityFilter) {
		List<IJavaProject> allSpringProjects = BootProjectTracker.streamSpringProjects().toList();
		if (!allSpringProjects.isEmpty()) {
			return Optional.of(LanguageServers.forProject(allSpringProjects.get(0).getProject()).withFilter(capabilityFilter).excludeInactive());
		}
		return Optional.empty();
	}
	

}
