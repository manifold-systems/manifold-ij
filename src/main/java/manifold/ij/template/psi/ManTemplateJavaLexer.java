package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.DelegateLexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import java.util.Collections;
import java.util.List;
import manifold.ij.template.IManTemplateOffsets;
import manifold.util.ReflectUtil;

/**
 * The Java produced from a ManTL template file consists of a series of directives, expressions, and statements
 * embedded in text-based content, like HTML, XML, and so forth.  To maintain a common symbol scope we tokenize the
 * concatenation of the series. However, a complication can arise from this strategy when tokens from
 * adjoining expressions form a single token:
 * <pre>
 *   ${a} abc ${b}
 * </pre>
 * The Java source produced from this template is the concatenation of the expressions 'a' and 'b', producing "ab".
 * Thus, instead of tokenizing as separate tokens 'a' and 'b', it produces a single token 'ab', which is wrong.
 * <p/>
 * The purpose of this class is to control the lexer so that it respects the boundaries of the Java components in the
 * concatenated series.  It wraps the JavaLexer so that it does not advance past the next Java component in series
 * for any given token i.e., a token is not allowed to straddle the boundary between two Java components in the series.
 */
class ManTemplateJavaLexer extends DelegateLexer
{
  private List<Integer> _exprOffsets;
  private List<Integer> _stmtOffsets;
  private List<Integer> _directiveOffsets;

  ManTemplateJavaLexer( Project project, ASTNode chameleon )
  {
    super( new JavaLexer( project != null
                          ? LanguageLevelProjectExtension.getInstance( project ).getLanguageLevel()
                          : LanguageLevel.HIGHEST ) );
    assignOffsets( chameleon );

  }

  private JavaLexer getJavaLexerDelegate()
  {
    return (JavaLexer)getDelegate();
  }

  private void assignOffsets( ASTNode chameleon )
  {
    if( chameleon == null )
    {
      _exprOffsets = Collections.emptyList();
      _stmtOffsets = Collections.emptyList();
      _directiveOffsets = Collections.emptyList();
      return;
    }

    ManTemplateJavaFile psiFile = (ManTemplateJavaFile)SharedImplUtil.getContainingFile( chameleon );
    _exprOffsets = psiFile.getUserData( IManTemplateOffsets.EXPR_OFFSETS );
    _exprOffsets = _exprOffsets == null ? Collections.emptyList() : _exprOffsets;
    _stmtOffsets = psiFile.getUserData( IManTemplateOffsets.STMT_OFFSETS );
    _stmtOffsets = _stmtOffsets == null ? Collections.emptyList() : _stmtOffsets;
    _directiveOffsets = psiFile.getUserData( IManTemplateOffsets.DIRECTIVE_OFFSETS );
    _directiveOffsets = _directiveOffsets == null ? Collections.emptyList() : _directiveOffsets;
  }

  public List<Integer> getExprOffsets()
  {
    return _exprOffsets;
  }

  public List<Integer> getStmtOffsets()
  {
    return _stmtOffsets;
  }

  public List<Integer> getDirectiveOffsets()
  {
    return _directiveOffsets;
  }

  @Override
  public void advance()
  {
    updateCodeBoundary();
    super.advance();
  }

  private void updateCodeBoundary()
  {
    JavaLexer delegate = getJavaLexerDelegate();
    int pos = delegate.getTokenEnd();
    int next = findNextOffset( pos, getDelegate().getBufferEnd(), _exprOffsets, _stmtOffsets, _directiveOffsets );
    if( next > 0 )
    {
      ReflectUtil.field( ReflectUtil.field( getJavaLexerDelegate(), "myFlexLexer" ).get(), "zzEndRead" ).set( next );
    }
  }

  @SafeVarargs
  public static int findNextOffset( int pos, int end, List<Integer>... lists )
  {
    int next = findNextOffset( lists[0], pos, end );
    for( int i = 1; i < lists.length; i++ )
    {
      int temp = findNextOffset( lists[i], pos, end );
      next = better( next, temp );
    }
    return next;
  }

  private static int better( int next, int temp )
  {
    if( next < 0 || temp > 0 && temp < next )
    {
      next = temp;
    }
    return next;
  }

  private static int findNextOffset( List<Integer> list, int pos, int end )
  {
    if( list.isEmpty() )
    {
      return -1;
    }

    int low = 0;
    int high = list.size() - 1;

    while( low <= high )
    {
      int mid = (low + high) >>> 1;
      int midVal = list.get( mid );
      if( midVal <= pos )
      {
        low = mid + 1;
      }
      else
      {
        high = mid - 1;
      }
    }
    if( list.size() <= low )
    {
      return end;
    }
    return list.get( low );
  }
}
