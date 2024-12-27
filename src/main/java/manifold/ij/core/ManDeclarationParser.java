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

package manifold.ij.core;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.lang.java.parser.BasicReferenceParser;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import manifold.ext.rt.api.Jailbreak;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// override to allow default param values, see "manifold:" comments below
public class ManDeclarationParser extends DeclarationParser
{
  public ManDeclarationParser( @NotNull JavaParser javaParser )
  {
    super( javaParser );
  }

  @Nullable
  public PsiBuilder.@Nullable Marker parseParameterOrRecordComponent( PsiBuilder builder, boolean ellipsis, boolean disjunctiveType, boolean varType, boolean isParameter )
  {
    int typeFlags = 0;
    if( ellipsis )
    {
      typeFlags |= 2;
    }

    if( disjunctiveType )
    {
      typeFlags |= 16;
    }

    if( varType )
    {
      typeFlags |= 128;
    }

    @Jailbreak ManDeclarationParser _this = this;
    return this.parseListElement( builder, true, typeFlags, isParameter ? _this.myJavaElementTypeContainer.PARAMETER : _this.myJavaElementTypeContainer.RECORD_COMPONENT );
  }


  @Nullable
  private PsiBuilder.@Nullable Marker parseListElement( PsiBuilder builder, boolean typed, int typeFlags, IElementType type )
  {
    @Jailbreak ManDeclarationParser _this = this;

    PsiBuilder.Marker param = builder.mark();
    Pair<PsiBuilder.Marker, Boolean> modListInfo = _this.parseModifierList( builder );
    if( typed )
    {
      int flags = 5 | typeFlags;
      BasicReferenceParser.TypeInfo typeInfo = _this.myParser.getReferenceParser().parseTypeInfo( builder, flags );
      if( typeInfo == null )
      {
        if( Boolean.TRUE.equals( modListInfo.second ) )
        {
          param.rollbackTo();
          return null;
        }

        BasicJavaParserUtil.error( builder, JavaPsiBundle.message( "expected.type", new Object[0] ) );
        BasicJavaParserUtil.emptyElement( builder, _this.myJavaElementTypeContainer.TYPE );
      }
    }

    if( typed )
    {
      IElementType tokenType = builder.getTokenType();
      if( tokenType == JavaTokenType.THIS_KEYWORD || tokenType == JavaTokenType.IDENTIFIER && builder.lookAhead( 1 ) == JavaTokenType.DOT )
      {
        PsiBuilder.Marker mark = builder.mark();
        PsiBuilder.Marker expr = _this.myParser.getExpressionParser().parse( builder );
        if( expr != null && BasicJavaParserUtil.exprType( expr ) == _this.myJavaElementTypeContainer.THIS_EXPRESSION )
        {
          mark.drop();
          BasicJavaParserUtil.done( param, _this.myJavaElementTypeContainer.RECEIVER_PARAMETER, _this.myWhiteSpaceAndCommentSetHolder );
          return param;
        }

        mark.rollbackTo();
      }
    }

    if( PsiBuilderUtil.expect( builder, JavaTokenType.IDENTIFIER ) )
    {
      //Manifold: allow parameters to have default values
      if( type != _this.myJavaElementTypeContainer.PARAMETER && type != _this.myJavaElementTypeContainer.RECORD_COMPONENT )
      {
        if( BasicJavaParserUtil.expectOrError( builder, JavaTokenType.EQ, "expected.eq" ) && _this.myParser.getExpressionParser().parse( builder ) == null )
        {
          BasicJavaParserUtil.error( builder, JavaPsiBundle.message( "expected.expression", new Object[0] ) );
        }

        BasicJavaParserUtil.done( param, _this.myJavaElementTypeContainer.RESOURCE_VARIABLE, _this.myWhiteSpaceAndCommentSetHolder );
        return param;
      }
      else if( type == _this.myJavaElementTypeContainer.PARAMETER )
      {
        _this.eatBrackets( builder, (String)null );
        if( PsiBuilderUtil.expect( builder, JavaTokenType.EQ ) && _this.myParser.getExpressionParser().parse( builder ) == null )
        {
          BasicJavaParserUtil.error( builder, JavaPsiBundle.message( "expected.expression", new Object[0] ) );
        }
      }
      BasicJavaParserUtil.done( param, type, _this.myWhiteSpaceAndCommentSetHolder );
      return param;
    }
    else
    {
      BasicJavaParserUtil.error( builder, JavaPsiBundle.message( "expected.identifier", new Object[0] ) );
      param.drop();
      return (PsiBuilder.Marker)modListInfo.first;
    }
  }
}
