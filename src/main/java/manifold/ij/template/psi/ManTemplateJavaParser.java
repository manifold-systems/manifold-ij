package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.StatementParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.tree.IElementType;
import java.util.List;
import manifold.ij.template.ManTemplateDataElementType;
import org.jetbrains.annotations.NotNull;

public class ManTemplateJavaParser implements PsiParser
{
  @NotNull
  @Override
  public ASTNode parse( @NotNull IElementType root, @NotNull PsiBuilder builder )
  {
    builder.setDebugMode( ApplicationManager.getApplication().isUnitTestMode() );
    JavaParserUtil.setLanguageLevel( builder, LanguageLevelProjectExtension.getInstance( builder.getProject() ).getLanguageLevel() );
    PsiBuilder.Marker rootMarker = builder.mark();
    StatementParser stmtParser = new JavaParser().getStatementParser();
    ExpressionParser exprParser = new JavaParser().getExpressionParser();
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    List<Integer> exprOffsets = psiFile.getUserData( ManTemplateDataElementType.EXPR_OFFSETS );
    while( !builder.eof() )
    {
      int offset = builder.getCurrentOffset();
      if( exprOffsets.contains( offset ) )
      {
        exprParser.parse( builder );
      }
      else
      {
        stmtParser.parseStatement( builder );
      }
    }
    rootMarker.done( root );
    return builder.getTreeBuilt();
  }
}
