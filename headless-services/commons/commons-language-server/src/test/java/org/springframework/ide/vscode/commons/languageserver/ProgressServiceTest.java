/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;

/**
 * Tests for ProgressService, ProgressClient, and ProgressTask classes.
 */
class ProgressServiceTest {

	private ProgressClient mockClient;
	private ProgressService service;

	@BeforeEach
	void setup() {
		mockClient = mock(ProgressClient.class);
		service = new ProgressService(mockClient);
	}

	// ========== ProgressService Tests ==========

	@Test
	void testServiceConstructorRejectsNull() {
		assertThatThrownBy(() -> new ProgressService(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ProgressClient cannot be null");
	}

	@Test
	void testFactoryMethodRejectsNull() {
		assertThatThrownBy(() -> ProgressService.create(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("STS4LanguageClient cannot be null");
	}

	@Test
	void testFactoryMethodCreatesService() {
		STS4LanguageClient mockLspClient = mock(STS4LanguageClient.class);
		when(mockLspClient.createProgress(any())).thenReturn(CompletableFuture.completedFuture(null));
		
		ProgressService service = ProgressService.create(mockLspClient);
		
		assertThat(service).isNotNull();
		
		// Verify it works by creating a task
		IndefiniteProgressTask task = service.createIndefiniteProgressTask("test", "Test", "Starting");
		assertThat(task).isNotNull();
		
		// Verify LSP client was called
		verify(mockLspClient).createProgress(any(WorkDoneProgressCreateParams.class));
	}

	// ========== IndefiniteProgressTask Tests ==========

	@Test
	void testIndefiniteProgressTaskCreation() {
		IndefiniteProgressTask task = service.createIndefiniteProgressTask(
			"indexing",
			"Indexing Project",
			"Starting..."
		);

		assertThat(task).isNotNull();
		
		// Verify begin was called with correct parameters
		ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<WorkDoneProgressBegin> reportCaptor = ArgumentCaptor.forClass(WorkDoneProgressBegin.class);
		
		verify(mockClient).begin(taskIdCaptor.capture(), reportCaptor.capture());
		
		assertThat(taskIdCaptor.getValue()).contains("indexing");
		WorkDoneProgressBegin report = reportCaptor.getValue();
		assertThat(report.getTitle()).isEqualTo("Indexing Project");
		assertThat(report.getMessage()).isEqualTo("Starting...");
		assertThat(report.getCancellable()).isFalse();
	}

	@Test
	void testIndefiniteProgressTaskUpdate() {
		IndefiniteProgressTask task = service.createIndefiniteProgressTask("test", "Test", "Start");
		reset(mockClient);

		task.progressEvent("Processing files...");

		ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<WorkDoneProgressReport> reportCaptor = ArgumentCaptor.forClass(WorkDoneProgressReport.class);
		
		verify(mockClient).report(taskIdCaptor.capture(), reportCaptor.capture());
		
		assertThat(taskIdCaptor.getValue()).contains("test");
		assertThat(reportCaptor.getValue().getMessage()).isEqualTo("Processing files...");
	}

	@Test
	void testIndefiniteProgressTaskMultipleUpdates() {
		IndefiniteProgressTask task = service.createIndefiniteProgressTask("test", "Test", "Start");
		reset(mockClient);

		task.progressEvent("Step 1");
		task.progressEvent("Step 2");
		task.progressEvent("Step 3");

		verify(mockClient, times(3)).report(anyString(), any(WorkDoneProgressReport.class));
	}

	@Test
	void testIndefiniteProgressTaskCompletion() {
		IndefiniteProgressTask task = service.createIndefiniteProgressTask("test", "Test", "Start");
		reset(mockClient);

		task.done();

		ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockClient).end(taskIdCaptor.capture(), any(WorkDoneProgressEnd.class));
		assertThat(taskIdCaptor.getValue()).contains("test");
	}

	@Test
	void testIndefiniteProgressTaskWithNullMessage() {
		service.createIndefiniteProgressTask("test", "Test", null);

		ArgumentCaptor<WorkDoneProgressBegin> reportCaptor = ArgumentCaptor.forClass(WorkDoneProgressBegin.class);
		verify(mockClient).begin(anyString(), reportCaptor.capture());
		assertThat(reportCaptor.getValue().getMessage()).isNull();
	}

	@Test
	void testIndefiniteProgressTaskWithEmptyMessage() {
		service.createIndefiniteProgressTask("test", "Test", "");

		ArgumentCaptor<WorkDoneProgressBegin> reportCaptor = ArgumentCaptor.forClass(WorkDoneProgressBegin.class);
		verify(mockClient).begin(anyString(), reportCaptor.capture());
		assertThat(reportCaptor.getValue().getMessage()).isNull();
	}

	// ========== PercentageProgressTask Tests ==========

	@Test
	void testPercentageProgressTaskCreation() {
		PercentageProgressTask task = service.createPercentageProgressTask(
			"building",
			100,
			"Building Project"
		);

		assertThat(task).isNotNull();
		assertThat(task.getTotal()).isEqualTo(100);
		assertThat(task.getCurrent()).isEqualTo(0);

		// Verify begin was called with percentage 0
		ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<WorkDoneProgressBegin> reportCaptor = ArgumentCaptor.forClass(WorkDoneProgressBegin.class);
		
		verify(mockClient).begin(taskIdCaptor.capture(), reportCaptor.capture());
		
		assertThat(taskIdCaptor.getValue()).contains("building");
		WorkDoneProgressBegin report = reportCaptor.getValue();
		assertThat(report.getTitle()).isEqualTo("Building Project");
		assertThat(report.getPercentage()).isEqualTo(0);
		assertThat(report.getCancellable()).isFalse();
	}

	@Test
	void testPercentageProgressTaskIncrement() {
		PercentageProgressTask task = service.createPercentageProgressTask("test", 10, "Test");
		reset(mockClient);

		task.increment();

		assertThat(task.getCurrent()).isEqualTo(1);
		
		ArgumentCaptor<WorkDoneProgressReport> reportCaptor = ArgumentCaptor.forClass(WorkDoneProgressReport.class);
		verify(mockClient).report(anyString(), reportCaptor.capture());
		assertThat(reportCaptor.getValue().getPercentage()).isEqualTo(10); // 1/10 * 100
	}

	@Test
	void testPercentageProgressTaskMultipleIncrements() {
		PercentageProgressTask task = service.createPercentageProgressTask("test", 4, "Test");
		reset(mockClient);

		task.increment(); // 25%
		task.increment(); // 50%
		task.increment(); // 75%
		task.increment(); // 100%

		assertThat(task.getCurrent()).isEqualTo(4);
		verify(mockClient, times(4)).report(anyString(), any(WorkDoneProgressReport.class));
	}

	@Test
	void testPercentageProgressTaskSetCurrent() {
		PercentageProgressTask task = service.createPercentageProgressTask("test", 100, "Test");
		reset(mockClient);

		task.setCurrent(50);

		assertThat(task.getCurrent()).isEqualTo(50);
		
		ArgumentCaptor<WorkDoneProgressReport> reportCaptor = ArgumentCaptor.forClass(WorkDoneProgressReport.class);
		verify(mockClient).report(anyString(), reportCaptor.capture());
		assertThat(reportCaptor.getValue().getPercentage()).isEqualTo(50);
	}

	@Test
	void testPercentageProgressTaskSetCurrentInvalid() {
		PercentageProgressTask task = service.createPercentageProgressTask("test", 100, "Test");

		assertThatThrownBy(() -> task.setCurrent(101))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testPercentageProgressTaskIncrementBeyondTotal() {
		PercentageProgressTask task = service.createPercentageProgressTask("test", 2, "Test");
		
		task.increment();
		task.increment();

		assertThatThrownBy(() -> task.increment())
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void testPercentageProgressTaskOnlyReportsOnPercentageChange() {
		PercentageProgressTask task = service.createPercentageProgressTask("test", 1000, "Test");
		reset(mockClient);

		// Increment 10 times (still 0%)
		for (int i = 0; i < 10; i++) {
			task.increment();
		}

		// Should only report once when reaching 1%
		assertThat(task.getCurrent()).isEqualTo(10);
		verify(mockClient, times(1)).report(anyString(), any(WorkDoneProgressReport.class));
	}

	@Test
	void testPercentageProgressTaskCompletion() {
		PercentageProgressTask task = service.createPercentageProgressTask("test", 10, "Test");
		reset(mockClient);

		task.done();

		verify(mockClient).end(anyString(), any(WorkDoneProgressEnd.class));
	}

	// ========== Task ID Uniqueness Tests ==========

	@Test
	void testTaskIdsAreUnique() {
		service.createIndefiniteProgressTask("test", "Test 1", "");
		service.createIndefiniteProgressTask("test", "Test 2", "");

		ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockClient, times(2)).begin(taskIdCaptor.capture(), any(WorkDoneProgressBegin.class));
		
		assertThat(taskIdCaptor.getAllValues()).hasSize(2);
		String taskId1 = taskIdCaptor.getAllValues().get(0);
		String taskId2 = taskIdCaptor.getAllValues().get(1);

		assertThat(taskId1).isNotEqualTo(taskId2);
		assertThat(taskId1).startsWith("test-");
		assertThat(taskId2).startsWith("test-");
	}

	// ========== Integration Tests ==========

	@Test
	void testFullIndefiniteProgressLifecycle() {
		IndefiniteProgressTask task = service.createIndefiniteProgressTask(
			"indexing",
			"Indexing Project",
			"Starting..."
		);

		task.progressEvent("Processing file 1");
		task.progressEvent("Processing file 2");
		task.done();

		// Verify sequence
		verify(mockClient, times(1)).begin(anyString(), any(WorkDoneProgressBegin.class));
		verify(mockClient, times(2)).report(anyString(), any(WorkDoneProgressReport.class));
		verify(mockClient, times(1)).end(anyString(), any(WorkDoneProgressEnd.class));
	}

	@Test
	void testFullPercentageProgressLifecycle() {
		PercentageProgressTask task = service.createPercentageProgressTask(
			"building",
			5,
			"Building"
		);

		for (int i = 0; i < 5; i++) {
			task.increment();
		}
		task.done();

		// Verify sequence
		verify(mockClient, times(1)).begin(anyString(), any(WorkDoneProgressBegin.class));
		verify(mockClient, times(5)).report(anyString(), any(WorkDoneProgressReport.class));
		verify(mockClient, times(1)).end(anyString(), any(WorkDoneProgressEnd.class));
	}

	@Test
	void testMultipleConcurrentTasks() {
		IndefiniteProgressTask task1 = service.createIndefiniteProgressTask("task1", "Task 1", "");
		IndefiniteProgressTask task2 = service.createIndefiniteProgressTask("task2", "Task 2", "");

		task1.progressEvent("T1 Update 1");
		task2.progressEvent("T2 Update 1");
		task1.progressEvent("T1 Update 2");
		task2.done();
		task1.done();

		verify(mockClient, times(2)).begin(anyString(), any(WorkDoneProgressBegin.class));
		verify(mockClient, times(3)).report(anyString(), any(WorkDoneProgressReport.class));
		verify(mockClient, times(2)).end(anyString(), any(WorkDoneProgressEnd.class));
	}

	// ========== ProgressClient Direct Tests ==========

	@Test
	void testProgressClientBeginIsCalled() {
		service.createIndefiniteProgressTask("test", "Test", "Message");
		
		verify(mockClient).begin(anyString(), any(WorkDoneProgressBegin.class));
		verifyNoMoreInteractions(mockClient);
	}

	@Test
	void testProgressClientReportIsCalled() {
		IndefiniteProgressTask task = service.createIndefiniteProgressTask("test", "Test", "");
		reset(mockClient);
		
		task.progressEvent("Update");
		
		verify(mockClient).report(anyString(), any(WorkDoneProgressReport.class));
		verifyNoMoreInteractions(mockClient);
	}

	@Test
	void testProgressClientEndIsCalled() {
		IndefiniteProgressTask task = service.createIndefiniteProgressTask("test", "Test", "");
		reset(mockClient);
		
		task.done();
		
		verify(mockClient).end(anyString(), any(WorkDoneProgressEnd.class));
		verifyNoMoreInteractions(mockClient);
	}
}

