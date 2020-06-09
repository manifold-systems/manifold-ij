package manifold.ij.extensions;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

import manifold.api.type.SourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 */
public class ManRenameHandler extends VariableInplaceRenameHandler
{
  protected boolean isAvailable(@Nullable PsiElement element,
                                @NotNull Editor editor,
                                @NotNull PsiFile file) {
    PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());

    if( nameSuggestionContext == null )
    {
      return false;
    }

    if (element == null && LookupManager.getActiveLookup(editor) != null) {
      element = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement.class);
    }
    final RefactoringSupportProvider
      supportProvider = element == null ? null : LanguageRefactoringSupport.INSTANCE.forLanguage(element.getLanguage());
    boolean langRefactorSupport = editor.getSettings().isVariableInplaceRenameEnabled()
                && supportProvider != null
                && element instanceof PsiNameIdentifierOwner
                && supportProvider.isMemberInplaceRenameAvailable( element, nameSuggestionContext );

    if( element instanceof PsiModifierListOwner )
    {
      PsiAnnotation sourcePosAnnotation = Arrays.stream( ((PsiModifierListOwner) element).getAnnotations() )
        .filter( anno -> Objects.equals( anno.getQualifiedName(), SourcePosition.class.getName() ) )
        .findFirst().orElse( null );
      if( sourcePosAnnotation == null )
      {
        return false;
      }
    }

    if( langRefactorSupport )
    {
      return true;
    }

    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement( nameSuggestionContext );
    return processor instanceof RenameResourceElementProcessor;
  }

  @Override
  public InplaceRefactoring doRename( @NotNull PsiElement elementToRename, Editor editor, DataContext dataContext )
  {
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);

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

  @Override
  public String toString()
  {
    // Displayed in chooser dialog when multiple rename handlers are available
    return "Rename item and code usages (Manifold)";
  }
}
