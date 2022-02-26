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

package manifold.ij.template;

import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;


import static manifold.ij.template.IManTemplateOffsets.DIRECTIVE_OFFSETS;
import static manifold.ij.template.IManTemplateOffsets.EXPR_OFFSETS;
import static manifold.ij.template.IManTemplateOffsets.STMT_OFFSETS;
import static manifold.ij.template.psi.ManTemplateTokenType.EXPR;
import static manifold.ij.template.psi.ManTemplateTokenType.STMT;
import static manifold.ij.template.psi.ManTemplateTokenType.DIRECTIVE;

/**
 * Extends {@link TemplateDataElementType} to support _multiple_ token types, STMT and EXPR, as template data tokens.
 * This is to support keeping track of expression offsets to help the parser differentiate between stmt and expr parsing.
 *
 * @see manifold.ij.template.psi.ManTemplateJavaParser
 */
public class ManTemplateDataElementType extends TemplateDataElementType
{
  ManTemplateDataElementType( String name, Language lang, IElementType contentElementType )
  {
    super( name, lang, STMT, contentElementType );
  }

  @Override
  protected CharSequence createTemplateText( @NotNull CharSequence sourceCode, @NotNull Lexer baseLexer, @NotNull RangeCollector outerRangesCollector )
  {
    throw new IllegalStateException( "Should be calling private createTemplateText overload instead" );
  }

  @Override
  protected Language getTemplateFileLanguage( TemplateLanguageFileViewProvider viewProvider )
  {
    return ManTemplateJavaLanguage.INSTANCE;
  }

  // must override this for "old" deprecated behavior
  protected void appendCurrentTemplateToken(@NotNull StringBuilder result,
                                            @NotNull CharSequence buf,
                                            @NotNull Lexer lexer,
                                            @NotNull TemplateDataElementType.RangeCollector collector) {
    result.append(buf, lexer.getTokenStart(), lexer.getTokenEnd());
  }

  private CharSequence createTemplateText( @NotNull CharSequence sourceCode,
                                           @NotNull Lexer baseLexer,
                                           @NotNull RangeCollector outerRangesCollector,
                                           List<Integer> expressionOffsets,
                                           List<Integer> statementOffsets,
                                           List<Integer> directiveOffsets )
  {
    StringBuilder result = new StringBuilder( sourceCode.length() );
    baseLexer.start( sourceCode );

    TextRange currentRange = TextRange.EMPTY_RANGE;
    while( baseLexer.getTokenType() != null )
    {
      TextRange newRange = TextRange.create( baseLexer.getTokenStart(), baseLexer.getTokenEnd() );
      assert currentRange.getEndOffset() == newRange.getStartOffset() :
        "Inconsistent tokens stream from " + baseLexer +
        ": " + getRangeDump( currentRange, sourceCode ) + " followed by " + getRangeDump( newRange, sourceCode );
      currentRange = newRange;
      IElementType tokenType = baseLexer.getTokenType();
      if( tokenType == STMT || tokenType == EXPR || tokenType == DIRECTIVE )
      {
        int offset = result.length();
        appendCurrentTemplateToken( result, sourceCode, baseLexer, outerRangesCollector );
        if( tokenType == EXPR )
        {
          expressionOffsets.add( offsetNoWhitespace( result, offset ) );
        }
        else if( tokenType == STMT )
        {
          statementOffsets.add( offsetNoWhitespace( result, offset ) );
        }
        else // DIRECTIVE
        {
          directiveOffsets.add( offsetNoWhitespace( result, offset ) );
        }
      }
      else
      {
        outerRangesCollector.addOuterRange( currentRange );
      }
      baseLexer.advance();
    }

    return result;
  }

  private Integer offsetNoWhitespace( StringBuilder result, int offset )
  {
    while( result.length() > offset && Character.isWhitespace( result.charAt( offset ) ) )
    {
      offset++;
    }
    return offset;
  }

  protected PsiFile createTemplateFile( final PsiFile psiFile,
                                        final Language templateLanguage,
                                        final CharSequence sourceCode,
                                        final TemplateLanguageFileViewProvider viewProvider,
                                        @NotNull RangeCollector outerRangesCollector )
  {
    List<Integer> expressionOffsets = new ArrayList<>();
    List<Integer> statementOffsets = new ArrayList<>();
    List<Integer> directiveOffsets = new ArrayList<>();
    CharSequence templateSourceCode = createTemplateText( sourceCode, createBaseLexer( viewProvider ), outerRangesCollector, expressionOffsets, statementOffsets, directiveOffsets );
    PsiFile file = createPsiFileFromSource( templateLanguage, templateSourceCode, psiFile.getManager() );
    file.putUserData( EXPR_OFFSETS, expressionOffsets );
    file.putUserData( STMT_OFFSETS, statementOffsets );
    file.putUserData( DIRECTIVE_OFFSETS, directiveOffsets );
    return file;
  }

  @NotNull
  private static String getRangeDump( @NotNull TextRange range, @NotNull CharSequence sequence )
  {
    return "'" + StringUtil.escapeLineBreak( range.subSequence( sequence ).toString() ) + "' " + range;
  }

}
