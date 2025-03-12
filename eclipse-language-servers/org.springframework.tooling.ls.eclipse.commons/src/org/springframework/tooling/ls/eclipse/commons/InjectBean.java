package org.springframework.tooling.ls.eclipse.commons;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.java.MethodDeclarationCompletionProposal;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PartInitException;
import org.springframework.tooling.jdt.ls.commons.Logger;
import org.springframework.tooling.jdt.ls.commons.resources.ResourceUtils;

@SuppressWarnings({ "restriction", "unchecked" })
public final class InjectBean {

	private final Logger logger;

	private String fieldTypeDeclarationName = null;

	public InjectBean(Logger logger) {
		this.logger = logger;
	}

	private void createFieldDeclaration(IJavaProject project, CompilationUnitRewrite cuRewrite, CompilationUnit domCu,
			String typeName, String fieldType, String fieldName) throws JavaModelException {
		AST ast= cuRewrite.getAST();
		VariableDeclarationFragment variableDeclarationFragment= ast.newVariableDeclarationFragment();
		variableDeclarationFragment.setName(ast.newSimpleName(fieldName));

		FieldDeclaration fieldDeclaration= ast.newFieldDeclaration(variableDeclarationFragment);

		fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));

		IType itype = project.findType(fieldType);
		String fqn = itype.getFullyQualifiedName();
		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		fieldTypeDeclarationName = importRewrite.addImport(fqn);
		fieldDeclaration.setType(ast.newSimpleType(ast.newName(fieldTypeDeclarationName)));

		AbstractTypeDeclaration parent = ((Stream<AbstractTypeDeclaration>) domCu.types().stream()
				.filter(AbstractTypeDeclaration.class::isInstance)
				.map(AbstractTypeDeclaration.class::cast))
				.filter(td -> typeName.equals(td.getName().getIdentifier()))
				.findFirst()
				.orElse(null);
		Assert.isNotNull(parent);
		ListRewrite listRewrite= cuRewrite.getASTRewrite().getListRewrite(parent, parent.getBodyDeclarationsProperty());
		TextEditGroup msg= cuRewrite.createGroupDescription(RefactoringCoreMessages.ExtractConstantRefactoring_declare_constant);
		listRewrite.insertFirst(fieldDeclaration, msg);
	}

	private void maybeAddConstructor(IJavaProject project, CompilationUnitRewrite cuRewrite, CompilationUnit domCu,
			String typeName, String fieldType, String fieldName) {
		TypeDeclaration typeDom = (TypeDeclaration) ((Stream<AbstractTypeDeclaration>) domCu.types().stream()
				.filter(TypeDeclaration.class::isInstance)
				.map(TypeDeclaration.class::cast))
				.filter(td -> typeName.equals(td.getName().getIdentifier()))
				.findFirst()
				.orElse(null);

		MethodDeclaration constructor = null;
		boolean parameterAdded = false;
		for (MethodDeclaration m : typeDom.getMethods()) {
			if (m.isConstructor()) {
				IMethodBinding methodBinding = m.resolveBinding();
 				if (methodBinding != null) {
					boolean autowired = Arrays.stream(methodBinding.getAnnotations()).anyMatch(a -> "org.springframework.beans.factory.annotation.Autowired".equals(a.getAnnotationType().getQualifiedName()));
					boolean hasParameter = Arrays.stream(methodBinding.getParameterTypes()).anyMatch(t -> fieldType.equals(t.getQualifiedName()));
					if (autowired) {
						constructor = m;
						parameterAdded = hasParameter;
						break;
					} else {
						if (constructor == null && !parameterAdded) {
							constructor = m;
							parameterAdded = hasParameter;
						}
					}
				}
			}
		}

		if (constructor == null) {
			AST ast = domCu.getAST();

			MethodDeclaration newConstructor = ast.newMethodDeclaration();
			newConstructor.setConstructor(true);
			newConstructor.setName(ast.newSimpleName(typeName));
			newConstructor.parameters().add(createVariableDeclaration(ast, fieldName));
			newConstructor.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
			Block block = ast.newBlock();
			block.statements().add(ast.newExpressionStatement(createAssignment(ast, fieldName)));
			newConstructor.setBody(block);

			ListRewrite listRewrite= cuRewrite.getASTRewrite().getListRewrite(typeDom, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			TextEditGroup msg= cuRewrite.createGroupDescription(RefactoringCoreMessages.ExtractConstantRefactoring_declare_constant);
			if (typeDom.getMethods().length == 0) {
				listRewrite.insertLast(newConstructor, msg);
			} else {
				listRewrite.insertBefore(newConstructor, typeDom.getMethods()[0], msg);
			}
		} else {
			AST ast = constructor.getAST();
			SingleVariableDeclaration newParam = createVariableDeclaration(ast, fieldName);

			ListRewrite listRewrite= cuRewrite.getASTRewrite().getListRewrite(constructor, MethodDeclaration.PARAMETERS_PROPERTY);
			TextEditGroup msg= cuRewrite.createGroupDescription(RefactoringCoreMessages.ExtractConstantRefactoring_declare_constant);
			List<SingleVariableDeclaration> parameters = constructor.parameters();
			listRewrite.insertAfter(newParam, parameters.get(parameters.size() - 1), msg);

			Block block = constructor.getBody();
			listRewrite = cuRewrite.getASTRewrite().getListRewrite(block, Block.STATEMENTS_PROPERTY);
			listRewrite.insertLast(ast.newExpressionStatement(createAssignment(ast, fieldName)), msg);
		}
	}

	private SingleVariableDeclaration createVariableDeclaration(AST ast, String fieldName) {
		SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
		newParam.setName(ast.newSimpleName(fieldName));
		newParam.setType(ast.newSimpleType(ast.newSimpleName(fieldTypeDeclarationName)));
		return newParam;
	}

	private Assignment createAssignment(AST ast, String fieldName) {
		Assignment assign = ast.newAssignment();
		assign.setRightHandSide(ast.newSimpleName(fieldName));
		FieldAccess thisField = ast.newFieldAccess();
		thisField.setName(ast.newSimpleName(fieldName));
		thisField.setExpression(ast.newThisExpression());
		assign.setLeftHandSide(thisField);
		return assign;
	}

	public TextDocumentEdit computeEdits(String docUri, String typeName, String fieldType, String fieldName) {
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

				createFieldDeclaration(project, cuRewrite, domCu, typeName, fieldType, fieldName);

				maybeAddConstructor(project, cuRewrite, domCu, typeName, fieldType, fieldName);

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
