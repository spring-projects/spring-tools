/**
 * Copied from Eclipse org.eclipse.compare.internal.ResourceCompareInput,
 *  and modified setSelection() method to support comparing non-IResource structures in Add Starters wizard
 *  , like a java.io.File. Original copyright below
 */

/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt McCutchen (hashproduct+eclipse@gmail.com) - Bug 35390 Three-way compare cannot select (mis-selects) )ancestor resource
 *     Aleksandra Wozniak (aleksandra.k.wozniak@gmail.com) - Bug 239959
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.starters.eclipse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.CompareViewerPane;
import org.eclipse.compare.CompareViewerSwitchingPane;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.Splitter;
import org.eclipse.compare.contentmergeviewer.ContentMergeViewer;
import org.eclipse.compare.contentmergeviewer.TextMergeViewer;
import org.eclipse.compare.internal.AbstractViewer;
import org.eclipse.compare.internal.BufferedResourceNode;
import org.eclipse.compare.internal.CompareContentViewerSwitchingPane;
import org.eclipse.compare.internal.CompareMessages;
import org.eclipse.compare.internal.CompareUIPlugin;
import org.eclipse.compare.internal.IMergeViewerTestAdapter;
import org.eclipse.compare.internal.NullViewer;
import org.eclipse.compare.internal.Utilities;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.PlatformUI;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.io.Files;

/**
 * A two-way or three-way compare for arbitrary IResources.
 */
@SuppressWarnings("restriction")
public class ResourceCompareInput extends CompareEditorInput {

	public static final String OPEN_DIFF_NODE_COMPARE_SETTING = "Open-Diff-Node-Setting";

	public interface ResourceDescriptor {
		String name();
		String tooltipPathLabel();
		Image image();
		String label();
		default void prepare(IProgressMonitor pm) throws CoreException {};
		IStructureComparator structure(Predicate<String> filter);
	}

	public static ResourceDescriptor fromWorkspaceResource(IResource r) {
		return new ResourceDescriptor() {

			@Override
			public String name() {
				return r.getName();
			}

			@Override
			public String tooltipPathLabel() {
				return r.getFullPath().makeRelative().toString();
			}

			@Override
			public String label() {
				// for a linked resource in a hidden project use its local file system location
				if (r.isLinked() && r.getProject().isHidden())
					return r.getLocation().toString();
				String n= r.getFullPath().toString();
				if (n.charAt(0) == IPath.SEPARATOR)
					return n.substring(1);
				return n;
			}

			@Override
			public Image image() {
				return CompareUIPlugin.getImage(r);
			}

			@Override
			public void prepare(IProgressMonitor pm) throws CoreException {
				r.refreshLocal(IResource.DEPTH_INFINITE, pm);
			}

			@Override
			public IStructureComparator structure(Predicate<String> filter) {
				if (r instanceof IContainer)
					return new FilteredBufferedResourceNode(r, filter);

				if (r instanceof IFile) {
					IStructureComparator rn= new FilteredBufferedResourceNode(r, filter);
					IFile file= (IFile) r;
					String type= normalizeCase(file.getFileExtension());
					if ("JAR".equals(type) || "ZIP".equals(type)) //$NON-NLS-2$ //$NON-NLS-1$
						return new ZipFileStructureCreator(filter).getStructure(rn);
					return rn;
				}
				return null;
			}

		};
	}

	public static ResourceDescriptor fromFile(File f) {
		return new ResourceDescriptor() {

			@Override
			public String name() {
				return f.getName();
			}

			@Override
			public String tooltipPathLabel() {
				return f.getAbsolutePath();
			}

			@Override
			public Image image() {
				return null;
			}

			@Override
			public String label() {
				return f.getAbsolutePath();
			}

			@Override
			public IStructureComparator structure(Predicate<String> filter) {
				if ("zip".equalsIgnoreCase(Files.getFileExtension(f.getName()))) {
					return new ZipFileStructureCreator(filter).getStructure(new IStreamContentAccessor() {

						@Override
						public InputStream getContents() throws CoreException {
							try {
								return new FileInputStream(f);
							} catch (FileNotFoundException e) {
								throw ExceptionUtil.unchecked(e);
							}
						}
					});
				}
				return null;
			}

		};
	}

	private static final boolean NORMALIZE_CASE= true;

	private boolean fThreeWay= false;
	private MyDiffNode fRoot;
	private IStructureComparator fAncestor;
	private IStructureComparator fLeft;
	private IStructureComparator fRight;
	private IResource fAncestorResource;
	private ResourceDescriptor fLeftResource;
	private ResourceDescriptor fRightResource;
	private CustomDiffTreeViewer fDiffViewer;
	private IAction fOpenAction;
	private IAction fCreateResourceAction;
	private IAction fAcceptChangesAction;

	final private Predicate<String> filter;

	enum DiffNodeState {
		NONE,
		PARTIAL,
		ALL
	}

	class MyDiffNode extends DiffNode {

		private ITypedElement fLastId;
		private String fLastName;

		/*
		 * Turns on/off state update based on diff node right side content.
		 */
		boolean determineStateFromContent = true;

		private DiffNodeState state = DiffNodeState.NONE;
		// Keep original "right" essentially to check if it was "null" initially
		private ITypedElement originalRight;

		public MyDiffNode(IDiffContainer parent, int description, ITypedElement ancestor, ITypedElement left, ITypedElement right) {
			super(parent, description, ancestor, left, right);
			// Set the DiffNode whenever "left" changes to be able to call MyDiffNode#fireChange() when "left" content changes
			if (left instanceof FilteredBufferedResourceNode) {
				((FilteredBufferedResourceNode) left).diffNode = this;
			}
			// Set the DiffNode whenever "right" changes to be able to call MyDiffNode#fireChange() "right" when content changes
			if (right instanceof FilteredBufferedResourceNode) {
				((FilteredBufferedResourceNode) right).diffNode = this;
			}
			this.originalRight = right;
		}
		@Override
		public void fireChange() {
			super.fireChange();
			setDirty(true);
			/*
			 * State update from content is off for "Accept All" and "Revert" when it is clear what state is going to become
			 */
			if (determineStateFromContent) {
				setState(determineStateFromContent());
				if (fDiffViewer != null)
					fDiffViewer.refresh(this);
			}
		}

		private DiffNodeState determineStateFromContent() {
			if (getRight() == null) {
				if (originalRight == null) {
					return DiffNodeState.NONE;
				} else if (getLeft() == null) {
					return DiffNodeState.ALL;
				} else {
					return DiffNodeState.PARTIAL;
				}
			} else if (getRight() instanceof FilteredBufferedResourceNode) {
				FilteredBufferedResourceNode right = (FilteredBufferedResourceNode) getRight();
				try {
					Shell shell = new Shell();
					shell.setVisible(false);
					// Content difference
					try {
						TextMergeViewer contentViewer = (TextMergeViewer) CompareUI.findContentViewer(new NullViewer(shell),
								this, shell, getCompareConfiguration());
						if (contentViewer != null) {
							contentViewer.setInput(this);
							IMergeViewerTestAdapter testAdapter = contentViewer.getAdapter(IMergeViewerTestAdapter.class);
							if (testAdapter.getChangesCount() == 0) {
								return DiffNodeState.ALL;
							}
						} else {
							Log.error("No Viewer created for " + getName());
						}
					} finally {
						shell.dispose();
					}

					if (Arrays.equals(right.getContent(), right.initialContent())) {
						return DiffNodeState.NONE;
					}
				} catch (Exception e) {
					Log.log(e);
				}
			}
			return DiffNodeState.PARTIAL;
		}

		void clearDirty() {
			setState(DiffNodeState.NONE);
		}

		@Override
		public String getName() {
			if (fLastName == null)
				fLastName= super.getName();
			return fLastName;
		}

		@Override
		public ITypedElement getId() {
			ITypedElement id= super.getId();
			if (id == null)
				return fLastId;
			fLastId= id;
			return id;
		}

		public DiffNodeState getState() {
			return state;
		}

		private void setState(DiffNodeState state) {
			if (this.state != state) {
				this.state = state;
				if (getParent() instanceof MyDiffNode) {
					MyDiffNode parent = (MyDiffNode) getParent();
					parent.setState(calculateContainerState(parent));
				}

				// PT 174276041 - Only enable Finish button if there are changes to save
				// This sets dirty if any of the diff nodes changes state that indicates dirty
				ResourceCompareInput.this.setDirty(isTreeDirty());

				if (fDiffViewer != null) {
					if (Display.getCurrent() == null) {
						if (!fDiffViewer.getControl().isDisposed()) {
							fDiffViewer.getControl().getDisplay().asyncExec(() -> fDiffViewer.refresh(this));
						}
					} else {
						fDiffViewer.refresh(this);
					}
				}
			}
		}

		private boolean isTreeDirty() {
			if (fDiffViewer != null) {
				MyDiffNode inp = (MyDiffNode)fDiffViewer.getInput();
				return inp.getState() != DiffNodeState.NONE;
			}
			return false;
		}

		public void reset() {
			determineStateFromContent = false;
			try {
				if (getChildren() == null || getChildren().length == 0) {
					if (originalRight != getRight()) {
						setRight(originalRight);
						fireChange();
					}
					if (getRight() instanceof FilteredBufferedResourceNode) {
						try {
							((FilteredBufferedResourceNode)getRight()).reset();
						} catch (CoreException e) {
							Log.log(e);
						}
					}
				} else {
					for (IDiffElement e : getChildren()) {
						if (e instanceof MyDiffNode) {
							((MyDiffNode)e).reset();
						}
					}
				}
				getMergeContentViewer().refresh();
				setState(DiffNodeState.NONE);
			} finally {
				determineStateFromContent = true;
			}
		}
		@Override
		public void setLeft(ITypedElement left) {
			super.setLeft(left);
			// Set the DiffNode whenever "left" changes to be able to call MyDiffNode#fireChange() when "left" content changes
			if (left instanceof FilteredBufferedResourceNode) {
				((FilteredBufferedResourceNode) left).diffNode = this;
			}
		}

		@Override
		public void setRight(ITypedElement right) {
			super.setRight(right);
			// Set the DiffNode whenever "right" changes to be able to call MyDiffNode#fireChange() "right" when content changes
			if (right instanceof FilteredBufferedResourceNode) {
				((FilteredBufferedResourceNode) right).diffNode = this;
			}
		}

	}

	/**
	 * Calculates container diff node state based on states of its children
	 * @param n the diff node
	 * @return
	 */
	private static DiffNodeState calculateContainerState(MyDiffNode n) {
		if (n.getChildren() == null || n.getChildren().length == 0) {
			return n.getState();
		} else {
			int numberOfFull = 0;
			for (int i = 0; i < n.getChildren().length; i++) {
				IDiffElement e = n.getChildren()[i];
				if (e instanceof MyDiffNode) {
					DiffNodeState childState = ((MyDiffNode) e).getState();
					if (childState == DiffNodeState.PARTIAL) {
						// At least one child partial? Stop - the parent would be partial as well in this case.
						return DiffNodeState.PARTIAL;
					} else if (childState == DiffNodeState.ALL) {
						// Guaranteed no partial changes for children prior to this one. Increase the number of full changes applied children
						numberOfFull = i + 1;
					} else {
						// Encountered none state child. Guaranteed no partial changes for children prior to this one.
						if (numberOfFull > 0) {
							// If there were children with all changes applied previously return partial state right away.
							return DiffNodeState.PARTIAL;
						}
						// else go to next element as all previous in initial state
					}
				}
			}
			return numberOfFull == 0 ? DiffNodeState.NONE : DiffNodeState.ALL;
		}
	}

	static class FilteredBufferedResourceNode extends BufferedResourceNode {

		private Predicate<String> filter;
		MyDiffNode diffNode;

		FilteredBufferedResourceNode(IResource resource, Predicate<String> filter) {
			super(resource);
			this.filter = filter;
		}

		public void reset() throws CoreException {
			setContent(initialContent());
		}

		byte[] initialContent() throws CoreException {
			try (InputStream in = createStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

				while (true) {
					int c= in.read();
					if (c == -1)
						break;
					bos.write(c);
				}

				return bos.toByteArray();

			} catch (IOException ex) {
				return null;
			}

		}

		@Override
		protected IStructureComparator createChild(IResource child) {
			String path = child.getType() == IResource.FILE ? child.getProjectRelativePath().toString()
					: child.getProjectRelativePath().addTrailingSeparator().toString();
			if (filter == null || filter.test(path) || child.getType() == IResource.FOLDER) {
				return new FilteredBufferedResourceNode(child, filter);
			}
			return null;
		}
		@Override
		public ITypedElement replace(ITypedElement child, ITypedElement other) {
			if (child == null) {	// add resource
				// create a node without a resource behind it!
				IResource resource= getResource();
				if (resource instanceof IProject) {
					IProject p = (IProject) resource;
					IFile file = p.getFile(other.getName());
					child = new FilteredBufferedResourceNode(file, filter);
				}
				if (resource instanceof IContainer && other.getType() == ITypedElement.FOLDER_TYPE) {
					IFolder folder = ((IContainer)resource).getFolder(new Path(other.getName()));
					FilteredBufferedResourceNode newFolderNode = new FilteredBufferedResourceNode(folder, filter);
					// Set content for the dirty flag to become set
					newFolderNode.setContent(null);
					child = newFolderNode;
				}
			}
			return super.replace(child, other);
		}
		@Override
		public void commit(IProgressMonitor pm) throws CoreException {
			if (isDirty()) {
				IResource resource= getResource();
				if (resource instanceof IFolder) {
					((IFolder) resource).create(true, true, pm);
					return;
				}
			}
			super.commit(pm);
		}
		@Override
		public void setContent(byte[] contents) {
			super.setContent(contents);
			// Overridden to send to call MyDiffNode#foreChange()
			if (diffNode != null) {
				diffNode.fireChange();
			}
		}


	}

	/*
	 * Creates an compare editor input for the given selection.
	 */
	public ResourceCompareInput(CompareConfiguration config, Predicate<String> filter) {
		super(config);
		this.filter = filter;
	}

	class CustomDiffTreeViewer extends CheckboxDiffTreeViewer {

		CustomDiffTreeViewer(Composite parent, CompareConfiguration config) {
			super(new Tree(parent, SWT.MULTI | SWT.CHECK), config);
		}

		@Override
		protected void fillContextMenu(IMenuManager manager) {

			if (fOpenAction == null) {
				fOpenAction= new Action() {
					@Override
					public void run() {
						handleOpen(null);
					}
				};
				Utilities.initAction(fOpenAction, getBundle(), "action.CompareContents."); //$NON-NLS-1$
			}

			if (fCreateResourceAction == null) {
				fCreateResourceAction = new Action() {
					@Override
					public void run() {
						copySelected(true);
					}
				};
				fCreateResourceAction.setText("Create Resource in Workspace");
				fCreateResourceAction.setToolTipText("Create missing resource in the local project");
			}

			if (fAcceptChangesAction == null) {
				fAcceptChangesAction = new Action() {
					@Override
					public void run() {
						acceptAllChanges();
					}
				};
				fAcceptChangesAction.setText("Accept Changes");
				fAcceptChangesAction.setToolTipText("Accept all non-conflicting changes into local project");
			}

			ISelection selection= getSelection();
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection ss= (IStructuredSelection)selection;


				if (ss.size() == 1) {
					Object element= ss.getFirstElement();
					if (element instanceof MyDiffNode) {
						MyDiffNode diffNode = (MyDiffNode) element;
						ITypedElement te= diffNode.getId();
						if (te != null) {
							if (diffNode.getRight() == null) {
								manager.add(fCreateResourceAction);
							} else {
								// Accept Changes action whenever create resource action is not shown
								manager.add(fAcceptChangesAction);
							}
							if (!ITypedElement.FOLDER_TYPE.equals(te.getType())) {
								manager.add(fOpenAction);
							}
						}
					}
				} else {
					Object[] selectedElements = ss.toArray();
					boolean enabled = true;
					for (int i = 0; enabled && i < selectedElements.length; i++) {
						if (selectedElements[i] instanceof MyDiffNode) {
							MyDiffNode diffNode = (MyDiffNode) selectedElements[i];
							enabled = diffNode.getRight() == null;
						} else {
							enabled = false;
						}
					}
					if (enabled) {
						manager.add(fCreateResourceAction);
					} else {
						// Accept Changes action whenever create resource action is not shown
						manager.add(fAcceptChangesAction);
					}
				}
			}

			super.fillContextMenu(manager);
		}

		@Override
		protected void initialSelection() {
			expandAll();
			Object input = getInput();
			Object diffNodeName = getCompareConfiguration().getProperty(OPEN_DIFF_NODE_COMPARE_SETTING);
			if (input instanceof MyDiffNode && diffNodeName != null) {
				MyDiffNode root = (MyDiffNode) input;
				for (IDiffElement e : root.getChildren()) {
					MyDiffNode child = (MyDiffNode) e;
					if (child.getRight() != null && child.getRight().getName().equals(diffNodeName)) {
						getControl().getDisplay().asyncExec(() -> {
							setSelection(new StructuredSelection(new MyDiffNode[] { child }), true);
							handleOpen(null);
						});
						return;
					}
				}
			}
			super.initialSelection();
		}

		@SuppressWarnings("unchecked")
		public void acceptAllChanges() {
			Set<Object> visited = new HashSet<>();
			Queue<Object> toVisit = new LinkedList<>(getStructuredSelection().toList());
			// To avoid problems with currently opened TextMergeViewer close whatever was opened
			fireOpen(new OpenEvent(this, StructuredSelection.EMPTY));
			while (!toVisit.isEmpty()) {
				Object o = toVisit.poll();
				if (!visited.contains(o)) {
					visited.add(o);
					if (o instanceof MyDiffNode) {
						MyDiffNode n = (MyDiffNode) o;
						n.determineStateFromContent = false;
						try {
							IDiffElement[] children = n.getChildren();
							if (children == null || children.length == 0) {
								if (n.getRight() == null) {
									// Add missing resource
									copyOne(n, true);
									n.setState(DiffNodeState.ALL);
								} else if (!ITypedElement.FOLDER_TYPE.equals(n.getId().getType())) {
									Shell shell = new Shell();
									shell.setVisible(false);
									// Content difference
									ContentMergeViewer contentViewer = (ContentMergeViewer) CompareUI.findContentViewer(new NullViewer(shell),
											n, shell, getCompareConfiguration());
									if (contentViewer != null) {
										contentViewer.setInput(n);
										try {
											Method method;
											try {
												method = contentViewer.getClass().getDeclaredMethod("copy", boolean.class);
											} catch (Exception e) {
												method = TextMergeViewer.class.getDeclaredMethod("copy", boolean.class);
											}
											method.setAccessible(true);
											method.invoke(contentViewer, true);
											contentViewer.flush(new NullProgressMonitor());
											n.setState(DiffNodeState.ALL);
										} catch (Exception e) {
											Log.log(e);
										}
									} else {
										Log.error("No Viewer created for " + n.getName());
									}
									shell.dispose();
								}
							} else {
								toVisit.addAll(Arrays.asList(children));
							}
						} finally {
							n.determineStateFromContent = true;
						}
					}
				}
			}
			// Reopen the TextMergeViewer whatever matches the current selection
			handleOpen(null);
		}


	}

	public Viewer getMergeContentViewer() {
		try {
			Field f = CompareEditorInput.class.getDeclaredField("fContentInputPane");
			f.setAccessible(true);
			CompareViewerSwitchingPane p = (CompareViewerSwitchingPane) f.get(ResourceCompareInput.this);
			Viewer viewer = p.getViewer();
			return viewer;
		} catch (Exception e) {
			Log.log(e);
			return null;
		}
	}

	class CustomNullViewer extends AbstractViewer {

		private final Control fDummy;
		private final Composite parent;
		private boolean firstSetInput = true;

		public CustomNullViewer(Composite parent) {
			this.parent = parent;
			fDummy= new Tree(parent, SWT.NULL);

		}

		@Override
		public void setInput(Object input) {
			if (!parent.isDisposed() && firstSetInput) {
				CompareViewerPane.clearToolBar(parent);
			}
			super.setInput(input);
			firstSetInput = false;
		}

		@Override
		public Control getControl() {
			return fDummy;
		}
	}

	@Override
	protected CompareViewerSwitchingPane createContentViewerSwitchingPane(Splitter parent, int style, CompareEditorInput cei) {
		return new CompareContentViewerSwitchingPane(parent, style, cei) {
			@Override
			protected Viewer getViewer(Viewer oldViewer, Object input) {
				if (input instanceof MyDiffNode n) {
					if (ITypedElement.FOLDER_TYPE.equals(n.getId().getType())) {
						return new CustomNullViewer(this);
					}
				}
				return super.getViewer(oldViewer, input);
			}
		};
	}

	@Override
	public Viewer createDiffViewer(Composite parent) {
		fDiffViewer= new CustomDiffTreeViewer(parent, getCompareConfiguration());
		fDiffViewer.setCheckStateProvider(new ICheckStateProvider() {

			@Override
			public boolean isGrayed(Object element) {
				if (element instanceof MyDiffNode) {
					return ((MyDiffNode)element).getState() == DiffNodeState.PARTIAL;
				}
				return false;
			}

			@Override
			public boolean isChecked(Object element) {
				if (element instanceof MyDiffNode) {
					return ((MyDiffNode)element).getState() != DiffNodeState.NONE ;
				}
				return false;
			}
		});
		fDiffViewer.addCheckStateListener(event -> {
			if (event.getElement() instanceof MyDiffNode) {
				MyDiffNode n = (MyDiffNode) event.getElement();
				fDiffViewer.setSelection(new StructuredSelection(Collections.singletonList(n)));
				if (event.getChecked()) {
					fDiffViewer.acceptAllChanges();
				} else {
					n.reset();
				}
			}
		});
		return fDiffViewer;
	}

	class SelectAncestorDialog extends MessageDialog {
		private IResource[] theResources;
		IResource ancestorResource;
		IResource leftResource;
		IResource rightResource;

		private Button[] buttons;

		public SelectAncestorDialog(Shell parentShell, IResource[] theResources) {
			super(parentShell, CompareMessages.SelectAncestorDialog_title,
				null, CompareMessages.SelectAncestorDialog_message,
				MessageDialog.QUESTION,
				new String[] { IDialogConstants.OK_LABEL,
					IDialogConstants.CANCEL_LABEL }, 0);
			this.theResources = theResources;
		}

		@Override
		protected Control createCustomArea(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout());
			buttons = new Button[3];
			for (int i = 0; i < 3; i++) {
				buttons[i] = new Button(composite, SWT.RADIO);
				buttons[i].addSelectionListener(selectionListener);
				buttons[i].setText(NLS.bind(CompareMessages.SelectAncestorDialog_option,
					theResources[i].getFullPath().toPortableString()));
				buttons[i].setFont(parent.getFont());
				// set initial state
				buttons[i].setSelection(i == 0);
			}
			pickAncestor(0);
			return composite;
		}

		private void pickAncestor(int i) {
			ancestorResource = theResources[i];
			leftResource = theResources[i == 0 ? 1 : 0];
			rightResource = theResources[i == 2 ? 1 : 2];
		}

		private SelectionListener selectionListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Button selectedButton = (Button) e.widget;
				if (!selectedButton.getSelection())
					return;
				for (int i = 0; i < 3; i++)
					if (selectedButton == buttons[i])
						pickAncestor(i);
			}
		};
	}
	// If the compare is three-way, this method asks the user which resource
	// to use as the ancestor. Depending on the value of
	// showSelectAncestorDialog flag it uses different dialogs to get the
	// feedback from the user. Returns false if the user cancels the prompt,
	// true otherwise.
	public boolean setSelection(ResourceDescriptor left, ResourceDescriptor right) {

		fLeftResource= left;
		fRightResource= left;

		fLeft= getStructure(fLeftResource);
		fRight= getStructure(right);
		return true;
	}

//	private boolean showCompareWithOtherResourceDialog(Shell shell, ISelection s) {
//		CompareWithOtherResourceDialog dialog = new CompareWithOtherResourceDialog(shell, s);
//		if (dialog.open() != IDialogConstants.OK_ID)
//			return false;
//		IResource[] selection = dialog.getResult();
//		if (!checkSelection(selection))
//			return false;
//
//		fThreeWay = selection.length == 3;
//		if (fThreeWay) {
//			fAncestorResource = selection[0];
//			fAncestor = getStructure(fAncestorResource);
//			fLeftResource = selection[1];
//			fRightResource = selection[2];
//		} else {
//			fAncestorResource = null;
//			fAncestor = null;
//			fLeftResource = selection[0];
//			fRightResource = selection[1];
//		}
//		fLeft= getStructure(fLeftResource);
//		fRight= getStructure(fRightResource);
//		return true;
//	}
//
//	private boolean checkSelection(IResource[] resources) {
//		for (IResource resource : resources) {
//			if (resource == null) {
//				return false;
//			}
//		}
//		return true;
//	}

	/*
	 * Returns true if compare can be executed for the given selection.
	 */
	public boolean isEnabled(ISelection s) {

		IResource[] selection= Utilities.getResources(s);
		if (selection.length < 2 || selection.length > 3)
			return false;

		boolean threeWay= selection.length == 3;

		if (threeWay)
			// It only makes sense if they're all mutually comparable.
			// If not, the user should compare two of them.
			return comparable(selection[0], selection[1])
				&& comparable(selection[0], selection[2])
				&& comparable(selection[1], selection[2]);

		return comparable(selection[0], selection[1]);
	}

	/**
	 * Initializes the images in the compare configuration.
	 */
	void initializeCompareConfiguration() {
		CompareConfiguration cc= getCompareConfiguration();
		if (fLeftResource != null) {
			cc.setLeftLabel(buildLabel(fLeftResource));
			cc.setLeftImage(fLeftResource.image());
		}
		if (fRightResource != null) {
			cc.setRightLabel(buildLabel(fRightResource));
			cc.setRightImage(fRightResource.image());
		}
//		if (fThreeWay && fAncestorResource != null) {
//			cc.setAncestorLabel(buildLabel(fAncestorResource));
//			cc.setAncestorImage(CompareUIPlugin.getImage(fAncestorResource));
//		}
	}

	/*
	 * Returns true if both resources are either structured or unstructured.
	 */
	private boolean comparable(IResource c1, IResource c2) {
		return hasStructure(c1) == hasStructure(c2);
	}

	/*
	 * Returns true if the given argument has a structure.
	 */
	private boolean hasStructure(IResource input) {

		if (input instanceof IContainer)
			return true;

		if (input instanceof IFile) {
			IFile file= (IFile) input;
			String type= file.getFileExtension();
			if (type != null) {
				type= normalizeCase(type);
				return "JAR".equals(type) || "ZIP".equals(type);	//$NON-NLS-2$ //$NON-NLS-1$
			}
		}

		return false;
	}

	/*
	 * Creates a <code>IStructureComparator</code> for the given input.
	 * Returns <code>null</code> if no <code>IStructureComparator</code>
	 * can be found for the <code>IResource</code>.
	 */
	private IStructureComparator getStructure(ResourceDescriptor r) {
		return r.structure(filter);
	}

	/*
	 * Performs a two-way or three-way diff on the current selection.
	 */
	@Override
	public Object prepareInput(IProgressMonitor pm) throws InvocationTargetException {

		try {
			// fix for PR 1GFMLFB: ITPUI:WIN2000 - files that are out of sync with the file system appear as empty
			fLeftResource.prepare(pm);
			fRightResource.prepare(pm);
			if (fThreeWay && fAncestorResource != null)
				fAncestorResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
			// end fix

			pm.beginTask(Utilities.getString("ResourceCompare.taskName"), IProgressMonitor.UNKNOWN); //$NON-NLS-1$

			String leftLabel= fLeftResource.name();
			String rightLabel= fRightResource.name();

			String title;
			if (fThreeWay) {
				String format= Utilities.getString("ResourceCompare.threeWay.title"); //$NON-NLS-1$
				String ancestorLabel= fAncestorResource.getName();
				title= MessageFormat.format(format, ancestorLabel, leftLabel, rightLabel);
			} else {
				String format= Utilities.getString("ResourceCompare.twoWay.title"); //$NON-NLS-1$
				title= MessageFormat.format(format, leftLabel, rightLabel);
			}
			setTitle(title);

			Differencer d= new Differencer() {
				@Override
				protected Object visit(Object parent, int description, Object ancestor, Object left, Object right) {
					return new MyDiffNode((IDiffContainer) parent, description, (ITypedElement)ancestor, (ITypedElement)left, (ITypedElement)right);
				}
			};

			fRoot= (MyDiffNode) d.findDifferences(fThreeWay, pm, null, fAncestor, fLeft, fRight);
			cleanDifferences(fRoot);
			return fRoot;

		} catch (CoreException ex) {
			throw new InvocationTargetException(ex);
		} finally {
			pm.done();
		}
	}

	private void cleanDifferences(MyDiffNode n) {
		if (n.hasChildren()) {
			for (IDiffElement child : n.getChildren()) {
				if (child instanceof MyDiffNode) {
					cleanDifferences((MyDiffNode) child);
				}
			}
		} else {
			ITypedElement right = n.getRight();
			ITypedElement left = n.getLeft();
			/*
			 * If left side is 'null' then it is a removal on the right. Ignore all such changes!
			 */
			if (left == null) {
				if (n.getParent() != null) {
					n.getParent().removeToRoot(n);
				}
				return;
			}
			/*
			 * Exclude added folders that don't have any filtered structure nodes
			 */
			if (right == null && left != null && left.getType() == ITypedElement.FOLDER_TYPE) {
				if (left instanceof IStructureComparator) {
					IStructureComparator str = (IStructureComparator) left;
					if (str.getChildren() == null || str.getChildren().length == 0) {
						if (filter != null) {
							StringBuilder folderPath = new StringBuilder();
							pathForLeft(n, folderPath);
							if (filter.test(folderPath.toString())) {
								return;
							}
						}
						if (n.getParent() != null) {
							n.getParent().removeToRoot(n);
						}
						return;
					}
				}
			}

		}
	}

	private void pathForLeft(MyDiffNode n, StringBuilder path) {
		if (n == null) {
			return;
		} else {
			ITypedElement left = n.getLeft();
			pathForLeft((MyDiffNode) n.getParent(), path);
			if (!left.getName().isEmpty()) {
				path.append(left.getName());
				if (left.getType() == ITypedElement.FOLDER_TYPE) {
					path.append('/');
				}
			}
		}
	}

	@Override
	public String getToolTipText() {
		if (fLeftResource != null && fRightResource != null) {
			String leftLabel= fLeftResource.tooltipPathLabel();
			String rightLabel= fRightResource.tooltipPathLabel();
			if (fThreeWay) {
				String format= Utilities.getString("ResourceCompare.threeWay.tooltip"); //$NON-NLS-1$
				String ancestorLabel= fAncestorResource.getFullPath().makeRelative().toString();
				return MessageFormat.format(format, ancestorLabel, leftLabel, rightLabel);
			}
			String format= Utilities.getString("ResourceCompare.twoWay.tooltip"); //$NON-NLS-1$
			return MessageFormat.format(format, leftLabel, rightLabel);
		}
		// fall back
		return super.getToolTipText();
	}

	private String buildLabel(ResourceDescriptor r) {
		return r.label();
	}


	@Override
	public void saveChanges(IProgressMonitor monitor) throws CoreException {

		// Save changes is run modally in a separate fork by the okPressed() method.
		// However, there are several steps that need to be performed in the UI thread, like
		// flushing the viewers and refreshing the diff viewer

		// The reason to override save changes is to ensure that the parts that need to be run in
		// UI thread are done so.

		runInUI(() -> flushViewers(monitor));

		if (fRoot instanceof DiffNode) {
			try {
				commit(monitor, (DiffNode) fRoot);
			} finally {
				runInUI(() -> {
					if (fDiffViewer != null) {
						fDiffViewer.refresh();
					}
					setDirty(false);
				});
			}
		}
	}

	private void runInUI(Runnable runnable) {
		PlatformUI.getWorkbench().getDisplay().syncExec(runnable);
	}

	/*
	 * Recursively walks the diff tree and commits all changes.
	 */
	private static void commit(IProgressMonitor pm, DiffNode node) throws CoreException {

		if (node instanceof MyDiffNode)
			((MyDiffNode)node).clearDirty();

		ITypedElement left= node.getLeft();
		if (left instanceof BufferedResourceNode)
			((BufferedResourceNode) left).commit(pm);

		ITypedElement right= node.getRight();
		if (right instanceof BufferedResourceNode)
			((BufferedResourceNode) right).commit(pm);

		IDiffElement[] children= node.getChildren();
		if (children != null) {
			for (IDiffElement element : children) {
				if (element instanceof DiffNode)
					commit(pm, (DiffNode) element);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (IFile.class.equals(adapter)) {
			IProgressMonitor pm= new NullProgressMonitor();
			// flush changes in any dirty viewer
			flushViewers(pm);
			IFile[] files= getAdapter(IFile[].class);
			if (files != null && files.length > 0)
				return (T) files[0];	// can only return one: limitation on IDE.saveAllEditors; see #64617
			return null;
		}
		if (IFile[].class.equals(adapter)) {
			HashSet<IFile> collector= new HashSet<>();
			collectDirtyResources(fRoot, collector);
			return (T) collector.toArray(new IFile[collector.size()]);
		}
		return super.getAdapter(adapter);
	}

	private void collectDirtyResources(Object o, Set<IFile> collector) {
		if (o instanceof DiffNode) {
			DiffNode node= (DiffNode) o;

			ITypedElement left= node.getLeft();
			if (left instanceof BufferedResourceNode) {
				BufferedResourceNode bn= (BufferedResourceNode) left;
				if (bn.isDirty()) {
					IResource resource= bn.getResource();
					if (resource instanceof IFile)
						collector.add((IFile) resource);
				}
			}

			ITypedElement right= node.getRight();
			if (right instanceof BufferedResourceNode) {
				BufferedResourceNode bn= (BufferedResourceNode) right;
				if (bn.isDirty()) {
					IResource resource= bn.getResource();
					if (resource instanceof IFile)
						collector.add((IFile) resource);
				}
			}

			IDiffElement[] children= node.getChildren();
			if (children != null) {
				for (IDiffElement element : children) {
					if (element instanceof DiffNode)
						collectDirtyResources(element, collector);
				}
			}
		}
	}

	private static String normalizeCase(String s) {
		if (NORMALIZE_CASE && s != null)
			return s.toUpperCase();
		return s;
	}

	@Override
	public boolean canRunAsJob() {
		return true;
	}

	public boolean hasDiffs() {
		return fRoot.hasChildren();
	}


}

