package manifold.ij.extensions;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreprocessorEnterHandler implements EnterHandlerDelegate
{
  @Override
  public Result preprocessEnter( @NotNull PsiFile file, @NotNull Editor editor,
                                 @NotNull Ref<Integer> caretOffset,
                                 @NotNull Ref<Integer> caretAdvance,
                                 @NotNull DataContext dataContext,
                                 @Nullable EditorActionHandler originalHandler )
  {
    if( !(file instanceof PsiJavaFile) )
    {
      return Result.Continue;
    }
    if( isInMaskedRegion( editor, caretOffset.get() ) )
    {
      return Result.Default;
    }
    return Result.Continue;
  }

  private boolean isInMaskedRegion( Editor editor, int offset )
  {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(
      editor.getDocument(), editor.getProject(), false );
    if( !(markupModel instanceof MarkupModelEx mmx) )
    {
      return false;
    }

    if( isAfterEndIf( editor, offset ) )
    {
      return true;
    }

    boolean[] found = {false};
    mmx.processRangeHighlightersOverlappingWith( offset + 1, offset + 1, highlighter -> {
      if( ManColorSettingsPage.PREPROCESSOR_MASKED_CODE.equals( highlighter.getTextAttributesKey() ) )
      {
        found[0] = true;
        return false;
      }
      return true;
    } );
    return found[0];
  }

  private boolean isAfterEndIf( Editor editor, int offset )
  {
    String endif = "#endif";
    if( offset < endif.length() )
    {
      return false;
    }
    int csr = offset;
    while( csr > 5 ) // remove whitespace in increments of 5 and check for "#endif"
    {
      String text = editor.getDocument().getText( TextRange.create( csr - 5, csr ) );
      String trim = text.trim();
      if( trim.isEmpty() )
      {
        csr -= 5;
        continue;
      }
      int spaces = text.length() - trim.length();
      offset = csr - spaces;
      text = editor.getDocument().getText( TextRange.create( offset - endif.length(), offset ) );
      return endif.equals( text );
    }

    return false;
  }

}
