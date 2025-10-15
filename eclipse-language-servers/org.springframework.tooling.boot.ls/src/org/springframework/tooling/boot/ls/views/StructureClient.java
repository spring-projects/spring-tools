package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import com.google.gson.JsonElement;

class StructureClient {
	
	private static final String FETCH_SPRING_BOOT_STRUCTURE = "sts/spring-boot/structure";
	private static final Predicate<ServerCapabilities> WS_STRUCTURE_CMD_CAP = capabilities -> capabilities.getExecuteCommandProvider().getCommands().contains(FETCH_SPRING_BOOT_STRUCTURE);
	
	@SuppressWarnings({ "restriction", "serial" })
	CompletableFuture<List<StereotypeNode>> fetch(boolean updateMetadata) {
		List<IJavaProject> allSpringProjects = BootProjectTracker.streamSpringProjects().toList();
		if (!allSpringProjects.isEmpty()) {
			StructureParameter param = new StructureParameter(updateMetadata, null);
			LanguageServerProjectExecutor lss = LanguageServers.forProject(allSpringProjects.get(0).getProject()).withFilter(WS_STRUCTURE_CMD_CAP).excludeInactive();
			List<CompletableFuture<@Nullable Object>> res = lss.computeAll(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(FETCH_SPRING_BOOT_STRUCTURE, List.of(param))));
			final List<StereotypeNode> nodes = Collections.synchronizedList(new ArrayList<>());
			final Gson gson = new Gson();
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
		}
		return CompletableFuture.completedFuture(Collections.emptyList());
	}
	
	
	private record StructureParameter(boolean updateMetadata, Map<String, Collection<String>>  groupings) {}

}
