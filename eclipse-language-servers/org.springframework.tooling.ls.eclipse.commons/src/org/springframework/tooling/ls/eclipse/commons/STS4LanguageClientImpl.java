/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.ISourceViewerExtension5;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageClientImpl;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.springframework.ide.vscode.commons.protocol.CursorMovement;
import org.springframework.ide.vscode.commons.protocol.HighlightParams;
import org.springframework.ide.vscode.commons.protocol.LiveProcessLoggersSummary;
import org.springframework.ide.vscode.commons.protocol.LiveProcessSummary;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;
import org.springframework.ide.vscode.commons.protocol.java.ClasspathListenerParams;
import org.springframework.ide.vscode.commons.protocol.java.Gav;
import org.springframework.ide.vscode.commons.protocol.java.JavaCodeCompleteData;
import org.springframework.ide.vscode.commons.protocol.java.JavaCodeCompleteParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaDataParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaSearchParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaTypeHierarchyParams;
import org.springframework.ide.vscode.commons.protocol.java.ProjectGavParams;
import org.springframework.ide.vscode.commons.protocol.java.TypeData;
import org.springframework.ide.vscode.commons.protocol.java.TypeDescriptorData;
import org.springframework.ide.vscode.commons.protocol.spring.IndexUpdatedParams;
import org.springframework.tooling.jdt.ls.commons.Logger;
import org.springframework.tooling.jdt.ls.commons.classpath.ReusableClasspathListenerHandler;
import org.springframework.tooling.jdt.ls.commons.java.BuildInfo;
import org.springframework.tooling.jdt.ls.commons.java.JavaCodeCompletion;
import org.springframework.tooling.jdt.ls.commons.java.JavaData;
import org.springframework.tooling.jdt.ls.commons.java.JavaFluxSearch;
import org.springframework.tooling.jdt.ls.commons.java.TypeHierarchy;
import org.springframework.tooling.jdt.ls.commons.javadoc.JavadocUtils;
import org.springframework.tooling.jdt.ls.commons.resources.ResourceUtils;
import org.springframework.tooling.ls.eclipse.commons.javadoc.JavaDoc2MarkdownConverter;
import org.springframework.tooling.ls.eclipse.commons.preferences.PreferenceConstants;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@SuppressWarnings("restriction")
public class STS4LanguageClientImpl extends LanguageClientImpl implements STS4LanguageClient {

	private final Executor executor;

	public static final ReusableClasspathListenerHandler CLASSPATH_SERVICE = new ReusableClasspathListenerHandler (
			Logger.forEclipsePlugin(LanguageServerCommonsActivator::getInstance),
			new LSP4ECommandExecutor(),
			() -> new ProjectSorter()
	);

	private static final long LABEL_FLAGS=
			JavaElementLabels.ALL_FULLY_QUALIFIED
			| JavaElementLabels.M_PRE_RETURNTYPE
			| JavaElementLabels.M_PARAMETER_ANNOTATIONS
			| JavaElementLabels.M_PARAMETER_TYPES
			| JavaElementLabels.M_PARAMETER_NAMES
			| JavaElementLabels.M_EXCEPTIONS
			| JavaElementLabels.F_PRE_TYPE_SIGNATURE
			| JavaElementLabels.M_PRE_TYPE_PARAMETERS
			| JavaElementLabels.T_TYPE_PARAMETERS
			| JavaElementLabels.USE_RESOLVED;

	private static final long LOCAL_VARIABLE_FLAGS= LABEL_FLAGS & ~JavaElementLabels.F_FULLY_QUALIFIED | JavaElementLabels.F_POST_QUALIFIED;

	private static final long COMMON_SIGNATURE_FLAGS = LABEL_FLAGS & ~JavaElementLabels.ALL_FULLY_QUALIFIED
			| JavaElementLabels.T_FULLY_QUALIFIED | JavaElementLabels.M_FULLY_QUALIFIED;


	private static String label(IJavaElement element) {
		try {
			if (element instanceof ILocalVariable) {
				return JavaElementLabels.getElementLabel(element,LOCAL_VARIABLE_FLAGS);
			} else {
				return JavaElementLabels.getElementLabel(element,COMMON_SIGNATURE_FLAGS);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private final JavaData javaData = new JavaData(STS4LanguageClientImpl::label , Logger.forEclipsePlugin(LanguageServerCommonsActivator::getInstance));
	private final JavaFluxSearch javaFluxSearch = new JavaFluxSearch(Logger.forEclipsePlugin(LanguageServerCommonsActivator::getInstance), javaData);
	private final TypeHierarchy typeHierarchy = new TypeHierarchy(Logger.forEclipsePlugin(LanguageServerCommonsActivator::getInstance), javaData);
	private final JavaCodeCompletion codeComplete = new JavaCodeCompletion();
	private final JavaElementLabelProvider javaLabelProvider = new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_POST_QUALIFIED);

	private static final String ANNOTION_TYPE_ID = "org.springframework.tooling.bootinfo";

//	private static final String ALT_ANNOTATION_DRAWING_STRATEGY_ID = "boot.hint.strategy";
//	private static final String ALT_ANNOTATION_TYPE_ID = "org.springframework.tooling.bootinfoCodeLens";

	/**
	 * Latest highlight request params. It is sufficient to only remember the last request per uri, because
	 * each new request is expected to replace the previous highlights.
	 */
	static final Map<String, HighlightParams> currentHighlights = new ConcurrentHashMap<>();

	/**
	 * Current markers... indexed per document uri, needed sp we to be removed upon next update.
	 */
	private static Map<String, Annotation[]> currentAnnotations = new ConcurrentHashMap<>();

//	private static final IDrawingStrategy BOOT_RANGE_HIGHLIGHT_DRAWING_STRATEGY = new IDrawingStrategy() {
//
//		@Override
//		public void draw(Annotation annotation, GC gc, StyledText textWidget, int offset, int length, Color color) {
//
//			if (gc == null) {
//				textWidget.redrawRange(offset, length, true);
//			} else {
//				int oldAlpha = gc.getAlpha();
//				Font oldFont = gc.getFont();
//
//				Point left= textWidget.getLocationAtOffset(offset);
//				Point right = textWidget.getLocationAtOffset(offset + length);
//				gc.setFont(textWidget.getFont());
//				int fontHeight = gc.getFontMetrics().getHeight();
//				Rectangle r = new Rectangle(left.x, left.y + textWidget.getLineHeight(offset) - fontHeight, right.x - left.x, fontHeight);
//				gc.setAlpha(0x40);
//				gc.setBackground(color);
//				gc.fillRectangle(r);
//
//				gc.setAlpha(oldAlpha);
//				gc.setFont(oldFont);
//			}
//		}
//
//	};

	static class UpdateHighlights extends UIJob {

		private String target;
		private boolean updateCodeMinings;

		UpdateHighlights(String target, boolean updateCodeMinings) {
			super("Update highlights");
			this.target = target;
			this.updateCodeMinings = updateCodeMinings;
			setSystem(true);
			schedule();
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			Utils.getActiveEditors().forEach(this::updateSourceViewer);
			return Status.OK_STATUS;
		}

		protected void updateSourceViewer(IEditorPart editor) {
			ITextViewer viewer = editor.getAdapter(ITextViewer.class);
			if (viewer instanceof ISourceViewer) {
				ISourceViewer sourceViewer = (ISourceViewer) viewer;
				IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
				if (annotationModel != null) {
					IDocument doc = sourceViewer.getDocument();
					if (doc != null && sourceViewer != null) {
						if (target != null) {
							HighlightParams highlightParams = currentHighlights.get(target);
							if (Utils.isProperDocumentIdFor(doc, highlightParams.getDoc())) {
								updateHighlightAnnotations(editor, sourceViewer, annotationModel, target, updateCodeMinings);
							}
						} else {
							URI uri = LSPEclipseUtils.toUri(doc);
							if (uri != null) {
								updateHighlightAnnotations(editor, sourceViewer, annotationModel, uri.toASCIIString(), updateCodeMinings);
							}
						}
					}
				}
			}
		}

	};

	private static void updateHighlightAnnotations(IEditorPart editor, ISourceViewer sourceViewer,
			IAnnotationModel annotationModel, String docUri, boolean updateCodeMinings) {
//		boolean codeLensHighlightOn = isCodeLensHighlightOn();
		if (annotationModel instanceof IAnnotationModelExtension) {
//			if (codeLensHighlightOn) {
//				addBootRangeHighlightSupport(editor, sourceViewer);
//			}
			updateAnnotations(docUri, sourceViewer, (IAnnotationModelExtension) annotationModel);
		}
		if (updateCodeMinings && sourceViewer instanceof ISourceViewerExtension5) {
			if (sourceViewer instanceof JavaSourceViewer) {
				// JavaSourceViewer#updateCodeMinings() is overridden and doesn't do anything
				try {
					Method method = JavaSourceViewer.class.getDeclaredMethod("doUpdateCodeMinings");
					method.setAccessible(true);
					method.invoke(sourceViewer);
				} catch (InvocationTargetException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException e) {
					// don't log the exception
					((ISourceViewerExtension5) sourceViewer).updateCodeMinings();
				}
			} else {
				((ISourceViewerExtension5) sourceViewer).updateCodeMinings();
			}

			/*
			 * Hack for Big Sur CodeMining layout issues when toggle CodeMinings on/off
			 * After 100ms emulate resize editor visible region
			 */
			final StyledText textWidget = sourceViewer.getTextWidget();
			if (textWidget != null && !textWidget.isDisposed()) {
				textWidget.getDisplay().timerExec(100, () -> {
					if (!textWidget.isDisposed()) {
						IRegion visibleRegion = sourceViewer.getVisibleRegion();
						int offset = visibleRegion.getOffset();
						int length = visibleRegion.getLength();
						if (length > 1) {
							sourceViewer.setVisibleRegion(offset, length - 1);
							sourceViewer.setVisibleRegion(offset, length);
						} else {
							sourceViewer.resetVisibleRegion();
						}
					}
				});
			}
		}
	}

//	private static void addBootRangeHighlightSupport(IEditorPart editor, ISourceViewer sourceViewer) {
//		if (editor instanceof AbstractDecoratedTextEditor) {
//			addBootRangeHighlightSupport((AbstractDecoratedTextEditor)editor, sourceViewer);
//		}
////		else if () {
////			// TODO: XML multi page editor handling for highlight support of XML source editor
////		}
//	}
//
//	@SuppressWarnings("unchecked")
//	private static void addBootRangeHighlightSupport(AbstractDecoratedTextEditor editor, ISourceViewer sourceViewer) {
//		try {
//			Field f = AbstractDecoratedTextEditor.class.getDeclaredField("fSourceViewerDecorationSupport");
//			f.setAccessible(true);
//			SourceViewerDecorationSupport support = (SourceViewerDecorationSupport) f.get(editor);
//			f = SourceViewerDecorationSupport.class.getDeclaredField("fAnnotationPainter");
//			f.setAccessible(true);
//			AnnotationPainter painter = (AnnotationPainter) f.get(support);
//
//			f = AnnotationPainter.class.getDeclaredField("fAnnotationType2Color");
//			f.setAccessible(true);
//			Color highlightColor = LanguageServerCommonsActivator.getInstance().getBootHighlightRangeColor();
//			if (highlightColor!=null && ((Map<Object, Color>)f.get(painter)).get(ALT_ANNOTATION_TYPE_ID) != highlightColor) {
//				painter.setAnnotationTypeColor(ALT_ANNOTATION_TYPE_ID, highlightColor);
//				painter.addDrawingStrategy(ALT_ANNOTATION_DRAWING_STRATEGY_ID, BOOT_RANGE_HIGHLIGHT_DRAWING_STRATEGY);
//				painter.addAnnotationType(ALT_ANNOTATION_TYPE_ID, ALT_ANNOTATION_DRAWING_STRATEGY_ID);
//			}
//
//		} catch (Exception e) {
//			LanguageServerCommonsActivator.logError(e,
//					"Failed to contribute alternative range highlight annotation. Switch off highlight CodeLense under STS Language Server preferences!");
//		}
//	}

	private static boolean isCodeLensHighlightOn() {
		IPreferenceStore store = LanguageServerCommonsActivator.getInstance().getPreferenceStore();
		return store.getBoolean(PreferenceConstants.HIGHLIGHT_CODELENS_PREFS);
	}

	static synchronized void updateAnnotations(String target, ISourceViewer sourceViewer,
			IAnnotationModelExtension annotationModel) {
		Annotation[] toRemove = currentAnnotations.get(target);
		if (toRemove == null) {
			toRemove = new Annotation[0];
		}
		HighlightParams highlightParams = currentHighlights.get(target);
		List<CodeLens> highlights = highlightParams == null ? null : highlightParams.getCodeLenses();
		String annotationType = /*isCodeLensHighlightOn() ? ALT_ANNOTATION_TYPE_ID :*/ ANNOTION_TYPE_ID;
		Map<Annotation, Position> newAnnotations = createAnnotations(sourceViewer.getDocument(), highlights, annotationType);
		annotationModel.replaceAnnotations(toRemove, newAnnotations);
		currentAnnotations.put(target, newAnnotations.keySet().toArray(new Annotation[newAnnotations.size()]));
	}

	private static Map<Annotation, Position> createAnnotations(IDocument doc, List<CodeLens> highlights, String annotationType) {
		ImmutableMap.Builder<Annotation, Position> annotations = ImmutableMap.builder();
		if (highlights==null) {
			highlights = ImmutableList.of();
		}
		highlights.stream().map(CodeLens::getRange).distinct().forEach(rng -> {
			try {
				int start = LSPEclipseUtils.toOffset(rng.getStart(), doc);
				int end = LSPEclipseUtils.toOffset(rng.getEnd(), doc);
				Position e_rng = new Position(start, Math.max(0, end-start));
				annotations.put(new Annotation(annotationType, false, null), e_rng);
			} catch (BadLocationException e) {
				//ignore invalid highlights
			}
		});
		return annotations.build();
	}

	@Override
	public synchronized void highlight(HighlightParams highlights) {
		String target = highlights.getDoc().getUri();
		if (target!=null) {
			HighlightParams oldHighligts = currentHighlights.get(target);
			List<CodeLens> oldCodelenses = oldHighligts==null ? ImmutableList.of() : oldHighligts.getCodeLenses();
			if (!oldCodelenses.equals(highlights.getCodeLenses())) {
				currentHighlights.put(target, highlights);
				new UpdateHighlights(target, isCodeLensHighlightOn());
			}
		}
	}



//	@Override
//	public void notifyProgress(org.eclipse.lsp4j.ProgressParams params) {
//		if (params.getValue().isLeft()) {
//			WorkDoneProgressNotification progressNotification = params.getValue().getLeft();
//			switch (progressNotification.getKind()) {
//			case begin:
//				WorkDoneProgressBegin begin = (WorkDoneProgressBegin) progressNotification;
//				showStatusMessage(begin.getMessage());
//				break;
//			case report:
//				WorkDoneProgressReport report = (WorkDoneProgressReport) progressNotification;
//				showStatusMessage(report.getMessage());
//				break;
//			case end:
//				showStatusMessage("");
//				break;
//			default:
//				break;
//			}
//		}
//	}
//
//	private void showStatusMessage(final String status) {
//		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
//			@Override
//			public void run() {
//				IStatusLineManager statusLineManager = getStatusLineManager();
//				if (statusLineManager != null) {
//					// PT 169821181 - Show progress in progress status line
//					IProgressMonitor progressMonitor = statusLineManager.getProgressMonitor();
//
//					if (progressMonitor != null) {
//						SubMonitor sub = SubMonitor.convert(progressMonitor);
//						// language server will send empty status when progress sequence terminates so
//						// this takes care of clearing the status line from the previous displayed message
//						sub.subTask(status);
//					}
//				}
//			}
//		});
//	}
//
//	private IStatusLineManager getStatusLineManager() {
//		try {
//			return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getEditorSite().getActionBars().getStatusLineManager();
//		}
//		catch (NullPointerException e) {
//			return null;
//		}
//	}



	public STS4LanguageClientImpl() {
		this.executor = new ThreadPoolExecutor(1, 5, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		CLASSPATH_SERVICE.addNotificationsSentCallback(projectNames -> {
			List<IProject> projects = projectNames.stream().map(projectName -> ResourcesPlugin.getWorkspace().getRoot().getProject(projectName)).filter(Objects::nonNull).collect(Collectors.toList());
			for (IWorkbenchWindow ww : PlatformUI.getWorkbench().getWorkbenchWindows()) {
				for (IWorkbenchPage page : ww.getPages()) {
					for (IEditorReference editorRef : page.getEditorReferences()) {
						try {
							IEditorPart editor = editorRef.getEditor(false);
							if (editor != null) {
								if (editor.getEditorInput() instanceof IFileEditorInput) {
									IFile file = ((IFileEditorInput)editor.getEditorInput()).getFile();
									if (file != null && projects.contains(file.getProject())) {
										IJavaProject javaProject = JavaCore.create(file.getProject());
										if (javaProject != null) {
											for (IClasspathEntry entry : javaProject.getRawClasspath()) {
												if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
													if (entry.getPath().isPrefixOf(file.getFullPath())) {
														ITextViewer viewer = editor.getAdapter(ITextViewer.class);
														if (viewer instanceof ISourceViewerExtension5) {
															((ISourceViewerExtension5)viewer).updateCodeMinings();
															break;
														}
													}
												}
											}
										}
									}
								}
							}
						} catch (Exception e) {
							// ignore, e.g. MavenPomEditor can throw NPE on getAdapter(...) call. Just log it and continue
							LanguageServerCommonsActivator.logError(e, "Failed to refresh Code Minings for editor.");
						}
					}
				}
			}

		});
	}

	@Override
	public CompletableFuture<Object> addClasspathListener(ClasspathListenerParams params) {
		return CompletableFuture.completedFuture(CLASSPATH_SERVICE.addClasspathListener(params.getCallbackCommandId(), params.isBatched()));
	}

	@Override
	public CompletableFuture<Object> removeClasspathListener(ClasspathListenerParams params) {
		return CompletableFuture.completedFuture(CLASSPATH_SERVICE.removeClasspathListener(params.getCallbackCommandId()));
	}

	@Override
	public CompletableFuture<MarkupContent> javadoc(JavaDataParams params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				MarkupContent mc = new MarkupContent();
				mc.setKind(MarkupKind.MARKDOWN);
				String javadoc = JavadocUtils.javadoc(JavaDoc2MarkdownConverter::getMarkdownContentReader,
						URI.create(params.getProjectUri()), params.getBindingKey(), JavaDataParams.isLookInOtherProjects(params));
				if (javadoc != null) {
					mc.setValue(javadoc);
				}
				return mc;
			} catch (Exception e) {
				LanguageServerCommonsActivator.logError(e, "Failed getting javadoc for " + params.toString());
			}
			return null;
		});
	}

	@Override
	public CompletableFuture<Object> moveCursor(CursorMovement cursorMovement) {
		Utils.getActiveEditors().forEach(_editor -> {
			try {
				if (_editor instanceof AbstractTextEditor) {
					AbstractTextEditor editor = (AbstractTextEditor) _editor;
					IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
					if (doc!=null) {
						URI uri = LSPEclipseUtils.toUri(doc);
						if (cursorMovement.getUri().equals(uri.toASCIIString())) {
							org.eclipse.lsp4j.Position pos = cursorMovement.getPosition();
							int offset = LSPEclipseUtils.toOffset(pos, doc);
							Display.getDefault().asyncExec(() -> {
								editor.getSelectionProvider().setSelection(new TextSelection(offset, 0));
							});
						}
					}
				}
			} catch (Exception e) {
				LanguageServerCommonsActivator.logError(e, "sts/moveCursor failed");
			}
		});
		return CompletableFuture.completedFuture("ok");
	}

	@Override
	public CompletableFuture<TypeData> javaType(JavaDataParams params) {
		return CompletableFuture.supplyAsync(() -> javaData.typeData(params.getProjectUri(), params.getBindingKey(), JavaDataParams.isLookInOtherProjects(params)));
	}

	@Override
	public CompletableFuture<String> javadocHoverLink(JavaDataParams params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				URI projectUri = params.getProjectUri() == null ? null : URI.create(params.getProjectUri());
				IJavaElement element = JavaData.findElement(projectUri, params.getBindingKey(), JavaDataParams.isLookInOtherProjects(params));
				if (element != null) {
					return JavaElementLinks.createURI(JavaElementLinks.OPEN_LINK_SCHEME, element);
				}
			} catch (Exception e) {
				LanguageServerCommonsActivator.logError(e, "Failed to find java element for key " + params.getBindingKey());
			}
			return null;
		});
	}

	@Override
	public CompletableFuture<Location> javaLocation(JavaDataParams params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				URI projectUri = params.getProjectUri() == null ? null : URI.create(params.getProjectUri());
				IJavaElement element = JavaData.findElement(projectUri, params.getBindingKey(), JavaDataParams.isLookInOtherProjects(params));
				if (element != null ) {
					IJavaProject project = element.getJavaProject() == null ? ResourceUtils.getJavaProject(projectUri) : element.getJavaProject();
					if (project != null) {
						Location location = new Location();
						String label = javaLabelProvider.getText(element);
						location.setUri(Utils.eclipseIntroUri(project.getElementName(), params.getBindingKey(), label).toASCIIString());
						// Set the range because LocationLink needs it to be non-null. The target range
						// would highlighted by the eclipse intro URL navigation anyway
						location.setRange(new Range());
						return location;
					}
				}
			} catch (Exception e) {
				LanguageServerCommonsActivator.logError(e, "Failed to find java element for key " + params.getBindingKey());
			}
			return null;
		});
	}

	@Override
	public CompletableFuture<List<TypeDescriptorData>> javaSearchTypes(JavaSearchParams params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return javaFluxSearch.fuzzySearchTypes(params);
			} catch (Exception e) {
				LanguageServerCommonsActivator.logError(e, "Failed to search type with term '" + params.getTerm()
						+ "' in project " + params.getProjectUri());
				return Collections.emptyList();
			}
		});
	}

	@Override
	public CompletableFuture<List<String>> javaSearchPackages(JavaSearchParams params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return javaFluxSearch.fuzzySearchPackages(params);
			} catch (Exception e) {
				LanguageServerCommonsActivator.logError(e, "Failed to search package with term '" + params.getTerm()
						+ "' in project " + params.getProjectUri());
				return Collections.emptyList();
			}
		});
	}

	@Override
	public CompletableFuture<List<Either<TypeDescriptorData, TypeData>>> javaSubTypes(JavaTypeHierarchyParams params) {
		return CompletableFuture.supplyAsync(() -> typeHierarchy.subTypes(params).collect(Collectors.toList()));
	}

	@Override
	public CompletableFuture<List<Either<TypeDescriptorData, TypeData>>> javaSuperTypes(JavaTypeHierarchyParams params) {
		return CompletableFuture.supplyAsync(() -> typeHierarchy.superTypes(params).collect(Collectors.toList()));
	}

	@Override
	public CompletableFuture<List<JavaCodeCompleteData>> javaCodeComplete(JavaCodeCompleteParams params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return codeComplete.codeComplete(params.getProjectUri(), params.getPrefix(), params.isIncludeTypes(), params.isIncludePackages());
			} catch (Exception e) {
				LanguageServerCommonsActivator.logError(e, "Failed to do code complete with prefix '" + params.getPrefix()
						+ "' in project " + params.getProjectUri());
				return Collections.emptyList();
			}
		});
	}


	@Override
	public void liveProcessConnected(LiveProcessSummary processKey) {
	}

	@Override
	public void liveProcessDisconnected(LiveProcessSummary processKey) {
	}

	@Override
	public void liveProcessDataUpdated(LiveProcessSummary processKey) {
	}

	@Override
	public void liveProcessGcPausesMetricsDataUpdated(LiveProcessSummary arg0) {
	}

	@Override
	public void liveProcessMemoryMetricsDataUpdated(LiveProcessSummary arg0) {
	}

	@Override
	public void indexUpdated(IndexUpdatedParams indexUpdateDetails) {
	}

	@Override
	public void liveProcessLogLevelUpdated(LiveProcessLoggersSummary summary) {
		// Nothing to do for now
	}

	@Override
	public CompletableFuture<List<Gav>> projectGAV(ProjectGavParams params) {
		return BuildInfo.projectGAV(params, executor, Logger.forEclipsePlugin(LanguageServerCommonsActivator::getInstance));
	}

}
