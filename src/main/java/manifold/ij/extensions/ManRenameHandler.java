package manifold.ij.extensions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import java.util.HashSet;
import org.jetbrains.annotations.NotNull;


/**
 * This RenameHandler exists only to work around a bug in IJ where rename substitution is not working properly
 * with the rename processor in dialog mode.
 */
public class ManRenameHandler extends MemberInplaceRenameHandler
{
  @Override
  public InplaceRefactoring doRename( @NotNull PsiElement elementToRename, Editor editor, DataContext dataContext )
  {
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement( elementToRename );
    PsiElement actualElem = processor.substituteElementToRename( elementToRename, editor );
    return super.doRename( actualElem, editor, dataContext );
  }

  public void invoke( @NotNull Project project, Editor editor, PsiFile file, DataContext dataContext )
  {
    PsiElement element = PsiElementRenameHandler.getElement( dataContext );
    if( element == null )
    {
      element = BaseRefactoringAction.getElementAtCaret( editor, file );
    }

    // substitute if necessary
    if( element != null )
    {
      RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement( element );
      element = processor.substituteElementToRename( element, editor );
    }

    if( ApplicationManager.getApplication().isUnitTestMode() )
    {
      final String newName = PsiElementRenameHandler.DEFAULT_NAME.getData( dataContext );
      if( newName != null )
      {
        PsiElementRenameHandler.rename( element, project, element, editor, newName );
        return;
      }
    }

    element = findTargetElement( element );

    editor.getScrollingModel().scrollToCaret( ScrollType.MAKE_VISIBLE );
    final PsiElement nameSuggestionContext = InjectedLanguageUtil.findElementAtNoCommit( file, editor.getCaretModel().getOffset() );
    PsiElementRenameHandler.invoke( element, project, nameSuggestionContext, editor );
  }

  // If renaming from a reference, resolve the reference and use that as the target to rename
  private PsiElement findTargetElement( PsiElement target )
  {
    if( target == null )
    {
      return null;
    }

    PsiFile containingFile = target.getContainingFile();
    PsiElement elemAt = containingFile instanceof PsiPlainTextFile ? target : containingFile.findElementAt( target.getTextOffset() );
    while( elemAt != null && !(elemAt instanceof PsiNamedElement) )
    {
      elemAt = elemAt.getParent();
    }
    if( elemAt != null )
    {
      // Make sure we rename the terminal target e.g., using the "JS GraphQL" plugin a query field is actually a ref to a field def
      elemAt = RenameResourceElementProcessor.getTerminalTarget( elemAt, new HashSet<>() );
    }
    return elemAt;
  }
}
