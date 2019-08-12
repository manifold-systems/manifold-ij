package manifold.ij.template.psi;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import manifold.api.util.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static manifold.ij.template.psi.ManTemplateTokenType.*;

public class ManTemplateLexer extends LexerBase
{
  private CharSequence myBuffer;
  private int myEndOffset;

  private int _index;
  private Stack<Token> _tokens;
  private CharSequence _text;
  private StringBuilder _stuff;

  private int _tokenIndex;
  private boolean _isParsingString;
  private boolean _isParsingCharLiteral;

  @Override
  public void start( @NotNull CharSequence buffer, int startOffset, int endOffset, int initialState )
  {
    myBuffer = buffer;
    myEndOffset = endOffset;

    _tokens = new Stack<>();
    _text = buffer.subSequence( startOffset, endOffset );
    _index = initialState - startOffset;
    tokenize();
    _tokenIndex = -1;
    nextToken();
  }

  @Override
  public void advance()
  {
    nextToken();
  }

  @Override
  public int getState()
  {
    return getToken() == null ? 0 : myEndOffset;
  }

  private Token getToken()
  {
    return _tokenIndex < 0 ? null : _tokens.get( _tokenIndex );
  }

  @Nullable
  @Override
  public IElementType getTokenType()
  {
    return getToken() == null ? null : getToken()._type;
  }

  @Override
  public int getTokenStart()
  {
    return getToken() == null ? 0 : getToken()._offset;
  }

  @Override
  public int getTokenEnd()
  {
    return getToken() == null ? 0 : getToken()._offset + getToken()._value.length();
  }

  @Override
  public int getBufferEnd()
  {
    return myEndOffset;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence()
  {
    return myBuffer;
  }

  private void nextToken()
  {
    if( _tokenIndex+1 < _tokens.size() )
    {
      _tokenIndex++;
    }
    else
    {
      _tokenIndex = -1;
    }
  }

  private void tokenize()
  {
    _stuff = new StringBuilder();
    _isParsingString = false;
    _isParsingCharLiteral = false;
    int index = _index;
    boolean escaped = false;
    while( true )
    {
      if( index >= _text.length() )
      {
        break;
      }

      int before = index;
      char c = _text.charAt( index );

      if( !escaped && c == '\\' && !isInCode() && _text.length() > index+1 &&
          (charIs( index+1, '<' ) || charIs( index+1, '$' )) )
      {
        _stuff.append( c );
        escaped = true;
        index++;
        continue;
      }

      if( isTop( COMMENT_BEGIN ) )
      {
        if( c == '-' )
        {
          index++;
          if( charIs( index, '-' ) )
          {
            index++;
            if( charIs( index, '%' ) )
            {
              index++;
              if( charIs( index, '>' ) )
              {
                pushStuff();
                pushToken( COMMENT_END, ++index );
                continue;
              }
            }
          }
        }
      }
      else
      {
        if( c == '$' && !isInCode() && !escaped )
        {
          index++;
          if( charIs( index, '{' ) )
          {
            pushStuff();
            pushToken( EXPR_BRACE_BEGIN, ++index );
            continue;
          }
        }
        else if( c == '<' && !isInCode() && !escaped )
        {
          index++;
          if( charIs( index, '%' ) )
          {
            pushStuff();
            index++;
            if( charIs( index, '=' ) )
            {
              pushToken( EXPR_ANGLE_BEGIN, ++index );
              continue;
            }
            else if( charIs( index, '@' ) )
            {
              pushToken( DIR_ANGLE_BEGIN, ++index );
              continue;
            }
            else if( charIs( index, '-' ) )
            {
              if( charIs( index + 1, '-' ) )
              {
                pushToken( COMMENT_BEGIN, index += 2 );
                continue;
              }
            }

            pushToken( STMT_ANGLE_BEGIN, index );
            continue;
          }
        }
        else if( c == '}' && isTop( EXPR_BRACE_BEGIN ) && isInCode() && !isParsingString() && !isParsingCharLiteral() )
        {
          pushStuff();
          pushToken( EXPR_BRACE_END, ++index );
          continue;
        }
        else if( c == '%' && isInCode() && !isParsingString() )
        {
          index++;
          if( charIs( index, '>' ) )
          {
            pushStuff();
            pushToken( ANGLE_END, ++index );
            continue;
          }
        }
      }
      _stuff.append( c );
      index = before + 1;
      setParsingString( c, before );
      setParsingCharLiteral( c, before );
      escaped = false;
    }
    
    pushStuff();
  }

  private void setParsingString( char c, int index )
  {
    if( !isInCode() || c != '"' || isParsingCharLiteral() )
    {
      return;
    }

    if( !isParsingString() )
    {
      _isParsingString = true;
    }
    else if( !charIs( index-1, '\\' ) )
    {
      _isParsingString = false;
    }
  }

  private boolean isParsingString()
  {
    return _isParsingString;
  }

  private void setParsingCharLiteral( char c, int index )
  {
    if( !isInCode() || c != '\'' || isParsingString() )
    {
      return;
    }

    if( !isParsingCharLiteral() )
    {
      _isParsingCharLiteral = true;
    }
    else if( !charIs( index-1, '\\' ) )
    {
      _isParsingCharLiteral = false;
    }
  }

  private boolean isParsingCharLiteral()
  {
    return _isParsingCharLiteral;
  }

  private boolean charIs( int index, char c )
  {
    return _text.length() > index && _text.charAt( index ) == c;
  }

  private void pushStuff()
  {
    if( _stuff == null || _stuff.length() == 0 )
    {
      return;
    }

    ManTemplateTokenType beginType = _tokens.size() == 0 ? null : _tokens.peek()._type;
    ManTemplateTokenType stuffType;
    if( beginType == EXPR_BRACE_BEGIN ||
        beginType == EXPR_ANGLE_BEGIN )
    {
      stuffType = EXPR;
    }
    else if( beginType == STMT_ANGLE_BEGIN )
    {
      stuffType = STMT;
    }
    else if( beginType == DIR_ANGLE_BEGIN )
    {
      stuffType = DIRECTIVE;
    }
    else if( beginType == COMMENT_BEGIN )
    {
      stuffType = COMMENT;
    }
    else
    {
      stuffType = CONTENT;
    }
    _tokens.push( new Token( stuffType, _index, _stuff.toString() ) );
    _index += _stuff.length();
    _stuff = new StringBuilder();
  }

  private void pushToken( ManTemplateTokenType tokenType, int index )
  {
    _tokens.push( new Token( tokenType, _index, tokenType.getToken() ) );
    _index = index;
  }

  private boolean isInCode()
  {
    return isTop( EXPR_ANGLE_BEGIN ) ||
           isTop( EXPR_BRACE_BEGIN ) ||
           isTop( STMT_ANGLE_BEGIN ) ||
           isTop( DIR_ANGLE_BEGIN ) ||
           isTop( COMMENT_BEGIN );
  }

  private boolean isTop( ManTemplateTokenType tokenType )
  {
    return !_tokens.isEmpty() && _tokens.peek()._type == tokenType;
  }

  private class Token
  {
    ManTemplateTokenType _type;
    int _offset;
    String _value;

    Token( ManTemplateTokenType type, int offset, String value )
    {
      _type = type;
      _offset = offset;
      _value = value;
    }
  }
}
