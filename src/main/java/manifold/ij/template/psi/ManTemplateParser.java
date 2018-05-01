package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;


public class ManTemplateParser implements PsiParser
{
  @NotNull
  @Override
  public ASTNode parse( @NotNull IElementType root, @NotNull PsiBuilder builder )
  {
    builder.setDebugMode( ApplicationManager.getApplication().isUnitTestMode() );

    PsiBuilder.Marker rootMarker = builder.mark();
    PsiBuilder.Marker marker = builder.mark();
    IElementType tokenType = builder.getTokenType();
    while( !builder.eof() && tokenType != null )
    {
      builder.advanceLexer();
      tokenType = builder.getTokenType();
    }
    marker.done( ManTemplateElementType.ALL );
    rootMarker.done( root );

    return builder.getTreeBuilt();
  }
}
