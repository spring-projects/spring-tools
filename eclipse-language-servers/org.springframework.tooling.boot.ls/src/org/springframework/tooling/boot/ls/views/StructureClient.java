package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

@SuppressWarnings({ "restriction" })
class StructureClient {
	
	record Groups (String projectName, List<Group> groups) {}
	record Group (String identifier, String displayName) {}
	/**
	 * The parameter of the structure command.
	 * 
	 * @param groups per project, the groups to structure its tree by
	 * @param compareAgainst per project, the snapshot to compare against - the most recent one if missing
	 * @param changes whether to mark the nodes that changed since the baseline - the language server
	 *        is spared working out what changed when that is not shown
	 * @param dependencies per project, the ids of the dependencies to include in its tree - sent in
	 *        dependency mode only, and a non-empty one is what puts the language server into that
	 *        mode: no change information on any tree then
	 */
	/**
	 * A dependency of a project that can be included in its tree.
	 * 
	 * @param id identifies the dependency in the selection
	 * @param kind WORKSPACE_PROJECT for a dependency that resolves to a project of the workspace, JAR
	 *        for a library
	 * @param groupId the Maven coordinates of a library, if they are known
	 */
	record DependencyDescriptor(String id, String kind, String displayName, String groupId, String artifactId, String version,
			String projectName, String location) {

		static final String WORKSPACE_PROJECT = "WORKSPACE_PROJECT";
		static final String JAR = "JAR";

		boolean isWorkspaceProject() {
			return WORKSPACE_PROJECT.equals(kind);
		}
	}
	record Dependencies(String projectName, List<DependencyDescriptor> dependencies) {}
	record CaptureBaselineResult(String projectName, int elementCount, String capturedAt) {}
	record ClearBaselineResult(String projectName, boolean hadBaseline) {}
	/**
	 * @param capturedAt identifies the snapshot, a manually captured one has no commit
	 */
	record BaselineHistoryEntry(String commitSha, String commitMessage, String capturedAt, int elementCount) {}
	record StructureParameter(boolean updateMetadata, Collection<String> affectedProjects, Map<String, List<String>> groups,
			Map<String, String> compareAgainst, boolean changes, Map<String, List<String>> dependencies) {}

	private static final String FETCH_SPRING_BOOT_STRUCTURE = "sts/spring-boot/structure";
	private static final String FETCH_STRUCTURE_GROUPS = "sts/spring-boot/structure/groups";
	private static final String FETCH_DEPENDENCIES = "sts/spring-boot/structure/dependencies";
	private static final String CAPTURE_BASELINE = "sts/spring-boot/structure/captureBaseline";
	private static final String CLEAR_BASELINE = "sts/spring-boot/structure/clearBaseline";
	private static final String BASELINE_HISTORY = "sts/spring-boot/structure/baselineHistory";
	// tells the language server the user turned showing changes on - it may ask whether to turn the
	// automatic git baseline on
	private static final String DIFF_ENABLED = "sts/spring-boot/structure/diffEnabled";
	
	private static final Predicate<ServerCapabilities> WS_STRUCTURE_CMD_CAP = capabilities -> capabilities.getExecuteCommandProvider().getCommands().contains(FETCH_SPRING_BOOT_STRUCTURE);
	private static final Predicate<ServerCapabilities> WS_GROUPS_CMD_CAP = capabilities -> capabilities.getExecuteCommandProvider().getCommands().contains(FETCH_STRUCTURE_GROUPS);
	
	CompletableFuture<List<StereotypeNode>> fetchStructure(StructureParameter param) {
		return getExecutor(WS_STRUCTURE_CMD_CAP).map(lss -> {
			List < CompletableFuture < @Nullable Object>> res = lss.computeAll(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(FETCH_SPRING_BOOT_STRUCTURE, List.of(param))));
			final List<StereotypeNode> nodes = Collections.synchronizedList(new ArrayList<>());
			final Gson gson = new GsonBuilder().registerTypeAdapter(StereotypeNode.class, new StereotypeNodeDeserializer()).create();
			for (CompletableFuture < @Nullable Object > f : res) {
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
			List < CompletableFuture < @Nullable Object>> res = lss.computeAll(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(FETCH_STRUCTURE_GROUPS, List.of())));
			final List<Groups> groups = Collections.synchronizedList(new ArrayList<>());
			final Gson gson = new GsonBuilder().create();
			for (CompletableFuture < @Nullable Object > f : res) {
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
	
	/**
	 * The dependencies of a project that can be included in its tree: the ones that resolve to
	 * projects of the workspace first, then the libraries, both sorted by name.
	 */
	CompletableFuture<List<DependencyDescriptor>> dependencies(String projectName) {
		return executeForProject(FETCH_DEPENDENCIES, projectName, Dependencies.class)
				.thenApply(result -> result == null || result.dependencies() == null ? List.<DependencyDescriptor>of() : result.dependencies());
	}

	CompletableFuture<CaptureBaselineResult> captureBaseline(String projectName) {
		return executeForProject(CAPTURE_BASELINE, projectName, CaptureBaselineResult.class);
	}

	CompletableFuture<ClearBaselineResult> clearBaseline(String projectName) {
		return executeForProject(CLEAR_BASELINE, projectName, ClearBaselineResult.class);
	}

	CompletableFuture<List<BaselineHistoryEntry>> baselineHistory(String projectName) {
		return executeForProject(BASELINE_HISTORY, projectName, BaselineHistoryEntry[].class)
				.thenApply(entries -> entries == null ? List.<BaselineHistoryEntry>of() : Arrays.asList(entries));
	}

	/**
	 * Tells the language server that changes are shown. It decides whether to ask the user anything,
	 * so there is nothing to wait for or to fail over.
	 */
	void diffEnabled() {
		getExecutor(capabilityOf(DIFF_ENABLED)).ifPresent(lss ->
			lss.computeAll(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(DIFF_ENABLED, List.of()))));
	}

	/**
	 * Executes a command that takes the name of a project, and answers one result.
	 * 
	 * @return the result, completing exceptionally if the language server is not there or fails
	 */
	private <T> CompletableFuture<T> executeForProject(String command, String projectName, Class<T> resultType) {
		Optional<LanguageServerProjectExecutor> executor = getExecutor(capabilityOf(command));
		if (executor.isEmpty()) {
			return CompletableFuture.failedFuture(new IllegalStateException("The Spring Boot language server is not available"));
		}

		List<CompletableFuture<@Nullable Object>> results = executor.get()
				.computeAll(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(command, List.of(projectName))));
		if (results.isEmpty()) {
			return CompletableFuture.failedFuture(new IllegalStateException("The Spring Boot language server is not available"));
		}

		final Gson gson = new Gson();
		return results.get(0).thenApply(o -> {
			if (o instanceof JsonElement json) {
				return gson.fromJson(json, resultType);
			} else if (o != null) {
				return gson.fromJson(gson.toJsonTree(o), resultType);
			}
			return null;
		});
	}

	private static Predicate<ServerCapabilities> capabilityOf(String command) {
		return capabilities -> capabilities.getExecuteCommandProvider() != null
				&& capabilities.getExecuteCommandProvider().getCommands().contains(command);
	}

	private Optional<LanguageServerProjectExecutor> getExecutor(Predicate<ServerCapabilities> capabilityFilter) {
		List<IJavaProject> allSpringProjects = BootProjectTracker.streamSpringProjects().toList();
		if (!allSpringProjects.isEmpty()) {
			return Optional.of(LanguageServers.forProject(allSpringProjects.get(0).getProject()).withFilter(capabilityFilter).excludeInactive());
		}
		return Optional.empty();
	}
	

}
