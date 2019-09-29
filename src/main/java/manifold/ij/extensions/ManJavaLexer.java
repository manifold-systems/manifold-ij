package manifold.ij.extensions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import manifold.ext.api.Jailbreak;
import manifold.ij.util.ManVersionUtil;
import manifold.ij.util.ReparseUtil;
import manifold.preprocessor.PreprocessorParser;
import manifold.preprocessor.definitions.Definitions;
import manifold.preprocessor.expression.Expression;
import manifold.preprocessor.expression.ExpressionParser;
import manifold.preprocessor.statement.FileStatement;
import manifold.preprocessor.statement.IfStatement;
import manifold.preprocessor.statement.SourceStatement;
import manifold.preprocessor.statement.Statement;
import manifold.util.ReflectUtil;
import manifold.util.concurrent.LocklessLazyVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.preprocessor.TokenType.*;

/**
 * Replaces the JavaLexer to handle Preprocessor tokens (that start with '#').
 */
public class ManJavaLexer extends LexerBase
{
  private static final IElementType TEXT_BLOCK_LITERAL =
    ManVersionUtil.is2019_2_orGreater()
    ? (IElementType)ReflectUtil.field( JavaTokenType.class, "TEXT_BLOCK_LITERAL" ).getStatic()
    : null;

  private static final String MANIFOLD_PREPROCESSOR_DUMB_MODE = "manifold.preprocessor.dumb_mode";
  private static final LocklessLazyVar<boolean[]> PREPROCESSOR_DUMB_MODE = LocklessLazyVar.make( () ->
    new boolean[] {PropertiesComponent.getInstance().getBoolean( MANIFOLD_PREPROCESSOR_DUMB_MODE )} );

  private final com.intellij.lang.java.lexer.@Jailbreak _JavaLexer _flexLexer;
  private CharSequence _buffer;
  private @Nullable char[] _bufferArray;
  private int _bufferIndex;
  private int _bufferEndOffset;
  private int _tokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType _tokenType;
  private PsiJavaFile _psiFile;
  private final List<SourceStatement> _visibleStmts = new ArrayList<>();
  private final LocklessLazyVar<Definitions> _definitions = LocklessLazyVar.make( () -> {
    Definitions definitions = makeDefinitions();
    if( _psiFile != null )
    {
      CharSequence text = _buffer.length() < _psiFile.getTextLength() ? _psiFile.getText() : _buffer;
      if( StringUtil.contains( text, "#define" ) || StringUtil.contains( text, "#undef" ) )
      {
        FileStatement fileStmt = new PreprocessorParser( text, null ).parseFile();
        fileStmt.execute( new ArrayList<>(), true, definitions );
      }
    }
    return definitions;
  } );

  static boolean isDumbPreprocessorMode()
  {
    return PREPROCESSOR_DUMB_MODE.get()[0];
  }
  static void setDumbPreprocessorMode( boolean dumbMode )
  {
    PREPROCESSOR_DUMB_MODE.get()[0] = dumbMode;
    PropertiesComponent.getInstance().setValue( MANIFOLD_PREPROCESSOR_DUMB_MODE, dumbMode );
    ReparseUtil.reparseOpenJavaFilesForAllProjects();
  }

  @NotNull
  private ManDefinitions makeDefinitions()
  {
    return new ManDefinitions( _psiFile );
  }

  public ManJavaLexer( @NotNull LanguageLevel level )
  {
    _flexLexer = new com.intellij.lang.java.lexer.@Jailbreak _JavaLexer( level );
  }

  public ManJavaLexer( @Jailbreak JavaLexer lexer )
  {
    _flexLexer = lexer.myFlexLexer;
  }

  public void setChameleon( ASTNode chameleon )
  {
    _psiFile = ManPsiBuilderFactoryImpl.getPsiFile( chameleon );
  }

  @Override
  public final void start( @NotNull CharSequence buffer, int startOffset, int endOffset, int initialState )
  {
    _visibleStmts.clear();
    _definitions.clear();
    _buffer = buffer;
    _bufferArray = CharArrayUtil.fromSequenceWithoutCopying( buffer );
    _bufferIndex = startOffset;
    _bufferEndOffset = endOffset;
    _tokenType = null;
    _tokenEndOffset = startOffset;
    _flexLexer.reset( _buffer, startOffset, endOffset, 0 );
  }

  @Override
  public int getState()
  {
    return 0;
  }

  @Override
  public final IElementType getTokenType()
  {
    locateToken();
    return _tokenType;
  }

  @Override
  public final int getTokenStart()
  {
    return _bufferIndex;
  }

  @Override
  public final int getTokenEnd()
  {
    locateToken();
    return _tokenEndOffset;
  }

  @Override
  public final void advance()
  {
    locateToken();
    _tokenType = null;
  }

  private void locateToken()
  {
    if( _tokenType != null )
    {
      return;
    }

    if( _tokenEndOffset == _bufferEndOffset )
    {
      _bufferIndex = _bufferEndOffset;
      return;
    }

    _bufferIndex = _tokenEndOffset;

    char c = charAt( _bufferIndex );
    switch( c )
    {
      case ' ':
      case '\t':
      case '\n':
      case '\r':
      case '\f':
        _tokenType = TokenType.WHITE_SPACE;
        _tokenEndOffset = getWhitespaces( _bufferIndex + 1 );
        break;

      case '/':
        if( _bufferIndex + 1 >= _bufferEndOffset )
        {
          _tokenType = JavaTokenType.DIV;
          _tokenEndOffset = _bufferEndOffset;
        }
        else
        {
          char nextChar = charAt( _bufferIndex + 1 );
          if( nextChar == '/' )
          {
            _tokenType = JavaTokenType.END_OF_LINE_COMMENT;
            _tokenEndOffset = getLineTerminator( _bufferIndex + 2 );
          }
          else if( nextChar == '*' )
          {
            if( _bufferIndex + 2 >= _bufferEndOffset ||
                (charAt( _bufferIndex + 2 )) != '*' ||
                (_bufferIndex + 3 < _bufferEndOffset &&
                 (charAt( _bufferIndex + 3 )) == '/') )
            {
              _tokenType = JavaTokenType.C_STYLE_COMMENT;
              _tokenEndOffset = getClosingComment( _bufferIndex + 2 );
            }
            else
            {
              _tokenType = JavaDocElementType.DOC_COMMENT;
              _tokenEndOffset = getClosingComment( _bufferIndex + 3 );
            }
          }
          else
          {
            flexLocateToken();
          }
        }
        break;

      case '\'':
        _tokenType = JavaTokenType.CHARACTER_LITERAL;
        _tokenEndOffset = getClosingQuote( _bufferIndex + 1, c );
        break;

      case '"':
        if( TEXT_BLOCK_LITERAL != null && // non-null if IJ >= 2019.2
            _bufferIndex + 2 < _bufferEndOffset &&
            charAt( _bufferIndex + 2 ) == '"' && charAt( _bufferIndex + 1 ) == '"' )
        {
          _tokenType = TEXT_BLOCK_LITERAL;
          _tokenEndOffset = getTextBlockEnd( _bufferIndex + 2 );
        }
        else
        {
          _tokenType = JavaTokenType.STRING_LITERAL;
          _tokenEndOffset = getClosingQuote( _bufferIndex + 1, c );
        }
        break;
//
//      case '`':
//        _tokenType = JavaTokenType.RAW_STRING_LITERAL;
//        _tokenEndOffset = getRawLiteralEnd( _bufferIndex );
//        break;

      case '#':
      {
        makeDirective();
        break;
      }

      default:
        flexLocateToken();
    }

    if( _tokenEndOffset > _bufferEndOffset )
    {
      _tokenEndOffset = _bufferEndOffset;
    }
  }

  private void makeDirective()
  {
    if( isDumbPreprocessorMode() )
    {
      makeDumbDirective();
    }
    else
    {
      makeSmartDirective();
    }
  }
  private void makeDumbDirective()
  {
    int offset = _bufferIndex + 1;
    String directive;
    if( match( directive = Define.getDirective(), offset ) ||
        match( directive = Undef.getDirective(), offset ) ||
        match( directive = If.getDirective(), offset ) ||
        match( directive = Elif.getDirective(), offset )||
        match( directive = Error.getDirective(), offset ) ||
        match( directive = Warning.getDirective(), offset ) )
    {
      offset = skipSpaces( offset + directive.length() );
      Expression expr = new ExpressionParser( _buffer, offset, _bufferEndOffset ).parse();

      _tokenType = JavaTokenType.C_STYLE_COMMENT;
      _tokenEndOffset = expr.getEndOffset();
    }
    else if( match( directive = Else.getDirective(), offset ) ||
             match( directive = Endif.getDirective(), offset ) )
    {
      _tokenType = JavaTokenType.C_STYLE_COMMENT;
      _tokenEndOffset = offset + directive.length();
    }
    else
    {
      flexLocateToken();
    }
  }
  private void makeSmartDirective()
  {
    int offset = _bufferIndex + 1;
    if( match( Elif.getDirective(), offset ) ||
        match( Else.getDirective(), offset ) )
    {
      _tokenType = JavaTokenType.C_STYLE_COMMENT;
      _tokenEndOffset = findCommentRangeEnd( false );
    }
    else if( match( Endif.getDirective(), offset ) )
    {
      _tokenType = JavaTokenType.C_STYLE_COMMENT;
      _tokenEndOffset = offset + Endif.getDirective().length();
    }
    else if( match( If.getDirective(), offset ) )
    {
      int rangeEnd = findCommentRangeEnd( true );
      if( rangeEnd > 0 )
      {
        // handle nested `#if`
        _tokenType = JavaTokenType.C_STYLE_COMMENT;
        _tokenEndOffset = rangeEnd;
      }
      else
      {
        // Create a PreprocessorParser with position set to '#' char, then call parseStatement()
        // Then use that to figure out what is a comment and what is not :)
        PreprocessorParser preProc = new PreprocessorParser( _buffer, _bufferIndex, _bufferEndOffset, null );
        IfStatement statement = (IfStatement)preProc.parseStatement();

        // note we always parse the toplevel #if, and nested #ifs are parsed with it.  since the lexer tokenizes
        // the whole #if statement, we only ever have just one list of visible stmts to manage
        _visibleStmts.clear();
        statement.execute( _visibleStmts, true, _definitions.get() );
        // add empty statement marking end of if-stmt
        _visibleStmts.add( new SourceStatement( null, statement.getTokenEnd(), statement.getTokenEnd() ) );

        _tokenType = JavaTokenType.C_STYLE_COMMENT;
        _tokenEndOffset = findCommentRangeEnd( false );
      }
    }
    else if( match( Define.getDirective(), offset ) ||
             match( Undef.getDirective(), offset ) )
    {
      PreprocessorParser preProc = new PreprocessorParser( _buffer, _bufferIndex, _bufferEndOffset, null );
      Statement statement = preProc.parseStatement();

      statement.execute( new ArrayList<>(), true, _definitions.get() );

      _tokenType = JavaTokenType.C_STYLE_COMMENT;
      _tokenEndOffset = statement.getTokenEnd();
    }
    else if( match( Error.getDirective(), offset ) ||
             match( Warning.getDirective(), offset ) )
    {
      PreprocessorParser preProc = new PreprocessorParser( _buffer, _bufferIndex, _bufferEndOffset, null );
      Statement statement = preProc.parseStatement();
      _tokenType = JavaTokenType.C_STYLE_COMMENT;
      _tokenEndOffset = statement.getTokenEnd();
    }
    else
    {
      flexLocateToken();
    }
  }

  private int findCommentRangeEnd( boolean nestedIf )
  {
    if( nestedIf && _visibleStmts.isEmpty() )
    {
      return -1;
    }

    for( int i = 0; i < _visibleStmts.size(); i++ )
    {
      Statement stmt = _visibleStmts.get( i );
      if( nestedIf && i == _visibleStmts.size() - 1 )
      {
        assert stmt.getTokenEnd() == stmt.getTokenStart(); // the empty statement
        // a nested `#if` must match a real
        return -1;
      }

      if( _bufferIndex < stmt.getTokenStart() )
      {
        return stmt.getTokenStart();
      }

      if( _bufferIndex < stmt.getTokenEnd() )
      {
        throw new IllegalStateException( "Directive is located in a visible statement at: " + _bufferIndex );
      }
    }
    return _bufferEndOffset;
  }

  private int getWhitespaces( int offset )
  {
    if( offset >= _bufferEndOffset )
    {
      return _bufferEndOffset;
    }

    int pos = offset;
    char c = charAt( pos );

    while( c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' )
    {
      pos++;
      if( pos == _bufferEndOffset )
      {
        return pos;
      }
      c = charAt( pos );
    }

    return pos;
  }

  private int skipSpaces( int offset )
  {
    if( offset >= _bufferEndOffset )
    {
      return _bufferEndOffset;
    }

    int pos = offset;
    char c = charAt( pos );

    while( c == ' ' || c == '\t' )
    {
      pos++;
      if( pos == _bufferEndOffset )
      {
        return pos;
      }
      c = charAt( pos );
    }

    return pos;
  }

  private boolean match( String str, int offset )
  {
    if( _buffer.length() < offset + str.length() )
    {
      return false;
    }

    int i;
    for( i = 0; i < str.length(); i++ )
    {
      if( str.charAt( i ) != _buffer.charAt( offset + i ) )
      {
        return false;
      }
    }

    if( offset + i >= _buffer.length() )
    {
      return true;
    }

    char c = _buffer.charAt( offset + i );
    return !Character.isJavaIdentifierPart( c );
  }


  private void flexLocateToken()
  {
    try
    {
      _flexLexer.goTo( _bufferIndex );
      _tokenType = _flexLexer.advance();
      _tokenEndOffset = _flexLexer.getTokenEnd();
    }
    catch( IOException e )
    { /* impossible */ }
  }

  private int getClosingQuote( int offset, char quoteChar )
  {
    if( offset >= _bufferEndOffset )
    {
      return _bufferEndOffset;
    }

    int pos = offset;
    char c = charAt( pos );

    while( true )
    {
      while( c != quoteChar && c != '\n' && c != '\r' && c != '\\' )
      {
        pos++;
        if( pos >= _bufferEndOffset )
        {
          return _bufferEndOffset;
        }
        c = charAt( pos );
      }

      if( c == '\\' )
      {
        pos++;
        if( pos >= _bufferEndOffset )
        {
          return _bufferEndOffset;
        }
        c = charAt( pos );
        if( c == '\n' || c == '\r' )
        {
          continue;
        }
        pos++;
        if( pos >= _bufferEndOffset )
        {
          return _bufferEndOffset;
        }
        c = charAt( pos );
      }
      else if( c == quoteChar )
      {
        break;
      }
      else
      {
        pos--;
        break;
      }
    }

    return pos + 1;
  }

  private int getTextBlockEnd( int offset )
  {
    int pos = offset;

    while( (pos = getClosingQuote( pos + 1, '"' )) < _bufferEndOffset )
    {
      if( pos + 1 < _bufferEndOffset && charAt( pos + 1 ) == '"' && charAt( pos ) == '"' )
      {
        pos += 2;
        break;
      }
    }

    return pos;
  }

  private int getClosingComment( int offset )
  {
    int pos = offset;

    while( pos < _bufferEndOffset - 1 )
    {
      char c = charAt( pos );
      if( c == '*' && (charAt( pos + 1 )) == '/' )
      {
        break;
      }
      pos++;
    }

    return pos + 2;
  }

  private int getLineTerminator( int offset )
  {
    int pos = offset;

    while( pos < _bufferEndOffset )
    {
      char c = charAt( pos );
      if( c == '\r' || c == '\n' )
      {
        break;
      }
      pos++;
    }

    return pos;
  }

  private int getRawLiteralEnd( int offset )
  {
    int pos = offset;

    while( pos < _bufferEndOffset && charAt( pos ) == '`' )
    {
      pos++;
    }
    int quoteLen = pos - offset;

    int start;
    do
    {
      while( pos < _bufferEndOffset && charAt( pos ) != '`' )
      {
        pos++;
      }
      start = pos;
      while( pos < _bufferEndOffset && charAt( pos ) == '`' )
      {
        pos++;
      }
    }
    while( pos - start != quoteLen && pos < _bufferEndOffset );

    return pos;
  }

  private char charAt( int position )
  {
    return _bufferArray != null ? _bufferArray[position] : _buffer.charAt( position );
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence()
  {
    return _buffer;
  }

  @Override
  public final int getBufferEnd()
  {
    return _bufferEndOffset;
  }

}