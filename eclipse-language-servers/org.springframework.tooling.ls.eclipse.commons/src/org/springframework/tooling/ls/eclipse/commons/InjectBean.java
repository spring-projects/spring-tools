package org.springframework.tooling.ls.eclipse.commons;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PartInitException;
import org.springframework.tooling.jdt.ls.commons.Logger;
import org.springframework.tooling.jdt.ls.commons.resources.ResourceUtils;

public final class InjectBean {

	private final Logger logger;

	public InjectBean(Logger logger) {
		this.logger = logger;
	}

	public TextDocumentEdit computeEdits(String docUri, String fieldType, String fieldName) {
		try {
			URI resourceUri = URI.create(docUri);
			IJavaProject project = ResourceUtils.getJavaProject(resourceUri);

			Optional<IEditorInput> optEditorInput = LSPEclipseUtils.findOpenEditorsFor(resourceUri).stream().map(e -> {
				try {
					return e.getEditorInput();
				} catch (PartInitException e1) {
					return null;
				}
			}).findFirst();

			if (project != null && optEditorInput.isPresent()) {
				ICompilationUnit cu = JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(optEditorInput.get());
				RefactoringASTParser parser= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
				CompilationUnit domCu = parser.parse(cu, true);


				CompilationUnitRewrite cuRewrite = new CompilationUnitRewrite(null, cu, domCu, Map.of());

				AST ast= cuRewrite.getAST();
				VariableDeclarationFragment variableDeclarationFragment= ast.newVariableDeclarationFragment();
				variableDeclarationFragment.setName(ast.newSimpleName(fieldName));

				FieldDeclaration fieldDeclaration= ast.newFieldDeclaration(variableDeclarationFragment);

				fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
				fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));

				IJavaElement el = project.findElement("L" + fieldType.replace(".", "/") + ";", null);
				IType itype = project.findType(fieldType);
				String packageName = itype.getPackageFragment().getElementName();
				String fqn = itype.getFullyQualifiedName();
//				org.eclipse.jdt.core.dom.ITypeBinding typeBinding= Bindings.normalizeForDeclarationUse(tb, ast);
				ImportRewrite importRewrite= cuRewrite.getImportRewrite();
				ImportRewriteContext context= new ContextSensitiveImportRewriteContext(cuRewrite.getRoot(), 0, importRewrite);
//				Type t = importRewrite.addImport(typeBinding, ast, context, TypeLocation.FIELD);
				String typeName = importRewrite.addImport(fqn);
				fieldDeclaration.setType(ast.newSimpleType(ast.newName(typeName)));

				AbstractTypeDeclaration parent = (AbstractTypeDeclaration) domCu.types().get(0);
				Assert.isNotNull(parent);
				ListRewrite listRewrite= cuRewrite.getASTRewrite().getListRewrite(parent, parent.getBodyDeclarationsProperty());
				TextEditGroup msg= cuRewrite.createGroupDescription(RefactoringCoreMessages.ExtractConstantRefactoring_declare_constant);
				listRewrite.insertFirst(fieldDeclaration, msg);

//				TextEdit edit = cuRewrite.getASTRewrite().rewriteAST();

				CompilationUnitChange c = cuRewrite.createChange(false);
				List<org.eclipse.lsp4j.TextEdit> textEdits = convertTextEdit(domCu, c.getEdit());

				logger.log("Here");
				return textEdits.isEmpty() ? null : new TextDocumentEdit(new VersionedTextDocumentIdentifier(docUri, 0), textEdits);
			}

		} catch (Exception e) {
			logger.log(e);
		}
		return null;
	}

	private List<org.eclipse.lsp4j.TextEdit> convertTextEdit(CompilationUnit domCu, TextEdit te) {
		LinkedList<org.eclipse.lsp4j.TextEdit> edits = new LinkedList<>();
		org.eclipse.lsp4j.TextEdit edit = convertSingleEdit(domCu, te);
		if (edit != null) {
			edits.add(edit);
		}
		for (TextEdit c : te.getChildren()) {
			edits.addAll(convertTextEdit(domCu, c));
		}
		return edits;
	}

	private org.eclipse.lsp4j.TextEdit convertSingleEdit(CompilationUnit domCu, TextEdit te) {
		if (te instanceof DeleteEdit de) {
			org.eclipse.lsp4j.TextEdit edit = new org.eclipse.lsp4j.TextEdit();
			int startLine = domCu.getLineNumber(de.getOffset());
			int startColumn = domCu.getColumnNumber(de.getOffset()) - 1;
			int endLine = domCu.getLineNumber(de.getOffset() + de.getLength());
			int endColumn = domCu.getColumnNumber(de.getOffset() + de.getLength()) - 1;
			edit.setNewText("");
			edit.setRange(new Range(new Position(startLine, startColumn), new Position(endLine, endColumn)));
			return edit;
		} else if (te instanceof ReplaceEdit re) {
			org.eclipse.lsp4j.TextEdit edit = new org.eclipse.lsp4j.TextEdit();
			int startLine = domCu.getLineNumber(re.getOffset());
			int startColumn = domCu.getColumnNumber(re.getOffset()) - 1;
			int endLine = domCu.getLineNumber(re.getOffset() + re.getLength());
			int endColumn = domCu.getColumnNumber(re.getOffset() + re.getLength()) - 1;
			edit.setNewText(re.getText());
			edit.setRange(new Range(new Position(startLine, startColumn), new Position(endLine, endColumn)));
			return edit;
		} else if (te instanceof InsertEdit ie) {
			org.eclipse.lsp4j.TextEdit edit = new org.eclipse.lsp4j.TextEdit();
			int startLine = domCu.getLineNumber(ie.getOffset());
			int startColumn = domCu.getColumnNumber(ie.getOffset()) - 1;
			edit.setNewText(ie.getText());
			edit.setRange(new Range(new Position(startLine, startColumn), new Position(startLine, startColumn)));
			return edit;
		}
		return null;
	}

}
