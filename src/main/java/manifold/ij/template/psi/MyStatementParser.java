package manifold.ij.template.psi;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.StatementParser;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MyStatementParser extends StatementParser
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
        _manParser.parseExpression( builder, _javaParser.getExpressionParser(), offset );
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

  private boolean isTemplateExpression( int tokenStart )
  {
    return _exprOffsets.contains( tokenStart );
  }

  private boolean isTemplateDirective( int tokenStart )
  {
    return _directiveOffsets.contains( tokenStart );
  }
}
