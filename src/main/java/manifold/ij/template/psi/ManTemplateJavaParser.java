package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.tree.IElementType;
import java.util.Collections;
import java.util.List;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.core.ManExpressionParser;
import manifold.ij.template.IManTemplateOffsets;
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
    @Jailbreak JavaParser javaParser = new JavaParser();
    ExpressionParser exprParser = javaParser.getExpressionParser();
    List<Integer> exprOffsets = getExpressionOffsets( builder );
    List<Integer> directiveOffsets = getDirectiveOffsets( builder );
    MyStatementParser stmtParser = new MyStatementParser( javaParser, this, exprOffsets, directiveOffsets );
    javaParser.myStatementParser = stmtParser;
    javaParser.myExpressionParser = new ManExpressionParser( javaParser ); // for binding expressions
    while( !builder.eof() )
    {
      int offset = builder.getCurrentOffset();
      if( directiveOffsets.contains( offset ) )
      {
        // Parse directive
        parseDirective( builder, offset );
      }
      else if( exprOffsets.contains( offset ) )
      {
        // Parse single expression
        parseExpression( builder, exprParser, offset );
      }
      else
      {
        // Parse single statement
        parseStatement( builder, stmtParser, offset );
      }
    }
    rootMarker.done( root );
    return builder.getTreeBuilt();
  }

  private void parseStatement( @NotNull PsiBuilder builder, MyStatementParser stmtParser, int offset )
  {
    stmtParser.parseStatement( builder );
    handleFailure( builder, offset, "Java statement expected" );
  }

  public void parseExpression( @NotNull PsiBuilder builder, ExpressionParser exprParser, int offset )
  {
    exprParser.parse( builder );
    handleFailure( builder, offset, "Java expression expected" );
  }

  public void parseDirective( @NotNull PsiBuilder builder, int offset )
  {
    DirectiveParser.instance().parse( builder );
    handleFailure( builder, offset, "Template directive expected" );
  }

  private void setLanguageLevel( @NotNull PsiBuilder builder )
  {
    LanguageLevel languageLevel = LanguageLevelProjectExtension.getInstance( builder.getProject() ).getLanguageLevel();
    JavaParserUtil.setLanguageLevel( builder, languageLevel );
  }

  private void handleFailure( @NotNull PsiBuilder builder, int offset, String errorMsg )
  {
    if( builder.getCurrentOffset() == offset )
    {
      builder.error( errorMsg );
      // blow past whatever remaining tokens until first token of next expr/stmt/directive
      eatRemainingTokensInCodeSegment( builder );
    }

  }

  private void eatRemainingTokensInCodeSegment( @NotNull PsiBuilder builder )
  {
    int currentOffset = builder.getCurrentOffset();
    int next = ManTemplateJavaLexer.findNextOffset( currentOffset, builder.getOriginalText().length(), getExpressionOffsets( builder ), getStatementOffsets( builder ), getDirectiveOffsets( builder ) );
    while( currentOffset < next )
    {
      builder.advanceLexer();
      currentOffset = builder.getCurrentOffset();
    }
  }

  private List<Integer> getExpressionOffsets( @NotNull PsiBuilder builder )
  {
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( IManTemplateOffsets.EXPR_OFFSETS );
  }

  private List<Integer> getDirectiveOffsets( @NotNull PsiBuilder builder )
  {
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( IManTemplateOffsets.DIRECTIVE_OFFSETS );
  }

  private List<Integer> getStatementOffsets( @NotNull PsiBuilder builder )
  {
    PsiFile psiFile = builder.getUserDataUnprotected( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( IManTemplateOffsets.STMT_OFFSETS );
  }
}
