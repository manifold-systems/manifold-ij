package manifold.ij.template;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class ManTemplateTypedHandler extends TypedHandlerDelegate
{
  @NotNull
  @Override
  public Result charTyped( char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file )
  {
    int offset = editor.getCaretModel().getOffset();
    FileViewProvider provider = file.getViewProvider();

    if( !provider.getBaseLanguage().isKindOf( ManTemplateLanguage.INSTANCE ) )
    {
      return Result.CONTINUE;
    }

    int docLength = editor.getDocument().getTextLength();
    if( offset < 2 || offset > docLength )
    {
      return Result.CONTINUE;
    }

    String previousChar = editor.getDocument().getText( new TextRange( offset - 2, offset - 1 ) );
    String nextChar = docLength > offset+1
                      ? editor.getDocument().getText( new TextRange( offset, offset + 1 ) )
                      : "";

    if( c == '%' && previousChar.equals( "<" ) && !nextChar.equalsIgnoreCase( "%" ) )
    {
      editor.getDocument().insertString( offset, "%>" );
    }
    else if( c == '{' && previousChar.equals( "$" ) && !nextChar.equalsIgnoreCase( "}" ) )
    {
      editor.getDocument().insertString( offset, "}" );
    }

    return Result.CONTINUE;
  }
}