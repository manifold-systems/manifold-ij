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
    JavaParserUtil.setParseStatementCodeBlocksDeep( builder, true );
    setLanguageLevel( builder );
    PsiBuilder.Marker rootMarker = builder.mark();
    ExpressionParser exprParser = new JavaParser().getExpressionParser();
    List<Integer> exprOffsets = getExpressionOffsets( builder );
    List<Integer> directiveOffsets = getDirectiveOffsets( builder );
    MyStatementParser stmtParser = new MyStatementParser( new JavaParser(), this, exprOffsets, directiveOffsets );
    while( !builder.eof() )
    {
      int offset = builder.getCurrentOffset();
      if( directiveOffsets.contains( offset ) )
      {
        // Parse directive

        if( !parseDirective( builder, stmtParser, offset ) )
        {
          break;
        }
      }
      else if( exprOffsets.contains( offset ) )
      {
        // Parse single expression

        if( !parseExpression( builder, stmtParser, exprParser, offset ) )
        {
          break;
        }
      }
      else
      {
        // Parse single statement

        if( !parseStatement( builder, stmtParser, offset ) )
        {
          break;
        }
      }
    }
    rootMarker.done( root );
    return builder.getTreeBuilt();
  }

  private boolean parseStatement( @NotNull PsiBuilder builder, MyStatementParser stmtParser, int offset )
  {
    stmtParser.parseStatement( builder );
    return !handleFailure( builder, stmtParser, offset, "Java statement expected" );
  }

  public boolean parseExpression( @NotNull PsiBuilder builder, MyStatementParser stmtParser, ExpressionParser exprParser, int offset )
  {
    exprParser.parse( builder );
    return !handleFailure( builder, stmtParser, offset, "Java expression expected" );
  }

  public boolean parseDirective( @NotNull PsiBuilder builder, StatementParser stmtParser, int offset )
  {
    DirectiveParser.instance().parse( builder );
    return !handleFailure( builder, stmtParser, offset, "Template directive expected" );
  }

  private void setLanguageLevel( @NotNull PsiBuilder builder )
  {
    LanguageLevel languageLevel = LanguageLevelProjectExtension.getInstance( builder.getProject() ).getLanguageLevel();
    JavaParserUtil.setLanguageLevel( builder, languageLevel );
  }

  private boolean handleFailure( @NotNull PsiBuilder builder, StatementParser stmtParser, int offset, String errorMsg )
  {
    if( couldNotParse( builder, offset ) )
    {
      // Force parsing by calling parseStatements(), it will blow past whatever
      // is not a start symbol for both expressions and statements.
      stmtParser.parseStatements( builder );
      builder.error( errorMsg );
      return couldNotParse( builder, offset );
    }
    return false;
  }

  private List<Integer> getExpressionOffsets( @NotNull PsiBuilder builder )
  {
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( ManTemplateDataElementType.EXPR_OFFSETS );
  }

  private List<Integer> getDirectiveOffsets( @NotNull PsiBuilder builder )
  {
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( ManTemplateDataElementType.DIRECTIVE_OFFSETS );
  }

  private List<Integer> getStatementOffsets( @NotNull PsiBuilder builder )
  {
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( ManTemplateDataElementType.STMT_OFFSETS );
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
