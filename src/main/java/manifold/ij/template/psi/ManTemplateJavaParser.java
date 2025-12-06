/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.tree.IElementType;
import java.util.Collections;
import java.util.List;
import manifold.ext.rt.api.Jailbreak;
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

    //note, not using ManExpressionParser here because the binding expr stuff blows up with adjacdent expressions in templates
    javaParser.myExpressionParser = new ExpressionParser( javaParser ); //new ManExpressionParser( javaParser, true ); // for binding expressions

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
    if( builder instanceof PsiBuilderImpl )
    {
      Lexer lexer = ((PsiBuilderImpl)builder).getLexer();
      if( lexer instanceof ManTemplateJavaLexer )
      {
        return ((ManTemplateJavaLexer)lexer).getExprOffsets();
      }
    }

    PsiFile psiFile = builder.getUserData( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( IManTemplateOffsets.EXPR_OFFSETS );
  }

  private List<Integer> getDirectiveOffsets( @NotNull PsiBuilder builder )
  {
    if( builder instanceof PsiBuilderImpl )
    {
      Lexer lexer = ((PsiBuilderImpl)builder).getLexer();
      if( lexer instanceof ManTemplateJavaLexer )
      {
        return ((ManTemplateJavaLexer)lexer).getDirectiveOffsets();
      }
    }

    PsiFile psiFile = builder.getUserData( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( IManTemplateOffsets.DIRECTIVE_OFFSETS );
  }

  private List<Integer> getStatementOffsets( @NotNull PsiBuilder builder )
  {
    if( builder instanceof PsiBuilderImpl )
    {
      Lexer lexer = ((PsiBuilderImpl)builder).getLexer();
      if( lexer instanceof ManTemplateJavaLexer )
      {
        return ((ManTemplateJavaLexer)lexer).getStmtOffsets();
      }
    }

    PsiFile psiFile = builder.getUserData( FileContextUtil.CONTAINING_FILE_KEY );
    return psiFile == null ? Collections.emptyList() : psiFile.getUserData( IManTemplateOffsets.STMT_OFFSETS );
  }
}
