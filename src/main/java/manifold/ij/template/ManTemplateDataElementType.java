package manifold.ij.template;

import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;


import static manifold.ij.template.psi.ManTemplateTokenType.EXPR;
import static manifold.ij.template.psi.ManTemplateTokenType.STMT;

/**
 * Extends {@link TemplateDataElementType} to support _multiple_ token types, STMT and EXPR, as template data tokens.
 * This is to support keeping track of expression offsets to help the parser differentiate between stmt and expr parsing.
 *
 * @see manifold.ij.template.psi.ManTemplateJavaParser
 */
public class ManTemplateDataElementType extends TemplateDataElementType
{
  public static final Key<List<Integer>> EXPR_OFFSETS = Key.create( "EXPR_OFFSETS" );

  ManTemplateDataElementType( String name, Language lang, IElementType contentElementType )
  {
    super( name, lang, STMT, contentElementType );
  }

  @Override
  protected CharSequence createTemplateText( @NotNull CharSequence sourceCode, @NotNull Lexer baseLexer, @NotNull RangesCollector outerRangesCollector )
  {
    throw new IllegalStateException( "Should be calling private createTemplateText overload instead" );
  }

  private CharSequence createTemplateText( @NotNull CharSequence sourceCode,
                                           @NotNull Lexer baseLexer,
                                           @NotNull RangesCollector outerRangesCollector,
                                           List<Integer> expressionOffsets )
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
      if( tokenType == STMT || tokenType == EXPR )
      {
        int offset = result.length();
        appendCurrentTemplateToken( result, sourceCode, baseLexer );
        if( tokenType == EXPR )
        {
          expressionOffsets.add( offsetNoWhitespace( result, offset ) );
        }
      }
      else
      {
        outerRangesCollector.addRange( currentRange );
      }
      baseLexer.advance();
    }

    return result;
  }

  private Integer offsetNoWhitespace( StringBuilder result, int offset )
  {
    while( Character.isWhitespace( result.charAt( offset ) ) )
    {
      offset++;
    }
    return offset;
  }

  protected PsiFile createTemplateFile( final PsiFile psiFile,
                                        final Language templateLanguage,
                                        final CharSequence sourceCode,
                                        final TemplateLanguageFileViewProvider viewProvider,
                                        @NotNull RangesCollector outerRangesCollector )
  {
    List<Integer> expressionOffsets = new ArrayList<>();
    CharSequence templateSourceCode = createTemplateText( sourceCode, createBaseLexer( viewProvider ), outerRangesCollector, expressionOffsets );
    PsiFile file = createPsiFileFromSource( templateLanguage, templateSourceCode, psiFile.getManager() );
    file.putUserData( EXPR_OFFSETS, expressionOffsets );
    return file;
  }

  @NotNull
  private static String getRangeDump( @NotNull TextRange range, @NotNull CharSequence sequence )
  {
    return "'" + StringUtil.escapeLineBreak( range.subSequence( sequence ).toString() ) + "' " + range;
  }

}
