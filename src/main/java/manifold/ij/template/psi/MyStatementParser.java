package manifold.ij.template.psi;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import java.util.List;
import manifold.ij.core.ManStatementParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static com.intellij.lang.java.parser.JavaParserUtil.done;

class MyStatementParser extends ManStatementParser
{
  private JavaParser _javaParser;
  private final ManTemplateJavaParser _manParser;
  private List<Integer> _exprOffsets;
  private final List<Integer> _directiveOffsets;

  MyStatementParser( @NotNull final JavaParser javaParser, ManTemplateJavaParser manParser, List<Integer> exprOffsets, List<Integer> directiveOffsets )
  {
    super( javaParser );
    _javaParser = javaParser;
    _manParser = manParser;
    _exprOffsets = exprOffsets;
    _directiveOffsets = directiveOffsets;
  }

  @Nullable
  public PsiBuilder.Marker parseStatement( final PsiBuilder builder )
  {
    while( true )
    {
      int offset = builder.getCurrentOffset();
      if( isTemplateExpression( offset ) )
      {
        final PsiBuilder.Marker empty = builder.mark();
        _manParser.parseExpression( builder, _javaParser.getExpressionParser(), offset );
        if( offset < builder.getCurrentOffset() )
        {
          // Wrap the expression in an empty statement to fix issues where
          // some IJ quick fixes expect there to be at least one statement in
          // a statement block when the expression is errant (see CreateLocalFromUsageFix#getAnchor)
          done( empty, JavaElementType.EMPTY_STATEMENT );
        }
      }
      else if( isTemplateDirective( offset ) )
      {
        _manParser.parseDirective( builder, offset );
      }
      else
      {
        break;
      }
    }
    return super.parseStatement( builder );
  }

  @Nullable
  @Override
  public PsiBuilder.Marker parseCodeBlock( PsiBuilder builder, boolean isStatement )
  {
    if( builder.getTokenType() != JavaTokenType.LBRACE )
    {
      return null;
    }

    // Always parse the block directly, otherwise for lambdas the block is lazily parsed which screws this up
    // if the block is split with content and expressions:
    // <% list.forEach( e -> { %>
    //   blah blah ${state} blah
    // <% } ) %>
    // because this class's parseStatement() method must handle the expression.
    return parseCodeBlockDeep( builder, false );
  }

  private boolean isTemplateExpression( int tokenStart )
  {
    return _exprOffsets.contains( tokenStart );
  }

  private boolean isTemplateDirective( int tokenStart )
  {
    return _directiveOffsets.contains( tokenStart );
  }
}
