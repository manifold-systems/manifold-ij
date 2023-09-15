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

package manifold.ij.extensions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.util.ReparseUtil;
import manifold.preprocessor.PreprocessorParser;
import manifold.preprocessor.definitions.Definitions;
import manifold.preprocessor.expression.Expression;
import manifold.preprocessor.expression.ExpressionParser;
import manifold.preprocessor.statement.FileStatement;
import manifold.preprocessor.statement.IfStatement;
import manifold.preprocessor.statement.SourceStatement;
import manifold.preprocessor.statement.Statement;
import manifold.util.concurrent.LocklessLazyVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_LITERAL;
import static manifold.preprocessor.TokenType.*;

/**
 * Replaces the JavaLexer to handle Preprocessor tokens (that start with '#').
 */
public class ManJavaLexer extends LexerBase
{
  private static final String MANIFOLD_PREPROCESSOR_DUMB_MODE = "manifold.preprocessor.dumb_mode";
  private static final LocklessLazyVar<boolean[]> PREPROCESSOR_DUMB_MODE = LocklessLazyVar.make( () ->
    new boolean[] {PropertiesComponent.getInstance().getBoolean( MANIFOLD_PREPROCESSOR_DUMB_MODE )} );

  private static final int STATE_DEFAULT = 0;
  private static final int STATE_TEXT_BLOCK_TEMPLATE = 1;

  private final com.intellij.lang.java.lexer.@Jailbreak _JavaLexer _flexLexer;
  private final boolean myStringTemplates;
  private final IntStack myStateStack = new IntArrayList(1);
  private CharSequence _buffer;
  private @Nullable char[] _bufferArray;
  private int _bufferIndex;
  private int _bufferEndOffset;
  private int _tokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType _tokenType;
  private SmartPsiElementPointer<PsiJavaFile> _psiFile;
  private ASTNode _chameleon;
  /** The length of the last valid unicode escape (6 or greater), or 1 when no unicode escape was found. */
  private int mySymbolLength = 1;


  private final List<SourceStatement> _visibleStmts = new ArrayList<>();
  private final LocklessLazyVar<Definitions> _definitions = LocklessLazyVar.make( () -> {
    Definitions definitions = makeDefinitions();
    PsiJavaFile psiJavaFile;
    if( _psiFile != null && (psiJavaFile = _psiFile.getElement()) != null )
    {
      // add local #define symbols
      CharSequence text = _buffer.length() < psiJavaFile.getTextLength() ? psiJavaFile.getText() : _buffer;
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
    ReparseUtil.instance().reparseOpenJavaFilesForAllProjects();
  }

  public ManJavaLexer( @NotNull LanguageLevel level )
  {
    _flexLexer = new com.intellij.lang.java.lexer.@Jailbreak _JavaLexer( level );
    myStringTemplates = level.isAtLeast( LanguageLevel.JDK_21_PREVIEW );
  }

  public ManJavaLexer( @Jailbreak JavaLexer lexer, @NotNull LanguageLevel level )
  {
    _flexLexer = lexer.myFlexLexer;
    myStringTemplates = level.isAtLeast( LanguageLevel.JDK_21_PREVIEW );
  }

  public void setChameleon( ASTNode chameleon )
  {
    _chameleon = chameleon;
    _psiFile = SmartPointerManager.createPointer( ManPsiBuilderFactoryImpl.getPsiFile( chameleon ) );
  }

  @NotNull
  private ManDefinitions makeDefinitions()
  {
    return new ManDefinitions( _chameleon, _psiFile );
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
    mySymbolLength = 1;
    myStateStack.push(initialState);
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

    char c = locateCharAt( _bufferIndex );
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

      case '{':
        if (myStringTemplates) {
          int count = myStateStack.topInt() >> 16;
          if (count > 0) myStateStack.push((myStateStack.popInt() & STATE_TEXT_BLOCK_TEMPLATE) | ((count + 1) << 16));
        }
        _tokenType = JavaTokenType.LBRACE;
        _tokenEndOffset = _bufferIndex + mySymbolLength;
        break;
      case '}':
        if (myStringTemplates) {
          int count = myStateStack.topInt() >> 16;
          if (count > 0) {
            if (count != 1) {
              myStateStack.push((myStateStack.popInt() & STATE_TEXT_BLOCK_TEMPLATE) | ((count - 1) << 16));
            }
            else {
              int state = myStateStack.popInt();
              if (myStateStack.isEmpty()) myStateStack.push(STATE_DEFAULT);
              if ((state & STATE_TEXT_BLOCK_TEMPLATE) != 0) {
                boolean fragment = locateLiteralEnd(_bufferIndex + mySymbolLength, LiteralType.TEXT_BLOCK);
                _tokenType = fragment ? JavaTokenType.TEXT_BLOCK_TEMPLATE_MID : JavaTokenType.TEXT_BLOCK_TEMPLATE_END;
              }
              else {
                boolean fragment = locateLiteralEnd(_bufferIndex + mySymbolLength, LiteralType.STRING);
                _tokenType = fragment ? JavaTokenType.STRING_TEMPLATE_MID : JavaTokenType.STRING_TEMPLATE_END;
              }
              break;
            }
          }
        }
        _tokenType = JavaTokenType.RBRACE;
        _tokenEndOffset = _bufferIndex + mySymbolLength;
        break;
      case '/':
        if( _bufferIndex + 1 >= _bufferEndOffset )
        {
          _tokenType = JavaTokenType.DIV;
          _tokenEndOffset = _bufferEndOffset;
        }
        else {
          int l1 = mySymbolLength;
          char nextChar = locateCharAt(_bufferIndex + l1);
          if (nextChar == '/') {
            _tokenType = JavaTokenType.END_OF_LINE_COMMENT;
            _tokenEndOffset = getLineTerminator(_bufferIndex + l1 + mySymbolLength);
          }
          else if (nextChar == '*') {
            int l2 = mySymbolLength;
            if (_bufferIndex + l1 + l2 < _bufferEndOffset && locateCharAt(_bufferIndex + l1 + l2) == '*') {
              int l3 = mySymbolLength;
              if (_bufferIndex + l1 + l2 + l3 < _bufferEndOffset && locateCharAt(_bufferIndex + l1 + l2 + l3) == '/') {
                _tokenType = JavaTokenType.C_STYLE_COMMENT;
                _tokenEndOffset = _bufferIndex + l1 + l2 + l3 + mySymbolLength;
              }
              else {
                _tokenType = JavaDocElementType.DOC_COMMENT;
                _tokenEndOffset = getClosingComment(_bufferIndex + l1 + l2 + l3);
              }
            }
            else {
              _tokenType = JavaTokenType.C_STYLE_COMMENT;
              _tokenEndOffset = getClosingComment(_bufferIndex + l1 + l2 + mySymbolLength);
            }
          }
          else {
            flexLocateToken();
          }
        }
        break;

      case '\'':
        _tokenType = JavaTokenType.CHARACTER_LITERAL;
        locateLiteralEnd(_bufferIndex + mySymbolLength, LiteralType.CHAR);
        break;

      case '"':
        int l1 = mySymbolLength;
        if (_bufferIndex + l1 < _bufferEndOffset && locateCharAt(_bufferIndex + l1) == '"') {
          int l2 = mySymbolLength;
          if (_bufferIndex + l1 + l2 < _bufferEndOffset && locateCharAt(_bufferIndex + l1 + l2) == '"') {
            boolean fragment = locateLiteralEnd(_bufferIndex + l1 + l2 + mySymbolLength, LiteralType.TEXT_BLOCK);
            _tokenType = fragment ? JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN : JavaTokenType.TEXT_BLOCK_LITERAL;
          }
          else {
            _tokenType = JavaTokenType.STRING_LITERAL;
            _tokenEndOffset = _bufferIndex + l1 + l2;
          }
        }
        else {
          boolean fragment = locateLiteralEnd(_bufferIndex + l1, LiteralType.STRING);
          _tokenType = fragment ? JavaTokenType.STRING_TEMPLATE_BEGIN : JavaTokenType.STRING_LITERAL;
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
    if( _chameleon == null || isDumbPreprocessorMode() )
    {
      // note the `_chamelion == null` check fixes an issue when an opening multiline comment proceeds a preprocessor directive
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
    char c = locateCharAt( pos );

    while( c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' )
    {
      pos += mySymbolLength;
      if( pos == _bufferEndOffset )
      {
        return pos;
      }
      c = locateCharAt( pos );
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
    char c = locateCharAt( pos );

    while( c == ' ' || c == '\t' )
    {
      pos++;
      if( pos == _bufferEndOffset )
      {
        return pos;
      }
      c = locateCharAt( pos );
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
    char c = locateCharAt( pos );

    while( true )
    {
      while( c != quoteChar && c != '\n' && c != '\r' && c != '\\' )
      {
        pos++;
        if( pos >= _bufferEndOffset )
        {
          return _bufferEndOffset;
        }
        c = locateCharAt( pos );
      }

      if( c == '\\' )
      {
        pos++;
        if( pos >= _bufferEndOffset )
        {
          return _bufferEndOffset;
        }
        c = locateCharAt( pos );
        if( c == '\n' || c == '\r' )
        {
          continue;
        }
        pos++;
        if( pos >= _bufferEndOffset )
        {
          return _bufferEndOffset;
        }
        c = locateCharAt( pos );
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

  /**
   * @param offset  the offset to start.
   * @param literalType  the type of string literal.
   * @return {@code true} if this is a string template fragment, {@code false} otherwise.
   */
  private boolean locateLiteralEnd(int offset, LiteralType literalType) {
    int pos = offset;

    while (pos < _bufferEndOffset) {
      char c = locateCharAt(pos);

      if (c == '\\') {
        pos += mySymbolLength;
        // on (encoded) backslash we also need to skip the next symbol (e.g. \\u005c" is translated to \")
        if (pos < _bufferEndOffset) {
          if (locateCharAt(pos) == '{' && myStringTemplates && literalType != LiteralType.CHAR) {
            pos += mySymbolLength;
            _tokenEndOffset = pos;
            if (myStateStack.topInt() == 0) myStateStack.popInt();
            if (literalType == LiteralType.TEXT_BLOCK) {
              myStateStack.push(STATE_TEXT_BLOCK_TEMPLATE | (1 << 16));
            }
            else {
              myStateStack.push(STATE_DEFAULT | (1 << 16));
            }
            return true;
          }
        }
      }
      else if (c == literalType.c) {
        if (literalType == LiteralType.TEXT_BLOCK) {
          if ((pos += mySymbolLength) < _bufferEndOffset && locateCharAt(pos) == '"') {
            if ((pos += mySymbolLength) < _bufferEndOffset && locateCharAt(pos) == '"') {
              _tokenEndOffset = pos + mySymbolLength;
              return false;
            }
          }
          continue;
        }
        else {
          _tokenEndOffset = pos + mySymbolLength;
          return false;
        }
      }
      else if ((c == '\n' || c == '\r') && mySymbolLength == 1 && literalType != LiteralType.TEXT_BLOCK) {
        _tokenEndOffset = pos;
        return false;
      }
      pos += mySymbolLength;
    }
    _tokenEndOffset = pos;
    return false;
  }

  private int getClosingComment( int offset )
  {
    int pos = offset;

    while (pos < _bufferEndOffset) {
      char c = locateCharAt(pos);
      pos += mySymbolLength;
      if (c == '*' && pos < _bufferEndOffset && locateCharAt(pos) == '/') break;
    }

    return pos + mySymbolLength;
  }

  private int getLineTerminator( int offset )
  {
    int pos = offset;

    while (pos < _bufferEndOffset) {
      char c = locateCharAt(pos);
      if (c == '\r' || c == '\n') break;
      pos += mySymbolLength;
    }

    return pos;
  }

  private char charAt( int position )
  {
    return _bufferArray != null ? _bufferArray[position] : _buffer.charAt( position );
  }

  private char locateCharAt(int offset) {
    mySymbolLength = 1;
    char first = charAt(offset);
    if (first != '\\') return first;
    int pos = offset + 1;
    if (pos < _bufferEndOffset && charAt(pos) == '\\') return first;
    boolean escaped = true;
    int i = offset;
    while (--i >= 0 && charAt(i) == '\\') escaped = !escaped;
    if (!escaped) return first;
    if (pos < _bufferEndOffset && charAt(pos) != 'u') return first;
    //noinspection StatementWithEmptyBody
    while (++pos < _bufferEndOffset && charAt(pos) == 'u');
    if (pos + 3 >= _bufferEndOffset) return first;
    int result = 0;
    for (int max = pos + 4; pos < max; pos++) {
      result <<= 4;
      char c = charAt(pos);
      if ('0' <= c && c <= '9') result += c - '0';
      else if ('a' <= c && c <= 'f') result += (c - 'a') + 10;
      else if ('A' <= c && c <= 'F') result += (c - 'A') + 10;
      else return first;
    }
    mySymbolLength = pos - offset;
    return (char)result;
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

  enum LiteralType {
    STRING('"'), CHAR('\''), TEXT_BLOCK('"');

    final char c;
    LiteralType(char c) {
      this.c = c;
    }
  }
}