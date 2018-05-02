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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.tree.IElementType;
import java.util.Collections;
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
    setLanguageLevel( builder );
    PsiBuilder.Marker rootMarker = builder.mark();
    StatementParser stmtParser = new JavaParser().getStatementParser();
    ExpressionParser exprParser = new JavaParser().getExpressionParser();
    List<Integer> exprOffsets = getExpressionOffsets( builder );
    while( !builder.eof() )
    {
      int offset = builder.getCurrentOffset();
      if( exprOffsets.contains( offset ) )
      {
        // Parse single expression

        exprParser.parse( builder );
        if( handleFailure( builder, stmtParser, offset ) )
        {
          break;
        }
      }
      else
      {
        // Parse single statement

        stmtParser.parseStatement( builder );
        if( handleFailure( builder, stmtParser, offset ) )
        {
          break;
        }
      }
    }
    rootMarker.done( root );
    return builder.getTreeBuilt();
  }

  private void setLanguageLevel( @NotNull PsiBuilder builder )
  {
    LanguageLevel languageLevel = LanguageLevelProjectExtension.getInstance( builder.getProject() ).getLanguageLevel();
    JavaParserUtil.setLanguageLevel( builder, languageLevel );
  }

  private boolean handleFailure( @NotNull PsiBuilder builder, StatementParser stmtParser, int offset )
  {
    if( couldNotParse( builder, offset ) )
    {
      // Force parsing by calling parseStatements(), it will blow past whatever
      // is not a start symbol for both expressions and statements.
      stmtParser.parseStatements( builder );

      return couldNotParse( builder, offset );
    }
    return false;
  }

  private List<Integer> getExpressionOffsets( @NotNull PsiBuilder builder )
  {
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( ManTemplateDataElementType.EXPR_OFFSETS );
  }

  private boolean couldNotParse( @NotNull PsiBuilder builder, int offset )
  {
    if( builder.getCurrentOffset() == offset )
    {
      // Whelp, the parser did not advance the lexer, therefore it did nothing.
      builder.error( "unexpected token" );
      return true;
    }
    return false;
  }
}
