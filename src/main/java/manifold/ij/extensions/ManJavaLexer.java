package manifold.ij.extensions;

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
import manifold.preprocessor.PreprocessorParser;
import manifold.preprocessor.definitions.Definitions;
import manifold.preprocessor.statement.FileStatement;
import manifold.preprocessor.statement.IfStatement;
import manifold.preprocessor.statement.SourceStatement;
import manifold.preprocessor.statement.Statement;
import manifold.util.ReflectUtil;
import manifold.util.concurrent.LocklessLazyVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.preprocessor.TokenType.Error;
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

  private final com.intellij.lang.java.lexer.@Jailbreak _JavaLexer myFlexLexer;
  private CharSequence myBuffer;
  private @Nullable
  char[] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;
  private PsiJavaFile _psiFile;
  private final List<SourceStatement> _visibleStmts = new ArrayList<>();
  private final LocklessLazyVar<Definitions> _definitions = LocklessLazyVar.make( () -> {
    Definitions definitions = makeDefinitions();
    if( _psiFile != null )
    {
      CharSequence text = myBuffer.length() < _psiFile.getTextLength() ? _psiFile.getText() : myBuffer;
      if( StringUtil.contains( text, "#define" ) || StringUtil.contains( text, "#undef" ) )
      {
        FileStatement fileStmt = new PreprocessorParser( text, null ).parseFile();
        fileStmt.execute( new ArrayList<>(), true, definitions );
      }
    }
    return definitions;
  } );

  @NotNull
  private ManDefinitions makeDefinitions()
  {
    return new ManDefinitions( _psiFile );
  }

  public ManJavaLexer( @NotNull LanguageLevel level )
  {
    myFlexLexer = new com.intellij.lang.java.lexer.@Jailbreak _JavaLexer( level );
  }

  public ManJavaLexer( @Jailbreak JavaLexer lexer )
  {
    myFlexLexer = lexer.myFlexLexer;
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
    myBuffer = buffer;
    myBufferArray = CharArrayUtil.fromSequenceWithoutCopying( buffer );
    myBufferIndex = startOffset;
    myBufferEndOffset = endOffset;
    myTokenType = null;
    myTokenEndOffset = startOffset;
    myFlexLexer.reset( myBuffer, startOffset, endOffset, 0 );
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
    return myTokenType;
  }

  @Override
  public final int getTokenStart()
  {
    return myBufferIndex;
  }

  @Override
  public final int getTokenEnd()
  {
    locateToken();
    return myTokenEndOffset;
  }

  @Override
  public final void advance()
  {
    locateToken();
    myTokenType = null;
  }

  private void locateToken()
  {
    if( myTokenType != null )
    {
      return;
    }

    if( myTokenEndOffset == myBufferEndOffset )
    {
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myBufferIndex = myTokenEndOffset;

    char c = charAt( myBufferIndex );
    switch( c )
    {
      case ' ':
      case '\t':
      case '\n':
      case '\r':
      case '\f':
        myTokenType = TokenType.WHITE_SPACE;
        myTokenEndOffset = getWhitespaces( myBufferIndex + 1 );
        break;

      case '/':
        if( myBufferIndex + 1 >= myBufferEndOffset )
        {
          myTokenType = JavaTokenType.DIV;
          myTokenEndOffset = myBufferEndOffset;
        }
        else
        {
          char nextChar = charAt( myBufferIndex + 1 );
          if( nextChar == '/' )
          {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator( myBufferIndex + 2 );
          }
          else if( nextChar == '*' )
          {
            if( myBufferIndex + 2 >= myBufferEndOffset ||
                (charAt( myBufferIndex + 2 )) != '*' ||
                (myBufferIndex + 3 < myBufferEndOffset &&
                 (charAt( myBufferIndex + 3 )) == '/') )
            {
              myTokenType = JavaTokenType.C_STYLE_COMMENT;
              myTokenEndOffset = getClosingComment( myBufferIndex + 2 );
            }
            else
            {
              myTokenType = JavaDocElementType.DOC_COMMENT;
              myTokenEndOffset = getClosingComment( myBufferIndex + 3 );
            }
          }
          else
          {
            flexLocateToken();
          }
        }
        break;

      case '\'':
        myTokenType = JavaTokenType.CHARACTER_LITERAL;
        myTokenEndOffset = getClosingQuote( myBufferIndex + 1, c );
        break;

      case '"':
        if( TEXT_BLOCK_LITERAL != null && // non-null if IJ >= 2019.2
            myBufferIndex + 2 < myBufferEndOffset &&
            charAt( myBufferIndex + 2 ) == '"' && charAt( myBufferIndex + 1 ) == '"' )
        {
          myTokenType = TEXT_BLOCK_LITERAL;
          myTokenEndOffset = getTextBlockEnd( myBufferIndex + 2 );
        }
        else
        {
          myTokenType = JavaTokenType.STRING_LITERAL;
          myTokenEndOffset = getClosingQuote( myBufferIndex + 1, c );
        }
        break;

      case '`':
        myTokenType = JavaTokenType.RAW_STRING_LITERAL;
        myTokenEndOffset = getRawLiteralEnd( myBufferIndex );
        break;

      case '#':
      {
        int offset = myBufferIndex + 1;
        if( match( Elif.getDirective(), offset ) ||
            match( Else.getDirective(), offset ) )
        {
          myTokenType = JavaTokenType.C_STYLE_COMMENT;
          myTokenEndOffset = findCommentRangeEnd( false );
        }
        else if( match( Endif.getDirective(), offset ) )
        {
          myTokenType = JavaTokenType.C_STYLE_COMMENT;
          myTokenEndOffset = offset + Endif.getDirective().length();
        }
        else if( match( If.getDirective(), offset ) )
        {
          int rangeEnd = findCommentRangeEnd( true );
          if( rangeEnd > 0 )
          {
            // handle nested `#if`
            myTokenType = JavaTokenType.C_STYLE_COMMENT;
            myTokenEndOffset = rangeEnd;
          }
          else
          {
            // Create a PreprocessorParser with position set to '#' char, then call parseStatement()
            // Then use that to figure out what is a comment and what is not :)
            PreprocessorParser preProc = new PreprocessorParser( myBuffer, myBufferIndex, myBufferEndOffset, null );
            IfStatement statement = (IfStatement)preProc.parseStatement();

            // note we always parse the toplevel #if, and nested #ifs are parsed with it.  since the lexer tokenizes
            // the whole #if statement, we only ever have just one list of visible stmts to manage
            _visibleStmts.clear();
            statement.execute( _visibleStmts, true, _definitions.get() );
            // add empty statement marking end of if-stmt
            _visibleStmts.add( new SourceStatement( null, statement.getTokenEnd(), statement.getTokenEnd() ) );

            myTokenType = JavaTokenType.C_STYLE_COMMENT;
            myTokenEndOffset = findCommentRangeEnd( false );
          }
        }
        else if( match( Define.getDirective(), offset ) ||
                 match( Undef.getDirective(), offset ) )
        {
          PreprocessorParser preProc = new PreprocessorParser( myBuffer, myBufferIndex, myBufferEndOffset, null );
          Statement statement = preProc.parseStatement();

          statement.execute( new ArrayList<>(), true, _definitions.get() );

          myTokenType = JavaTokenType.C_STYLE_COMMENT;
          myTokenEndOffset = statement.getTokenEnd();
        }
        else if( match( Error.getDirective(), offset ) ||
                 match( Warning.getDirective(), offset ) )
        {
          PreprocessorParser preProc = new PreprocessorParser( myBuffer, myBufferIndex, myBufferEndOffset, null );
          Statement statement = preProc.parseStatement();
          myTokenType = JavaTokenType.C_STYLE_COMMENT;
          myTokenEndOffset = statement.getTokenEnd();
        }
        else
        {
          flexLocateToken();
        }
        break;
      }

      default:
        flexLocateToken();
    }

    if( myTokenEndOffset > myBufferEndOffset )
    {
      myTokenEndOffset = myBufferEndOffset;
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

      if( myBufferIndex < stmt.getTokenStart() )
      {
        return stmt.getTokenStart();
      }

      if( myBufferIndex < stmt.getTokenEnd() )
      {
        throw new IllegalStateException( "Directive is located in a visible statement at: " + myBufferIndex );
      }
    }
    return myBufferEndOffset;
  }

  private int getWhitespaces( int offset )
  {
    if( offset >= myBufferEndOffset )
    {
      return myBufferEndOffset;
    }

    int pos = offset;
    char c = charAt( pos );

    while( c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' )
    {
      pos++;
      if( pos == myBufferEndOffset )
      {
        return pos;
      }
      c = charAt( pos );
    }

    return pos;
  }

  private boolean match( String str, int offset )
  {
    if( myBuffer.length() < offset + str.length() )
    {
      return false;
    }

    int i;
    for( i = 0; i < str.length(); i++ )
    {
      if( str.charAt( i ) != myBuffer.charAt( offset + i ) )
      {
        return false;
      }
    }

    if( offset + i >= myBuffer.length() )
    {
      return true;
    }

    char c = myBuffer.charAt( offset + i );
    return !Character.isJavaIdentifierPart( c );
  }


  private void flexLocateToken()
  {
    try
    {
      myFlexLexer.goTo( myBufferIndex );
      myTokenType = myFlexLexer.advance();
      myTokenEndOffset = myFlexLexer.getTokenEnd();
    }
    catch( IOException e )
    { /* impossible */ }
  }

  private int getClosingQuote( int offset, char quoteChar )
  {
    if( offset >= myBufferEndOffset )
    {
      return myBufferEndOffset;
    }

    int pos = offset;
    char c = charAt( pos );

    while( true )
    {
      while( c != quoteChar && c != '\n' && c != '\r' && c != '\\' )
      {
        pos++;
        if( pos >= myBufferEndOffset )
        {
          return myBufferEndOffset;
        }
        c = charAt( pos );
      }

      if( c == '\\' )
      {
        pos++;
        if( pos >= myBufferEndOffset )
        {
          return myBufferEndOffset;
        }
        c = charAt( pos );
        if( c == '\n' || c == '\r' )
        {
          continue;
        }
        pos++;
        if( pos >= myBufferEndOffset )
        {
          return myBufferEndOffset;
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

    while( (pos = getClosingQuote( pos + 1, '"' )) < myBufferEndOffset )
    {
      if( pos + 1 < myBufferEndOffset && charAt( pos + 1 ) == '"' && charAt( pos ) == '"' )
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

    while( pos < myBufferEndOffset - 1 )
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

    while( pos < myBufferEndOffset )
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

    while( pos < myBufferEndOffset && charAt( pos ) == '`' )
    {
      pos++;
    }
    int quoteLen = pos - offset;

    int start;
    do
    {
      while( pos < myBufferEndOffset && charAt( pos ) != '`' )
      {
        pos++;
      }
      start = pos;
      while( pos < myBufferEndOffset && charAt( pos ) == '`' )
      {
        pos++;
      }
    }
    while( pos - start != quoteLen && pos < myBufferEndOffset );

    return pos;
  }

  private char charAt( int position )
  {
    return myBufferArray != null ? myBufferArray[position] : myBuffer.charAt( position );
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence()
  {
    return myBuffer;
  }

  @Override
  public final int getBufferEnd()
  {
    return myBufferEndOffset;
  }

}