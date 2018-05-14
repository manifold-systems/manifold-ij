package manifold.ij.template.psi;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class ManTemplateEnterHandler extends EnterHandlerDelegateAdapter
{
  public Result preprocessEnter( @NotNull final PsiFile file,
                                 @NotNull final Editor editor,
                                 @NotNull final Ref<Integer> caretOffset,
                                 @NotNull final Ref<Integer> caretAdvance,
                                 @NotNull final DataContext dataContext,
                                 final EditorActionHandler originalHandler )
  {
    // if we are between open and close tags, ensure the caret ends up in the "logical" place on Enter.
    // i.e. "<%<caret>%>" becomes the following on Enter:
    //
    // <%
    // <caret>
    // %>
    //
    // (Note: <caret> may be indented depending on formatter settings.)
    if( (file instanceof ManTemplateFile || file instanceof ManTemplateJavaFile)
        && isBetweenManTags( editor, caretOffset.get() ) )
    {
      originalHandler.execute( editor, editor.getCaretModel().getCurrentCaret(), dataContext );
      return Result.Default;
    }
    return Result.Continue;
  }

  /**
   * Checks to see if {@code Enter} has been typed while the caret is between an open and close tag pair.
   */
  private boolean isBetweenManTags( Editor editor, int offset )
  {
    return offset >= 2 && isOpenTagBefore( editor, offset ) && isCloseTagAfter( editor, offset );
  }

  private boolean isOpenTagBefore( Editor editor, int offset )
  {
    String text = editor.getDocument().getText( new TextRange( 0, offset ) );
    int csr = text.length() - 1;
    while( csr > 0 && isSpaceOrTab( text, csr ) )
    {
      csr--;
    }
    return csr > 0 && text.substring( csr - 1, csr + 1 ).equals( "<%" ) ||
           csr > 1 && text.substring( csr - 2, csr + 1 ).equals( "<%@" ) ||
           csr > 1 && text.substring( csr - 2, csr + 1 ).equals( "<%=" );
  }

  private boolean isCloseTagAfter( Editor editor, int offset )
  {
    String text = editor.getDocument().getText( new TextRange( offset, editor.getDocument().getTextLength() ) );
    int csr = 0;
    while( csr <= text.length()-2 && isSpaceOrTab( text, csr ) )
    {
      csr++;
    }
    return csr <= text.length()-2 && text.substring( csr, csr + 2 ).equals( "%>" );
  }

  private boolean isSpaceOrTab( String text, int csr )
  {
    char c = text.charAt( csr );
    return c == ' ' || c == '\t';
  }
}
