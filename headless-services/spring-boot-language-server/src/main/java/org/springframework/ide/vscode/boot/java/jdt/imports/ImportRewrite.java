/*******************************************************************************
 * Derived from:
 * org.eclipse.jdt.core.dom.rewrite.ImportRewrite
 *
 * for use in STS4, where IProject and ICompilationUnit are not available when parsing a Java source.
 *
 * Original license:
 *
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     John Glassmyer <jogl@google.com> - import group sorting is broken - https://bugs.eclipse.org/430303
 *     Lars Vogel <Lars.Vogel@vogella.com> - Contributions for
 *     						Bug 473178
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.jdt.imports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.util.text.IDocument;


/**
 * The {@link ImportRewrite} helps updating imports following a import order and on-demand imports threshold as configured by a project.
 * <p>
 * The import rewrite is created on a compilation unit and collects references to types that are added or removed. When adding imports, e.g. using
 * {@link #addImport(String)}, the import rewrite evaluates if the type can be imported and returns the a reference to the type that can be used in code.
 * This reference is either unqualified if the import could be added, or fully qualified if the import failed due to a conflict with another element of the same name.
 * </p>
 * <p>
 * On {@link #rewriteImports(IProgressMonitor)} the rewrite translates these descriptions into
 * text edits that can then be applied to the original source. The rewrite infrastructure tries to generate minimal text changes and only
 * works on the import statements. It is possible to combine the result of an import rewrite with the result of a {@link org.eclipse.jdt.core.dom.rewrite.ASTRewrite}
 * as long as no import statements are modified by the AST rewrite.
 * </p>
 * <p>The options controlling the import order and on-demand thresholds are:
 * <ul><li>{@link #setImportOrder(String[])} specifies the import groups and their preferred order</li>
 * <li>{@link #setOnDemandImportThreshold(int)} specifies the number of imports in a group needed for a on-demand import statement (star import)</li>
 * <li>{@link #setStaticOnDemandImportThreshold(int)} specifies the number of static imports in a group needed for a on-demand import statement (star import)</li>
 *</ul>
 * This class is not intended to be subclassed.
 * </p>
 * @since 3.2
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class ImportRewrite {

	/**
	 * A {@link ImportRewrite.ImportRewriteContext} can optionally be used in e.g. {@link ImportRewrite#addImport(String, ImportRewrite.ImportRewriteContext)} to
	 * give more information about the types visible in the scope. These types can be for example inherited inner types where it is
	 * unnecessary to add import statements for.
	 *
	 * </p>
	 * <p>
	 * This class can be implemented by clients.
	 * </p>
	 */
	public static abstract class ImportRewriteContext {

		/**
		 * Result constant signaling that the given element is know in the context.
		 */
		public final static int RES_NAME_FOUND= 1;

		/**
		 * Result constant signaling that the given element is not know in the context.
		 */
		public final static int RES_NAME_UNKNOWN= 2;

		/**
		 * Result constant signaling that the given element is conflicting with an other element in the context.
		 */
		public final static int RES_NAME_CONFLICT= 3;

		/**
		 * Result constant signaling that the given element must be imported explicitly (and must not be folded into
		 * an on-demand import or filtered as an implicit import).
		 *
		 * @since 3.11
		 */
		public final static int RES_NAME_UNKNOWN_NEEDS_EXPLICIT_IMPORT= 4;

		/**
		 * Kind constant specifying that the element is a type import.
		 */
		public final static int KIND_TYPE= 1;

		/**
		 * Kind constant specifying that the element is a static field import.
		 */
		public final static int KIND_STATIC_FIELD= 2;

		/**
		 * Kind constant specifying that the element is a static method import.
		 */
		public final static int KIND_STATIC_METHOD= 3;

		/**
		 * Searches for the given element in the context and reports if the element is known ({@link #RES_NAME_FOUND}),
		 * unknown ({@link #RES_NAME_UNKNOWN}), unknown in the context but known to require an explicit import
		 * ({@link #RES_NAME_UNKNOWN_NEEDS_EXPLICIT_IMPORT}), or if its name conflicts ({@link #RES_NAME_CONFLICT})
		 * with an other element.
		 *
		 * @param qualifier The qualifier of the element, can be package or the qualified name of a type
		 * @param name The simple name of the element; either a type, method or field name or * for on-demand imports.
		 * @param kind The kind of the element. Can be either {@link #KIND_TYPE}, {@link #KIND_STATIC_FIELD} or
		 * {@link #KIND_STATIC_METHOD}. Implementors should be prepared for new, currently unspecified kinds and return
		 * {@link #RES_NAME_UNKNOWN} by default.
		 * @return Returns the result of the lookup. Can be either {@link #RES_NAME_FOUND}, {@link #RES_NAME_UNKNOWN},
		 * {@link #RES_NAME_CONFLICT}, or {@link #RES_NAME_UNKNOWN_NEEDS_EXPLICIT_IMPORT}.
		 */
		public abstract int findInContext(String qualifier, String name, int kind);
	}

	private static final char STATIC_PREFIX= 's';
	private static final char NORMAL_PREFIX= 'n';

	private final ImportRewriteContext defaultContext;

	private final CompilationUnit astRoot;

	private final boolean restoreExistingImports;
	private final List existingImports;


	private List<String> addedImports;

	/**
	 * Simple names of non-static imports which must not be reduced into on-demand imports
	 * or filtered out as implicit.
	 */
	private Set<String> typeExplicitSimpleNames;


	private boolean filterImplicitImports;
	private boolean useContextToFilterImplicitImports;


	/**
	 * Creates an {@link ImportRewrite} from an AST ({@link CompilationUnit}). The AST has to be created from an
	 * {@link ICompilationUnit}, that means {@link ASTParser#setSource(ICompilationUnit)} has been used when creating the
	 * AST. If <code>restoreExistingImports</code> is <code>true</code>, all existing imports are kept, and new imports
	 * will be inserted at best matching locations. If <code>restoreExistingImports</code> is <code>false</code>, the
	 * existing imports will be removed and only the newly added imports will be created.
	 * <p>
	 * Note that this method is more efficient than using {@link #create(ICompilationUnit, boolean)} if an AST is already available.
	 * </p>
	 * @param astRoot the AST root node to create the imports for
	 * @param restoreExistingImports specifies if the existing imports should be kept or removed.
	 * @return the created import rewriter.
	 * @throws IllegalArgumentException thrown when the passed AST is null or was not created from a compilation unit.
	 */
	public static ImportRewrite create(CompilationUnit astRoot, boolean restoreExistingImports) {
		if (astRoot == null) {
			throw new IllegalArgumentException("AST must not be null"); //$NON-NLS-1$
		}

		List existingImport= null;
		if (restoreExistingImports) {
			existingImport= new ArrayList();
			List imports= astRoot.imports();
			for (int i= 0; i < imports.size(); i++) {
				ImportDeclaration curr= (ImportDeclaration) imports.get(i);
				StringBuffer buf= new StringBuffer();
				buf.append(curr.isStatic() ? STATIC_PREFIX : NORMAL_PREFIX).append(curr.getName().getFullyQualifiedName());
				if (curr.isOnDemand()) {
					if (buf.length() > 1)
						buf.append('.');
					buf.append('*');
				}
				existingImport.add(buf.toString());
			}
		}
		return new ImportRewrite(astRoot, existingImport);
	}

	private ImportRewrite(CompilationUnit astRoot, List existingImports) {
		this.astRoot= astRoot; // might be null
		if (existingImports != null) {
			this.existingImports= existingImports;
			this.restoreExistingImports= !existingImports.isEmpty();
		} else {
			this.existingImports= new ArrayList();
			this.restoreExistingImports= false;
		}
		this.filterImplicitImports= true;
		// consider that no contexts are used
		this.useContextToFilterImplicitImports = false;

		this.defaultContext= new ImportRewriteContext() {
			@Override
			public int findInContext(String qualifier, String name, int kind) {
				return findInImports(qualifier, name, kind);
			}
		};
		this.addedImports= new ArrayList<>();
		this.typeExplicitSimpleNames = new HashSet<>();
	}

	/**
	 * Returns the default rewrite context that only knows about the imported types. Clients
	 * can write their own context and use the default context for the default behavior.
	 * @return the default import rewrite context.
	 */
	public ImportRewriteContext getDefaultImportRewriteContext() {
		return this.defaultContext;
	}

	/**
	 * Specifies that implicit imports (for types in <code>java.lang</code>, types in the same package as the rewrite
	 * compilation unit, and types in the compilation unit's main type) should not be created, except if necessary to
	 * resolve an on-demand import conflict.
	 * <p>
	 * The filter is enabled by default.
	 * </p>
	 * <p>
	 * Note: {@link #setUseContextToFilterImplicitImports(boolean)} can be used to filter implicit imports
	 * when a context is used.
	 * </p>
	 *
	 * @param filterImplicitImports
	 *            if <code>true</code>, implicit imports will be filtered
	 *
	 * @see #setUseContextToFilterImplicitImports(boolean)
	 */
	public void setFilterImplicitImports(boolean filterImplicitImports) {
		this.filterImplicitImports= filterImplicitImports;
	}

	/**
	* Sets whether a context should be used to properly filter implicit imports.
	* <p>
	* By default, the option is disabled to preserve pre-3.6 behavior.
	* </p>
	* <p>
	* When this option is set, the context passed to the <code>addImport*(...)</code> methods is used to determine
	* whether an import can be filtered because the type is implicitly visible. Note that too many imports
	* may be kept if this option is set and <code>addImport*(...)</code> methods are called without a context.
	* </p>
	*
	* @param useContextToFilterImplicitImports the given setting
	*
	* @see #setFilterImplicitImports(boolean)
	* @since 3.6
	*/
	public void setUseContextToFilterImplicitImports(boolean useContextToFilterImplicitImports) {
		this.useContextToFilterImplicitImports = useContextToFilterImplicitImports;
	}

	private static int compareImport(char prefix, String qualifier, String name, String curr) {
		if (curr.charAt(0) != prefix || !curr.endsWith(name)) {
			return ImportRewriteContext.RES_NAME_UNKNOWN;
		}

		curr= curr.substring(1); // remove the prefix

		if (curr.length() == name.length()) {
			if (qualifier.length() == 0) {
				return ImportRewriteContext.RES_NAME_FOUND;
			}
			return ImportRewriteContext.RES_NAME_CONFLICT;
		}
		// at this place: curr.length > name.length

		int dotPos= curr.length() - name.length() - 1;
		if (curr.charAt(dotPos) != '.') {
			return ImportRewriteContext.RES_NAME_UNKNOWN;
		}
		if (qualifier.length() != dotPos || !curr.startsWith(qualifier)) {
			return ImportRewriteContext.RES_NAME_CONFLICT;
		}
		return ImportRewriteContext.RES_NAME_FOUND;
	}

	/**
	 * Not API, package visibility as accessed from an anonymous type
	 */
	/* package */ final int findInImports(String qualifier, String name, int kind) {
		boolean allowAmbiguity=  (kind == ImportRewriteContext.KIND_STATIC_METHOD) || (name.length() == 1 && name.charAt(0) == '*');
		List imports= this.existingImports;
		char prefix= (kind == ImportRewriteContext.KIND_TYPE) ? NORMAL_PREFIX : STATIC_PREFIX;

		for (int i= imports.size() - 1; i >= 0 ; i--) {
			String curr= (String) imports.get(i);
			int res= compareImport(prefix, qualifier, name, curr);
			if (res != ImportRewriteContext.RES_NAME_UNKNOWN) {
				if (!allowAmbiguity || res == ImportRewriteContext.RES_NAME_FOUND) {
					if (prefix != STATIC_PREFIX) {
						return res;
					}
				}
			}
		}

		String packageName = getPackageName();
		if (kind == ImportRewriteContext.KIND_TYPE) {
			if (this.filterImplicitImports && this.useContextToFilterImplicitImports) {

				// [STS4] No ICompilationUnit available as there is no class file or associated IJavaElement available for the source

//				String mainTypeSimpleName= JavaCore.removeJavaLikeExtension(this.compilationUnit.getElementName());
//				String mainTypeName= Util.concatenateName(packageName, mainTypeSimpleName, '.');
//				if (qualifier.equals(packageName)
//						|| mainTypeName.equals(Util.concatenateName(qualifier, name, '.'))) {
//					return ImportRewriteContext.RES_NAME_FOUND;
//				}

				if (this.astRoot != null) {
					List<AbstractTypeDeclaration> types = this.astRoot.types();
					int nTypes = types.size();
					for (int i = 0; i < nTypes; i++) {
						AbstractTypeDeclaration type = types.get(i);
						SimpleName simpleName = type.getName();
						if (simpleName.getIdentifier().equals(name)) {
							return qualifier.equals(packageName)
									? ImportRewriteContext.RES_NAME_FOUND
									: ImportRewriteContext.RES_NAME_CONFLICT;
						}
					}
				} else {

					// [STS4] No ICompilationUnit available as there is no class file or associated IJavaElement available for the source
//					try {
//						IType[] types = this.compilationUnit.getTypes();
//						int nTypes = types.length;
//						for (int i = 0; i < nTypes; i++) {
//							IType type = types[i];
//							String typeName = type.getElementName();
//							if (typeName.equals(name)) {
//								return qualifier.equals(packageName)
//										? ImportRewriteContext.RES_NAME_FOUND
//										: ImportRewriteContext.RES_NAME_CONFLICT;
//							}
//						}
//					} catch (JavaModelException e) {
//						// don't want to throw an exception here
//					}
				}
			}
		}

		return ImportRewriteContext.RES_NAME_UNKNOWN;
	}

	private String getPackageName() {
		// [STS4] No ICompilationUnit available as there is no class file or associated IJavaElement available for the source
//		this.compilationUnit.getParent().getElementName();
		return this.astRoot.getPackage().getName().getFullyQualifiedName();
	}

	/**
	 * Adds a new import to the rewriter's record and returns a type reference that can be used
	 * in the code. The type binding can only be an array or non-generic type.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param qualifiedTypeName the qualified type name of the type to be added
	 * @param context an optional context that knows about types visible in the current scope or <code>null</code>
	 * to use the default context only using the available imports.
	 * @return a type reference for the given qualified type name. The type name is a simple name if an import could be used,
	 * or else a qualified name if an import conflict prevented an import.
	 */
	public String addImport(String qualifiedTypeName, ImportRewriteContext context) {
		int angleBracketOffset= qualifiedTypeName.indexOf('<');
		if (angleBracketOffset != -1) {
			return internalAddImport(qualifiedTypeName.substring(0, angleBracketOffset), context) + qualifiedTypeName.substring(angleBracketOffset);
		}
		int bracketOffset= qualifiedTypeName.indexOf('[');
		if (bracketOffset != -1) {
			return internalAddImport(qualifiedTypeName.substring(0, bracketOffset), context) + qualifiedTypeName.substring(bracketOffset);
		}
		return internalAddImport(qualifiedTypeName, context);
	}

	/**
	 * Adds a new import to the rewriter's record and returns a type reference that can be used
	 * in the code. The type binding can only be an array or non-generic type.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param qualifiedTypeName the qualified type name of the type to be added
	 * @return a type reference for the given qualified type name. The type name is a simple name if an import could be used,
	 * or else a qualified name if an import conflict prevented an import.
	 */
	public String addImport(String qualifiedTypeName) {
		return addImport(qualifiedTypeName, this.defaultContext);
	}



	private String internalAddImport(String fullTypeName, ImportRewriteContext context) {
		int idx= fullTypeName.lastIndexOf('.');
		String typeContainerName, typeName;
		if (idx != -1) {
			typeContainerName= fullTypeName.substring(0, idx);
			typeName= fullTypeName.substring(idx + 1);
		} else {
			typeContainerName= ""; //$NON-NLS-1$
			typeName= fullTypeName;
		}

		if (typeContainerName.length() == 0 && PrimitiveType.toCode(typeName) != null) {
			return fullTypeName;
		}

		if (context == null)
			context= this.defaultContext;

		int res= context.findInContext(typeContainerName, typeName, ImportRewriteContext.KIND_TYPE);
		if (res == ImportRewriteContext.RES_NAME_CONFLICT) {
			return fullTypeName;
		}
		if (res == ImportRewriteContext.RES_NAME_UNKNOWN) {
			addEntry(NORMAL_PREFIX + fullTypeName);
		}
		if (res == ImportRewriteContext.RES_NAME_UNKNOWN_NEEDS_EXPLICIT_IMPORT) {
			addEntry(NORMAL_PREFIX + fullTypeName);
			this.typeExplicitSimpleNames.add(typeName);
		}
		return typeName;
	}

	private void addEntry(String entry) {
		this.existingImports.add(entry);

		this.addedImports.add(entry);
	}



	/**
	 * Returns all non-static imports that are recorded to be added.
	 *
	 * @return the imports recorded to be added.
	 */
	public String[] getAddedImports() {
		return filterFromList(this.addedImports, NORMAL_PREFIX);
	}

	/**
	 * Returns <code>true</code> if imports have been recorded to be added or removed.
	 * @return boolean returns if any changes to imports have been recorded.
	 */
	public boolean hasRecordedChanges() {
		return !this.restoreExistingImports
				|| !this.addedImports.isEmpty();
	}

	private static String[] filterFromList(List<String> imports, char prefix) {
		if (imports == null) {
			return CharOperation.NO_STRINGS;
		}
		List<String> res= new ArrayList<>();
		for (String curr : imports) {
			if (prefix == curr.charAt(0)) {
				res.add(curr.substring(1));
			}
		}
		return res.toArray(new String[res.size()]);
	}


	/**
	 * Reads the positions of each existing import declaration along with any associated comments,
	 * and returns these in a list whose iteration order reflects the existing order of the imports
	 * in the compilation unit.
	 */
	private int getAddedImportsInsertLocation() {
		List<ImportDeclaration> importDeclarations = astRoot.imports();

		if (importDeclarations == null) {
			importDeclarations = Collections.emptyList();
		}

		List<Comment> comments = astRoot.getCommentList();

		int currentCommentIndex = 0;

		// Skip over package and file header comments (see https://bugs.eclipse.org/121428).
		ImportDeclaration firstImport = importDeclarations.get(0);
		PackageDeclaration packageDeclaration = astRoot.getPackage();
		int firstImportStartPosition = packageDeclaration == null
				? firstImport.getStartPosition()
				: astRoot.getExtendedStartPosition(packageDeclaration)
						+ astRoot.getExtendedLength(packageDeclaration);
		while (currentCommentIndex < comments.size()
				&& comments.get(currentCommentIndex).getStartPosition() < firstImportStartPosition) {
			currentCommentIndex++;
		}

		int previousExtendedEndPosition = -1;
		for (ImportDeclaration currentImport : importDeclarations) {
			int extendedEndPosition = astRoot.getExtendedStartPosition(currentImport)
					+ astRoot.getExtendedLength(currentImport);

			int commentAfterImportIndex = currentCommentIndex;
			while (commentAfterImportIndex < comments.size()
					&& comments.get(commentAfterImportIndex).getStartPosition() < extendedEndPosition) {
				commentAfterImportIndex++;
			}


			currentCommentIndex = commentAfterImportIndex;
			previousExtendedEndPosition = extendedEndPosition;
		}

		return previousExtendedEndPosition;
	}

	public DocumentEdits createEdit(IDocument doc) {
		DocumentEdits edits = null;
		StringBuffer buffer = new StringBuffer();

		String[] createdImprts = getAddedImports();
		if (createdImprts != null && createdImprts.length > 0) {
			edits =new DocumentEdits(doc, false);
			buffer.append('\n');
			for (String imp : createdImprts) {
				buffer.append("import ");
				buffer.append(imp);
				buffer.append(';');
				buffer.append('\n');
			}
			edits.insert(getAddedImportsInsertLocation(), buffer.toString());

		}
		return edits;
	}
}
