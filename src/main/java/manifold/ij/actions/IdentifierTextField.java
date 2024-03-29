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

package manifold.ij.actions;

import com.google.common.base.Strings;
import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

public class IdentifierTextField extends JTextField
{
  private final boolean _bAcceptDot;
  private final boolean _bAcceptUnderscore;

  public IdentifierTextField()
  {
    this( false, true );
  }

  public IdentifierTextField( boolean bAcceptDot )
  {
    this( bAcceptDot, true );
  }

  public IdentifierTextField( boolean bAcceptDot, boolean bAcceptUnderscore )
  {
    _bAcceptDot = bAcceptDot;
    _bAcceptUnderscore = bAcceptUnderscore;
  }

  @Override
  protected Document createDefaultModel()
  {
    return new IdentifierDocument();
  }

  private class IdentifierDocument extends PlainDocument
  {
    @Override
    public void insertString( int iOffset, String str, AttributeSet a ) throws BadLocationException
    {
      if( Strings.isNullOrEmpty( str ) )
      {
        return;
      }

      String strText = IdentifierTextField.this.getText();
      StringBuilder strbText = new StringBuilder( Strings.nullToEmpty( strText ) );

      if( iOffset <= strbText.length() )
      {
        strbText.insert( iOffset, str );
      }
      else
      {
        strbText.append( str );
      }

      if( !isValidIdentifier( strbText, _bAcceptDot ) || strbText.toString().contains( "$" ) )
      {
        String validID = makeValidIdentifier( strbText.toString(), _bAcceptDot, _bAcceptUnderscore );
        if( !_bAcceptUnderscore )
        {
          validID = validID.replace( "$", "" );
        }
        str = validID.substring( iOffset, iOffset + (validID.length() - strText.length()) );
      }

      super.insertString( iOffset, str, a );
    }
  }

  public static boolean isValidIdentifier( CharSequence seqId, boolean acceptDot )
  {
    if( seqId.length() == 0 || !Character.isJavaIdentifierStart( seqId.charAt( 0 ) ) )
    {
      return false;
    }
    for( int i = 1, iLen = seqId.length(); i < iLen; i++ )
    {
      if( acceptDot && (seqId.charAt( i ) == '.' || seqId.charAt( i ) == '#') )
      {
        continue;
      }

      if( !Character.isJavaIdentifierPart( seqId.charAt( i ) ) )
      {
        return false;
      }
    }
    return true;
  }

  public static String makeValidIdentifier( String str, boolean acceptDot, boolean bAcceptUnderscore )
  {
    StringBuilder rtn = new StringBuilder( str );
    while( rtn.length() > 0 && (!Character.isJavaIdentifierStart( rtn.charAt( 0 ) ) || (!bAcceptUnderscore && rtn.charAt( 0 ) == '_')) )
    {
      rtn.deleteCharAt( 0 );
    }

    for( int i = 1; i < rtn.length(); )
    {
      char c = rtn.charAt( i );
      if( acceptDot && (c == '.' || c == '#') )
      {
        i++;
        continue;
      }

      if( !Character.isJavaIdentifierPart( c ) || (!bAcceptUnderscore && c == '_') )
      {
        rtn.deleteCharAt( i );
        continue;
      }

      i++;
    }
    return rtn.toString();
  }
}
